package uz.abumme.harfgame.feature

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import uz.abumme.harfgame.lang.UzbekDailyWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

    @Test
    fun historical_returns_matching_puzzle_for_valid_past_day() = runTest {
        for (lang in listOf("en", "ru", "kk", "uz-latn", "uz-cyrl")) {
            val pastDay = provider.epochDay(lang, day1)
            val historicalPuzzle = provider.historical(lang, pastDay, day2)
            val dailyPuzzle = provider.daily(lang, day1)
            assertEquals(pastDay, historicalPuzzle.epochDay)
            assertEquals(dailyPuzzle.answer, historicalPuzzle.answer)
        }
    }

    @Test
    fun historical_rejects_pre_publication_today_future_and_out_of_range_days_in_each_timezone() = runTest {
        // Test near midnight transition: 2026-08-23T21:30:00Z
        // Moscow (UTC+3): 2026-08-24 00:30 (next day)
        // Tashkent (UTC+5): 2026-08-24 02:30 (next day)
        // UTC: 2026-08-23
        val midnightEdge = Instant.parse("2026-08-23T21:30:00Z")

        for (lang in listOf("en", "ru", "kk", "uz-latn", "uz-cyrl")) {
            val today = provider.epochDay(lang, midnightEdge)
            val firstPublic = provider.firstPublicDay(lang)

            // 1. Pre-publication day rejection
            assertFailsWith<IllegalArgumentException> {
                provider.historical(lang, firstPublic - 1, midnightEdge)
            }

            // 2. Today rejection
            assertFailsWith<IllegalArgumentException> {
                provider.historical(lang, today, midnightEdge)
            }

            // 3. Future day rejection
            assertFailsWith<IllegalArgumentException> {
                provider.historical(lang, today + 1, midnightEdge)
            }

            // 4. Far out of range rejection
            assertFailsWith<IllegalArgumentException> {
                provider.historical(lang, today + 100_000L, midnightEdge)
            }
        }
    }
}
