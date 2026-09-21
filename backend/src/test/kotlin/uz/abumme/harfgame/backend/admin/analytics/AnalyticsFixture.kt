package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.db.AccountEventsDailyTable
import uz.abumme.harfgame.backend.db.AnalyticsMetaTable
import uz.abumme.harfgame.backend.db.CalendarStateTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.ActorKind
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The analytics fixture: a small world with hand-computed numbers, loaded with [load] (or [loadAccounts] alone) after
 * [resetAnalyticsData]. Every scene sits on its own days or languages so no scene disturbs another's numbers; the
 * expected values are in [AnalyticsExpected] next to it.
 *
 * The clock is [NOW], 2026-09-17 10:00 UTC (15:00 Tashkent/Almaty, 13:00 Moscow): the last puzzle day closed in every
 * zone is 2026-09-16, and days from 2026-09-10 on are still provisional. Result days are offsets from [BASE]
 * (2026-05-01); event dates are Asia/Tashkent dates around [E] (2026-08-20).
 *
 * Accounts ([loadAccounts], ids 1–10): 1–3 Google, 4 Apple, 5 Google and Apple, 6–10 anonymous. 1–3 are created on E
 * (00:30, 15:00 and 20:00 Tashkent), 4 at 23:30 Moscow on E (01:30 on E+1 in Tashkent), the rest on 2026-04-30. Link
 * times are recorded from the start of E+1: account 1's Google link and account 5's Apple link are on E+1, the others
 * have no link time. Two explicit deletions are counted on E.
 *
 * Results (accounts 11–69, anonymous, created 2026-04-30):
 * - day 0: accounts 11–14 play English, 14 also Russian.
 * - day 3: Kazakh, accounts 15–19: wins in 2, 3, 3, 4 attempts and one loss.
 * - day 5: the cohort 20–29 starts in English; 20, 21 play Russian and 22, 23 English on day 6; 20, 21 English on 12;
 *   20 English on 35.
 * - day 40: English manual pick "apple", accounts 30–49: 15 wins (5 each in 3, 4, 5 attempts), 5 losses.
 * - day 41: an automatic repeat "bread" (no results); day 43: a LEGACY day "chair"; day 44: no calendar row (the
 *   pack schedule's "wharf").
 * - day 42: Uzbek "kitob"/"китоб", accounts 50–55 in Latin (4 attempts), 56–59 in Cyrillic (5 attempts).
 * - day 80: account 60 played only on 74 (d−6), account 61 only on 73 (d−7); nothing else within 51–80.
 * - day 115 (English streaks): 62 won 113–115; 63 won 106–114; 64 won only 113; 65 won 86–115 (30 days); 66 won 112,
 *   113, lost 114, won 115.
 * - account 67 (deleted by the privacy test) plays Russian on day 120 (final) and 136 (2026-09-14, provisional).
 * - account 68 plays English on 123 (2026-09-01) and 124: a cohort whose D30 is not known yet.
 * - account 69 plays English today (day 139).
 *
 * Suggestions: Russian on E — 3 automatic (1, 2, 3 minutes), 1 editor-accepted and 1 rejected (3 hours each), 1 still
 * pending, all submitted on E; a legacy acceptance (no recorded source) submitted E−1 and decided E+1 09:00. English —
 * one submitted E 09:00 and accepted E+2 12:00; three editor decisions on E+5 after 10 minutes, 2 hours and 1 day.
 *
 * Catalog and staff (ADMIN "boss", WORDER "worder" with Russian):
 * - English on E: boss adds 12 words (one later edited on E+1, one removed on E), restores a bundled word from E−10;
 *   3 words enter by automatic acceptance (SYSTEM).
 * - Russian on E: worder bulk-adds 40 words, edits 2, removes 1, and decides 5 suggestions through a linked Telegram
 *   account (2 accepted as new words, 1 accepted restoring a removed word, 2 rejected); unlinked Telegram editors decide
 *   2; automatic acceptance decides 3. On E+1 worder restores a word.
 * - Kazakh: 30 daily-eligible words (added 2026-07-31); the calendar started 2026-08-01 and used 23 of them.
 * - English calendar: day 41 is a repeat; one repeat scheduled on 2026-09-25.
 */
object AnalyticsFixture {
    val NOW: Instant = Instant.parse("2026-09-17T10:00:00Z")
    val BASE: LocalDate = LocalDate.of(2026, 5, 1)
    val E: LocalDate = LocalDate.of(2026, 8, 20)
    private val TASHKENT: ZoneId = ZoneId.of("Asia/Tashkent")
    private val OLD: Instant = Instant.parse("2026-04-30T08:00:00Z")

    const val ACTIVITY = 0
    const val OUTCOMES = 3
    const val COHORT = 5
    const val MANUAL_PICK = 40
    const val REPEAT = 41
    const val UZBEK = 42
    const val LEGACY = 43
    const val SCHEDULED = 44
    const val WINDOW = 80
    const val STREAK = 115
    const val DELETED_FINAL = 120
    const val LATE_COHORT = 123
    const val DELETED_PROVISIONAL = 136
    const val TODAY = 139

    /** The epoch day of result offset [offset]. */
    fun day(offset: Int): Long = BASE.toEpochDay() + offset

    fun date(offset: Int): LocalDate = BASE.plusDays(offset.toLong())

    fun tashkent(date: LocalDate, hour: Int, minute: Int = 0): Instant = date.atTime(hour, minute).atZone(TASHKENT).toInstant()

    /** Fixture account [n]'s id. */
    fun player(n: Int): String = "00000000-0000-4000-8000-%012d".format(n)

    /** Display names and provider subjects the fixture stores: none may appear in analytics output. */
    val personalData = mutableListOf<String>()

    /** The staff ids of the last [load]. */
    lateinit var bossId: String
    lateinit var worderId: String

    fun load() {
        loadAccounts()
        loadResults()
        loadCalendars()
        loadSuggestions()
        loadCatalogAndStaff()
    }

    // --- accounts -------------------------------------------------------------------------------------------------

    fun loadAccounts() {
        personalData.clear()
        val created = mapOf(
            1 to tashkent(E, 0, 30),
            2 to tashkent(E, 15),
            3 to tashkent(E, 20),
            4 to Instant.parse("2026-08-20T20:30:00Z"), // 23:30 in Moscow, 01:30 on 2026-08-21 in Tashkent
        )
        for (n in 1..10) {
            val name = if (n <= 5) "Fixture Name $n" else null
            name?.let { personalData += it }
            insertAccount(player(n), created[n] ?: OLD, name)
        }
        identity(1, OAuthProvider.GOOGLE, linkedAt = tashkent(E.plusDays(1), 9))
        identity(2, OAuthProvider.GOOGLE)
        identity(3, OAuthProvider.GOOGLE)
        identity(4, OAuthProvider.APPLE)
        identity(5, OAuthProvider.GOOGLE)
        identity(5, OAuthProvider.APPLE, linkedAt = tashkent(E.plusDays(1), 11))
        transaction(DatabaseFactory.init()) {
            AccountEventsDailyTable.insert {
                it[date] = E
                it[deletions] = 2
            }
            AnalyticsMetaTable.insert {
                it[key] = AnalyticsRollupJob.LINKS_RECORDED_SINCE_KEY
                it[value] = tashkent(E.plusDays(1), 0).toString()
            }
        }
    }

    private fun identity(n: Int, provider: OAuthProvider, linkedAt: Instant? = null) {
        val subject = "${provider.name.lowercase()}-subject-fixture-$n"
        personalData += subject
        transaction(DatabaseFactory.init()) {
            OAuthIdentitiesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[userId] = player(n)
                it[OAuthIdentitiesTable.provider] = provider.name
                it[providerSubject] = subject
                it[OAuthIdentitiesTable.linkedAt] = linkedAt
            }
        }
    }

    // --- results --------------------------------------------------------------------------------------------------

    fun loadResults() {
        for (n in 11..69) insertAccount(player(n))
        // Activity: four English players, one of them also Russian.
        for (n in 11..14) insertResults(player(n), "en", listOf(day(ACTIVITY)))
        insertResults(player(14), "ru", listOf(day(ACTIVITY)), attempts = 2)
        // Outcomes (Kazakh): wins in 2, 3, 3, 4 attempts and a loss.
        listOf(15 to 2, 16 to 3, 17 to 3, 18 to 4).forEach { (n, attempts) -> insertResults(player(n), "kk", listOf(day(OUTCOMES)), attempts = attempts) }
        insertResults(player(19), "kk", listOf(day(OUTCOMES)), won = false)
        // Retention: a cohort of 10 on day 5; 4 back on day 6 (two in Russian), 2 on day 12, 1 on day 35.
        for (n in 20..29) insertResults(player(n), "en", listOf(day(COHORT)), attempts = 4)
        for (n in 20..21) insertResults(player(n), "ru", listOf(day(COHORT + 1)))
        for (n in 22..23) insertResults(player(n), "en", listOf(day(COHORT + 1)))
        for (n in 20..21) insertResults(player(n), "en", listOf(day(COHORT + 7)))
        insertResults(player(20), "en", listOf(day(COHORT + 30)))
        // Word difficulty: 20 players of the manual pick, 15 wins averaging 4 attempts.
        for (n in 30..34) insertResults(player(n), "en", listOf(day(MANUAL_PICK)), attempts = 3)
        for (n in 35..39) insertResults(player(n), "en", listOf(day(MANUAL_PICK)), attempts = 4)
        for (n in 40..44) insertResults(player(n), "en", listOf(day(MANUAL_PICK)), attempts = 5)
        for (n in 45..49) insertResults(player(n), "en", listOf(day(MANUAL_PICK)), won = false)
        // Uzbek: 6 in Latin, 4 in Cyrillic.
        for (n in 50..55) insertResults(player(n), "uz-latn", listOf(day(UZBEK)), attempts = 4)
        for (n in 56..59) insertResults(player(n), "uz-cyrl", listOf(day(UZBEK)), attempts = 5)
        // WAU/MAU window edges.
        insertResults(player(60), "en", listOf(day(WINDOW - 6)))
        insertResults(player(61), "en", listOf(day(WINDOW - 7)))
        // Streaks on day 115.
        insertResults(player(62), "en", (day(STREAK - 2)..day(STREAK)).toList())
        insertResults(player(63), "en", (day(STREAK - 9)..day(STREAK - 1)).toList())
        insertResults(player(64), "en", listOf(day(STREAK - 2)))
        insertResults(player(65), "en", (day(STREAK - 29)..day(STREAK)).toList())
        insertResults(player(66), "en", listOf(day(STREAK - 3), day(STREAK - 2), day(STREAK)))
        insertResults(player(66), "en", listOf(day(STREAK - 1)), won = false)
        // The account the privacy test deletes: a final and a provisional Russian day.
        insertResults(player(67), "ru", listOf(day(DELETED_FINAL), day(DELETED_PROVISIONAL)))
        // A recent cohort whose D30 is not known yet.
        insertResults(player(68), "en", listOf(day(LATE_COHORT), day(LATE_COHORT + 1)))
        // Today so far.
        insertResults(player(69), "en", listOf(day(TODAY)))
    }

    // --- calendars ------------------------------------------------------------------------------------------------

    fun loadCalendars() {
        transaction(DatabaseFactory.init()) {
            fun dailyWord(calendar: String, day: LocalDate, text: String, source: DaySource, repeat: Boolean = false, cyrl: String? = null) {
                DailyWordsTable.insert {
                    it[DailyWordsTable.calendar] = calendar
                    it[DailyWordsTable.day] = day
                    it[wordId] = null
                    it[lexemeId] = null
                    it[DailyWordsTable.text] = text
                    it[textCyrl] = cyrl
                    it[daySource] = source.name
                    it[isRepeat] = repeat
                    it[pickedByStaffId] = null
                    it[pickedAt] = OLD
                }
            }
            dailyWord("en", date(MANUAL_PICK), "apple", DaySource.MANUAL)
            dailyWord("en", date(REPEAT), "bread", DaySource.AUTO, repeat = true)
            dailyWord("uz", date(UZBEK), "kitob", DaySource.AUTO, cyrl = "китоб")
            dailyWord("en", date(LEGACY), "chair", DaySource.LEGACY)
            dailyWord("en", LocalDate.of(2026, 9, 25), "bread", DaySource.AUTO, repeat = true)

            // Kazakh: 30 eligible words, 23 of them used since the calendar started on 2026-08-01.
            CalendarStateTable.insert {
                it[calendar] = "kk"
                it[initializedOn] = LocalDate.of(2026, 8, 1)
            }
            KAZAKH_POOL.forEachIndexed { index, text ->
                insertWord("kk", text, WordSource.BUNDLED, tashkent(LocalDate.of(2026, 7, 31), 9), eligible = true)
                if (index < 23) dailyWord("kk", LocalDate.of(2026, 8, 1).plusDays(index.toLong()), text, DaySource.AUTO)
            }
        }
    }

    /** 30 distinct Kazakh spellings (normalization leaves them as they are). */
    val KAZAKH_POOL: List<String> = listOf(
        "а", "ә", "б", "г", "ғ", "д", "е", "ж", "з", "и", "й", "к", "қ", "л", "м",
        "н", "ң", "о", "ө", "п", "р", "с", "т", "у", "ұ", "ү", "х", "һ", "ы", "і",
    ).map { "кіта$it" }

    // --- suggestions ----------------------------------------------------------------------------------------------

    fun loadSuggestions() {
        val e = E
        fun suggestion(lang: String, created: Instant, status: SuggestionStatus, decided: Instant? = null, via: String? = null) {
            transaction(DatabaseFactory.init()) {
                WordSuggestionsTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[WordSuggestionsTable.lang] = lang
                    it[word] = "fixture"
                    it[suggestedBy] = null
                    it[WordSuggestionsTable.status] = status.name
                    it[createdAt] = created
                    it[decidedAt] = decided
                    it[decidedVia] = via
                    it[decidedBy] = if (decided == null) null else "fixture"
                }
            }
        }
        // Russian on E.
        suggestion("ru", tashkent(e, 6), SuggestionStatus.ACCEPTED, tashkent(e, 6, 1), "AUTO")
        suggestion("ru", tashkent(e, 7), SuggestionStatus.ACCEPTED, tashkent(e, 7, 2), "AUTO")
        suggestion("ru", tashkent(e, 8), SuggestionStatus.ACCEPTED, tashkent(e, 8, 3), "AUTO")
        suggestion("ru", tashkent(e, 9), SuggestionStatus.ACCEPTED, tashkent(e, 12), "EDITOR")
        suggestion("ru", tashkent(e, 10), SuggestionStatus.REJECTED, tashkent(e, 13), "EDITOR")
        suggestion("ru", tashkent(e, 11), SuggestionStatus.PENDING)
        suggestion("ru", tashkent(e.minusDays(1), 12), SuggestionStatus.ACCEPTED, tashkent(e.plusDays(1), 9), via = null)
        // English: a backlog across E..E+2, and three editor decisions on E+5.
        suggestion("en", tashkent(e, 9), SuggestionStatus.ACCEPTED, tashkent(e.plusDays(2), 12), "EDITOR")
        suggestion("en", tashkent(e.plusDays(5), 8), SuggestionStatus.ACCEPTED, tashkent(e.plusDays(5), 8, 10), "EDITOR")
        suggestion("en", tashkent(e.plusDays(5), 6), SuggestionStatus.REJECTED, tashkent(e.plusDays(5), 8), "EDITOR")
        suggestion("en", tashkent(e.plusDays(4), 9), SuggestionStatus.ACCEPTED, tashkent(e.plusDays(5), 9), "EDITOR")
    }

    // --- catalog and staff ----------------------------------------------------------------------------------------

    fun loadCatalogAndStaff() {
        bossId = insertStaff("boss", Role.ADMIN, now = OLD)
        worderId = insertStaff("worder", Role.WORDER, listOf("ru"), now = OLD)
        val e = E
        val boss = Actor(ActorKind.STAFF, bossId)
        val worderPanel = Actor(ActorKind.STAFF, worderId)
        val worderTelegram = Actor(ActorKind.TELEGRAM, worderId)
        val unlinked = Actor(ActorKind.TELEGRAM, null)
        val system = Actor(ActorKind.SYSTEM, null)

        transaction(DatabaseFactory.init()) {
            // English: boss adds 12 on E, removes the 12th, restores a bundled word, edits the 11th on E+1.
            val enBundled = insertWord("en", "enbundled", WordSource.BUNDLED, tashkent(e.minusDays(10), 9), status = WordStatus.ACTIVE)
            val enStaff = (1..12).map { n ->
                val text = "enstaff%02d".format(n)
                val id = insertWord("en", text, WordSource.STAFF, tashkent(e, 10), status = if (n == 12) WordStatus.REMOVED else WordStatus.ACTIVE)
                audit(tashkent(e, 10), boss, AuditActions.WORD_ADDED, id, "en", "text" to text, "source" to WordSource.STAFF.name)
                id
            }
            audit(tashkent(e, 11), boss, AuditActions.WORD_REMOVED, enStaff[11], "en", "text" to "enstaff12")
            audit(tashkent(e, 12), boss, AuditActions.WORD_RESTORED, enBundled, "en", "text" to "enbundled")
            editWord("en", enStaff[10], "enstaff11", "enstaff11b", tashkent(e, 10), tashkent(e.plusDays(1), 10), boss)
            // English: three automatic acceptances.
            (1..3).forEach { n ->
                val id = insertWord("en", "enauto$n", WordSource.AUTO, tashkent(e, 14))
                audit(tashkent(e, 14), system, AuditActions.WORD_ADDED, id, "en", "text" to "enauto$n", "source" to WordSource.AUTO.name)
                audit(tashkent(e, 14), system, AuditActions.SUGGESTION_DECIDED, UUID.randomUUID().toString(), "en", "status" to "ACCEPTED", "via" to "AUTO")
            }

            // Russian: worder's bulk add of 40, two edits, one removal.
            val ruStaff = (1..40).map { n ->
                val text = "слово%02d".format(n)
                val id = insertWord("ru", text, WordSource.STAFF, tashkent(e, 10), status = if (n == 40) WordStatus.REMOVED else WordStatus.ACTIVE)
                audit(tashkent(e, 10), worderPanel, AuditActions.WORD_ADDED, id, "ru", "text" to text, "source" to WordSource.STAFF.name)
                id
            }
            editWord("ru", ruStaff[0], "слово01", "слово01а", tashkent(e, 10), tashkent(e, 11), worderPanel)
            editWord("ru", ruStaff[1], "слово02", "слово02а", tashkent(e, 10), tashkent(e, 11, 5), worderPanel)
            audit(tashkent(e, 12), worderPanel, AuditActions.WORD_REMOVED, ruStaff[39], "ru", "text" to "слово40")
            // Five Telegram decisions by worder: two accepted as new words, one restoring a removed word, two rejected.
            val ruRestored = insertWord("ru", "возврат", WordSource.BUNDLED, tashkent(e.minusDays(10), 9))
            (1..2).forEach { n ->
                val suggestionId = UUID.randomUUID().toString()
                val id = insertWord("ru", "предложено$n", WordSource.SUGGESTION, tashkent(e, 13))
                audit(tashkent(e, 13), worderTelegram, AuditActions.WORD_ADDED, id, "ru", "text" to "предложено$n", "source" to WordSource.SUGGESTION.name)
                audit(tashkent(e, 13), worderTelegram, AuditActions.SUGGESTION_DECIDED, suggestionId, "ru", "status" to "ACCEPTED", "via" to "EDITOR")
            }
            val restoringSuggestion = UUID.randomUUID().toString()
            audit(tashkent(e, 13, 30), worderTelegram, AuditActions.WORD_RESTORED, ruRestored, "ru", "text" to "возврат", "suggestion" to restoringSuggestion)
            audit(tashkent(e, 13, 30), worderTelegram, AuditActions.SUGGESTION_DECIDED, restoringSuggestion, "ru", "status" to "ACCEPTED", "via" to "EDITOR")
            (1..2).forEach { audit(tashkent(e, 14), worderTelegram, AuditActions.SUGGESTION_DECIDED, UUID.randomUUID().toString(), "ru", "status" to "REJECTED", "via" to "EDITOR") }
            // Unlinked Telegram editors decide two; automatic acceptance three (SYSTEM, never a staff member's).
            (1..2).forEach { audit(tashkent(e, 15), unlinked, AuditActions.SUGGESTION_DECIDED, UUID.randomUUID().toString(), "ru", "status" to "REJECTED", "via" to "EDITOR") }
            (1..3).forEach { audit(tashkent(e, 6), system, AuditActions.SUGGESTION_DECIDED, UUID.randomUUID().toString(), "ru", "status" to "ACCEPTED", "via" to "AUTO") }
            // E+1: worder restores a removed word in the panel.
            val ruOld = insertWord("ru", "старое", WordSource.BUNDLED, tashkent(e.minusDays(10), 9))
            audit(tashkent(e.plusDays(1), 10), worderPanel, AuditActions.WORD_RESTORED, ruOld, "ru", "text" to "старое")
        }
    }

    private class Actor(val kind: ActorKind, val staffId: String?)

    private fun audit(at: Instant, actor: Actor, action: String, targetId: String, lang: String, vararg details: Pair<String, String>) {
        StaffAuditLogTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[StaffAuditLogTable.at] = at
            it[actorKind] = actor.kind.name
            it[actorStaffId] = actor.staffId
            it[StaffAuditLogTable.action] = action
            it[targetType] = if (action == AuditActions.SUGGESTION_DECIDED) AuditTargets.SUGGESTION else AuditTargets.WORD
            it[StaffAuditLogTable.targetId] = targetId
            it[StaffAuditLogTable.lang] = lang
            it[StaffAuditLogTable.details] = JsonObject(details.associate { (k, v) -> k to JsonPrimitive(v) }).toString()
        }
    }

    /** Inside a transaction. */
    private fun insertWord(
        lang: String,
        text: String,
        source: WordSource,
        createdAt: Instant,
        status: WordStatus = WordStatus.ACTIVE,
        eligible: Boolean = false,
    ): String {
        val id = UUID.randomUUID().toString()
        WordsTable.insert {
            it[WordsTable.id] = id
            it[WordsTable.lang] = lang
            it[WordsTable.text] = text
            it[WordsTable.status] = status.name
            it[wordSource] = source.name
            it[suggestionId] = null
            it[dailyEligible] = eligible
            it[createdByStaffId] = null
            it[WordsTable.createdAt] = createdAt
            it[updatedByStaffId] = null
            it[updatedAt] = createdAt
            it[removedByStaffId] = null
            it[removedAt] = null
        }
        return id
    }

    /** An edit as the catalog does it: the row is respelled, a REMOVED tombstone keeps the old spelling. */
    private fun editWord(lang: String, id: String, from: String, to: String, createdAt: Instant, at: Instant, actor: Actor) {
        WordsTable.update({ WordsTable.id eq id }) { it[WordsTable.text] = to }
        insertWord(lang, from, WordSource.STAFF, createdAt, status = WordStatus.REMOVED)
        StaffAuditLogTable.insert {
            it[StaffAuditLogTable.id] = UUID.randomUUID().toString()
            it[StaffAuditLogTable.at] = at
            it[actorKind] = actor.kind.name
            it[actorStaffId] = actor.staffId
            it[action] = AuditActions.WORD_EDITED
            it[targetType] = AuditTargets.WORD
            it[targetId] = id
            it[StaffAuditLogTable.lang] = lang
            it[details] = """{"text":{"from":"$from","to":"$to"}}"""
        }
    }
}

/**
 * What the fixture must produce ([AnalyticsFixture]'s scenes, computed by hand). Result days are offsets from BASE,
 * event dates relative to E.
 */
object AnalyticsExpected {
    // Activity (day 0): English 4 players/games, Russian 1, DAU 4.
    const val ACTIVITY_EN_PLAYERS = 4
    const val ACTIVITY_RU_PLAYERS = 1
    const val ACTIVITY_DAU = 4

    // Outcomes (Kazakh, day 3): 5 games, wins 2:1 3:2 4:1, 1 loss, win rate 80%, average 3.0 attempts.
    const val OUTCOME_GAMES = 5
    const val OUTCOME_WINS = 4
    const val OUTCOME_LOSSES = 1
    val OUTCOME_DISTRIBUTION = listOf(0L, 1L, 2L, 1L, 0L, 0L)
    const val OUTCOME_WIN_RATE = 0.8
    const val OUTCOME_AVG_ATTEMPTS = 3.0

    // WAU/MAU on day 80: the d−6 account counts in both, the d−7 one only in MAU.
    const val WINDOW_DAU = 0
    const val WINDOW_WAU = 1
    const val WINDOW_MAU = 2

    // Streaks (English, day 115): 1 → loss-broken run, 2–6 → the 3-day run, 7–29 → the 9-day run ending yesterday,
    // 30+ → the 30-day run; the account whose last win was d−2 is not counted.
    val STREAK_BUCKETS = listOf(1L, 1L, 1L, 1L)

    // Retention (cohort day 5): 10 accounts, D1 4 (40%), D7 2 (20%), D30 1 (10%); cohort day 123: D30 unknown.
    const val COHORT_SIZE = 10
    const val COHORT_D1 = 4
    const val COHORT_D7 = 2
    const val COHORT_D30 = 1

    // Word difficulty: manual pick day 40 — 20 players, 15 wins (75%), 4.0 attempts; Uzbek day 42 — 10 players,
    // "kitob"/"китоб", 4.4 attempts; day 41 an automatic repeat; day 43 legacy "chair" without marker; day 44 "wharf".
    const val MANUAL_PLAYERS = 20
    const val MANUAL_WINS = 15
    const val MANUAL_AVG_ATTEMPTS = 4.0
    const val UZBEK_PLAYERS = 10
    const val UZBEK_AVG_ATTEMPTS = 4.4

    // Accounts (loadAccounts alone): 10 total, 5 linked, 4 Google, 2 Apple, 5 anonymous. New accounts: 3 on E, 1 on E+1
    // (the 23:30 Moscow account). Links: unknown on E, Google 1 / Apple 1 on E+1. Deletions: 2 on E.
    const val ACCOUNTS_TOTAL = 10
    const val ACCOUNTS_LINKED = 5
    const val ACCOUNTS_GOOGLE = 4
    const val ACCOUNTS_APPLE = 2
    const val ACCOUNTS_ANONYMOUS = 5
    const val NEW_ON_E = 3
    const val NEW_ON_E_PLUS_1 = 1
    const val DELETIONS_ON_E = 2

    /** The whole fixture's accounts (10 + 59 players). */
    const val FULL_ACCOUNTS_TOTAL = 69

    // Suggestions. Russian on E: 6 submitted, 3 automatic (median 120 s), 1 editor, 1 rejected (editor median 3 h),
    // backlog 2 (the pending one and the legacy one decided on E+1). Russian on E+1: 1 editor-accepted (legacy), backlog 1.
    // English backlog: 1 on E and E+1, 0 on E+2; editor median on E+5: 2 hours.
    const val RU_SUBMITTED = 6
    const val RU_AUTO = 3
    const val RU_EDITOR = 1
    const val RU_REJECTED = 1
    const val RU_BACKLOG = 2
    const val RU_MEDIAN_AUTO_SECONDS = 120.0
    const val RU_MEDIAN_EDITOR_SECONDS = 10_800.0
    const val EN_MEDIAN_EDITOR_E5_SECONDS = 7_200.0

    // Content on E: English 12 by staff (the edit's tombstone is not a new word) and 3 automatic, 1 removed, 1 restored;
    // Russian 40 by staff, 2 from suggestions, 1 removed, 1 restored. Active words captured on 2026-09-16: en 15, ru 43,
    // kk 30. Kazakh pool: 30, never used 7.
    const val EN_ADDED_STAFF = 12
    const val EN_ADDED_AUTO = 3
    const val RU_ADDED_STAFF = 40
    const val RU_ADDED_SUGGESTION = 2
    const val EN_ACTIVE = 15
    const val RU_ACTIVE = 43
    const val KK_ACTIVE = 30
    const val KK_POOL = 30
    const val KK_UNUSED = 7

    // Staff on E: worder (ru) 40 added, 2 edited, 1 removed, 5 decided; unlinked Telegram 2 decided; boss (en) 13 added
    // (12 + the restore), 1 removed. On E+1: worder 1 added (a restore), boss 1 edited.
    const val WORDER_ADDED = 40
    const val WORDER_EDITED = 2
    const val WORDER_REMOVED = 1
    const val WORDER_DECIDED = 5
    const val UNLINKED_DECIDED = 2
    const val BOSS_ADDED = 13
}
