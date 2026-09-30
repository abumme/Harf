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
    /** False mimics a successful restore that found nothing to grant — a player who never bought. */
    private val grantsOnRestore: Boolean = true,
    initialEntitlements: Entitlements = Entitlements(),
) : PurchaseController {

    private var entitlements = initialEntitlements
    var simulateFailure: Boolean = false
    var identifiedUser: String? = null
    var resetCalled: Boolean = false

    override suspend fun offerings(): OfferingsResult =
        if (isAvailable) OfferingsResult.Available(offerings) else OfferingsResult.Unavailable

    override suspend fun purchase(productId: String): PurchaseOutcome {
        if (nextOutcome is PurchaseOutcome.Success) grant(productId)
        return nextOutcome
    }

    override suspend fun restore(): PurchaseOutcome {
        if (nextOutcome is PurchaseOutcome.Success && grantsOnRestore) grant("lifetime")
        return nextOutcome
    }

    override suspend fun currentEntitlements(): EntitlementsResult {
        if (!isAvailable || simulateFailure) return EntitlementsResult.Failure
        return EntitlementsResult.Success(entitlements)
    }

    override suspend fun identify(userId: String) {
        identifiedUser = userId
    }

    override suspend fun reset() {
        resetCalled = true
        identifiedUser = null
    }

    fun setEntitlements(e: Entitlements) {
        entitlements = e
    }

    private fun grant(productId: String) {
        entitlements = if (productId.startsWith(ENTITLEMENT_THEME_PREFIX)) {
            entitlements.copy(ownedThemes = entitlements.ownedThemes + productId)
        } else {
            entitlements.copy(lifetime = true)
        }
    }
}
