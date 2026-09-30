package uz.abumme.harfgame.feature.purchases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.purchases_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.PurchaseOutcome
import uz.abumme.harfgame.core.mvi.BaseViewModel
import uz.abumme.harfgame.core.mvi.UiAction
import uz.abumme.harfgame.core.mvi.UiEvent
import uz.abumme.harfgame.core.mvi.UiState

@Immutable
data class ManagePurchasesState(
    val entitlements: Entitlements = Entitlements(),
    val restoring: Boolean = false,
) : UiState {
    val ownsNothing: Boolean get() = !entitlements.lifetime && entitlements.ownedThemes.isEmpty()
}

sealed interface ManagePurchasesAction : UiAction {
    data object Restore : ManagePurchasesAction
}

sealed interface ManagePurchasesEvent : UiEvent {
    data object Restored : ManagePurchasesEvent
    data object NothingRestored : ManagePurchasesEvent
    data class Failed(val message: String) : ManagePurchasesEvent
}

/**
 * Lists what the player owns and restores purchases. Ownership comes from [EntitlementRepository] —
 * the same source the paywall reads — so the two screens can never disagree about what is unlocked.
 */
class ManagePurchasesViewModel(
    private val controller: PurchaseController,
    private val entitlements: EntitlementRepository,
) : BaseViewModel<ManagePurchasesState, ManagePurchasesAction, ManagePurchasesEvent>(ManagePurchasesState()) {

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { setState { copy(entitlements = it) } }
        }
        viewModelScope.launch { entitlements.refresh() } // no-op offline; keeps the cached grant
    }

    override fun onAction(action: ManagePurchasesAction) {
        when (action) {
            ManagePurchasesAction.Restore -> restore()
        }
    }

    private fun restore() = viewModelScope.launch {
        if (state.value.restoring) return@launch
        setState { copy(restoring = true) }
        val outcome = controller.restore()
        if (outcome is PurchaseOutcome.Success) entitlements.applyFromController()
        setState { copy(restoring = false) }
        when (outcome) {
            // A successful restore that granted nothing is the common case for a player who never
            // bought: saying "restored" there would be a lie the next screen contradicts. Read the
            // repository rather than this state, which only catches up when its collector runs.
            PurchaseOutcome.Success -> {
                val owned = entitlements.entitlements.value
                val gotSomething = owned.lifetime || owned.ownedThemes.isNotEmpty()
                sendEvent(if (gotSomething) ManagePurchasesEvent.Restored else ManagePurchasesEvent.NothingRestored)
            }
            PurchaseOutcome.Cancelled -> Unit
            is PurchaseOutcome.Error -> sendEvent(ManagePurchasesEvent.Failed(outcome.message))
            PurchaseOutcome.Unavailable ->
                sendEvent(ManagePurchasesEvent.Failed(getString(Res.string.purchases_unavailable)))
        }
    }
}
