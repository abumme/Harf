package uz.abumme.harfgame.admin.suggestions

import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionParams
import uz.abumme.harfgame.data.api.ApiResult

/** "Ожидают" (pending, oldest first) or "История" (decided, newest first). */
enum class SuggestionTab(val param: String, val apiStatus: String) {
    PENDING("pending", SuggestionParams.PENDING),
    HISTORY("history", SuggestionParams.DECIDED),
}

/** The `/suggestions` view, kept in the page's query parameters like the word list. */
data class SuggestionsQueryState(
    val lang: String,
    val tab: SuggestionTab = SuggestionTab.PENDING,
    val page: Int = 0,
) {
    fun withLanguage(lang: String) = if (lang == this.lang) this else copy(lang = lang, page = 0)
    fun withTab(tab: SuggestionTab) = if (tab == this.tab) this else copy(tab = tab, page = 0)
    fun withPage(page: Int) = copy(page = maxOf(0, page))

    fun toRouteQuery(): String = queryString(
        PARAM_LANG to lang,
        PARAM_TAB to tab.param.takeIf { tab != SuggestionTab.PENDING },
        PARAM_PAGE to (page + 1).toString().takeIf { page > 0 },
    )

    fun toApiQuery(size: Int): String = queryString(
        SuggestionParams.LANG to lang,
        SuggestionParams.STATUS to tab.apiStatus,
        SuggestionParams.PAGE to page.toString(),
        SuggestionParams.SIZE to size.toString(),
    )

    companion object {
        const val PARAM_LANG = "lang"
        const val PARAM_TAB = "tab"
        const val PARAM_PAGE = "page"

        /** The view a URL describes within the member's [languages]; null when the member has none. */
        fun fromParams(params: Map<String, String>, languages: List<String>): SuggestionsQueryState? {
            val lang = params[PARAM_LANG]?.takeIf { it in languages } ?: languages.firstOrNull() ?: return null
            return SuggestionsQueryState(
                lang = lang,
                tab = SuggestionTab.entries.firstOrNull { it.param == params[PARAM_TAB] } ?: SuggestionTab.PENDING,
                page = (params[PARAM_PAGE]?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1,
            )
        }
    }
}

/** What a decision in the panel came to, and so what the page shows next. */
sealed interface DecisionOutcome {
    /** The decision stands; the row leaves the pending list. */
    data class Applied(val suggestion: SuggestionDto) : DecisionOutcome

    /** Someone (Telegram, another staff member, verification) decided first: say so and refresh the list. */
    data object AlreadyDecided : DecisionOutcome

    /** The language is not (or no longer) the member's. */
    data object Forbidden : DecisionOutcome

    /** The word cannot enter the catalog ([reason] is a word reason); rejecting is still possible. */
    data class ValidationFailed(val reason: String) : DecisionOutcome

    /** Anything else: network, server, unexpected answer. */
    data class Failed(val error: ApiResult.Error) : DecisionOutcome

    /** Whether the pending list should be reloaded. */
    val refreshList: Boolean get() = this is Applied || this is AlreadyDecided
}

fun decisionOutcome(result: ApiResult<SuggestionDto>): DecisionOutcome = when (result) {
    is ApiResult.Success -> DecisionOutcome.Applied(result.data)
    is ApiResult.Error -> {
        val field = result.fieldError()
        when {
            // 409 `status: already_decided` is the only conflict a decision has.
            result.code == AdminErrors.CONFLICT -> DecisionOutcome.AlreadyDecided
            result.code == AdminErrors.FORBIDDEN -> DecisionOutcome.Forbidden
            result.code == AdminErrors.VALIDATION_FAILED && field != null -> DecisionOutcome.ValidationFailed(field.reason)
            else -> DecisionOutcome.Failed(result)
        }
    }
}
