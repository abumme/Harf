package uz.abumme.harfgame.data.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ResultRecordDto(
    val language: String,
    val puzzleDay: Long,
    val won: Boolean,
    val attempts: Int,
)

@Serializable
data class UserStatsDto(
    val updatedAt: Instant,
    val records: List<ResultRecordDto> = emptyList(),
)
