package uz.abumme.harfgame.backend.review

import uz.abumme.harfgame.backend.dictionary.LookupResult
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.dictionary.WordLookup
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.QueuedSuggestion
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/**
 * Works through the suggestion review queue: a word the dictionary verifies is accepted into the pack and
 * announced; any other word is posted to editors with decision controls. Driven entirely by database state,
 * so a restart, a dictionary outage or a Telegram outage delays work instead of losing it — a suggestion
 * leaves the queue only once Telegram confirms its message (or the bot is disabled).
 */
class SuggestionReviewWorker(
    private val suggestions: SuggestionServerService,
    private val lookup: WordLookup,
    private val telegram: TelegramBot,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxLookupAttempts: Int = DEFAULT_MAX_LOOKUP_ATTEMPTS,
) {
    /** Process up to [batchSize] queued suggestions, oldest first. */
    suspend fun runOnce() {
        for (suggestion in suggestions.queued(batchSize)) {
            val delivered = when (suggestion.status) {
                SuggestionStatus.PENDING -> review(suggestion)
                // Accepted by an earlier run whose announcement Telegram did not confirm: announce again only.
                SuggestionStatus.ACCEPTED -> announce(suggestion, suggestion.autoForm ?: WordForm.DICTIONARY)
                SuggestionStatus.REJECTED -> true
            }
            if (delivered) suggestions.markPosted(suggestion.id)
        }
    }

    /** Whether the suggestion's message was delivered; false keeps it queued for the next run. */
    private suspend fun review(suggestion: QueuedSuggestion): Boolean =
        when (val result = lookup.lookup(suggestion.lang, suggestion.word)) {
            is LookupResult.Auto -> {
                val outcome = suggestions.decide(
                    suggestion.id, accept = true, editor = AUTO_EDITOR, via = DecidedVia.AUTO, form = result.form,
                )
                // Pack first, announcement second: a Telegram outage delays the notice, never the word.
                if (outcome is DecideOutcome.Applied) announce(suggestion, result.form) else true
            }
            is LookupResult.Review -> sendForReview(suggestion, result.reason)
            LookupResult.Unavailable ->
                suggestions.incrementLookupAttempts(suggestion.id) >= maxLookupAttempts &&
                    sendForReview(suggestion, ReviewReason.UNVERIFIED)
        }

    private suspend fun announce(suggestion: QueuedSuggestion, form: WordForm): Boolean =
        telegram.announceAutoAccepted(suggestion.lang, suggestion.word, form, suggestions.authorLabel(suggestion.suggestedBy))

    private suspend fun sendForReview(suggestion: QueuedSuggestion, reason: ReviewReason): Boolean =
        telegram.sendForReview(
            suggestion.id, suggestion.lang, suggestion.word, suggestions.authorLabel(suggestion.suggestedBy), reason,
        )

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val DEFAULT_MAX_LOOKUP_ATTEMPTS = 3

        /** `decided_by` recorded for automatic acceptances. */
        const val AUTO_EDITOR = "wiktionary"
    }
}
