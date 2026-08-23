package uz.abumme.harfgame.data

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.RoundStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultLogTest {

    // distinctive so persisted KSafe state from other runs can't interfere
    private val rec = ResultRecord("test-lang", 987654L, won = true, attempts = 4)

    @Test
    fun record_survives_reopen_and_dedups() = runTest {
        val ksafe = KSafe()
        val log = ResultLog(ksafe)
        log.record(rec)
        log.record(rec) // duplicate language-day ignored

        assertEquals(1, log.all().count { it == rec }, "duplicate ignored")

        val reopened = ResultLog(ksafe)
        assertTrue(reopened.all().any { it == rec }, "survives re-open")
    }

    @Test
    fun round_store_saves_restores_and_clears_by_day() = runTest {
        val store = RoundStore(KSafe())
        val round = InProgressRound(
            languageId = "test-lang",
            puzzleDay = 555L,
            rows = listOf(InProgressRow(listOf("a", "b"), listOf(0, 1))),
            current = listOf("c"),
        )
        store.save(round)
        assertEquals(round, store.load("test-lang", 555L))
        assertNull(store.load("test-lang", 556L), "different day = fresh round")
        store.clear()
        assertNull(store.load("test-lang", 555L), "cleared")
    }
}
