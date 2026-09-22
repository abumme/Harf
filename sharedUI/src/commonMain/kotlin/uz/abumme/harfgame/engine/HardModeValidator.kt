package uz.abumme.harfgame.engine

sealed interface HardModeViolation {
    /** A previously confirmed green clue at [position] was changed to [actual] instead of [expected]. */
    data class CorrectPositionChanged(
        val position: Int,
        val expected: String,
        val actual: String,
    ) : HardModeViolation

    /** A previously confirmed yellow clue for [grapheme] was repeated at the same [position]. */
    data class PresentGraphemeAtSamePosition(
        val position: Int,
        val grapheme: String,
    ) : HardModeViolation

    /** A grapheme previously revealed as present/correct requires at least [requiredCount] occurrences, but [actualCount] were supplied. */
    data class MinimumCountNotSatisfied(
        val grapheme: String,
        val requiredCount: Int,
        val actualCount: Int,
    ) : HardModeViolation
}

data class AccumulatedClues(
    val fixedPositions: Map<Int, String> = emptyMap(),
    val forbiddenPositions: Set<Pair<Int, String>> = emptySet(),
    val minCounts: Map<String, Int> = emptyMap(),
)

object HardModeValidator {

    fun accumulate(rows: List<Pair<List<String>, List<Mark>>>): AccumulatedClues {
        val fixed = mutableMapOf<Int, String>()
        val forbidden = mutableSetOf<Pair<Int, String>>()
        val minCounts = mutableMapOf<String, Int>()

        for ((graphemes, marks) in rows) {
            val rowCounts = mutableMapOf<String, Int>()
            for (i in graphemes.indices) {
                if (i >= marks.size) continue
                val g = graphemes[i]
                when (marks[i]) {
                    Mark.CORRECT -> {
                        fixed[i] = g
                        rowCounts[g] = (rowCounts[g] ?: 0) + 1
                    }
                    Mark.PRESENT -> {
                        forbidden.add(i to g)
                        rowCounts[g] = (rowCounts[g] ?: 0) + 1
                    }
                    Mark.ABSENT -> {
                        // Gray duplicates: absent mark does not prohibit confirmed copies.
                    }
                }
            }
            for ((g, count) in rowCounts) {
                val currentMax = minCounts[g] ?: 0
                if (count > currentMax) {
                    minCounts[g] = count
                }
            }
        }
        return AccumulatedClues(fixed, forbidden, minCounts)
    }

    fun validate(clues: AccumulatedClues, guess: List<String>): HardModeViolation? {
        // 1. Fixed greens: every confirmed correct position must be retained.
        for ((pos, expected) in clues.fixedPositions) {
            val actual = guess.getOrNull(pos) ?: ""
            if (actual != expected) {
                return HardModeViolation.CorrectPositionChanged(pos, expected, actual)
            }
        }

        // 2. Displaced yellows: a grapheme marked present at pos must not be re-submitted at pos.
        for ((pos, grapheme) in clues.forbiddenPositions) {
            if (guess.getOrNull(pos) == grapheme) {
                return HardModeViolation.PresentGraphemeAtSamePosition(pos, grapheme)
            }
        }

        // 3. Minimum counts: all confirmed present/correct graphemes must appear in at least the confirmed count.
        for ((grapheme, requiredCount) in clues.minCounts) {
            val actualCount = guess.count { it == grapheme }
            if (actualCount < requiredCount) {
                return HardModeViolation.MinimumCountNotSatisfied(grapheme, requiredCount, actualCount)
            }
        }

        return null
    }

    fun validate(rows: List<Pair<List<String>, List<Mark>>>, guess: List<String>): HardModeViolation? =
        validate(accumulate(rows), guess)
}
