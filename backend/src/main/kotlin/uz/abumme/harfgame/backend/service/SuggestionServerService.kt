package uz.abumme.harfgame.backend.service

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.admin.words.PackIntegrityException
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.admin.words.WordValidator
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.SuggestionReportsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.dictionary.ReviewReason
import uz.abumme.harfgame.backend.dictionary.WordForm
import uz.abumme.harfgame.backend.telegram.PostedMessage
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.lang.LanguageRegistry
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Result of attempting to store a suggestion. The route maps these to HTTP status codes. */
sealed interface SuggestOutcome {
    /** A new pending suggestion was stored and queued for the review worker. */
    data class Stored(val id: String) : SuggestOutcome
    /** An identical pending suggestion already existed; reported as success, nothing new stored. */
    data object DuplicatePending : SuggestOutcome
    /** Failed validation (ill-formed, offensive, gibberish, already an active word, or not playable). */
    data class Rejected(val reason: String) : SuggestOutcome
    /** The author exceeded the per-day cap. */
    data object OverCap : SuggestOutcome

    /** An ADMIN blocked the author's suggestions: refused before validation, nothing stored, looked up or counted. */
    data object Blocked : SuggestOutcome
}

/** Result of an editor decision on a suggestion. */
sealed interface DecideOutcome {
    data class Applied(val accepted: Boolean, val lang: String, val word: String) : DecideOutcome
    data object NotFound : DecideOutcome
    data object AlreadyDecided : DecideOutcome

    /**
     * Accepting was refused and nothing changed: the (legacy) word fails catalog validation, or the resulting pack
     * would fail the app's check. [reason] is one of [WordReasons]. The suggestion stays pending and can be rejected.
     */
    data class Invalid(val reason: String) : DecideOutcome
}

/** Background review progress of a suggestion (`word_suggestions.review_state`). */
enum class ReviewState { QUEUED, POSTED }

/** Who decided a suggestion (`word_suggestions.decided_via`). */
enum class DecidedVia { AUTO, EDITOR }

/** A suggestion awaiting the review worker. */
data class QueuedSuggestion(
    val id: String,
    val lang: String,
    val word: String,
    val suggestedBy: String?,
    val status: SuggestionStatus,
    val lookupAttempts: Int,
    /** Set once the suggestion was accepted automatically. */
    val autoForm: WordForm?,
    /** How it was decided, when it already was (e.g. by staff in the panel while still queued). */
    val decidedVia: DecidedVia? = null,
)

/** The facts about one suggestion that authorization and decisions need. */
data class SuggestionRef(val id: String, val lang: String, val word: String, val status: SuggestionStatus)

/** One language's decisions within a report window, plus its suggestions still pending when queried. */
data class DailySummary(
    val autoAccepted: List<String>,
    val editorAccepted: List<String>,
    val rejected: List<String>,
    val pending: Long,
)

/**
 * Validates and stores word suggestions, and applies decisions on them. Validation first cuts obvious noise with
 * string heuristics (character bounds, blocklist, gibberish), then requires a playable word: it must tokenize into the
 * language's letters with a supported board length, exactly as the app and the word catalog count graphemes.
 * Editors decide whether a word is real; an accepted word enters the word catalog in the decision's transaction.
 */
class SuggestionServerService(
    wordPackService: WordPackServerService,
    private val dailyCap: Int = DEFAULT_DAILY_CAP,
    private val catalog: WordCatalogService = WordCatalogService(wordPackService),
    private val registry: LanguageRegistry = LanguageRegistry(),
) {
    suspend fun suggest(userId: String, langRaw: String, wordRaw: String): SuggestOutcome {
        // A blocked account is refused before anything else looks at the word.
        if (suggestionsBlocked(userId)) return SuggestOutcome.Blocked
        val lang = langRaw.trim()
        val word = wordRaw.trim().lowercase()

        if (lang.isEmpty()) return SuggestOutcome.Rejected("bad_lang")
        if (word.length !in MIN_CHARS..MAX_CHARS) return SuggestOutcome.Rejected("bad_length")
        if (catalog.blocklists.isBlocked(lang, word)) return SuggestOutcome.Rejected("offensive")
        gibberishReason(word)?.let { return SuggestOutcome.Rejected(it) }
        if (catalog.statusOf(lang, word) == WordStatus.ACTIVE) return SuggestOutcome.Rejected("already_present")
        if (!isPlayable(lang, word)) return SuggestOutcome.Rejected(NOT_PLAYABLE)

        return DatabaseFactory.dbQuery {
            val hasPending = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.lang eq lang) and
                    (WordSuggestionsTable.word eq word) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }.any()
            if (hasPending) return@dbQuery SuggestOutcome.DuplicatePending

            val since = Instant.ofEpochMilli(System.currentTimeMillis() - DAY_MILLIS)
            val recent = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.suggestedBy eq userId) and
                    (WordSuggestionsTable.createdAt greater since)
            }.count()
            if (recent >= dailyCap) return@dbQuery SuggestOutcome.OverCap

            val id = UUID.randomUUID().toString()
            WordSuggestionsTable.insert {
                it[WordSuggestionsTable.id] = id
                it[WordSuggestionsTable.lang] = lang
                it[WordSuggestionsTable.word] = word
                it[suggestedBy] = userId
                it[status] = SuggestionStatus.PENDING.name
                it[createdAt] = Instant.now()
                it[reviewState] = ReviewState.QUEUED.name
            }
            SuggestOutcome.Stored(id)
        }
    }

    /** Whether an ADMIN blocked [userId]'s suggestions (an unknown account is not blocked). */
    private suspend fun suggestionsBlocked(userId: String): Boolean = DatabaseFactory.dbQuery {
        UsersTable.select(UsersTable.suggestionsBlockedAt).where { UsersTable.id eq userId }
            .singleOrNull()?.get(UsersTable.suggestionsBlockedAt) != null
    }

    /** Whether [word] tokenizes in [lang] with a supported board length (the shared word rules). */
    fun isPlayable(lang: String, word: String): Boolean =
        registry.config(lang)?.let { WordRules.check(it, word).isValid } ?: false

    /** The catalog status of [word] in [lang]: the review worker never auto-accepts a word staff removed. */
    suspend fun catalogStatus(lang: String, word: String): WordStatus? = catalog.statusOf(lang, word)

    /** Validates [word] with the catalog rules of [lang] (incl. the blocklist); null when it may enter the catalog. */
    fun catalogRejection(lang: String, word: String): String? =
        (catalog.validator(lang).validate(word) as? WordValidator.Invalid)?.reason

    /** Human label for a suggestion's author: the display name, else "Аноним" (also for a deleted account). */
    suspend fun authorLabel(userId: String?): String = DatabaseFactory.dbQuery {
        userId?.let { UsersTable.selectAll().where { UsersTable.id eq it }.singleOrNull()?.get(UsersTable.name) }
            ?.takeIf { it.isNotBlank() }
            ?: ANONYMOUS
    }

    suspend fun find(suggestionId: String): SuggestionRef? = DatabaseFactory.dbQuery {
        WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }.singleOrNull()?.toRef()
    }

    /**
     * Apply a decision. Acts only while the suggestion is still PENDING (idempotent against a double-tap, a second
     * editor, the panel, or a racing automatic acceptance). On accept, the word enters the word catalog — inserted,
     * restored, or left alone when already active — and the pack is published only when that changed something, all
     * in the same transaction as the status change. A word that fails catalog validation is refused as
     * [DecideOutcome.Invalid] without changing anything.
     *
     * [editor] is stored as `decided_by` (`staff:<id>`, a Telegram user id, or "wiktionary"); [actor] attributes the
     * audit entry. [authorize] runs inside the transaction once the suggestion's language is known and refuses by
     * throwing (the panel's language scope). An automatic acceptance passes the verified [form], kept for its
     * announcement.
     */
    suspend fun decide(
        suggestionId: String,
        accept: Boolean,
        editor: String,
        via: DecidedVia = DecidedVia.EDITOR,
        form: WordForm? = null,
        actor: AuditActor = if (via == DecidedVia.AUTO) AuditActor.System else AuditActor.Telegram(null),
        authorize: (lang: String) -> Unit = {},
    ): DecideOutcome {
        val newStatus = if (accept) SuggestionStatus.ACCEPTED else SuggestionStatus.REJECTED
        return try {
            // One conditional UPDATE decides the race. READ COMMITTED makes a concurrent decision re-check
            // `status = PENDING` after waiting for the row lock (and update nothing) instead of failing with
            // the serialization error the pool's default REPEATABLE READ raises. The catalog write then takes the
            // language's pack lock, always after the suggestion row, so the lock order never inverts.
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                val row = WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }.singleOrNull()
                    ?.toRef() ?: return@dbQuery DecideOutcome.NotFound
                authorize(row.lang)
                if (row.status != SuggestionStatus.PENDING) return@dbQuery DecideOutcome.AlreadyDecided
                val valid = if (!accept) null else {
                    when (val result = catalog.validator(row.lang).validate(row.word)) {
                        is WordValidator.Valid -> result
                        is WordValidator.Invalid -> return@dbQuery DecideOutcome.Invalid(result.reason)
                    }
                }

                val updated = WordSuggestionsTable.update({
                    (WordSuggestionsTable.id eq suggestionId) and
                        (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
                }) {
                    it[status] = newStatus.name
                    it[decidedBy] = editor
                    it[decidedVia] = via.name
                    it[autoForm] = form?.name
                    it[decidedAt] = Instant.now()
                }
                if (updated == 0) return@dbQuery DecideOutcome.AlreadyDecided

                if (valid != null) {
                    catalog.acceptSuggestionInTransaction(row.lang, valid.normalized, suggestionId, via, actor)
                }
                catalog.audit.record(
                    actor, AuditActions.SUGGESTION_DECIDED, AuditTargets.SUGGESTION, suggestionId, row.lang,
                    auditDetails {
                        fact("word", jsonOf(row.word))
                        fact("status", jsonOf(newStatus.name))
                        fact("via", jsonOf(via.name))
                    },
                )
                DecideOutcome.Applied(accept, row.lang, row.word)
            }
        } catch (e: PackIntegrityException) {
            System.err.println(e.message)
            DecideOutcome.Invalid(WordReasons.PACK_INTEGRITY)
        }
    }

    /** Suggestions awaiting the review worker, oldest first. Rows that predate the worker are never returned. */
    suspend fun queued(limit: Int): List<QueuedSuggestion> = DatabaseFactory.dbQuery {
        WordSuggestionsTable.selectAll()
            .where { WordSuggestionsTable.reviewState eq ReviewState.QUEUED.name }
            .orderBy(WordSuggestionsTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { row ->
                QueuedSuggestion(
                    id = row[WordSuggestionsTable.id],
                    lang = row[WordSuggestionsTable.lang],
                    word = row[WordSuggestionsTable.word],
                    suggestedBy = row[WordSuggestionsTable.suggestedBy],
                    status = SuggestionStatus.valueOf(row[WordSuggestionsTable.status]),
                    lookupAttempts = row[WordSuggestionsTable.lookupAttempts] ?: 0,
                    autoForm = row[WordSuggestionsTable.autoForm]?.let(WordForm::valueOf),
                    decidedVia = row[WordSuggestionsTable.decidedVia]?.let(DecidedVia::valueOf),
                )
            }
    }

    /**
     * Record that Telegram confirmed the suggestion's message (or that there was nothing to send); the worker stops
     * selecting it. A suggestion sent to editors records why ([reason]) and, when the bot is enabled, the decision
     * [message] a panel decision will later update.
     */
    suspend fun markPosted(suggestionId: String, reason: ReviewReason? = null, message: PostedMessage? = null) {
        DatabaseFactory.dbQuery {
            WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) {
                it[reviewState] = ReviewState.POSTED.name
                if (reason != null) it[reviewReason] = reason.name
                if (message != null) {
                    it[telegramChatId] = message.chatId
                    it[telegramMessageId] = message.messageId
                    it[telegramText] = message.text
                }
            }
        }
    }

    /** The editors' decision message whose controls are still to be replaced, if one was recorded. */
    suspend fun telegramMessage(suggestionId: String): PostedMessage? = DatabaseFactory.dbQuery {
        val row = WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }.singleOrNull()
            ?: return@dbQuery null
        val chatId = row[WordSuggestionsTable.telegramChatId] ?: return@dbQuery null
        val messageId = row[WordSuggestionsTable.telegramMessageId] ?: return@dbQuery null
        val text = row[WordSuggestionsTable.telegramText] ?: return@dbQuery null
        PostedMessage(chatId, messageId, text)
    }

    /** Forget the decision message once an edit replaced its controls, so a later decision never overwrites that outcome. */
    suspend fun clearTelegramMessage(suggestionId: String) {
        DatabaseFactory.dbQuery {
            WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) {
                it[telegramChatId] = null
                it[telegramMessageId] = null
                it[telegramText] = null
            }
        }
    }

    /** Count one more failed dictionary lookup; returns the new total. */
    suspend fun incrementLookupAttempts(suggestionId: String): Int = DatabaseFactory.dbQuery {
        val attempts = (WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq suggestionId }
            .single()[WordSuggestionsTable.lookupAttempts] ?: 0) + 1
        WordSuggestionsTable.update({ WordSuggestionsTable.id eq suggestionId }) { it[lookupAttempts] = attempts }
        attempts
    }

    /**
     * [lang]'s decisions with `decided_at` in the half-open window [[from], [to]), in decision order, plus
     * its suggestions still pending now. Decisions with no recorded source (made before automatic
     * acceptance existed) count as editor decisions.
     */
    suspend fun dailySummary(lang: String, from: Instant, to: Instant): DailySummary = DatabaseFactory.dbQuery {
        val decided = WordSuggestionsTable.selectAll().where {
            (WordSuggestionsTable.lang eq lang) and
                (WordSuggestionsTable.decidedAt greaterEq from) and
                (WordSuggestionsTable.decidedAt less to)
        }.orderBy(WordSuggestionsTable.decidedAt, SortOrder.ASC).toList()

        fun words(predicate: (ResultRow) -> Boolean) = decided.filter(predicate).map { it[WordSuggestionsTable.word] }
        fun ResultRow.isAccepted() = this[WordSuggestionsTable.status] == SuggestionStatus.ACCEPTED.name
        fun ResultRow.isAuto() = this[WordSuggestionsTable.decidedVia] == DecidedVia.AUTO.name

        DailySummary(
            autoAccepted = words { it.isAccepted() && it.isAuto() },
            editorAccepted = words { it.isAccepted() && !it.isAuto() },
            rejected = words { it[WordSuggestionsTable.status] == SuggestionStatus.REJECTED.name },
            pending = WordSuggestionsTable.selectAll().where {
                (WordSuggestionsTable.lang eq lang) and
                    (WordSuggestionsTable.status eq SuggestionStatus.PENDING.name)
            }.count(),
        )
    }

    /** Whether [lang]'s report for [day] was already delivered or settled as quiet. */
    suspend fun reportRecorded(lang: String, day: LocalDate): Boolean = DatabaseFactory.dbQuery {
        SuggestionReportsTable.selectAll()
            .where { (SuggestionReportsTable.lang eq lang) and (SuggestionReportsTable.day eq day) }
            .any()
    }

    /** Settle [lang]'s report for [day] so it is never sent again. */
    suspend fun recordReport(lang: String, day: LocalDate) {
        DatabaseFactory.dbQuery {
            SuggestionReportsTable.insertIgnore {
                it[SuggestionReportsTable.lang] = lang
                it[SuggestionReportsTable.day] = day
                it[sentAt] = Instant.now()
            }
        }
    }

    private fun ResultRow.toRef() = SuggestionRef(
        id = this[WordSuggestionsTable.id],
        lang = this[WordSuggestionsTable.lang],
        word = this[WordSuggestionsTable.word],
        status = SuggestionStatus.valueOf(this[WordSuggestionsTable.status]),
    )

    /** Cheap gibberish checks. Returns a reason string when the word looks like junk, else null. */
    private fun gibberishReason(word: String): String? {
        // three or more of the same character in a row: "aaa", "sssalom"
        var run = 1
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) {
                run++
                if (run >= 3) return "repeat_run"
            } else run = 1
        }
        if (word.toSet().size < MIN_DISTINCT) return "too_few_distinct"
        if (word.none { it in VOWELS }) return "no_vowel"
        return null
    }

    companion object {
        const val DEFAULT_DAILY_CAP = 10

        /** Rejection reason: the word does not tokenize in the language or has an unsupported board length. */
        const val NOT_PLAYABLE = "not_playable"
        private const val ANONYMOUS = "Аноним"
        private const val MIN_CHARS = 2
        private const val MAX_CHARS = 24
        private const val MIN_DISTINCT = 3
        private const val DAY_MILLIS = 24L * 3600 * 1000
        // Latin + Cyrillic (ru/kk) + Uzbek vowels. Broad on purpose — a false "has vowel" is safe.
        private val VOWELS = "aeiouyаеёиоуыэюяәөүұіи".toSet()
    }
}
