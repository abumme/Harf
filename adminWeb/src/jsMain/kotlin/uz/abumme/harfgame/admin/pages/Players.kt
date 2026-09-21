package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.forms.Switch
import com.varabyte.kobweb.silk.components.icons.lucide.LucideCopy
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.DateField
import uz.abumme.harfgame.admin.components.FieldLabelStyle
import uz.abumme.harfgame.admin.components.IconButton
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.formatDate
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.players.PlayerFilters
import uz.abumme.harfgame.admin.players.PlayerListState
import uz.abumme.harfgame.admin.players.TypeFilter
import uz.abumme.harfgame.admin.players.shortAccountId
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.players.PlayerSummaryDto
import uz.abumme.harfgame.data.api.ApiResult
import org.w3c.dom.HTMLElement

private const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * `/players` (ADMIN): player accounts, newest first. Search by account id or part of the name, filter by account type,
 * creation dates and blocked suggestions; the filters live in the query string, so a reload or a shared link restores
 * the search. Pages are appended with "Показать ещё".
 */
@Page
@Composable
fun PlayersPage() {
    RequireSession(PageAccess.PLAYERS) { me ->
        AdminShell(me, NavSection.PLAYERS) { PlayerSearch() }
    }
}

@Composable
private fun PlayerSearch() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    // A confirmation carried over from a player's page after a deletion (`?deleted=1`); the URL drops it right away.
    val deleted = remember { ctx.route.params[Routes.PLAYER_DELETED_PARAM] != null }
    val initial = remember { PlayerFilters.fromRoute(ctx.route.params) }
    var filters by remember { mutableStateOf(initial) }
    var search by remember { mutableStateOf(initial.search) }
    var list by remember { mutableStateOf(PlayerListState(initial)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    suspend fun loadNext(state: PlayerListState) {
        val request = state.nextRequest() ?: return
        loading = true
        when (val result = AdminApp.api.players(request)) {
            is ApiResult.Success -> {
                list = list.append(state.filters, result.data)
                error = null
            }
            is ApiResult.Error -> error = result.fieldError()?.let { Strings.fieldReason(it.field, it.reason) } ?: generalMessage(result)
        }
        loading = false
    }

    LaunchedEffect(search) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        filters = filters.withSearch(search)
    }

    LaunchedEffect(filters) {
        ctx.router.navigateTo(Routes.PLAYERS + filters.toRouteQuery(), UpdateHistoryMode.REPLACE)
        val fresh = list.withFilters(filters)
        list = fresh
        error = null
        loadNext(fresh)
    }

    PageHeader(Strings.Players.TITLE, Strings.Players.SUBTITLE)
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        if (deleted) Notice(Strings.Players.DELETED, NoticeTone.SUCCESS)
        Div(PanelStyle.toModifier().then(AuditFiltersStyle.toModifier()).toAttrs { attr("role", "search") }) {
            TextField(
                "players-search",
                Strings.Players.SEARCH,
                search,
                { search = it },
                hint = Strings.Players.SEARCH_HINT,
                error = filters.searchProblem?.let { Strings.fieldReason("q", it) },
                autoComplete = "off",
            )
            DateField("players-from", Strings.Players.FILTER_FROM, filters.createdFrom, { filters = filters.withCreatedFrom(it) })
            DateField("players-to", Strings.Players.FILTER_TO, filters.createdToInclusive, { filters = filters.withCreatedToInclusive(it) })
            TypeToggles(filters.typeFilter) { filters = filters.withType(it) }
            BlockedSwitch(filters.blockedOnly) { filters = filters.withBlockedOnly(it) }
            if (filters.hasFilters) {
                Div { ActionButton(Strings.Players.RESET_FILTERS, onClick = { search = ""; filters = filters.withoutFilters() }) }
            }
        }
        P(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Players.DATES_HINT) }
        Notice(if (copied) Strings.Players.COPIED else null, NoticeTone.INFO)
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = Strings.Players.TITLE,
            columns = playerColumns(onCopy = { id ->
                copyToClipboard(id)
                copied = true
            }),
            rows = list.items,
            emptyText = if (error != null || filters.searchProblem != null) "" else Strings.Players.EMPTY,
            loading = loading,
            footer = if (!list.loaded || list.items.isEmpty()) null else {
                {
                    Span { Text(Strings.Players.shown(list.items.size)) }
                    if (list.hasMore) {
                        ActionButton(Strings.Players.LOAD_MORE, onClick = { scope.launch { loadNext(list) } }, enabled = !loading)
                    } else {
                        Span { Text(Strings.Players.ALL_SHOWN) }
                    }
                }
            },
        )
    }
}

/** The account-type choice as a row of toggle buttons; the pressed one is the filter. */
@Composable
private fun TypeToggles(selected: TypeFilter, onSelect: (TypeFilter) -> Unit) {
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "min-width" to "0").toAttrs()) {
        Span(FieldLabelStyle.toModifier().toAttrs { id("players-type-label") }) { Text(Strings.Players.FILTER_TYPE) }
        Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs {
            attr("role", "group")
            attr("aria-labelledby", "players-type-label")
        }) {
            TypeFilter.entries.forEach { filter ->
                val pressed = filter == selected
                ActionButton(
                    Strings.Players.type(filter),
                    onClick = { onSelect(filter) },
                    kind = if (pressed) ButtonKind.PRIMARY else ButtonKind.QUIET,
                    modifier = Modifier.css("height" to "36px", "padding" to "0 12px").attrsModifier { attr("aria-pressed", pressed.toString()) },
                )
            }
        }
    }
}

/** Silk's switch for "only blocked"; its hidden checkbox gets the label as its accessible name. */
@Composable
private fun BlockedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "10px", "min-height" to "40px").toAttrs()) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            ref = ref { element: HTMLElement -> element.querySelector("input")?.setAttribute("aria-label", Strings.Players.FILTER_BLOCKED) },
        )
        Span(Modifier.css("font-size" to "14px", "cursor" to "pointer").toAttrs { onClick { onChange(!checked) } }) {
            Text(Strings.Players.FILTER_BLOCKED)
        }
    }
}

private fun copyToClipboard(text: String) {
    try {
        window.navigator.clipboard.writeText(text)
    } catch (e: Throwable) {
        // No clipboard access (insecure context): the id stays visible on the detail page.
    }
}

private fun playerColumns(onCopy: (String) -> Unit): List<TableColumn<PlayerSummaryDto>> = listOf(
    TableColumn(Strings.Players.COLUMN_ACCOUNT) { player ->
        Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "6px", "white-space" to "nowrap").toAttrs()) {
            Anchor(
                Routes.player(player.id),
                Modifier.css("color" to Tokens.INK, "font-weight" to "650", "font-family" to "ui-monospace, SFMono-Regular, Consolas, monospace").toAttrs {
                    attr("title", player.id)
                },
            ) { Text(shortAccountId(player.id)) }
            IconButton(Strings.Players.COPY_ID, onClick = { onCopy(player.id) }) { LucideCopy(Modifier.css("width" to "15px", "height" to "15px")) }
        }
    },
    TableColumn(Strings.Players.COLUMN_NAME) { player -> Text(player.displayName ?: Strings.Common.NOT_SET) },
    TableColumn(Strings.Players.COLUMN_SIGN_IN) { player ->
        Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs()) {
            if (player.providers.isEmpty()) Badge(Strings.Players.ANONYMOUS, TileTone.OPEN)
            player.providers.forEach { Badge(Strings.Players.provider(it), TileTone.CORRECT) }
        }
    },
    TableColumn(Strings.Players.COLUMN_CREATED) { player ->
        Span(Modifier.css("white-space" to "nowrap").toAttrs { attr("title", formatDateTime(player.createdAt)) }) { Text(formatDate(player.createdAt)) }
    },
    TableColumn(Strings.Players.COLUMN_SUGGESTIONS) { player ->
        if (player.suggestionsBlocked) Badge(Strings.Players.BLOCKED, TileTone.ABSENT) else Text(Strings.Common.NOT_SET)
    },
)
