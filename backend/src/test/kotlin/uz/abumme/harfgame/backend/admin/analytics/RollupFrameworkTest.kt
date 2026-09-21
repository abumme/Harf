package uz.abumme.harfgame.backend.admin.analytics

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.db.AnalyticsCohortDayTable
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollupFrameworkTest {
    private val clock = MutableClock(Instant.parse("2026-09-17T10:00:00Z"))
    private val job = rollupJob(clock)

    @BeforeTest
    fun setup() {
        resetAnalyticsData()
    }

    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    private fun moscow(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(ZoneId.of("Europe/Moscow")).toInstant()

    private fun ruPlayers(date: String): Int? = rowOf(AnalyticsLangDayTable) { (lang eq "ru") and (puzzleDay eq day(date)) }?.get(AnalyticsLangDayTable.players)

    private fun contents(table: Table): List<List<Any?>> = transaction(DatabaseFactory.init()) {
        table.selectAll().map { row: ResultRow -> table.columns.map { row[it] } }.sortedBy { it.toString() }
    }

    private fun rolledUp(): List<List<List<Any?>>> =
        AnalyticsRollupJob.ROLLUP_TABLES.map(::contents)

    @Test
    fun daysMissedDuringDowntimeAreAllComputedInOneTick() {
        insertAccount("a", createdAt = moscow("2026-09-10T12:00:00"))
        insertResults("a", "ru", listOf(day("2026-09-12"), day("2026-09-13"), day("2026-09-14"), day("2026-09-15")))
        job.run(moscow("2026-09-13T00:30:00"))
        assertEquals(1, ruPlayers("2026-09-12"))
        assertEquals(null, ruPlayers("2026-09-13"))

        // The server was down for three days.
        val report = job.run(moscow("2026-09-16T00:30:00"))

        for (date in listOf("2026-09-13", "2026-09-14", "2026-09-15")) {
            assertEquals(1, ruPlayers(date), date)
            assertFalse(rollupDay("lang:ru", day(date))!![AnalyticsRollupDaysTable.final], "$date is provisional")
        }
        assertTrue(report.computed.filter { it.group.key == "lang:ru" }.map { it.day }.containsAll(listOf(day("2026-09-13"), day("2026-09-14"), day("2026-09-15"))))
        assertEquals(0, report.remaining)
    }

    @Test
    fun finalDaysAreNeverComputedAgainAndNothingIsDueRightAfterARun() {
        insertAccount("a", createdAt = moscow("2026-08-01T12:00:00"))
        insertResults("a", "ru", listOf(day("2026-08-01"), day("2026-08-02")))
        val now = moscow("2026-09-01T12:00:00")
        val first = job.run(now)
        assertTrue(first.computed.isNotEmpty())
        // Computed long after closing: final at once. The last seven closed days stay provisional.
        assertTrue(rollupDay("lang:ru", day("2026-08-01"))!![AnalyticsRollupDaysTable.final])
        assertTrue(rollupDay("lang:ru", day("2026-08-24"))!![AnalyticsRollupDaysTable.final])
        assertFalse(rollupDay("lang:ru", day("2026-08-25"))!![AnalyticsRollupDaysTable.final])

        assertEquals(emptyList(), job.run(now.plus(Duration.ofMinutes(30))).computed)

        val refresh = job.run(now.plus(Duration.ofHours(2)))
        assertTrue(refresh.computed.isNotEmpty())
        assertTrue(refresh.computed.all { it.priority == 2 })
        assertTrue(refresh.computed.none { it.group.key == "lang:ru" && it.day <= day("2026-08-24") })
    }

    @Test
    fun recomputingAProvisionalDayEqualsComputingItOnce() {
        insertAccount("a", createdAt = moscow("2026-09-01T12:00:00"))
        insertAccount("b", createdAt = moscow("2026-09-01T12:00:00"))
        insertResults("a", "ru", (day("2026-09-01")..day("2026-09-14")).toList())
        insertResults("b", "ru", listOf(day("2026-09-10"), day("2026-09-14")), won = false)
        insertResults("b", "en", listOf(day("2026-09-12")), attempts = 5)
        val now = moscow("2026-09-15T01:00:00")
        job.run(now)
        val once = rolledUp()

        // Two more runs, each after the refresh interval: every provisional day is recomputed from the same rows.
        val again = job.run(now.plus(Duration.ofHours(2)))
        assertTrue(again.computed.any { it.group.key == "lang:ru" && it.day == day("2026-09-14") })
        job.run(now.plus(Duration.ofHours(4)))

        assertEquals(once, rolledUp())
        assertEquals(2, rowOf(AnalyticsGlobalDayTable) { puzzleDay eq day("2026-09-12") }!![AnalyticsGlobalDayTable.dau])
        assertEquals(2, rowOf(AnalyticsGlobalDayTable) { puzzleDay eq day("2026-09-14") }!![AnalyticsGlobalDayTable.dau])
        assertEquals(1, rowOf(AnalyticsCohortDayTable) { cohortDay eq day("2026-09-01") }!![AnalyticsCohortDayTable.size])
    }

    @Test
    fun aResultSyncedThreeDaysAfterCloseIsIncludedWhileTheDayIsProvisional() {
        insertAccount("a")
        insertAccount("late")
        insertResults("a", "ru", listOf(day("2026-09-01")))
        job.run(moscow("2026-09-02T01:00:00"))
        assertEquals(1, ruPlayers("2026-09-01"))

        insertResults("late", "ru", listOf(day("2026-09-01")), receivedAt = moscow("2026-09-05T00:00:00"))
        job.run(moscow("2026-09-05T00:00:00"))

        assertEquals(2, ruPlayers("2026-09-01"))
        assertFalse(rollupDay("lang:ru", day("2026-09-01"))!![AnalyticsRollupDaysTable.final])
    }

    @Test
    fun aResultSyncedAfterTheDayIsFinalChangesNothing() {
        insertAccount("a")
        insertAccount("late")
        insertResults("a", "ru", listOf(day("2026-09-01")))
        job.run(moscow("2026-09-02T01:00:00"))
        // Past the settle window (closed 2026-09-02 00:00 Moscow + 7 days): the last computation makes it final.
        job.run(moscow("2026-09-09T00:30:00"))
        val finalDay = rollupDay("lang:ru", day("2026-09-01"))!!
        assertTrue(finalDay[AnalyticsRollupDaysTable.final])

        insertResults("late", "ru", listOf(day("2026-09-01")))
        job.run(moscow("2026-09-10T12:00:00"))

        assertEquals(1, ruPlayers("2026-09-01"))
        assertEquals(finalDay[AnalyticsRollupDaysTable.computedAt], rollupDay("lang:ru", day("2026-09-01"))!![AnalyticsRollupDaysTable.computedAt])
    }

    @Test
    fun aTickComputesAtMostItsCapMissingDaysFirstOldestFirst() {
        insertAccount("a", createdAt = moscow("2026-09-14T12:00:00"))
        insertResults("a", "ru", listOf(day("2026-09-10"), day("2026-09-11")))
        val capped = rollupJob(clock, cap = 3)
        val now = moscow("2026-09-12T12:00:00")

        val first = capped.run(now)

        assertEquals(3, first.computed.size)
        assertTrue(first.computed.all { it.priority == 0 })
        assertEquals(first.computed.map { it.day }.sorted(), first.computed.map { it.day })
        assertTrue(first.remaining > 0)
        var runs = 1
        while (capped.run(now).computed.isNotEmpty()) runs++
        assertTrue(runs > 1)
        assertEquals(1, ruPlayers("2026-09-11"))
    }
}
