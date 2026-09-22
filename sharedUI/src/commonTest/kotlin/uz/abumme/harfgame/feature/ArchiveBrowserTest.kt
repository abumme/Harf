package uz.abumme.harfgame.feature

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.Entitlements
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
}
