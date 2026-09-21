package uz.abumme.harfgame.backend.admin.analytics

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.calendar.CalendarDays
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.AnalyticsAccountsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsCohortDayTable
import uz.abumme.harfgame.backend.db.AnalyticsContentDayTable
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsPoolDayTable
import uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable
import uz.abumme.harfgame.backend.db.AnalyticsStaffDayTable
import uz.abumme.harfgame.backend.db.AnalyticsSuggestionsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsWordDayTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.analytics.AccountsDayDto
import uz.abumme.harfgame.data.admin.analytics.AccountsDto
import uz.abumme.harfgame.data.admin.analytics.ActivityDto
import uz.abumme.harfgame.data.admin.analytics.ActivityLangDayDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsParams
import uz.abumme.harfgame.data.admin.analytics.AnalyticsRange
import uz.abumme.harfgame.data.admin.analytics.CalendarContentDto
import uz.abumme.harfgame.data.admin.analytics.CohortDto
import uz.abumme.harfgame.data.admin.analytics.ContentCurrentDto
import uz.abumme.harfgame.data.admin.analytics.ContentDayDto
import uz.abumme.harfgame.data.admin.analytics.ContentDto
import uz.abumme.harfgame.data.admin.analytics.DayRangeDto
import uz.abumme.harfgame.data.admin.analytics.GlobalDayDto
import uz.abumme.harfgame.data.admin.analytics.KpiDto
import uz.abumme.harfgame.data.admin.analytics.OutcomeDayDto
import uz.abumme.harfgame.data.admin.analytics.OutcomeTotalsDto
import uz.abumme.harfgame.data.admin.analytics.OutcomesDto
import uz.abumme.harfgame.data.admin.analytics.OverviewDto
import uz.abumme.harfgame.data.admin.analytics.PoolDayDto
import uz.abumme.harfgame.data.admin.analytics.RetentionDto
import uz.abumme.harfgame.data.admin.analytics.StaffActivityDto
import uz.abumme.harfgame.data.admin.analytics.StaffDayDto
import uz.abumme.harfgame.data.admin.analytics.StreakDayDto
import uz.abumme.harfgame.data.admin.analytics.StreaksDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionDayDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionsAnalyticsDto
import uz.abumme.harfgame.data.admin.analytics.TodaySoFarDto
import uz.abumme.harfgame.data.admin.analytics.WordDayDto
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import uz.abumme.harfgame.data.admin.analytics.WordSort
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The validated parameters of one analytics request. Days are inclusive. */
data class AnalyticsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val lang: String? = null,
    val calendar: String? = null,
    val sort: WordSort = WordSort.DAY,
    val descending: Boolean = true,
) {
    val fromDay: Long get() = from.toEpochDay()
    val toDay: Long get() = to.toEpochDay()
    val range: DayRangeDto get() = DayRangeDto(from.toString(), to.toString())

    companion object {
        /**
         * Parses query [parameters]; refuses with `422 validation_failed` naming the first bad one. The range is checked
         * before anything else and needs no query; without `from`/`to` it is the [AnalyticsParams.DEFAULT_DAYS] days
         * ending with the last day closed in every language at [now].
         */
        fun parse(parameters: Map<String, String>, packLanguages: Collection<String>, now: Instant): AnalyticsQuery {
            fun date(name: String): LocalDate? = parameters[name]?.takeIf { it.isNotBlank() }?.let {
                try {
                    LocalDate.parse(it.trim())
                } catch (e: DateTimeParseException) {
                    throw AdminApiException.validation(name, FieldReasons.INVALID)
                }
            }
            val to = date(AnalyticsParams.TO) ?: LocalDate.ofEpochDay(AnalyticsDays.closedGlobalPuzzleDay(now))
            val from = date(AnalyticsParams.FROM) ?: to.minusDays(AnalyticsParams.DEFAULT_DAYS - 1L)
            AnalyticsRange.problem(
                kotlinx.datetime.LocalDate.fromEpochDays(from.toEpochDay().toInt()),
                kotlinx.datetime.LocalDate.fromEpochDays(to.toEpochDay().toInt()),
            )?.let { throw AdminApiException.validation(it.field, it.reason) }

            val lang = parameters[AnalyticsParams.LANG]?.takeIf { it.isNotBlank() }
            if (lang != null && lang !in packLanguages) throw AdminApiException.validation(AnalyticsParams.LANG, FieldReasons.UNKNOWN)
            val calendar = parameters[AnalyticsParams.CALENDAR]?.takeIf { it.isNotBlank() }
            if (calendar != null && !DailyCalendars.isCalendar(calendar)) {
                throw AdminApiException.validation(AnalyticsParams.CALENDAR, FieldReasons.UNKNOWN)
            }
            val sort = parameters[AnalyticsParams.SORT]?.takeIf { it.isNotBlank() }
                ?.let { WordSort.fromParam(it) ?: throw AdminApiException.validation(AnalyticsParams.SORT, FieldReasons.INVALID) }
                ?: WordSort.DAY
            val descending = when (parameters[AnalyticsParams.DIR]?.takeIf { it.isNotBlank() }) {
                null, AnalyticsParams.DIR_DESC -> true
                AnalyticsParams.DIR_ASC -> false
                else -> throw AdminApiException.validation(AnalyticsParams.DIR, FieldReasons.INVALID)
            }
            return AnalyticsQuery(from, to, lang, calendar, sort, descending)
        }
    }
}

/**
 * The dashboard's reads. Every series comes from the rollup tables (at most one row per day of the range per series),
 * with each day's `provisional` flag from `analytics_rollup_days`; only today's partial players and games, and the
 * current catalog and pool values, are read live. Each call is one transaction with a statement timeout.
 */
class AnalyticsService(private val catalog: WordCatalogService, private val clock: Clock) {
    private val computations = RollupComputations(catalog)

    suspend fun parse(parameters: Map<String, String>): AnalyticsQuery {
        val now = clock.instant()
        // The range is refused before the languages are even read.
        AnalyticsQuery.parse(parameters - AnalyticsParams.LANG, emptyList(), now)
        return AnalyticsQuery.parse(parameters, packLanguages(), now)
    }

    suspend fun packLanguages(): List<String> = DatabaseFactory.dbQuery {
        WordPacksTable.select(WordPacksTable.lang).map { it[WordPacksTable.lang] }.sorted()
    }

    suspend fun overview(): OverviewDto = read {
        val now = clock.instant()
        val day = AnalyticsDays.closedGlobalPuzzleDay(now)
        val date = AnalyticsDays.closedEventDates(now)
        val global = AnalyticsGlobalDayTable.selectAll().where { AnalyticsGlobalDayTable.puzzleDay.between(day - 1, day) }
            .associateBy { it[AnalyticsGlobalDayTable.puzzleDay] }
        val langRows = AnalyticsLangDayTable.selectAll().where { AnalyticsLangDayTable.puzzleDay.between(day - 1, day) }.toList()
        val games = langRows.groupBy { it[AnalyticsLangDayTable.puzzleDay] }
            .mapValues { (_, rows) -> rows.sumOf { it[AnalyticsLangDayTable.games].toLong() } to rows.sumOf { it[AnalyticsLangDayTable.wins].toLong() } }
        val accounts = AnalyticsAccountsDayTable.selectAll()
            .where { AnalyticsAccountsDayTable.date.between(date.minusDays(1), date) }
            .associateBy { it[AnalyticsAccountsDayTable.date] }
        val backlog = AnalyticsSuggestionsDayTable.selectAll()
            .where { AnalyticsSuggestionsDayTable.date.between(date.minusDays(1), date) }
            .groupBy { it[AnalyticsSuggestionsDayTable.date] }
            .mapValues { (_, rows) -> rows.sumOf { it[AnalyticsSuggestionsDayTable.backlogEnd].toLong() } }
        val globalProvisional = provisional(RollupGroup.Global.key, day - 1, day)
        val languages = WordPacksTable.select(WordPacksTable.lang).map { it[WordPacksTable.lang] }.sorted()
        val langProvisional = languages.any { provisional(RollupGroup.Lang(it).key, day, day)[day] != false }
        val eventsProvisional = provisional(RollupGroup.Events.key, date.toEpochDay(), date.toEpochDay())[date.toEpochDay()] != false

        fun resultKpi(value: (Long) -> Double?, isProvisional: Boolean) = KpiDto(
            day = LocalDate.ofEpochDay(day).toString(),
            value = value(day),
            previousDay = LocalDate.ofEpochDay(day - 1).toString(),
            previous = value(day - 1),
            provisional = isProvisional,
        )
        fun eventKpi(value: (LocalDate) -> Double?) = KpiDto(
            day = date.toString(),
            value = value(date),
            previousDay = date.minusDays(1).toString(),
            previous = value(date.minusDays(1)),
            provisional = eventsProvisional,
        )
        val globalFlag = globalProvisional[day] != false
        OverviewDto(
            dau = resultKpi({ global[it]?.get(AnalyticsGlobalDayTable.dau)?.toDouble() }, globalFlag),
            wau = resultKpi({ global[it]?.get(AnalyticsGlobalDayTable.wau)?.toDouble() }, globalFlag),
            mau = resultKpi({ global[it]?.get(AnalyticsGlobalDayTable.mau)?.toDouble() }, globalFlag),
            games = resultKpi({ games[it]?.first?.toDouble() }, langProvisional),
            winRate = resultKpi({ games[it]?.let { (played, won) -> if (played == 0L) null else won.toDouble() / played } }, langProvisional),
            newAccounts = eventKpi { accounts[it]?.get(AnalyticsAccountsDayTable.newAccounts)?.toDouble() },
            totalAccounts = eventKpi { accounts[it]?.get(AnalyticsAccountsDayTable.total)?.toDouble() },
            backlog = eventKpi { backlog[it]?.toDouble() },
            today = today(languages, now),
        )
    }

    suspend fun accounts(query: AnalyticsQuery): AccountsDto = read {
        val flags = provisional(RollupGroup.Events.key, query.fromDay, query.toDay)
        val days = AnalyticsAccountsDayTable.selectAll()
            .where { AnalyticsAccountsDayTable.date.between(query.from, query.to) }
            .orderBy(AnalyticsAccountsDayTable.date)
            .map { row ->
                val total = row[AnalyticsAccountsDayTable.total]
                val linked = row[AnalyticsAccountsDayTable.linked]
                AccountsDayDto(
                    date = row[AnalyticsAccountsDayTable.date].toString(),
                    newAccounts = row[AnalyticsAccountsDayTable.newAccounts].toLong(),
                    linksGoogle = row[AnalyticsAccountsDayTable.linksGoogle]?.toLong(),
                    linksApple = row[AnalyticsAccountsDayTable.linksApple]?.toLong(),
                    deletions = row[AnalyticsAccountsDayTable.deletions].toLong(),
                    total = total?.toLong(),
                    linked = linked?.toLong(),
                    googleLinked = row[AnalyticsAccountsDayTable.googleLinked]?.toLong(),
                    appleLinked = row[AnalyticsAccountsDayTable.appleLinked]?.toLong(),
                    anonymous = if (total != null && linked != null) (total - linked).toLong() else null,
                    provisional = flags.isProvisional(row[AnalyticsAccountsDayTable.date].toEpochDay()),
                )
            }
        AccountsDto(query.range, languageFiltered = false, days = days)
    }

    suspend fun activity(query: AnalyticsQuery): ActivityDto = read {
        val now = clock.instant()
        val rows = langRows(query)
        val languages = rows.map { it.lang }.distinct()
        val langFlags = languages.associateWith { provisional(RollupGroup.Lang(it).key, query.fromDay, query.toDay) }
        val globalFlags = provisional(RollupGroup.Global.key, query.fromDay, query.toDay)
        val global = AnalyticsGlobalDayTable.selectAll()
            .where { AnalyticsGlobalDayTable.puzzleDay.between(query.fromDay, query.toDay) }
            .orderBy(AnalyticsGlobalDayTable.puzzleDay)
            .map {
                val day = it[AnalyticsGlobalDayTable.puzzleDay]
                GlobalDayDto(
                    day = LocalDate.ofEpochDay(day).toString(),
                    dau = it[AnalyticsGlobalDayTable.dau].toLong(),
                    wau = it[AnalyticsGlobalDayTable.wau].toLong(),
                    mau = it[AnalyticsGlobalDayTable.mau].toLong(),
                    provisional = globalFlags.isProvisional(day),
                )
            }
        val todayLanguages = query.lang?.let(::listOf)
            ?: WordPacksTable.select(WordPacksTable.lang).map { it[WordPacksTable.lang] }.sorted()
        ActivityDto(
            range = query.range,
            lang = query.lang,
            languages = rows.map {
                ActivityLangDayDto(it.lang, it.dayString, it.players, it.games, langFlags.getValue(it.lang).isProvisional(it.day))
            },
            global = global,
            languageFiltered = false,
            today = today(todayLanguages, now),
        )
    }

    suspend fun retention(query: AnalyticsQuery): RetentionDto = read {
        val flags = provisional(RollupGroup.Cohort.key, query.fromDay, query.toDay)
        val cohorts = AnalyticsCohortDayTable.selectAll()
            .where { AnalyticsCohortDayTable.cohortDay.between(query.fromDay, query.toDay) }
            .orderBy(AnalyticsCohortDayTable.cohortDay)
            .map {
                val day = it[AnalyticsCohortDayTable.cohortDay]
                CohortDto(
                    day = LocalDate.ofEpochDay(day).toString(),
                    size = it[AnalyticsCohortDayTable.size].toLong(),
                    d1 = it[AnalyticsCohortDayTable.d1]?.toLong(),
                    d7 = it[AnalyticsCohortDayTable.d7]?.toLong(),
                    d30 = it[AnalyticsCohortDayTable.d30]?.toLong(),
                    provisional = flags.isProvisional(day),
                )
            }
        RetentionDto(query.range, languageFiltered = false, cohorts = cohorts)
    }

    suspend fun streaks(query: AnalyticsQuery): StreaksDto = read {
        val rows = langRows(query)
        val flags = rows.map { it.lang }.distinct().associateWith { provisional(RollupGroup.Lang(it).key, query.fromDay, query.toDay) }
        StreaksDto(
            range = query.range,
            lang = query.lang,
            days = rows.map {
                StreakDayDto(it.lang, it.dayString, it.streaks[0], it.streaks[1], it.streaks[2], it.streaks[3], flags.getValue(it.lang).isProvisional(it.day))
            },
        )
    }

    suspend fun outcomes(query: AnalyticsQuery): OutcomesDto = read {
        val rows = langRows(query)
        val flags = rows.map { it.lang }.distinct().associateWith { provisional(RollupGroup.Lang(it).key, query.fromDay, query.toDay) }
        val days = rows.map {
            OutcomeDayDto(
                lang = it.lang,
                day = it.dayString,
                games = it.games,
                wins = it.wins,
                losses = it.games - it.wins,
                distribution = it.distribution,
                winRate = rate(it.wins, it.games),
                avgAttempts = rate(it.attemptsSumWon, it.wins),
                provisional = flags.getValue(it.lang).isProvisional(it.day),
            )
        }
        fun totals(lang: String?, subset: List<LangDayRow>) = OutcomeTotalsDto(
            lang = lang,
            games = subset.sumOf { it.games },
            wins = subset.sumOf { it.wins },
            losses = subset.sumOf { it.games - it.wins },
            distribution = (0 until DISTRIBUTION_SIZE).map { i -> subset.sumOf { it.distribution[i] } },
            winRate = rate(subset.sumOf { it.wins }, subset.sumOf { it.games }),
            avgAttempts = rate(subset.sumOf { it.attemptsSumWon }, subset.sumOf { it.wins }),
            provisional = subset.any { flags.getValue(it.lang).isProvisional(it.day) },
        )
        val perLanguage = rows.groupBy { it.lang }.toSortedMap().map { (lang, subset) -> totals(lang, subset) }
        OutcomesDto(
            range = query.range,
            lang = query.lang,
            days = days,
            totals = if (query.lang != null) perLanguage else perLanguage + totals(null, rows),
        )
    }

    suspend fun words(query: AnalyticsQuery): WordDifficultyDto = read {
        val calendars = query.calendar?.let(::listOf) ?: DailyCalendars.ALL
        val flags = calendars.associateWith { provisional(RollupGroup.Word(it).key, query.fromDay, query.toDay) }
        val rows = AnalyticsWordDayTable.selectAll()
            .where { (AnalyticsWordDayTable.calendar inList calendars) and AnalyticsWordDayTable.puzzleDay.between(query.fromDay, query.toDay) }
            .map {
                val day = it[AnalyticsWordDayTable.puzzleDay]
                val calendar = it[AnalyticsWordDayTable.calendar]
                val players = it[AnalyticsWordDayTable.players].toLong()
                val wins = it[AnalyticsWordDayTable.wins].toLong()
                WordDayDto(
                    calendar = calendar,
                    day = LocalDate.ofEpochDay(day).toString(),
                    word = it[AnalyticsWordDayTable.word],
                    wordCyrl = it[AnalyticsWordDayTable.wordCyrl],
                    marker = it[AnalyticsWordDayTable.daySource]?.let { source -> DaySource.valueOf(source) },
                    repeat = it[AnalyticsWordDayTable.isRepeat],
                    players = players,
                    wins = wins,
                    winRate = rate(wins, players),
                    avgAttempts = rate(it[AnalyticsWordDayTable.attemptsSumWon], wins),
                    provisional = flags.getValue(calendar).isProvisional(day),
                )
            }
        WordDifficultyDto(
            range = query.range,
            calendar = query.calendar,
            sort = query.sort.param,
            dir = if (query.descending) AnalyticsParams.DIR_DESC else AnalyticsParams.DIR_ASC,
            rows = sortWords(rows, query.sort, query.descending),
        )
    }

    suspend fun suggestions(query: AnalyticsQuery): SuggestionsAnalyticsDto = read {
        val flags = provisional(RollupGroup.Events.key, query.fromDay, query.toDay)
        val days = AnalyticsSuggestionsDayTable.selectAll()
            .where {
                val range = AnalyticsSuggestionsDayTable.date.between(query.from, query.to)
                if (query.lang == null) range else range and (AnalyticsSuggestionsDayTable.lang eq query.lang)
            }
            .orderBy(AnalyticsSuggestionsDayTable.date to SortOrder.ASC, AnalyticsSuggestionsDayTable.lang to SortOrder.ASC)
            .map {
                val date = it[AnalyticsSuggestionsDayTable.date]
                SuggestionDayDto(
                    date = date.toString(),
                    lang = it[AnalyticsSuggestionsDayTable.lang],
                    submitted = it[AnalyticsSuggestionsDayTable.submitted].toLong(),
                    autoAccepted = it[AnalyticsSuggestionsDayTable.autoAccepted].toLong(),
                    editorAccepted = it[AnalyticsSuggestionsDayTable.editorAccepted].toLong(),
                    rejected = it[AnalyticsSuggestionsDayTable.rejected].toLong(),
                    backlogEnd = it[AnalyticsSuggestionsDayTable.backlogEnd].toLong(),
                    medianAutoSeconds = it[AnalyticsSuggestionsDayTable.medianAutoSeconds],
                    medianEditorSeconds = it[AnalyticsSuggestionsDayTable.medianEditorSeconds],
                    provisional = flags.isProvisional(date.toEpochDay()),
                )
            }
        SuggestionsAnalyticsDto(query.range, query.lang, days)
    }

    suspend fun content(query: AnalyticsQuery): ContentDto = read {
        val now = clock.instant()
        val flags = provisional(RollupGroup.Events.key, query.fromDay, query.toDay)
        val calendars = query.lang?.let { listOfNotNull(DailyCalendars.calendarOf(it)) } ?: DailyCalendars.ALL
        val days = AnalyticsContentDayTable.selectAll()
            .where {
                val range = AnalyticsContentDayTable.date.between(query.from, query.to)
                if (query.lang == null) range else range and (AnalyticsContentDayTable.lang eq query.lang)
            }
            .orderBy(AnalyticsContentDayTable.date to SortOrder.ASC, AnalyticsContentDayTable.lang to SortOrder.ASC)
            .map {
                val date = it[AnalyticsContentDayTable.date]
                ContentDayDto(
                    date = date.toString(),
                    lang = it[AnalyticsContentDayTable.lang],
                    activeWords = it[AnalyticsContentDayTable.activeWords]?.toLong(),
                    addedBundled = it[AnalyticsContentDayTable.addedBundled].toLong(),
                    addedSuggestion = it[AnalyticsContentDayTable.addedSuggestion].toLong(),
                    addedAuto = it[AnalyticsContentDayTable.addedAuto].toLong(),
                    addedStaff = it[AnalyticsContentDayTable.addedStaff].toLong(),
                    removed = it[AnalyticsContentDayTable.removed].toLong(),
                    restored = it[AnalyticsContentDayTable.restored].toLong(),
                    provisional = flags.isProvisional(date.toEpochDay()),
                )
            }
        val pool = if (calendars.isEmpty()) emptyList() else AnalyticsPoolDayTable.selectAll()
            .where { AnalyticsPoolDayTable.date.between(query.from, query.to) and (AnalyticsPoolDayTable.calendar inList calendars) }
            .orderBy(AnalyticsPoolDayTable.date to SortOrder.ASC, AnalyticsPoolDayTable.calendar to SortOrder.ASC)
            .map {
                val date = it[AnalyticsPoolDayTable.date]
                PoolDayDto(
                    date = date.toString(),
                    calendar = it[AnalyticsPoolDayTable.calendar],
                    poolSize = it[AnalyticsPoolDayTable.poolSize]?.toLong(),
                    unusedLeft = it[AnalyticsPoolDayTable.unusedLeft]?.toLong(),
                    provisional = flags.isProvisional(date.toEpochDay()),
                )
            }
        val current = AnalyticsSql.query(
            "SELECT lang, count(*) AS n FROM words WHERE status = :active GROUP BY lang ORDER BY lang",
            mapOf("active" to WordStatus.ACTIVE.name),
        ) { rs -> ContentCurrentDto(rs.getString("lang"), rs.getLong("n")) }
            .filter { query.lang == null || it.lang == query.lang }
        val calendarValues = calendars.map { calendar ->
            val today = CalendarDays.today(calendar, now)
            val (poolSize, unusedLeft) = computations.currentPool(calendar, now)
            val repeats = AnalyticsSql.query(
                """
                SELECT count(*) FILTER (WHERE day BETWEEN :from AND :last) AS past,
                       count(*) FILTER (WHERE day > :today) AS upcoming
                FROM daily_words
                WHERE calendar = :calendar AND is_repeat
                """.trimIndent(),
                mapOf("from" to query.from, "last" to minOf(query.to, today), "today" to today, "calendar" to calendar),
            ) { rs -> rs.getLong("past") to rs.getLong("upcoming") }.single()
            CalendarContentDto(calendar, poolSize?.toLong(), unusedLeft?.toLong(), repeats.first, repeats.second)
        }
        ContentDto(query.range, query.lang, days, pool, current, calendarValues)
    }

    suspend fun staff(query: AnalyticsQuery): StaffActivityDto = read {
        val flags = provisional(RollupGroup.Events.key, query.fromDay, query.toDay)
        val rows = AnalyticsStaffDayTable.selectAll()
            .where {
                val range = AnalyticsStaffDayTable.date.between(query.from, query.to)
                if (query.lang == null) range else range and (AnalyticsStaffDayTable.lang eq query.lang)
            }
            .toList()
        val staffIds = rows.map { it[AnalyticsStaffDayTable.staffKey] }.filter { it != RollupComputations.TELEGRAM_UNLINKED }.distinct()
        val refs = if (staffIds.isEmpty()) emptyMap() else StaffTable.select(StaffTable.id, StaffTable.username, StaffTable.displayName)
            .where { StaffTable.id inList staffIds }
            .associate { it[StaffTable.id] to StaffRefDto(it[StaffTable.id], it[StaffTable.username], it[StaffTable.displayName]) }
        val items = rows.map {
            val key = it[AnalyticsStaffDayTable.staffKey]
            val date = it[AnalyticsStaffDayTable.date]
            val unlinked = key == RollupComputations.TELEGRAM_UNLINKED
            StaffDayDto(
                date = date.toString(),
                staff = if (unlinked) null else refs[key] ?: StaffRefDto(key, key),
                telegramUnlinked = unlinked,
                lang = it[AnalyticsStaffDayTable.lang],
                added = it[AnalyticsStaffDayTable.added].toLong(),
                edited = it[AnalyticsStaffDayTable.edited].toLong(),
                removed = it[AnalyticsStaffDayTable.removed].toLong(),
                decided = it[AnalyticsStaffDayTable.decided].toLong(),
                provisional = flags.isProvisional(date.toEpochDay()),
            )
        }.sortedWith(compareBy<StaffDayDto>({ it.date }, { it.staff?.username ?: "" }, { it.lang }))
        StaffActivityDto(query.range, query.lang, items)
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    /** One `analytics_lang_day` row, read once for the activity, streak and outcome views. */
    private class LangDayRow(
        val lang: String,
        val day: Long,
        val players: Long,
        val games: Long,
        val wins: Long,
        val distribution: List<Long>,
        val attemptsSumWon: Long,
        val streaks: List<Long>,
    ) {
        val dayString: String get() = LocalDate.ofEpochDay(day).toString()
    }

    private fun langRows(query: AnalyticsQuery): List<LangDayRow> =
        AnalyticsLangDayTable.selectAll()
            .where {
                val range = AnalyticsLangDayTable.puzzleDay.between(query.fromDay, query.toDay)
                if (query.lang == null) range else range and (AnalyticsLangDayTable.lang eq query.lang)
            }
            .orderBy(AnalyticsLangDayTable.puzzleDay to SortOrder.ASC, AnalyticsLangDayTable.lang to SortOrder.ASC)
            .map {
                LangDayRow(
                    lang = it[AnalyticsLangDayTable.lang],
                    day = it[AnalyticsLangDayTable.puzzleDay],
                    players = it[AnalyticsLangDayTable.players].toLong(),
                    games = it[AnalyticsLangDayTable.games].toLong(),
                    wins = it[AnalyticsLangDayTable.wins].toLong(),
                    distribution = listOf(
                        AnalyticsLangDayTable.won1, AnalyticsLangDayTable.won2, AnalyticsLangDayTable.won3,
                        AnalyticsLangDayTable.won4, AnalyticsLangDayTable.won5, AnalyticsLangDayTable.won6,
                    ).map { column -> it[column].toLong() },
                    attemptsSumWon = it[AnalyticsLangDayTable.attemptsSumWon],
                    streaks = listOf(
                        AnalyticsLangDayTable.streak1, AnalyticsLangDayTable.streak2to6,
                        AnalyticsLangDayTable.streak7to29, AnalyticsLangDayTable.streak30plus,
                    ).map { column -> it[column].toLong() },
                )
            }

    /** Day -> whether it is still provisional, for [group] over [fromDay]..[toDay]. */
    private fun provisional(group: String, fromDay: Long, toDay: Long): Map<Long, Boolean> =
        AnalyticsRollupDaysTable.selectAll()
            .where { (AnalyticsRollupDaysTable.grp eq group) and AnalyticsRollupDaysTable.day.between(fromDay, toDay) }
            .associate { it[AnalyticsRollupDaysTable.day] to !it[AnalyticsRollupDaysTable.final] }

    /** A day without bookkeeping is treated as provisional (it cannot be final). */
    private fun Map<Long, Boolean>.isProvisional(day: Long): Boolean = this[day] ?: true

    /** Players and games of each language's current puzzle day so far: a live count on the `(lang, puzzle_day)` index. */
    private fun today(languages: List<String>, now: Instant): List<TodaySoFarDto> = languages.map { lang ->
        val day = AnalyticsDays.puzzleToday(lang, now)
        val count = AnalyticsSql.query(
            "SELECT count(*) AS n FROM game_results WHERE lang = :lang AND puzzle_day = :day",
            mapOf("lang" to lang, "day" to day),
        ) { it.getLong("n") }.single()
        TodaySoFarDto(lang, LocalDate.ofEpochDay(day).toString(), players = count, games = count, partial = true)
    }

    private suspend fun <T> read(block: () -> T): T = DatabaseFactory.dbQuery {
        AnalyticsSql.limitStatementTime()
        block()
    }

    companion object {
        private const val DISTRIBUTION_SIZE = 6

        /** [numerator] / [denominator], or null when there is nothing to divide by. */
        fun rate(numerator: Long, denominator: Long): Double? = if (denominator == 0L) null else numerator.toDouble() / denominator

        /** Word difficulty order; values without a rate sort last either way, ties newest day first. */
        fun sortWords(rows: List<WordDayDto>, sort: WordSort, descending: Boolean): List<WordDayDto> {
            val newestFirst = compareByDescending<WordDayDto> { it.day }.thenBy { it.calendar }
            if (sort == WordSort.DAY) return if (descending) rows.sortedWith(newestFirst) else rows.sortedWith(compareBy<WordDayDto> { it.day }.thenBy { it.calendar })
            val key: (WordDayDto) -> Double? = when (sort) {
                WordSort.PLAYERS -> { row -> row.players.toDouble() }
                WordSort.WIN_RATE -> WordDayDto::winRate
                WordSort.AVG_ATTEMPTS -> WordDayDto::avgAttempts
                WordSort.DAY -> error("handled above")
            }
            val (known, unknown) = rows.partition { key(it) != null }
            val byValue = compareBy<WordDayDto> { key(it)!! }
            val ordered = known.sortedWith((if (descending) byValue.reversed() else byValue).then(newestFirst))
            return ordered + unknown.sortedWith(newestFirst)
        }
    }
}
