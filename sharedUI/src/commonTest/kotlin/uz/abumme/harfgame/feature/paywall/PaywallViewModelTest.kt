package uz.abumme.harfgame.feature.paywall

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.FakeEntitlementService
import uz.abumme.harfgame.billing.FakePurchaseController
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.entitlement.AccountEntitlementsDto
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun repo(controller: FakePurchaseController) =
        EntitlementRepository(controller, AppSettings(KSafe()).apply { cacheEntitlements(Entitlements()) })

    @Test
    fun loads_offerings_ready() = runTest {
        val controller = FakePurchaseController()
        val vm = PaywallViewModel(controller, repo(controller))
        assertEquals(PaywallPhase.Ready, vm.state.value.phase)
        assertEquals("lifetime", vm.state.value.offerings?.lifetime?.id)
        assertFalse(vm.state.value.owns("lifetime"))
    }

    @Test
    fun unavailable_when_no_store() = runTest {
        val controller = FakePurchaseController(isAvailable = false)
        val vm = PaywallViewModel(controller, repo(controller))
        assertEquals(PaywallPhase.Unavailable, vm.state.value.phase)
    }

    @Test
    fun restore_without_a_store_reads_the_account_from_the_server() = runTest {
        val controller = FakePurchaseController(isAvailable = false)
        val server = FakeEntitlementService(ApiResult.Success(AccountEntitlementsDto(ownedThemes = setOf("theme_dusk"))))
        val entitlements = EntitlementRepository(controller, AppSettings(KSafe()), server)
        entitlements.onAccountChanged("owner-web")
        val vm = PaywallViewModel(controller, entitlements)

        vm.onAction(PaywallAction.Restore)

        assertEquals(listOf("owner-web"), server.askedFor)
        assertTrue(vm.state.value.owns("theme_dusk"), "a theme bought on a phone shows as owned on the web")
        assertEquals(null, vm.state.value.busyProductId)
        AppSettings(KSafe()).cacheEntitlements(Entitlements(), "owner-web")
    }

    @Test
    fun purchase_moves_item_to_owned() = runTest {
        val controller = FakePurchaseController()
        val vm = PaywallViewModel(controller, repo(controller))
        vm.onAction(PaywallAction.Purchase("lifetime"))
        assertTrue(vm.state.value.owns("lifetime"), "lifetime owned after purchase")
        assertEquals(null, vm.state.value.busyProductId)
    }
}
