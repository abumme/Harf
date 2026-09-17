package uz.abumme.harfgame.admin.words

import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.components.endOfLocalDay
import uz.abumme.harfgame.admin.components.startOfLocalDay
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.BulkLineResult
import uz.abumme.harfgame.data.admin.words.WordParams
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.lang.LanguageConfig
import uz.abumme.harfgame.lang.LaunchLanguages

/** Which words the list shows: active, removed ("Удалённые") or both. */
enum class StatusFilter(val param: String) {
    ACTIVE(WordStatus.ACTIVE.name),
    REMOVED(WordStatus.REMOVED.name),
    ALL(WordParams.STATUS_ALL),
}

enum class WordSort(val param: String) {
    TEXT(WordParams.SORT_TEXT),
    CREATED(WordParams.SORT_CREATED),
    UPDATED(WordParams.SORT_UPDATED),
}

/**
 * The `/words` view: language, filters, sort and page. It lives in the page's query parameters, so a reload or a shared
 * link restores the view. Every filter or sort change starts again from the first page.
 */
data class WordsQueryState(
    val lang: String,
    val q: String = "",
    val status: StatusFilter = StatusFilter.ACTIVE,
    /** A [WordSource] name, or empty for any. */
    val source: String = "",
    /** A staff id, or empty for anyone. */
    val addedBy: String = "",
    /** `yyyy-mm-dd` from a date input, or empty. */
    val addedFrom: String = "",
    val addedTo: String = "",
    val sort: WordSort = WordSort.TEXT,
    val descending: Boolean = false,
    val page: Int = 0,
) {
    fun withLanguage(lang: String) = if (lang == this.lang) this else WordsQueryState(lang = lang)
    fun withSearch(q: String) = if (q == this.q) this else copy(q = q, page = 0)
    fun withStatus(status: StatusFilter) = copy(status = status, page = 0)
    fun withSource(source: String) = copy(source = source, page = 0)
    fun withAddedBy(addedBy: String) = copy(addedBy = addedBy, page = 0)
    fun withAddedFrom(date: String) = copy(addedFrom = date, page = 0)
    fun withAddedTo(date: String) = copy(addedTo = date, page = 0)
    fun withPage(page: Int) = copy(page = maxOf(0, page))

    /** Clears every filter but keeps the language. */
    fun withoutFilters() = WordsQueryState(lang = lang)

    val hasFilters: Boolean get() = this != WordsQueryState(lang = lang, sort = sort, descending = descending, page = page)

    /** The same column flips the direction; a new column starts alphabetically for text and newest first for dates. */
    fun withSort(column: WordSort): WordsQueryState =
        if (column == sort) copy(descending = !descending, page = 0)
        else copy(sort = column, descending = column != WordSort.TEXT, page = 0)

    /** The panel URL's query (`?lang=…&…`), holding only what differs from the defaults. */
    fun toRouteQuery(): String = queryString(
        PARAM_LANG to lang,
        PARAM_Q to q.ifEmpty { null },
        PARAM_STATUS to status.param.takeIf { status != StatusFilter.ACTIVE },
        PARAM_SOURCE to source.ifEmpty { null },
        PARAM_ADDED_BY to addedBy.ifEmpty { null },
        PARAM_FROM to addedFrom.ifEmpty { null },
        PARAM_TO to addedTo.ifEmpty { null },
        PARAM_SORT to sort.param.takeIf { sort != WordSort.TEXT },
        PARAM_DIR to WordParams.DIR_DESC.takeIf { descending },
        PARAM_PAGE to (page + 1).toString().takeIf { page > 0 },
    )

    /** The admin API query of `GET /words` for this view; dates become the viewer's local day bounds. */
    fun toApiQuery(size: Int): String = queryString(
        WordParams.LANG to lang,
        WordParams.Q to q.trim().ifEmpty { null },
        WordParams.STATUS to status.param,
        WordParams.SOURCE to source.ifEmpty { null },
        WordParams.ADDED_BY to addedBy.ifEmpty { null },
        WordParams.ADDED_FROM to startOfLocalDay(addedFrom)?.toString(),
        WordParams.ADDED_TO to endOfLocalDay(addedTo)?.toString(),
        WordParams.SORT to sort.param,
        WordParams.DIR to if (descending) WordParams.DIR_DESC else WordParams.DIR_ASC,
        WordParams.PAGE to page.toString(),
        WordParams.SIZE to size.toString(),
    )

    companion object {
        const val PARAM_LANG = "lang"
        const val PARAM_Q = "q"
        const val PARAM_STATUS = "status"
        const val PARAM_SOURCE = "source"
        const val PARAM_ADDED_BY = "addedBy"
        const val PARAM_FROM = "from"
        const val PARAM_TO = "to"
        const val PARAM_SORT = "sort"
        const val PARAM_DIR = "dir"

        /** 1-based in the URL, like the page number the table shows. */
        const val PARAM_PAGE = "page"

        /**
         * The view a URL describes, limited to the member's [languages]: an unknown language falls back to the first
         * one, and malformed values to their defaults. Null when the member has no language at all.
         */
        fun fromParams(params: Map<String, String>, languages: List<String>): WordsQueryState? {
            val lang = params[PARAM_LANG]?.takeIf { it in languages } ?: languages.firstOrNull() ?: return null
            fun date(name: String) = params[name]?.takeIf { DATE.matches(it) }.orEmpty()
            return WordsQueryState(
                lang = lang,
                q = params[PARAM_Q].orEmpty(),
                status = StatusFilter.entries.firstOrNull { it.param == params[PARAM_STATUS] } ?: StatusFilter.ACTIVE,
                source = params[PARAM_SOURCE]?.takeIf { value -> WordSource.entries.any { it.name == value } }.orEmpty(),
                addedBy = params[PARAM_ADDED_BY].orEmpty(),
                addedFrom = date(PARAM_FROM),
                addedTo = date(PARAM_TO),
                sort = WordSort.entries.firstOrNull { it.param == params[PARAM_SORT] } ?: WordSort.TEXT,
                descending = params[PARAM_DIR] == WordParams.DIR_DESC,
                page = (params[PARAM_PAGE]?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1,
            )
        }

        private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    }
}

/** A pasted word list: its non-blank lines, trimmed, and whether it is over the limit of one request. */
data class BulkInput(val lines: List<String>) {
    val count: Int get() = lines.size
    val tooMany: Boolean get() = count > WordRules.MAX_BULK_LINES
    val canSubmit: Boolean get() = count in 1..WordRules.MAX_BULK_LINES

    companion object {
        fun parse(text: String): BulkInput = BulkInput(text.lines().map { it.trim() }.filter { it.isNotEmpty() })
    }
}

/** One outcome's lines of a bulk result, in the order they were pasted. */
data class BulkGroup(val outcome: BulkLineOutcome, val items: List<BulkGroupItem>) {
    val count: Int get() = items.size
}

/** [text] is the normalized word, or the pasted line when the server could not normalize it. */
data class BulkGroupItem(val line: Int, val text: String, val reason: String?)

/** Groups a bulk result by outcome (added, restored, duplicate, invalid, blocklisted), leaving out empty groups. */
fun groupBulkResults(results: List<BulkLineResult>, submitted: List<String>): List<BulkGroup> =
    BulkLineOutcome.entries.mapNotNull { outcome ->
        results.filter { it.outcome == outcome }
            .map { BulkGroupItem(it.line, it.text ?: submitted.getOrNull(it.line - 1).orEmpty(), it.reason) }
            .takeIf { it.isNotEmpty() }
            ?.let { BulkGroup(outcome, it) }
    }

/**
 * What the panel can tell about a typed word before asking the server, with the app's own rules: its normalized
 * form, letter count, whether it tokenizes and whether its length fits the board. [reason] is the first rule it breaks.
 */
data class WordPreview(
    val normalized: String,
    val graphemeCount: Int?,
    val tokenizes: Boolean,
    val lengthOk: Boolean,
    val reason: String?,
    val minLength: Int,
    val maxLength: Int,
) {
    val isValid: Boolean get() = reason == null

    companion object {
        /** Null for a language the panel has no rules for (the server still checks). */
        fun of(lang: String, raw: String, configs: Map<String, LanguageConfig> = LaunchLanguages.all): WordPreview? {
            val config = configs[lang] ?: return null
            val check = WordRules.check(config, raw)
            val graphemes = check.graphemes
            return WordPreview(
                normalized = check.normalized,
                graphemeCount = graphemes?.size,
                tokenizes = graphemes != null,
                lengthOk = graphemes != null && graphemes.size in config.minLength..config.maxLength,
                reason = check.reason,
                minLength = config.minLength,
                maxLength = config.maxLength,
            )
        }
    }
}
