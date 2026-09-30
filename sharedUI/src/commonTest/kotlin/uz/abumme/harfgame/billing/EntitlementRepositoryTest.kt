package uz.abumme.harfgame.billing

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementRepositoryTest {

    @BeforeTest
    fun cleanup() = runTest {
        val ksafe = KSafe()
        SessionStore(ksafe).clear()
        AppSettings(ksafe).cacheEntitlements(Entitlements(), "owner-1")
        AppSettings(ksafe).cacheEntitlements(Entitlements(), "owner-2")
    }

    @Test
    fun offline_access_persists_for_same_owner_on_refresh_failure() = runTest {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)
        val controller = FakePurchaseController(initialEntitlements = Entitlements(lifetime = true))
        val repo = EntitlementRepository(controller, settings)

        val owner = "owner-1"
        repo.onAccountChanged(owner)

        // 1. Successful verification caches lifetime
        repo.refresh()
        assertTrue(repo.entitlements.value.lifetime)

        // 2. Controller enters temporary failure / offline mode
        controller.simulateFailure = true
        repo.refresh()

        // Cache must NOT be cleared; offline access persists
        assertTrue(repo.entitlements.value.lifetime, "Offline failure must preserve cached entitlement")
    }

    @Test
    fun verified_empty_ownership_revokes_access() = runTest {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)
        val controller = FakePurchaseController(initialEntitlements = Entitlements(lifetime = true))
        val repo = EntitlementRepository(controller, settings)

        val owner = "owner-1"
        repo.onAccountChanged(owner)
        repo.refresh()
        assertTrue(repo.entitlements.value.lifetime)

        // Store confirms refund / revocation
        controller.simulateFailure = false
        controller.setEntitlements(Entitlements(lifetime = false))
        repo.refresh()

        // Access revoked
        assertFalse(repo.entitlements.value.lifetime, "Confirmed revocation must update state")
    }

    @Test
    fun different_account_cannot_inherit_the_cache_and_logout_clears() = runTest {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)
        val controller = FakePurchaseController(initialEntitlements = Entitlements(lifetime = true))
        val repo = EntitlementRepository(controller, settings)

        // Owner 1 has lifetime
        val owner1 = "owner-1"
        repo.onAccountChanged(owner1)
        repo.refresh()
        assertTrue(repo.entitlements.value.lifetime)

        // Switch to Owner 2 (who has not bought Founder)
        val owner2 = "owner-2"
        repo.onAccountChanged(owner2)
        assertFalse(repo.entitlements.value.lifetime, "Second account must not inherit previous account's cache")

        // Switch back to Owner 1 -> cache restored
        repo.onAccountChanged(owner1)
        assertTrue(repo.entitlements.value.lifetime, "Original owner must restore their cached entitlements")

        // Logout -> state cleared
        repo.onAccountChanged(null)
        assertFalse(repo.entitlements.value.lifetime, "Logout must clear active entitlements")
    }

    @Test
    fun identify_and_reset_bind_mobile_revenuecat_identity() = runTest {
        val controller = FakePurchaseController()
        controller.identify("harf-user-abc")
        assertEquals("harf-user-abc", controller.identifiedUser)

        controller.reset()
        assertTrue(controller.resetCalled)
        assertEquals(null, controller.identifiedUser)
    }

    @Test
    fun bindAccount_identifies_store_refreshes_and_resets_on_logout() = runTest {
        val ksafe = KSafe()
        val settings = AppSettings(ksafe)
        val controller = FakePurchaseController(initialEntitlements = Entitlements(lifetime = true))
        val repo = EntitlementRepository(controller, settings)

        repo.bindAccount("owner-1")
        assertEquals("owner-1", controller.identifiedUser)
        assertTrue(repo.entitlements.value.lifetime, "Binding must reconcile with the store")
        assertTrue(settings.cachedEntitlements("owner-1").lifetime, "Grant must be cached per owner")

        repo.bindAccount(null)
        assertTrue(controller.resetCalled)
        assertFalse(repo.entitlements.value.lifetime, "Logout must clear active entitlements")
    }
}
