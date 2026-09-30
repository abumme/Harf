package uz.abumme.harfgame.feature.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.action_back
import harf_game.sharedui.generated.resources.founder_title
import harf_game.sharedui.generated.resources.manage_empty_body
import harf_game.sharedui.generated.resources.manage_empty_title
import harf_game.sharedui.generated.resources.manage_nothing_restored
import harf_game.sharedui.generated.resources.manage_owned_themes
import harf_game.sharedui.generated.resources.manage_refund
import harf_game.sharedui.generated.resources.manage_refund_note
import harf_game.sharedui.generated.resources.manage_title
import harf_game.sharedui.generated.resources.paywall_restore
import harf_game.sharedui.generated.resources.paywall_restored
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.ENTITLEMENT_THEME_PREFIX
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.storeRefundUrl
import uz.abumme.harfgame.theme.GhostButton
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.ScreenTopBar
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the player owns, and the two things they can do about it: restore purchases and ask the store
 * for a refund. Harf draws this itself so it follows the active edition — the screen a reviewer opens
 * from Settings should look like the rest of the game.
 */
@Composable
fun ManagePurchasesScreen(onBack: () -> Unit = {}) {
    val controller = koinInject<PurchaseController>()
    val entitlements = koinInject<EntitlementRepository>()

    val vm = viewModel { ManagePurchasesViewModel(controller, entitlements) }
    val state by vm.state.collectAsState()

    var message by remember { mutableStateOf<String?>(null) }
    val restoredMsg = stringResource(Res.string.paywall_restored)
    val nothingMsg = stringResource(Res.string.manage_nothing_restored)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            message = when (ev) {
                ManagePurchasesEvent.Restored -> restoredMsg
                ManagePurchasesEvent.NothingRestored -> nothingMsg
                is ManagePurchasesEvent.Failed -> ev.message
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500.milliseconds)
            message = null
        }
    }

    val uriHandler = LocalUriHandler.current
    ManagePurchasesContent(
        state = state,
        message = message,
        onAction = vm::onAction,
        onBack = onBack,
        onRefund = { storeRefundUrl?.let(uriHandler::openUri) },
    )
}

@Composable
fun ManagePurchasesContent(
    state: ManagePurchasesState,
    message: String? = null,
    onAction: (ManagePurchasesAction) -> Unit = {},
    onBack: () -> Unit = {},
    onRefund: () -> Unit = {},
) {
    val colors = LocalHarfColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.manage_title),
            backLabel = stringResource(Res.string.action_back),
            onBack = onBack,
        )

        if (state.ownsNothing) {
            // Never an empty area: a player who bought on another device has to see the way back in.
            Text(
                stringResource(Res.string.manage_empty_title),
                color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            Text(stringResource(Res.string.manage_empty_body), color = colors.muted, fontSize = 14.sp)
        } else {
            if (state.entitlements.lifetime) {
                OwnedRow(stringResource(Res.string.founder_title))
            }
            val themes = state.entitlements.ownedThemes.map { it.removePrefix(ENTITLEMENT_THEME_PREFIX) }
            if (themes.isNotEmpty()) {
                Text(
                    stringResource(Res.string.manage_owned_themes),
                    color = colors.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
                // The palette's own display name, so a theme is named here exactly as on the home screen.
                for (paletteId in themes.sorted()) OwnedRow(HarfPalettes.byId(paletteId).displayName)
            }
        }

        GhostButton(
            stringResource(Res.string.paywall_restore),
            { onAction(ManagePurchasesAction.Restore) },
            Modifier.fillMaxWidth(),
            enabled = !state.restoring,
        )

        if (storeRefundUrl != null) {
            GhostButton(stringResource(Res.string.manage_refund), onRefund, Modifier.fillMaxWidth())
            Text(stringResource(Res.string.manage_refund_note), color = colors.muted, fontSize = 12.sp)
        }

        if (message != null) {
            Text(message, color = colors.accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OwnedRow(title: String) {
    val colors = LocalHarfColors.current
    Text("• $title", color = colors.ink, fontSize = 16.sp)
}
