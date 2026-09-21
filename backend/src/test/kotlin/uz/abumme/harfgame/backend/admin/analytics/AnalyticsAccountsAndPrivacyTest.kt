package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.NOW
import uz.abumme.harfgame.backend.admin.analytics.AnalyticsFixture.day
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable
import uz.abumme.harfgame.backend.db.AnalyticsWordDayTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.GameResultsTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.service.AuthServerService
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsAccountsAndPrivacyTest {
    private val clock = MutableClock(NOW)
    private val service = AnalyticsService(analyticsCatalog(clock), clock)

    @BeforeTest
    fun setup() {
        resetAnalyticsData()
    }

    @Test
    fun linkedAndAnonymousTotalsCountAnAccountWithBothProvidersOnceAndUnderEach() {
        AnalyticsFixture.loadAccounts()
        rollupJob(clock).run(NOW)

        val lastClosed = AnalyticsDays.closedEventDates(NOW)
        val day = runBlocking { service.accounts(AnalyticsQuery(lastClosed, lastClosed)) }.days.single()

        assertEquals(AnalyticsExpected.ACCOUNTS_TOTAL.toLong(), day.total)
        assertEquals(AnalyticsExpected.ACCOUNTS_LINKED.toLong(), day.linked)
        assertEquals(AnalyticsExpected.ACCOUNTS_GOOGLE.toLong(), day.googleLinked)
        assertEquals(AnalyticsExpected.ACCOUNTS_APPLE.toLong(), day.appleLinked)
        assertEquals(AnalyticsExpected.ACCOUNTS_ANONYMOUS.toLong(), day.anonymous)
        assertTrue(day.provisional)

        // Kept as captured: a later recomputation of the date does not re-read today's totals.
        AnalyticsFixture.loadResults()
        rollupJob(clock).run(NOW.plus(Duration.ofHours(2)))
        assertEquals(AnalyticsExpected.ACCOUNTS_TOTAL.toLong(), runBlocking { service.accounts(AnalyticsQuery(lastClosed, lastClosed)) }.days.single().total)
    }

    private fun rows(): Map<String, List<List<Any?>>> = transaction(DatabaseFactory.init()) {
        mapOf(
            "lang ru final" to AnalyticsLangDayTable.selectAll().where { (AnalyticsLangDayTable.lang eq "ru") and (AnalyticsLangDayTable.puzzleDay eq day(AnalyticsFixture.DELETED_FINAL)) }.values(AnalyticsLangDayTable.columns),
            "global final" to AnalyticsGlobalDayTable.selectAll().where { AnalyticsGlobalDayTable.puzzleDay eq day(AnalyticsFixture.DELETED_FINAL) }.values(AnalyticsGlobalDayTable.columns),
            "word ru final" to AnalyticsWordDayTable.selectAll().where { (AnalyticsWordDayTable.calendar eq "ru") and (AnalyticsWordDayTable.puzzleDay eq day(AnalyticsFixture.DELETED_FINAL)) }.values(AnalyticsWordDayTable.columns),
        )
    }

    private fun Iterable<ResultRow>.values(columns: List<org.jetbrains.exposed.v1.core.Column<*>>) = map { row -> columns.map { row[it] } }

    @Test
    fun deletingAnAccountRemovesItsResultsButLeavesFinalDaysUnchanged() {
        AnalyticsFixture.load()
        rollupJob(clock).run(NOW)
        val deleted = AnalyticsFixture.player(67)
        val finalBefore = rows()
        assertTrue(rollupDay("lang:ru", day(AnalyticsFixture.DELETED_FINAL))!![AnalyticsRollupDaysTable.final])
        assertEquals(1, rowOf(AnalyticsLangDayTable) { (lang eq "ru") and (puzzleDay eq day(AnalyticsFixture.DELETED_FINAL)) }!![AnalyticsLangDayTable.players])
        assertEquals(1, rowOf(AnalyticsLangDayTable) { (lang eq "ru") and (puzzleDay eq day(AnalyticsFixture.DELETED_PROVISIONAL)) }!![AnalyticsLangDayTable.players])
        val provisionalDau = rowOf(AnalyticsGlobalDayTable) { puzzleDay eq day(AnalyticsFixture.DELETED_PROVISIONAL) }!![AnalyticsGlobalDayTable.dau]

        assertTrue(runBlocking { AuthServerService(JwtService(), clock = clock).deleteAccount(deleted) })
        rollupJob(clock).run(NOW.plus(Duration.ofHours(2)))

        assertEquals(0, transaction(DatabaseFactory.init()) { GameResultsTable.selectAll().where { GameResultsTable.userId eq deleted }.count() })
        assertEquals(finalBefore, rows())
        val provisional = rowOf(AnalyticsLangDayTable) { (lang eq "ru") and (puzzleDay eq day(AnalyticsFixture.DELETED_PROVISIONAL)) }!!
        assertEquals(0, provisional[AnalyticsLangDayTable.players])
        assertFalse(rollupDay("lang:ru", day(AnalyticsFixture.DELETED_PROVISIONAL))!![AnalyticsRollupDaysTable.final])
        assertEquals(provisionalDau - 1, rowOf(AnalyticsGlobalDayTable) { puzzleDay eq day(AnalyticsFixture.DELETED_PROVISIONAL) }!![AnalyticsGlobalDayTable.dau])
    }
}
