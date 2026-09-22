package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.archive.ArchiveHistoryDto
import uz.abumme.harfgame.data.archive.ArchiveHistoryManager
import uz.abumme.harfgame.data.archive.ArchiveRowDto
import uz.abumme.harfgame.data.archive.ArchiveRunDto
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.service.ArchiveService
import uz.abumme.harfgame.data.stats.ResultLog
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class FakeArchiveService : ArchiveService {
    val serverRuns = mutableListOf<ArchiveRunDto>()

    override suspend fun listRuns(token: String): ApiResult<ArchiveHistoryDto> {
        return ApiResult.Success(ArchiveHistoryDto(serverRuns.toList()))
    }

    override suspend fun uploadRuns(token: String, runs: List<ArchiveRunDto>): ApiResult<Int> {
        val newRuns = runs.filter { incoming -> serverRuns.none { it.runId == incoming.runId } }
        serverRuns.addAll(newRuns)
        return ApiResult.Success(newRuns.size)
    }
}

class ArchiveHistoryManagerTest {

    @BeforeTest
    fun cleanup() = runTest {
        val ksafe = KSafe()
        SessionStore(ksafe).clear()
        ResultLog(ksafe).clear()
        ksafe.put("archive.history.owner-1", ArchiveHistoryDto())
        ksafe.put("archive.queue.owner-1", ArchiveHistoryDto())
        ksafe.put("archive.history.user-cloud-1", ArchiveHistoryDto())
        ksafe.put("archive.queue.user-cloud-1", ArchiveHistoryDto())
        ksafe.put("archive.history.user-replay", ArchiveHistoryDto())
        ksafe.put("archive.queue.user-replay", ArchiveHistoryDto())
    }

    @Test
    fun archive_completion_isolated_from_result_log_and_official_stats() = runTest {
        val ksafe = KSafe()
        val sessionStore = SessionStore(ksafe)
        val resultLog = ResultLog(ksafe)
        val archiveManager = ArchiveHistoryManager(ksafe, sessionStore)

        val owner = "owner-1"
        val run1 = ArchiveRunDto(
            runId = "run-1",
            language = "en",
            puzzleDay = 500L,
            won = true,
            attempts = 4,
            hardMode = false,
            rows = listOf(ArchiveRowDto(listOf("a", "p", "p", "l", "e"), listOf(2, 2, 2, 2, 2))),
            completedAt = Instant.parse("2026-08-20T12:00:00Z"),
        )
        val run2 = ArchiveRunDto(
            runId = "run-2",
            language = "en",
            puzzleDay = 500L,
            won = true,
            attempts = 3,
            hardMode = true,
            rows = listOf(ArchiveRowDto(listOf("a", "p", "p", "l", "e"), listOf(2, 2, 2, 2, 2))),
            completedAt = Instant.parse("2026-08-20T13:00:00Z"),
        )

        archiveManager.record(owner, run1)
        archiveManager.record(owner, run2)

        // Archive runs must be in archive history
        val history = archiveManager.history(owner)
        assertEquals(2, history.size)

        // Official ResultLog must remain completely empty and untouched
        assertTrue(resultLog.all().isEmpty(), "ResultLog must not receive archive runs")
    }

    @Test
    fun offline_queue_and_sync_appears_on_second_device_once() = runTest {
        val ksafe1 = KSafe()
        val session1 = SessionStore(ksafe1)
        val fakeServer = FakeArchiveService()

        // Device 1: initially offline / signed out
        val manager1 = ArchiveHistoryManager(ksafe1, session1, fakeServer)
        val owner = "user-cloud-1"

        val offlineRun = ArchiveRunDto(
            runId = "offline-run-123",
            language = "uz-latn",
            puzzleDay = 200L,
            won = true,
            attempts = 5,
            hardMode = true,
            rows = emptyList(),
            completedAt = Instant.parse("2026-08-20T10:00:00Z"),
        )

        // Record while signed out -> queued locally
        manager1.record(owner, offlineRun)
        assertEquals(1, manager1.history(owner).size)
        assertEquals(0, fakeServer.serverRuns.size, "Server must not have received run before sign-in")

        // User signs in on Device 1
        session1.saveSession(userId = owner, accessToken = "token-1", refreshToken = "refresh-1")
        manager1.sync(owner)

        // Server now has the run exactly once
        assertEquals(1, fakeServer.serverRuns.size)
        assertEquals("offline-run-123", fakeServer.serverRuns[0].runId)

        // Device 2: signs into the same account
        val ksafe2 = KSafe()
        val session2 = SessionStore(ksafe2)
        session2.saveSession(userId = owner, accessToken = "token-2", refreshToken = "refresh-2")
        val manager2 = ArchiveHistoryManager(ksafe2, session2, fakeServer)

        // Device 2: fresh local device cache has not seen device 1's local run yet
        ksafe2.put("archive.history.$owner", ArchiveHistoryDto())
        assertEquals(0, manager2.history(owner).size)

        // After sync, device 2 receives the run from server
        manager2.sync(owner)
        val dev2History = manager2.history(owner)
        assertEquals(1, dev2History.size)
        assertEquals("offline-run-123", dev2History[0].runId)

        // Redundant sync on device 1 does not duplicate
        manager1.sync(owner)
        assertEquals(1, manager1.history(owner).size)
    }

    @Test
    fun replay_creates_new_run_id_and_preserves_earlier_playthroughs() = runTest {
        val ksafe = KSafe()
        val sessionStore = SessionStore(ksafe)
        val manager = ArchiveHistoryManager(ksafe, sessionStore)

        val owner = "user-replay"
        val initialRunId = manager.generateReplayRunId()

        val firstPlaythrough = ArchiveRunDto(
            runId = initialRunId,
            language = "ru",
            puzzleDay = 300L,
            won = false,
            attempts = 6,
            hardMode = false,
            rows = emptyList(),
            completedAt = Instant.parse("2026-08-21T09:00:00Z"),
        )
        manager.record(owner, firstPlaythrough)

        // Start a replay of the same day
        val replayRunId = manager.generateReplayRunId()
        assertTrue(replayRunId != initialRunId, "Replay must generate a fresh run id")

        val secondPlaythrough = ArchiveRunDto(
            runId = replayRunId,
            language = "ru",
            puzzleDay = 300L,
            won = true,
            attempts = 3,
            hardMode = true,
            rows = emptyList(),
            completedAt = Instant.parse("2026-08-21T10:00:00Z"),
        )
        manager.record(owner, secondPlaythrough)

        // Both playthroughs must remain visible in history
        val history = manager.history(owner)
        assertEquals(2, history.size)
        val runIds = history.map { it.runId }.toSet()
        assertTrue(runIds.contains(initialRunId), "Initial playthrough must be preserved")
        assertTrue(runIds.contains(replayRunId), "Replay playthrough must be present")
    }
}
