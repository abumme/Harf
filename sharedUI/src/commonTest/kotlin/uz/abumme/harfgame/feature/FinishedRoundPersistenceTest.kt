package uz.abumme.harfgame.feature

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.game.GameAction
import uz.abumme.harfgame.feature.game.GameStatus
import uz.abumme.harfgame.feature.game.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * B11: a finished daily round must persist and reopen as its result — not a blank editable board —
 * and must not log a duplicate result on reopen. Exercises the exact save→load round-trip that
 * GameScreen performs (persist finished snapshots; never clear on completion).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishedRoundPersistenceTest {

    private val answer = listOf("b", "r", "e", "a", "d")
    private val pack = WordPack(
        languageId = "en",
        answers = listOf(answer),
        guesses = setOf(answer),
    )

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun finishedRoundPersistsAndReopensAsResult() = runTest {
        val ksafe = KSafe()
        val roundStore = RoundStore(ksafe)
        roundStore.clear("en")
        val resultLog = ResultLog(ksafe)
        resultLog.clear()

        val puzzle = DailyPuzzle("en", 100L, answer)

        // Play and win, recording the result once (as GameScreen's onFinish does).
        var finishes = 0
        val vm = GameViewModel(puzzle, pack) { record ->
            finishes++
            // idempotent per (language, puzzleDay)
            kotlinx.coroutines.runBlocking { resultLog.record(record) }
        }
        answer.forEach { vm.onAction(GameAction.Input(it)) }
        vm.onAction(GameAction.Submit)
        assertEquals(GameStatus.Won, vm.state.value.status)

        // GameScreen persists the finished snapshot (no clear on completion).
        roundStore.save(vm.snapshot())

        // Relaunch: the finished round is restored, not dropped, and reopens as the result.
        val restored = roundStore.load("en", 100L)
        assertNotNull(restored, "finished round must be restored on relaunch")
        val reopened = GameViewModel(puzzle, pack, restored)
        assertEquals(GameStatus.Won, reopened.state.value.status, "reopens as result, not a blank board")
        assertEquals(1, reopened.state.value.submitted.size)

        // Reopening does not fire onFinish again, so no duplicate result is logged.
        assertEquals(1, finishes)
        assertEquals(1, resultLog.all().count { it.puzzleDay == 100L && it.language == "en" })
    }
}
