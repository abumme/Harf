package uz.abumme.harfgame.data.admin.auth

import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * The signed-in staff member. [languages] are the assigned languages for a WORDER and every pack language for an
 * ADMIN; [permissions] is the member's effective set, computed by the server.
 */
@Serializable
data class MeDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val role: Role,
    val languages: List<String>,
    val permissions: Set<Permission>,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
