package uz.abumme.harfgame.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import harf_game.sharedui.generated.resources.Res
import harf_game.sharedui.generated.resources.archive_title
import harf_game.sharedui.generated.resources.home_edition
import harf_game.sharedui.generated.resources.home_play_today
import harf_game.sharedui.generated.resources.home_tagline
import harf_game.sharedui.generated.resources.settings_title
import harf_game.sharedui.generated.resources.statistics
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import uz.abumme.harfgame.billing.EntitlementGate
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.settings.AppSettings
import uz.abumme.harfgame.theme.GhostButton
import uz.abumme.harfgame.theme.HarfPalettes
import uz.abumme.harfgame.theme.LocalHarfColors
import uz.abumme.harfgame.theme.LocalHarfShapes
import uz.abumme.harfgame.theme.PrimaryButton
import uz.abumme.harfgame.theme.harfSerif

private val LANGUAGES = listOf(
    "uz-latn" to "Oʻzbekcha",
    "ru" to "Русский",
    "en" to "English",
    "kk" to "Қазақша",
)

@Composable
fun HomeScreen(
    onPlay: (String) -> Unit = {},
    onArchive: () -> Unit = {},
    onStats: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPaywall: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    val entitlements = koinInject<EntitlementRepository>()
    val controller = koinInject<PurchaseController>()
    val paletteId by settings.paletteId.collectAsState()
    val ents by entitlements.entitlements.collectAsState()
    val colors = LocalHarfColors.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
      // Bounded to 450.dp and centered so wide desktop/web windows frame the content instead of
      // stretching it edge to edge. Centered vertically when it fits; scrolls when it doesn't.
      // No fillMaxWidth here — it would override widthIn and stretch the column full width.
      Column(
        modifier = Modifier
            .widthIn(max = 450.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = buildAnnotatedString {
                append("Harf")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            color = colors.ink,
            fontFamily = harfSerif(),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(Res.string.home_tagline), color = colors.muted, fontSize = 14.sp)

        // Primary action: start today's puzzle, one per playable language.
        SectionLabel(stringResource(Res.string.home_play_today))
        for ((id, label) in LANGUAGES) {
            PrimaryButton(text = label, onClick = { onPlay(id) }, modifier = Modifier.fillMaxWidth())
        }

        // Secondary navigation, demoted to a ghost row.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GhostButton(stringResource(Res.string.archive_title), onArchive, Modifier.weight(1f))
            GhostButton(stringResource(Res.string.statistics), onStats, Modifier.weight(1f))
            GhostButton(stringResource(Res.string.settings_title), onSettings, Modifier.weight(1f))
        }

        // Editions shown as selectable swatches (recognition, not a blind cycle). The active
        // edition's name sits opposite the "Edition" label so the current choice is always legible.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.home_edition).uppercase(),
                color = colors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Text(
                HarfPalettes.byId(paletteId).displayName,
                color = colors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            for (palette in HarfPalettes.all) {
                val unlocked = EntitlementGate.canApplyTheme(palette.id, ents, controller.isAvailable)
                EditionSwatch(
                    palette = palette,
                    selected = palette.id == paletteId,
                    locked = !unlocked,
                    onClick = { if (unlocked) settings.setPaletteId(palette.id) else onPaywall() },
                )
            }
        }
      }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalHarfColors.current
    Text(
        text.uppercase(),
        color = c.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp),
    )
}

@Composable
private fun EditionSwatch(
    palette: uz.abumme.harfgame.theme.HarfPalette,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalHarfColors.current
    val shape = LocalHarfShapes.current.swatch
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(palette.colors.paper, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.rule, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = palette.displayName },
        contentAlignment = Alignment.Center,
    ) {
        // A dot of the edition's accent identifies it at a glance.
        Box(Modifier.size(16.dp).clip(shape).background(palette.colors.accent))
        if (locked) Text("🔒", fontSize = 11.sp)
    }
}
