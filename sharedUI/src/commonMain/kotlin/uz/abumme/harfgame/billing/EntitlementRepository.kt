package uz.abumme.harfgame.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.service.EntitlementService
import uz.abumme.harfgame.settings.AppSettings

/**
 * Single source of truth for unlocked content. State is seeded from the offline cache and only
 * ever advanced from verified entitlements — the client never self-grants. Where the store is
 * available they come from [PurchaseController]; elsewhere (web, desktop) from [accountEntitlements],
 * the server's view of the same RevenueCat customer, so a purchase made on a phone follows the account.
 * [refresh] re-reads them; [applyFromController] is used after a purchase/restore.
 */
class EntitlementRepository(
    private val controller: PurchaseController,
    private val settings: AppSettings,
    private val accountEntitlements: EntitlementService? = null,
) {
    private var currentOwnerId: String = ""
    // A blank owner means no signed-in account, so there is no legitimate cached grant to seed from
    // (a real grant is always owner-scoped). Mirrors the same guard in onAccountChanged; without it a
    // legacy blank-owner ent.lifetime would unlock paid extras with no account/purchase.
    private val _entitlements = MutableStateFlow(
        if (currentOwnerId.isBlank()) Entitlements() else settings.cachedEntitlements(currentOwnerId)
    )
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _accountKnown = MutableStateFlow(false)

    /**
     * False until the session has been read once and [bindAccount] has seeded the account's cached grant.
     * Before that [entitlements] is empty for everyone, so a caller that takes things away for lack of an
     * entitlement (the theme fallback) waits for this instead of flashing the free palette at a paying player.
     */
    val accountKnown: StateFlow<Boolean> = _accountKnown.asStateFlow()

    /** Swaps the active account context on sign-in, account switch, or sign-out. */
    fun onAccountChanged(newOwnerId: String?) {
        currentOwnerId = newOwnerId ?: ""
        val cached = if (currentOwnerId.isNotBlank()) settings.cachedEntitlements(currentOwnerId) else Entitlements()
        _entitlements.value = cached
    }

    /**
     * Follows the Harf session: binds the store identity to the account (so a purchase made on one
     * device is found by "restore" on any device signed into the same account), swaps the cached
     * grant, and reconciles with the store. Same owner twice is a no-op.
     */
    suspend fun bindAccount(userId: String?) {
        val ownerId = userId ?: ""
        if (ownerId == currentOwnerId) {
            _accountKnown.value = true
            return
        }
        onAccountChanged(userId)
        _accountKnown.value = true
        if (ownerId.isBlank()) {
            controller.reset() // no account: nothing to reconcile, the empty state is final
            return
        }
        controller.identify(ownerId)
        refresh()
    }

    /**
     * Pull the latest entitlements: from the controller where the store is available, otherwise from
     * the server for the bound account. A failure (offline, store unreachable) keeps the cached access.
     * Returns whether a verified answer was applied.
     */
    suspend fun refresh(): Boolean {
        if (controller.isAvailable) return applyFromController()
        val service = accountEntitlements ?: return false
        val ownerId = currentOwnerId
        if (ownerId.isBlank()) return false // no account: nothing the server could vouch for
        return when (val res = service.getEntitlements(ownerId)) {
            is ApiResult.Success -> {
                // The account may have changed while the request was in flight; that grant is not this one's.
                if (ownerId != currentOwnerId) return false
                apply(Entitlements(lifetime = res.data.lifetime, ownedThemes = res.data.ownedThemes))
                true
            }
            // Refresh failure / offline: preserve the account's existing cached access!
            is ApiResult.Error -> false
        }
    }

    /** Record entitlements derived from the controller (e.g. after a successful purchase/restore). */
    suspend fun applyFromController(): Boolean {
        if (!controller.isAvailable) return false
        val ownerId = currentOwnerId
        return when (val res = controller.currentEntitlements()) {
            is EntitlementsResult.Success -> {
                if (ownerId != currentOwnerId) return false
                apply(res.entitlements)
                true
            }
            // Refresh failure / offline: preserve the account's existing cached access!
            EntitlementsResult.Failure -> false
        }
    }

    private fun apply(e: Entitlements) {
        _entitlements.value = e
        if (currentOwnerId.isNotBlank()) {
            settings.cacheEntitlements(e, currentOwnerId)
        }
    }
}
