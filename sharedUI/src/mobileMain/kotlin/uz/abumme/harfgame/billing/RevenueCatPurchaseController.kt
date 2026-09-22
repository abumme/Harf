package uz.abumme.harfgame.billing

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitLogOut
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException

/**
 * Real RevenueCat-backed controller for Android and iOS. Configured once from the platform's
 * public SDK key; a blank key leaves [isAvailable] false so the app behaves like an unconfigured
 * (no-op) store rather than crashing.
 */
class RevenueCatPurchaseController(apiKey: String) : PurchaseController {

    override val isAvailable: Boolean

    init {
        // Configuration must never crash the app. A blank key means "no store"; a bad/rejected key
        // (e.g. a Test Store key that slipped into a build) degrades to unavailable instead of
        // throwing out of the Koin graph on startup.
        isAvailable = if (apiKey.isNotBlank()) {
            try {
                if (!Purchases.isConfigured) {
                    Purchases.configure(PurchasesConfiguration.Builder(apiKey).build())
                }
                true
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    override suspend fun identify(userId: String) {
        if (!isAvailable) return
        try {
            Purchases.sharedInstance.awaitLogIn(userId)
        } catch (_: Exception) {}
    }

    override suspend fun reset() {
        if (!isAvailable) return
        try {
            Purchases.sharedInstance.awaitLogOut()
        } catch (_: Exception) {}
    }

    override suspend fun offerings(): OfferingsResult {
        if (!isAvailable) return OfferingsResult.Unavailable
        return try {
            val current = Purchases.sharedInstance.awaitOfferings().current
                ?: return OfferingsResult.Unavailable
            val items = current.availablePackages.map { pkg ->
                val p = pkg.storeProduct
                StoreItem(id = p.id, title = p.title, priceLabel = p.price.formatted)
            }
            val lifetime = items.firstOrNull { it.id.contains("lifetime", ignoreCase = true) }
            // classify each package once: the lifetime pick is never also listed as a theme
            val themes = items.filter {
                it != lifetime && (it.id.startsWith(ENTITLEMENT_THEME_PREFIX) || it.id.contains("theme", ignoreCase = true))
            }
            OfferingsResult.Available(Offerings(lifetime = lifetime, themes = themes))
        } catch (e: PurchasesException) {
            OfferingsResult.Unavailable
        }
    }

    override suspend fun purchase(productId: String): PurchaseOutcome {
        if (!isAvailable) return PurchaseOutcome.Unavailable
        return try {
            val pkg = Purchases.sharedInstance.awaitOfferings().current
                ?.availablePackages?.firstOrNull { it.storeProduct.id == productId }
                ?: return PurchaseOutcome.Error("Product not found: $productId")
            Purchases.sharedInstance.awaitPurchase(packageToPurchase = pkg)
            PurchaseOutcome.Success
        } catch (e: PurchasesException) {
            if (e.code == PurchasesErrorCode.PurchaseCancelledError) PurchaseOutcome.Cancelled
            else PurchaseOutcome.Error(e.message ?: "Purchase failed")
        }
    }

    override suspend fun restore(): PurchaseOutcome {
        if (!isAvailable) return PurchaseOutcome.Unavailable
        return try {
            Purchases.sharedInstance.awaitRestore()
            PurchaseOutcome.Success
        } catch (e: PurchasesException) {
            PurchaseOutcome.Error(e.message ?: "Restore failed")
        }
    }

    override suspend fun currentEntitlements(): EntitlementsResult {
        if (!isAvailable) return EntitlementsResult.Success(Entitlements())
        return try {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            EntitlementsResult.Success(info.toEntitlements())
        } catch (e: PurchasesException) {
            EntitlementsResult.Failure
        } catch (e: Exception) {
            EntitlementsResult.Failure
        }
    }

    private fun CustomerInfo.toEntitlements(): Entitlements {
        val active = entitlements.active.keys
        return Entitlements(
            lifetime = ENTITLEMENT_LIFETIME in active,
            ownedThemes = active.filter { it.startsWith(ENTITLEMENT_THEME_PREFIX) }.toSet(),
        )
    }
}
