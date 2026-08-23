package uz.abumme.harfgame.feature.paywall

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.Offerings
import uz.abumme.harfgame.billing.OfferingsResult
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.PurchaseOutcome
import uz.abumme.harfgame.core.mvi.BaseViewModel
import uz.abumme.harfgame.core.mvi.UiAction
import uz.abumme.harfgame.core.mvi.UiEvent
import uz.abumme.harfgame.core.mvi.UiState

enum class PaywallPhase { Loading, Ready, Unavailable }

data class PaywallState(
    val phase: PaywallPhase = PaywallPhase.Loading,
    val offerings: Offerings? = null,
    val entitlements: Entitlements = Entitlements(),
    val busyProductId: String? = null,
) : UiState {
    fun owns(productId: String): Boolean =
        entitlements.lifetime && !productId.startsWith("theme_") ||
            entitlements.ownsTheme(productId) ||
            entitlements.ownsTheme(EntitlementGate.themeEntitlementId(productId))
}

sealed interface PaywallAction : UiAction {
    data object Load : PaywallAction
    data class Purchase(val productId: String) : PaywallAction
    data object Restore : PaywallAction
}

sealed interface PaywallEvent : UiEvent {
    data object Purchased : PaywallEvent
    data object Restored : PaywallEvent
    data class Failed(val message: String) : PaywallEvent
}

/**
 * Drives the paywall: loads offerings, runs purchase/restore through [PurchaseController], and
 * refreshes entitlements from the controller on success (never self-granting).
 */
class PaywallViewModel(
    private val controller: PurchaseController,
    private val entitlements: EntitlementRepository,
) : BaseViewModel<PaywallState, PaywallAction, PaywallEvent>(PaywallState()) {

    init {
        onAction(PaywallAction.Load)
        viewModelScope.launch {
            entitlements.entitlements.collect { setState { copy(entitlements = it) } }
        }
    }

    override fun onAction(action: PaywallAction) {
        when (action) {
            PaywallAction.Load -> load()
            is PaywallAction.Purchase -> purchase(action.productId)
            PaywallAction.Restore -> restore()
        }
    }

    private fun load() = viewModelScope.launch {
        setState { copy(phase = PaywallPhase.Loading) }
        when (val r = controller.offerings()) {
            is OfferingsResult.Available -> setState { copy(phase = PaywallPhase.Ready, offerings = r.offerings) }
            OfferingsResult.Unavailable -> setState { copy(phase = PaywallPhase.Unavailable) }
        }
    }

    private fun purchase(productId: String) = viewModelScope.launch {
        setState { copy(busyProductId = productId) }
        val outcome = controller.purchase(productId)
        setState { copy(busyProductId = null) }
        when (outcome) {
            PurchaseOutcome.Success -> {
                entitlements.applyFromController()
                sendEvent(PaywallEvent.Purchased)
            }
            PurchaseOutcome.Cancelled -> Unit
            is PurchaseOutcome.Error -> sendEvent(PaywallEvent.Failed(outcome.message))
            PurchaseOutcome.Unavailable -> sendEvent(PaywallEvent.Failed("Purchases unavailable"))
        }
    }

    private fun restore() = viewModelScope.launch {
        setState { copy(busyProductId = RESTORE) }
        val outcome = controller.restore()
        setState { copy(busyProductId = null) }
        when (outcome) {
            PurchaseOutcome.Success -> {
                entitlements.applyFromController()
                sendEvent(PaywallEvent.Restored)
            }
            PurchaseOutcome.Cancelled -> Unit
            is PurchaseOutcome.Error -> sendEvent(PaywallEvent.Failed(outcome.message))
            PurchaseOutcome.Unavailable -> sendEvent(PaywallEvent.Failed("Purchases unavailable"))
        }
    }

    companion object {
        const val RESTORE = "__restore__"
    }
}
