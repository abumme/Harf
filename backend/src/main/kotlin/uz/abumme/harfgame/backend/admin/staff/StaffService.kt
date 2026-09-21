package uz.abumme.harfgame.backend.admin.staff

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.admin.auth.PasswordHasher
import uz.abumme.harfgame.backend.admin.auth.StaffSessionStore
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import uz.abumme.harfgame.data.admin.valueOr
import java.sql.Connection
import java.time.Clock
import java.util.UUID

/**
 * ADMIN management of staff accounts: list, create, edit, reset a password, disable and enable. Accounts are never
 * deleted. Every mutation writes its audit entry in the same transaction and refuses (changing nothing) with a
 * `field: reason` message when a rule is broken.
 *
 * Mutations run at READ COMMITTED and take row locks: the last-ADMIN guard locks every active ADMIN row (in id
 * order, always before the target row) so two concurrent demotions or disables cannot both pass.
 */
class StaffService(
    private val clock: Clock,
    private val hasher: PasswordHasher,
    private val sessions: StaffSessionStore,
    private val audit: AuditLog,
    /** Languages that have a word pack: the only languages a staff member can be assigned. */
    private val packLanguages: suspend () -> List<String>,
) {

    suspend fun list(): List<StaffDto> = DatabaseFactory.dbQuery {
        val languages = StaffLanguagesTable.selectAll().groupBy({ it[StaffLanguagesTable.staffId] }, { it[StaffLanguagesTable.lang] })
        StaffTable.selectAll().orderBy(StaffTable.username, SortOrder.ASC).map { row ->
            row.toDto(languages[row[StaffTable.id]].orEmpty())
        }
    }

    suspend fun get(id: String): StaffDto = DatabaseFactory.dbQuery { loadDto(id) }

    suspend fun create(actor: StaffPrincipal, request: CreateStaffRequest): StaffDto {
        val username = StaffRules.normalizeUsername(request.username)
        if (!StaffRules.isValidUsername(username)) throw AdminApiException.validation("username", FieldReasons.INVALID)
        if (!StaffRules.isValidPassword(request.password)) throw AdminApiException.validation("password", FieldReasons.LENGTH)
        val displayName = validDisplayName(request.displayName)
        val languages = validLanguages(request.role, request.languages)

        val hash = hasher.hash(request.password)
        return uniqueViolationsAsConflicts {
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                if (StaffTable.selectAll().where { StaffTable.username eq username }.any()) {
                    throw AdminApiException.conflict("username", FieldReasons.TAKEN)
                }
                request.telegramUserId?.let(::requireTelegramIdFree)

                val id = UUID.randomUUID().toString()
                val now = clock.instant()
                StaffTable.insert {
                    it[StaffTable.id] = id
                    it[StaffTable.username] = username
                    it[StaffTable.displayName] = displayName
                    it[role] = request.role.name
                    it[status] = StaffStatus.ACTIVE.name
                    it[passwordHash] = hash
                    it[passwordChangedAt] = now
                    it[failedLoginCount] = 0
                    it[lockedUntil] = null
                    it[telegramUserId] = request.telegramUserId
                    it[createdAt] = now
                    it[createdBy] = actor.staffId
                    it[updatedAt] = now
                    it[lastLoginAt] = null
                }
                replaceLanguages(id, languages)
                audit.record(
                    AuditActor.Staff(actor.staffId), AuditActions.STAFF_CREATED, AuditTargets.STAFF, id,
                    details = auditDetails {
                        change("username", jsonOf(null as String?), jsonOf(username))
                        change("role", jsonOf(null as String?), jsonOf(request.role.name))
                        change("languages", jsonOf(emptyList()), jsonOf(languages))
                        if (displayName != null) change("displayName", jsonOf(null as String?), jsonOf(displayName))
                        if (request.telegramUserId != null) change("telegramUserId", jsonOf(null as Long?), jsonOf(request.telegramUserId))
                    },
                )
                loadDto(id)
            }
        }
    }

    suspend fun update(actor: StaffPrincipal, id: String, request: UpdateStaffRequest): StaffDto {
        val available = packLanguages()
        return uniqueViolationsAsConflicts {
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                val activeAdmins = lockActiveAdmins()
                val row = lockStaff(id)
                val currentRole = Role.valueOf(row[StaffTable.role])
                val currentLanguages = StaffSessionStore.languagesOf(id)

                val newRole = request.role.valueOr(currentRole)
                val newDisplayName = when (val patch = request.displayName) {
                    Patch.Unchanged -> row[StaffTable.displayName]
                    is Patch.Set -> validDisplayName(patch.value)
                }
                val newLanguages = when (val patch = request.languages) {
                    Patch.Unchanged -> currentLanguages
                    is Patch.Set -> checkLanguagesKnown(patch.value, available)
                }
                if (newRole == Role.WORDER && newLanguages.isEmpty()) {
                    throw AdminApiException.validation("languages", FieldReasons.REQUIRED)
                }
                val newTelegramId = request.telegramUserId.valueOr(row[StaffTable.telegramUserId])
                if (newTelegramId != null && newTelegramId != row[StaffTable.telegramUserId]) {
                    requireTelegramIdFree(newTelegramId)
                }
                val isActiveAdmin = currentRole == Role.ADMIN && row[StaffTable.status] == StaffStatus.ACTIVE.name
                if (isActiveAdmin && newRole != Role.ADMIN && activeAdmins.size <= 1) {
                    throw AdminApiException.conflict("role", FieldReasons.LAST_ADMIN)
                }

                val details = auditDetails {
                    changeIfDifferent("displayName", jsonOf(row[StaffTable.displayName]), jsonOf(newDisplayName))
                    changeIfDifferent("role", jsonOf(currentRole.name), jsonOf(newRole.name))
                    changeIfDifferent("languages", jsonOf(currentLanguages), jsonOf(newLanguages))
                    changeIfDifferent("telegramUserId", jsonOf(row[StaffTable.telegramUserId]), jsonOf(newTelegramId))
                }
                if (details.isNotEmpty()) {
                    StaffTable.update({ StaffTable.id eq id }) {
                        it[displayName] = newDisplayName
                        it[role] = newRole.name
                        it[telegramUserId] = newTelegramId
                        it[updatedAt] = clock.instant()
                    }
                    if (newLanguages != currentLanguages) replaceLanguages(id, newLanguages)
                    audit.record(AuditActor.Staff(actor.staffId), AuditActions.STAFF_UPDATED, AuditTargets.STAFF, id, details = details)
                }
                loadDto(id)
            }
        }
    }

    /** Sets a new password, clears any lock and ends all of the member's sessions. */
    suspend fun resetPassword(actor: StaffPrincipal, id: String, newPassword: String) {
        if (!StaffRules.isValidPassword(newPassword)) throw AdminApiException.validation("newPassword", FieldReasons.LENGTH)
        val hash = hasher.hash(newPassword)
        DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            lockStaff(id)
            val now = clock.instant()
            StaffTable.update({ StaffTable.id eq id }) {
                it[passwordHash] = hash
                it[passwordChangedAt] = now
                it[failedLoginCount] = 0
                it[lockedUntil] = null
                it[updatedAt] = now
            }
            sessions.revokeAll(id)
            audit.record(AuditActor.Staff(actor.staffId), AuditActions.STAFF_PASSWORD_RESET, AuditTargets.STAFF, id)
        }
    }

    /** Disables the account and ends its sessions, unless it is the last active ADMIN. */
    suspend fun disable(actor: StaffPrincipal, id: String): StaffDto = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
        val activeAdmins = lockActiveAdmins()
        val row = lockStaff(id)
        if (row[StaffTable.status] != StaffStatus.DISABLED.name) {
            if (id in activeAdmins && activeAdmins.size <= 1) {
                throw AdminApiException.conflict("status", FieldReasons.LAST_ADMIN)
            }
            StaffTable.update({ StaffTable.id eq id }) {
                it[status] = StaffStatus.DISABLED.name
                it[updatedAt] = clock.instant()
            }
            sessions.revokeAll(id)
            audit.record(
                AuditActor.Staff(actor.staffId), AuditActions.STAFF_DISABLED, AuditTargets.STAFF, id,
                details = auditDetails { change("status", jsonOf(StaffStatus.ACTIVE.name), jsonOf(StaffStatus.DISABLED.name)) },
            )
        }
        loadDto(id)
    }

    /** Re-enables a disabled account; it signs in again with its existing password. */
    suspend fun enable(actor: StaffPrincipal, id: String): StaffDto = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
        val row = lockStaff(id)
        if (row[StaffTable.status] != StaffStatus.ACTIVE.name) {
            StaffTable.update({ StaffTable.id eq id }) {
                it[status] = StaffStatus.ACTIVE.name
                it[updatedAt] = clock.instant()
            }
            audit.record(
                AuditActor.Staff(actor.staffId), AuditActions.STAFF_ENABLED, AuditTargets.STAFF, id,
                details = auditDetails { change("status", jsonOf(StaffStatus.DISABLED.name), jsonOf(StaffStatus.ACTIVE.name)) },
            )
        }
        loadDto(id)
    }

    // --- validation -------------------------------------------------------------------------------------------

    private fun validDisplayName(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > StaffRules.DISPLAY_NAME_MAX) throw AdminApiException.validation("displayName", FieldReasons.TOO_LONG)
        return trimmed
    }

    private suspend fun validLanguages(role: Role, requested: List<String>): Set<String> {
        val languages = checkLanguagesKnown(requested, packLanguages())
        if (role == Role.WORDER && languages.isEmpty()) throw AdminApiException.validation("languages", FieldReasons.REQUIRED)
        return languages
    }

    private fun checkLanguagesKnown(requested: List<String>, available: List<String>): Set<String> {
        val languages = requested.map { it.trim() }.toSet()
        if (languages.any { it !in available }) throw AdminApiException.validation("languages", FieldReasons.UNKNOWN)
        return languages
    }

    // --- persistence helpers (inside a transaction) -------------------------------------------------------------

    /** Locks every active ADMIN row, in id order, and returns their ids. */
    private fun lockActiveAdmins(): List<String> =
        StaffTable.select(StaffTable.id)
            .where { (StaffTable.role eq Role.ADMIN.name) and (StaffTable.status eq StaffStatus.ACTIVE.name) }
            .orderBy(StaffTable.id, SortOrder.ASC)
            .forUpdate(ForUpdateOption.ForUpdate)
            .map { it[StaffTable.id] }

    private fun lockStaff(id: String): ResultRow =
        StaffTable.selectAll().where { StaffTable.id eq id }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
            ?: throw AdminApiException.notFound("No staff account $id")

    private fun requireTelegramIdFree(telegramUserId: Long) {
        if (StaffTable.selectAll().where { StaffTable.telegramUserId eq telegramUserId }.any()) {
            throw AdminApiException.conflict("telegramUserId", FieldReasons.TAKEN)
        }
    }

    private fun replaceLanguages(staffId: String, languages: Set<String>) {
        StaffLanguagesTable.deleteWhere { StaffLanguagesTable.staffId eq staffId }
        StaffLanguagesTable.batchInsert(languages.sorted()) { lang ->
            this[StaffLanguagesTable.staffId] = staffId
            this[StaffLanguagesTable.lang] = lang
        }
    }

    private fun loadDto(id: String): StaffDto {
        val row = StaffTable.selectAll().where { StaffTable.id eq id }.singleOrNull()
            ?: throw AdminApiException.notFound("No staff account $id")
        return row.toDto(StaffSessionStore.languagesOf(id).toList())
    }

    private fun ResultRow.toDto(languages: List<String>): StaffDto {
        val lockedUntil = this[StaffTable.lockedUntil]?.takeIf { it.isAfter(clock.instant()) }
        return StaffDto(
            id = this[StaffTable.id],
            username = this[StaffTable.username],
            displayName = this[StaffTable.displayName],
            role = Role.valueOf(this[StaffTable.role]),
            status = StaffStatus.valueOf(this[StaffTable.status]),
            languages = languages.sorted(),
            telegramUserId = this[StaffTable.telegramUserId],
            lockedUntil = lockedUntil?.toEpochMilli(),
            lastLoginAt = this[StaffTable.lastLoginAt]?.toEpochMilli(),
            createdAt = this[StaffTable.createdAt].toEpochMilli(),
            updatedAt = this[StaffTable.updatedAt].toEpochMilli(),
        )
    }

    /** A concurrent insert can still hit a unique index after the pre-checks; answer it as the same conflict. */
    private suspend fun <T> uniqueViolationsAsConflicts(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ExposedSQLException) {
            if (e.sqlState != UNIQUE_VIOLATION) throw e
            val message = e.message.orEmpty()
            when {
                "idx_staff_username" in message -> throw AdminApiException.conflict("username", FieldReasons.TAKEN)
                "idx_staff_telegram_user_id" in message -> throw AdminApiException.conflict("telegramUserId", FieldReasons.TAKEN)
                else -> throw e
            }
        }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
