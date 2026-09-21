package uz.abumme.harfgame.data.admin.words

import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.PageDto

/** A catalog word is ACTIVE (served to players) or REMOVED (a tombstone that keeps the spelling from coming back). */
@Serializable
enum class WordStatus { ACTIVE, REMOVED }

/** Where a catalog word came from: a bundled dictionary, an editor-accepted or automatically accepted suggestion, or staff. */
@Serializable
enum class WordSource { BUNDLED, SUGGESTION, AUTO, STAFF }

/** A staff member named in a word's provenance. */
@Serializable
data class StaffRefDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
)

/**
 * One catalog word, for every role. It deliberately carries nothing about daily words (eligibility, schedule, usage).
 * [text] is the normalized form; [graphemeCount] is null for a legacy word that no longer tokenizes. Times are epoch
 * milliseconds (UTC); a null [createdBy] means the word predates staff tracking or came from a suggestion.
 * [restored] is set only in the response to an add that restored a removed word.
 */
@Serializable
data class WordDto(
    val id: String,
    val lang: String,
    val text: String,
    val graphemeCount: Int? = null,
    val status: WordStatus,
    val source: WordSource,
    val suggestionId: String? = null,
    val createdBy: StaffRefDto? = null,
    val createdAt: Long,
    val updatedBy: StaffRefDto? = null,
    val updatedAt: Long,
    val removedBy: StaffRefDto? = null,
    val removedAt: Long? = null,
    val restored: Boolean = false,
)

/** A page of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.WORDS]. */
typealias WordPageDto = PageDto<WordDto>

@Serializable
data class CheckWordRequest(
    val lang: String,
    val text: String,
)

/** What adding a word would do, without doing it. */
@Serializable
enum class WordCheckOutcome {
    /** A new word: adding it creates it. */
    VALID,

    /** The spelling belongs to a removed word: adding it restores that word. */
    RESTORABLE,

    /** An active word already has this spelling. */
    DUPLICATE,

    /** Fails the language rules; see the reason. */
    INVALID,

    /** On the language's blocklist. */
    BLOCKLISTED,
}

/** [reason] is one of [WordReasons] for [WordCheckOutcome.INVALID] and [WordCheckOutcome.BLOCKLISTED]. */
@Serializable
data class CheckWordResult(
    val normalized: String,
    val graphemeCount: Int? = null,
    val outcome: WordCheckOutcome,
    val reason: String? = null,
)

@Serializable
data class AddWordRequest(
    val lang: String,
    val text: String,
)

/** Up to [WordRules.MAX_BULK_LINES] lines, one word each; blank lines are skipped. */
@Serializable
data class BulkAddRequest(
    val lang: String,
    val lines: List<String>,
)

@Serializable
enum class BulkLineOutcome { ADDED, RESTORED, DUPLICATE, INVALID, BLOCKLISTED }

/** The outcome of one non-blank line; [line] is its 1-based position in the request, [text] its normalized form. */
@Serializable
data class BulkLineResult(
    val line: Int,
    val text: String? = null,
    val outcome: BulkLineOutcome,
    val reason: String? = null,
)

/** Per-line outcomes and the language's pack version after the request (unchanged when nothing was added). */
@Serializable
data class BulkAddResult(
    val results: List<BulkLineResult>,
    val packVersion: String,
)

@Serializable
data class EditWordRequest(
    val text: String,
)

/**
 * Query parameters of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.WORDS]. [ADDED_BY] is a staff id,
 * [ADDED_FROM] and [ADDED_TO] are epoch milliseconds ([ADDED_TO] exclusive), [PAGE] is zero-based.
 */
object WordParams {
    const val LANG = "lang"
    const val Q = "q"
    const val STATUS = "status"
    const val SOURCE = "source"
    const val ADDED_BY = "addedBy"
    const val ADDED_FROM = "addedFrom"
    const val ADDED_TO = "addedTo"
    const val SORT = "sort"
    const val DIR = "dir"
    const val PAGE = "page"
    const val SIZE = "size"

    /** [STATUS] value listing both active and removed words; otherwise a [WordStatus] name (default ACTIVE). */
    const val STATUS_ALL = "ALL"

    const val SORT_TEXT = "text"
    const val SORT_CREATED = "created"
    const val SORT_UPDATED = "updated"
    const val DIR_ASC = "asc"
    const val DIR_DESC = "desc"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200
}

/**
 * Reasons in word validation and conflict errors (`field: reason` in `ApiErrorResponse.message`, field `text`, `lines`
 * or `pack`) and in [CheckWordResult]/[BulkLineResult].
 */
object WordReasons {
    const val UNSUPPORTED_LANGUAGE = "unsupported_language"
    const val EMPTY = "empty"
    const val NOT_TOKENIZABLE = "not_tokenizable"
    const val BAD_LENGTH = "bad_length"
    const val BLOCKLISTED = "blocklisted"

    /** An active word already has this spelling. */
    const val DUPLICATE = "duplicate"

    /** A removed word has this spelling: restore it instead. */
    const val REMOVED_EXISTS = "removed_exists"

    /** Only an active word can be respelled. */
    const val NOT_ACTIVE = "not_active"

    /** The resulting word list would be refused by the app; the change was not made. */
    const val PACK_INTEGRITY = "pack_integrity"

    /** A bulk add has more than [WordRules.MAX_BULK_LINES] lines. */
    const val TOO_MANY_LINES = "too_many_lines"
}
