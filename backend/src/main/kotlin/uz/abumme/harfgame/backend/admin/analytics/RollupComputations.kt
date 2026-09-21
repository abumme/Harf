package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.admin.calendar.CalendarDays
import uz.abumme.harfgame.backend.admin.calendar.CalendarUsage
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.AnalyticsAccountsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsCohortDayTable
import uz.abumme.harfgame.backend.db.AnalyticsContentDayTable
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsPoolDayTable
import uz.abumme.harfgame.backend.db.AnalyticsStaffDayTable
import uz.abumme.harfgame.backend.db.AnalyticsSuggestionsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsWordDayTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.admin.audit.ActorKind
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The SQL behind each rollup group: one day recomputed from the raw tables, its rows replaced. Every method runs inside
 * the job's transaction for that day.
 *
 * **State snapshots.** End-of-day states (account totals, active words, answer pools) cannot be rebuilt for a past
 * date, so they are captured on the date's first computation — only while that happens within [STATE_CAPTURE_WINDOW]
 * of the date's end, which in steady state is minutes — and kept as they are by every recomputation. A date first
 * computed later (history before the first run, a long downtime) keeps NULL states, shown as unavailable.
 */
internal class RollupComputations(private val catalog: WordCatalogService) {
    private val json = Json { ignoreUnknownKeys = true }

    // --- first data day ------------------------------------------------------------------------------------------

    /** The earliest day [group] has data for, or null without any. */
    fun firstDataDay(group: RollupGroup): Long? = when (group) {
        is RollupGroup.Lang -> minDay("SELECT min(puzzle_day) AS first FROM game_results WHERE lang = :lang", mapOf("lang" to group.lang))
        RollupGroup.Global, RollupGroup.Cohort -> minDay("SELECT min(puzzle_day) AS first FROM game_results")
        is RollupGroup.Word -> minDay(
            "SELECT min(puzzle_day) AS first FROM game_results WHERE lang IN (:langs)",
            mapOf("langs" to DailyCalendars.packLanguages(group.calendar)),
        )
        RollupGroup.Events -> firstEventDate()?.toEpochDay()
    }

    private fun minDay(sql: String, params: Map<String, Any?> = emptyMap()): Long? =
        AnalyticsSql.query(sql, params) { it.longOrNull("first") }.firstOrNull()

    private fun firstEventDate(): LocalDate? {
        val instants = AnalyticsSql.query(
            """
            SELECT (SELECT min(created_at) FROM users) AS users,
                   (SELECT min(created_at) FROM word_suggestions) AS suggestions,
                   (SELECT min(created_at) FROM words) AS words,
                   (SELECT min(at) FROM staff_audit_log) AS audit
            """.trimIndent(),
        ) { rs -> listOf("users", "suggestions", "words", "audit").mapNotNull { rs.getTimestamp(it)?.toInstant() } }.single()
        val dates = instants.map { LocalDate.ofInstant(it, AnalyticsDays.EVENTS_ZONE) } +
            AnalyticsSql.query("SELECT min(date) AS first FROM account_events_daily") { rs -> rs.getDate("first")?.toLocalDate() }.filterNotNull()
        return dates.minOrNull()
    }

    // --- results ---------------------------------------------------------------------------------------------------

    fun lang(lang: String, day: Long) {
        val params = mapOf("lang" to lang, "day" to day, "from" to day - STREAK_WINDOW_DAYS, "yesterday" to day - 1)
        val outcome = AnalyticsSql.query(
            """
            SELECT count(*) AS games,
                   count(*) FILTER (WHERE won) AS wins,
                   count(*) FILTER (WHERE won AND attempts = 1) AS won_1,
                   count(*) FILTER (WHERE won AND attempts = 2) AS won_2,
                   count(*) FILTER (WHERE won AND attempts = 3) AS won_3,
                   count(*) FILTER (WHERE won AND attempts = 4) AS won_4,
                   count(*) FILTER (WHERE won AND attempts = 5) AS won_5,
                   count(*) FILTER (WHERE won AND attempts = 6) AS won_6,
                   coalesce(sum(attempts) FILTER (WHERE won), 0) AS attempts_sum_won
            FROM game_results
            WHERE lang = :lang AND puzzle_day = :day
            """.trimIndent(),
            params,
        ) { rs -> IntArray(8) { i -> rs.getInt(if (i == 0) "games" else if (i == 1) "wins" else "won_${i - 1}") } to rs.getLong("attempts_sum_won") }
            .single()
        // Current streaks by the app's rule (Streaks.streak): the run of consecutive won days ending at the latest win,
        // live only when that win is the day itself or the day before. A run of 30 already lands in 30+, so 31 days of
        // wins are enough; gaps-and-islands finds the run.
        val streaks = AnalyticsSql.query(
            """
            WITH wins AS (
                SELECT user_id, puzzle_day FROM game_results
                WHERE lang = :lang AND won AND puzzle_day BETWEEN :from AND :day
            ), live AS (
                SELECT user_id, max(puzzle_day) AS last_day FROM wins
                GROUP BY user_id HAVING max(puzzle_day) >= :yesterday
            ), islands AS (
                SELECT w.user_id, w.puzzle_day,
                       w.puzzle_day - row_number() OVER (PARTITION BY w.user_id ORDER BY w.puzzle_day) AS island
                FROM wins w JOIN live l ON l.user_id = w.user_id
            ), runs AS (
                SELECT user_id, count(*) AS run_length, max(puzzle_day) AS end_day FROM islands GROUP BY user_id, island
            )
            SELECT count(*) FILTER (WHERE r.run_length = 1) AS streak_1,
                   count(*) FILTER (WHERE r.run_length BETWEEN 2 AND 6) AS streak_2_6,
                   count(*) FILTER (WHERE r.run_length BETWEEN 7 AND 29) AS streak_7_29,
                   count(*) FILTER (WHERE r.run_length >= 30) AS streak_30_plus
            FROM runs r JOIN live l ON l.user_id = r.user_id AND r.end_day = l.last_day
            """.trimIndent(),
            params,
        ) { rs -> listOf(rs.getInt("streak_1"), rs.getInt("streak_2_6"), rs.getInt("streak_7_29"), rs.getInt("streak_30_plus")) }
            .single()

        val (counts, attemptsSum) = outcome
        AnalyticsLangDayTable.deleteWhere { (AnalyticsLangDayTable.lang eq lang) and (AnalyticsLangDayTable.puzzleDay eq day) }
        AnalyticsLangDayTable.insert {
            it[AnalyticsLangDayTable.lang] = lang
            it[puzzleDay] = day
            // One result per account, language and day: players and games are the same count.
            it[players] = counts[0]
            it[games] = counts[0]
            it[wins] = counts[1]
            it[losses] = counts[0] - counts[1]
            it[won1] = counts[2]
            it[won2] = counts[3]
            it[won3] = counts[4]
            it[won4] = counts[5]
            it[won5] = counts[6]
            it[won6] = counts[7]
            it[attemptsSumWon] = attemptsSum
            it[streak1] = streaks[0]
            it[streak2to6] = streaks[1]
            it[streak7to29] = streaks[2]
            it[streak30plus] = streaks[3]
        }
    }

    fun global(day: Long) {
        val (dau, wau, mau) = AnalyticsSql.query(
            """
            SELECT count(DISTINCT user_id) FILTER (WHERE puzzle_day = :day) AS dau,
                   count(DISTINCT user_id) FILTER (WHERE puzzle_day >= :week) AS wau,
                   count(DISTINCT user_id) AS mau
            FROM game_results
            WHERE puzzle_day BETWEEN :month AND :day
            """.trimIndent(),
            mapOf("day" to day, "week" to day - 6, "month" to day - 29),
        ) { rs -> Triple(rs.getInt("dau"), rs.getInt("wau"), rs.getInt("mau")) }.single()
        AnalyticsGlobalDayTable.deleteWhere { AnalyticsGlobalDayTable.puzzleDay eq day }
        AnalyticsGlobalDayTable.insert {
            it[puzzleDay] = day
            it[AnalyticsGlobalDayTable.dau] = dau
            it[AnalyticsGlobalDayTable.wau] = wau
            it[AnalyticsGlobalDayTable.mau] = mau
        }
    }

    /** Cohort [day]: accounts whose earliest result (any language) is on it; D1/D7/D30 only once the target day closed. */
    fun cohort(day: Long, now: Instant) {
        val lastClosed = AnalyticsDays.closedGlobalPuzzleDay(now)
        val row = AnalyticsSql.query(
            """
            WITH cohort AS (
                SELECT DISTINCT g.user_id FROM game_results g
                WHERE g.puzzle_day = :day
                  AND NOT EXISTS (SELECT 1 FROM game_results e WHERE e.user_id = g.user_id AND e.puzzle_day < :day)
            )
            SELECT count(*) AS size,
                   count(r1.user_id) AS d1,
                   count(r7.user_id) AS d7,
                   count(r30.user_id) AS d30
            FROM cohort c
            LEFT JOIN (SELECT DISTINCT user_id FROM game_results WHERE puzzle_day = :day1) r1 ON r1.user_id = c.user_id
            LEFT JOIN (SELECT DISTINCT user_id FROM game_results WHERE puzzle_day = :day7) r7 ON r7.user_id = c.user_id
            LEFT JOIN (SELECT DISTINCT user_id FROM game_results WHERE puzzle_day = :day30) r30 ON r30.user_id = c.user_id
            """.trimIndent(),
            mapOf("day" to day, "day1" to day + 1, "day7" to day + 7, "day30" to day + 30),
        ) { rs -> listOf(rs.getInt("size"), rs.getInt("d1"), rs.getInt("d7"), rs.getInt("d30")) }.single()
        fun known(offset: Long, value: Int): Int? = value.takeIf { day + offset <= lastClosed }
        AnalyticsCohortDayTable.deleteWhere { AnalyticsCohortDayTable.cohortDay eq day }
        AnalyticsCohortDayTable.insert {
            it[cohortDay] = day
            it[size] = row[0]
            it[d1] = known(1, row[1])
            it[d7] = known(7, row[2])
            it[d30] = known(30, row[3])
        }
    }

    /**
     * [calendar]'s word of puzzle [day] and how it was played; Uzbek counts the results of both scripts. The word and its
     * marker come from the calendar; a LEGACY day keeps its stored word without a marker, and a day the calendar has no
     * row for takes the published pack schedule's word.
     */
    fun word(calendar: String, day: Long) {
        val (players, wins, attemptsSum) = AnalyticsSql.query(
            """
            SELECT count(*) AS players,
                   count(*) FILTER (WHERE won) AS wins,
                   coalesce(sum(attempts) FILTER (WHERE won), 0) AS attempts_sum_won
            FROM game_results
            WHERE lang IN (:langs) AND puzzle_day = :day
            """.trimIndent(),
            mapOf("langs" to DailyCalendars.packLanguages(calendar), "day" to day),
        ) { rs -> Triple(rs.getInt("players"), rs.getInt("wins"), rs.getLong("attempts_sum_won")) }.single()
        val stored = DailyWordsTable.selectAll()
            .where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq LocalDate.ofEpochDay(day)) }
            .singleOrNull()
        val picked = stored?.get(DailyWordsTable.daySource)?.takeIf { it != DaySource.LEGACY.name }
        val word: String
        val wordCyrl: String?
        if (stored != null) {
            word = stored[DailyWordsTable.text]
            wordCyrl = stored[DailyWordsTable.textCyrl]
        } else if (calendar == DailyCalendars.UZ) {
            word = scheduledWord("uz-latn", day).orEmpty()
            wordCyrl = scheduledWord("uz-cyrl", day)
        } else {
            word = scheduledWord(calendar, day).orEmpty()
            wordCyrl = null
        }
        AnalyticsWordDayTable.deleteWhere { (AnalyticsWordDayTable.calendar eq calendar) and (AnalyticsWordDayTable.puzzleDay eq day) }
        AnalyticsWordDayTable.insert {
            it[AnalyticsWordDayTable.calendar] = calendar
            it[puzzleDay] = day
            it[AnalyticsWordDayTable.word] = word.take(WORD_MAX)
            it[AnalyticsWordDayTable.wordCyrl] = wordCyrl?.take(WORD_MAX)
            it[daySource] = picked
            it[isRepeat] = picked != null && stored?.get(DailyWordsTable.isRepeat) == true
            it[AnalyticsWordDayTable.players] = players
            it[AnalyticsWordDayTable.wins] = wins
            it[attemptsSumWon] = attemptsSum
        }
    }

    /** The word [lang]'s published schedule gives [day], as the app resolves it; null without a pack or schedule. */
    private fun scheduledWord(lang: String, day: Long): String? {
        val pack = WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.singleOrNull() ?: return null
        val schedule = runCatching { json.decodeFromString<List<String>>(pack[WordPacksTable.schedule]) }.getOrNull()
        if (schedule.isNullOrEmpty()) return null
        return WordPackSchedule.answerFor(schedule, pack[WordPacksTable.anchorEpochDay], day)
    }

    // --- events ----------------------------------------------------------------------------------------------------

    /** Every event metric of Asia/Tashkent [date]. [firstComputation] decides whether end-of-day states are captured. */
    fun events(date: LocalDate, now: Instant, firstComputation: Boolean) {
        val range = mapOf("start" to AnalyticsDays.startOfEventDate(date), "end" to AnalyticsDays.endOfEventDate(date))
        val capture = firstComputation && now.isBefore(AnalyticsDays.endOfEventDate(date).plus(STATE_CAPTURE_WINDOW))
        val packLanguages = WordPacksTable.selectAll().map { it[WordPacksTable.lang] }.toSet()
        accounts(date, range, now, capture)
        suggestions(date, range, packLanguages)
        content(date, range, packLanguages, capture)
        pools(date, now, capture)
        staff(date, range)
    }

    private fun accounts(date: LocalDate, range: Map<String, Any?>, now: Instant, capture: Boolean) {
        val newAccounts = AnalyticsSql.query("SELECT count(*) AS n FROM users WHERE created_at >= :start AND created_at < :end", range) { it.getInt("n") }.single()
        val linksSince = readMeta(AnalyticsRollupJob.LINKS_RECORDED_SINCE_KEY)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val linksKnown = linksSince != null && !linksSince.isAfter(AnalyticsDays.startOfEventDate(date))
        val links = if (!linksKnown) emptyMap() else AnalyticsSql.query(
            "SELECT provider, count(*) AS n FROM oauth_identities WHERE linked_at >= :start AND linked_at < :end GROUP BY provider",
            range,
        ) { rs -> rs.getString("provider") to rs.getInt("n") }.toMap()
        val deletions = AnalyticsSql.query("SELECT deletions FROM account_events_daily WHERE date = :date", mapOf("date" to date)) { it.getInt("deletions") }
            .singleOrNull() ?: 0

        val previous = AnalyticsAccountsDayTable.selectAll().where { AnalyticsAccountsDayTable.date eq date }.singleOrNull()
        val states: List<Int?> = when {
            previous != null -> listOf(
                previous[AnalyticsAccountsDayTable.total],
                previous[AnalyticsAccountsDayTable.linked],
                previous[AnalyticsAccountsDayTable.googleLinked],
                previous[AnalyticsAccountsDayTable.appleLinked],
            )
            capture -> AnalyticsSql.query(
                """
                SELECT (SELECT count(*) FROM users) AS total,
                       (SELECT count(DISTINCT user_id) FROM oauth_identities) AS linked,
                       (SELECT count(DISTINCT user_id) FROM oauth_identities WHERE provider = :google) AS google,
                       (SELECT count(DISTINCT user_id) FROM oauth_identities WHERE provider = :apple) AS apple
                """.trimIndent(),
                mapOf("google" to OAuthProvider.GOOGLE.name, "apple" to OAuthProvider.APPLE.name),
            ) { rs -> listOf(rs.getInt("total"), rs.getInt("linked"), rs.getInt("google"), rs.getInt("apple")) }.single()
            else -> listOf(null, null, null, null)
        }
        AnalyticsAccountsDayTable.deleteWhere { AnalyticsAccountsDayTable.date eq date }
        AnalyticsAccountsDayTable.insert {
            it[AnalyticsAccountsDayTable.date] = date
            it[AnalyticsAccountsDayTable.newAccounts] = newAccounts
            it[linksGoogle] = if (linksKnown) links[OAuthProvider.GOOGLE.name] ?: 0 else null
            it[linksApple] = if (linksKnown) links[OAuthProvider.APPLE.name] ?: 0 else null
            it[AnalyticsAccountsDayTable.deletions] = deletions
            it[total] = states[0]
            it[linked] = states[1]
            it[googleLinked] = states[2]
            it[appleLinked] = states[3]
        }
    }

    /**
     * Suggestions per language: stored that day; decided that day by `decided_at` (AUTO is automatic; EDITOR, legacy
     * NULL and panel decisions are editor decisions); pending at the end of the day, exactly, from the two timestamps;
     * and the median time to decision of each kind.
     */
    private fun suggestions(date: LocalDate, range: Map<String, Any?>, packLanguages: Set<String>) {
        val params = range + mapOf(
            "accepted" to SuggestionStatus.ACCEPTED.name,
            "rejected" to SuggestionStatus.REJECTED.name,
            "pending" to SuggestionStatus.PENDING.name,
            "auto" to AUTO,
        )
        val rows = AnalyticsSql.query(
            """
            SELECT lang,
                   count(*) FILTER (WHERE created_at >= :start) AS submitted,
                   count(*) FILTER (WHERE decided_at >= :start AND decided_at < :end AND status = :accepted AND decided_via = :auto) AS auto_accepted,
                   count(*) FILTER (WHERE decided_at >= :start AND decided_at < :end AND status = :accepted AND decided_via IS DISTINCT FROM :auto) AS editor_accepted,
                   count(*) FILTER (WHERE decided_at >= :start AND decided_at < :end AND status = :rejected) AS rejected,
                   count(*) FILTER (WHERE (decided_at IS NULL AND status = :pending) OR decided_at >= :end) AS backlog_end,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM decided_at - created_at)::float8)
                       FILTER (WHERE decided_at >= :start AND decided_at < :end AND status <> :pending AND decided_via = :auto) AS median_auto,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM decided_at - created_at)::float8)
                       FILTER (WHERE decided_at >= :start AND decided_at < :end AND status <> :pending AND decided_via IS DISTINCT FROM :auto) AS median_editor
            FROM word_suggestions
            WHERE created_at < :end
            GROUP BY lang
            """.trimIndent(),
            params,
        ) { rs ->
            rs.getString("lang") to SuggestionCounts(
                rs.getInt("submitted"), rs.getInt("auto_accepted"), rs.getInt("editor_accepted"), rs.getInt("rejected"),
                rs.getInt("backlog_end"), rs.doubleOrNull("median_auto"), rs.doubleOrNull("median_editor"),
            )
        }.toMap()
        AnalyticsSuggestionsDayTable.deleteWhere { AnalyticsSuggestionsDayTable.date eq date }
        val languages = (packLanguages + rows.keys).sorted()
        AnalyticsSuggestionsDayTable.batchInsert(languages, shouldReturnGeneratedValues = false) { lang ->
            val counts = rows[lang] ?: SuggestionCounts()
            this[AnalyticsSuggestionsDayTable.date] = date
            this[AnalyticsSuggestionsDayTable.lang] = lang
            this[AnalyticsSuggestionsDayTable.submitted] = counts.submitted
            this[AnalyticsSuggestionsDayTable.autoAccepted] = counts.autoAccepted
            this[AnalyticsSuggestionsDayTable.editorAccepted] = counts.editorAccepted
            this[AnalyticsSuggestionsDayTable.rejected] = counts.rejected
            this[AnalyticsSuggestionsDayTable.backlogEnd] = counts.backlogEnd
            this[AnalyticsSuggestionsDayTable.medianAutoSeconds] = counts.medianAuto
            this[AnalyticsSuggestionsDayTable.medianEditorSeconds] = counts.medianEditor
        }
    }

    private data class SuggestionCounts(
        val submitted: Int = 0,
        val autoAccepted: Int = 0,
        val editorAccepted: Int = 0,
        val rejected: Int = 0,
        val backlogEnd: Int = 0,
        val medianAuto: Double? = null,
        val medianEditor: Double? = null,
    )

    /**
     * Catalog words per language: added by `created_at` and source, removed and restored from the audit log, and the
     * active count captured once. An edit keeps its word's row and inserts a tombstone of the old spelling that copies
     * `created_at` and source, so each `WORD_EDITED` of a word created that day is taken back out of "added".
     */
    private fun content(date: LocalDate, range: Map<String, Any?>, packLanguages: Set<String>, capture: Boolean) {
        val added = HashMap<Pair<String, String>, Int>()
        AnalyticsSql.query(
            "SELECT lang, source, count(*) AS n FROM words WHERE created_at >= :start AND created_at < :end GROUP BY lang, source",
            range,
        ) { rs -> added.merge(rs.getString("lang") to rs.getString("source"), rs.getInt("n"), Int::plus) }
        AnalyticsSql.query(
            """
            SELECT w.lang, w.source, count(*) AS n
            FROM staff_audit_log a JOIN words w ON w.id = a.target_id
            WHERE a.action = :edited AND a.at >= :start AND w.created_at >= :start AND w.created_at < :end
            GROUP BY w.lang, w.source
            """.trimIndent(),
            range + ("edited" to AuditActions.WORD_EDITED),
        ) { rs -> added.merge(rs.getString("lang") to rs.getString("source"), -rs.getInt("n"), Int::plus) }
        val changes = AnalyticsSql.query(
            """
            SELECT lang,
                   count(*) FILTER (WHERE action = :removed) AS removed,
                   count(*) FILTER (WHERE action = :restored) AS restored
            FROM staff_audit_log
            WHERE at >= :start AND at < :end AND action IN (:removed, :restored) AND lang IS NOT NULL
            GROUP BY lang
            """.trimIndent(),
            range + mapOf("removed" to AuditActions.WORD_REMOVED, "restored" to AuditActions.WORD_RESTORED),
        ) { rs -> rs.getString("lang") to (rs.getInt("removed") to rs.getInt("restored")) }.toMap()

        val previous = AnalyticsContentDayTable.selectAll().where { AnalyticsContentDayTable.date eq date }
            .associate { it[AnalyticsContentDayTable.lang] to it[AnalyticsContentDayTable.activeWords] }
        val active: Map<String, Int?> = when {
            previous.isNotEmpty() -> previous
            capture -> AnalyticsSql.query(
                "SELECT lang, count(*) AS n FROM words WHERE status = :active GROUP BY lang",
                mapOf("active" to WordStatus.ACTIVE.name),
            ) { rs -> rs.getString("lang") to rs.getInt("n") }.toMap()
            else -> emptyMap()
        }
        val languages = (packLanguages + added.keys.map { it.first } + changes.keys + active.keys).sorted()
        AnalyticsContentDayTable.deleteWhere { AnalyticsContentDayTable.date eq date }
        AnalyticsContentDayTable.batchInsert(languages, shouldReturnGeneratedValues = false) { lang ->
            fun addedBy(source: WordSource) = added[lang to source.name] ?: 0
            this[AnalyticsContentDayTable.date] = date
            this[AnalyticsContentDayTable.lang] = lang
            this[AnalyticsContentDayTable.activeWords] = when {
                previous.isNotEmpty() -> previous[lang]
                capture -> active[lang] ?: 0
                else -> null
            }
            this[AnalyticsContentDayTable.addedBundled] = addedBy(WordSource.BUNDLED)
            this[AnalyticsContentDayTable.addedSuggestion] = addedBy(WordSource.SUGGESTION)
            this[AnalyticsContentDayTable.addedAuto] = addedBy(WordSource.AUTO)
            this[AnalyticsContentDayTable.addedStaff] = addedBy(WordSource.STAFF)
            this[AnalyticsContentDayTable.removed] = changes[lang]?.first ?: 0
            this[AnalyticsContentDayTable.restored] = changes[lang]?.second ?: 0
        }
    }

    /** Each calendar's answer pool and never-used eligible words, captured once (null before a calendar's first run). */
    private fun pools(date: LocalDate, now: Instant, capture: Boolean) {
        val previous = AnalyticsPoolDayTable.selectAll().where { AnalyticsPoolDayTable.date eq date }
            .associate { it[AnalyticsPoolDayTable.calendar] to (it[AnalyticsPoolDayTable.poolSize] to it[AnalyticsPoolDayTable.unusedLeft]) }
        val values = DailyCalendars.ALL.associateWith { calendar ->
            when {
                previous.isNotEmpty() -> previous[calendar] ?: (null to null)
                capture -> currentPool(calendar, now)
                else -> null to null
            }
        }
        AnalyticsPoolDayTable.deleteWhere { AnalyticsPoolDayTable.date eq date }
        AnalyticsPoolDayTable.batchInsert(DailyCalendars.ALL, shouldReturnGeneratedValues = false) { calendar ->
            this[AnalyticsPoolDayTable.date] = date
            this[AnalyticsPoolDayTable.calendar] = calendar
            this[AnalyticsPoolDayTable.poolSize] = values.getValue(calendar).first
            this[AnalyticsPoolDayTable.unusedLeft] = values.getValue(calendar).second
        }
    }

    /**
     * A calendar's pool now: its active eligible words (Uzbek: complete pairs) and how many of them were never a daily
     * word since the history start, as the answer pool shows it. Nulls before the calendar's first run.
     */
    fun currentPool(calendar: String, now: Instant): Pair<Int?, Int?> {
        val calendars = catalog.calendars
        if (calendars.initializedOn(calendar) == null) return null to null
        val eligible = calendars.eligible(calendar)
        val usage = CalendarUsage.load(calendars, calendar, CalendarDays.today(calendar, now))
        return eligible.size to eligible.count { usage.isUnused(it.text) }
    }

    /**
     * Staff activity from the audit log, counted in words (one entry is one word; a bulk add writes one entry per word).
     * Added: `WORD_ADDED` by staff (source STAFF) and `WORD_RESTORED` not caused by an accepted suggestion; words that
     * enter through a decision count as that decision only. Decisions: `SUGGESTION_DECIDED` in the panel or Telegram.
     * A Telegram actor without a linked staff member is [TELEGRAM_UNLINKED]; SYSTEM (automatic acceptance) is excluded.
     */
    private fun staff(date: LocalDate, range: Map<String, Any?>) {
        val rows = AnalyticsSql.query(
            """
            SELECT staff_key, lang,
                   count(*) FILTER (WHERE (action = :added AND details::jsonb ->> 'source' = :staffSource)
                                       OR (action = :restored AND (details IS NULL OR details::jsonb ->> 'suggestion' IS NULL))) AS added,
                   count(*) FILTER (WHERE action = :edited) AS edited,
                   count(*) FILTER (WHERE action = :removed) AS removed,
                   count(*) FILTER (WHERE action = :decided) AS decided
            FROM (
                SELECT coalesce(actor_staff_id, :unlinked) AS staff_key, lang, action, details
                FROM staff_audit_log
                WHERE at >= :start AND at < :end AND lang IS NOT NULL
                  AND action IN (:added, :restored, :edited, :removed, :decided)
                  AND (actor_kind = :staffKind OR actor_kind = :telegramKind)
            ) entries
            GROUP BY staff_key, lang
            """.trimIndent(),
            range + mapOf(
                "added" to AuditActions.WORD_ADDED,
                "restored" to AuditActions.WORD_RESTORED,
                "edited" to AuditActions.WORD_EDITED,
                "removed" to AuditActions.WORD_REMOVED,
                "decided" to AuditActions.SUGGESTION_DECIDED,
                "staffSource" to WordSource.STAFF.name,
                "unlinked" to TELEGRAM_UNLINKED,
                "staffKind" to ActorKind.STAFF.name,
                "telegramKind" to ActorKind.TELEGRAM.name,
            ),
        ) { rs -> StaffCounts(rs.getString("staff_key"), rs.getString("lang"), rs.getInt("added"), rs.getInt("edited"), rs.getInt("removed"), rs.getInt("decided")) }
            .filter { it.added + it.edited + it.removed + it.decided > 0 }
        AnalyticsStaffDayTable.deleteWhere { AnalyticsStaffDayTable.date eq date }
        AnalyticsStaffDayTable.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
            this[AnalyticsStaffDayTable.date] = date
            this[AnalyticsStaffDayTable.staffKey] = row.staffKey
            this[AnalyticsStaffDayTable.lang] = row.lang
            this[AnalyticsStaffDayTable.added] = row.added
            this[AnalyticsStaffDayTable.edited] = row.edited
            this[AnalyticsStaffDayTable.removed] = row.removed
            this[AnalyticsStaffDayTable.decided] = row.decided
        }
    }

    private data class StaffCounts(val staffKey: String, val lang: String, val added: Int, val edited: Int, val removed: Int, val decided: Int)

    companion object {
        /** The staff key of Telegram decisions by editors not linked to a staff account. */
        const val TELEGRAM_UNLINKED = "telegram-unlinked"

        /** A current streak reaches 30+ at exactly 30 days, so wins older than this many days before the day are not needed. */
        const val STREAK_WINDOW_DAYS = 30L

        /** How long after a date's end its first computation may still capture end-of-day states. */
        val STATE_CAPTURE_WINDOW: Duration = Duration.ofDays(1)

        private const val AUTO = "AUTO"
        private const val WORD_MAX = 64
    }
}
