package uz.abumme.harfgame.backend.admin.players

import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.RefreshTokensTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.UserStatsTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.players.EndSessionsResultDto
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerLanguageStatsDto
import uz.abumme.harfgame.data.admin.players.PlayerParams
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerSuggestionDto
import uz.abumme.harfgame.data.admin.players.PlayerSummaryDto
import uz.abumme.harfgame.data.admin.players.PlayerType
import uz.abumme.harfgame.data.admin.players.SuggestionBlockDto
import uz.abumme.harfgame.data.admin.players.SuggestionCountsDto
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.stats.Streaks
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.data.sync.ResultRecordDto
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlin.time.ExperimentalTime

/**
 * Player accounts for ADMINs: search, detail, and the support actions. Deleting and ending sessions go through the
 * players' own [AuthServerService] paths (Apple revocation included); every action writes its audit entry in the
 * transaction of the change, never the removed display name, a provider subject or a token. Views and searches are
 * not audited.
 */
class PlayersService(
    private val clock: Clock,
    private val audit: AuditLog,
    private val playerAuth: AuthServerService,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * One page of accounts, newest first, continuing after [PlayerSearchQuery.cursor]. Keyset paging over
     * `(created_at, id)`, so accounts created while an ADMIN pages never shift the rest: nothing repeats or is skipped.
     */
    suspend fun search(query: PlayerSearchQuery): PlayerSearchPageDto {
        query.problems().firstOrNull()?.let { throw AdminApiException.validation(it.field, it.reason) }
        val after = query.cursor?.let { SearchCursor.decode(it) ?: throw AdminApiException.validation(PlayerParams.CURSOR, FieldReasons.INVALID) }
        return DatabaseFactory.dbQuery {
            val rows = UsersTable.select(UsersTable.id, UsersTable.createdAt, UsersTable.name, UsersTable.suggestionsBlockedAt)
                .where(conditionFor(query, after))
                .orderBy(UsersTable.createdAt to SortOrder.DESC, UsersTable.id to SortOrder.DESC)
                .limit(query.size + 1)
                .toList()
            val page = rows.take(query.size)
            val providers = providersOf(page.map { it[UsersTable.id] })
            PlayerSearchPageDto(
                items = page.map { row ->
                    val id = row[UsersTable.id]
                    PlayerSummaryDto(
                        id = id,
                        displayName = row[UsersTable.name]?.takeIf { it.isNotBlank() },
                        providers = providers[id].orEmpty(),
                        createdAt = row[UsersTable.createdAt].toEpochMilli(),
                        suggestionsBlocked = row[UsersTable.suggestionsBlockedAt] != null,
                    )
                },
                nextCursor = if (rows.size > query.size) page.last().let { SearchCursor(it[UsersTable.createdAt], it[UsersTable.id]).encode() } else null,
            )
        }
    }

    /** The account's detail, or null when there is no such account. */
    suspend fun detail(id: String): PlayerDetailDto? = DatabaseFactory.dbQuery { detailOf(id) }

    /**
     * Deletes the account exactly as the player's own deletion does. An unknown account (404) and a confirmation that
     * does not name it (422 `confirmAccountId: mismatch`) are refused before anything is revoked or deleted. The audit
     * entry lists only the provider types and commits together with the deletion.
     */
    suspend fun delete(id: String, confirmAccountId: String, actor: AuditActor.Staff) {
        val providers = DatabaseFactory.dbQuery {
            requirePlayer(id)
            providersOf(listOf(id))[id].orEmpty()
        }
        if (confirmAccountId != id) throw AdminApiException.validation("confirmAccountId", PlayerReasons.MISMATCH)
        val deleted = playerAuth.deleteAccount(id) {
            audit.record(
                actor, AuditActions.PLAYER_DELETED, AuditTargets.PLAYER, id,
                details = auditDetails { fact("providers", jsonOf(providers.map { it.name })) },
            )
        }
        if (!deleted) throw notFound(id)
    }

    /**
     * Revokes every refresh token of the account (the players' logout), so no device can refresh any more; access tokens
     * already issued last until they expire. Audited with the number revoked, even when that is 0.
     */
    suspend fun endSessions(id: String, actor: AuditActor.Staff): EndSessionsResultDto {
        val revoked = playerAuth.logout(id) { count ->
            // Throwing here rolls the (then empty) revocation back.
            requirePlayer(id)
            audit.record(
                actor, AuditActions.PLAYER_SESSIONS_ENDED, AuditTargets.PLAYER, id,
                details = auditDetails { fact("revoked", jsonOf(count.toLong())) },
            )
        }
        return EndSessionsResultDto(revoked)
    }

    /** Blocks the account's suggestions. A second block changes nothing: the original staff member and time stay. */
    suspend fun blockSuggestions(id: String, actor: AuditActor.Staff): PlayerDetailDto = change(id) {
        val changed = UsersTable.update({ (UsersTable.id eq id) and UsersTable.suggestionsBlockedAt.isNull() }) {
            it[suggestionsBlockedAt] = clock.instant()
            it[suggestionsBlockedBy] = actor.staffId
        }
        if (changed > 0) audit.record(actor, AuditActions.PLAYER_SUGGESTIONS_BLOCKED, AuditTargets.PLAYER, id)
    }

    /** Lifts the block; pending suggestions were never touched by it. Unblocking an unblocked account changes nothing. */
    suspend fun unblockSuggestions(id: String, actor: AuditActor.Staff): PlayerDetailDto = change(id) {
        val changed = UsersTable.update({ (UsersTable.id eq id) and UsersTable.suggestionsBlockedAt.isNotNull() }) {
            it[suggestionsBlockedAt] = null
            it[suggestionsBlockedBy] = null
        }
        if (changed > 0) audit.record(actor, AuditActions.PLAYER_SUGGESTIONS_UNBLOCKED, AuditTargets.PLAYER, id)
    }

    /**
     * Clears the display name, so the author label of the account's suggestions is anonymous from now on. The removed
     * name is not kept anywhere, the audit entry included.
     */
    suspend fun clearDisplayName(id: String, actor: AuditActor.Staff): PlayerDetailDto = change(id) {
        val changed = UsersTable.update({ (UsersTable.id eq id) and UsersTable.name.isNotNull() }) {
            it[name] = null
        }
        if (changed > 0) audit.record(actor, AuditActions.PLAYER_DISPLAY_NAME_CLEARED, AuditTargets.PLAYER, id)
    }

    /**
     * Runs a conditional update of one account and answers its new detail. READ COMMITTED: a concurrent identical action
     * waits for the row, re-checks its condition and changes (and audits) nothing, instead of failing to serialize.
     */
    private suspend fun change(id: String, update: () -> Unit): PlayerDetailDto =
        DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            requirePlayer(id)
            update()
            detailOf(id) ?: throw notFound(id)
        }

    // --- inside a transaction -----------------------------------------------------------------------------------

    private fun requirePlayer(id: String) {
        if (UsersTable.select(UsersTable.id).where { UsersTable.id eq id }.empty()) throw notFound(id)
    }

    private fun notFound(id: String) = AdminApiException.notFound("No player account $id")

    private fun conditionFor(query: PlayerSearchQuery, after: SearchCursor?): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        query.accountId?.let { conditions += UsersTable.id eq it }
        query.namePart?.let { part ->
            // lower(name) LIKE '%…%' ESCAPE '\', with the typed %, _ and \ matched literally.
            conditions += UsersTable.name.lowerCase() like (LikePattern("%", LIKE_ESCAPE) + LikePattern.ofLiteral(part.lowercase(), LIKE_ESCAPE) + "%")
        }
        when (val type = query.type) {
            null -> {}
            PlayerType.ANONYMOUS -> conditions += notExists(identities())
            PlayerType.GOOGLE, PlayerType.APPLE -> conditions += exists(identities(provider = type.name))
        }
        query.createdFrom?.let { conditions += UsersTable.createdAt greaterEq startOfDay(it) }
        query.createdTo?.let { conditions += UsersTable.createdAt less startOfDay(it) }
        query.blocked?.let { blocked ->
            conditions += if (blocked) UsersTable.suggestionsBlockedAt.isNotNull() else UsersTable.suggestionsBlockedAt.isNull()
        }
        after?.let { cursor ->
            conditions += (UsersTable.createdAt less cursor.createdAt) or
                ((UsersTable.createdAt eq cursor.createdAt) and (UsersTable.id less cursor.id))
        }
        return conditions.fold(Op.TRUE as Op<Boolean>) { all, condition -> all and condition }
    }

    /** The linked identities (of [provider], when given) of the outer query's account. */
    private fun identities(provider: String? = null) =
        OAuthIdentitiesTable.select(OAuthIdentitiesTable.id).where {
            val ofAccount = OAuthIdentitiesTable.userId eq UsersTable.id
            if (provider == null) ofAccount else ofAccount and (OAuthIdentitiesTable.provider eq provider)
        }

    private fun startOfDay(date: kotlinx.datetime.LocalDate): Instant = date.toJavaLocalDate().atStartOfDay(EDITORS_ZONE).toInstant()

    /** Each account's linked provider types, sorted; accounts without identities are absent. */
    private fun providersOf(userIds: Collection<String>): Map<String, List<OAuthProvider>> {
        if (userIds.isEmpty()) return emptyMap()
        return OAuthIdentitiesTable.select(OAuthIdentitiesTable.userId, OAuthIdentitiesTable.provider)
            .where { OAuthIdentitiesTable.userId inList userIds }
            .groupBy({ it[OAuthIdentitiesTable.userId] }, { it[OAuthIdentitiesTable.provider] })
            .mapValues { (_, names) -> names.mapNotNull { name -> OAuthProvider.entries.firstOrNull { it.name == name } }.distinct().sorted() }
    }

    @OptIn(ExperimentalTime::class)
    private fun detailOf(id: String): PlayerDetailDto? {
        val user = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull() ?: return null
        val now = clock.instant()

        val stats = UserStatsTable.selectAll().where { UserStatsTable.userId eq id }.singleOrNull()
        val records = stats?.let { decodeRecords(id, it[UserStatsTable.data]) }.orEmpty()
        val nowForDays = kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilli())
        val languages = records.map { it.language }.distinct().sorted().map { lang ->
            // The app's own rules: the current streak is judged against the language's current puzzle day.
            val summary = Streaks.stats(records, lang)
            val streak = Streaks.streak(records, lang, today = PuzzleDays.epochDay(lang, nowForDays))
            PlayerLanguageStatsDto(
                lang = lang,
                games = summary.played,
                wins = records.count { it.language == lang && it.won },
                winRate = summary.winRate,
                currentStreak = streak.current,
                bestStreak = streak.best,
            )
        }

        val activeSessions = RefreshTokensTable.selectAll().where {
            (RefreshTokensTable.userId eq id) and
                RefreshTokensTable.revokedAt.isNull() and
                RefreshTokensTable.replacedBy.isNull() and
                (RefreshTokensTable.expiresAt greater now)
        }.count()

        val suggestionCount = WordSuggestionsTable.id.count()
        val counts = WordSuggestionsTable.select(WordSuggestionsTable.status, suggestionCount)
            .where { WordSuggestionsTable.suggestedBy eq id }
            .groupBy(WordSuggestionsTable.status)
            .associate { it[WordSuggestionsTable.status] to it[suggestionCount] }
        val recent = WordSuggestionsTable.selectAll()
            .where { WordSuggestionsTable.suggestedBy eq id }
            .orderBy(WordSuggestionsTable.createdAt to SortOrder.DESC, WordSuggestionsTable.id to SortOrder.DESC)
            .limit(PlayerParams.RECENT_SUGGESTIONS)
            .map { row ->
                PlayerSuggestionDto(
                    id = row[WordSuggestionsTable.id],
                    lang = row[WordSuggestionsTable.lang],
                    word = row[WordSuggestionsTable.word],
                    status = SuggestionStatus.valueOf(row[WordSuggestionsTable.status]),
                    createdAt = row[WordSuggestionsTable.createdAt].toEpochMilli(),
                    decidedAt = row[WordSuggestionsTable.decidedAt]?.toEpochMilli(),
                )
            }

        val block = user[UsersTable.suggestionsBlockedAt]?.let { blockedAt ->
            val staffId = user[UsersTable.suggestionsBlockedBy].orEmpty()
            val staff = StaffTable.select(StaffTable.username, StaffTable.displayName).where { StaffTable.id eq staffId }.singleOrNull()
            SuggestionBlockDto(
                blockedAt = blockedAt.toEpochMilli(),
                blockedBy = StaffRefDto(staffId, staff?.get(StaffTable.username) ?: staffId, staff?.get(StaffTable.displayName)),
            )
        }

        return PlayerDetailDto(
            id = id,
            createdAt = user[UsersTable.createdAt].toEpochMilli(),
            displayName = user[UsersTable.name]?.takeIf { it.isNotBlank() },
            providers = providersOf(listOf(id))[id].orEmpty(),
            lastStatsSnapshotAt = stats?.get(UserStatsTable.updatedAt)?.toEpochMilli(),
            activeSessions = activeSessions,
            languages = languages,
            suggestionCounts = SuggestionCountsDto(
                pending = counts[SuggestionStatus.PENDING.name] ?: 0,
                accepted = counts[SuggestionStatus.ACCEPTED.name] ?: 0,
                rejected = counts[SuggestionStatus.REJECTED.name] ?: 0,
            ),
            recentSuggestions = recent,
            suggestionBlock = block,
        )
    }

    private fun decodeRecords(id: String, data: String): List<ResultRecordDto> = try {
        json.decodeFromString<List<ResultRecordDto>>(data)
    } catch (e: SerializationException) {
        // The server stored it from a validated upload; an unreadable snapshot shows as no stats rather than an error.
        System.err.println("Player $id: unreadable stats snapshot (${e.message})")
        emptyList()
    }

    private companion object {
        /** The editors' clock (also the daily report's): creation-date filters are Asia/Tashkent days. */
        val EDITORS_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

        /** The LIKE escape character (Postgres' default), for the literal parts of a name search. */
        const val LIKE_ESCAPE = '\\'
    }
}

/** Where a search page ended: the last account's `(created_at, id)`, opaque to clients (base64url). */
internal data class SearchCursor(val createdAt: Instant, val id: String) {
    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${createdAt.epochSecond}:${createdAt.nano}:$id".toByteArray(Charsets.UTF_8))

    companion object {
        fun decode(raw: String): SearchCursor? = try {
            val parts = String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8).split(":", limit = 3)
            if (parts.size != 3 || parts[2].isEmpty()) null
            else SearchCursor(Instant.ofEpochSecond(parts[0].toLong(), parts[1].toLong()), parts[2])
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.time.DateTimeException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }
}
