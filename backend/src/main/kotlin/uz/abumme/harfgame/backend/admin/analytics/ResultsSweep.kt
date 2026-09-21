package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.db.AnalyticsMetaTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.data.sync.ResultRecordDto
import java.time.Instant

/**
 * Backfill and repair in one: reads stored stats snapshots changed since a watermark, oldest first, and records their
 * plausible results with the upload's insert-ignore (`received_at` = the snapshot's `updated_at`).
 *
 * On a database that predates analytics the watermark is absent, so the first runs backfill every snapshot; after that
 * only snapshots updated since the previous run are read again, so the backfill never repeats. Each run reads at most
 * [maxBatches] batches of [batchSize] snapshots, one transaction each, yielding between them, so a large backfill
 * spreads over several runs instead of loading the box.
 */
class ResultsSweep(
    private val recorder: GameResultRecorder,
    private val batchSize: Int = 500,
    private val maxBatches: Int = 20,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** What one run did: snapshots read, batches used, and whether it reached the newest snapshot. */
    data class Report(val snapshots: Int, val batches: Int, val caughtUp: Boolean)

    /** The position after the last swept snapshot: its `updated_at`, then its account id. */
    data class Watermark(val updatedAt: Instant, val userId: String) {
        fun encode(): String = "$updatedAt|$userId"

        companion object {
            fun decode(value: String): Watermark? {
                val at = value.substringBefore('|', missingDelimiterValue = "")
                val user = value.substringAfter('|', missingDelimiterValue = "")
                if (at.isEmpty() || user.isEmpty()) return null
                return runCatching { Watermark(Instant.parse(at), user) }.getOrNull()
            }
        }
    }

    suspend fun run(now: Instant): Report {
        val languages = recorder.packLanguages()
        var snapshots = 0
        var batches = 0
        while (batches < maxBatches) {
            val read = DatabaseFactory.dbQuery { sweepBatch(languages, now) }
            batches++
            snapshots += read
            if (read < batchSize) return Report(snapshots, batches, caughtUp = true)
            yield()
        }
        return Report(snapshots, batches, caughtUp = false)
    }

    /** One batch inside a transaction: read, record, advance the watermark. Returns the snapshots read. */
    private fun sweepBatch(languages: Set<String>, now: Instant): Int {
        val watermark = watermark()
        val rows = UserStatsTable.selectAll()
            .apply {
                if (watermark != null) {
                    where {
                        (UserStatsTable.updatedAt greater watermark.updatedAt) or
                            ((UserStatsTable.updatedAt eq watermark.updatedAt) and (UserStatsTable.userId greater watermark.userId))
                    }
                }
            }
            .orderBy(UserStatsTable.updatedAt to SortOrder.ASC, UserStatsTable.userId to SortOrder.ASC)
            .limit(batchSize)
            .toList()
        if (rows.isEmpty()) return 0
        for (row in rows) {
            val records = try {
                json.decodeFromString<List<ResultRecordDto>>(row[UserStatsTable.data])
            } catch (e: Exception) {
                emptyList()
            }
            GameResultRecorder.insertIgnore(
                row[UserStatsTable.userId],
                ResultPlausibility.plausible(records, languages, now),
                row[UserStatsTable.updatedAt],
            )
        }
        val last = rows.last()
        writeMeta(WATERMARK_KEY, Watermark(last[UserStatsTable.updatedAt], last[UserStatsTable.userId]).encode())
        return rows.size
    }

    companion object {
        const val WATERMARK_KEY = "results_sweep_watermark"

        /** The stored watermark, or null before the first sweep. Inside a transaction. */
        fun watermark(): Watermark? = readMeta(WATERMARK_KEY)?.let(Watermark::decode)
    }
}

/** An `analytics_meta` value, inside a transaction. */
internal fun readMeta(key: String): String? =
    AnalyticsMetaTable.selectAll().where { AnalyticsMetaTable.key eq key }.singleOrNull()?.get(AnalyticsMetaTable.value)

/** Sets an `analytics_meta` value, inside a transaction. */
internal fun writeMeta(key: String, value: String) {
    AnalyticsSql.update(
        "INSERT INTO analytics_meta (key, value) VALUES (:key, :value) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
        mapOf("key" to key, "value" to value),
        StatementType.INSERT,
    )
}
