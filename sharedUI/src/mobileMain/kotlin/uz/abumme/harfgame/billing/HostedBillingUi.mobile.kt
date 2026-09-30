package uz.abumme.harfgame.billing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    // fillMaxSize is load-bearing on iOS: the composable wraps a UIKit view controller, which without
    // a size measures to zero and paints an empty screen. Android's version sizes itself.
    CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = onDismiss)
}
