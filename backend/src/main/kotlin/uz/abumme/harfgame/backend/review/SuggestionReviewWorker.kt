package uz.abumme.harfgame.backend.review

import uz.abumme.harfgame.backend.dictionary.LookupResult
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.dictionary.WordLookup
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.QueuedSuggestion
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.telegram.PostedMessage
import uz.abumme.harfgame.backend.telegram.ReviewPost
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/**
 * Works through the suggestion review queue: a word the dictionary verifies is accepted into the word catalog and
 * announced; any other word is posted to editors with decision controls. A word staff removed from the catalog always
 * goes to editors, whatever the dictionary says. Driven entirely by database state, so a restart, a dictionary outage
 * or a Telegram outage delays work instead of losing it — a suggestion leaves the queue only once Telegram confirms
 * its message (or the bot is disabled).
 */
class SuggestionReviewWorker(
    private val suggestions: SuggestionServerService,
    private val lookup: WordLookup,
    private val telegram: TelegramBot,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxLookupAttempts: Int = DEFAULT_MAX_LOOKUP_ATTEMPTS,
) {
    /** A handled suggestion: why it went to editors and the message a panel decision will update, when any. */
    private data class Handled(val reason: ReviewReason? = null, val message: PostedMessage? = null)

    /** Process up to [batchSize] queued suggestions, oldest first. */
    suspend fun runOnce() {
        for (suggestion in suggestions.queued(batchSize)) {
            val handled = when (suggestion.status) {
                SuggestionStatus.PENDING -> review(suggestion)
                // Accepted by an earlier run whose announcement Telegram did not confirm: announce again only.
                // Accepted by staff in the panel while still queued: nothing to announce.
                SuggestionStatus.ACCEPTED ->
                    if (suggestion.decidedVia != DecidedVia.AUTO) Handled()
                    else announce(suggestion, suggestion.autoForm ?: WordForm.DICTIONARY)
                SuggestionStatus.REJECTED -> Handled()
            }
            if (handled != null) suggestions.markPosted(suggestion.id, handled.reason, handled.message)
        }
    }

    /** How the suggestion was handled; null keeps it queued for the next run. */
    private suspend fun review(suggestion: QueuedSuggestion): Handled? {
        // Never auto-accept a word staff removed, and never look up a word the catalog could not take.
        if (suggestions.catalogStatus(suggestion.lang, suggestion.word) == WordStatus.REMOVED) {
            return sendForReview(suggestion, ReviewReason.REMOVED_BY_STAFF)
        }
        if (suggestions.catalogRejection(suggestion.lang, suggestion.word) != null) {
            return sendForReview(suggestion, ReviewReason.NOT_PLAYABLE)
        }
        return when (val result = lookup.lookup(suggestion.lang, suggestion.word)) {
            is LookupResult.Auto -> {
                val outcome = suggestions.decide(
                    suggestion.id, accept = true, editor = AUTO_EDITOR, via = DecidedVia.AUTO, form = result.form,
                )
                // Catalog first, announcement second: a Telegram outage delays the notice, never the word.
                when (outcome) {
                    is DecideOutcome.Applied -> announce(suggestion, result.form)
                    is DecideOutcome.Invalid -> sendForReview(suggestion, ReviewReason.NOT_PLAYABLE)
                    DecideOutcome.AlreadyDecided, DecideOutcome.NotFound -> Handled()
                }
            }
            is LookupResult.Review -> sendForReview(suggestion, result.reason)
            LookupResult.Unavailable ->
                if (suggestions.incrementLookupAttempts(suggestion.id) >= maxLookupAttempts) {
                    sendForReview(suggestion, ReviewReason.UNVERIFIED)
                } else {
                    null
                }
        }
    }

    private suspend fun announce(suggestion: QueuedSuggestion, form: WordForm): Handled? =
        Handled().takeIf {
            telegram.announceAutoAccepted(suggestion.lang, suggestion.word, form, suggestions.authorLabel(suggestion.suggestedBy))
        }

    private suspend fun sendForReview(suggestion: QueuedSuggestion, reason: ReviewReason): Handled? =
        when (val post = telegram.sendForReview(
            suggestion.id, suggestion.lang, suggestion.word, suggestions.authorLabel(suggestion.suggestedBy), reason,
        )) {
            is ReviewPost.Posted -> Handled(reason, post.message)
            ReviewPost.Disabled -> Handled(reason)
            ReviewPost.NotConfirmed -> null
        }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val DEFAULT_MAX_LOOKUP_ATTEMPTS = 3

        /** `decided_by` recorded for automatic acceptances. */
        const val AUTO_EDITOR = "wiktionary"
    }
}
