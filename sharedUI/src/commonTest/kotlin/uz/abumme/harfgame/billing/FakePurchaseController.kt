package uz.abumme.harfgame.billing

/**
 * In-memory [PurchaseController] for tests. A successful purchase/restore flips the entitlements
 * it reports from [currentEntitlements], mimicking RevenueCat customer info updating.
 */
class FakePurchaseController(
    override val isAvailable: Boolean = true,
    private val offerings: Offerings = Offerings(
        lifetime = StoreItem("lifetime", "Founder", "$1.49"),
        themes = listOf(StoreItem("theme_dusk", "Dusk", "$0.99")),
    ),
    var nextOutcome: PurchaseOutcome = PurchaseOutcome.Success,
) : PurchaseController {

    private var entitlements = Entitlements()

    override suspend fun offerings(): OfferingsResult =
        if (isAvailable) OfferingsResult.Available(offerings) else OfferingsResult.Unavailable

    override suspend fun purchase(productId: String): PurchaseOutcome {
        if (nextOutcome is PurchaseOutcome.Success) grant(productId)
        return nextOutcome
    }

    override suspend fun restore(): PurchaseOutcome {
        if (nextOutcome is PurchaseOutcome.Success) grant("lifetime")
        return nextOutcome
    }

    override suspend fun currentEntitlements(): Entitlements = entitlements

    private fun grant(productId: String) {
        entitlements = if (productId.startsWith(ENTITLEMENT_THEME_PREFIX)) {
            entitlements.copy(ownedThemes = entitlements.ownedThemes + productId)
        } else {
            entitlements.copy(lifetime = true)
        }
    }
}
