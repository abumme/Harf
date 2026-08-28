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
        } else {
            if (oauthClient.isGoogleSupported) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val idToken = oauthClient.signInWithGoogle()
                            if (idToken != null) {
                                syncManager.linkAccount(OAuthProvider.GOOGLE, idToken)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Link Google Account")
                }
            }
            if (oauthClient.isAppleSupported) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val idToken = oauthClient.signInWithApple()
                            if (idToken != null) {
                                syncManager.linkAccount(OAuthProvider.APPLE, idToken)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Link Apple Account")
                }
            }
        }

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Account", color = Color(0xFFD32F2F))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Account") },
                text = { Text("Are you sure you want to delete your account? All server-side data and local stats will be permanently removed.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                syncManager.deleteAccount()
                                showDeleteConfirm = false
                            }
                        }
                    ) {
                        Text("Delete", color = Color(0xFFD32F2F))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
