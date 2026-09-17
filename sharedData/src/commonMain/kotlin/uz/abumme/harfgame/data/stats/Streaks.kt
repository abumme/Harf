package uz.abumme.harfgame.data.stats

import uz.abumme.harfgame.data.sync.ResultRecordDto

data class StreakStats(val current: Int, val best: Int)

data class PlayerStats(
    val played: Int,
    val winRate: Float,
    /** attempts -> number of wins solved in that many attempts. */
    val distribution: Map<Int, Int>,
) {
    companion object {
        val EMPTY = PlayerStats(0, 0f, emptyMap())
    }
}

/**
 * Pure replay of the result log. Streaks and stats are computed per language, never stored
 * as a mutable counter, so the value is always consistent with history.
 *
 * Shared by the app's stats screen (which maps its local records with `toDto()`) and the server's player detail in the
 * staff panel, so both show the same numbers for the same records.
 */
object Streaks {

    /**
     * Per-language current and best consecutive-solved-day streak. [today] is the current puzzle
     * day for the language; the current streak lapses to 0 once the most recent won day is neither
     * today nor yesterday (a missed daily breaks the streak).
     */
    fun streak(records: List<ResultRecordDto>, language: String, today: Long): StreakStats {
        val days = records.filter { it.language == language && it.won }
            .map { it.puzzleDay }
            .distinct()
            .sorted()
        if (days.isEmpty()) return StreakStats(0, 0)

        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == days[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        // current streak = the run ending at the most recent won day, but only while it is still
        // "live" — i.e. that day is today or yesterday; otherwise the daily streak has lapsed.
        val last = days.last()
        val current = if (today - last in 0..1) {
            var c = 1
            for (i in days.indices.reversed()) {
                if (i == days.lastIndex) continue
                if (days[i] == days[i + 1] - 1) c++ else break
            }
            c
        } else {
            0
        }
        return StreakStats(current, best)
    }

    fun stats(records: List<ResultRecordDto>, language: String): PlayerStats {
        val forLang = records.filter { it.language == language }
        if (forLang.isEmpty()) return PlayerStats.EMPTY
        val wins = forLang.filter { it.won }
        val distribution = wins.groupingBy { it.attempts }.eachCount()
        return PlayerStats(
            played = forLang.size,
            winRate = wins.size.toFloat() / forLang.size,
            distribution = distribution,
        )
    }
}
