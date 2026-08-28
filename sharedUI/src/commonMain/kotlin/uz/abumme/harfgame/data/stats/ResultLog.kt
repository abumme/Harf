package uz.abumme.harfgame.data.stats

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Append-only, KSafe-backed log of finished rounds. One record per (language, puzzleDay):
 * a duplicate is ignored, keeping replay and any future sync idempotent.
 */
class ResultLog(private val ksafe: KSafe) {

    private val mutex = Mutex()

    suspend fun all(): List<ResultRecord> = ksafe.get(KEY, ResultLogData()).records

    suspend fun getUpdatedAt(): Instant = ksafe.get(KEY, ResultLogData()).updatedAt

    suspend fun record(record: ResultRecord) = mutex.withLock {
        val data = ksafe.get(KEY, ResultLogData())
        val exists = data.records.any { it.language == record.language && it.puzzleDay == record.puzzleDay }
        if (!exists) {
            val now = Clock.System.now()
            ksafe.put(KEY, data.copy(records = data.records + record, updatedAt = now))
        }
    }

    suspend fun merge(remoteRecords: List<ResultRecord>, remoteUpdatedAt: Instant) = mutex.withLock {
        val local = ksafe.get(KEY, ResultLogData())
        val existingKeys = local.records.map { it.language to it.puzzleDay }.toSet()
        val newFromRemote = remoteRecords.filter { (it.language to it.puzzleDay) !in existingKeys }
        val mergedRecords = local.records + newFromRemote
        val newUpdatedAt = if (remoteUpdatedAt > local.updatedAt) remoteUpdatedAt else local.updatedAt
        ksafe.put(KEY, local.copy(records = mergedRecords, updatedAt = newUpdatedAt))
    }

    suspend fun replace(remoteRecords: List<ResultRecord>, remoteUpdatedAt: Instant) = mutex.withLock {
        val data = ksafe.get(KEY, ResultLogData())
        ksafe.put(KEY, data.copy(records = remoteRecords, updatedAt = remoteUpdatedAt))
    }

    suspend fun clear() = mutex.withLock {
        ksafe.put(KEY, ResultLogData())
    }

    companion object {
        private const val KEY = "stats.resultLog"
    }
}
