package uz.abumme.harfgame.billing

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
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

    @Test
    fun without_a_store_the_server_grant_is_applied_and_cached_per_owner() = runTest {
        val settings = AppSettings(KSafe())
        val server = FakeEntitlementService(
            ApiResult.Success(AccountEntitlementsDto(lifetime = true, ownedThemes = setOf("theme_dusk")))
        )
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), settings, server)

        repo.bindAccount("owner-1")

        assertEquals(listOf("owner-1"), server.askedFor, "The server is asked about the bound account")
        assertTrue(repo.entitlements.value.ownsTheme("theme_dusk"), "A purchase made on a phone must count on the web")
        assertTrue(repo.entitlements.value.lifetime)
        assertTrue(settings.cachedEntitlements("owner-1").ownsTheme("theme_dusk"), "Grant must be cached per owner")
    }

    @Test
    fun without_a_store_a_server_failure_keeps_the_cached_grant() = runTest {
        val settings = AppSettings(KSafe())
        settings.cacheEntitlements(Entitlements(ownedThemes = setOf("theme_dusk")), "owner-1")
        val server = FakeEntitlementService(ApiResult.Error("entitlements_unavailable", "down"))
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), settings, server)

        repo.bindAccount("owner-1")

        assertEquals(1, server.calls)
        assertTrue(repo.entitlements.value.ownsTheme("theme_dusk"), "An outage must not take a paid theme away")
    }

    @Test
    fun without_a_store_a_verified_empty_answer_revokes() = runTest {
        val settings = AppSettings(KSafe())
        settings.cacheEntitlements(Entitlements(ownedThemes = setOf("theme_dusk")), "owner-1")
        val server = FakeEntitlementService(ApiResult.Success(AccountEntitlementsDto()))
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), settings, server)

        repo.bindAccount("owner-1")

        assertFalse(repo.entitlements.value.ownsTheme("theme_dusk"), "A refund confirmed by the server must apply")
    }

    @Test
    fun without_an_account_the_server_is_not_asked() = runTest {
        val server = FakeEntitlementService(ApiResult.Success(AccountEntitlementsDto(lifetime = true)))
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), AppSettings(KSafe()), server)

        repo.refresh()
        repo.bindAccount(null)

        assertEquals(0, server.calls)
        assertFalse(repo.entitlements.value.lifetime)
    }

    @Test
    fun a_grant_answered_after_an_account_switch_is_dropped() = runTest {
        val settings = AppSettings(KSafe())
        val server = FakeEntitlementService(ApiResult.Success(AccountEntitlementsDto(ownedThemes = setOf("theme_dusk"))))
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), settings, server)
        repo.bindAccount("owner-1")

        // owner-1's answer arrives only after the session moved to owner-2.
        server.beforeAnswer = { repo.onAccountChanged("owner-2") }
        settings.cacheEntitlements(Entitlements(), "owner-1")
        repo.refresh()

        assertFalse(repo.entitlements.value.ownsTheme("theme_dusk"), "owner-2 must not wear owner-1's grant")
        assertFalse(settings.cachedEntitlements("owner-2").ownsTheme("theme_dusk"))
    }

    @Test
    fun account_is_known_once_the_session_is_bound_even_without_a_user() = runTest {
        val repo = EntitlementRepository(FakePurchaseController(isAvailable = false), AppSettings(KSafe()))
        assertFalse(repo.accountKnown.value, "Before the session is read nothing is known")

        repo.bindAccount(null) // same blank owner as the initial state: still counts as known
        assertTrue(repo.accountKnown.value)
    }
}
