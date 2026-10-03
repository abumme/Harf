package uz.abumme.harfgame.feature.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.data.archive.ArchiveHistoryManager
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfShapes
import uz.abumme.harfgame.theme.ScreenTopBar

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
    val archiveHistory = koinInject<ArchiveHistoryManager>()
    val resultLog = koinInject<ResultLog>()
    val sessionStore = koinInject<SessionStore>()
    val entitlements by entitlementsRepo.entitlements.collectAsState()
    val isOwner = EntitlementGate.lifetimeExtrasUnlocked(entitlements)
    val colors = LocalHarfColors.current

    // Saveable: coming back from an archived game keeps the language the player was browsing.
    var selectedLang by rememberSaveable { mutableStateOf(LANGUAGES.first().first) }
    var availableDays by remember { mutableStateOf<List<Long>>(emptyList()) }
    var results by remember { mutableStateOf<Map<Long, ArchiveDayBrowser.DayResult>>(emptyMap()) }

    LaunchedEffect(selectedLang, isOwner) {
        if (isOwner) {
            availableDays = ArchiveDayBrowser.availableDays(provider, selectedLang).reversed()
            val owner = sessionStore.get().userId.orEmpty()
            results = ArchiveDayBrowser.results(selectedLang, archiveHistory.history(owner), resultLog.all())
            // Then upload offline playthroughs and pull other devices' ones (best-effort; rows refresh when it lands).
            runCatching { archiveHistory.sync(owner) }
            results = ArchiveDayBrowser.results(selectedLang, archiveHistory.history(owner), resultLog.all())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTopBar(
            title = stringResource(Res.string.archive_title),
            backLabel = stringResource(Res.string.action_back),
            onBack = onBack,
        )

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
                    Text(stringResource(Res.string.action_back))
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
                ) {
                    items(availableDays) { day ->
                        ArchiveDayRow(day, results[day], onClick = { onOpenPuzzle(selectedLang, day) })
                    }
                }
            }
        }
    }
}

/**
 * One past day: its date and a status badge — solved `N/6`, lost `X/6`, or not played — in the
 * mockup's row shape. The badge reads out the full result, since "X/6" means nothing spoken.
 */
@Composable
internal fun ArchiveDayRow(day: Long, result: ArchiveDayBrowser.DayResult?, onClick: () -> Unit) {
    val c = LocalHarfColors.current
    val shapes = LocalHarfShapes.current
    val status = when {
        result == null -> stringResource(Res.string.archive_not_played)
        result.won -> stringResource(Res.string.result_solved, result.attempts, 6)
        else -> stringResource(Res.string.result_out_of_tries)
    }
    val won = result?.won == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.row)
            .background(c.card)
            .border(1.dp, c.rule, shapes.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ArchiveDayBrowser.dateLabel(day), Modifier.weight(1f), color = c.ink, fontWeight = FontWeight.SemiBold)
        Text(
            when {
                result == null -> status
                won -> "${result.attempts}/6"
                else -> "X/6"
            },
            modifier = Modifier
                .clip(shapes.pill)
                .background(if (won) lerp(c.card, c.success, 0.14f) else c.paper2)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clearAndSetSemantics { contentDescription = status },
            color = if (won) c.success else c.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (result == null) Text("›", Modifier.clearAndSetSemantics {}, color = c.muted)
    }
}
