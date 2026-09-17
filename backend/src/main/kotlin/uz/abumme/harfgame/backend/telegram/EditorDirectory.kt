package uz.abumme.harfgame.backend.telegram

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffLanguagesTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.StaffStatus

/** An editor allowed to decide a suggestion from Telegram. */
sealed interface Editor {
    /** An active staff member whose linked Telegram user ID tapped, within their languages. */
    data class Staff(val staffId: String, val name: String) : Editor

    /** A Telegram user on the legacy `TELEGRAM_EDITOR_IDS` allowlist: every language, until editors are linked to staff. */
    data class Legacy(val telegramUserId: Long) : Editor
}

/**
 * Decides who may act on a Telegram decision tap. Staff are looked up on every tap, so role, status, language and link
 * changes apply to the next tap. [legacyEditorIds] keeps the old allowlist working during the migration to staff.
 */
class EditorDirectory(private val legacyEditorIds: Set<Long> = emptySet()) {

    val hasLegacyEditors: Boolean get() = legacyEditorIds.isNotEmpty()

    /**
     * The editor [telegramUserId] acts as for a suggestion in [lang]: an active staff member linked to that Telegram
     * user who is an ADMIN or has [lang]; else a legacy allowlisted user (any language); else null.
     */
    suspend fun authorize(telegramUserId: Long, lang: String): Editor? {
        val staff = DatabaseFactory.dbQuery {
            val row = StaffTable.selectAll()
                .where { (StaffTable.telegramUserId eq telegramUserId) and (StaffTable.status eq StaffStatus.ACTIVE.name) }
                .singleOrNull() ?: return@dbQuery null
            val staffId = row[StaffTable.id]
            val inScope = row[StaffTable.role] == Role.ADMIN.name ||
                StaffLanguagesTable.selectAll()
                    .where { (StaffLanguagesTable.staffId eq staffId) and (StaffLanguagesTable.lang eq lang) }
                    .any()
            if (inScope) Editor.Staff(staffId, row[StaffTable.displayName] ?: row[StaffTable.username]) else null
        }
        return staff ?: Editor.Legacy(telegramUserId).takeIf { telegramUserId in legacyEditorIds }
    }
}
