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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.hostedBillingUiSupported
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.feature.cellstyles.StyleExperimentController
import uz.abumme.harfgame.feature.cellstyles.StylePreview
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId

@Composable
fun SettingsScreen(onPaywall: () -> Unit = {}, onCustomerCenter: () -> Unit = {}) {
    val controller = koinInject<StyleExperimentController>()
    val entitlements = koinInject<EntitlementRepository>()
    val sessionStore = koinInject<SessionStore>()
    val syncManager = koinInject<SyncManager>()
    val oauthClient = koinInject<OAuthClient>()
    val scope = rememberCoroutineScope()
    val active by controller.activeStyle.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val session by sessionStore.sessionFlow.collectAsState(initial = null)
    val colors = LocalHarfColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text("Mark style", color = colors.muted, fontSize = 13.sp)

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

        if (ents.lifetime) {
            Text("★ Founder", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        OutlinedButton(onClick = onPaywall, modifier = Modifier.fillMaxWidth()) {
            Text(if (ents.lifetime) "Support Harf / themes" else "Support Harf")
        }
        if (hostedBillingUiSupported) {
            OutlinedButton(onClick = onCustomerCenter, modifier = Modifier.fillMaxWidth()) {
                Text("Manage purchases")
            }
        }

        Text("Account & Sync", color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

        if (session?.isLinked == true) {
            Text("✓ Account linked", color = colors.accent, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            OutlinedButton(
                onClick = { scope.launch { syncManager.logout() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out")
            }
        } else {
            if (oauthClient.isGoogleSupported) {
                OutlinedButton(
                    onClick = { scope.launch { authMessage = linkWith(oauthClient.signInWithGoogle(), OAuthProvider.GOOGLE, "Google", syncManager) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Link Google Account")
                }
            }
            if (oauthClient.isAppleSupported) {
                OutlinedButton(
                    onClick = { scope.launch { authMessage = linkWith(oauthClient.signInWithApple(), OAuthProvider.APPLE, "Apple", syncManager) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Link Apple Account")
                }
            }
        }

        authMessage?.let { Text(it, color = colors.muted, fontSize = 13.sp) }

        OutlinedButton(
            onClick = { deleteError = null; showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Account", color = Color(0xFFD32F2F))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteConfirm = false },
                title = { Text("Delete Account") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to delete your account? All server-side data and local stats will be permanently removed.")
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
                                        deleteError = "Couldn't delete your account (${result.message}). Please try again."
                                }
                                deleting = false
                            }
                        }
                    ) {
                        Text(if (deleting) "Deleting…" else "Delete", color = Color(0xFFD32F2F))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !deleting, onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/** Run a native sign-in outcome through account linking; returns a user-facing status message. */
private suspend fun linkWith(
    result: uz.abumme.harfgame.data.auth.OAuthResult,
    provider: OAuthProvider,
    label: String,
    syncManager: SyncManager,
): String = when (result) {
    is uz.abumme.harfgame.data.auth.OAuthResult.Token -> {
        when (val r = syncManager.linkAccount(provider, result.idToken, result.nonce)) {
            is uz.abumme.harfgame.data.api.ApiResult.Success -> "$label account linked"
            is uz.abumme.harfgame.data.api.ApiResult.Error -> "Couldn't link $label account (${r.message})"
        }
    }
    uz.abumme.harfgame.data.auth.OAuthResult.Cancelled -> "$label sign-in cancelled"
    uz.abumme.harfgame.data.auth.OAuthResult.NotConfigured -> "$label sign-in isn't available yet"
    is uz.abumme.harfgame.data.auth.OAuthResult.Failed -> "$label sign-in failed: ${result.message}"
}
