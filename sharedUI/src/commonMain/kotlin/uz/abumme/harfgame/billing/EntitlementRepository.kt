package uz.abumme.harfgame.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.settings.AppSettings

/**
 * Single source of truth for unlocked content. State is seeded from the offline cache and only
 * ever advanced from [PurchaseController]-derived entitlements — the client never self-grants.
 * [refresh] re-reads customer info; [applyFromController] is used after a purchase/restore.
 */
class EntitlementRepository(
    private val controller: PurchaseController,
    private val settings: AppSettings,
) {
    private var currentOwnerId: String = ""
    private val _entitlements = MutableStateFlow(settings.cachedEntitlements(currentOwnerId))
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    /** Swaps the active account context on sign-in, account switch, or sign-out. */
    fun onAccountChanged(newOwnerId: String?) {
        currentOwnerId = newOwnerId ?: ""
        val cached = if (currentOwnerId.isNotBlank()) settings.cachedEntitlements(currentOwnerId) else Entitlements()
        _entitlements.value = cached
    }

    /** Pull the latest entitlements from the controller (no-op when unavailable). */
    suspend fun refresh() {
        if (!controller.isAvailable) return
        when (val res = controller.currentEntitlements()) {
            is EntitlementsResult.Success -> apply(res.entitlements)
            EntitlementsResult.Failure -> {
                // Refresh failure / offline: preserve the account's existing cached access!
            }
        }
    }

    /** Record entitlements derived from the controller (e.g. after a successful purchase/restore). */
    suspend fun applyFromController() {
        if (!controller.isAvailable) return
        when (val res = controller.currentEntitlements()) {
            is EntitlementsResult.Success -> apply(res.entitlements)
            EntitlementsResult.Failure -> {}
        }
    }

    private fun apply(e: Entitlements) {
        _entitlements.value = e
        if (currentOwnerId.isNotBlank()) {
            settings.cacheEntitlements(e, currentOwnerId)
        }
    }
}
