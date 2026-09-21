package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.GameResultsTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultsSweepTest {
    private val now = Instant.parse("2026-09-17T10:00:00Z")
    private val clock = MutableClock(now)
    private val today = LocalDate.parse("2026-09-17").toEpochDay()

    @BeforeTest
    fun setup() {
        resetAnalyticsData()
    }

    private fun sweep(batchSize: Int = 500, maxBatches: Int = 20) = ResultsSweep(GameResultRecorder(clock), batchSize, maxBatches)

    private fun watermark() = transaction(DatabaseFactory.init()) { ResultsSweep.watermark() }

    private fun receivedAt(userId: String) = transaction(DatabaseFactory.init()) {
        GameResultsTable.selectAll().where { GameResultsTable.userId eq userId }.map { it[GameResultsTable.receivedAt] }.distinct()
    }

    @Test
    fun snapshotsStoredBeforeAnalyticsAreBackfilledWithTheUploadRules() {
        insertAccount("a")
        insertAccount("b")
        val aAt = Instant.parse("2026-09-10T08:00:00Z")
        insertSnapshot("a", listOf(ResultRecordDto("en", today - 3, true, 2), ResultRecordDto("en", today - 2, false, 6)), aAt)
        insertSnapshot(
            "b",
            listOf(
                ResultRecordDto("ru", today - 1, true, 4),
                ResultRecordDto("ru", today - 1, false, 6), // a duplicate day: the first one wins
                ResultRecordDto("ru", today + 1, true, 4), // tomorrow
                ResultRecordDto("xx", today - 1, true, 4), // no pack
                ResultRecordDto("kk", today - 1, true, 9), // attempts
            ),
            Instant.parse("2026-09-11T08:00:00Z"),
        )

        val report = runBlocking { sweep().run(now) }

        assertEquals(ResultsSweep.Report(snapshots = 2, batches = 1, caughtUp = true), report)
        assertEquals(listOf(ResultRecordDto("en", today - 3, true, 2), ResultRecordDto("en", today - 2, false, 6)), storedResults("a"))
        assertEquals(listOf(ResultRecordDto("ru", today - 1, true, 4)), storedResults("b"))
        assertEquals(listOf(aAt), receivedAt("a"))
        assertEquals(ResultsSweep.Watermark(Instant.parse("2026-09-11T08:00:00Z"), "b"), watermark())
    }

    @Test
    fun aSecondRunWithoutChangesAddsNothingAndKeepsTheWatermark() {
        insertAccount("a")
        insertSnapshot("a", listOf(ResultRecordDto("en", today - 3, true, 2)), Instant.parse("2026-09-10T08:00:00Z"))
        runBlocking { sweep().run(now) }
        val mark = watermark()
        // Results stored meanwhile by uploads are never touched by the sweep either.
        insertResults("a", "kk", listOf(today - 5))

        val second = runBlocking { sweep().run(now.plus(Duration.ofMinutes(15))) }

        assertEquals(ResultsSweep.Report(snapshots = 0, batches = 1, caughtUp = true), second)
        assertEquals(mark, watermark())
        assertEquals(2, gameResultCount())
    }

    @Test
    fun aSnapshotUpdatedAfterTheWatermarkIsReadAgain() {
        insertAccount("a")
        insertAccount("b")
        insertSnapshot("a", listOf(ResultRecordDto("en", today - 3, true, 2)), Instant.parse("2026-09-10T08:00:00Z"))
        insertSnapshot("b", listOf(ResultRecordDto("en", today - 3, true, 5)), Instant.parse("2026-09-11T08:00:00Z"))
        runBlocking { sweep().run(now) }
        // Account a uploads a newer snapshot whose recording failed at upload time.
        val newer = Instant.parse("2026-09-16T08:00:00Z")
        transaction(DatabaseFactory.init()) {
            UserStatsTable.update({ UserStatsTable.userId eq "a" }) {
                it[data] = Json.encodeToString(listOf(ResultRecordDto("en", today - 1, true, 3)))
                it[updatedAt] = newer
            }
        }

        val report = runBlocking { sweep().run(now) }

        assertEquals(1, report.snapshots)
        assertEquals(listOf(ResultRecordDto("en", today - 3, true, 2), ResultRecordDto("en", today - 1, true, 3)), storedResults("a"))
        assertEquals(ResultsSweep.Watermark(newer, "a"), watermark())
    }

    @Test
    fun aBackfillLargerThanOneRunsCapCompletesOverSeveralRuns() {
        assertNull(watermark())
        val at = Instant.parse("2026-09-10T08:00:00Z")
        // Seven snapshots, two with the same timestamp (ordered by account id then).
        for (n in 1..7) {
            val id = "user-$n"
            insertAccount(id)
            insertSnapshot(id, listOf(ResultRecordDto("en", today - n, true, 3)), if (n <= 2) at else at.plus(Duration.ofMinutes(n.toLong())))
        }
        val sweep = sweep(batchSize = 2, maxBatches = 2)

        val first = runBlocking { sweep.run(now) }
        assertEquals(4, first.snapshots)
        assertFalse(first.caughtUp)
        assertEquals(4, gameResultCount())

        val second = runBlocking { sweep.run(now) }
        assertEquals(3, second.snapshots)
        assertTrue(second.caughtUp)
        assertEquals(7, gameResultCount())
        assertEquals(ResultsSweep.Watermark(at.plus(Duration.ofMinutes(7)), "user-7"), watermark())

        assertEquals(0, runBlocking { sweep.run(now) }.snapshots)
    }
}
