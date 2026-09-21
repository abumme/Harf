package uz.abumme.harfgame.admin.components

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.disclosure.TabPanel
import com.varabyte.kobweb.silk.components.disclosure.Tabs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.Div
import uz.abumme.harfgame.admin.Strings

/** A Silk tab panel is a grid that sizes its child to the content; stretch it and let wide tables scroll inside instead. */
private val TabsFillModifier = Modifier.css("width" to "100%", "min-width" to "0")

/** Keeps every tab label on one line; when the labels do not fit, the tabs wrap onto a second row. */
val StateTabsStyle = CssStyle {
    cssRule(" > .silk-tabs-tab-row") { Modifier.css("max-width" to "100%", "flex-wrap" to "wrap") }
    cssRule(" > .silk-tabs-tab-row > .silk-tabs-tab") { Modifier.css("white-space" to "nowrap", "flex-shrink" to "0") }
}

/**
 * Silk tabs over [options] (value to label) with [selected] active; [content] renders the active option's panel. Silk
 * keeps its own selection only within one composition, so the caller's state drives it through `isDefault`.
 */
@Composable
fun StateTabs(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable (value: String) -> Unit,
) {
    Tabs(
        modifier = StateTabsStyle.toModifier().then(TabsFillModifier),
        commonTabModifier = Modifier.css("padding" to "8px 14px", "font-weight" to "600", "cursor" to "pointer"),
        commonPanelModifier = TabsFillModifier.css("padding" to "16px 0 0 0", "grid-template-columns" to "minmax(0, 1fr)"),
        onTabSelected = { index -> options.getOrNull(index)?.let { onSelect(it.first) } },
    ) {
        options.forEach { (value, label) ->
            TabPanel(label, isDefault = value == selected) {
                Div(TabsFillModifier.toAttrs()) { content(value) }
            }
        }
    }
}

/** [StateTabs] over the member's pack languages. */
@Composable
fun LanguageTabs(
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable (lang: String) -> Unit,
) {
    StateTabs(languages.map { it to Strings.Languages.label(it) }, selected, onSelect, content)
}
