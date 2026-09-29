package uz.abumme.harfgame.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_close
import harf_game.sharedui.generated.resources.founder_desktop_guidance
import harf_game.sharedui.generated.resources.founder_feature_archive
import harf_game.sharedui.generated.resources.founder_feature_future
import harf_game.sharedui.generated.resources.founder_feature_hard_mode
import harf_game.sharedui.generated.resources.founder_one_time
import harf_game.sharedui.generated.resources.founder_title
import harf_game.sharedui.generated.resources.paywall_founder_subtitle
import harf_game.sharedui.generated.resources.paywall_intro
import harf_game.sharedui.generated.resources.paywall_loading
import harf_game.sharedui.generated.resources.paywall_no_products
import harf_game.sharedui.generated.resources.paywall_owned
import harf_game.sharedui.generated.resources.paywall_purchased
import harf_game.sharedui.generated.resources.paywall_restore
import harf_game.sharedui.generated.resources.paywall_restored
import harf_game.sharedui.generated.resources.paywall_store_unavailable
import harf_game.sharedui.generated.resources.paywall_theme_subtitle
import harf_game.sharedui.generated.resources.settings_support_harf
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.Entitlements
import uz.abumme.harfgame.billing.Offerings
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.StoreItem
import uz.abumme.harfgame.billing.hostedBillingUiSupported
import uz.abumme.harfgame.theme.GhostButton
import uz.abumme.harfgame.theme.HarfTheme
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfShapes
import uz.abumme.harfgame.theme.harfSerif
import kotlin.time.Duration.Companion.milliseconds

/**
 * The paywall — reachable from settings and the result screen, never blocking the daily game.
 * Presents the Founder lifetime unlock and cosmetic themes with localized prices, purchase and
 * restore. Shows owned/unavailable states gracefully.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit = {}) {
    val controller = koinInject<PurchaseController>()
    val entitlements = koinInject<EntitlementRepository>()

    val vm = viewModel { PaywallViewModel(controller, entitlements) }
    val state by vm.state.collectAsState()

    var message by remember { mutableStateOf<String?>(null) }
    val purchasedMsg = stringResource(Res.string.paywall_purchased)
    val restoredMsg = stringResource(Res.string.paywall_restored)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            message = when (ev) {
                PaywallEvent.Purchased -> purchasedMsg
                PaywallEvent.Restored -> restoredMsg
                is PaywallEvent.Failed -> ev.message
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500.milliseconds)
            message = null
        }
    }

    PaywallContent(
        state = state,
        message = message,
        onAction = vm::onAction,
        onBack = onBack,
    )
}

@Composable
fun PaywallContent(
    state: PaywallState,
    message: String? = null,
    onAction: (PaywallAction) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val colors = LocalHarfColors.current
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Scrollable body takes the remaining space; the Restore CTA below stays pinned + always visible.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_support_harf),
                    color = colors.ink,
                    fontFamily = harfSerif(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onBack) { Text(stringResource(Res.string.action_close), color = colors.muted) }
            }
            Text(
                stringResource(Res.string.paywall_intro),
                color = colors.muted, fontSize = 13.sp,
            )

            when (state.phase) {
                PaywallPhase.Loading -> Text(stringResource(Res.string.paywall_loading), color = colors.muted)
                PaywallPhase.Unavailable -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(Res.string.founder_title), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(stringResource(Res.string.founder_one_time), color = colors.accent, fontSize = 14.sp)
                        Text("• " + stringResource(Res.string.founder_feature_archive), color = colors.ink, fontSize = 14.sp)
                        Text("• " + stringResource(Res.string.founder_feature_hard_mode), color = colors.ink, fontSize = 14.sp)
                        Text("• " + stringResource(Res.string.founder_feature_future), color = colors.ink, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        // A store-less platform is told where to buy; on Android/iOS the store itself is down.
                        Text(
                            stringResource(
                                if (hostedBillingUiSupported) Res.string.paywall_store_unavailable
                                else Res.string.founder_desktop_guidance
                            ),
                            color = colors.muted, fontSize = 13.sp,
                        )
                    }
                }

                PaywallPhase.Ready -> {
                    state.offerings?.lifetime?.let { item ->
                        ProductRow(
                            item = item,
                            subtitle = stringResource(Res.string.paywall_founder_subtitle),
                            owned = state.owns(item.id),
                            busy = state.busyProductId == item.id,
                            colors = colors,
                        ) { onAction(PaywallAction.Purchase(item.id)) }
                    }
                    state.offerings?.themes?.forEach { item ->
                        ProductRow(
                            item = item,
                            subtitle = stringResource(Res.string.paywall_theme_subtitle),
                            owned = state.owns(item.id),
                            busy = state.busyProductId == item.id,
                            colors = colors,
                        ) { onAction(PaywallAction.Purchase(item.id)) }
                    }
                    if (state.offerings?.all.isNullOrEmpty()) {
                        Text(stringResource(Res.string.paywall_no_products), color = colors.muted)
                    }
                }
            }

        }
        message?.let { Text(it, color = colors.accent, fontSize = 13.sp) }
        GhostButton(
            text = stringResource(Res.string.paywall_restore),
            onClick = { onAction(PaywallAction.Restore) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.busyProductId == null,
        )
    }
}

@Preview
@Composable
fun PaywallPreviewReady() {
    HarfTheme {
        PaywallContent(
            state = PaywallState(
                phase = PaywallPhase.Ready,
                offerings = Offerings(
                    lifetime = StoreItem(
                        id = "harf_founder",
                        title = "Harf Founder",
                        priceLabel = "49 000 UZS",
                    ),
                    themes = listOf(
                        StoreItem(id = "theme_press", title = "Матбуот (Press)", priceLabel = "12 000 UZS"),
                        StoreItem(id = "theme_ink", title = "Сиёҳдон (Ink)", priceLabel = "12 000 UZS"),
                        StoreItem(id = "theme_blueprint", title = "Чизма (Blueprint)", priceLabel = "12 000 UZS"),
                        StoreItem(id = "theme_schoolbook", title = "Дарслик (Schoolbook)", priceLabel = "12 000 UZS"),
                    ),
                ),
                entitlements = Entitlements(ownedThemes = setOf("theme_press")),
            ),
        )
    }
}

@Preview
@Composable
fun PaywallPreviewUnavailable() {
    HarfTheme {
        PaywallContent(
            state = PaywallState(
                phase = PaywallPhase.Unavailable,
            ),
        )
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
            Text(stringResource(Res.string.paywall_owned), color = colors.accent, fontWeight = FontWeight.Medium)
        } else {
            Button(
                onClick = onBuy,
                enabled = !busy,
                shape = LocalHarfShapes.current.button,
            ) {
                Text(if (busy) "…" else item.priceLabel)
            }
        }
    }
}
