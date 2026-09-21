package uz.abumme.harfgame.admin.pages.words

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
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLElement
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.FieldHintStyle
import uz.abumme.harfgame.admin.components.FormDialog
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.TextAreaField
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.words.BulkGroup
import uz.abumme.harfgame.admin.words.BulkInput
import uz.abumme.harfgame.admin.words.WordPreview
import uz.abumme.harfgame.admin.words.canAdd
import uz.abumme.harfgame.admin.words.groupBulkResults
import uz.abumme.harfgame.admin.words.previewMessage
import uz.abumme.harfgame.admin.words.wordErrorMessage
import uz.abumme.harfgame.data.admin.words.BulkAddResult
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.api.ApiResult

private const val CHECK_DEBOUNCE_MILLIS = 350L

/** The dialog footer: cancel and the primary action, right-aligned. */
@Composable
private fun DialogActions(cancelLabel: String, onCancel: () -> Unit, confirmLabel: String?, enabled: Boolean, busy: Boolean, id: String) {
    Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
        ActionButton(cancelLabel, onCancel, ButtonKind.QUIET, enabled = !busy, id = "$id-cancel")
        if (confirmLabel != null) ActionButton(confirmLabel, {}, ButtonKind.PRIMARY, enabled = enabled && !busy, submit = true, id = "$id-submit")
    }
}

/** What the panel can tell about a word before saving: its normalized form, letter count and the rules it meets. */
@Composable
private fun PreviewFacts(preview: WordPreview) {
    Div(Modifier.css("display" to "grid", "grid-template-columns" to "auto 1fr", "gap" to "4px 12px", "margin" to "0", "font-size" to "14px").toAttrs {
        attr("aria-live", "polite")
    }) {
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.NORMALIZED) }
        Span(Modifier.css("margin" to "0", "font-weight" to "650").toAttrs()) { Text(preview.normalized.ifEmpty { Strings.Common.NOT_SET }) }
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.LETTERS) }
        Span(Modifier.css("margin" to "0").toAttrs()) {
            Text("${preview.graphemeCount ?: Strings.Common.NOT_SET} (${Strings.Words.lengthRange(preview.minLength, preview.maxLength)})")
        }
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Words.TOKENIZES) }
        Span(Modifier.css("margin" to "0").toAttrs()) { Text(if (preview.tokenizes) Strings.Words.YES else Strings.Words.NO) }
    }
}

/**
 * Adds one word. While typing, the normalized form and the language rules come from the shared engine at once; the
 * blocklist and the catalog state come from the server's side-effect-free check, debounced. The server re-validates on
 * save.
 */
@Composable
fun AddWordDialog(lang: String, onDismiss: () -> Unit, onAdded: (WordDto) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<CheckWordResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val preview = WordPreview.of(lang, text)

    LaunchedEffect(text) {
        error = null
        if (preview == null || !preview.isValid) {
            checking = false
            return@LaunchedEffect
        }
        checking = true
        delay(CHECK_DEBOUNCE_MILLIS)
        check = (AdminApp.api.checkWord(lang, text) as? ApiResult.Success)?.data
        checking = false
    }

    fun submit() {
        if (busy || !canAdd(preview, check)) return
        busy = true
        scope.launch {
            when (val result = AdminApp.api.addWord(lang, text)) {
                is ApiResult.Success -> onAdded(result.data)
                is ApiResult.Error -> error = wordErrorMessage(result, lang)
            }
            busy = false
        }
    }

    FormDialog("add-word", "${Strings.Words.ADD_TITLE} · ${Strings.Languages.label(lang)}", onDismiss, busy = busy) {
        Form(attrs = {
            onSubmit { it.preventDefault(); submit() }
            attr("novalidate", "")
        }) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                TextField("add-word-text", Strings.Words.WORD, text, { text = it }, autoComplete = "off")
                if (preview != null && text.isNotBlank()) {
                    PreviewFacts(preview)
                    val message = previewMessage(preview, check, checking)
                    val ok = preview.isValid && !checking && check?.normalized == preview.normalized &&
                        (check?.outcome == WordCheckOutcome.VALID || check?.outcome == WordCheckOutcome.RESTORABLE)
                    Notice(message, if (ok) NoticeTone.SUCCESS else if (preview.isValid && (checking || check == null)) NoticeTone.INFO else NoticeTone.ERROR)
                }
                Notice(error, NoticeTone.ERROR)
                DialogActions(Strings.Common.CANCEL, onDismiss, Strings.Words.SUBMIT_ADD, canAdd(preview, check) && !checking, busy, "add-word")
            }
        }
    }
    LaunchedEffect(Unit) { (document.getElementById("add-word-text") as? HTMLElement)?.focus() }
}

/** Pastes up to 1,000 words; shows a live line counter, then every line's outcome grouped by kind. */
@Composable
fun BulkAddDialog(lang: String, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<BulkAddResult, List<String>>?>(null) }
    val input = BulkInput.parse(text)

    fun submit() {
        if (busy || !input.canSubmit) return
        busy = true
        error = null
        scope.launch {
            when (val response = AdminApp.api.addWords(lang, input.lines)) {
                is ApiResult.Success -> {
                    result = response.data to input.lines
                    if (response.data.results.any { it.outcome == BulkLineOutcome.ADDED || it.outcome == BulkLineOutcome.RESTORED }) onChanged()
                }
                is ApiResult.Error -> error = wordErrorMessage(response, lang)
            }
            busy = false
        }
    }

    FormDialog("bulk-add", "${Strings.Words.BULK_TITLE} · ${Strings.Languages.label(lang)}", onDismiss, busy = busy, wide = true) {
        val done = result
        if (done != null) {
            BulkResults(groupBulkResults(done.first.results, done.second))
            Div(Modifier.css("display" to "flex", "justify-content" to "flex-end", "gap" to "10px").toAttrs()) {
                ActionButton(Strings.Words.BULK_AGAIN, { result = null; text = "" }, ButtonKind.QUIET)
                ActionButton(Strings.Common.BACK, onDismiss, ButtonKind.PRIMARY, id = "bulk-add-close")
            }
            return@FormDialog
        }
        Form(attrs = { onSubmit { it.preventDefault(); submit() } }) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                TextAreaField(
                    "bulk-add-lines",
                    Strings.Words.BULK_LABEL,
                    text,
                    { text = it },
                    hint = Strings.Words.BULK_HINT,
                    error = if (input.tooMany) Strings.Words.BULK_TOO_MANY else null,
                    rows = 12,
                    enabled = !busy,
                )
                P(MutedTextStyle.toModifier().toAttrs { attr("aria-live", "polite") }) { Text(Strings.Words.lineCount(input.count)) }
                Notice(error, NoticeTone.ERROR)
                DialogActions(Strings.Common.CANCEL, onDismiss, Strings.Words.BULK_SUBMIT, input.canSubmit, busy, "bulk-add")
            }
        }
    }
}

@Composable
private fun BulkResults(groups: List<BulkGroup>) {
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px", "max-height" to "55vh", "overflow-y" to "auto").toAttrs {
        attr("role", "status")
    }) {
        groups.forEach { group ->
            val tone = when (group.outcome) {
                BulkLineOutcome.ADDED, BulkLineOutcome.RESTORED -> TileTone.CORRECT
                BulkLineOutcome.DUPLICATE -> TileTone.OPEN
                BulkLineOutcome.INVALID, BulkLineOutcome.BLOCKLISTED -> TileTone.ABSENT
            }
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "6px").toAttrs()) {
                H3(Modifier.css("margin" to "0", "font-size" to "15px", "display" to "flex", "gap" to "8px", "align-items" to "center").toAttrs()) {
                    Badge(group.count.toString(), tone)
                    Text(Strings.Words.bulkOutcome(group.outcome))
                }
                Ul(Modifier.css("margin" to "0", "padding-left" to "18px", "font-size" to "14px").toAttrs()) {
                    group.items.forEach { item ->
                        Li {
                            Span(Modifier.css("font-weight" to "600").toAttrs()) { Text(item.text) }
                            item.reason?.let { reason ->
                                Span(MutedTextStyle.toModifier().toAttrs()) { Text(" — ${Strings.Words.reason(reason)}") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Respells an active word; the previous spelling stays removed. A 409 explains a duplicate or a removed spelling. */
@Composable
fun EditWordDialog(word: WordDto, onDismiss: () -> Unit, onSaved: (WordDto) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(word.text) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val preview = WordPreview.of(word.lang, text)

    fun submit() {
        if (busy || preview?.isValid == false) return
        busy = true
        error = null
        scope.launch {
            when (val result = AdminApp.api.editWord(word.id, text)) {
                is ApiResult.Success -> onSaved(result.data)
                is ApiResult.Error -> error = wordErrorMessage(result, word.lang)
            }
            busy = false
        }
    }

    FormDialog("edit-word", "${Strings.Words.EDIT_TITLE} · ${word.text}", onDismiss, busy = busy) {
        Form(attrs = { onSubmit { it.preventDefault(); submit() } }) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                TextField("edit-word-text", Strings.Words.WORD, text, { text = it; error = null }, autoComplete = "off")
                if (preview != null) {
                    PreviewFacts(preview)
                    preview.reason?.let { Notice(Strings.Words.reason(it, preview.minLength, preview.maxLength), NoticeTone.ERROR) }
                }
                P(Modifier.toAttrs()) {
                    Span(FieldHintStyle.toModifier().css("color" to Tokens.MUTED).toAttrs()) { Text(Strings.Words.EDIT_NOTE) }
                }
                Notice(error, NoticeTone.ERROR)
                DialogActions(Strings.Common.CANCEL, onDismiss, Strings.Words.SUBMIT_EDIT, preview?.isValid != false, busy, "edit-word")
            }
        }
    }
    LaunchedEffect(Unit) { (document.getElementById("edit-word-text") as? HTMLElement)?.focus() }
}
