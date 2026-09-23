package uz.abumme.harfgame.feature.settings

import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.*
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
import uz.abumme.harfgame.theme.DangerButton
import uz.abumme.harfgame.theme.GhostButton
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfShapes
import uz.abumme.harfgame.theme.ScreenTopBar
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId

/** Public legal document URLs. Update these to the hosted locations before release. */
private object LegalLinks {
    const val PRIVACY = "https://lazydevs.uz/harf/privacy"
    const val OFFER = "https://lazydevs.uz/harf/offer"
}

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onPaywall: () -> Unit = {}, onCustomerCenter: () -> Unit = {}) {
    val controller = koinInject<StyleExperimentController>()
    val purchases = koinInject<PurchaseController>()
    val entitlements = koinInject<EntitlementRepository>()
    val sessionStore = koinInject<SessionStore>()
    val syncManager = koinInject<SyncManager>()
    val oauthClient = koinInject<OAuthClient>()
    val gamesServices = koinInject<uz.abumme.harfgame.games.GamesServices>()
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.settings_title),
            backLabel = stringResource(Res.string.action_back),
            onBack = onBack,
        )

        // Appearance — mark style
        SettingsSection(stringResource(Res.string.settings_mark_style)) {
            for (id in HarfMarkStyleId.entries) {
                val selected = id == active
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { controller.setFromSettings(id) } }
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) colors.accent else colors.rule,
                            shape = LocalHarfShapes.current.swatch,
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
        }

        // Support / purchases
        if (ents.lifetime) {
            Text(stringResource(Res.string.settings_founder), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (purchases.isAvailable) {
            GhostButton(
                stringResource(if (ents.lifetime) Res.string.settings_support_harf_themes else Res.string.settings_support_harf),
                onPaywall, Modifier.fillMaxWidth(),
            )
            if (hostedBillingUiSupported) {
                GhostButton(stringResource(Res.string.settings_manage_purchases), onCustomerCenter, Modifier.fillMaxWidth())
            }
        } else if (!ents.lifetime) {
            Text(
                stringResource(Res.string.founder_desktop_guidance),
                color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Account
        SettingsSection(stringResource(Res.string.settings_account_sync)) {
            if (session?.isLinked == true) {
                val linkedName = session?.displayName
                Text(
                    if (linkedName != null) stringResource(Res.string.settings_signed_in_as, linkedName)
                    else stringResource(Res.string.settings_account_linked),
                    color = colors.accent, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                )
                GhostButton(stringResource(Res.string.settings_log_out), { scope.launch { syncManager.logout() } }, Modifier.fillMaxWidth())
            } else {
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
                            is OAuthResult.Failed -> {
                                // Keep the technical cause in logs; show the user a plain recovery message.
                                println("Sign-in failed ($label): ${result.message}")
                                authMessage = getString(Res.string.signin_failed, label)
                            }
                        }
                    }
                }
                if (oauthClient.isGoogleSupported) {
                    GhostButton(stringResource(Res.string.settings_link_google), { startSignIn(OAuthProvider.GOOGLE, "Google") { oauthClient.signInWithGoogle() } }, Modifier.fillMaxWidth())
                }
                if (oauthClient.isAppleSupported) {
                    GhostButton(stringResource(Res.string.settings_link_apple), { startSignIn(OAuthProvider.APPLE, "Apple") { oauthClient.signInWithApple() } }, Modifier.fillMaxWidth())
                }
            }
            authMessage?.let { Text(it, color = colors.muted, fontSize = 13.sp) }
        }

        // Play Games
        if (gamesServices.isAvailable) {
            SettingsSection(stringResource(Res.string.settings_play_games)) {
                GhostButton(stringResource(Res.string.settings_leaderboards), { gamesServices.showLeaderboards() }, Modifier.fillMaxWidth())
                GhostButton(stringResource(Res.string.settings_achievements), { gamesServices.showAchievements() }, Modifier.fillMaxWidth())
            }
        }

        // Legal
        SettingsSection(stringResource(Res.string.settings_legal)) {
            GhostButton(stringResource(Res.string.settings_privacy_policy), { uriHandler.openUri(LegalLinks.PRIVACY) }, Modifier.fillMaxWidth())
            GhostButton(stringResource(Res.string.settings_terms_offer), { uriHandler.openUri(LegalLinks.OFFER) }, Modifier.fillMaxWidth())
        }

        // Danger zone — isolated, using the danger token
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.danger.copy(alpha = 0.4f), LocalHarfShapes.current.container)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.settings_delete_account).uppercase(),
                color = colors.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            )
            DangerButton(stringResource(Res.string.settings_delete), { deleteError = null; showDeleteConfirm = true }, Modifier.fillMaxWidth())
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteConfirm = false },
                title = { Text(stringResource(Res.string.settings_delete_account)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.settings_delete_body))
                        deleteError?.let { Text(it, color = colors.danger, fontSize = 13.sp) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            scope.launch {
                                deleting = true
                                deleteError = null
                                when (val result = syncManager.deleteAccount()) {
                                    is uz.abumme.harfgame.data.api.ApiResult.Success -> showDeleteConfirm = false
                                    is uz.abumme.harfgame.data.api.ApiResult.Error -> {
                                        println("Delete account failed: ${result.message}")
                                        deleteError = getString(Res.string.delete_failed)
                                    }
                                }
                                deleting = false
                            }
                        }
                    ) {
                        Text(stringResource(if (deleting) Res.string.settings_deleting else Res.string.settings_delete), color = colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !deleting, onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(Res.string.settings_cancel))
                    }
                }
            )
        }

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
                                    is uz.abumme.harfgame.data.api.ApiResult.Error -> {
                                        println("Link account failed (${link.label}): ${r.message}")
                                        getString(Res.string.link_failed, link.label)
                                    }
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
    }
}

/** A labeled group: muted uppercase header + a bordered container holding the section's controls. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val c = LocalHarfColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.rule, LocalHarfShapes.current.container)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** A sign-in that produced a token and is waiting for the user to confirm a display name. */
private data class PendingLink(
    val provider: OAuthProvider,
    val label: String,
    val idToken: String,
    val nonce: String?,
)
