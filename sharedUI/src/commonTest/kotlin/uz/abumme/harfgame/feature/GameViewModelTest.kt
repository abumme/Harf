package uz.abumme.harfgame.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.data.stats.InProgressRow
import uz.abumme.harfgame.engine.Mark
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.game.GameAction
import uz.abumme.harfgame.feature.game.GameStatus
import uz.abumme.harfgame.feature.game.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        ),
    )

    private fun vm() = GameViewModel(DailyPuzzle("en", 100L, answer), pack)

    private fun GameViewModel.type(word: List<String>) {
        word.forEach { onAction(GameAction.Input(it)) }
        onAction(GameAction.Submit)
    }

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

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
