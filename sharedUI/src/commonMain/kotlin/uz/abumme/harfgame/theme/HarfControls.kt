package uz.abumme.harfgame.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shared control system: one primary, one secondary (ghost), one danger button, plus a
 * consistent screen top bar. Radii come from [LocalHarfShapes]; colors from [LocalHarfColors].
 * Screens use these instead of hand-rolling buttons so the mockup's shapes and hierarchy hold.
 */

private val MinTouch = 44.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalHarfColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTouch),
        shape = LocalHarfShapes.current.button,
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.paper),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalHarfColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTouch),
        shape = LocalHarfShapes.current.button,
        border = BorderStroke(1.5.dp, c.accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
    ) { Text(text, Modifier.basicMarquee(), fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalHarfColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTouch),
        shape = LocalHarfShapes.current.button,
        border = BorderStroke(1.5.dp, c.danger),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/**
 * A segmented switch (like a light/dark theme toggle): one rounded track holding mutually
 * exclusive options, the selected one filled with the accent. Reused for the Uzbek script
 * switch and any other small either/or choice. [options] is a list of key to visible label.
 */
@Composable
fun SegmentedSwitch(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalHarfColors.current
    val track = LocalHarfShapes.current.button
    val segment = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .clip(track)
            .background(c.paper2)
            .border(1.dp, c.rule, track)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((key, label) in options) {
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .clip(segment)
                    .background(if (selected) c.accent else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) c.paper else c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Consistent screen top bar: a labeled, accessible back control + serif title. Reused by every
 * non-Home screen so it is dismissible on desktop/web without a system gesture. [backLabel] is
 * the visible + accessible name of the back action.
 */
@Composable
fun ScreenTopBar(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalHarfColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = backLabel },
        ) {
            Text("‹  $backLabel", color = c.accent, fontWeight = FontWeight.SemiBold)
        }
        Text(
            title,
            fontFamily = harfSerif(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = c.ink,
        )
        // Balance the back control so the title reads optically centered.
        Spacer(Modifier.width(56.dp))
    }
}
