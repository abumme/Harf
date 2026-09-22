package uz.abumme.harfgame.feature

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.engine.HardModeValidator
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.ScoreResult
import uz.abumme.harfgame.engine.Scorer
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords
import uz.abumme.harfgame.lang.UzbekScriptConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UzbekHardModeScriptSwitchTest {

    private val registry = LanguageRegistry()
    private val repo = WordPackRepository(registry)
    private val provider = DailyPuzzleProvider(registry, repo)

    @Test
    fun both_scripts_represent_the_same_lexeme() = runTest {
        val testInstant = Instant.parse("2026-08-23T12:00:00Z")
        val latn = provider.daily("uz-latn", testInstant)
        val cyrl = provider.daily("uz-cyrl", testInstant)

        assertEquals(latn.epochDay, cyrl.epochDay)
        val lexeme = UzbekDailyWords.lexemes.firstOrNull { it.graphemes("uz-latn") == latn.answer }
        assertNotNull(lexeme, "Latin answer must map to a daily lexeme")
        assertEquals(lexeme.graphemes("uz-cyrl"), cyrl.answer, "Cyrillic answer must be the exact same lexeme")
    }

    @Test
    fun hard_mode_constraints_preserved_when_switching_scripts() = runTest {
        val latnAnswer = listOf("k", "i", "t", "o", "b")
        val cyrlAnswer = listOf("к", "и", "т", "о", "б")

        // Guess in Latin: "bahor" -> 'b' is PRESENT (index 0), 'o' is PRESENT (index 3)
        val guessLatin = listOf("b", "a", "h", "o", "r")
        val marksLatin = (Scorer.score(guessLatin, latnAnswer) as ScoreResult.Scored).marks

        val latnRound = InProgressRound(
            languageId = "uz-latn",
            puzzleDay = 500L,
            rows = listOf(InProgressRow(guessLatin, marksLatin.map { it.ordinal })),
            current = emptyList(),
            hardMode = true,
        )

        // Switch to Cyrillic
        val cyrlRound = UzbekScriptConverter.convertRound(latnRound, "uz-cyrl", cyrlAnswer)

        assertTrue(cyrlRound.hardMode, "Hard mode flag must be preserved across script switch")
        assertEquals("uz-cyrl", cyrlRound.languageId)
        assertEquals(1, cyrlRound.rows.size)

        val cyrlRow = cyrlRound.rows.first()
        assertEquals(listOf("б", "а", "ҳ", "о", "р"), cyrlRow.graphemes)

        val rowsForValidator = cyrlRound.rows.map { it.graphemes to it.marks.map { Mark.entries[it] } }

        // Guess in Cyrillic attempting to bypass: "шаҳар" has neither 'б' nor 'о'
        val bypassGuess = listOf("ш", "а", "ҳ", "а", "р")
        val violation = HardModeValidator.validate(rowsForValidator, bypassGuess)
        assertNotNull(violation, "Switching script must not allow bypassing revealed clues")

        // Cyrillic guess keeping 'б' at index 0 (where it was PRESENT) must also be rejected
        val samePosGuess = listOf("б", "о", "д", "о", "м")
        val violationSamePos = HardModeValidator.validate(rowsForValidator, samePosGuess)
        assertNotNull(violationSamePos, "Present letter must not repeat in the same position in new script")

        // Cyrillic winning guess "китоб" satisfies all constraints
        val winningGuess = listOf("к", "и", "т", "о", "б")
        val winningViolation = HardModeValidator.validate(rowsForValidator, winningGuess)
        assertNull(winningViolation, "Valid guess in new script must be accepted")
    }

    @Test
    fun cyrillic_to_latin_switch_preserves_hard_mode_constraints() = runTest {
        val latnAnswer = listOf("k", "i", "t", "o", "b")
        val cyrlAnswer = listOf("к", "и", "т", "о", "б")

        // Guess in Cyrillic: "баҳор"
        val guessCyrl = listOf("б", "а", "ҳ", "о", "р")
        val marksCyrl = (Scorer.score(guessCyrl, cyrlAnswer) as ScoreResult.Scored).marks

        val cyrlRound = InProgressRound(
            languageId = "uz-cyrl",
            puzzleDay = 500L,
            rows = listOf(InProgressRow(guessCyrl, marksCyrl.map { it.ordinal })),
            current = emptyList(),
            hardMode = true,
        )

        // Switch to Latin
        val latnRound = UzbekScriptConverter.convertRound(cyrlRound, "uz-latn", latnAnswer)

        assertTrue(latnRound.hardMode)
        assertEquals("uz-latn", latnRound.languageId)
        assertEquals(listOf("b", "a", "h", "o", "r"), latnRound.rows.first().graphemes)

        val rowsForValidator = latnRound.rows.map { it.graphemes to it.marks.map { Mark.entries[it] } }
        val bypassGuess = listOf("s", "h", "a", "h", "a", "r")
        assertNotNull(HardModeValidator.validate(rowsForValidator, bypassGuess))
    }
}
