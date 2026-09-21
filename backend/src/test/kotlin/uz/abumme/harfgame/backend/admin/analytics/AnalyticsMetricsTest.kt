package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.E
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.NOW
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.day
import uz.abumme.harfgame.backend.db.AnalyticsAccountsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsCohortDayTable
import uz.abumme.harfgame.backend.db.AnalyticsContentDayTable
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsPoolDayTable
import uz.abumme.harfgame.backend.db.AnalyticsStaffDayTable
import uz.abumme.harfgame.backend.db.AnalyticsSuggestionsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsWordDayTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.GameResultsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.stats.Streaks
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every metric computed by the rollup job over [AnalyticsFixture], against [AnalyticsExpected]. The fixture is loaded
 * and rolled up once for the class (the tests only read).
 */
class AnalyticsMetricsTest {
    private val clock = MutableClock(NOW)
    private val service = AnalyticsService(analyticsCatalog(clock), clock)

    @BeforeTest
    fun setup() {
        if (!loaded || rowOf(UsersTable) { id eq AnalyticsFixture.player(69) } == null || rowOf(AnalyticsLangDayTable) { (lang eq "kk") and (puzzleDay eq day(AnalyticsFixture.OUTCOMES)) } == null) {
            resetAnalyticsData()
            AnalyticsFixture.load()
            rollupJob(clock).run(NOW)
            loaded = true
        }
    }

    private fun langDay(lang: String, offset: Int) = rowOf(AnalyticsLangDayTable) { (AnalyticsLangDayTable.lang eq lang) and (puzzleDay eq day(offset)) }!!
    private fun globalDay(offset: Int) = rowOf(AnalyticsGlobalDayTable) { puzzleDay eq day(offset) }!!
    private fun wordDay(calendar: String, offset: Int) = rowOf(AnalyticsWordDayTable) { (AnalyticsWordDayTable.calendar eq calendar) and (puzzleDay eq day(offset)) }!!
    private fun suggestionsDay(lang: String, date: LocalDate) = rowOf(AnalyticsSuggestionsDayTable) { (AnalyticsSuggestionsDayTable.lang eq lang) and (AnalyticsSuggestionsDayTable.date eq date) }!!
    private fun contentDay(lang: String, date: LocalDate) = rowOf(AnalyticsContentDayTable) { (AnalyticsContentDayTable.lang eq lang) and (AnalyticsContentDayTable.date eq date) }!!
    private fun query(from: LocalDate, to: LocalDate, lang: String? = null) = AnalyticsQuery(from, to, lang)

    @Test
    fun theFixtureLoads() {
        transaction(DatabaseFactory.init()) {
            assertEquals(AnalyticsExpected.FULL_ACCOUNTS_TOTAL.toLong(), UsersTable.selectAll().count())
            assertEquals(11, WordSuggestionsTable.selectAll().count())
            assertTrue(GameResultsTable.selectAll().count() > 100)
        }
    }

    // --- 5.2 players, games, outcomes, DAU/WAU/MAU -----------------------------------------------------------------

    @Test
    fun playersAndGamesPerLanguageAndDau() {
        with(langDay("en", AnalyticsFixture.ACTIVITY)) {
            assertEquals(AnalyticsExpected.ACTIVITY_EN_PLAYERS, this[AnalyticsLangDayTable.players])
            assertEquals(AnalyticsExpected.ACTIVITY_EN_PLAYERS, this[AnalyticsLangDayTable.games])
        }
        assertEquals(AnalyticsExpected.ACTIVITY_RU_PLAYERS, langDay("ru", AnalyticsFixture.ACTIVITY)[AnalyticsLangDayTable.players])
        assertEquals(AnalyticsExpected.ACTIVITY_DAU, globalDay(AnalyticsFixture.ACTIVITY)[AnalyticsGlobalDayTable.dau])
    }

    @Test
    fun outcomesOfALanguageDay() {
        val row = langDay("kk", AnalyticsFixture.OUTCOMES)
        assertEquals(AnalyticsExpected.OUTCOME_GAMES, row[AnalyticsLangDayTable.games])
        assertEquals(AnalyticsExpected.OUTCOME_WINS, row[AnalyticsLangDayTable.wins])
        assertEquals(AnalyticsExpected.OUTCOME_LOSSES, row[AnalyticsLangDayTable.losses])

        val outcomes = runBlocking { service.outcomes(query(AnalyticsFixture.date(AnalyticsFixture.OUTCOMES), AnalyticsFixture.date(AnalyticsFixture.OUTCOMES), "kk")) }
        val dayRow = outcomes.days.single()
        assertEquals(AnalyticsExpected.OUTCOME_DISTRIBUTION, dayRow.distribution)
        assertEquals(AnalyticsExpected.OUTCOME_WIN_RATE, dayRow.winRate)
        assertEquals(AnalyticsExpected.OUTCOME_AVG_ATTEMPTS, dayRow.avgAttempts)
        assertEquals(1L, dayRow.losses)
        assertFalse(dayRow.provisional)
        assertEquals(listOf("kk"), outcomes.totals.map { it.lang })
        assertEquals(AnalyticsExpected.OUTCOME_DISTRIBUTION, outcomes.totals.single().distribution)
    }

    @Test
    fun wauAndMauWindowEdges() {
        val row = globalDay(AnalyticsFixture.WINDOW)
        assertEquals(AnalyticsExpected.WINDOW_DAU, row[AnalyticsGlobalDayTable.dau])
        assertEquals(AnalyticsExpected.WINDOW_WAU, row[AnalyticsGlobalDayTable.wau])
        assertEquals(AnalyticsExpected.WINDOW_MAU, row[AnalyticsGlobalDayTable.mau])
        // The day after, the d−6 account falls out of the 7-day window: d−7 relative to day 81.
        assertEquals(0, globalDay(AnalyticsFixture.WINDOW + 1)[AnalyticsGlobalDayTable.wau])
    }

    // --- 5.3 streaks -------------------------------------------------------------------------------------------------

    @Test
    fun streakBucketsFollowTheAppsRule() {
        val row = langDay("en", AnalyticsFixture.STREAK)
        assertEquals(
            AnalyticsExpected.STREAK_BUCKETS,
            listOf(AnalyticsLangDayTable.streak1, AnalyticsLangDayTable.streak2to6, AnalyticsLangDayTable.streak7to29, AnalyticsLangDayTable.streak30plus).map { row[it].toLong() },
        )
    }

    @Test
    fun streakBucketsMatchTheSharedStreaksRuleOnEveryDayAroundTheScene() {
        val records: Map<String, List<ResultRecordDto>> = transaction(DatabaseFactory.init()) {
            GameResultsTable.selectAll().where { GameResultsTable.lang eq "en" }
                .groupBy({ it[GameResultsTable.userId] }, { ResultRecordDto("en", it[GameResultsTable.puzzleDay], it[GameResultsTable.won], it[GameResultsTable.attempts]) })
        }
        for (offset in (AnalyticsFixture.STREAK - 32)..(AnalyticsFixture.STREAK + 2)) {
            val d = day(offset)
            val expected = LongArray(4)
            records.values.forEach { list ->
                val current = Streaks.streak(list.filter { it.puzzleDay <= d }, "en", d).current
                when {
                    current == 0 -> Unit
                    current == 1 -> expected[0]++
                    current <= 6 -> expected[1]++
                    current <= 29 -> expected[2]++
                    else -> expected[3]++
                }
            }
            val row = langDay("en", offset)
            val actual = listOf(AnalyticsLangDayTable.streak1, AnalyticsLangDayTable.streak2to6, AnalyticsLangDayTable.streak7to29, AnalyticsLangDayTable.streak30plus).map { row[it].toLong() }
            assertEquals(expected.toList(), actual, "streak buckets on offset $offset")
        }
    }

    // --- 5.4 retention -----------------------------------------------------------------------------------------------

    @Test
    fun retentionByCohort() {
        val retention = runBlocking { service.retention(query(AnalyticsFixture.date(AnalyticsFixture.COHORT), AnalyticsFixture.date(AnalyticsFixture.COHORT))) }
        val cohort = retention.cohorts.single()
        assertEquals(AnalyticsExpected.COHORT_SIZE.toLong(), cohort.size)
        assertEquals(AnalyticsExpected.COHORT_D1.toLong(), cohort.d1)
        assertEquals(AnalyticsExpected.COHORT_D7.toLong(), cohort.d7)
        assertEquals(AnalyticsExpected.COHORT_D30.toLong(), cohort.d30)
        assertEquals(0.4, AnalyticsService.rate(cohort.d1!!, cohort.size))
        assertEquals(0.2, AnalyticsService.rate(cohort.d7!!, cohort.size))
        assertEquals(0.1, AnalyticsService.rate(cohort.d30!!, cohort.size))
        assertFalse(retention.languageFiltered)
        assertFalse(cohort.provisional)
    }

    @Test
    fun d30IsUnknownUntilItsTargetDayHasClosed() {
        val row = rowOf(AnalyticsCohortDayTable) { cohortDay eq day(AnalyticsFixture.LATE_COHORT) }!!
        assertEquals(1, row[AnalyticsCohortDayTable.size])
        assertEquals(1, row[AnalyticsCohortDayTable.d1])
        assertEquals(0, row[AnalyticsCohortDayTable.d7])
        assertNull(row[AnalyticsCohortDayTable.d30])
        // Not final until its D30 day is.
        assertFalse(rollupDay(RollupGroup.Cohort.key, day(AnalyticsFixture.LATE_COHORT))!![uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable.final])
    }

    // --- 5.5 word difficulty -----------------------------------------------------------------------------------------

    @Test
    fun wordDifficultyWithCalendarMarkers() {
        with(wordDay("en", AnalyticsFixture.MANUAL_PICK)) {
            assertEquals("apple", this[AnalyticsWordDayTable.word])
            assertEquals(DaySource.MANUAL.name, this[AnalyticsWordDayTable.daySource])
            assertEquals(AnalyticsExpected.MANUAL_PLAYERS, this[AnalyticsWordDayTable.players])
            assertEquals(AnalyticsExpected.MANUAL_WINS, this[AnalyticsWordDayTable.wins])
        }
        with(wordDay("uz", AnalyticsFixture.UZBEK)) {
            assertEquals("kitob", this[AnalyticsWordDayTable.word])
            assertEquals("китоб", this[AnalyticsWordDayTable.wordCyrl])
            assertEquals(AnalyticsExpected.UZBEK_PLAYERS, this[AnalyticsWordDayTable.players])
        }
        with(wordDay("en", AnalyticsFixture.REPEAT)) {
            assertTrue(this[AnalyticsWordDayTable.isRepeat])
            assertEquals(DaySource.AUTO.name, this[AnalyticsWordDayTable.daySource])
        }
        with(wordDay("en", AnalyticsFixture.LEGACY)) {
            assertEquals("chair", this[AnalyticsWordDayTable.word])
            assertNull(this[AnalyticsWordDayTable.daySource])
            assertFalse(this[AnalyticsWordDayTable.isRepeat])
        }
        with(wordDay("en", AnalyticsFixture.SCHEDULED)) {
            assertEquals("wharf", this[AnalyticsWordDayTable.word])
            assertNull(this[AnalyticsWordDayTable.daySource])
        }

        val words = runBlocking { service.words(query(AnalyticsFixture.date(AnalyticsFixture.MANUAL_PICK), AnalyticsFixture.date(AnalyticsFixture.SCHEDULED))) }
        val manual = words.rows.single { it.calendar == "en" && it.day == AnalyticsFixture.date(AnalyticsFixture.MANUAL_PICK).toString() }
        assertEquals(0.75, manual.winRate)
        assertEquals(AnalyticsExpected.MANUAL_AVG_ATTEMPTS, manual.avgAttempts)
        assertEquals(DaySource.MANUAL, manual.marker)
        val uzbek = words.rows.single { it.calendar == "uz" && it.day == AnalyticsFixture.date(AnalyticsFixture.UZBEK).toString() }
        assertEquals(10L, uzbek.players)
        assertEquals(AnalyticsExpected.UZBEK_AVG_ATTEMPTS, uzbek.avgAttempts!!, 1e-9)
        assertTrue(words.rows.single { it.calendar == "en" && it.day == AnalyticsFixture.date(AnalyticsFixture.REPEAT).toString() }.repeat)
    }

    // --- 5.6 accounts ------------------------------------------------------------------------------------------------

    @Test
    fun accountFlowsByTashkentDate() {
        val accounts = runBlocking { service.accounts(query(E.minusDays(1), E.plusDays(2))) }.days.associateBy { it.date }
        val onE = accounts.getValue(E.toString())
        assertEquals(AnalyticsExpected.NEW_ON_E.toLong(), onE.newAccounts)
        // Created at 23:30 in Moscow on E: counted on E+1, the Tashkent date.
        assertEquals(AnalyticsExpected.NEW_ON_E_PLUS_1.toLong(), accounts.getValue(E.plusDays(1).toString()).newAccounts)
        assertEquals(AnalyticsExpected.DELETIONS_ON_E.toLong(), onE.deletions)
        // Link times are recorded from E+1: E has no link data, not zero.
        assertNull(onE.linksGoogle)
        assertNull(onE.linksApple)
        assertEquals(1L, accounts.getValue(E.plusDays(1).toString()).linksGoogle)
        assertEquals(1L, accounts.getValue(E.plusDays(1).toString()).linksApple)
        assertEquals(0L, accounts.getValue(E.plusDays(2).toString()).linksGoogle)
        // First computed weeks after E: its end-of-day totals are unknown.
        assertNull(onE.total)
        assertNull(onE.anonymous)
        assertFalse(onE.provisional)
    }

    @Test
    fun endOfDayTotalsAreCapturedOnTheFirstComputationAfterADateCloses() {
        val lastClosed = AnalyticsDays.closedEventDates(NOW)
        val row = rowOf(AnalyticsAccountsDayTable) { date eq lastClosed }!!
        assertEquals(AnalyticsExpected.FULL_ACCOUNTS_TOTAL, row[AnalyticsAccountsDayTable.total])
        assertEquals(AnalyticsExpected.ACCOUNTS_LINKED, row[AnalyticsAccountsDayTable.linked])
        assertEquals(AnalyticsExpected.ACCOUNTS_GOOGLE, row[AnalyticsAccountsDayTable.googleLinked])
        assertEquals(AnalyticsExpected.ACCOUNTS_APPLE, row[AnalyticsAccountsDayTable.appleLinked])
    }

    // --- 5.7 suggestions ---------------------------------------------------------------------------------------------

    @Test
    fun dailySuggestionCountsAndMedians() {
        with(suggestionsDay("ru", E)) {
            assertEquals(AnalyticsExpected.RU_SUBMITTED, this[AnalyticsSuggestionsDayTable.submitted])
            assertEquals(AnalyticsExpected.RU_AUTO, this[AnalyticsSuggestionsDayTable.autoAccepted])
            assertEquals(AnalyticsExpected.RU_EDITOR, this[AnalyticsSuggestionsDayTable.editorAccepted])
            assertEquals(AnalyticsExpected.RU_REJECTED, this[AnalyticsSuggestionsDayTable.rejected])
            assertEquals(AnalyticsExpected.RU_BACKLOG, this[AnalyticsSuggestionsDayTable.backlogEnd])
            assertEquals(AnalyticsExpected.RU_MEDIAN_AUTO_SECONDS, this[AnalyticsSuggestionsDayTable.medianAutoSeconds])
            assertEquals(AnalyticsExpected.RU_MEDIAN_EDITOR_SECONDS, this[AnalyticsSuggestionsDayTable.medianEditorSeconds])
        }
        // The legacy decision (no recorded source) is an editor acceptance.
        with(suggestionsDay("ru", E.plusDays(1))) {
            assertEquals(0, this[AnalyticsSuggestionsDayTable.submitted])
            assertEquals(1, this[AnalyticsSuggestionsDayTable.editorAccepted])
            assertEquals(1, this[AnalyticsSuggestionsDayTable.backlogEnd])
            assertNull(this[AnalyticsSuggestionsDayTable.medianAutoSeconds])
        }
    }

    @Test
    fun theBacklogAtTheEndOfEachDayIsExact() {
        assertEquals(listOf(1, 1, 0), (0L..2L).map { suggestionsDay("en", E.plusDays(it))[AnalyticsSuggestionsDayTable.backlogEnd] })
        assertEquals(AnalyticsExpected.EN_MEDIAN_EDITOR_E5_SECONDS, suggestionsDay("en", E.plusDays(5))[AnalyticsSuggestionsDayTable.medianEditorSeconds])
    }

    // --- 5.8 content, pools, staff -----------------------------------------------------------------------------------

    @Test
    fun wordsAddedBySourceRemovedAndRestored() {
        with(contentDay("en", E)) {
            assertEquals(AnalyticsExpected.EN_ADDED_STAFF, this[AnalyticsContentDayTable.addedStaff])
            assertEquals(AnalyticsExpected.EN_ADDED_AUTO, this[AnalyticsContentDayTable.addedAuto])
            assertEquals(0, this[AnalyticsContentDayTable.addedBundled])
            assertEquals(1, this[AnalyticsContentDayTable.removed])
            assertEquals(1, this[AnalyticsContentDayTable.restored])
            assertNull(this[AnalyticsContentDayTable.activeWords])
        }
        with(contentDay("ru", E)) {
            assertEquals(AnalyticsExpected.RU_ADDED_STAFF, this[AnalyticsContentDayTable.addedStaff])
            assertEquals(AnalyticsExpected.RU_ADDED_SUGGESTION, this[AnalyticsContentDayTable.addedSuggestion])
            assertEquals(1, this[AnalyticsContentDayTable.removed])
            assertEquals(1, this[AnalyticsContentDayTable.restored])
        }
        assertEquals(2, contentDay("ru", E.minusDays(10))[AnalyticsContentDayTable.addedBundled])
        assertEquals(1, contentDay("ru", E.plusDays(1))[AnalyticsContentDayTable.restored])
    }

    @Test
    fun activeWordsAndPoolsAreCapturedOnceAndShownNow() {
        val lastClosed = AnalyticsDays.closedEventDates(NOW)
        assertEquals(AnalyticsExpected.EN_ACTIVE, contentDay("en", lastClosed)[AnalyticsContentDayTable.activeWords])
        assertEquals(AnalyticsExpected.RU_ACTIVE, contentDay("ru", lastClosed)[AnalyticsContentDayTable.activeWords])
        assertEquals(AnalyticsExpected.KK_ACTIVE, contentDay("kk", lastClosed)[AnalyticsContentDayTable.activeWords])
        val kkPool = rowOf(AnalyticsPoolDayTable) { (date eq lastClosed) and (calendar eq "kk") }!!
        assertEquals(AnalyticsExpected.KK_POOL, kkPool[AnalyticsPoolDayTable.poolSize])
        assertEquals(AnalyticsExpected.KK_UNUSED, kkPool[AnalyticsPoolDayTable.unusedLeft])
        assertNull(rowOf(AnalyticsPoolDayTable) { (date eq lastClosed) and (calendar eq "en") }!![AnalyticsPoolDayTable.poolSize])

        val content = runBlocking { service.content(query(AnalyticsFixture.date(0), AnalyticsFixture.date(60))) }
        val kk = content.calendars.single { it.calendar == "kk" }
        assertEquals(AnalyticsExpected.KK_POOL.toLong(), kk.poolSize)
        assertEquals(AnalyticsExpected.KK_UNUSED.toLong(), kk.unusedLeft)
        val en = content.calendars.single { it.calendar == "en" }
        assertEquals(1L, en.repeatDays)
        assertEquals(1L, en.upcomingRepeatDays)
        assertNull(en.poolSize)
        assertEquals(AnalyticsExpected.EN_ACTIVE.toLong(), content.current.single { it.lang == "en" }.activeWords)
    }

    @Test
    fun staffActivityInWords() {
        val rows = runBlocking { service.staff(query(E.minusDays(1), E.plusDays(2))) }.rows
        fun row(username: String?, lang: String, date: LocalDate) =
            rows.single { it.staff?.username == username && it.lang == lang && it.date == date.toString() }

        with(row("worder", "ru", E)) {
            assertEquals(AnalyticsExpected.WORDER_ADDED.toLong(), added)
            assertEquals(AnalyticsExpected.WORDER_EDITED.toLong(), edited)
            assertEquals(AnalyticsExpected.WORDER_REMOVED.toLong(), removed)
            assertEquals(AnalyticsExpected.WORDER_DECIDED.toLong(), decided)
            assertFalse(telegramUnlinked)
        }
        with(row(null, "ru", E)) {
            assertTrue(telegramUnlinked)
            assertEquals(AnalyticsExpected.UNLINKED_DECIDED.toLong(), decided)
            assertEquals(0L, added)
        }
        with(row("boss", "en", E)) {
            assertEquals(AnalyticsExpected.BOSS_ADDED.toLong(), added)
            assertEquals(1L, removed)
            assertEquals(0L, decided)
        }
        assertEquals(1L, row("worder", "ru", E.plusDays(1)).added)
        assertEquals(1L, row("boss", "en", E.plusDays(1)).edited)
        // Automatic acceptances (SYSTEM) count for nobody.
        assertEquals(5, rows.size)
        assertEquals(setOf(AnalyticsFixture.bossId, AnalyticsFixture.worderId, RollupComputations.TELEGRAM_UNLINKED), transaction(DatabaseFactory.init()) {
            AnalyticsStaffDayTable.selectAll().map { it[AnalyticsStaffDayTable.staffKey] }.toSet()
        })
    }

    companion object {
        private var loaded = false
    }
}
