package uz.abumme.harfgame.backend.admin.auth

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import java.sql.Connection
import java.time.Clock
import java.util.UUID

/**
 * Creates the first ADMIN, or recovers one, from `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` at startup.
 * Acts only while no ACTIVE ADMIN exists: creates that ADMIN, or makes an existing account with that username an
 * active ADMIN with that password (lock cleared, sessions ended). Never throws for bad configuration, because the
 * player API must keep serving.
 */
class StaffBootstrap(
    private val clock: Clock,
    private val hasher: PasswordHasher,
    private val sessions: StaffSessionStore,
    private val audit: AuditLog,
) {
    enum class Outcome { NOT_CONFIGURED, ACTIVE_ADMIN_EXISTS, INVALID_CONFIGURATION, CREATED, RECOVERED }

    suspend fun run(rawUsername: String?, password: String?): Outcome {
        if (rawUsername.isNullOrBlank() || password.isNullOrEmpty()) return Outcome.NOT_CONFIGURED

        if (activeAdminExists()) {
            log.warn("An active ADMIN exists; ADMIN_BOOTSTRAP_USERNAME/ADMIN_BOOTSTRAP_PASSWORD are ignored. Remove them from the environment.")
            return Outcome.ACTIVE_ADMIN_EXISTS
        }
        val username = StaffRules.normalizeUsername(rawUsername)
        if (!StaffRules.isValidUsername(username)) {
            log.error("ADMIN_BOOTSTRAP_USERNAME must be ${StaffRules.USERNAME_MIN}-${StaffRules.USERNAME_MAX} characters of a-z, 0-9, '.', '_' or '-'; no ADMIN was bootstrapped.")
            return Outcome.INVALID_CONFIGURATION
        }
        if (!StaffRules.isValidPassword(password)) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD must be ${StaffRules.PASSWORD_MIN}-${StaffRules.PASSWORD_MAX} characters; no ADMIN was bootstrapped.")
            return Outcome.INVALID_CONFIGURATION
        }

        val hash = hasher.hash(password)
        val outcome = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            if (activeAdminExistsInTransaction()) return@dbQuery Outcome.ACTIVE_ADMIN_EXISTS
            val now = clock.instant()
            val existing = StaffTable.selectAll().where { StaffTable.username eq username }.singleOrNull()
            if (existing == null) {
                val id = UUID.randomUUID().toString()
                StaffTable.insert {
                    it[StaffTable.id] = id
                    it[StaffTable.username] = username
                    it[displayName] = null
                    it[role] = Role.ADMIN.name
                    it[status] = StaffStatus.ACTIVE.name
                    it[passwordHash] = hash
                    it[passwordChangedAt] = now
                    it[failedLoginCount] = 0
                    it[lockedUntil] = null
                    it[telegramUserId] = null
                    it[createdAt] = now
                    it[createdBy] = null
                    it[updatedAt] = now
                    it[lastLoginAt] = null
                }
                audit.record(
                    AuditActor.System, AuditActions.STAFF_BOOTSTRAPPED, AuditTargets.STAFF, id,
                    details = auditDetails {
                        fact("mode", jsonOf("created"))
                        change("role", jsonOf(null as String?), jsonOf(Role.ADMIN.name))
                    },
                )
                Outcome.CREATED
            } else {
                val id = existing[StaffTable.id]
                StaffTable.update({ StaffTable.id eq id }) {
                    it[role] = Role.ADMIN.name
                    it[status] = StaffStatus.ACTIVE.name
                    it[passwordHash] = hash
                    it[passwordChangedAt] = now
                    it[failedLoginCount] = 0
                    it[lockedUntil] = null
                    it[updatedAt] = now
                }
                sessions.revokeAll(id)
                audit.record(
                    AuditActor.System, AuditActions.STAFF_BOOTSTRAPPED, AuditTargets.STAFF, id,
                    details = auditDetails {
                        fact("mode", jsonOf("recovered"))
                        changeIfDifferent("role", jsonOf(existing[StaffTable.role]), jsonOf(Role.ADMIN.name))
                        changeIfDifferent("status", jsonOf(existing[StaffTable.status]), jsonOf(StaffStatus.ACTIVE.name))
                    },
                )
                Outcome.RECOVERED
            }
        }
        when (outcome) {
            Outcome.CREATED -> log.info("Bootstrapped ADMIN '$username'. Remove ADMIN_BOOTSTRAP_* from the environment after signing in.")
            Outcome.RECOVERED -> log.info("Recovered '$username' as an active ADMIN. Remove ADMIN_BOOTSTRAP_* from the environment after signing in.")
            else -> {}
        }
        return outcome
    }

    private suspend fun activeAdminExists(): Boolean = DatabaseFactory.dbQuery { activeAdminExistsInTransaction() }

    private fun activeAdminExistsInTransaction(): Boolean =
        StaffTable.selectAll()
            .where { (StaffTable.role eq Role.ADMIN.name) and (StaffTable.status eq StaffStatus.ACTIVE.name) }
            .any()

    private companion object {
        val log = LoggerFactory.getLogger(StaffBootstrap::class.java)
    }
}
