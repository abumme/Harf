package uz.abumme.harfgame.data.admin.suggestions

import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/** Who decided a suggestion: a staff member (panel, or Telegram when linked), a legacy Telegram editor, or verification. */
@Serializable
enum class DeciderKind { STAFF, TELEGRAM, AUTO }

@Serializable
data class DeciderDto(
    val kind: DeciderKind,
    val name: String,
)

/**
 * A player's word suggestion as staff review it. [author] is the display label ("Аноним" without a name). [reason] is
 * why it needs a human, one of [ReviewReasons]; null while it is still queued for verification or when it was
 * reviewed before reasons were recorded. Times are epoch milliseconds (UTC).
 */
@Serializable
data class SuggestionDto(
    val id: String,
    val lang: String,
    val word: String,
    val author: String,
    val createdAt: Long,
    val reason: String? = null,
    val status: SuggestionStatus,
    val decidedBy: DeciderDto? = null,
    val decidedAt: Long? = null,
)

/** A page of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.SUGGESTIONS]. */
typealias SuggestionPageDto = PageDto<SuggestionDto>

@Serializable
data class DecisionRequest(
    val accept: Boolean,
)

/**
 * Query parameters of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.SUGGESTIONS]: [STATUS] is [PENDING] (oldest
 * first, the default) or [DECIDED] (newest first); without [LANG] every language the caller may review is listed.
 */
object SuggestionParams {
    const val LANG = "lang"
    const val STATUS = "status"
    const val PAGE = "page"
    const val SIZE = "size"

    const val PENDING = "PENDING"
    const val DECIDED = "DECIDED"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200
}

/** Why a suggestion went to editors ([SuggestionDto.reason]). */
object ReviewReasons {
    const val NOT_FOUND = "NOT_FOUND"
    const val PROPER_NOUN = "PROPER_NOUN"
    const val ABBREVIATION = "ABBREVIATION"
    const val MISSPELLING = "MISSPELLING"
    const val VULGAR = "VULGAR"
    const val DISABLED = "DISABLED"
    const val UNVERIFIED = "UNVERIFIED"
    const val REMOVED_BY_STAFF = "REMOVED_BY_STAFF"

    /** Stored before suggestions had to be playable, and failing the language rules: it can only be rejected. */
    const val NOT_PLAYABLE = "NOT_PLAYABLE"
}

/** Reasons in suggestion decision errors (`field: reason`, field `status`). */
object SuggestionReasons {
    /** 409: the suggestion is no longer pending. */
    const val ALREADY_DECIDED = "already_decided"
}
