package uz.abumme.harfgame.feature

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DailyPuzzleTest {

    private val registry = LanguageRegistry()
    private val provider = DailyPuzzleProvider(registry, WordPackRepository(registry))

    private val day1 = Instant.parse("2026-08-23T12:00:00Z")
    private val day2 = Instant.parse("2026-08-24T12:00:00Z")

    @Test
    fun same_day_same_word_and_consecutive_days_differ() = runTest {
        assertEquals(provider.daily("en", day1).answer, provider.daily("en", day1).answer)
        assertNotEquals(provider.daily("en", day1).answer, provider.daily("en", day2).answer)
    }

    @Test
    fun uzbek_same_day_resolves_one_lexeme_in_both_scripts() = runTest {
        val latn = provider.daily("uz-latn", day1)
        val cyrl = provider.daily("uz-cyrl", day1)
        assertEquals(latn.epochDay, cyrl.epochDay)

        // Whatever the schedule picked, both scripts must resolve to the SAME lexeme that day.
        val lexeme = UzbekDailyWords.lexemes.first { it.graphemes("uz-latn") == latn.answer }
        assertEquals(lexeme.graphemes("uz-cyrl"), cyrl.answer)
    }
}
