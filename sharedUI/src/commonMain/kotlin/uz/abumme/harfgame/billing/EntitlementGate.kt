package uz.abumme.harfgame.billing

import uz.abumme.harfgame.settings.AppSettings

/**
 * The single place feature edges ask "is this unlocked?". The daily game never calls this —
 * gating exists only for the paid extras, so the free round stays free by construction.
 */
object EntitlementGate {

    /** The one always-free palette; every other palette is a cosmetic theme product. */
    const val FREE_PALETTE = AppSettings.DEFAULT_PALETTE

    /** RevenueCat theme entitlement id for a palette (e.g. "theme_dusk"). */
    fun themeEntitlementId(paletteId: String): String = ENTITLEMENT_THEME_PREFIX + paletteId

    /**
     * Whether a palette may be applied to the board. When purchases are unavailable
     * (desktop/web, or an unconfigured store) cosmetics are free — we never lock what
     * cannot be bought. Otherwise: the free palette and owned themes only.
     */
    fun canApplyTheme(paletteId: String, entitlements: Entitlements, purchasesAvailable: Boolean): Boolean =
        !purchasesAvailable ||
            paletteId == FREE_PALETTE ||
            entitlements.ownsTheme(themeEntitlementId(paletteId))

    /** Lifetime-only extras (archive, hard mode, Founder badge) — active with the lifetime unlock. */
    fun lifetimeExtrasUnlocked(entitlements: Entitlements): Boolean = entitlements.lifetime
}
