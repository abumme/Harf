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
import com.varabyte.kobweb.silk.components.icons.lucide.LucideCheck
import com.varabyte.kobweb.silk.components.icons.lucide.LucideX
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.LanguageTabs
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.StateTabs
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.formatDate
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.admin.suggestions.DecisionOutcome
import uz.abumme.harfgame.admin.suggestions.SuggestionTab
import uz.abumme.harfgame.admin.suggestions.SuggestionsQueryState
import uz.abumme.harfgame.admin.suggestions.decisionOutcome
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionParams
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/**
 * `/suggestions`: players' word suggestions in the member's languages. "Ожидают" lists pending ones oldest first with
 * accept and reject; "История" lists decided ones newest first. A decision follows the same apply-once rule as a
 * Telegram tap, so a suggestion decided elsewhere meanwhile answers "Уже обработано".
 */
@Page
@Composable
fun SuggestionsPage() {
    RequireSession(PageAccess.SUGGESTIONS) { me ->
        AdminShell(me, NavSection.SUGGESTIONS) { SuggestionReview(me) }
    }
}

@Composable
private fun SuggestionReview(me: MeDto) {
    val ctx = rememberPageContext()
    val initial = remember { SuggestionsQueryState.fromParams(ctx.route.params, me.languages) }
    PageHeader(Strings.Suggestions.TITLE, Strings.Suggestions.SUBTITLE)
    if (initial == null) {
        Notice(Strings.Words.NO_LANGUAGES, NoticeTone.INFO)
        return
    }
    var state by remember { mutableStateOf(initial) }
    var notice by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }

    LaunchedEffect(state) { ctx.router.navigateTo(Routes.SUGGESTIONS + state.toRouteQuery(), UpdateHistoryMode.REPLACE) }

    notice?.let { (text, tone) -> Notice(text, tone, Modifier.css("margin-bottom" to "14px")) }
    LanguageTabs(me.languages, state.lang, onSelect = { lang -> state = state.withLanguage(lang); notice = null }) { lang ->
        if (lang == state.lang) {
            StateTabs(
                options = SuggestionTab.entries.map { tab ->
                    tab.name to if (tab == SuggestionTab.PENDING) Strings.Suggestions.TAB_PENDING else Strings.Suggestions.TAB_HISTORY
                },
                selected = state.tab.name,
                onSelect = { value -> state = state.withTab(SuggestionTab.valueOf(value)); notice = null },
            ) { value ->
                if (value == state.tab.name) {
                    SuggestionList(state, onPage = { page -> state = state.withPage(page) }, onNotice = { notice = it })
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(
    state: SuggestionsQueryState,
    onPage: (Int) -> Unit,
    onNotice: (Pair<String, NoticeTone>?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SuggestionDto>>(emptyList()) }
    var paging by remember { mutableStateOf(PagingState(size = SuggestionParams.DEFAULT_SIZE)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(state, reload) {
        loading = true
        when (val result = AdminApp.api.suggestions(state.toApiQuery(SuggestionParams.DEFAULT_SIZE))) {
            is ApiResult.Success -> {
                items = result.data.items
                paging = PagingState(page = state.page, size = SuggestionParams.DEFAULT_SIZE).withTotal(result.data.total)
                error = null
            }
            is ApiResult.Error -> {
                items = emptyList()
                error = generalMessage(result)
            }
        }
        loading = false
    }

    fun decide(suggestion: SuggestionDto, accept: Boolean) {
        if (inFlight != null) return
        inFlight = suggestion.id
        onNotice(null)
        scope.launch {
            val outcome = decisionOutcome(AdminApp.api.decideSuggestion(suggestion.id, accept))
            onNotice(
                when (outcome) {
                    is DecisionOutcome.Applied ->
                        (if (accept) Strings.Suggestions.accepted(suggestion.word) else Strings.Suggestions.rejected(suggestion.word)) to NoticeTone.SUCCESS
                    DecisionOutcome.AlreadyDecided -> Strings.Suggestions.ALREADY_DECIDED to NoticeTone.INFO
                    DecisionOutcome.Forbidden -> Strings.Suggestions.FORBIDDEN to NoticeTone.ERROR
                    is DecisionOutcome.ValidationFailed -> Strings.Words.reason(outcome.reason) to NoticeTone.ERROR
                    is DecisionOutcome.Failed -> generalMessage(outcome.error) to NoticeTone.ERROR
                },
            )
            if (outcome.refreshList) reload++
            inFlight = null
        }
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        Notice(error, NoticeTone.ERROR)
        val pending = state.tab == SuggestionTab.PENDING
        DataTable(
            caption = "${Strings.Suggestions.TITLE} · ${Strings.Languages.label(state.lang)}",
            columns = if (pending) pendingColumns(inFlight, ::decide) else historyColumns(),
            rows = items,
            emptyText = if (error != null) "" else if (pending) Strings.Suggestions.EMPTY_PENDING else Strings.Suggestions.EMPTY_HISTORY,
            loading = loading,
            paging = paging,
            onPageChange = { onPage(it.page) },
        )
    }
}

private fun pendingColumns(inFlight: String?, decide: (SuggestionDto, Boolean) -> Unit): List<TableColumn<SuggestionDto>> = listOf(
    TableColumn(Strings.Suggestions.COLUMN_WORD) { Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(it.word) } },
    TableColumn(Strings.Suggestions.COLUMN_AUTHOR) { Text(it.author) },
    TableColumn(Strings.Suggestions.COLUMN_CREATED) { suggestion ->
        Span(Modifier.css("white-space" to "nowrap").toAttrs { attr("title", formatDateTime(suggestion.createdAt)) }) { Text(formatDate(suggestion.createdAt)) }
    },
    TableColumn(Strings.Suggestions.COLUMN_REASON) { suggestion ->
        Badge(Strings.Suggestions.reason(suggestion.reason), if (suggestion.reason == null) TileTone.OPEN else TileTone.PRESENT)
    },
    TableColumn(Strings.Suggestions.COLUMN_DECISION) { suggestion ->
        val compact = Modifier.css("height" to "32px", "padding" to "0 10px")
        val enabled = inFlight == null
        Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs()) {
            ActionButton(Strings.Suggestions.ACCEPT, { decide(suggestion, true) }, ButtonKind.PRIMARY, enabled = enabled, icon = { LucideCheck() }, modifier = compact)
            ActionButton(Strings.Suggestions.REJECT, { decide(suggestion, false) }, ButtonKind.QUIET, enabled = enabled, icon = { LucideX() }, modifier = compact)
        }
    },
)

private fun historyColumns(): List<TableColumn<SuggestionDto>> = listOf(
    TableColumn(Strings.Suggestions.COLUMN_WORD) { Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(it.word) } },
    TableColumn(Strings.Suggestions.COLUMN_AUTHOR) { Text(it.author) },
    TableColumn(Strings.Suggestions.COLUMN_OUTCOME) { suggestion ->
        Badge(Strings.Suggestions.status(suggestion.status), if (suggestion.status == SuggestionStatus.ACCEPTED) TileTone.CORRECT else TileTone.ABSENT)
    },
    TableColumn(Strings.Suggestions.COLUMN_DECIDED_BY) { Text(it.decidedBy?.let(Strings.Suggestions::decider) ?: Strings.Common.NOT_SET) },
    TableColumn(Strings.Suggestions.COLUMN_DECIDED_AT) { suggestion ->
        Span(Modifier.css("white-space" to "nowrap").toAttrs()) { Text(suggestion.decidedAt?.let(::formatDateTime) ?: Strings.Common.NOT_SET) }
    },
)
