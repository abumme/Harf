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
import com.varabyte.kobweb.silk.components.icons.lucide.LucideListPlus
import com.varabyte.kobweb.silk.components.icons.lucide.LucidePlus
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
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
import uz.abumme.harfgame.admin.components.ConfirmDialog
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.DateField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SelectField
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TableSort
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.LanguageTabs
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.formatDate
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.pages.words.AddWordDialog
import uz.abumme.harfgame.admin.pages.words.BulkAddDialog
import uz.abumme.harfgame.admin.pages.words.EditWordDialog
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.admin.words.StatusFilter
import uz.abumme.harfgame.admin.words.WordSort
import uz.abumme.harfgame.admin.words.WordsQueryState
import uz.abumme.harfgame.admin.words.wordErrorMessage
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.admin.words.WordParams
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.api.ApiResult

private const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * `/words`: the word catalog of the member's languages (a WORDER's assigned ones, every one for an ADMIN). Language,
 * filters, sort and page live in the query string. Nothing here shows or implies anything about daily words.
 */
@Page
@Composable
fun WordsPage() {
    RequireSession(PageAccess.WORDS) { me ->
        AdminShell(me, NavSection.WORDS) { WordCatalog(me) }
    }
}

private sealed interface WordDialog {
    data object Add : WordDialog
    data object Bulk : WordDialog
    data class Edit(val word: WordDto) : WordDialog
    data class Remove(val word: WordDto) : WordDialog
}

@Composable
private fun WordCatalog(me: MeDto) {
    val ctx = rememberPageContext()
    val canWrite = Permission.WORDS_WRITE in me.permissions
    val initial = remember { WordsQueryState.fromParams(ctx.route.params, me.languages) }
    if (initial == null) {
        PageHeader(Strings.Words.TITLE)
        Notice(Strings.Words.NO_LANGUAGES, NoticeTone.INFO)
        return
    }
    var state by remember { mutableStateOf(initial) }
    var dialog by remember { mutableStateOf<WordDialog?>(null) }
    var notice by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(state) { ctx.router.navigateTo(Routes.WORDS + state.toRouteQuery(), UpdateHistoryMode.REPLACE) }

    fun changed(message: String) {
        notice = message to NoticeTone.SUCCESS
        dialog = null
        reload++
    }

    PageHeader(Strings.Words.TITLE) {
        if (canWrite) {
            ActionButton(Strings.Words.BULK_ADD, { notice = null; dialog = WordDialog.Bulk }, ButtonKind.QUIET, icon = { LucideListPlus() })
            ActionButton(Strings.Words.ADD, { notice = null; dialog = WordDialog.Add }, ButtonKind.PRIMARY, icon = { LucidePlus() })
        }
    }
    notice?.let { (text, tone) -> Notice(text, tone, Modifier.css("margin-bottom" to "14px")) }
    LanguageTabs(me.languages, state.lang, onSelect = { state = state.withLanguage(it); notice = null }) { lang ->
        if (lang == state.lang) {
            WordList(
                me, state, reload, canWrite,
                onState = { update -> state = update(state) },
                onDialog = { notice = null; dialog = it },
                onNotice = { notice = it },
                onChanged = { message -> changed(message) },
            )
        }
    }

    when (val open = dialog) {
        null -> {}
        WordDialog.Add -> AddWordDialog(state.lang, onDismiss = { dialog = null }) { word ->
            changed(if (word.restored) Strings.Words.RESTORED_BY_ADD else Strings.Words.ADDED)
        }
        WordDialog.Bulk -> BulkAddDialog(state.lang, onDismiss = { dialog = null }, onChanged = { reload++ })
        is WordDialog.Edit -> EditWordDialog(open.word, onDismiss = { dialog = null }) { changed(Strings.Words.EDITED) }
        is WordDialog.Remove -> RemoveWordDialog(open.word, onDismiss = { dialog = null }) { changed(Strings.Words.REMOVED) }
    }
}

@Composable
private fun RemoveWordDialog(word: WordDto, onDismiss: () -> Unit, onRemoved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = Strings.Words.REMOVE_TITLE,
        body = Strings.Words.removeBody(word.text),
        confirmLabel = Strings.Words.REMOVE,
        danger = true,
        busy = busy,
        onDismiss = onDismiss,
        onConfirm = {
            busy = true
            error = null
            scope.launch {
                when (val result = AdminApp.api.removeWord(word.id)) {
                    is ApiResult.Success -> onRemoved()
                    is ApiResult.Error -> error = wordErrorMessage(result, word.lang)
                }
                busy = false
            }
        },
        extra = { Notice(error, NoticeTone.ERROR) },
    )
}

@Composable
private fun WordList(
    me: MeDto,
    state: WordsQueryState,
    reload: Int,
    canWrite: Boolean,
    /** Applies a change to the current view (not to a copy captured earlier, e.g. before a debounce). */
    onState: ((WordsQueryState) -> WordsQueryState) -> Unit,
    onDialog: (WordDialog) -> Unit,
    onNotice: (Pair<String, NoticeTone>?) -> Unit,
    onChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<WordDto>>(emptyList()) }
    var paging by remember { mutableStateOf(PagingState(size = WordParams.DEFAULT_SIZE)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember(state.lang) { mutableStateOf(state.q) }
    var adders by remember { mutableStateOf(listOf(me.id to Strings.Words.ADDED_BY_ME)) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Who can be picked as "added by": every staff member for an ADMIN, the member themself otherwise.
        if (Permission.STAFF_MANAGE in me.permissions) {
            val staff = AdminApp.api.staffList()
            if (staff is ApiResult.Success) adders = staff.data.map { it.id to (it.displayName?.let { name -> "$name (${it.username})" } ?: it.username) }
        }
    }

    LaunchedEffect(search) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        onState { it.withSearch(search) }
    }

    LaunchedEffect(state, reload) {
        loading = true
        when (val result = AdminApp.api.words(state.toApiQuery(WordParams.DEFAULT_SIZE))) {
            is ApiResult.Success -> {
                words = result.data.items
                paging = PagingState(page = state.page, size = WordParams.DEFAULT_SIZE).withTotal(result.data.total)
                error = null
                if (paging.page != state.page) onState { it.withPage(paging.page) }
            }
            is ApiResult.Error -> {
                words = emptyList()
                error = generalMessage(result)
            }
        }
        loading = false
    }

    fun restore(word: WordDto) {
        busyId = word.id
        onNotice(null)
        scope.launch {
            when (val result = AdminApp.api.restoreWord(word.id)) {
                is ApiResult.Success -> onChanged(Strings.Words.RESTORED)
                is ApiResult.Error -> onNotice(wordErrorMessage(result, word.lang) to NoticeTone.ERROR)
            }
            busyId = null
        }
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        Div(PanelStyle.toModifier().then(AuditFiltersStyle.toModifier()).toAttrs { attr("role", "search") }) {
            TextField("words-search", Strings.Words.SEARCH, search, { search = it }, autoComplete = "off", hint = null)
            SelectField(
                "words-status",
                Strings.Words.FILTER_STATUS,
                state.status.name,
                StatusFilter.entries.map { it.name to Strings.Words.statusFilter(it) },
                { value -> onState { it.withStatus(StatusFilter.valueOf(value)) } },
            )
            SelectField(
                "words-source",
                Strings.Words.FILTER_SOURCE,
                state.source,
                listOf("" to Strings.Words.ANY) + WordSource.entries.map { it.name to Strings.Words.source(it) },
                { source -> onState { it.withSource(source) } },
            )
            SelectField(
                "words-added-by",
                Strings.Words.FILTER_ADDED_BY,
                state.addedBy,
                listOf("" to Strings.Words.ANY) + adders,
                { staffId -> onState { it.withAddedBy(staffId) } },
            )
            DateField("words-from", Strings.Words.FILTER_FROM, state.addedFrom, { date -> onState { it.withAddedFrom(date) } })
            DateField("words-to", Strings.Words.FILTER_TO, state.addedTo, { date -> onState { it.withAddedTo(date) } })
            if (state.hasFilters) {
                Div { ActionButton(Strings.Words.RESET_FILTERS, onClick = { search = ""; onState { it.withoutFilters() } }) }
            }
        }
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = "${Strings.Words.TITLE} · ${Strings.Languages.label(state.lang)}",
            columns = wordColumns(state, canWrite, busyId, expanded,
                onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
                onEdit = { onDialog(WordDialog.Edit(it)) },
                onRemove = { onDialog(WordDialog.Remove(it)) },
                onRestore = ::restore,
            ),
            rows = words,
            emptyText = if (error != null) "" else Strings.Words.EMPTY,
            loading = loading,
            paging = paging,
            onPageChange = { target -> onState { it.withPage(target.page) } },
            sort = TableSort(state.sort.param, state.descending),
            onSort = { key -> WordSort.entries.firstOrNull { it.param == key }?.let { column -> onState { it.withSort(column) } } },
            expanded = { it.id in expanded },
            expandedContent = { Provenance(it) },
        )
    }
}

private fun wordColumns(
    state: WordsQueryState,
    canWrite: Boolean,
    busyId: String?,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onEdit: (WordDto) -> Unit,
    onRemove: (WordDto) -> Unit,
    onRestore: (WordDto) -> Unit,
): List<TableColumn<WordDto>> = buildList {
    add(TableColumn(Strings.Words.COLUMN_WORD, sortKey = WordSort.TEXT.param) { word ->
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px").toAttrs()) {
            Span(Modifier.css("font-weight" to "650", "font-size" to "15px").toAttrs()) { Text(word.text) }
            Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) {
                Text(word.graphemeCount?.let(Strings.Words::letters) ?: Strings.Common.NOT_SET)
            }
        }
    })
    add(TableColumn(Strings.Words.COLUMN_SOURCE) { word -> Badge(Strings.Words.source(word.source), TileTone.OPEN) })
    add(TableColumn(Strings.Words.COLUMN_ADDED, sortKey = WordSort.CREATED.param) { word ->
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px", "white-space" to "nowrap").toAttrs()) {
            Span(Modifier.toAttrs { attr("title", formatDateTime(word.createdAt)) }) { Text(formatDate(word.createdAt)) }
            word.createdBy?.let { Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(staffName(it)) } }
        }
    })
    add(TableColumn(Strings.Words.COLUMN_UPDATED, sortKey = WordSort.UPDATED.param) { word ->
        Span(Modifier.css("white-space" to "nowrap").toAttrs { attr("title", formatDateTime(word.updatedAt)) }) { Text(formatDate(word.updatedAt)) }
    })
    if (state.status != StatusFilter.ACTIVE) {
        add(TableColumn(Strings.Words.COLUMN_STATUS) { word ->
            Badge(Strings.Words.status(word.status), if (word.status == WordStatus.ACTIVE) TileTone.CORRECT else TileTone.ABSENT)
        })
    }
    add(TableColumn(Strings.Words.COLUMN_ACTIONS) { word ->
        val compact = Modifier.css("height" to "32px", "padding" to "0 10px")
        Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs()) {
            if (canWrite && word.status == WordStatus.ACTIVE) {
                ActionButton(Strings.Words.EDIT, { onEdit(word) }, ButtonKind.QUIET, enabled = busyId == null, modifier = compact)
                ActionButton(Strings.Words.REMOVE, { onRemove(word) }, ButtonKind.DANGER, enabled = busyId == null, modifier = compact)
            }
            if (canWrite && word.status == WordStatus.REMOVED) {
                ActionButton(Strings.Words.RESTORE, { onRestore(word) }, ButtonKind.QUIET, enabled = busyId == null, modifier = compact)
            }
            ActionButton(
                if (word.id in expanded) Strings.Words.HIDE_DETAILS else Strings.Words.DETAILS,
                { onToggle(word.id) },
                ButtonKind.QUIET,
                modifier = compact,
            )
        }
    })
}

private fun staffName(staff: StaffRefDto): String = staff.displayName?.let { "$it (${staff.username})" } ?: staff.username

/** Where the word came from and who touched it when; a word without a staff author predates staff tracking. */
@Composable
private fun Provenance(word: WordDto) {
    Div(Modifier.css("display" to "grid", "grid-template-columns" to "max-content 1fr", "gap" to "4px 16px", "margin" to "0", "font-size" to "14px").toAttrs()) {
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.COLUMN_SOURCE) }
        Span(Modifier.css("margin" to "0").toAttrs()) {
            Text(if (word.suggestionId != null || word.source == WordSource.AUTO || word.source == WordSource.SUGGESTION) Strings.Words.suggestionOrigin(word.source) else Strings.Words.source(word.source))
        }
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.PROVENANCE_ADDED) }
        Span(Modifier.css("margin" to "0").toAttrs()) {
            val who = word.createdBy?.let(::staffName)
                ?: if (word.source == WordSource.BUNDLED) Strings.Words.PREDATES_STAFF else Strings.Words.BY_SYSTEM
            Text("${formatDateTime(word.createdAt)} — $who")
        }
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.PROVENANCE_UPDATED) }
        Span(Modifier.css("margin" to "0").toAttrs()) {
            Text(formatDateTime(word.updatedAt) + (word.updatedBy?.let { " — ${staffName(it)}" } ?: ""))
        }
        if (word.status == WordStatus.REMOVED) {
            Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.PROVENANCE_REMOVED) }
            Span(Modifier.css("margin" to "0").toAttrs()) {
                Text((word.removedAt?.let(::formatDateTime) ?: Strings.Common.NOT_SET) + (word.removedBy?.let { " — ${staffName(it)}" } ?: ""))
            }
        }
    }
}
