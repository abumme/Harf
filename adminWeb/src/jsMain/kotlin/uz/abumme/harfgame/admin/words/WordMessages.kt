package uz.abumme.harfgame.admin.words

import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.lang.LaunchLanguages

/** The message for a refused word request: the word reason it names (e.g. `text: removed_exists`), else a general one. */
fun wordErrorMessage(error: ApiResult.Error, lang: String?): String {
    val reason = error.fieldError()?.reason ?: return generalMessage(error)
    val config = lang?.let { LaunchLanguages.all[it] }
    return if (config != null) Strings.Words.reason(reason, config.minLength, config.maxLength) else Strings.Words.reason(reason)
}

/** What the add dialog says about a typed word: the local rule it breaks, or the server's verdict once known. */
fun previewMessage(preview: WordPreview?, check: CheckWordResult?, checking: Boolean): String? = when {
    preview == null -> null
    preview.normalized.isEmpty() -> null
    preview.reason != null -> Strings.Words.reason(preview.reason, preview.minLength, preview.maxLength)
    checking || check == null -> Strings.Words.CHECKING
    check.normalized != preview.normalized -> Strings.Words.CHECKING
    else -> when (check.outcome) {
        WordCheckOutcome.VALID -> Strings.Words.CHECK_VALID
        WordCheckOutcome.RESTORABLE -> Strings.Words.CHECK_RESTORABLE
        WordCheckOutcome.DUPLICATE -> Strings.Words.CHECK_DUPLICATE
        WordCheckOutcome.INVALID, WordCheckOutcome.BLOCKLISTED ->
            Strings.Words.reason(check.reason.orEmpty(), preview.minLength, preview.maxLength)
    }
}

/** Whether the add dialog may submit: the word passes the local rules and the server has not already refused it. */
fun canAdd(preview: WordPreview?, check: CheckWordResult?): Boolean =
    preview != null && preview.isValid &&
        (check == null || check.normalized != preview.normalized ||
            check.outcome == WordCheckOutcome.VALID || check.outcome == WordCheckOutcome.RESTORABLE)
