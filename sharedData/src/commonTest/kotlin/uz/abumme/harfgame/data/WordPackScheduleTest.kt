package uz.abumme.harfgame.data

import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WordPackScheduleTest {

    private val sampleAnswers = listOf("apple", "berry", "cherry", "date", "elder", "fig", "grape")
    private val seed = 42L
    private val anchor = WordPackSchedule.ANCHOR_EPOCH_DAY

    @Test
    fun extend_preserves_historical_answers_and_extends_horizon() {
        val original = WordPackSchedule.build(sampleAnswers, seed, horizon = 800)
        assertEquals(800, original.size)

        val extended = WordPackSchedule.extend(original, sampleAnswers, seed, newHorizon = 1200)
        assertEquals(1200, extended.size)

        // Historical prefix must be identical
        assertEquals(original, extended.subList(0, 800))

        // Old day resolves identically across pack versions
        for (day in listOf(anchor, anchor + 1, anchor + 50, anchor + 799)) {
            val fromOriginal = WordPackSchedule.archiveAnswerFor(original, anchor, day)
            val fromExtended = WordPackSchedule.archiveAnswerFor(extended, anchor, day)
            assertEquals(fromOriginal, fromExtended)
        }
    }

    @Test
    fun archive_answer_does_not_modulo_wrap_and_rejects_out_of_bounds() {
        val schedule = WordPackSchedule.build(sampleAnswers, seed, horizon = 800)

        // Valid within range
        assertEquals(schedule[0], WordPackSchedule.archiveAnswerFor(schedule, anchor, anchor))
        assertEquals(schedule[799], WordPackSchedule.archiveAnswerFor(schedule, anchor, anchor + 799))

        // Pre-anchor day must not wrap
        assertFailsWith<IllegalArgumentException> {
            WordPackSchedule.archiveAnswerFor(schedule, anchor, anchor - 1)
        }

        // Past horizon day must not wrap
        assertFailsWith<IllegalArgumentException> {
            WordPackSchedule.archiveAnswerFor(schedule, anchor, anchor + 800)
        }
        assertFailsWith<IllegalArgumentException> {
            WordPackSchedule.archiveAnswerFor(schedule, anchor, anchor + 1600)
        }
    }

    @Test
    fun extend_order_preserves_historical_indices() {
        val originalOrder = WordPackSchedule.buildOrder(sampleAnswers.size, seed, horizon = 800)
        val extendedOrder = WordPackSchedule.extendOrder(originalOrder, sampleAnswers.size, seed, newHorizon = 1000)

        assertEquals(1000, extendedOrder.size)
        assertEquals(originalOrder, extendedOrder.subList(0, 800))
    }
}
