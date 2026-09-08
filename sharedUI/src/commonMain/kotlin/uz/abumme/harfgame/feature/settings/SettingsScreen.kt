package uz.abumme.harfgame.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.delete_failed
import harf_game.sharedui.generated.resources.link_failed
import harf_game.sharedui.generated.resources.link_success
import harf_game.sharedui.generated.resources.settings_account_linked
import harf_game.sharedui.generated.resources.settings_account_sync
import harf_game.sharedui.generated.resources.settings_cancel
import harf_game.sharedui.generated.resources.settings_delete
import harf_game.sharedui.generated.resources.settings_delete_account
import harf_game.sharedui.generated.resources.settings_delete_body
import harf_game.sharedui.generated.resources.settings_deleting
import harf_game.sharedui.generated.resources.settings_founder
import harf_game.sharedui.generated.resources.settings_legal
import harf_game.sharedui.generated.resources.settings_link_apple
import harf_game.sharedui.generated.resources.settings_link_google
import harf_game.sharedui.generated.resources.settings_name_confirm
import harf_game.sharedui.generated.resources.settings_name_dialog_title
import harf_game.sharedui.generated.resources.settings_name_label
import harf_game.sharedui.generated.resources.settings_signed_in_as
import harf_game.sharedui.generated.resources.settings_log_out
import harf_game.sharedui.generated.resources.settings_manage_purchases
import harf_game.sharedui.generated.resources.settings_mark_style
import harf_game.sharedui.generated.resources.settings_privacy_policy
import harf_game.sharedui.generated.resources.settings_support_harf
import harf_game.sharedui.generated.resources.settings_support_harf_themes
import harf_game.sharedui.generated.resources.settings_terms_offer
import harf_game.sharedui.generated.resources.settings_title
import harf_game.sharedui.generated.resources.signin_cancelled
import harf_game.sharedui.generated.resources.signin_failed
import harf_game.sharedui.generated.resources.signin_unavailable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.hostedBillingUiSupported
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.auth.OAuthResult
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.feature.cellstyles.StyleExperimentController
import uz.abumme.harfgame.feature.cellstyles.StylePreview
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId

/** Public legal document URLs. Update these to the hosted locations before release. */
private object LegalLinks {
    const val PRIVACY = "https://lazydevs.uz/harf/privacy"
    const val OFFER = "https://lazydevs.uz/harf/offer"
}

@Composable
fun SettingsScreen(onPaywall: () -> Unit = {}, onCustomerCenter: () -> Unit = {}) {
    val controller = koinInject<StyleExperimentController>()
    val purchases = koinInject<PurchaseController>()
    val entitlements = koinInject<EntitlementRepository>()
    val sessionStore = koinInject<SessionStore>()
    val syncManager = koinInject<SyncManager>()
    val oauthClient = koinInject<OAuthClient>()
    val scope = rememberCoroutineScope()
    val active by controller.activeStyle.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    val colors = LocalHarfColors.current
    val uriHandler = LocalUriHandler.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf<PendingLink?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.settings_title), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(stringResource(Res.string.settings_mark_style), color = colors.muted, fontSize = 13.sp)

        for (id in HarfMarkStyleId.entries) {
            val selected = id == active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { controller.setFromSettings(id) } }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.accent else colors.rule,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StylePreview(id)
                Text(
                    id.name,
                    color = colors.ink,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                )
            }
        }

        // Purchases surface only when the store is actually configured (real RevenueCat key). With a
        // blank key isAvailable is false, so the whole paywall/founder/manage block is hidden — the
        // first release ships without purchases and the section reappears once a key + products exist.
        if (purchases.isAvailable) {
            if (ents.lifetime) {
                Text(stringResource(Res.string.settings_founder), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            OutlinedButton(onClick = onPaywall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (ents.lifetime) Res.string.settings_support_harf_themes else Res.string.settings_support_harf))
            }
            if (hostedBillingUiSupported) {
                OutlinedButton(onClick = onCustomerCenter, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.settings_manage_purchases))
                }
            }
        }

        Text(stringResource(Res.string.settings_account_sync), color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

        if (session?.isLinked == true) {
            val linkedName = session?.displayName
            Text(
                if (linkedName != null) stringResource(Res.string.settings_signed_in_as, linkedName)
                else stringResource(Res.string.settings_account_linked),
                color = colors.accent,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            OutlinedButton(
                onClick = { scope.launch { syncManager.logout() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.settings_log_out))
            }
        } else {
            // Sign-in yields a token; the actual link is deferred until the user confirms a name in
            // the dialog below. A cancel/unavailable/failure surfaces as a status message instead.
            val startSignIn: (OAuthProvider, String, suspend () -> OAuthResult) -> Unit = { provider, label, signIn ->
                scope.launch {
                    when (val result = signIn()) {
                        is OAuthResult.Token -> {
                            nameInput = result.suggestedName ?: session?.displayName ?: ""
                            authMessage = null
                            pendingLink = PendingLink(provider, label, result.idToken, result.nonce)
                        }
                        OAuthResult.Cancelled -> authMessage = getString(Res.string.signin_cancelled, label)
                        OAuthResult.NotConfigured -> authMessage = getString(Res.string.signin_unavailable, label)
                        is OAuthResult.Failed -> authMessage = getString(Res.string.signin_failed, label, result.message)
                    }
                }
            }
            if (oauthClient.isGoogleSupported) {
                OutlinedButton(
                    onClick = { startSignIn(OAuthProvider.GOOGLE, "Google") { oauthClient.signInWithGoogle() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.settings_link_google))
                }
            }
            if (oauthClient.isAppleSupported) {
                OutlinedButton(
                    onClick = { startSignIn(OAuthProvider.APPLE, "Apple") { oauthClient.signInWithApple() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.settings_link_apple))
                }
            }
        }

        authMessage?.let { Text(it, color = colors.muted, fontSize = 13.sp) }

        pendingLink?.let { link ->
            AlertDialog(
                onDismissRequest = { pendingLink = null },
                title = { Text(stringResource(Res.string.settings_name_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.settings_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = nameInput.isNotBlank(),
                        onClick = {
                            val confirmedName = nameInput.trim()
                            scope.launch {
                                authMessage = when (val r = syncManager.linkAccount(link.provider, link.idToken, link.nonce, confirmedName)) {
                                    is uz.abumme.harfgame.data.api.ApiResult.Success -> getString(Res.string.link_success, link.label)
                                    is uz.abumme.harfgame.data.api.ApiResult.Error -> getString(Res.string.link_failed, link.label, r.message)
                                }
                                pendingLink = null
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.settings_name_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLink = null }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
            )
        }

        Text(stringResource(Res.string.settings_legal), color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        OutlinedButton(
            onClick = { uriHandler.openUri(LegalLinks.PRIVACY) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.settings_privacy_policy))
        }
        OutlinedButton(
            onClick = { uriHandler.openUri(LegalLinks.OFFER) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.settings_terms_offer))
        }

        OutlinedButton(
            onClick = { deleteError = null; showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.settings_delete_account), color = Color(0xFFD32F2F))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteConfirm = false },
                title = { Text(stringResource(Res.string.settings_delete_account)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.settings_delete_body))
                        deleteError?.let { Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            scope.launch {
                                deleting = true
                                deleteError = null
                                // Only close on confirmed success; on failure keep the dialog open
                                // with a retryable error and the account/data intact.
                                when (val result = syncManager.deleteAccount()) {
                                    is uz.abumme.harfgame.data.api.ApiResult.Success -> showDeleteConfirm = false
                                    is uz.abumme.harfgame.data.api.ApiResult.Error ->
                                        deleteError = getString(Res.string.delete_failed, result.message)
                                }
                                deleting = false
                            }
                        }
                    ) {
                        Text(stringResource(if (deleting) Res.string.settings_deleting else Res.string.settings_delete), color = Color(0xFFD32F2F))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !deleting, onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
            )
        }
    }
}

/** A sign-in that produced a token and is waiting for the user to confirm a display name. */
private data class PendingLink(
    val provider: OAuthProvider,
    val label: String,
    val idToken: String,
    val nonce: String?,
)
