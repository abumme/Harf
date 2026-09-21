package uz.abumme.harfgame.admin.pages.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronLeft
import com.varabyte.kobweb.silk.components.icons.lucide.LucideCopy
import com.varabyte.kobweb.silk.components.icons.lucide.LucideEraser
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLogOut
import com.varabyte.kobweb.silk.components.icons.lucide.LucideShieldBan
import com.varabyte.kobweb.silk.components.icons.lucide.LucideShieldCheck
import com.varabyte.kobweb.silk.components.icons.lucide.LucideTrash2
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.style.until
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.ConfirmDialog
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.IconButton
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SectionTitleStyle
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.formatDate
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.players.accountIdFromRoute
import uz.abumme.harfgame.admin.players.deleteConfirmationMatches
import uz.abumme.harfgame.admin.players.deleteRequestFor
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerLanguageStatsDto
import uz.abumme.harfgame.data.admin.players.PlayerSuggestionDto
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

val PlayerFactsStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "grid",
            "grid-template-columns" to "max-content minmax(0, 1fr)",
            "gap" to "8px 20px",
            "padding" to "20px",
            "font-size" to "14px",
            "align-items" to "center",
        )
    }
    until(Breakpoint.SM) { Modifier.css("grid-template-columns" to "minmax(0, 1fr)", "gap" to "2px") }
}

/**
 * `/players/{id}` (ADMIN): one player account with its stats, suggestions and block, and the support actions, each
 * confirmed in a dialog. A dynamic route: the export has no page for it, so a direct load gets the backend's
 * `index.html` fallback and the router renders this page from the URL.
 */
@Page("{id}")
@Composable
fun PlayerPage() {
    val ctx = rememberPageContext()
    RequireSession(PageAccess.PLAYERS) { me ->
        AdminShell(me, NavSection.PLAYERS) { PlayerDetail(me, ctx.route.params["id"]) }
    }
}

private enum class PlayerAction { CLEAR_NAME, BLOCK, UNBLOCK, END_SESSIONS, DELETE }

@Composable
private fun PlayerDetail(me: MeDto, rawId: String?) {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val id = remember(rawId) { accountIdFromRoute(rawId) }
    val canWrite = Permission.PLAYERS_WRITE in me.permissions
    var player by remember(id) { mutableStateOf<PlayerDetailDto?>(null) }
    var notFound by remember(id) { mutableStateOf(id == null) }
    var loadError by remember(id) { mutableStateOf<String?>(null) }
    var notice by remember(id) { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var pending by remember(id) { mutableStateOf<PlayerAction?>(null) }
    var busy by remember(id) { mutableStateOf(false) }
    var actionError by remember(id) { mutableStateOf<String?>(null) }
    var typedId by remember(id) { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        when (val result = AdminApp.api.player(id)) {
            is ApiResult.Success -> player = result.data
            is ApiResult.Error -> if (result.code == AdminErrors.NOT_FOUND) notFound = true else loadError = generalMessage(result)
        }
    }

    PageHeader(player?.let { it.displayName ?: Strings.Players.TITLE_UNNAMED } ?: Strings.Players.TITLE, player?.id) {
        Anchor(Routes.PLAYERS, Modifier.css("display" to "inline-flex", "align-items" to "center", "gap" to "6px", "color" to Tokens.MUTED).toAttrs()) {
            LucideChevronLeft()
            Text(Strings.Players.BACK)
        }
    }
    val current = player
    if (current == null) {
        when {
            notFound -> Notice(Strings.Players.NOT_FOUND, NoticeTone.ERROR)
            loadError != null -> Notice(loadError, NoticeTone.ERROR)
            else -> Notice(Strings.Common.LOADING, NoticeTone.INFO)
        }
        return
    }

    fun run(action: PlayerAction) {
        busy = true
        actionError = null
        scope.launch {
            val failure: ApiResult.Error? = when (action) {
                PlayerAction.DELETE -> {
                    val request = deleteRequestFor(typedId, current.id)
                    if (request == null) null else when (val result = AdminApp.api.deletePlayer(current.id, request)) {
                        is ApiResult.Success -> {
                            ctx.router.navigateTo("${Routes.PLAYERS}?${Routes.PLAYER_DELETED_PARAM}=1", UpdateHistoryMode.PUSH)
                            null
                        }
                        is ApiResult.Error -> result
                    }
                }
                PlayerAction.END_SESSIONS -> when (val result = AdminApp.api.endPlayerSessions(current.id)) {
                    is ApiResult.Success -> {
                        (AdminApp.api.player(current.id) as? ApiResult.Success)?.let { player = it.data }
                        notice = Strings.Players.sessionsEnded(result.data.revoked) to NoticeTone.SUCCESS
                        null
                    }
                    is ApiResult.Error -> result
                }
                PlayerAction.CLEAR_NAME, PlayerAction.BLOCK, PlayerAction.UNBLOCK -> {
                    val result = when (action) {
                        PlayerAction.CLEAR_NAME -> AdminApp.api.clearPlayerDisplayName(current.id)
                        PlayerAction.BLOCK -> AdminApp.api.blockPlayerSuggestions(current.id)
                        else -> AdminApp.api.unblockPlayerSuggestions(current.id)
                    }
                    when (result) {
                        is ApiResult.Success -> {
                            player = result.data
                            notice = when (action) {
                                PlayerAction.CLEAR_NAME -> Strings.Players.NAME_CLEARED
                                PlayerAction.BLOCK -> Strings.Players.BLOCKED_DONE
                                else -> Strings.Players.UNBLOCKED_DONE
                            } to NoticeTone.SUCCESS
                            null
                        }
                        is ApiResult.Error -> result
                    }
                }
            }
            busy = false
            when {
                failure == null -> pending = null
                failure.code == AdminErrors.NOT_FOUND -> {
                    // Deleted meanwhile.
                    pending = null
                    player = null
                    notFound = true
                }
                else -> actionError = failure.fieldError()?.let { Strings.fieldReason(it.field, it.reason) } ?: generalMessage(failure)
            }
        }
    }

    fun ask(action: PlayerAction) {
        notice = null
        actionError = null
        typedId = ""
        pending = action
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "18px").toAttrs()) {
        notice?.let { (text, tone) -> Notice(text, tone) }
        Facts(current)
        LanguageStats(current.languages)
        Suggestions(current)
        Section(PanelStyle.toModifier().css("padding" to "20px", "display" to "flex", "flex-direction" to "column", "gap" to "10px").toAttrs()) {
            H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Players.BLOCK_SECTION) }
            val block = current.suggestionBlock
            if (block == null) {
                P { Text(Strings.Players.NOT_BLOCKED) }
            } else {
                P { Text(Strings.Players.blockedBy(block.blockedBy.displayName ?: block.blockedBy.username, formatDateTime(block.blockedAt))) }
            }
        }
        if (canWrite) {
            Section(PanelStyle.toModifier().css("padding" to "20px", "display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Players.ACTIONS_TITLE) }
                Div(Modifier.css("display" to "flex", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
                    ActionButton(Strings.Players.CLEAR_NAME, { ask(PlayerAction.CLEAR_NAME) }, enabled = !busy && current.displayName != null, icon = { LucideEraser() })
                    if (current.suggestionBlock == null) {
                        ActionButton(Strings.Players.BLOCK, { ask(PlayerAction.BLOCK) }, enabled = !busy, icon = { LucideShieldBan() })
                    } else {
                        ActionButton(Strings.Players.UNBLOCK, { ask(PlayerAction.UNBLOCK) }, enabled = !busy, icon = { LucideShieldCheck() })
                    }
                    ActionButton(Strings.Players.END_SESSIONS, { ask(PlayerAction.END_SESSIONS) }, enabled = !busy, icon = { LucideLogOut() })
                    ActionButton(Strings.Players.DELETE, { ask(PlayerAction.DELETE) }, ButtonKind.DANGER, enabled = !busy, icon = { LucideTrash2() })
                }
            }
        }
    }

    val open = pending ?: return
    val (title, body, label) = when (open) {
        PlayerAction.CLEAR_NAME -> Triple(Strings.Players.CLEAR_NAME_TITLE, Strings.Players.CLEAR_NAME_BODY, Strings.Players.CLEAR_NAME)
        PlayerAction.BLOCK -> Triple(Strings.Players.BLOCK_TITLE, Strings.Players.BLOCK_BODY, Strings.Players.BLOCK)
        PlayerAction.UNBLOCK -> Triple(Strings.Players.UNBLOCK_TITLE, Strings.Players.UNBLOCK_BODY, Strings.Players.UNBLOCK)
        PlayerAction.END_SESSIONS -> Triple(Strings.Players.END_SESSIONS_TITLE, Strings.Players.END_SESSIONS_BODY, Strings.Players.END_SESSIONS)
        PlayerAction.DELETE -> Triple(Strings.Players.DELETE_TITLE, Strings.Players.DELETE_BODY, Strings.Players.DELETE)
    }
    ConfirmDialog(
        title = title,
        body = body,
        confirmLabel = label,
        onConfirm = { run(open) },
        onDismiss = { pending = null },
        danger = open == PlayerAction.DELETE || open == PlayerAction.END_SESSIONS,
        busy = busy,
        confirmEnabled = open != PlayerAction.DELETE || deleteConfirmationMatches(typedId, current.id),
        extra = {
            if (open == PlayerAction.DELETE) {
                TextField("delete-confirm", Strings.Players.DELETE_CONFIRM, typedId, { typedId = it }, hint = current.id, autoComplete = "off", enabled = !busy)
            }
            Notice(actionError, NoticeTone.ERROR)
        },
    )
}

@Composable
private fun Facts(player: PlayerDetailDto) {
    Div(PanelStyle.toModifier().then(PlayerFactsStyle.toModifier()).toAttrs()) {
        Fact(Strings.Players.FACT_ID) {
            Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "min-width" to "0").toAttrs()) {
                Span(Modifier.css("font-family" to "ui-monospace, SFMono-Regular, Consolas, monospace", "overflow-wrap" to "anywhere").toAttrs()) { Text(player.id) }
                IconButton(Strings.Players.COPY_ID, onClick = {
                    try {
                        window.navigator.clipboard.writeText(player.id)
                    } catch (e: Throwable) {
                        // No clipboard access (insecure context): the id is selectable text.
                    }
                }) { LucideCopy(Modifier.css("width" to "15px", "height" to "15px")) }
            }
        }
        Fact(Strings.Players.FACT_CREATED) { Text(formatDateTime(player.createdAt)) }
        Fact(Strings.Players.FACT_NAME) { Text(player.displayName ?: Strings.Common.NOT_SET) }
        Fact(Strings.Players.FACT_SIGN_IN) {
            Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs()) {
                if (player.providers.isEmpty()) Badge(Strings.Players.ANONYMOUS, TileTone.OPEN)
                player.providers.forEach { Badge(Strings.Players.provider(it), TileTone.CORRECT) }
            }
        }
        Fact(Strings.Players.FACT_SNAPSHOT) { Text(player.lastStatsSnapshotAt?.let(::formatDateTime) ?: Strings.Players.NEVER_SYNCED) }
        Fact(Strings.Players.FACT_SESSIONS) { Text(player.activeSessions.toString()) }
    }
}

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Span(MutedTextStyle.toModifier().toAttrs()) { Text(label) }
    Div(Modifier.css("min-width" to "0").toAttrs()) { value() }
}

@Composable
private fun LanguageStats(languages: List<PlayerLanguageStatsDto>) {
    Section(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "8px").toAttrs()) {
        H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Players.STATS_TITLE) }
        P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Players.STATS_HINT) }
        DataTable(
            caption = Strings.Players.STATS_TITLE,
            columns = listOf(
                TableColumn(Strings.Players.COLUMN_LANGUAGE) { Text(Strings.Languages.label(it.lang)) },
                TableColumn(Strings.Players.COLUMN_GAMES, numeric = true) { Text(it.games.toString()) },
                TableColumn(Strings.Players.COLUMN_WINS, numeric = true) { Text(it.wins.toString()) },
                TableColumn(Strings.Players.COLUMN_WIN_RATE, numeric = true) { Text(Strings.Players.winRate(it.winRate)) },
                TableColumn(Strings.Players.COLUMN_STREAK, numeric = true) { Text(it.currentStreak.toString()) },
                TableColumn(Strings.Players.COLUMN_BEST_STREAK, numeric = true) { Text(it.bestStreak.toString()) },
            ),
            rows = languages,
            emptyText = Strings.Players.STATS_EMPTY,
        )
    }
}

@Composable
private fun Suggestions(player: PlayerDetailDto) {
    Section(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "8px").toAttrs()) {
        H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Players.SUGGESTIONS_TITLE) }
        val counts = player.suggestionCounts
        P(MutedTextStyle.toModifier().toAttrs()) {
            Text(Strings.Players.counts(counts.pending, counts.accepted, counts.rejected))
            if (player.recentSuggestions.isNotEmpty()) Text(" · " + Strings.Players.recent(player.recentSuggestions.size))
        }
        DataTable(
            caption = Strings.Players.SUGGESTIONS_TITLE,
            columns = listOf<TableColumn<PlayerSuggestionDto>>(
                TableColumn(Strings.Players.COLUMN_WORD) { Span(Modifier.css("font-weight" to "650").toAttrs()) { Text(it.word) } },
                TableColumn(Strings.Players.COLUMN_LANGUAGE) { Text(Strings.Languages.label(it.lang)) },
                TableColumn(Strings.Players.COLUMN_STATUS) { suggestion ->
                    val tone = when (suggestion.status) {
                        SuggestionStatus.PENDING -> TileTone.PRESENT
                        SuggestionStatus.ACCEPTED -> TileTone.CORRECT
                        SuggestionStatus.REJECTED -> TileTone.ABSENT
                    }
                    Badge(Strings.Suggestions.status(suggestion.status), tone)
                },
                TableColumn(Strings.Players.COLUMN_SUGGESTED) { suggestion ->
                    Span(Modifier.css("white-space" to "nowrap").toAttrs { attr("title", formatDateTime(suggestion.createdAt)) }) { Text(formatDate(suggestion.createdAt)) }
                },
                TableColumn(Strings.Players.COLUMN_DECIDED) { suggestion ->
                    Span(Modifier.css("white-space" to "nowrap").toAttrs()) { Text(suggestion.decidedAt?.let(::formatDateTime) ?: Strings.Common.NOT_SET) }
                },
            ),
            rows = player.recentSuggestions,
            emptyText = Strings.Players.RECENT_EMPTY,
        )
    }
}
