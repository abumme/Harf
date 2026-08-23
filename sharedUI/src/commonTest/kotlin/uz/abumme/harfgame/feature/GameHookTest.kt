package uz.abumme.harfgame.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.data.stats.ResultRecord
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.game.GameAction
import uz.abumme.harfgame.feature.game.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class GameHookTest {

    private val answer = listOf("b", "r", "e", "a", "d")
    private val pack = WordPack("en", listOf(answer), setOf(answer))

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun round_end_produces_exactly_one_record() = runTest {
        val records = mutableListOf<ResultRecord>()
        val vm = GameViewModel(DailyPuzzle("en", 42L, answer), pack) { records.add(it) }

        answer.forEach { vm.onAction(GameAction.Input(it)) }
        vm.onAction(GameAction.Submit)

        assertEquals(1, records.size)
        assertEquals(ResultRecord("en", 42L, won = true, attempts = 1), records[0])

        // further actions after finish are ignored — no extra record
        vm.onAction(GameAction.Input("x"))
        vm.onAction(GameAction.Submit)
        assertEquals(1, records.size)
    }
}
