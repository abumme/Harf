package uz.abumme.harfgame.feature.paywall

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.FakePurchaseController
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
    fun purchase_moves_item_to_owned() = runTest {
        val controller = FakePurchaseController()
        val vm = PaywallViewModel(controller, repo(controller))
        vm.onAction(PaywallAction.Purchase("lifetime"))
        assertTrue(vm.state.value.owns("lifetime"), "lifetime owned after purchase")
        assertEquals(null, vm.state.value.busyProductId)
    }
}
