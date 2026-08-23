package uz.abumme.harfgame.billing

import androidx.compose.runtime.Composable

/**
 * RevenueCat's hosted, dashboard-configured Paywall and Customer Center. Available on mobile
 * (Android/iOS) via `purchases-kmp-ui`; a no-op on desktop/web, where the custom paywall is used.
 */
expect val hostedBillingUiSupported: Boolean

/** Full-screen RevenueCat Paywall for the current offering. */
@Composable
expect fun HostedPaywall(onDismiss: () -> Unit)

/** RevenueCat Customer Center (manage/restore purchases, request refunds). */
@Composable
expect fun HostedCustomerCenter(onDismiss: () -> Unit)
