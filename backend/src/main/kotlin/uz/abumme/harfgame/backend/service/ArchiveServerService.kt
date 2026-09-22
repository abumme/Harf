package uz.abumme.harfgame.backend.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.db.ArchiveRunsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.data.archive.ArchiveHistoryDto
import uz.abumme.harfgame.data.archive.ArchiveRowDto
import uz.abumme.harfgame.data.archive.ArchiveRunDto
import java.time.Clock
import java.time.Instant as JInstant
import kotlin.time.Instant as KInstant

class ArchiveServerService(
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun listRuns(userId: String): ArchiveHistoryDto = DatabaseFactory.dbQuery {
        val rows = ArchiveRunsTable.selectAll()
            .where { ArchiveRunsTable.userId eq userId }
            .orderBy(ArchiveRunsTable.completedAt to SortOrder.DESC)
            .map { r ->
                val rowsList = try {
                    json.decodeFromString<List<ArchiveRowDto>>(r[ArchiveRunsTable.rows])
                } catch (_: Exception) {
                    emptyList()
                }
                ArchiveRunDto(
                    runId = r[ArchiveRunsTable.runId],
                    language = r[ArchiveRunsTable.lang],
                    puzzleDay = r[ArchiveRunsTable.puzzleDay],
                    won = r[ArchiveRunsTable.won],
                    attempts = r[ArchiveRunsTable.attempts],
                    hardMode = r[ArchiveRunsTable.hardMode],
                    rows = rowsList,
                    completedAt = KInstant.fromEpochMilliseconds(r[ArchiveRunsTable.completedAt].toEpochMilli()),
                )
            }
        ArchiveHistoryDto(rows)
    }

    suspend fun uploadRuns(userId: String, runs: List<ArchiveRunDto>): Int {
        if (runs.isEmpty()) return 0
        val receivedAt = clock.instant()
        return DatabaseFactory.dbQuery {
            val inserted = ArchiveRunsTable.batchInsert(runs, ignore = true, shouldReturnGeneratedValues = false) { run ->
                this[ArchiveRunsTable.userId] = userId
                this[ArchiveRunsTable.runId] = run.runId
                this[ArchiveRunsTable.lang] = run.language
                this[ArchiveRunsTable.puzzleDay] = run.puzzleDay
                this[ArchiveRunsTable.won] = run.won
                this[ArchiveRunsTable.attempts] = run.attempts
                this[ArchiveRunsTable.hardMode] = run.hardMode
                this[ArchiveRunsTable.rows] = json.encodeToString(run.rows)
                this[ArchiveRunsTable.completedAt] = JInstant.ofEpochMilli(run.completedAt.toEpochMilliseconds())
                this[ArchiveRunsTable.receivedAt] = receivedAt
            }
            inserted.size
        }
    }
}
