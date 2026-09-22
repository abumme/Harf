package uz.abumme.harfgame.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HardModeValidatorTest {

    @Test
    fun fixed_greens_must_be_kept_in_place() {
        val row1 = listOf("s", "t", "a", "r", "t") to listOf(Mark.CORRECT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT)
        val rows = listOf(row1)

        // Same green position kept -> valid
        val validGuess = listOf("s", "m", "i", "l", "e")
        assertNull(HardModeValidator.validate(rows, validGuess))

        // Green position changed -> violation
        val invalidGuess = listOf("p", "m", "i", "l", "e")
        val violation = HardModeValidator.validate(rows, invalidGuess)
        assertNotNull(violation)
        val v = assertIs<HardModeViolation.CorrectPositionChanged>(violation)
        assertEquals(0, v.position)
        assertEquals("s", v.expected)
        assertEquals("p", v.actual)
    }

    @Test
    fun displaced_yellows_must_move_and_not_repeat_in_same_position() {
        // 't' is present at index 1
        val row1 = listOf("s", "t", "a", "r", "e") to listOf(Mark.ABSENT, Mark.PRESENT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT)
        val rows = listOf(row1)

        // 't' moved to index 3 -> valid
        val validGuess = listOf("p", "o", "r", "t", "s")
        assertNull(HardModeValidator.validate(rows, validGuess))

        // 't' kept at index 1 -> violation
        val samePosGuess = listOf("a", "t", "l", "a", "s")
        val v1 = assertIs<HardModeViolation.PresentGraphemeAtSamePosition>(HardModeValidator.validate(rows, samePosGuess))
        assertEquals(1, v1.position)
        assertEquals("t", v1.grapheme)

        // 't' omitted entirely -> violation
        val omittedGuess = listOf("c", "l", "o", "u", "d")
        val v2 = assertIs<HardModeViolation.MinimumCountNotSatisfied>(HardModeValidator.validate(rows, omittedGuess))
        assertEquals("t", v2.grapheme)
        assertEquals(1, v2.requiredCount)
        assertEquals(0, v2.actualCount)
    }

    @Test
    fun repeated_minimum_counts_are_enforced() {
        // Two 'e' graphemes revealed (one PRESENT, one CORRECT)
        val row1 = listOf("s", "p", "e", "e", "d") to listOf(Mark.ABSENT, Mark.ABSENT, Mark.PRESENT, Mark.CORRECT, Mark.ABSENT)
        val rows = listOf(row1)

        // 2 'e's present (with index 3 kept) -> valid
        val validGuess = listOf("e", "l", "d", "e", "r")
        assertNull(HardModeValidator.validate(rows, validGuess))

        // Only 1 'e' present -> violation
        val singleEGuess = listOf("m", "e", "r", "e", "t") // wait, "meret" has 2 'e's!
        val oneEGuess = listOf("a", "b", "o", "e", "s")
        val v = assertIs<HardModeViolation.MinimumCountNotSatisfied>(HardModeValidator.validate(rows, oneEGuess))
        assertEquals("e", v.grapheme)
        assertEquals(2, v.requiredCount)
        assertEquals(1, v.actualCount)
    }

    @Test
    fun gray_duplicates_do_not_prohibit_confirmed_copies() {
        // First 'e' is ABSENT, second 'e' is PRESENT
        val row1 = listOf("e", "r", "a", "s", "e") to listOf(Mark.ABSENT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT, Mark.PRESENT)
        val rows = listOf(row1)

        // Guess with single 'e' at a new position (index 1) -> valid, the absent 'e' at index 0 didn't forbid 'e'
        val validGuess = listOf("b", "e", "l", "o", "w")
        assertNull(HardModeValidator.validate(rows, validGuess))
    }

    @Test
    fun clues_accumulate_across_multiple_rows() {
        // Row 1 confirms 's' at index 0
        val row1 = listOf("s", "t", "a", "r", "e") to listOf(Mark.CORRECT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT, Mark.ABSENT)
        // Row 2 confirms 'l' is present (at index 3) and 'i' at index 2 is correct
        val row2 = listOf("s", "p", "i", "l", "l") to listOf(Mark.CORRECT, Mark.ABSENT, Mark.CORRECT, Mark.PRESENT, Mark.ABSENT)
        val rows = listOf(row1, row2)

        // Must have 's' at 0, 'i' at 2, and 'l' somewhere other than 3
        val validGuess = listOf("s", "l", "i", "m", "e")
        assertNull(HardModeValidator.validate(rows, validGuess))

        // Violating row 1's green
        val badRow1 = listOf("c", "l", "i", "m", "b")
        assertIs<HardModeViolation.CorrectPositionChanged>(HardModeValidator.validate(rows, badRow1))

        // Violating row 2's green
        val badRow2Green = listOf("s", "l", "a", "m", "e")
        assertIs<HardModeViolation.CorrectPositionChanged>(HardModeValidator.validate(rows, badRow2Green))

        // Violating row 2's yellow position
        val badRow2YellowPos = listOf("s", "u", "i", "l", "t")
        assertIs<HardModeViolation.PresentGraphemeAtSamePosition>(HardModeValidator.validate(rows, badRow2YellowPos))
    }
}
