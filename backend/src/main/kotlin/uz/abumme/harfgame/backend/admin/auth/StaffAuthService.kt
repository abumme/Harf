package uz.abumme.harfgame.backend.admin.auth

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.access.permissions
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import java.sql.Connection
import java.time.Clock
import java.time.Duration

sealed interface LoginResult {
    class Success(val me: MeDto, val sessionToken: String) : LoginResult

    /** Unknown username, wrong password or disabled account: one generic answer for all three. */
    data object InvalidCredentials : LoginResult

    /** Too many consecutive failures; sign-in is blocked until the lock expires. */
    data object Locked : LoginResult
}

/**
 * Staff sign-in, sign-out, "me" and own password change.
 *
 * Lockout lives on the staff row: every failure increments `failed_login_count` atomically, the
 * [MAX_FAILURES]th locks the account for [LOCK_DURATION] and resets the count, and a success resets it. The
 * counter update and its audit entries share one transaction.
 *
 * Failed attempts are recorded with the SYSTEM as actor and the account as target: nobody proved to be that staff
 * member. Successful sign-ins, sign-outs and password changes have the member as actor.
 */
class StaffAuthService(
    private val clock: Clock,
    private val hasher: PasswordHasher,
    private val sessions: StaffSessionStore,
    private val audit: AuditLog,
    /** Languages that have a word pack: every language an ADMIN has. */
    private val packLanguages: suspend () -> List<String>,
) {

    suspend fun login(rawUsername: String, password: String, ip: String?, userAgent: String?): LoginResult {
        val username = StaffRules.normalizeUsername(rawUsername)
        val account = DatabaseFactory.dbQuery {
            StaffTable.selectAll().where { StaffTable.username eq username }.singleOrNull()?.let {
                Account(
                    id = it[StaffTable.id],
                    status = StaffStatus.valueOf(it[StaffTable.status]),
                    passwordHash = it[StaffTable.passwordHash],
                    lockedUntil = it[StaffTable.lockedUntil],
                )
            }
        }

        if (account == null) {
            // Same cost as a real account, so response time doesn't reveal which usernames exist.
            hasher.verifyDummy(password)
            DatabaseFactory.dbQuery {
                // No target and never the attempted username: only the client address.
                audit.record(AuditActor.System, AuditActions.AUTH_LOGIN_FAILED, details = auditDetails { fact("ip", jsonOf(ip)) })
            }
            return LoginResult.InvalidCredentials
        }

        if (account.lockedUntil?.isAfter(clock.instant()) == true) {
            // Refused without checking the password.
            DatabaseFactory.dbQuery {
                audit.record(
                    AuditActor.System, AuditActions.AUTH_LOGIN_FAILED, AuditTargets.STAFF, account.id,
                    details = auditDetails { fact("reason", jsonOf("locked")); fact("ip", jsonOf(ip)) },
                )
            }
            return LoginResult.Locked
        }

        val passwordMatches = hasher.verify(password, account.passwordHash)

        if (account.status != StaffStatus.ACTIVE) {
            DatabaseFactory.dbQuery {
                audit.record(
                    AuditActor.System, AuditActions.AUTH_LOGIN_FAILED, AuditTargets.STAFF, account.id,
                    details = auditDetails { fact("reason", jsonOf("disabled")); fact("ip", jsonOf(ip)) },
                )
            }
            return LoginResult.InvalidCredentials
        }

        if (!passwordMatches) return recordFailure(account.id, ip)

        val upgradedHash = if (hasher.needsRehash(account.passwordHash)) hasher.hash(password) else null
        return DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            val row = StaffTable.selectAll().where { StaffTable.id eq account.id }
                .forUpdate(ForUpdateOption.ForUpdate).single()
            // Disabled or locked between the check and now: refuse like any other attempt at that moment.
            if (row[StaffTable.status] != StaffStatus.ACTIVE.name) return@dbQuery LoginResult.InvalidCredentials
            if (row[StaffTable.lockedUntil]?.isAfter(clock.instant()) == true) return@dbQuery LoginResult.Locked

            val now = clock.instant()
            StaffTable.update({ StaffTable.id eq account.id }) {
                it[failedLoginCount] = 0
                it[lockedUntil] = null
                it[lastLoginAt] = now
                if (upgradedHash != null && row[StaffTable.passwordHash] == account.passwordHash) {
                    it[passwordHash] = upgradedHash
                }
            }
            val session = sessions.create(account.id, ip, userAgent)
            audit.record(
                AuditActor.Staff(account.id), AuditActions.AUTH_LOGIN_SUCCEEDED, AuditTargets.STAFF, account.id,
                details = auditDetails { fact("ip", jsonOf(ip)) },
            )
            val principal = StaffPrincipal(
                staffId = account.id,
                username = row[StaffTable.username],
                displayName = row[StaffTable.displayName],
                role = Role.valueOf(row[StaffTable.role]),
                assignedLanguages = StaffSessionStore.languagesOf(account.id),
                sessionId = session.id,
            )
            LoginResult.Success(meOf(principal), session.token)
        }.let { result ->
            // The pack-language lookup for an ADMIN's `me` runs outside the sign-in transaction.
            if (result is LoginResult.Success) LoginResult.Success(completeLanguages(result.me), result.sessionToken) else result
        }
    }

    private suspend fun recordFailure(staffId: String, ip: String?): LoginResult =
        DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            // Atomic increment: concurrent failures never lose a count.
            val failures = StaffTable.updateReturning(listOf(StaffTable.failedLoginCount), { StaffTable.id eq staffId }) {
                it.update(StaffTable.failedLoginCount, StaffTable.failedLoginCount + 1)
            }.single()[StaffTable.failedLoginCount]
            audit.record(
                AuditActor.System, AuditActions.AUTH_LOGIN_FAILED, AuditTargets.STAFF, staffId,
                details = auditDetails { fact("reason", jsonOf("wrong_password")); fact("ip", jsonOf(ip)) },
            )
            if (failures >= MAX_FAILURES) {
                val until = clock.instant().plus(LOCK_DURATION)
                StaffTable.update({ StaffTable.id eq staffId }) {
                    it[failedLoginCount] = 0
                    it[lockedUntil] = until
                }
                audit.record(
                    AuditActor.System, AuditActions.AUTH_ACCOUNT_LOCKED, AuditTargets.STAFF, staffId,
                    details = auditDetails { fact("lockedUntil", jsonOf(until.toString())) },
                )
                LoginResult.Locked
            } else {
                LoginResult.InvalidCredentials
            }
        }

    suspend fun logout(principal: StaffPrincipal) = DatabaseFactory.dbQuery {
        sessions.revoke(principal.sessionId)
        audit.record(AuditActor.Staff(principal.staffId), AuditActions.AUTH_LOGGED_OUT, AuditTargets.STAFF, principal.staffId)
    }

    suspend fun me(principal: StaffPrincipal): MeDto = completeLanguages(meOf(principal))

    /**
     * Changes the caller's own password. A wrong current password or an out-of-range new one answers
     * `422 validation_failed` (never 401, which the panel treats as an ended session) and changes nothing. Every
     * other session of the member ends; the calling one stays signed in.
     */
    suspend fun changePassword(principal: StaffPrincipal, request: ChangePasswordRequest) {
        val currentHash = DatabaseFactory.dbQuery {
            StaffTable.select(StaffTable.passwordHash).where { StaffTable.id eq principal.staffId }.single()[StaffTable.passwordHash]
        }
        if (!hasher.verify(request.currentPassword, currentHash)) {
            throw AdminApiException.validation("currentPassword", FieldReasons.WRONG)
        }
        if (!StaffRules.isValidPassword(request.newPassword)) {
            throw AdminApiException.validation("newPassword", FieldReasons.LENGTH)
        }
        val newHash = hasher.hash(request.newPassword)
        DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            val now = clock.instant()
            StaffTable.update({ StaffTable.id eq principal.staffId }) {
                it[passwordHash] = newHash
                it[passwordChangedAt] = now
                it[updatedAt] = now
            }
            sessions.revokeAllExcept(principal.staffId, principal.sessionId)
            audit.record(AuditActor.Staff(principal.staffId), AuditActions.AUTH_PASSWORD_CHANGED, AuditTargets.STAFF, principal.staffId)
        }
    }

    private fun meOf(principal: StaffPrincipal) = MeDto(
        id = principal.staffId,
        username = principal.username,
        displayName = principal.displayName,
        role = principal.role,
        languages = principal.assignedLanguages.sorted(),
        permissions = principal.role.permissions,
    )

    /** An ADMIN has every pack language; a WORDER keeps the assigned ones that still have a pack. */
    private suspend fun completeLanguages(me: MeDto): MeDto {
        val available = packLanguages()
        val languages = if (me.role == Role.ADMIN) available else available.filter { it in me.languages }
        return me.copy(languages = languages)
    }

    private class Account(
        val id: String,
        val status: StaffStatus,
        val passwordHash: String,
        val lockedUntil: java.time.Instant?,
    )

    companion object {
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
