package uz.abumme.harfgame.data.suggestion

import kotlinx.serialization.Serializable

/** A player's proposal to add [word] (raw, as typed) to [lang]'s dictionary. */
@Serializable
data class SuggestWordRequest(
    val lang: String,
    val word: String,
)

/** Outcome of a suggestion. [status] is one of the [SuggestionStatus] names. */
@Serializable
data class SuggestWordResponse(
    val status: String,
)

/** `ApiErrorResponse.error` codes of the suggestion endpoint besides `rejected` and `rate_limited`. */
object SuggestionErrors {
    /** 403: an ADMIN blocked the account's suggestions; nothing was stored. */
    const val BLOCKED = "suggestions_blocked"
}

/** Server-side lifecycle of a suggestion; also the response status vocabulary the client shows. */
enum class SuggestionStatus {
    /** Stored, awaiting editor review (also returned when an identical pending suggestion already existed). */
    PENDING,
    ACCEPTED,
    REJECTED,
}
