package uz.abumme.harfgame.engine

import uz.abumme.harfgame.engine.Mark.ABSENT
import uz.abumme.harfgame.engine.Mark.CORRECT
import uz.abumme.harfgame.engine.Mark.PRESENT
import kotlin.test.Test
import kotlin.test.assertEquals

class ScorerTest {

    private fun marks(guess: List<String>, answer: List<String>): List<Mark> =
        (Scorer.score(guess, answer) as ScoreResult.Scored).marks

    @Test
    fun all_correct() {
        val w = listOf("s", "h", "a", "h", "a") // arbitrary equal lists
        assertEquals(List(5) { CORRECT }, marks(w, w))
    }

    @Test
    fun digraph_matches_as_a_unit() {
        // guess has "sh" in wrong position; answer contains "sh" elsewhere -> PRESENT as a unit
        val answer = listOf("a", "sh", "b", "c", "d")
        val guess = listOf("sh", "x", "y", "z", "e")
        assertEquals(listOf(PRESENT, ABSENT, ABSENT, ABSENT, ABSENT), marks(guess, answer))
    }

    @Test
    fun surplus_duplicate_becomes_absent_and_correct_takes_priority() {
        // answer has one "a"; guess has "a" correct at 0 and an extra "a" at 2 -> extra ABSENT
        val answer = listOf("a", "b", "c")
        val guess = listOf("a", "x", "a")
        assertEquals(listOf(CORRECT, ABSENT, ABSENT), marks(guess, answer))
    }

    @Test
    fun present_when_duplicate_available() {
        val answer = listOf("a", "a", "b")
        val guess = listOf("a", "x", "a") // pos0 correct, pos2 present (second 'a' available)
        assertEquals(listOf(CORRECT, ABSENT, PRESENT), marks(guess, answer))
    }

    @Test
    fun mismatched_length_is_invalid() {
        assertEquals(ScoreResult.InvalidLength, Scorer.score(listOf("a", "b"), listOf("a", "b", "c")))
    }

    @Test
    fun language_mismatch_is_invalid() {
        val g = Word("en", listOf("a", "b"))
        val a = Word("ru", listOf("а", "б"))
        assertEquals(ScoreResult.LanguageMismatch, Scorer.score(g, a))
    }
}
