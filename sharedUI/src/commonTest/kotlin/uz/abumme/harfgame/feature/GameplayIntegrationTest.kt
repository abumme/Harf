package uz.abumme.harfgame.feature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.feature.game.GameAction
import uz.abumme.harfgame.feature.game.GameStatus
import uz.abumme.harfgame.feature.game.GameViewModel
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class GameplayIntegrationTest {

    private val registry = LanguageRegistry()
    private val packs = WordPackRepository(registry)
    private val provider = DailyPuzzleProvider(registry, packs)
    private val instant = Instant.parse("2026-08-23T12:00:00Z")

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun offline_winning_round_per_language() = runTest {
        for (id in listOf("uz-latn", "uz-cyrl", "ru", "en", "kk")) {
            val puzzle = provider.daily(id, instant)
            val pack = packs.load(id)
            val vm = GameViewModel(puzzle, pack)

            // typing the answer is a valid guess and wins
            puzzle.answer.forEach { vm.onAction(GameAction.Input(it)) }
            vm.onAction(GameAction.Submit)

            assertEquals(GameStatus.Won, vm.state.value.status, "language $id should win on the answer")
        }
    }
}
