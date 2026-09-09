package uz.abumme.harfgame.games

import uz.abumme.harfgame.data.stats.ResultRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamesProgressTest {

    @Test
    fun computesAggregatesAcrossLanguages() {
        val records = listOf(
            ResultRecord("en", 1, won = true, attempts = 4),
            ResultRecord("en", 2, won = true, attempts = 1), // hole in one
            ResultRecord("en", 3, won = true, attempts = 3),
            ResultRecord("en", 5, won = false, attempts = 6),
            ResultRecord("ru", 1, won = true, attempts = 2),
        )
        val p = GamesProgress.from(records, listOf("en", "ru"))

        assertEquals(4, p.totalWins)          // en:1,2,3 + ru:1
        assertEquals(3, p.bestStreak)         // en days 1-2-3 consecutive
        assertTrue(p.holeInOne)               // en day 2 solved in 1
        assertTrue(p.hasFirstWin)
    }

    @Test
    fun emptyHistoryIsAllZero() {
        val p = GamesProgress.from(emptyList(), listOf("en"))
        assertEquals(0, p.bestStreak)
        assertEquals(0, p.totalWins)
        assertFalse(p.holeInOne)
        assertFalse(p.hasFirstWin)
    }

    @Test
    fun noOpIsUnavailableAndDoesNothing() {
        val g = NoOpGamesServices()
        assertFalse(g.isAvailable)
        // Must not throw.
        g.submitProgress(GamesProgress(10, 100, true))
        g.showLeaderboards()
        g.showAchievements()
    }
}
