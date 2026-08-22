package uz.abumme.harfgame.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable screen state. */
interface UiState

/** User intent handled by a ViewModel. */
interface UiAction

/** One-off event delivered exactly once (message, navigation signal, …). */
interface UiEvent

/**
 * Minimal MVI base: a [StateFlow] of state, a channel-backed [Flow] of one-off events,
 * and a single [onAction] entry point. Feature ViewModels extend this.
 */
abstract class BaseViewModel<S : UiState, A : UiAction, E : UiEvent>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _events = Channel<E>(Channel.BUFFERED)
    val events: Flow<E> = _events.receiveAsFlow()

    protected val currentState: S get() = _state.value

    protected fun setState(reducer: S.() -> S) = _state.update(reducer)

    protected fun sendEvent(event: E) {
        viewModelScope.launch { _events.send(event) }
    }

    abstract fun onAction(action: A)
}
