package uz.abumme.harfgame.settings

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Typed wrapper over [KSafe] for durable user preferences. Persistence is provided
 * by KSafe (survives relaunch, all platforms); reactive observation is provided by
 * in-memory [StateFlow]s seeded from the persisted value. Callers never touch KSafe
 * directly, keeping the store swappable.
 */
class AppSettings(private val ksafe: KSafe) {

    private val _paletteId = MutableStateFlow(ksafe.getDirect(KEY_PALETTE, DEFAULT_PALETTE))

    /** Active theme/palette id, observable and persisted. */
    val paletteId: StateFlow<String> = _paletteId.asStateFlow()

    fun setPaletteId(id: String) {
        _paletteId.value = id
        ksafe.putDirect(KEY_PALETTE, id)
    }

    /** Whether the user has completed first-run onboarding. */
    fun isOnboarded(): Boolean = ksafe.getDirect(KEY_ONBOARDED, false)

    fun setOnboarded(value: Boolean) = ksafe.putDirect(KEY_ONBOARDED, value)

    /**
     * Last known entitlements, cached for offline display only. This is a mirror of RevenueCat,
     * never a grant: [uz.abumme.harfgame.billing.EntitlementRepository] writes it solely from
     * controller-derived state.
     */
    fun cachedEntitlements(): uz.abumme.harfgame.billing.Entitlements = uz.abumme.harfgame.billing.Entitlements(
        lifetime = ksafe.getDirect(KEY_ENT_LIFETIME, false),
        ownedThemes = ksafe.getDirect(KEY_ENT_THEMES, "").split(',').filter { it.isNotBlank() }.toSet(),
    )

    fun cacheEntitlements(e: uz.abumme.harfgame.billing.Entitlements) {
        ksafe.putDirect(KEY_ENT_LIFETIME, e.lifetime)
        ksafe.putDirect(KEY_ENT_THEMES, e.ownedThemes.joinToString(","))
    }

    companion object {
        const val DEFAULT_PALETTE = "newsprint"
        private const val KEY_PALETTE = "app.paletteId"
        private const val KEY_ONBOARDED = "app.onboarded"
        private const val KEY_ENT_LIFETIME = "ent.lifetime"
        private const val KEY_ENT_THEMES = "ent.themes"
    }
}
