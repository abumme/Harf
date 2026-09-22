package uz.abumme.harfgame.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.data.sync.RoundKind
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.game.GameAction
import uz.abumme.harfgame.feature.game.GameEvent
import uz.abumme.harfgame.feature.game.GameStatus
import uz.abumme.harfgame.feature.game.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val answer = listOf("b", "r", "e", "a", "d")
    private val pack = WordPack(
        languageId = "en",
        answers = listOf(answer),
        guesses = setOf(
            answer,
            listOf("c", "r", "a", "n", "e"),
            listOf("s", "l", "a", "t", "e"),
            listOf("p", "l", "a", "n", "t"),
            listOf("w", "a", "t", "e", "r"),
            listOf("l", "i", "g", "h", "t"),
            listOf("m", "o", "n", "e", "y"),
            listOf("r", "b", "e", "a", "d"),
            listOf("b", "r", "e", "e", "d"),
        ),
    )

    private fun vm(
        restore: InProgressRound? = null,
        onFinish: (ResultRecord) -> Unit = {},
    ) = GameViewModel(DailyPuzzle("en", 100L, answer), pack, restore, onFinish = onFinish)

    private fun GameViewModel.type(word: List<String>) {
        word.forEach { onAction(GameAction.Input(it)) }
        onAction(GameAction.Submit)
    }

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun hard_mode_violating_guess_leaves_board_and_attempts_unchanged() = runTest {
        val vm = vm()
        vm.onAction(GameAction.SetHardMode(true))
        assertTrue(vm.state.value.hardMode)

        // Guess 1: "crane" against "bread"
        // 'c' ABSENT, 'r' CORRECT (index 1), 'a' PRESENT, 'n' ABSENT, 'e' PRESENT
        vm.type(listOf("c", "r", "a", "n", "e"))
        assertEquals(1, vm.state.value.submitted.size)

        var emittedViolation: GameEvent.HardModeViolation? = null
        backgroundScope.launch {
            vm.events.collect { if (it is GameEvent.HardModeViolation) emittedViolation = it }
        }

        // Violating guess: "plant" does not keep 'r' at index 1 and omits 'e'
        vm.type(listOf("p", "l", "a", "n", "t"))
        testScheduler.runCurrent()

        // Attempt not consumed, board still has the typed word, violation event fired
        assertEquals(1, vm.state.value.submitted.size)
        assertEquals(listOf("p", "l", "a", "n", "t"), vm.state.value.current)
        assertIs<GameEvent.HardModeViolation>(emittedViolation)

        // Clear and type valid word ("bread") that satisfies all clues -> scores and wins
        repeat(5) { vm.onAction(GameAction.Delete) }
        vm.type(answer)
        assertEquals(2, vm.state.value.submitted.size)
        assertEquals(GameStatus.Won, vm.state.value.status)
    }

    @Test
    fun mode_selection_persists_and_locks_after_first_guess() = runTest {
        var finishedRecord: ResultRecord? = null
        val vm = vm(onFinish = { finishedRecord = it })

        // Toggle before any guess
        vm.onAction(GameAction.SetHardMode(true))
        assertTrue(vm.state.value.hardMode)
        vm.onAction(GameAction.ToggleHardMode)
        assertFalse(vm.state.value.hardMode)
        vm.onAction(GameAction.SetHardMode(true))
        assertTrue(vm.state.value.hardMode)

        // Submit first guess
        vm.type(listOf("c", "r", "a", "n", "e"))
        assertEquals(1, vm.state.value.submitted.size)

        // Attempting to change mode after first guess is blocked
        vm.onAction(GameAction.SetHardMode(false))
        assertTrue(vm.state.value.hardMode, "Mode change must be ignored after first guess")
        vm.onAction(GameAction.ToggleHardMode)
        assertTrue(vm.state.value.hardMode, "Toggle mode must be ignored after first guess")

        // Snapshot preserves mode
        val snapshot = vm.snapshot()
        assertTrue(snapshot.hardMode)
        assertEquals(RoundKind.OFFICIAL, snapshot.roundKind)

        // Restore preserves mode
        val restoredVm = vm(restore = snapshot, onFinish = { finishedRecord = it })
        assertTrue(restoredVm.state.value.hardMode)
        restoredVm.type(answer)
        assertEquals(GameStatus.Won, restoredVm.state.value.status)

        // Exactly one official record reported with hardMode = true
        assertEquals(ResultRecord("en", 100L, true, 2, RoundKind.OFFICIAL, true), finishedRecord)
    }

    @Test
    fun restored_finished_round_stays_finished() = runTest {
        val winRow = InProgressRow(answer, List(answer.size) { Mark.CORRECT.ordinal })
        val restore = InProgressRound(languageId = "en", puzzleDay = 100L, rows = listOf(winRow), current = emptyList())
        val vm = GameViewModel(DailyPuzzle("en", 100L, answer), pack, restore)
        assertEquals(GameStatus.Won, vm.state.value.status, "restored winning round reopens as Won")
        vm.onAction(GameAction.Input("x")) // input ignored once the round is over
        assertEquals(0, vm.state.value.current.size)
    }

    @Test
    fun full_win() = runTest {
        val vm = vm()
        vm.type(answer)
        assertEquals(GameStatus.Won, vm.state.value.status)
        assertEquals(1, vm.state.value.submitted.size)
    }

    @Test
    fun full_loss_reveals_answer() = runTest {
        val vm = vm()
        repeat(6) { vm.type(listOf("c", "r", "a", "n", "e")) }
        assertEquals(GameStatus.Lost, vm.state.value.status)
        assertEquals(answer, vm.state.value.revealed)
    }

    @Test
    fun invalid_guess_does_not_consume_attempt() = runTest {
        val vm = vm()
        vm.type(listOf("z", "z", "z", "z", "z")) // valid graphemes, not in dictionary
        assertEquals(0, vm.state.value.submitted.size)
        assertEquals(GameStatus.Playing, vm.state.value.status)
    }

    @Test
    fun key_state_never_downgrades() = runTest {
        val vm = vm()
        vm.type(listOf("r", "b", "e", "a", "d")) // r present, b present, e/a/d correct
        assertEquals(Mark.PRESENT, vm.state.value.keyStates["r"])
        vm.type(answer) // win: r now correct
        assertEquals(Mark.CORRECT, vm.state.value.keyStates["r"])
        assertEquals(GameStatus.Won, vm.state.value.status)
    }
}
