package uz.abumme.harfgame.billing

/** A purchasable item as shown on the paywall (id + display title + localized price). */
data class StoreItem(val id: String, val title: String, val priceLabel: String)

/** The products offered: the one-time lifetime unlock and any cosmetic theme packs. */
data class Offerings(val lifetime: StoreItem?, val themes: List<StoreItem> = emptyList()) {
    val all: List<StoreItem> get() = listOfNotNull(lifetime) + themes
}

/**
 * What the user has unlocked. Mirrors RevenueCat customer info — never self-granted.
 * [lifetime] enables the bundled extras (archive, hard mode, Founder badge);
 * [ownedThemes] are the cosmetic theme entitlement ids the user owns.
 */
data class Entitlements(
    val lifetime: Boolean = false,
    val ownedThemes: Set<String> = emptySet(),
) {
    fun ownsTheme(id: String): Boolean = id in ownedThemes
}

/** The RevenueCat entitlement identifier for the lifetime bundle. */
const val ENTITLEMENT_LIFETIME = "lifetime"

/** Cosmetic theme entitlements are identified by this prefix (e.g. "theme_dusk"). */
const val ENTITLEMENT_THEME_PREFIX = "theme_"

/** Result of a purchase or restore attempt. */
sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Error(val message: String) : PurchaseOutcome
    /** No IAP on this platform, or the SDK is not configured. */
    data object Unavailable : PurchaseOutcome
}

/** Result of fetching offerings. */
sealed interface OfferingsResult {
    data class Available(val offerings: Offerings) : OfferingsResult
    data object Unavailable : OfferingsResult
}
