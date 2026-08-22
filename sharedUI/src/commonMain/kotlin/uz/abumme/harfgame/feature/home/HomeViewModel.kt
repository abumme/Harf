package uz.abumme.harfgame.feature.home

import uz.abumme.harfgame.core.mvi.BaseViewModel
import uz.abumme.harfgame.core.mvi.UiAction
import uz.abumme.harfgame.core.mvi.UiEvent
import uz.abumme.harfgame.core.mvi.UiState

data class HomeState(val pokes: Int = 0) : UiState

sealed interface HomeAction : UiAction {
    data object Poke : HomeAction
}

sealed interface HomeEvent : UiEvent {
    data class Message(val text: String) : HomeEvent
}

/** Placeholder Home ViewModel demonstrating the shared MVI contract. */
class HomeViewModel : BaseViewModel<HomeState, HomeAction, HomeEvent>(HomeState()) {
    override fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Poke -> {
                setState { copy(pokes = pokes + 1) }
                sendEvent(HomeEvent.Message("poked ${currentState.pokes}"))
            }
        }
    }
}
