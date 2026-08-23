package uz.abumme.harfgame.data

import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.Streaks
import kotlin.test.Test
import kotlin.test.assertEquals

class StreaksTest {

    private fun won(lang: String, day: Long, attempts: Int = 3) = ResultRecord(lang, day, true, attempts)
    private fun lost(lang: String, day: Long) = ResultRecord(lang, day, false, 6)

    @Test
    fun consecutive_solves_increase_streak() {
        val r = listOf(won("en", 1), won("en", 2), won("en", 3))
        assertEquals(3, Streaks.streak(r, "en", today = 3).current)
        assertEquals(3, Streaks.streak(r, "en", today = 3).best)
    }

    @Test
    fun gap_or_loss_breaks_current_but_best_retained() {
        val r = listOf(won("en", 1), won("en", 2), lost("en", 3), won("en", 4))
        val s = Streaks.streak(r, "en", today = 4)
        assertEquals(1, s.current, "run ending at day 4 is length 1")
        assertEquals(2, s.best, "days 1-2 were the best run")
    }

    @Test
    fun streaks_are_independent_per_language() {
        val r = listOf(won("en", 1), won("en", 2), won("ru", 10))
        assertEquals(2, Streaks.streak(r, "en", today = 2).current)
        assertEquals(1, Streaks.streak(r, "ru", today = 10).current)
    }

    @Test
    fun current_lapses_when_last_win_is_stale() {
        val r = listOf(won("en", 1), won("en", 2), won("en", 3))
        // today is far past the last won day -> the daily streak has lapsed
        assertEquals(0, Streaks.streak(r, "en", today = 10).current)
        assertEquals(3, Streaks.streak(r, "en", today = 10).best, "best still remembers the run")
        // still live the day after the last win
        assertEquals(3, Streaks.streak(r, "en", today = 4).current)
    }

    @Test
    fun empty_stats_state() {
        assertEquals(0, Streaks.stats(emptyList(), "en").played)
        assertEquals(0f, Streaks.stats(emptyList(), "en").winRate)
        assertEquals(emptyMap(), Streaks.stats(emptyList(), "en").distribution)
    }

    @Test
    fun stats_played_winrate_distribution() {
        val r = listOf(won("en", 1, 3), won("en", 2, 4), lost("en", 3), won("en", 4, 3))
        val s = Streaks.stats(r, "en")
        assertEquals(4, s.played)
        assertEquals(0.75f, s.winRate)
        assertEquals(mapOf(3 to 2, 4 to 1), s.distribution)
    }
}
