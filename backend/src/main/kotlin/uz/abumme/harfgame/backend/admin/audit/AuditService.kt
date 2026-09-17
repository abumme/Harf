package uz.abumme.harfgame.backend.admin.audit

import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.audit.ActorKind
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.audit.AuditParams
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import java.time.Instant

/** Filters of one audit query; [to] is exclusive, [page] zero-based. */
data class AuditFilter(
    val actor: String? = null,
    val action: String? = null,
    val lang: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val page: Int = 0,
    val size: Int = AuditParams.DEFAULT_SIZE,
) {
    companion object {
        /** Parses [AuditParams] query parameters; a malformed number answers 422 naming the parameter. */
        fun parse(parameters: Parameters): AuditFilter {
            fun text(name: String) = parameters[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun long(name: String) = text(name)?.let { it.toLongOrNull() ?: throw AdminApiException.validation(name, FieldReasons.INVALID) }
            val page = long(AuditParams.PAGE) ?: 0
            val size = long(AuditParams.SIZE) ?: AuditParams.DEFAULT_SIZE.toLong()
            if (page < 0 || page > Int.MAX_VALUE) throw AdminApiException.validation(AuditParams.PAGE, FieldReasons.INVALID)
            if (size !in 1..AuditParams.MAX_SIZE) throw AdminApiException.validation(AuditParams.SIZE, FieldReasons.INVALID)
            return AuditFilter(
                actor = text(AuditParams.ACTOR),
                action = text(AuditParams.ACTION),
                lang = text(AuditParams.LANG),
                from = long(AuditParams.FROM)?.let(Instant::ofEpochMilli),
                to = long(AuditParams.TO)?.let(Instant::ofEpochMilli),
                page = page.toInt(),
                size = size.toInt(),
            )
        }
    }
}

/** Reads the audit log, newest first, with what each staff member may see. The log has no edit or delete path. */
class AuditService {

    /**
     * `AUDIT_READ_ALL` sees every entry. `AUDIT_READ_OWN` is limited to entries the caller acted in, and naming
     * another actor is refused as forbidden.
     */
    suspend fun list(principal: StaffPrincipal, requested: AuditFilter): PageDto<AuditEntryDto> {
        val filter = when {
            principal.has(Permission.AUDIT_READ_ALL) -> requested
            principal.has(Permission.AUDIT_READ_OWN) -> {
                if (requested.actor != null && requested.actor != principal.staffId) {
                    throw AdminApiException.forbidden("You can only read your own activity")
                }
                requested.copy(actor = principal.staffId)
            }
            else -> throw AdminApiException.forbidden()
        }
        // Daily-word actions (including SYSTEM re-picks a WORDER's catalog change caused) are shown only to members
        // who work on the calendar.
        val showsDaily = principal.has(Permission.CALENDAR_MANAGE) || principal.has(Permission.DAILY_POOL_MANAGE)

        return DatabaseFactory.dbQuery {
            val condition = conditionFor(filter).let { base ->
                if (showsDaily) base else base and (StaffAuditLogTable.action notLike "${AuditActions.DAILY_PREFIX}%")
            }
            val total = StaffAuditLogTable.selectAll().where(condition).count()
            val rows = StaffAuditLogTable.selectAll().where(condition)
                .orderBy(StaffAuditLogTable.at to SortOrder.DESC, StaffAuditLogTable.id to SortOrder.DESC)
                .limit(filter.size)
                .offset(filter.page.toLong() * filter.size)
                .toList()

            val staffIds = rows.flatMap { row ->
                listOfNotNull(
                    row[StaffAuditLogTable.actorStaffId],
                    row[StaffAuditLogTable.targetId]?.takeIf { row[StaffAuditLogTable.targetType] == AuditTargets.STAFF },
                )
            }.toSet()
            val usernames = if (staffIds.isEmpty()) emptyMap() else {
                StaffTable.select(StaffTable.id, StaffTable.username).where { StaffTable.id inList staffIds }
                    .associate { it[StaffTable.id] to it[StaffTable.username] }
            }
            fun targetIds(type: String) = rows.mapNotNull { row ->
                row[StaffAuditLogTable.targetId]?.takeIf { row[StaffAuditLogTable.targetType] == type }
            }.toSet()
            // Words and suggestions are labelled by their current spelling.
            val wordIds = targetIds(AuditTargets.WORD)
            val words = if (wordIds.isEmpty()) emptyMap() else {
                WordsTable.select(WordsTable.id, WordsTable.text).where { WordsTable.id inList wordIds }
                    .associate { it[WordsTable.id] to it[WordsTable.text] }
            }
            val suggestionIds = targetIds(AuditTargets.SUGGESTION)
            val suggestionWords = if (suggestionIds.isEmpty()) emptyMap() else {
                WordSuggestionsTable.select(WordSuggestionsTable.id, WordSuggestionsTable.word)
                    .where { WordSuggestionsTable.id inList suggestionIds }
                    .associate { it[WordSuggestionsTable.id] to it[WordSuggestionsTable.word] }
            }
            // A pair is labelled "latin / cyrillic" while it exists.
            val pairIds = targetIds(AuditTargets.LEXEME_PAIR)
            val pairs = if (pairIds.isEmpty()) emptyMap() else {
                val pairRows = LexemePairsTable.selectAll().where { LexemePairsTable.id inList pairIds }.toList()
                val pairWordIds = pairRows.flatMap { listOf(it[LexemePairsTable.latnWordId], it[LexemePairsTable.cyrlWordId]) }
                val texts = WordsTable.select(WordsTable.id, WordsTable.text).where { WordsTable.id inList pairWordIds }
                    .associate { it[WordsTable.id] to it[WordsTable.text] }
                pairRows.associate {
                    it[LexemePairsTable.id] to "${texts[it[LexemePairsTable.latnWordId]]} / ${texts[it[LexemePairsTable.cyrlWordId]]}"
                }
            }
            val labels = mapOf(
                AuditTargets.STAFF to usernames,
                AuditTargets.WORD to words,
                AuditTargets.SUGGESTION to suggestionWords,
                AuditTargets.LEXEME_PAIR to pairs,
            )

            PageDto(
                items = rows.map { row ->
                    val targetType = row[StaffAuditLogTable.targetType]
                    val targetId = row[StaffAuditLogTable.targetId]
                    AuditEntryDto(
                        id = row[StaffAuditLogTable.id],
                        at = row[StaffAuditLogTable.at].toEpochMilli(),
                        actorKind = ActorKind.valueOf(row[StaffAuditLogTable.actorKind]),
                        actorStaffId = row[StaffAuditLogTable.actorStaffId],
                        actorUsername = row[StaffAuditLogTable.actorStaffId]?.let(usernames::get),
                        action = row[StaffAuditLogTable.action],
                        targetType = targetType,
                        targetId = targetId,
                        targetLabel = targetId?.let { labels[targetType]?.get(it) },
                        lang = row[StaffAuditLogTable.lang],
                        details = row[StaffAuditLogTable.details]?.let { Json.parseToJsonElement(it) as? JsonObject },
                    )
                },
                page = filter.page,
                size = filter.size,
                total = total,
            )
        }
    }

    private fun conditionFor(filter: AuditFilter): Op<Boolean> {
        val conditions = listOfNotNull(
            filter.actor?.let { StaffAuditLogTable.actorStaffId eq it },
            filter.action?.let { StaffAuditLogTable.action eq it },
            filter.lang?.let { StaffAuditLogTable.lang eq it },
            filter.from?.let { StaffAuditLogTable.at greaterEq it },
            filter.to?.let { StaffAuditLogTable.at less it },
        )
        return conditions.fold(Op.TRUE as Op<Boolean>) { acc, op -> acc and op }
    }
}
