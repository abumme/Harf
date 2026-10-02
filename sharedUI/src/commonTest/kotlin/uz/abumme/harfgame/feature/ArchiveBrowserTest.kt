package uz.abumme.harfgame.feature

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.data.archive.ArchiveRunDto
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.sync.RoundKind
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.archive.ArchiveDayBrowser
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ArchiveBrowserTest {

    private val registry = LanguageRegistry()
    private val repo = WordPackRepository(registry)
    private val provider = DailyPuzzleProvider(registry, repo)

    @Test
    fun non_owner_sees_locked_gate_and_owner_unlocked() {
        val nonOwner = Entitlements(lifetime = false)
        assertFalse(EntitlementGate.lifetimeExtrasUnlocked(nonOwner), "Non-owner must be locked")

        val owner = Entitlements(lifetime = true)
        assertTrue(EntitlementGate.lifetimeExtrasUnlocked(owner), "Owner must be unlocked")
    }

    @Test
    fun owner_can_select_every_published_past_day_without_exposing_today() = runTest {
        // Choose a fixed time several days past anchor/first published day
        val testInstant = Instant.parse("2026-08-25T12:00:00Z")

        for (lang in listOf("en", "ru", "kk", "uz-latn", "uz-cyrl")) {
            val firstDay = provider.firstPublicDay(lang)
            val today = provider.epochDay(lang, testInstant)

            assertTrue(today > firstDay, "Test day must be after first public day")

            val available = ArchiveDayBrowser.availableDays(provider, lang, testInstant)

            // Must include first day through yesterday
            assertEquals(firstDay, available.first())
            assertEquals(today - 1, available.last())
            assertEquals((today - firstDay).toInt(), available.size)

            // Today and future MUST NOT be exposed
            assertFalse(available.contains(today), "Today must never appear in archive")
            assertFalse(available.contains(today + 1), "Future days must never appear in archive")
            assertFalse(available.contains(firstDay - 1), "Pre-publication days must never appear in archive")

            // Range check helper validation
            assertTrue(ArchiveDayBrowser.isDayAvailable(provider, lang, firstDay, testInstant))
            assertTrue(ArchiveDayBrowser.isDayAvailable(provider, lang, today - 1, testInstant))
            assertFalse(ArchiveDayBrowser.isDayAvailable(provider, lang, today, testInstant))
            assertFalse(ArchiveDayBrowser.isDayAvailable(provider, lang, today + 1, testInstant))
            assertFalse(ArchiveDayBrowser.isDayAvailable(provider, lang, firstDay - 1, testInstant))
        }
    }

    private fun run(lang: String, day: Long, won: Boolean, attempts: Int, at: Long) = ArchiveRunDto(
        runId = "r$at", language = lang, puzzleDay = day, won = won, attempts = attempts,
        hardMode = false, completedAt = Instant.fromEpochMilliseconds(at),
    )

    @Test
    fun rows_show_the_latest_archive_playthrough_else_the_daily_played_that_day() {
        val records = listOf(
            ResultRecord("en", 10, won = true, attempts = 4),
            ResultRecord("en", 11, won = false, attempts = 6),
            ResultRecord("ru", 12, won = true, attempts = 2),
            ResultRecord("en", 13, won = true, attempts = 1, roundKind = RoundKind.ARCHIVE),
        )
        val runs = listOf(
            run("en", 11, won = true, attempts = 5, at = 1),
            run("en", 11, won = true, attempts = 3, at = 2),
            run("ru", 10, won = false, attempts = 6, at = 3),
        )
        val en = ArchiveDayBrowser.results("en", runs, records)
        assertEquals(ArchiveDayBrowser.DayResult(won = true, attempts = 4), en[10], "the daily played that day")
        assertEquals(ArchiveDayBrowser.DayResult(won = true, attempts = 3), en[11], "the latest replay wins")
        assertEquals(null, en[12], "another language's day is not played in en")
        assertEquals(null, en[13], "only official daily records count from the result log")

        val uz = ArchiveDayBrowser.results(
            "uz-latn",
            listOf(run("uz-cyrl", 20, won = true, attempts = 2, at = 1)),
            listOf(ResultRecord("uz-latn", 21, won = false, attempts = 6)),
        )
        assertEquals(setOf(20L, 21L), uz.keys, "both Uzbek scripts count for the Uzbek archive")
    }

    @Test
    fun rows_are_labelled_with_the_calendar_date_not_the_epoch_day() {
        assertEquals("01.10.2026", ArchiveDayBrowser.dateLabel(20727))
        assertEquals("31.12.1969", ArchiveDayBrowser.dateLabel(-1))
    }

    @Test
    fun an_archived_day_resolves_its_own_word_not_todays() = runTest {
        val now = Instant.parse("2026-08-25T12:00:00Z")
        val lang = "en"
        val today = provider.epochDay(lang, now)
        val daily = provider.daily(lang, now)
        // Within any short stretch at least one past day carries a different word than today.
        val past = (1..5L).map { provider.historical(lang, today - it, now) }
        assertTrue(past.all { it.epochDay < today })
        assertTrue(past.any { it.answer != daily.answer }, "archive days must not all show today's word")
    }
}
