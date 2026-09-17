package uz.abumme.harfgame.backend.admin.suggestions

import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.review.SuggestionReviewWorker
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderKind
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionParams
import uz.abumme.harfgame.data.admin.suggestions.SuggestionReasons
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/** Filters of one suggestion-list query: [lang] null lists every language in the caller's scope. */
data class SuggestionQuery(
    val lang: String? = null,
    val decided: Boolean = false,
    val page: Int = 0,
    val size: Int = SuggestionParams.DEFAULT_SIZE,
) {
    companion object {
        /** Parses [SuggestionParams] query parameters; a malformed value answers 422 naming the parameter. */
        fun parse(parameters: Parameters): SuggestionQuery {
            fun text(name: String) = parameters[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun invalid(name: String): Nothing = throw AdminApiException.validation(name, FieldReasons.INVALID)
            fun long(name: String) = text(name)?.let { it.toLongOrNull() ?: invalid(name) }
            val decided = when (text(SuggestionParams.STATUS)) {
                null, SuggestionParams.PENDING -> false
                SuggestionParams.DECIDED -> true
                else -> invalid(SuggestionParams.STATUS)
            }
            val page = long(SuggestionParams.PAGE) ?: 0
            val size = long(SuggestionParams.SIZE) ?: SuggestionParams.DEFAULT_SIZE.toLong()
            if (page < 0 || page > Int.MAX_VALUE) invalid(SuggestionParams.PAGE)
            if (size !in 1..SuggestionParams.MAX_SIZE) invalid(SuggestionParams.SIZE)
            return SuggestionQuery(text(SuggestionParams.LANG), decided, page.toInt(), size.toInt())
        }
    }
}

/**
 * Suggestion review in the panel: pending suggestions oldest first and decided ones newest first, limited to the
 * caller's languages, and decisions that follow the same apply-once rule as Telegram taps. A decision also replaces
 * the known Telegram decision message's controls with the outcome (best-effort).
 */
class SuggestionsService(
    private val suggestions: SuggestionServerService,
    private val telegram: TelegramBot?,
    /** Languages that have a word pack: every language an ADMIN reviews. */
    private val packLanguages: suspend () -> List<String>,
) {

    suspend fun list(principal: StaffPrincipal, query: SuggestionQuery): PageDto<SuggestionDto> {
        val languages = query.lang?.let { lang -> principal.requireLanguage(lang); listOf(lang) }
            ?: principal.languagesWithin(packLanguages())
        return DatabaseFactory.dbQuery {
            val status = if (query.decided) WordSuggestionsTable.status neq SuggestionStatus.PENDING.name
            else WordSuggestionsTable.status eq SuggestionStatus.PENDING.name
            val condition = (WordSuggestionsTable.lang inList languages) and status
            val total = WordSuggestionsTable.selectAll().where(condition).count()
            val order = if (query.decided) {
                arrayOf(WordSuggestionsTable.decidedAt to SortOrder.DESC_NULLS_LAST, WordSuggestionsTable.id to SortOrder.DESC)
            } else {
                arrayOf(WordSuggestionsTable.createdAt to SortOrder.ASC, WordSuggestionsTable.id to SortOrder.ASC)
            }
            val rows = withAuthors(condition)
                .orderBy(*order)
                .limit(query.size)
                .offset(query.page.toLong() * query.size)
                .toList()
            PageDto(items = toDtos(rows), page = query.page, size = query.size, total = total)
        }
    }

    /**
     * Accepts or rejects a pending suggestion as [principal] (`decided_by = staff:<id>`). Refuses a language outside
     * the caller's scope (403), an unknown suggestion (404), one already decided (409 `status: already_decided`) and a
     * word that cannot enter the catalog (422 `word: <reason>`), changing nothing.
     */
    suspend fun decide(principal: StaffPrincipal, id: String, accept: Boolean): SuggestionDto {
        val outcome = suggestions.decide(
            suggestionId = id,
            accept = accept,
            editor = STAFF_PREFIX + principal.staffId,
            via = DecidedVia.EDITOR,
            actor = AuditActor.Staff(principal.staffId),
            authorize = principal::requireLanguage,
        )
        val name = principal.displayName ?: principal.username
        when (outcome) {
            is DecideOutcome.Applied ->
                replaceTelegramControls(id, if (accept) "✅ Принято — $name (панель)" else "❌ Отклонено — $name (панель)")
            DecideOutcome.AlreadyDecided -> {
                replaceTelegramControls(id, "ℹ️ Уже обработано")
                throw AdminApiException.conflict("status", SuggestionReasons.ALREADY_DECIDED)
            }
            DecideOutcome.NotFound -> throw AdminApiException.notFound("No suggestion $id")
            is DecideOutcome.Invalid -> throw AdminApiException.validation("word", outcome.reason)
        }
        return DatabaseFactory.dbQuery { toDtos(withAuthors(WordSuggestionsTable.id eq id).toList()).single() }
    }

    /**
     * Best effort, after the decision committed: edit the recorded decision message, then forget it so a later
     * decision never overwrites the outcome. A failure is only logged; if the controls survive, a later tap resolves
     * to Telegram's own "already decided" edit.
     */
    private suspend fun replaceTelegramControls(id: String, outcome: String) {
        val bot = telegram?.takeIf { it.enabled } ?: return
        try {
            val message = suggestions.telegramMessage(id) ?: return
            if (bot.replaceControls(message, outcome)) {
                suggestions.clearTelegramMessage(id)
            } else {
                System.err.println("Telegram: could not replace the controls of suggestion $id's message")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- inside a transaction -----------------------------------------------------------------------------------

    private fun withAuthors(condition: Op<Boolean>): Query =
        WordSuggestionsTable.join(UsersTable, JoinType.LEFT, WordSuggestionsTable.suggestedBy, UsersTable.id)
            .select(WordSuggestionsTable.columns + UsersTable.name)
            .where(condition)

    private fun toDtos(rows: List<ResultRow>): List<SuggestionDto> {
        val staffIds = rows.mapNotNull { row ->
            row[WordSuggestionsTable.decidedBy]?.takeIf { it.startsWith(STAFF_PREFIX) }?.removePrefix(STAFF_PREFIX)
        }.toSet()
        val staffNames = if (staffIds.isEmpty()) emptyMap() else {
            StaffTable.select(StaffTable.id, StaffTable.username, StaffTable.displayName)
                .where { StaffTable.id inList staffIds }
                .associate { it[StaffTable.id] to (it[StaffTable.displayName] ?: it[StaffTable.username]) }
        }
        return rows.map { row ->
            SuggestionDto(
                id = row[WordSuggestionsTable.id],
                lang = row[WordSuggestionsTable.lang],
                word = row[WordSuggestionsTable.word],
                author = row[UsersTable.name]?.takeIf { it.isNotBlank() } ?: ANONYMOUS,
                createdAt = row[WordSuggestionsTable.createdAt].toEpochMilli(),
                reason = row[WordSuggestionsTable.reviewReason],
                status = SuggestionStatus.valueOf(row[WordSuggestionsTable.status]),
                decidedBy = decider(row[WordSuggestionsTable.decidedBy], row[WordSuggestionsTable.decidedVia], staffNames),
                decidedAt = row[WordSuggestionsTable.decidedAt]?.toEpochMilli(),
            )
        }
    }

    private fun decider(decidedBy: String?, decidedVia: String?, staffNames: Map<String, String>): DeciderDto? = when {
        decidedBy == null -> null
        decidedBy.startsWith(STAFF_PREFIX) -> decidedBy.removePrefix(STAFF_PREFIX).let { DeciderDto(DeciderKind.STAFF, staffNames[it] ?: it) }
        decidedVia == DecidedVia.AUTO.name || decidedBy == SuggestionReviewWorker.AUTO_EDITOR -> DeciderDto(DeciderKind.AUTO, decidedBy)
        else -> DeciderDto(DeciderKind.TELEGRAM, decidedBy)
    }

    private companion object {
        const val STAFF_PREFIX = "staff:"
        const val ANONYMOUS = "Аноним"
    }
}
