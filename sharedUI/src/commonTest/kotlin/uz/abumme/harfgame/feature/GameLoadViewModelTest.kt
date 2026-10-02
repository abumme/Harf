package uz.abumme.harfgame.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.feature.game.GameLoadViewModel
import uz.abumme.harfgame.feature.game.Loaded
import uz.abumme.harfgame.lang.LanguageRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The game's round loads outside composition and stays published until the next one is ready. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameLoadViewModelTest {
    private val registry = LanguageRegistry()

    @BeforeTest
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private fun loaded(script: String) = Loaded(
        puzzle = DailyPuzzle(script, 1L, listOf("a")),
        config = registry.config(script)!!,
        pack = WordPack(script, emptyList(), emptySet()),
        restore = null,
    )

    /** A loader whose rounds complete only when the test says so. */
    private class Gate {
        val pending = HashMap<String, CompletableDeferred<Loaded>>()
        var calls = 0
        suspend fun load(script: String): Loaded {
            calls++
            return pending.getOrPut(script) { CompletableDeferred() }.await()
        }
        fun finish(script: String, value: Loaded) = pending.getOrPut(script) { CompletableDeferred() }.complete(value)
    }

    @Test
    fun selectPublishesTheLoadedRound() = runTest {
        val gate = Gate()
        val vm = GameLoadViewModel(gate::load)
        vm.select("en")
        assertNull(vm.loaded.value, "nothing until the round is ready")
        assertEquals("en", vm.selected.value)

        val round = loaded("en")
        gate.finish("en", round)
        assertSame(round.puzzle, vm.loaded.value?.puzzle)
    }

    @Test
    fun switchingScriptKeepsTheCurrentRoundUntilTheOtherIsReady() = runTest {
        val gate = Gate()
        val vm = GameLoadViewModel(gate::load)
        val latin = loaded("uz-latn")
        vm.select("uz-latn")
        gate.finish("uz-latn", latin)

        vm.select("uz-cyrl")
        assertEquals("uz-cyrl", vm.selected.value)
        assertSame(latin.puzzle, vm.loaded.value?.puzzle, "the Latin board stays while Cyrillic loads")

        val cyrillic = loaded("uz-cyrl")
        gate.finish("uz-cyrl", cyrillic)
        assertSame(cyrillic.puzzle, vm.loaded.value?.puzzle)
    }

    @Test
    fun selectingTheSameScriptAgainDoesNotReload() = runTest {
        val gate = Gate()
        val vm = GameLoadViewModel(gate::load)
        vm.select("en")
        gate.finish("en", loaded("en"))
        vm.select("en")
        assertEquals(1, gate.calls)
    }

    @Test
    fun switchingThereAndBackPublishesANewRoundGeneration() = runTest {
        val vm = GameLoadViewModel { script -> loaded(script) }
        vm.select("uz-latn")
        val first = vm.loaded.value!!
        vm.select("uz-cyrl")
        vm.select("uz-latn")
        val back = vm.loaded.value!!
        assertEquals("uz-latn", back.puzzle.languageId)
        // The board's VM is keyed by generation, so the converted round is not shadowed by the first visit's VM.
        assertNotEquals(first.generation, back.generation)
    }

    @Test
    fun reloadLoadsTheSelectedScriptAgainAsANewRound() = runTest {
        var calls = 0
        val vm = GameLoadViewModel { s -> calls++; loaded(s) }
        vm.select("en")
        val first = vm.loaded.value!!
        vm.reload()
        assertEquals(2, calls, "an archive replay builds a fresh round")
        assertEquals("en", vm.selected.value)
        assertNotEquals(first.generation, vm.loaded.value!!.generation, "a new generation, so a new game VM")
    }

    @Test
    fun aRoundThatFailsToLoadLeavesTheSkeletonInsteadOfCrashing() = runTest {
        val vm = GameLoadViewModel { error("day not in this pack") }
        vm.select("en")
        assertNull(vm.loaded.value, "nothing is published, so the page stays a skeleton with its back control")
    }
}
