package uz.abumme.harfgame.admin.forms

import uz.abumme.harfgame.admin.FormReasons
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.ClientErrors
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import uz.abumme.harfgame.data.api.ApiResult

/** Field name -> reason; empty when the form may be submitted. */
typealias FieldErrors = Map<String, String>

/** How a failed request is shown on a form: next to one of its fields, or as a message above it. */
data class FormFailure(val fieldErrors: FieldErrors, val message: String?)

/**
 * Places a server error: a `field: reason` naming one of [fields] goes next to that field, anything else becomes a
 * message for the whole form.
 */
fun formFailure(error: ApiResult.Error, fields: Set<String>): FormFailure {
    val field = error.fieldError()
    if (field != null && field.field in fields) return FormFailure(mapOf(field.field to field.reason), null)
    return FormFailure(emptyMap(), generalMessage(error, field))
}

/** A message for errors that belong to no field. */
fun generalMessage(error: ApiResult.Error, field: FieldError? = error.fieldError()): String = when {
    field != null -> Strings.fieldReason(field.field, field.reason)
    error.code == ClientErrors.NETWORK -> Strings.Common.NETWORK_ERROR
    error.code == AdminErrors.FORBIDDEN -> Strings.Common.FORBIDDEN_ACTION
    else -> Strings.Common.SERVER_ERROR
}

/** The login page's message for a refused sign-in. */
fun loginMessage(error: ApiResult.Error): String = when (error.code) {
    AdminErrors.UNAUTHORIZED -> Strings.Login.INVALID
    AdminErrors.LOCKED -> Strings.Login.LOCKED
    AdminErrors.RATE_LIMITED -> Strings.Login.RATE_LIMITED
    ClientErrors.NETWORK -> Strings.Common.NETWORK_ERROR
    else -> Strings.Common.SERVER_ERROR
}

/** The staff create/edit form. The username and password are only used when creating. */
data class StaffFormState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val role: Role = Role.WORDER,
    val languages: Set<String> = emptySet(),
    val telegramUserId: String = "",
) {
    companion object {
        val CREATE_FIELDS = setOf("username", "password", "displayName", "role", "languages", "telegramUserId")
        val EDIT_FIELDS = setOf("displayName", "role", "languages", "telegramUserId")

        fun from(staff: StaffDto) = StaffFormState(
            username = staff.username,
            displayName = staff.displayName.orEmpty(),
            role = staff.role,
            languages = staff.languages.toSet(),
            telegramUserId = staff.telegramUserId?.toString().orEmpty(),
        )
    }

    /** The same rules the server applies, checked before sending. */
    fun validate(creating: Boolean): FieldErrors = buildMap {
        if (creating) {
            val normalized = StaffRules.normalizeUsername(username)
            if (normalized.isEmpty()) put("username", FieldReasons.REQUIRED)
            else if (!StaffRules.isValidUsername(normalized)) put("username", FieldReasons.INVALID)
            if (password.isEmpty()) put("password", FieldReasons.REQUIRED)
            else if (!StaffRules.isValidPassword(password)) put("password", FieldReasons.LENGTH)
        }
        if (displayName.trim().length > StaffRules.DISPLAY_NAME_MAX) put("displayName", FieldReasons.TOO_LONG)
        if (role == Role.WORDER && languages.isEmpty()) put("languages", FieldReasons.REQUIRED)
        if (telegramUserId.isNotBlank() && parsedTelegramId() == null) put("telegramUserId", FieldReasons.INVALID)
    }

    fun toCreateRequest() = CreateStaffRequest(
        username = StaffRules.normalizeUsername(username),
        password = password,
        role = role,
        languages = languages.sorted(),
        displayName = displayName.trim().ifEmpty { null },
        telegramUserId = parsedTelegramId(),
    )

    /** Only the fields that differ from [original]; a cleared name or Telegram ID is sent as an explicit null. */
    fun toUpdateRequest(original: StaffDto) = UpdateStaffRequest(
        displayName = displayName.trim().ifEmpty { null }.let { if (it == original.displayName) Patch.Unchanged else Patch.Set(it) },
        role = if (role == original.role) Patch.Unchanged else Patch.Set(role),
        languages = if (languages == original.languages.toSet()) Patch.Unchanged else Patch.Set(languages.sorted()),
        telegramUserId = parsedTelegramId().let { if (it == original.telegramUserId) Patch.Unchanged else Patch.Set(it) },
    )

    private fun parsedTelegramId(): Long? =
        telegramUserId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
}

/** The own-password form: the new password is typed twice. */
data class PasswordChangeState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val repeatPassword: String = "",
) {
    fun validate(): FieldErrors = buildMap {
        if (currentPassword.isEmpty()) put("currentPassword", FieldReasons.REQUIRED)
        if (newPassword.isEmpty()) put("newPassword", FieldReasons.REQUIRED)
        else if (!StaffRules.isValidPassword(newPassword)) put("newPassword", FieldReasons.LENGTH)
        if (repeatPassword != newPassword) put("repeatPassword", FormReasons.MISMATCH)
    }

    val canSubmit: Boolean get() = validate().isEmpty()

    companion object {
        val FIELDS = setOf("currentPassword", "newPassword", "repeatPassword")
    }
}
