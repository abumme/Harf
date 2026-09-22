package uz.abumme.harfgame.feature.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.harfSerif

private val LANGUAGES = listOf(
    "uz-latn" to "Oʻzbekcha",
    "ru" to "Русский",
    "en" to "English",
    "kk" to "Қазақша",
)

@Composable
fun ArchiveScreen(
    onBack: () -> Unit = {},
    onOpenPuzzle: (languageId: String, epochDay: Long) -> Unit = { _, _ -> },
    onPaywall: () -> Unit = {},
) {
    val entitlementsRepo = koinInject<EntitlementRepository>()
    val provider = koinInject<DailyPuzzleProvider>()
    val entitlements by entitlementsRepo.entitlements.collectAsState()
    val isOwner = EntitlementGate.lifetimeExtrasUnlocked(entitlements)
    val colors = LocalHarfColors.current

    var selectedLang by remember { mutableStateOf(LANGUAGES.first().first) }
    var availableDays by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(selectedLang, isOwner) {
        if (isOwner) {
            availableDays = ArchiveDayBrowser.availableDays(provider, selectedLang).reversed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("←", fontSize = 24.sp, color = colors.ink)
            }
            Text(
                stringResource(Res.string.archive_title),
                fontFamily = harfSerif(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (!isOwner) {
            // Clear unlock path for non-owners without blocking free daily play
            Column(
                modifier = Modifier.weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(Res.string.archive_locked_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.archive_locked_desc),
                    fontSize = 14.sp,
                    color = colors.muted,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onPaywall, modifier = Modifier.width(220.dp)) {
                    Text(stringResource(Res.string.archive_unlock_action))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.width(220.dp)) {
                    Text("Back")
                }
            }
        } else {
            // Language selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                for ((id, label) in LANGUAGES) {
                    FilterChip(
                        selected = selectedLang == id,
                        onClick = { selectedLang = id },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (availableDays.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.archive_empty), color = colors.muted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(availableDays) { day ->
                        OutlinedButton(
                            onClick = { onOpenPuzzle(selectedLang, day) },
                            modifier = Modifier.fillMaxWidth(0.85f),
                        ) {
                            Text(stringResource(Res.string.archive_day_label, day))
                        }
                    }
                }
            }
        }
    }
}
