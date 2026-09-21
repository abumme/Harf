package uz.abumme.harfgame.backend.admin.words

import io.ktor.http.Parameters
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.words.WordParams
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import java.time.Instant

/** Filters of one word-list query. A null [status] lists both statuses; [addedTo] is exclusive; [page] is zero-based. */
data class WordQuery(
    val lang: String,
    val q: String? = null,
    val status: WordStatus? = WordStatus.ACTIVE,
    val source: WordSource? = null,
    val addedBy: String? = null,
    val addedFrom: Instant? = null,
    val addedTo: Instant? = null,
    val sort: Sort = Sort.TEXT,
    val descending: Boolean = false,
    val page: Int = 0,
    val size: Int = WordParams.DEFAULT_SIZE,
) {
    enum class Sort { TEXT, CREATED, UPDATED }

    companion object {
        /** Parses [WordParams] query parameters; a missing language or a malformed value answers 422 naming the parameter. */
        fun parse(parameters: Parameters): WordQuery {
            fun text(name: String) = parameters[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun invalid(name: String): Nothing = throw AdminApiException.validation(name, FieldReasons.INVALID)
            fun long(name: String) = text(name)?.let { it.toLongOrNull() ?: invalid(name) }

            val lang = text(WordParams.LANG) ?: throw AdminApiException.validation(WordParams.LANG, FieldReasons.REQUIRED)
            val status = when (val raw = text(WordParams.STATUS)) {
                null -> WordStatus.ACTIVE
                WordParams.STATUS_ALL -> null
                else -> WordStatus.entries.firstOrNull { it.name == raw } ?: invalid(WordParams.STATUS)
            }
            val source = text(WordParams.SOURCE)?.let { raw -> WordSource.entries.firstOrNull { it.name == raw } ?: invalid(WordParams.SOURCE) }
            val sort = when (text(WordParams.SORT)) {
                null, WordParams.SORT_TEXT -> Sort.TEXT
                WordParams.SORT_CREATED -> Sort.CREATED
                WordParams.SORT_UPDATED -> Sort.UPDATED
                else -> invalid(WordParams.SORT)
            }
            val descending = when (text(WordParams.DIR)) {
                null, WordParams.DIR_ASC -> false
                WordParams.DIR_DESC -> true
                else -> invalid(WordParams.DIR)
            }
            val page = long(WordParams.PAGE) ?: 0
            val size = long(WordParams.SIZE) ?: WordParams.DEFAULT_SIZE.toLong()
            if (page < 0 || page > Int.MAX_VALUE) invalid(WordParams.PAGE)
            if (size !in 1..WordParams.MAX_SIZE) invalid(WordParams.SIZE)
            return WordQuery(
                lang = lang,
                q = text(WordParams.Q),
                status = status,
                source = source,
                addedBy = text(WordParams.ADDED_BY),
                addedFrom = long(WordParams.ADDED_FROM)?.let(Instant::ofEpochMilli),
                addedTo = long(WordParams.ADDED_TO)?.let(Instant::ofEpochMilli),
                sort = sort,
                descending = descending,
                page = page.toInt(),
                size = size.toInt(),
            )
        }
    }
}
