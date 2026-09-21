package uz.abumme.harfgame.data.admin.staff

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role

@Serializable
enum class StaffStatus { ACTIVE, DISABLED }

/**
 * A staff account as ADMINs see it; never carries password material. Times are epoch milliseconds (UTC).
 * [languages] are the stored assignments, kept for an ADMIN so a later demotion can restore them.
 */
@Serializable
data class StaffDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val role: Role,
    val status: StaffStatus,
    val languages: List<String>,
    val telegramUserId: Long? = null,
    /** Set while sign-in is blocked after repeated failures. */
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CreateStaffRequest(
    val username: String,
    val password: String,
    val role: Role,
    /** Required (at least one) for a WORDER. */
    val languages: List<String> = emptyList(),
    val displayName: String? = null,
    val telegramUserId: Long? = null,
)

/** PATCH body: absent fields stay unchanged; an explicit `null` clears [displayName] or [telegramUserId]. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateStaffRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val displayName: Patch<String?> = Patch.Unchanged,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val role: Patch<Role> = Patch.Unchanged,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val languages: Patch<List<String>> = Patch.Unchanged,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val telegramUserId: Patch<Long?> = Patch.Unchanged,
)

@Serializable
data class ResetPasswordRequest(
    val newPassword: String,
)
