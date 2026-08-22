package uz.abumme.harfgame

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.feature.home.HomeAction
import uz.abumme.harfgame.feature.home.HomeEvent
import uz.abumme.harfgame.feature.home.HomeViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class MviTest {

    @BeforeTest
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun action_updates_state_and_emits_one_off_event_once() = runTest {
        val vm = HomeViewModel()
        assertEquals(0, vm.state.value.pokes)

        val events = mutableListOf<HomeEvent>()
        val job = launch { vm.events.collect { events.add(it) } }

        vm.onAction(HomeAction.Poke)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.pokes, "state reflects the action")
        assertEquals(1, events.size, "one-off event delivered exactly once")
        assertEquals(HomeEvent.Message("poked 1"), events.first())

        job.cancel()
    }
}
