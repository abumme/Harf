package uz.abumme.harfgame.data.stats

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** One finished daily round. `puzzleDay` is the epoch-day in that language's fixed timezone. */
@Serializable
data class ResultRecord(
    val language: String,
    val puzzleDay: Long,
    val won: Boolean,
    val attempts: Int,
)

/** Versioned container for the append-only result log. */
@Serializable
data class ResultLogData(
    val version: Int = 1,
    val records: List<ResultRecord> = emptyList(),
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
)

/** A submitted row inside an in-progress round; marks stored as ordinals to avoid annotating the engine enum. */
@Serializable
data class InProgressRow(
    val graphemes: List<String>,
    val marks: List<Int>,
)

/** Versioned snapshot of the current day's in-progress round. */
@Serializable
data class InProgressRound(
    val version: Int = 1,
    val languageId: String,
    val puzzleDay: Long,
    val rows: List<InProgressRow>,
    val current: List<String>,
)

/** Nullable holder so KSafe can store "no round in progress" with a non-null default. */
@Serializable
data class RoundHolder(val round: InProgressRound? = null)
