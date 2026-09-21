package uz.abumme.harfgame.backend.admin

import io.ktor.http.HttpStatusCode
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.FieldError

/**
 * A refused admin request, answered by StatusPages as `ApiErrorResponse(code, message)` with [status]. Thrown inside
 * a `dbQuery` it also rolls the transaction back, so a refused action changes nothing and leaves no audit entry.
 */
class AdminApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun forbidden(message: String = "Not permitted") =
            AdminApiException(HttpStatusCode.Forbidden, AdminErrors.FORBIDDEN, message)

        fun notFound(message: String = "Not found") =
            AdminApiException(HttpStatusCode.NotFound, AdminErrors.NOT_FOUND, message)

        fun badRequest(message: String) = AdminApiException(HttpStatusCode.BadRequest, "bad_request", message)

        /** 422 naming the invalid field as `field: reason`. */
        fun validation(field: String, reason: String) =
            AdminApiException(HttpStatusCode.UnprocessableEntity, AdminErrors.VALIDATION_FAILED, FieldError(field, reason).toMessage())

        /** 409 naming the conflicting field as `field: reason`. */
        fun conflict(field: String, reason: String) =
            AdminApiException(HttpStatusCode.Conflict, AdminErrors.CONFLICT, FieldError(field, reason).toMessage())
    }
}
