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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.hostedBillingUiSupported
import uz.abumme.harfgame.feature.cellstyles.StyleExperimentController
import uz.abumme.harfgame.feature.cellstyles.StylePreview
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.marks.HarfMarkStyleId

@Composable
fun SettingsScreen(onPaywall: () -> Unit = {}, onCustomerCenter: () -> Unit = {}) {
    val controller = koinInject<StyleExperimentController>()
    val entitlements = koinInject<EntitlementRepository>()
    val scope = rememberCoroutineScope()
    val active by controller.activeStyle.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val colors = LocalHarfColors.current

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
    }
}
