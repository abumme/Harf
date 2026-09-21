package uz.abumme.harfgame.admin.pages.answerpool

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.builders.InputAttrsScope
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLElement
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.answerpool.PairForm
import uz.abumme.harfgame.admin.answerpool.groupMarkResults
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.FormDialog
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.TextAreaField
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.words.BulkInput
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatus
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatusDto
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolParams
import uz.abumme.harfgame.data.api.ApiResult

private const val SEARCH_DEBOUNCE_MILLIS = 300L
private const val STATUS_DEBOUNCE_MILLIS = 350L
private const val CANDIDATES_SHOWN = 50

/** The message for a refused pool request: the pool or word reason it names, else a general one. */
private fun poolError(error: ApiResult.Error): String =
    error.fieldError()?.let { Strings.AnswerPool.reason(it.field, it.reason) } ?: generalMessage(error)

/** A searchable list of catalog words, each with a button; [label] names the button, [onChoose] handles a click. */
@Composable
private fun CandidateList(
    calendar: String,
    idPrefix: String,
    searchLabel: String,
    hint: String,
    reload: Int,
    label: String,
    enabled: (PoolCandidateDto) -> Boolean,
    onChoose: (PoolCandidateDto) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<PoolCandidateDto>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(search, reload) {
        if (search.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        loading = true
        val query = queryString(PoolParams.Q to search.trim().ifEmpty { null }, PoolParams.SIZE to CANDIDATES_SHOWN.toString())
        when (val result = AdminApp.api.poolCandidates(calendar, query)) {
            is ApiResult.Success -> {
                items = result.data.items
                total = result.data.total
                error = null
            }
            is ApiResult.Error -> error = generalMessage(result)
        }
        loading = false
    }

    TextField("$idPrefix-search", searchLabel, search, { search = it }, hint = hint, autoComplete = "off")
    Notice(error, NoticeTone.ERROR)
    if (!loading && items.isEmpty() && error == null) P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.AnswerPool.NO_CANDIDATES) }
    Ul(Modifier.css(
        "margin" to "0", "padding" to "0", "list-style" to "none", "max-height" to "36vh", "overflow-y" to "auto",
        "border" to "1px solid ${Tokens.LINE}", "border-radius" to "6px",
    ).toAttrs { attr("aria-busy", loading.toString()) }) {
        items.forEach { word ->
            Li(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "12px", "padding" to "7px 12px", "border-bottom" to "1px solid ${Tokens.LINE}").toAttrs()) {
                Span(Modifier.css("font-weight" to "650", "flex" to "1").toAttrs()) { Text(word.text) }
                Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Words.letters(word.graphemeCount)) }
                ActionButton(label, { onChoose(word) }, ButtonKind.QUIET, enabled = enabled(word), modifier = Modifier.css("height" to "30px", "padding" to "0 10px"))
            }
        }
    }
    if (total > items.size) {
        P(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Common.range(if (items.isEmpty()) 0L else 1L, items.size.toLong(), total)) }
    }
}

/** Adds catalog words to an `en`/`ru`/`kk` pool one at a time; the list refreshes after each. */
@Composable
fun CandidatesDialog(calendar: String, onDismiss: () -> Unit, onMarked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<Pair<String, NoticeTone>?>(null) }

    FormDialog("pool-candidates", "${Strings.AnswerPool.CANDIDATES_TITLE} · ${Strings.Calendar.calendar(calendar)}", onDismiss, busy = busy, wide = true) {
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "12px").toAttrs()) {
            CandidateList(
                calendar = calendar,
                idPrefix = "pool-candidates",
                searchLabel = Strings.AnswerPool.SEARCH,
                hint = Strings.AnswerPool.CANDIDATES_HINT,
                reload = reload,
                label = Strings.AnswerPool.MARK,
                enabled = { !busy },
                onChoose = { word ->
                    busy = true
                    message = null
                    scope.launch {
                        when (val result = AdminApp.api.markWords(calendar, MarkWordsRequest(wordIds = listOf(word.wordId)))) {
                            is ApiResult.Success -> {
                                val outcome = result.data.singleOrNull()?.outcome
                                message = if (outcome == MarkOutcome.MARKED) "«${word.text}»: ${Strings.AnswerPool.MARKED}" to NoticeTone.SUCCESS
                                else "«${word.text}»: ${outcome?.let(Strings.AnswerPool::outcome).orEmpty()}" to NoticeTone.INFO
                                reload++
                                onMarked()
                            }
                            is ApiResult.Error -> message = poolError(result) to NoticeTone.ERROR
                        }
                        busy = false
                    }
                },
            )
            message?.let { (text, tone) -> Notice(text, tone) }
            Div(Modifier.css("display" to "flex", "justify-content" to "flex-end").toAttrs()) {
                ActionButton(Strings.Common.BACK, onDismiss, ButtonKind.QUIET, enabled = !busy)
            }
        }
    }
    LaunchedEffect(Unit) { (document.getElementById("pool-candidates-search") as? HTMLElement)?.focus() }
}

/** Marks a pasted list of words for an `en`/`ru`/`kk` pool and shows every line's outcome, grouped. */
@Composable
fun PasteDialog(calendar: String, onDismiss: () -> Unit, onMarked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<MarkItemResultDto>?>(null) }
    val input = BulkInput.parse(text)

    fun submit() {
        if (busy || !input.canSubmit) return
        busy = true
        error = null
        scope.launch {
            when (val response = AdminApp.api.markWords(calendar, MarkWordsRequest(texts = input.lines))) {
                is ApiResult.Success -> {
                    results = response.data
                    if (response.data.any { it.outcome == MarkOutcome.MARKED }) onMarked()
                }
                is ApiResult.Error -> error = poolError(response)
            }
            busy = false
        }
    }

    FormDialog("pool-paste", "${Strings.AnswerPool.PASTE_TITLE} · ${Strings.Calendar.calendar(calendar)}", onDismiss, busy = busy, wide = true) {
        val done = results
        if (done != null) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px", "max-height" to "55vh", "overflow-y" to "auto").toAttrs {
                attr("role", "status")
            }) {
                groupMarkResults(done).forEach { group ->
                    val tone = when (group.outcome) {
                        MarkOutcome.MARKED -> TileTone.CORRECT
                        MarkOutcome.ALREADY_ELIGIBLE -> TileTone.OPEN
                        else -> TileTone.ABSENT
                    }
                    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "6px").toAttrs()) {
                        H3(Modifier.css("margin" to "0", "font-size" to "15px", "display" to "flex", "gap" to "8px", "align-items" to "center").toAttrs()) {
                            Badge(group.items.size.toString(), tone)
                            Text(Strings.AnswerPool.outcome(group.outcome))
                        }
                        P(Modifier.css("margin" to "0", "font-size" to "14px").toAttrs()) {
                            Text(group.items.joinToString(", ") { it.text ?: it.input })
                        }
                    }
                }
            }
            Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px").toAttrs()) {
                ActionButton(Strings.AnswerPool.PASTE_AGAIN, { results = null; text = "" }, ButtonKind.QUIET)
                ActionButton(Strings.Common.BACK, onDismiss, ButtonKind.PRIMARY)
            }
            return@FormDialog
        }
        Form(attrs = { onSubmit { it.preventDefault(); submit() } }) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                TextAreaField(
                    "pool-paste-lines",
                    Strings.AnswerPool.PASTE_LABEL,
                    text,
                    { text = it },
                    hint = Strings.AnswerPool.PASTE_HINT,
                    error = if (input.tooMany) Strings.Words.BULK_TOO_MANY else null,
                    rows = 12,
                    enabled = !busy,
                )
                P(MutedTextStyle.toModifier().toAttrs { attr("aria-live", "polite") }) { Text(Strings.Words.lineCount(input.count)) }
                Notice(error, NoticeTone.ERROR)
                Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px").toAttrs()) {
                    ActionButton(Strings.Common.CANCEL, onDismiss, ButtonKind.QUIET, enabled = !busy)
                    ActionButton(Strings.AnswerPool.PASTE_SUBMIT, {}, ButtonKind.PRIMARY, enabled = input.canSubmit && !busy, submit = true)
                }
            }
        }
    }
}

/**
 * A new Uzbek pair: choose a Latin catalog word, confirm or correct the Cyrillic spelling suggested by the shared
 * transliteration rules (checked at once against the Cyrillic alphabet), see whether that spelling is in the catalog,
 * and optionally add or restore it there as part of creating the pair. The server re-validates everything.
 */
@Composable
fun PairDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(PairForm()) }
    var status by remember { mutableStateOf<CyrlStatusDto?>(null) }
    var checking by remember { mutableStateOf(false) }
    var addToCatalog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val check = form.check

    LaunchedEffect(form.cyrillic) {
        status = null
        error = null
        if (check.cyrillicReason != null) {
            checking = false
            return@LaunchedEffect
        }
        checking = true
        delay(STATUS_DEBOUNCE_MILLIS)
        status = (AdminApp.api.cyrlStatus(form.cyrillic) as? ApiResult.Success)?.data
        checking = false
    }

    val current = status?.takeIf { it.normalized == check.normalized }
    val canCreate = check.isValid && !checking && !busy && current != null && current.pairedWith == null &&
        (current.cyrlStatus == CyrlStatus.ACTIVE || addToCatalog)

    fun submit() {
        val latin = form.latin ?: return
        if (!canCreate) return
        busy = true
        error = null
        scope.launch {
            val request = CreatePairRequest(latin.wordId, form.cyrillic, addCyrlToCatalog = current.cyrlStatus != CyrlStatus.ACTIVE && addToCatalog)
            when (val result = AdminApp.api.createPair(request)) {
                is ApiResult.Success -> onCreated()
                is ApiResult.Error -> error = poolError(result)
            }
            busy = false
        }
    }

    FormDialog("pool-pair", Strings.AnswerPool.PAIR_TITLE, onDismiss, busy = busy, wide = true) {
        Form(attrs = {
            onSubmit { it.preventDefault(); submit() }
            attr("novalidate", "")
        }) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                val chosen = form.latin
                if (chosen == null) {
                    CandidateList(
                        calendar = "uz",
                        idPrefix = "pool-pair-latin",
                        searchLabel = Strings.AnswerPool.PAIR_LATIN_SEARCH,
                        hint = Strings.AnswerPool.PAIR_LATIN_HINT,
                        reload = 0,
                        label = Strings.AnswerPool.PAIR_CHOOSE,
                        enabled = { !busy },
                        onChoose = { word -> form = form.withLatin(word); addToCatalog = false },
                    )
                } else {
                    Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
                        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.AnswerPool.PAIR_CHOSEN) }
                        Span(Modifier.css("font-weight" to "700", "font-size" to "17px").toAttrs()) { Text(chosen.text) }
                        ActionButton(Strings.Common.BACK, { form = PairForm(); addToCatalog = false }, ButtonKind.QUIET, enabled = !busy, modifier = Modifier.css("height" to "30px"))
                    }
                    TextField(
                        "pool-pair-cyrillic",
                        Strings.AnswerPool.PAIR_CYRILLIC,
                        form.cyrillic,
                        { form = form.withCyrillic(it) },
                        hint = if (PairForm.suggestion(chosen.text) == null) Strings.AnswerPool.PAIR_NO_SUGGESTION else Strings.AnswerPool.PAIR_CYRILLIC_HINT,
                        error = when {
                            check.notCyrillic -> Strings.AnswerPool.PAIR_NOT_CYRILLIC
                            form.cyrillic.isNotBlank() && check.cyrillicReason != null -> Strings.Words.reason(check.cyrillicReason)
                            else -> null
                        },
                        autoComplete = "off",
                        enabled = !busy,
                    )
                    if (form.edited && PairForm.suggestion(chosen.text) != null) {
                        Div { ActionButton(Strings.AnswerPool.PAIR_RESET, { form = form.resetToSuggestion() }, ButtonKind.QUIET, enabled = !busy, modifier = Modifier.css("height" to "30px")) }
                    }
                    when {
                        check.cyrillicReason != null -> {}
                        checking || current == null -> Notice(Strings.AnswerPool.PAIR_CHECKING, NoticeTone.INFO)
                        current.pairedWith != null -> Notice(Strings.AnswerPool.pairedWith(current.pairedWith!!), NoticeTone.ERROR)
                        current.cyrlStatus == CyrlStatus.ACTIVE -> Notice(Strings.AnswerPool.PAIR_ACTIVE, NoticeTone.SUCCESS)
                        else -> {
                            Notice(if (current.cyrlStatus == CyrlStatus.MISSING) Strings.AnswerPool.PAIR_MISSING else Strings.AnswerPool.PAIR_REMOVED, NoticeTone.INFO)
                            Label(forId = "pool-pair-add", attrs = Modifier.css("display" to "flex", "align-items" to "center", "gap" to "8px", "font-weight" to "600").toAttrs()) {
                                Input(InputType.Checkbox, attrs = Modifier.css("accent-color" to Tokens.CORRECT, "margin" to "0").toAttrs<InputAttrsScope<Boolean>> {
                                    id("pool-pair-add")
                                    checked(addToCatalog)
                                    if (busy) attr("disabled", "")
                                    onChange { addToCatalog = it.value }
                                })
                                Text(if (current.cyrlStatus == CyrlStatus.MISSING) Strings.AnswerPool.PAIR_ADD_TO_CATALOG else Strings.AnswerPool.PAIR_RESTORE_IN_CATALOG)
                            }
                        }
                    }
                }
                Notice(error, NoticeTone.ERROR)
                Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
                    ActionButton(Strings.Common.CANCEL, onDismiss, ButtonKind.QUIET, enabled = !busy)
                    ActionButton(Strings.AnswerPool.PAIR_CREATE, {}, ButtonKind.PRIMARY, enabled = canCreate, submit = true)
                }
            }
        }
    }
    LaunchedEffect(form.latin) {
        val id = if (form.latin == null) "pool-pair-latin-search" else "pool-pair-cyrillic"
        (document.getElementById(id) as? HTMLElement)?.focus()
    }
}
