package uz.abumme.harfgame.billing

/**
 * Cross-platform purchase surface. The real implementation runs on Android and iOS via
 * RevenueCat; desktop and web bind [NoOpPurchaseController] so those targets still build and run.
 * All feature code depends on this interface, never on the RevenueCat SDK directly.
 */
interface PurchaseController {
    /** True only where IAP is real and configured (a non-blank RevenueCat key on a mobile target). */
    val isAvailable: Boolean

    /** Fetch offerings, or [OfferingsResult.Unavailable] when offline / not configured. */
    suspend fun offerings(): OfferingsResult

    /** Run the store purchase flow for a product id from offerings. */
    suspend fun purchase(productId: String): PurchaseOutcome

    /** Restore prior purchases for the current store account. */
    suspend fun restore(): PurchaseOutcome

    /** Read the current entitlements from customer info (empty when unavailable). */
    suspend fun currentEntitlements(): Entitlements
}

/** Safe no-op used on desktop/web and whenever RevenueCat is not configured. */
object NoOpPurchaseController : PurchaseController {
    override val isAvailable: Boolean = false
    override suspend fun offerings(): OfferingsResult = OfferingsResult.Unavailable
    override suspend fun purchase(productId: String): PurchaseOutcome = PurchaseOutcome.Unavailable
    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Unavailable
    override suspend fun currentEntitlements(): Entitlements = Entitlements()
}
