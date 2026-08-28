package uz.abumme.harfgame.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import uz.abumme.harfgame.backend.service.AuthServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.LinkAccountRequest
import uz.abumme.harfgame.data.auth.RefreshRequest

fun Route.authRoutes(authService: AuthServerService) {
    post(ApiRoutes.AUTH_ANONYMOUS) {
        val response = authService.createAnonymousAccount()
        call.respond(HttpStatusCode.Created, response)
    }

    post(ApiRoutes.AUTH_REFRESH) {
        val request = call.receive<RefreshRequest>()
        val response = authService.refreshToken(request.refreshToken)
        if (response != null) {
            call.respond(HttpStatusCode.OK, response)
        } else {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponse("unauthorized", "Invalid, expired, or revoked refresh token")
            )
        }
    }

    authenticate("auth-jwt") {
        post(ApiRoutes.AUTH_LINK) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }

            val request = call.receive<LinkAccountRequest>()
            val response = authService.linkAccount(userId, request)
            if (response != null) {
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiErrorResponse("invalid_provider_token", "Failed to verify provider token")
                )
            }
        }

        post(ApiRoutes.AUTH_LOGOUT) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }
            authService.logout(userId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        delete(ApiRoutes.ACCOUNT) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@delete
            }
            authService.deleteAccount(userId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }
    }
}
