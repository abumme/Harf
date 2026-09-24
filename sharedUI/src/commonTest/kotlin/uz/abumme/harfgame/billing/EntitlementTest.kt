package uz.abumme.harfgame.billing

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementTest {

    private fun freshSettings(): AppSettings =
        AppSettings(KSafe()).apply { cacheEntitlements(Entitlements()) } // reset shared store

    @Test
    fun none_active_by_default() = runTest {
        val repo = EntitlementRepository(FakePurchaseController(), freshSettings())
        assertFalse(repo.entitlements.value.lifetime)
        assertTrue(repo.entitlements.value.ownedThemes.isEmpty())
    }

    @Test
    fun entitlement_reflects_a_purchase() = runTest {
        val controller = FakePurchaseController()
        val repo = EntitlementRepository(controller, freshSettings())
        controller.purchase("lifetime")
        repo.applyFromController()
        assertTrue(repo.entitlements.value.lifetime, "lifetime active after purchase")
    }

    @Test
    fun offline_shows_cached() = runTest {
        val settings = freshSettings()
        val ownerId = "user_123"
        settings.cacheEntitlements(Entitlements(lifetime = true, ownedThemes = setOf("theme_dusk")), ownerId)
        // an unavailable controller (offline / desktop) — repo must show the cached state for the signed-in account
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), settings)
        repo.onAccountChanged(ownerId)
        assertTrue(repo.entitlements.value.lifetime)
        assertTrue(repo.entitlements.value.ownsTheme("theme_dusk"))
    }

    @Test
    fun no_local_granting_when_controller_unavailable() = runTest {
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), freshSettings())
        repo.refresh() // unavailable => must not grant anything
        assertFalse(repo.entitlements.value.lifetime)
    }

    @Test
    fun gate_free_and_owned_only_when_purchases_available() {
        val ents = Entitlements(ownedThemes = setOf("theme_dusk"))
        // purchases available: free palette + owned theme apply, unowned does not
        assertTrue(EntitlementGate.canApplyTheme(EntitlementGate.FREE_PALETTE, ents, purchasesAvailable = true))
        assertTrue(EntitlementGate.canApplyTheme("dusk", ents, purchasesAvailable = true))
        assertFalse(EntitlementGate.canApplyTheme("aurora", ents, purchasesAvailable = true))
        // purchases unavailable: nothing is locked (can't sell it)
        assertTrue(EntitlementGate.canApplyTheme("aurora", ents, purchasesAvailable = false))
    }

    @Test
    fun lifetime_extras_gate() {
        assertFalse(EntitlementGate.lifetimeExtrasUnlocked(Entitlements()))
        assertTrue(EntitlementGate.lifetimeExtrasUnlocked(Entitlements(lifetime = true)))
        assertEquals("theme_dusk", EntitlementGate.themeEntitlementId("dusk"))
    }
}
