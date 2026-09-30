package uz.abumme.harfgame.feature.purchases

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.FakePurchaseController
import uz.abumme.harfgame.billing.PurchaseOutcome
import uz.abumme.harfgame.settings.AppSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ManagePurchasesViewModelTest {

    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun repo(controller: FakePurchaseController) =
        EntitlementRepository(controller, AppSettings(KSafe()).apply { cacheEntitlements(Entitlements()) })

    @Test
    fun restore_that_grants_reports_restored_and_lists_the_purchase() = runTest {
        val controller = FakePurchaseController()
        val vm = ManagePurchasesViewModel(controller, repo(controller))
        assertTrue(vm.state.value.ownsNothing, "nothing owned before the restore")

        vm.onAction(ManagePurchasesAction.Restore)

        assertEquals(ManagePurchasesEvent.Restored, vm.events.first())
        assertTrue(vm.state.value.entitlements.lifetime, "the restored unlock shows up in the list")
        assertEquals(false, vm.state.value.restoring)
    }

    @Test
    fun restore_that_grants_nothing_says_so() = runTest {
        val controller = FakePurchaseController(grantsOnRestore = false)
        val vm = ManagePurchasesViewModel(controller, repo(controller))

        vm.onAction(ManagePurchasesAction.Restore)

        assertEquals(ManagePurchasesEvent.NothingRestored, vm.events.first(), "never claim a restore that granted nothing")
        assertTrue(vm.state.value.ownsNothing)
    }

    @Test
    fun restore_without_a_store_reports_the_failure() = runTest {
        val controller = FakePurchaseController(nextOutcome = PurchaseOutcome.Unavailable)
        val vm = ManagePurchasesViewModel(controller, repo(controller))

        vm.onAction(ManagePurchasesAction.Restore)

        assertTrue(vm.events.first() is ManagePurchasesEvent.Failed)
        assertEquals(false, vm.state.value.restoring)
    }
}
