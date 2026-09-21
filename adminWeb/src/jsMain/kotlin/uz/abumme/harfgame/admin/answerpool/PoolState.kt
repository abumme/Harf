package uz.abumme.harfgame.admin.answerpool

import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolParams
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.lang.LaunchLanguages
import uz.abumme.harfgame.lang.UzbekTransliteration

/** The `/answer-pool` view: calendar, search and page, kept in the page's query parameters. */
data class PoolQueryState(val calendar: String, val q: String = "", val page: Int = 0) {
    fun withCalendar(calendar: String) = if (calendar == this.calendar) this else PoolQueryState(calendar)
    fun withSearch(q: String) = if (q == this.q) this else copy(q = q, page = 0)
    fun withPage(page: Int) = copy(page = maxOf(0, page))

    fun toRouteQuery(): String = queryString(
        PARAM_CALENDAR to calendar,
        PARAM_Q to q.ifEmpty { null },
        PARAM_PAGE to (page + 1).toString().takeIf { page > 0 },
    )

    fun toApiQuery(size: Int): String = queryString(
        PoolParams.Q to q.trim().ifEmpty { null },
        PoolParams.PAGE to page.toString(),
        PoolParams.SIZE to size.toString(),
    )

    companion object {
        const val PARAM_CALENDAR = "calendar"
        const val PARAM_Q = "q"
        const val PARAM_PAGE = "page"

        fun fromParams(params: Map<String, String>): PoolQueryState = PoolQueryState(
            calendar = params[PARAM_CALENDAR]?.takeIf(DailyCalendars::isCalendar) ?: DailyCalendars.ALL.first(),
            q = params[PARAM_Q].orEmpty(),
            page = (params[PARAM_PAGE]?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1,
        )
    }
}

/** One outcome's entries of a mark request, in the order they were sent. */
data class MarkGroup(val outcome: MarkOutcome, val items: List<MarkItemResultDto>)

/** Groups mark results by outcome (marked first), leaving out empty groups. */
fun groupMarkResults(results: List<MarkItemResultDto>): List<MarkGroup> =
    MarkOutcome.entries.mapNotNull { outcome ->
        results.filter { it.outcome == outcome }.takeIf { it.isNotEmpty() }?.let { MarkGroup(outcome, it) }
    }

/**
 * The new Uzbek pair form: the chosen Latin word and the Cyrillic spelling, which starts as the rule-based suggestion
 * and may be corrected. The server re-validates the confirmed spelling through the catalog's rules.
 */
data class PairForm(
    val latin: PoolCandidateDto? = null,
    val cyrillic: String = "",
    /** The ADMIN changed the suggested spelling. */
    val edited: Boolean = false,
) {
    /** Chooses the Latin word; the Cyrillic field starts again from that word's suggestion. */
    fun withLatin(word: PoolCandidateDto): PairForm = PairForm(word, suggestion(word.text).orEmpty(), edited = false)

    fun withCyrillic(text: String): PairForm = copy(cyrillic = text, edited = true)

    /** Back to the suggestion for the chosen word. */
    fun resetToSuggestion(): PairForm = copy(cyrillic = latin?.let { suggestion(it.text) }.orEmpty(), edited = false)

    val check: PairFormCheck get() {
        val rules = WordRules.check(LaunchLanguages.uzCyrl, cyrillic)
        return PairFormCheck(
            normalized = rules.normalized,
            latinMissing = latin == null,
            cyrillicReason = rules.reason,
            graphemeCount = rules.graphemes?.size,
        )
    }

    companion object {
        /** The rule-based Cyrillic spelling of a Latin word, or null when the rules cannot spell it. */
        fun suggestion(latin: String): String? = UzbekTransliteration.transliterateOrNull(latin)
    }
}

/** What the pair form can tell before asking the server. [cyrillicReason] is a `WordReasons` value. */
data class PairFormCheck(
    val normalized: String,
    val latinMissing: Boolean,
    val cyrillicReason: String?,
    val graphemeCount: Int?,
) {
    val isValid: Boolean get() = !latinMissing && cyrillicReason == null

    /** A Cyrillic spelling that uses letters outside the Uzbek Cyrillic alphabet. */
    val notCyrillic: Boolean get() = cyrillicReason == WordReasons.NOT_TOKENIZABLE
}
