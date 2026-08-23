package uz.abumme.harfgame.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _entitlements = MutableStateFlow(settings.cachedEntitlements())
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    /** Pull the latest entitlements from the controller (no-op when unavailable). */
    suspend fun refresh() {
        if (!controller.isAvailable) return
        apply(controller.currentEntitlements())
    }

    /** Record entitlements derived from the controller (e.g. after a successful purchase/restore). */
    suspend fun applyFromController() {
        if (!controller.isAvailable) return
        apply(controller.currentEntitlements())
    }

    private fun apply(e: Entitlements) {
        _entitlements.value = e
        settings.cacheEntitlements(e) // display-only cache
    }
}
