package uz.abumme.harfgame.billing

import androidx.compose.runtime.Composable

// Desktop/web have no in-app purchases; the custom PaywallScreen is used instead.
actual val hostedBillingUiSupported: Boolean = false

@Composable
actual fun HostedPaywall(onDismiss: () -> Unit) = Unit

@Composable
actual fun HostedCustomerCenter(onDismiss: () -> Unit) = Unit
