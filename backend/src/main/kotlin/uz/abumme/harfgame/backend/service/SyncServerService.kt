package uz.abumme.harfgame.backend.service

import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
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
            val updated = UserStatsTable.update({
                (UserStatsTable.userId eq userId) and (UserStatsTable.updatedAt less javaInstant)
            }) {
                it[data] = recordsJson
                it[updatedAt] = javaInstant
            }

            if (updated > 0) {
                UploadStatsResult.Success
            } else {
                val existing = UserStatsTable
                    .selectAll()
                    .where { UserStatsTable.userId eq userId }
                    .singleOrNull()

                if (existing != null) {
                    UploadStatsResult.StoredSnapshotWon
                } else {
                    // A concurrent insert may have raced us in between the update and this select,
                    // so guard the conflict path too: only overwrite an existing row when ours is
                    // newer, preserving last-write-wins.
                    UserStatsTable.upsert(
                        where = { UserStatsTable.updatedAt less javaInstant },
                    ) {
                        it[UserStatsTable.userId] = userId
                        it[data] = recordsJson
                        it[updatedAt] = javaInstant
                    }
                    UploadStatsResult.Success
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
