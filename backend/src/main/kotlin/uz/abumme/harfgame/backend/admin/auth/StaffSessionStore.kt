package uz.abumme.harfgame.backend.admin.auth

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffSessionsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.security.TokenUtils
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Server-side staff sessions. The browser holds a random 256-bit token; only its SHA-256 is stored. A session ends
 * when revoked, [ABSOLUTE_LIFETIME] after sign-in, after [IDLE_TIMEOUT] without requests, or as soon as its staff
 * member is disabled.
 *
 * [create] and the revoke functions write inside the caller's transaction, so they commit together with the action
 * that triggered them (sign-in, password change, disable).
 */
class StaffSessionStore(private val clock: Clock) {

    class NewSession(val id: String, val token: String, val expiresAt: Instant)

    /** Starts a session for [staffId]. Call inside a transaction. */
    fun create(staffId: String, ip: String?, userAgent: String?): NewSession {
        val token = TokenUtils.generateSecureToken() // 32 random bytes
        val now = clock.instant()
        val id = UUID.randomUUID().toString()
        val expires = now.plus(ABSOLUTE_LIFETIME)
        StaffSessionsTable.insert {
            it[StaffSessionsTable.id] = id
            it[StaffSessionsTable.staffId] = staffId
            it[tokenHash] = TokenUtils.hashToken(token)
            it[createdAt] = now
            it[lastSeenAt] = now
            it[expiresAt] = expires
            it[StaffSessionsTable.ip] = ip?.take(64)
            it[StaffSessionsTable.userAgent] = userAgent?.take(256)
        }
        return NewSession(id, token, expires)
    }

    /**
     * The staff member signed in with [token], with role and languages as stored right now; null when the session is
     * unknown, revoked, past its absolute lifetime, idle too long, or its staff member is disabled.
     */
    suspend fun resolve(token: String): StaffPrincipal? = DatabaseFactory.dbQuery {
        val now = clock.instant()
        val row = StaffSessionsTable
            .join(StaffTable, JoinType.INNER, StaffSessionsTable.staffId, StaffTable.id)
            .selectAll()
            .where { StaffSessionsTable.tokenHash eq TokenUtils.hashToken(token) }
            .singleOrNull()
            ?: return@dbQuery null

        val lastSeen = row[StaffSessionsTable.lastSeenAt]
        val usable = row[StaffSessionsTable.revokedAt] == null &&
            now.isBefore(row[StaffSessionsTable.expiresAt]) &&
            Duration.between(lastSeen, now) <= IDLE_TIMEOUT &&
            row[StaffTable.status] == StaffStatus.ACTIVE.name
        if (!usable) return@dbQuery null

        val sessionId = row[StaffSessionsTable.id]
        // Throttled so a burst of requests doesn't turn into a write each.
        if (Duration.between(lastSeen, now) >= LAST_SEEN_WRITE_INTERVAL) {
            StaffSessionsTable.update({ StaffSessionsTable.id eq sessionId }) { it[lastSeenAt] = now }
        }

        val staffId = row[StaffTable.id]
        StaffPrincipal(
            staffId = staffId,
            username = row[StaffTable.username],
            displayName = row[StaffTable.displayName],
            role = Role.valueOf(row[StaffTable.role]),
            assignedLanguages = languagesOf(staffId),
            sessionId = sessionId,
        )
    }

    /** Ends one session. Call inside a transaction. */
    fun revoke(sessionId: String) {
        StaffSessionsTable.update({ (StaffSessionsTable.id eq sessionId) and StaffSessionsTable.revokedAt.isNull() }) {
            it[revokedAt] = clock.instant()
        }
    }

    /** Ends every session of [staffId]. Call inside a transaction. */
    fun revokeAll(staffId: String) {
        StaffSessionsTable.update({ (StaffSessionsTable.staffId eq staffId) and StaffSessionsTable.revokedAt.isNull() }) {
            it[revokedAt] = clock.instant()
        }
    }

    /** Ends every session of [staffId] except [keepSessionId]. Call inside a transaction. */
    fun revokeAllExcept(staffId: String, keepSessionId: String) {
        StaffSessionsTable.update({
            (StaffSessionsTable.staffId eq staffId) and (StaffSessionsTable.id neq keepSessionId) and
                StaffSessionsTable.revokedAt.isNull()
        }) {
            it[revokedAt] = clock.instant()
        }
    }

    /** Deletes sessions that expired or were revoked more than [RETENTION] ago; returns how many. */
    suspend fun deleteStale(): Int = DatabaseFactory.dbQuery {
        val cutoff = clock.instant().minus(RETENTION)
        StaffSessionsTable.deleteWhere {
            (StaffSessionsTable.expiresAt less cutoff) or
                (StaffSessionsTable.revokedAt.isNotNull() and (StaffSessionsTable.revokedAt less cutoff))
        }
    }

    companion object {
        val ABSOLUTE_LIFETIME: Duration = Duration.ofDays(7)
        val IDLE_TIMEOUT: Duration = Duration.ofHours(12)
        val LAST_SEEN_WRITE_INTERVAL: Duration = Duration.ofMinutes(1)
        val RETENTION: Duration = Duration.ofDays(7)

        /** Assigned languages of [staffId]. Call inside a transaction. */
        fun languagesOf(staffId: String): Set<String> =
            StaffLanguagesTable.select(StaffLanguagesTable.lang)
                .where { StaffLanguagesTable.staffId eq staffId }
                .map { it[StaffLanguagesTable.lang] }
                .toSet()
    }
}
