package uz.abumme.harfgame.data.admin

/** `ApiErrorResponse.error` codes of the admin API, one per HTTP status it uses. */
object AdminErrors {
    const val UNAUTHORIZED = "unauthorized" // 401
    const val FORBIDDEN = "forbidden" // 403
    const val NOT_FOUND = "not_found" // 404
    const val CONFLICT = "conflict" // 409
    const val VALIDATION_FAILED = "validation_failed" // 422
    const val LOCKED = "locked" // 423
    const val RATE_LIMITED = "rate_limited" // 429
}

/**
 * A validation or conflict failure tied to one request field. It travels in `ApiErrorResponse.message` as
 * `field: reason`, so the envelope stays unchanged and the panel can show the error next to the field.
 */
data class FieldError(val field: String, val reason: String) {
    fun toMessage(): String = "$field: $reason"

    companion object {
        private val pattern = Regex("""^([A-Za-z][A-Za-z0-9]*): (\S.*)$""")

        /** The field error carried by [message], or null when the message names no field. */
        fun parse(message: String?): FieldError? =
            message?.let { pattern.matchEntire(it) }?.let { FieldError(it.groupValues[1], it.groupValues[2]) }
    }
}

/** The `reason` vocabulary of [FieldError]; later changes add their own. */
object FieldReasons {
    const val REQUIRED = "required"
    const val INVALID = "invalid"
    const val TOO_LONG = "too_long"

    /** Outside the allowed length range (passwords: [StaffRules.PASSWORD_MIN]..[StaffRules.PASSWORD_MAX]). */
    const val LENGTH = "length"
    const val TAKEN = "taken"
    const val UNKNOWN = "unknown"
    const val WRONG = "wrong"

    /** The change would leave no active ADMIN. */
    const val LAST_ADMIN = "last_admin"
}
