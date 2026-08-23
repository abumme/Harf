package uz.abumme.harfgame.billing

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
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

    override val isAvailable: Boolean = apiKey.isNotBlank()

    init {
        if (isAvailable && !Purchases.isConfigured) {
            Purchases.configure(PurchasesConfiguration.Builder(apiKey).build())
        }
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
            val themes = items.filter { it.id.startsWith(ENTITLEMENT_THEME_PREFIX) || it.id.contains("theme", ignoreCase = true) }
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

    override suspend fun currentEntitlements(): Entitlements {
        if (!isAvailable) return Entitlements()
        return try {
            Purchases.sharedInstance.awaitCustomerInfo().toEntitlements()
        } catch (e: PurchasesException) {
            Entitlements()
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
