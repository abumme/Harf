package uz.abumme.harfgame.data.wordpack

import kotlinx.serialization.Serializable

/**
 * A versioned vocabulary pack for one language, distributed over the air.
 *
 * The daily answer for a calendar day `d` is `schedule[d - anchorEpochDay]` when that index is in
 * range, otherwise `schedule[(d - anchorEpochDay) mod schedule.size]`. Updates only ever APPEND to
 * `schedule` (extending the horizon) and set [effectiveFrom] to the new segment's start, so any day
 * already within the published horizon is immutable.
 *
 * [answers] and [guesses] are raw (untokenized) words; the client tokenizes per its language config.
 */
@Serializable
data class WordPackDto(
    val lang: String,
    val version: String,
    /** epoch-day from which this version's schedule changes take effect (never in the past). */
    val effectiveFrom: Long,
    /** epoch-day mapped to `schedule[0]`. */
    val anchorEpochDay: Long,
    val answers: List<String>,
    val guesses: List<String>,
    val schedule: List<String>,
)
