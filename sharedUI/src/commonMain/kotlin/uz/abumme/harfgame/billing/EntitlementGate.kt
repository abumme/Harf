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
     * Whether a palette may be applied to the board: the free palette and owned themes only, on every
     * platform. Where the store is unavailable (web, desktop) the account's purchases still count —
     * [EntitlementRepository] reads them from the server — but nothing is unlocked for lack of a store.
     */
    fun canApplyTheme(paletteId: String, entitlements: Entitlements): Boolean =
        paletteId == FREE_PALETTE || entitlements.ownsTheme(themeEntitlementId(paletteId))

    /** The palette to actually draw: the chosen one if the account may use it, else the free one. */
    fun paletteToApply(paletteId: String, entitlements: Entitlements): String =
        if (canApplyTheme(paletteId, entitlements)) paletteId else FREE_PALETTE

    /** Lifetime-only extras (archive, hard mode, Founder badge) — active with the lifetime unlock. */
    fun lifetimeExtrasUnlocked(entitlements: Entitlements): Boolean = entitlements.lifetime
}
