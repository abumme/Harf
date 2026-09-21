package uz.abumme.harfgame.admin.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.icons.lucide.LucideArrowDown
import com.varabyte.kobweb.silk.components.icons.lucide.LucideArrowUp
import com.varabyte.kobweb.silk.components.icons.lucide.LucideArrowUpDown
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronLeft
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronRight
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.w3c.dom.HTMLElement
import uz.abumme.harfgame.admin.Strings

enum class ButtonKind { PRIMARY, QUIET, DANGER }

/** A native button in one of the panel's three button styles. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    kind: ButtonKind = ButtonKind.QUIET,
    enabled: Boolean = true,
    submit: Boolean = false,
    id: String? = null,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val style = when (kind) {
        ButtonKind.PRIMARY -> PrimaryButtonStyle
        ButtonKind.QUIET -> QuietButtonStyle
        ButtonKind.DANGER -> DangerButtonStyle
    }
    Button(style.toModifier().then(modifier).toAttrs {
        type(if (submit) ButtonType.Submit else ButtonType.Button)
        id?.let { id(it) }
        if (!enabled) disabled()
        if (!submit) onClick { onClick() }
    }) {
        icon?.invoke()
        Text(text)
    }
}

enum class NoticeTone { ERROR, SUCCESS, INFO }

/** A one-line message above a form or table. Errors are announced immediately, confirmations politely. */
@Composable
fun Notice(text: String?, tone: NoticeTone, modifier: Modifier = Modifier) {
    if (text.isNullOrEmpty()) return
    val accent = when (tone) {
        NoticeTone.ERROR -> Tokens.DANGER
        NoticeTone.SUCCESS -> Tokens.CORRECT
        NoticeTone.INFO -> Tokens.PRESENT
    }
    Div(NoticeStyle.toModifier().css("border-left-color" to accent).then(modifier).toAttrs {
        attr("role", if (tone == NoticeTone.ERROR) "alert" else "status")
    }) { Text(text) }
}

val DialogStyle = CssStyle.base {
    Modifier.css(
        "margin-top" to "12vh",
        "width" to "min(440px, calc(100vw - 32px))",
        "background-color" to Tokens.SURFACE,
        "color" to Tokens.INK,
        "border-radius" to "10px",
        "border" to "1px solid ${Tokens.LINE}",
        "box-shadow" to "0 18px 50px rgba(0, 0, 0, 0.25)",
        "padding" to "22px",
        "display" to "flex",
        "flex-direction" to "column",
        "gap" to "14px",
    )
}

/**
 * Asks before an action that cannot simply be undone (disable an account, reset a password). The action runs only
 * from [onConfirm]; Escape, the scrim and Cancel all dismiss. [extra] can show a field or an error inside;
 * [confirmEnabled] false keeps the confirm button disabled until that field is filled in as required.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    busy: Boolean = false,
    confirmEnabled: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    Overlay(Modifier.css("background-color" to Tokens.SCRIM, "z-index" to "50").onClick { if (!busy) onDismiss() }) {
        Div(DialogStyle.toModifier().toAttrs {
            attr("role", "alertdialog")
            attr("aria-modal", "true")
            attr("aria-labelledby", "confirm-title")
            attr("aria-describedby", "confirm-body")
            onClick { it.stopPropagation() }
            onKeyDown { if (it.key == "Escape" && !busy) onDismiss() }
        }) {
            H2(SectionTitleStyle.toModifier().toAttrs { id("confirm-title") }) { Text(title) }
            P(MutedTextStyle.toModifier().toAttrs { id("confirm-body") }) { Text(body) }
            extra?.invoke()
            Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
                ActionButton(Strings.Common.CANCEL, onDismiss, ButtonKind.QUIET, enabled = !busy, id = "confirm-cancel")
                ActionButton(confirmLabel, onConfirm, if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, enabled = !busy && confirmEnabled, id = "confirm-ok")
            }
        }
    }
    LaunchedEffect(Unit) { (document.getElementById("confirm-cancel") as? HTMLElement)?.focus() }
}

/** One column of a [DataTable]; with a [sortKey] its header sorts the table. */
class TableColumn<T>(
    val header: String,
    val numeric: Boolean = false,
    val sortKey: String? = null,
    val cell: @Composable (T) -> Unit,
)

/** The server-side sort a [DataTable] shows: the column's sort key and direction. */
data class TableSort(val key: String, val descending: Boolean)

val SortHeaderStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "inline-flex",
            "align-items" to "center",
            "gap" to "4px",
            "padding" to "0",
            "border" to "none",
            "background" to "none",
            "font" to "inherit",
            "color" to "inherit",
            "cursor" to "pointer",
        )
    }
    hover { Modifier.css("color" to Tokens.INK) }
}

/**
 * A dialog for a small form (add, edit): a titled card over a scrim. Escape, the scrim and the form's own cancel
 * button dismiss it, unless [busy]. [wide] fits a longer list.
 */
@Composable
fun FormDialog(
    id: String,
    title: String,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    Overlay(Modifier.css("background-color" to Tokens.SCRIM, "z-index" to "50", "overflow-y" to "auto").onClick { if (!busy) onDismiss() }) {
        Div(DialogStyle.toModifier()
            .css("margin-bottom" to "8vh")
            .then(if (wide) Modifier.css("width" to "min(640px, calc(100vw - 32px))", "margin-top" to "6vh") else Modifier)
            .toAttrs {
                attr("role", "dialog")
                attr("aria-modal", "true")
                attr("aria-labelledby", "$id-title")
                onClick { it.stopPropagation() }
                onKeyDown { if (it.key == "Escape" && !busy) onDismiss() }
            }) {
            H2(SectionTitleStyle.toModifier().toAttrs { id("$id-title") }) { Text(title) }
            content()
        }
    }
}

val TableScrollStyle = CssStyle.base {
    Modifier.css("overflow-x" to "auto", "width" to "100%")
}

val DataTableStyle = CssStyle {
    base {
        Modifier.css(
            "width" to "100%",
            "border-collapse" to "collapse",
            "font-size" to "14px",
            "font-variant-numeric" to "tabular-nums",
        )
    }
    cssRule(" th") {
        Modifier.css(
            "text-align" to "left",
            "font-weight" to "600",
            "font-size" to "13px",
            "color" to Tokens.MUTED,
            "padding" to "10px 12px",
            "border-bottom" to "1px solid ${Tokens.LINE}",
            "white-space" to "nowrap",
            "background-color" to Tokens.SURFACE_SUNKEN,
        )
    }
    cssRule(" td") {
        Modifier.css("padding" to "11px 12px", "border-bottom" to "1px solid ${Tokens.LINE}", "vertical-align" to "top")
    }
    cssRule(" tbody tr:last-child td") { Modifier.css("border-bottom" to "none") }
    cssRule(" tbody tr:hover td") { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN) }
    cssRule(" .numeric") { Modifier.css("text-align" to "right") }
}

val TableFooterStyle = CssStyle.base {
    Modifier.css(
        "display" to "flex",
        "align-items" to "center",
        "justify-content" to "space-between",
        "gap" to "12px",
        "padding" to "10px 14px",
        "border-top" to "1px solid ${Tokens.LINE}",
        "font-size" to "14px",
        "color" to Tokens.MUTED,
    )
}

/**
 * A table inside a panel. With [paging] it shows the server's range and previous/next controls, and [onPageChange]
 * gets the requested page; the caller loads it. A cursor-paged list passes no [paging] and a [footer] instead (e.g. a
 * "load more" button). Columns with a sort key become sort buttons reporting to [onSort] ([sort] marks the current
 * one). A row for which [expanded] is true gets [expandedContent] in a full-width row below.
 */
@Composable
fun <T> DataTable(
    caption: String,
    columns: List<TableColumn<T>>,
    rows: List<T>,
    emptyText: String,
    loading: Boolean = false,
    paging: PagingState? = null,
    onPageChange: (PagingState) -> Unit = {},
    sort: TableSort? = null,
    onSort: (String) -> Unit = {},
    expanded: (T) -> Boolean = { false },
    expandedContent: (@Composable (T) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Div(PanelStyle.toModifier().css("overflow" to "hidden").toAttrs()) {
        Div(TableScrollStyle.toModifier().toAttrs()) {
            Table(DataTableStyle.toModifier().toAttrs {
                attr("aria-label", caption)
                attr("aria-busy", loading.toString())
            }) {
                Thead {
                    Tr {
                        columns.forEach { column ->
                            val key = column.sortKey
                            val sorted = key != null && key == sort?.key
                            Th({
                                attr("scope", "col")
                                if (column.numeric) classes("numeric")
                                if (key != null) {
                                    attr("aria-sort", if (!sorted) "none" else if (sort?.descending == true) "descending" else "ascending")
                                }
                            }) {
                                if (key == null) {
                                    Text(column.header)
                                } else {
                                    Button(SortHeaderStyle.toModifier().toAttrs {
                                        type(ButtonType.Button)
                                        onClick { onSort(key) }
                                    }) {
                                        Text(column.header)
                                        val iconSize = Modifier.css("width" to "14px", "height" to "14px")
                                        when {
                                            !sorted -> LucideArrowUpDown(iconSize.css("opacity" to "0.45"))
                                            sort?.descending == true -> LucideArrowDown(iconSize)
                                            else -> LucideArrowUp(iconSize)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Tbody {
                    rows.forEach { row ->
                        Tr {
                            columns.forEach { column ->
                                Td({ if (column.numeric) classes("numeric") }) { column.cell(row) }
                            }
                        }
                        if (expandedContent != null && expanded(row)) {
                            Tr {
                                Td({
                                    attr("colspan", columns.size.toString())
                                    style { property("background-color", Tokens.SURFACE_SUNKEN) }
                                }) { expandedContent(row) }
                            }
                        }
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            P(MutedTextStyle.toModifier().css("padding" to "28px 14px", "text-align" to "center").toAttrs()) {
                Text(if (loading) Strings.Common.LOADING else emptyText)
            }
        }
        footer?.let { content -> Div(TableFooterStyle.toModifier().toAttrs()) { content() } }
        if (paging != null && paging.total > 0) {
            Div(TableFooterStyle.toModifier().toAttrs()) {
                Span { Text(Strings.Common.range(paging.firstItem, paging.lastItem, paging.total)) }
                Div(Modifier.css("display" to "flex", "gap" to "8px", "align-items" to "center").toAttrs()) {
                    Span { Text(Strings.Common.pageOf(paging.page + 1, paging.pageCount)) }
                    IconButton(Strings.Common.PREVIOUS_PAGE, enabled = paging.hasPrevious && !loading, onClick = { onPageChange(paging.previous()) }) {
                        LucideChevronLeft()
                    }
                    IconButton(Strings.Common.NEXT_PAGE, enabled = paging.hasNext && !loading, onClick = { onPageChange(paging.next()) }) {
                        LucideChevronRight()
                    }
                }
            }
        }
    }
}

val IconButtonStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "inline-grid",
            "place-items" to "center",
            "width" to "34px",
            "height" to "34px",
            "border-radius" to "6px",
            "border" to "1px solid ${Tokens.LINE_STRONG}",
            "background-color" to Tokens.SURFACE,
            "color" to Tokens.INK,
            "cursor" to "pointer",
        )
    }
    hover { Modifier.css("background-color" to Tokens.SURFACE_SUNKEN) }
    cssRule(":disabled") { Modifier.css("opacity" to "0.45", "cursor" to "not-allowed") }
}

/** A square button with only an icon; [label] names it for screen readers and as a tooltip. */
@Composable
fun IconButton(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: @Composable () -> Unit) {
    Button(IconButtonStyle.toModifier().toAttrs {
        type(ButtonType.Button)
        attr("aria-label", label)
        attr("title", label)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { icon() }
}
