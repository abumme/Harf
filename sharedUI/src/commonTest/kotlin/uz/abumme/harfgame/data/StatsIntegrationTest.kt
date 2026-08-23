package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.Streaks
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsIntegrationTest {

    @Test
    fun multi_day_log_drives_streak_and_stats() = runTest {
        val log = ResultLog(KSafe())
        val lang = "sim-lang"
        // solves days 1-3, miss/loss day 4 (idempotent thanks to per-day dedup)
        listOf(1L, 2L, 3L).forEach { log.record(ResultRecord(lang, it, won = true, attempts = 3)) }
        log.record(ResultRecord(lang, 4L, won = false, attempts = 6))

        val records = log.all()
        val streak = Streaks.streak(records, lang)
        assertEquals(3, streak.current, "trailing run of solved days 1-3")
        assertEquals(3, streak.best)

        val stats = Streaks.stats(records, lang)
        assertEquals(4, stats.played)
        assertEquals(0.75f, stats.winRate)
    }
}
