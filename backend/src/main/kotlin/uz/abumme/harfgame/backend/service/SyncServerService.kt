package uz.abumme.harfgame.backend.service

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.sync.UserStatsDto

sealed interface UploadStatsResult {
    object Success : UploadStatsResult
    object StoredSnapshotWon : UploadStatsResult
    data class Rejected(val message: String) : UploadStatsResult
}

class SyncServerService(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun uploadStats(userId: String, incoming: UserStatsDto): UploadStatsResult {
        val nowMillis = System.currentTimeMillis()
        // Reject snapshots more than 5 minutes ahead of server time
        if (incoming.updatedAt.toEpochMilliseconds() > nowMillis + 5 * 60 * 1000L) {
            return UploadStatsResult.Rejected("Snapshot timestamp is too far in the future")
        }

        val recordsJson = json.encodeToString(incoming.records)

        val javaInstant = java.time.Instant.ofEpochMilli(incoming.updatedAt.toEpochMilliseconds())

        return DatabaseFactory.dbQuery {
            val existing = UserStatsTable
                .selectAll()
                .where { UserStatsTable.userId eq userId }
                .singleOrNull()

            if (existing == null) {
                UserStatsTable.insert {
                    it[UserStatsTable.userId] = userId
                    it[data] = recordsJson
                    it[updatedAt] = javaInstant
                }
                UploadStatsResult.Success
            } else {
                val currentUpdatedAt = existing[UserStatsTable.updatedAt]
                if (javaInstant.isAfter(currentUpdatedAt)) {
                    UserStatsTable.update({ UserStatsTable.userId eq userId }) {
                        it[data] = recordsJson
                        it[updatedAt] = javaInstant
                    }
                    UploadStatsResult.Success
                } else {
                    UploadStatsResult.StoredSnapshotWon
                }
            }
        }
    }

    suspend fun getStats(userId: String): UserStatsDto {
        return DatabaseFactory.dbQuery {
            val row = UserStatsTable
                .selectAll()
                .where { UserStatsTable.userId eq userId }
                .singleOrNull()

            if (row != null) {
                val records = json.decodeFromString<List<ResultRecordDto>>(row[UserStatsTable.data])
                UserStatsDto(
                    updatedAt = Instant.fromEpochMilliseconds(row[UserStatsTable.updatedAt].toEpochMilli()),
                    records = records
                )
            } else {
                UserStatsDto(
                    updatedAt = Instant.fromEpochMilliseconds(0),
                    records = emptyList()
                )
            }
        }
    }
}
