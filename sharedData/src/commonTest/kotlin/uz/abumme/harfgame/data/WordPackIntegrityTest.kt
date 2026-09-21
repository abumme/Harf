package uz.abumme.harfgame.data

import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackIntegrity
import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WordPackIntegrityTest {

    private fun pack(
        answers: List<String> = listOf("crane", "apple"),
        guesses: List<String> = listOf("crane", "quilt"),
        schedule: List<String> = listOf("apple", "crane"),
    ) = WordPackDto("en", "3", effectiveFrom = 0L, anchorEpochDay = 0L, answers = answers, guesses = guesses, schedule = schedule)

    private fun problemsOf(dto: WordPackDto): List<String> =
        assertIs<WordPackIntegrity.Invalid>(WordPackIntegrity.check(dto, LaunchLanguages.en)).problems

    @Test
    fun validPackIsTokenizedWithAnswersAndScheduleFoldedIntoGuesses() {
        val valid = assertIs<WordPackIntegrity.Valid>(
            WordPackIntegrity.check(pack(schedule = listOf("apple", "Plumb")), LaunchLanguages.en),
        )
        assertEquals(listOf("c", "r", "a", "n", "e"), valid.answers.first())
        assertEquals(listOf(listOf("a", "p", "p", "l", "e"), listOf("p", "l", "u", "m", "b")), valid.schedule)
        val words = valid.guesses.map { it.joinToString("") }.toSet()
        assertEquals(setOf("crane", "quilt", "apple", "plumb"), words)
    }

    @Test
    fun untokenizableAnswerIsRefused() {
        val problems = problemsOf(pack(answers = listOf("crane", "crâne")))
        assertTrue(problems.single().contains("answer not tokenizable"), problems.toString())
    }

    @Test
    fun untokenizableScheduleEntryIsRefused() {
        val problems = problemsOf(pack(schedule = listOf("apple", "кран")))
        assertTrue(problems.single().contains("schedule entry not tokenizable"), problems.toString())
    }

    @Test
    fun answerOutsideTheBoardLengthsIsRefused() {
        assertTrue(problemsOf(pack(answers = listOf("crane", "cat"))).single().contains("answer length 3"))
        assertTrue(problemsOf(pack(answers = listOf("crane", "strawberry"))).single().contains("answer length 10"))
    }

    @Test
    fun emptyAnswersOrScheduleAreRefused() {
        assertEquals(listOf("no answers"), problemsOf(pack(answers = emptyList())))
        assertEquals(listOf("no schedule"), problemsOf(pack(schedule = emptyList())))
    }

    @Test
    fun untokenizableGuessIsDroppedNotRefused() {
        val valid = assertIs<WordPackIntegrity.Valid>(
            WordPackIntegrity.check(pack(guesses = listOf("crane", "naïve", "x-ray")), LaunchLanguages.en),
        )
        val words = valid.guesses.map { it.joinToString("") }.toSet()
        assertEquals(setOf("crane", "apple"), words)
    }

    @Test
    fun uzbekDigraphsCountAsOneLetterAndApostrophesFold() {
        val dto = WordPackDto(
            "uz-latn", "1", 0L, 0L,
            answers = listOf("shahar", "o'g'il"), guesses = emptyList(), schedule = listOf("oʻgʻil"),
        )
        val valid = assertIs<WordPackIntegrity.Valid>(WordPackIntegrity.check(dto, LaunchLanguages.uzLatn))
        assertEquals(listOf("oʻ", "gʻ", "i", "l"), valid.answers[1])
        assertEquals(2, valid.guesses.size)
    }
}
