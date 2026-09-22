package uz.abumme.harfgame.data.archive

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ArchiveRowDto(
    val graphemes: List<String>,
    val marks: List<Int>,
)

@Serializable
data class ArchiveRunDto(
    val runId: String,
    val language: String,
    val puzzleDay: Long,
    val won: Boolean,
    val attempts: Int,
    val hardMode: Boolean,
    val rows: List<ArchiveRowDto> = emptyList(),
    val completedAt: Instant,
)

@Serializable
data class ArchiveHistoryDto(
    val runs: List<ArchiveRunDto> = emptyList(),
)

@Serializable
data class ArchiveUploadRequest(
    val runs: List<ArchiveRunDto>,
)

@Serializable
data class ArchiveUploadResponse(
    val acceptedCount: Int,
)
