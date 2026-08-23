package uz.abumme.harfgame.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.HostedPaywall
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.StoreItem
import uz.abumme.harfgame.billing.hostedBillingUiSupported
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif

/**
 * The paywall — reachable from settings and the result screen, never blocking the daily game.
 * Presents the Founder lifetime unlock and cosmetic themes with localized prices, purchase and
 * restore. Shows owned/unavailable states gracefully.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit = {}) {
    val controller = koinInject<PurchaseController>()
    val entitlements = koinInject<EntitlementRepository>()

    // On mobile with a configured store, present RevenueCat's hosted (dashboard-configured) paywall;
    // refresh entitlements on dismiss so a completed purchase reflects immediately.
    if (hostedBillingUiSupported && controller.isAvailable) {
        val scope = rememberCoroutineScope()
        HostedPaywall(onDismiss = {
            scope.launch { entitlements.applyFromController() }
            onBack()
        })
        return
    }

    val vm = viewModel { PaywallViewModel(controller, entitlements) }
    val state by vm.state.collectAsState()
    val colors = LocalHarfColors.current

    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            message = when (ev) {
                PaywallEvent.Purchased -> "Thank you — unlocked!"
                PaywallEvent.Restored -> "Purchases restored"
                is PaywallEvent.Failed -> ev.message
            }
        }
    }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(2500); message = null } }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Support Harf", color = colors.ink, fontFamily = harfSerif(), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Close", color = colors.muted) }
        }
        Text(
            "Harf is free forever — no ads, no timers. These are optional ways to support it.",
            color = colors.muted, fontSize = 13.sp,
        )

        when (state.phase) {
            PaywallPhase.Loading -> Text("Loading…", color = colors.muted)
            PaywallPhase.Unavailable -> {
                Text("Store unavailable right now.", color = colors.ink)
                OutlinedButton(onClick = { vm.onAction(PaywallAction.Load) }) { Text("Retry") }
            }
            PaywallPhase.Ready -> {
                state.offerings?.lifetime?.let { item ->
                    ProductRow(
                        item = item,
                        subtitle = "Founder — archive, hard mode, badge",
                        owned = state.owns(item.id),
                        busy = state.busyProductId == item.id,
                        colors = colors,
                    ) { vm.onAction(PaywallAction.Purchase(item.id)) }
                }
                state.offerings?.themes?.forEach { item ->
                    ProductRow(
                        item = item,
                        subtitle = "Cosmetic theme",
                        owned = state.owns(item.id),
                        busy = state.busyProductId == item.id,
                        colors = colors,
                    ) { vm.onAction(PaywallAction.Purchase(item.id)) }
                }
                if (state.offerings?.all.isNullOrEmpty()) {
                    Text("No products available.", color = colors.muted)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        message?.let { Text(it, color = colors.accent, fontSize = 13.sp) }
        OutlinedButton(
            onClick = { vm.onAction(PaywallAction.Restore) },
            enabled = state.busyProductId == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore purchases") }
    }
}

@Composable
private fun ProductRow(
    item: StoreItem,
    subtitle: String,
    owned: Boolean,
    busy: Boolean,
    colors: uz.abumme.harfgame.theme.HarfColors,
    onBuy: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.title, color = colors.ink, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(subtitle, color = colors.muted, fontSize = 12.sp)
        }
        if (owned) {
            Text("Owned", color = colors.accent, fontWeight = FontWeight.Medium)
        } else {
            Button(onClick = onBuy, enabled = !busy) { Text(if (busy) "…" else item.priceLabel) }
        }
    }
}
