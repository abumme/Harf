package uz.abumme.harfgame.admin.pages

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
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.icons.lucide.LucideClipboardPaste
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLink2
import com.varabyte.kobweb.silk.components.icons.lucide.LucidePlus
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.answerpool.PoolQueryState
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.ConfirmDialog
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.StateTabs
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.pages.answerpool.CandidatesDialog
import uz.abumme.harfgame.admin.pages.answerpool.PairDialog
import uz.abumme.harfgame.admin.pages.answerpool.PasteDialog
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.answerpool.PoolParams
import uz.abumme.harfgame.data.admin.answerpool.PoolWordDto
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.api.ApiResult

private const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * `/answer-pool`: the words each calendar's daily words are picked from, with the never-used counter. `en`, `ru` and
 * `kk` pools take catalog words (one by one or pasted as a list); the `uz` pool is made of Latin/Cyrillic pairs. The
 * calendar, search and page live in the query string.
 */
@Page
@Composable
fun AnswerPoolPage() {
    RequireSession(PageAccess.ANSWER_POOL) { me ->
        AdminShell(me, NavSection.ANSWER_POOL) { AnswerPools() }
    }
}

private sealed interface PoolDialog {
    data object Candidates : PoolDialog
    data object Paste : PoolDialog
    data object NewPair : PoolDialog
    data class Remove(val entry: PoolWordDto) : PoolDialog
}

@Composable
private fun AnswerPools() {
    val ctx = rememberPageContext()
    var state by remember { mutableStateOf(PoolQueryState.fromParams(ctx.route.params)) }
    var dialog by remember { mutableStateOf<PoolDialog?>(null) }
    var notice by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val uzbek = state.calendar == DailyCalendars.UZ

    LaunchedEffect(state) { ctx.router.navigateTo(Routes.ANSWER_POOL + state.toRouteQuery(), UpdateHistoryMode.REPLACE) }

    fun changed(message: String?) {
        message?.let { notice = it to NoticeTone.SUCCESS }
        reload++
    }

    PageHeader(Strings.AnswerPool.TITLE, Strings.AnswerPool.SUBTITLE) {
        if (uzbek) {
            ActionButton(Strings.AnswerPool.NEW_PAIR, { notice = null; dialog = PoolDialog.NewPair }, ButtonKind.PRIMARY, icon = { LucideLink2() })
        } else {
            ActionButton(Strings.AnswerPool.PASTE, { notice = null; dialog = PoolDialog.Paste }, ButtonKind.QUIET, icon = { LucideClipboardPaste() })
            ActionButton(Strings.AnswerPool.ADD, { notice = null; dialog = PoolDialog.Candidates }, ButtonKind.PRIMARY, icon = { LucidePlus() })
        }
    }
    notice?.let { (text, tone) -> Notice(text, tone, Modifier.css("margin-bottom" to "14px")) }
    StateTabs(
        options = DailyCalendars.ALL.map { it to Strings.Calendar.calendar(it) },
        selected = state.calendar,
        onSelect = { calendar -> state = state.withCalendar(calendar); notice = null },
    ) { calendar ->
        if (calendar == state.calendar) {
            PoolList(state, reload, onState = { update -> state = update(state) }, onRemove = { dialog = PoolDialog.Remove(it) })
        }
    }

    when (val open = dialog) {
        null -> {}
        PoolDialog.Candidates -> CandidatesDialog(state.calendar, onDismiss = { dialog = null }, onMarked = { changed(null) })
        PoolDialog.Paste -> PasteDialog(state.calendar, onDismiss = { dialog = null }, onMarked = { changed(null) })
        PoolDialog.NewPair -> PairDialog(onDismiss = { dialog = null }) {
            dialog = null
            changed(Strings.AnswerPool.PAIR_CREATED)
        }
        is PoolDialog.Remove -> RemoveDialog(state.calendar, open.entry, onDismiss = { dialog = null }) { message ->
            dialog = null
            changed(message)
        }
    }
}

@Composable
private fun PoolList(
    state: PoolQueryState,
    reload: Int,
    onState: ((PoolQueryState) -> PoolQueryState) -> Unit,
    onRemove: (PoolWordDto) -> Unit,
) {
    var entries by remember { mutableStateOf<List<PoolWordDto>>(emptyList()) }
    var unusedLeft by remember { mutableStateOf<Int?>(null) }
    var paging by remember { mutableStateOf(PagingState(size = PoolParams.DEFAULT_SIZE)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember(state.calendar) { mutableStateOf(state.q) }
    val uzbek = state.calendar == DailyCalendars.UZ

    LaunchedEffect(search) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        onState { it.withSearch(search) }
    }

    LaunchedEffect(state, reload) {
        loading = true
        when (val result = AdminApp.api.answerPool(state.calendar, state.toApiQuery(PoolParams.DEFAULT_SIZE))) {
            is ApiResult.Success -> {
                entries = result.data.page.items
                unusedLeft = result.data.unusedLeft
                paging = PagingState(page = state.page, size = PoolParams.DEFAULT_SIZE).withTotal(result.data.page.total)
                error = null
            }
            is ApiResult.Error -> {
                entries = emptyList()
                error = if (result.code == "not_found") Strings.Calendar.NOT_SET_UP else generalMessage(result)
            }
        }
        loading = false
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        Div(Modifier.css("display" to "flex", "gap" to "14px", "flex-wrap" to "wrap", "align-items" to "stretch").toAttrs()) {
            Div(PanelStyle.toModifier().css(
                "padding" to "12px 16px", "display" to "flex", "flex-direction" to "column", "gap" to "2px", "min-width" to "220px",
                "border-left" to "4px solid ${Tokens.CORRECT}",
            ).toAttrs { attr("title", Strings.AnswerPool.UNUSED_HINT) }) {
                Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.AnswerPool.UNUSED) }
                Span(Modifier.css("font-size" to "28px", "font-weight" to "750", "line-height" to "1.1", "font-variant-numeric" to "tabular-nums").toAttrs {
                    attr("aria-live", "polite")
                }) { Text(unusedLeft?.toString() ?: Strings.Common.NOT_SET) }
                Span(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) { Text(Strings.AnswerPool.UNUSED_HINT) }
            }
            Div(PanelStyle.toModifier().css("padding" to "12px 16px", "flex" to "1", "min-width" to "220px").toAttrs { attr("role", "search") }) {
                TextField("pool-search", Strings.AnswerPool.SEARCH, search, { search = it }, autoComplete = "off")
            }
        }
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = "${Strings.AnswerPool.TITLE} · ${Strings.Calendar.calendar(state.calendar)}",
            columns = poolColumns(uzbek, onRemove),
            rows = entries,
            emptyText = if (error != null) "" else if (uzbek) Strings.AnswerPool.EMPTY_PAIRS else Strings.AnswerPool.EMPTY,
            loading = loading,
            paging = paging,
            onPageChange = { target -> onState { it.withPage(target.page) } },
        )
    }
}

private fun poolColumns(uzbek: Boolean, onRemove: (PoolWordDto) -> Unit): List<TableColumn<PoolWordDto>> = buildList {
    add(TableColumn(if (uzbek) Strings.AnswerPool.COLUMN_LATIN else Strings.AnswerPool.COLUMN_WORD) { entry ->
        Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "flex-wrap" to "wrap").toAttrs()) {
            Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(entry.text) }
            if (!entry.active) Badge(if (uzbek) Strings.AnswerPool.PAIR_INACTIVE else Strings.AnswerPool.INACTIVE, TileTone.ABSENT)
        }
    })
    if (uzbek) {
        add(TableColumn(Strings.AnswerPool.COLUMN_CYRILLIC) { entry ->
            Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(entry.textCyrl ?: Strings.Common.NOT_SET) }
        })
    }
    add(TableColumn(Strings.AnswerPool.COLUMN_USAGE) { entry ->
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px", "font-size" to "14px").toAttrs()) {
            Span { Text(Strings.Calendar.lastUsed(entry.lastUsed)) }
            entry.scheduledOn?.let { Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Calendar.scheduledOn(it.day, it.source)) } }
        }
    })
    add(TableColumn(Strings.AnswerPool.COLUMN_ACTIONS) { entry ->
        ActionButton(
            if (uzbek) Strings.AnswerPool.REMOVE_PAIR else Strings.AnswerPool.UNMARK,
            { onRemove(entry) },
            ButtonKind.DANGER,
            modifier = Modifier.css("height" to "32px", "padding" to "0 10px"),
        )
    })
}

@Composable
private fun RemoveDialog(calendar: String, entry: PoolWordDto, onDismiss: () -> Unit, onRemoved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pair = entry.pairId
    ConfirmDialog(
        title = if (pair != null) Strings.AnswerPool.REMOVE_PAIR_TITLE else Strings.AnswerPool.UNMARK_TITLE,
        body = if (pair != null) Strings.AnswerPool.removePairBody(entry.text, entry.textCyrl.orEmpty()) else Strings.AnswerPool.unmarkBody(entry.text),
        confirmLabel = if (pair != null) Strings.AnswerPool.REMOVE_PAIR else Strings.AnswerPool.UNMARK,
        danger = true,
        busy = busy,
        onDismiss = onDismiss,
        onConfirm = {
            busy = true
            error = null
            scope.launch {
                val result = if (pair != null) AdminApp.api.removePair(pair) else AdminApp.api.unmarkWord(calendar, entry.wordId.orEmpty())
                when (result) {
                    is ApiResult.Success -> onRemoved(if (pair != null) Strings.AnswerPool.PAIR_REMOVED_DONE else Strings.AnswerPool.UNMARKED)
                    is ApiResult.Error -> error = result.fieldError()?.let { Strings.AnswerPool.reason(it.field, it.reason) } ?: generalMessage(result)
                }
                busy = false
            }
        },
        extra = { Notice(error, NoticeTone.ERROR) },
    )
}
