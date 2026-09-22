package uz.abumme.harfgame.data.archive

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.service.ArchiveService
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Manages completed archive runs, durable offline queueing, and account synchronization.
 * Completely separate from [uz.abumme.harfgame.data.stats.ResultLog] and official daily stats.
 */
class ArchiveHistoryManager(
    private val ksafe: KSafe,
    private val sessionStore: SessionStore,
    private val archiveService: ArchiveService? = null,
) {
    private val mutex = Mutex()

    suspend fun record(ownerId: String, run: ArchiveRunDto) = mutex.withLock {
        // 1. Append to local history
        val currentHistory = getHistoryInternal(ownerId)
        val updatedRuns = (listOf(run) + currentHistory.runs).distinctBy { it.runId }
        ksafe.put(historyKey(ownerId), ArchiveHistoryDto(updatedRuns))

        // 2. Queue for upload
        val queue = getQueueInternal(ownerId)
        val updatedQueue = (queue + run).distinctBy { it.runId }
        ksafe.put(queueKey(ownerId), ArchiveHistoryDto(updatedQueue))

        // 3. Attempt immediate sync if service is available
        syncInternal(ownerId)
    }

    suspend fun history(ownerId: String): List<ArchiveRunDto> = mutex.withLock {
        getHistoryInternal(ownerId).runs
    }

    suspend fun sync(ownerId: String) = mutex.withLock {
        syncInternal(ownerId)
    }

    @OptIn(ExperimentalTime::class)
    fun generateReplayRunId(): String =
        "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toString(16)}"

    private suspend fun syncInternal(ownerId: String) {
        val service = archiveService ?: return
        val token = sessionStore.get().accessToken ?: return

        // 1. Flush offline queue
        val queue = getQueueInternal(ownerId)
        if (queue.isNotEmpty()) {
            val uploadResult = service.uploadRuns(token, queue)
            if (uploadResult is ApiResult.Success) {
                ksafe.put(queueKey(ownerId), ArchiveHistoryDto(emptyList()))
            }
        }

        // 2. Pull remote history and merge by runId
        val listResult = service.listRuns(token)
        if (listResult is ApiResult.Success) {
            val local = getHistoryInternal(ownerId).runs
            val remote = listResult.data.runs
            val merged = (remote + local).distinctBy { it.runId }
            ksafe.put(historyKey(ownerId), ArchiveHistoryDto(merged))
        }
    }

    private suspend fun getHistoryInternal(ownerId: String): ArchiveHistoryDto =
        ksafe.get(historyKey(ownerId), ArchiveHistoryDto())

    private suspend fun getQueueInternal(ownerId: String): List<ArchiveRunDto> =
        ksafe.get(queueKey(ownerId), ArchiveHistoryDto()).runs

    private fun historyKey(ownerId: String) = "archive.history.$ownerId"
    private fun queueKey(ownerId: String) = "archive.queue.$ownerId"
}
