package uz.abumme.harfgame.engine

/** Per-tile feedback. */
enum class Mark { CORRECT, PRESENT, ABSENT }

sealed interface ScoreResult {
    data class Scored(val marks: List<Mark>) : ScoreResult
    /** Guess and answer have different grapheme counts. */
    data object InvalidLength : ScoreResult
    /** Guess and answer belong to different languages. */
    data object LanguageMismatch : ScoreResult
}

/**
 * Two-pass grapheme scoring with Wordle's duplicate rule, at grapheme granularity:
 * digraphs match as whole units, correct positions take priority, and the number of
 * present/correct marks for a grapheme never exceeds its count in the answer.
 */
object Scorer {

    fun score(guess: List<String>, answer: List<String>): ScoreResult {
        if (guess.size != answer.size) return ScoreResult.InvalidLength

        val marks = MutableList(guess.size) { Mark.ABSENT }
        val remaining = HashMap<String, Int>()

        // pass 1: correct positions; count the rest of the answer's graphemes
        for (i in answer.indices) {
            if (guess[i] == answer[i]) {
                marks[i] = Mark.CORRECT
            } else {
                remaining[answer[i]] = (remaining[answer[i]] ?: 0) + 1
            }
        }
        // pass 2: present only while the answer still has that grapheme unaccounted for
        for (i in guess.indices) {
            if (marks[i] == Mark.CORRECT) continue
            val left = remaining[guess[i]] ?: 0
            if (left > 0) {
                marks[i] = Mark.PRESENT
                remaining[guess[i]] = left - 1
            }
        }
        return ScoreResult.Scored(marks)
    }

    /** Word overload — also validates that both words are the same language. */
    fun score(guess: Word, answer: Word): ScoreResult {
        if (guess.languageId != answer.languageId) return ScoreResult.LanguageMismatch
        return score(guess.graphemes, answer.graphemes)
    }
}
