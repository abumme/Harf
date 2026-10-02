package uz.abumme.harfgame.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.abumme.harfgame.backend.service.EntitlementServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes

fun Route.entitlementRoutes(entitlementService: EntitlementServerService) {
    authenticate("auth-jwt") {
        get(ApiRoutes.ENTITLEMENTS) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@get
            }
            val entitlements = entitlementService.getEntitlements(userId)
            if (entitlements == null) {
                // The store could not be asked: say so, so the client keeps what it last verified.
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ApiErrorResponse("entitlements_unavailable", "Purchases could not be checked right now"),
                )
                return@get
            }
            call.respond(HttpStatusCode.OK, entitlements)
        }
    }
}
