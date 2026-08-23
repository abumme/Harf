package uz.abumme.harfgame.billing

import androidx.compose.runtime.Composable
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

actual val hostedBillingUiSupported: Boolean = true

@Composable
actual fun HostedPaywall(onDismiss: () -> Unit) {
    Paywall(PaywallOptions(dismissRequest = onDismiss) { shouldDisplayDismissButton = true })
}

@Composable
actual fun HostedCustomerCenter(onDismiss: () -> Unit) {
    CustomerCenter(onDismiss = onDismiss)
}
