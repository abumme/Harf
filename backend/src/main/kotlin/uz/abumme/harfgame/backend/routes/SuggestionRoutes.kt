package uz.abumme.harfgame.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.service.SuggestOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestionErrors
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

/** Stores a suggestion for the review worker; nothing reaches Telegram while the player waits. */
fun Route.suggestionRoutes(service: SuggestionServerService) {
    authenticate("auth-jwt") {
        post(ApiRoutes.SUGGESTIONS) {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }
            val request = call.receive<SuggestWordRequest>()
            when (val outcome = service.suggest(userId, request.lang, request.word)) {
                is SuggestOutcome.Stored, SuggestOutcome.DuplicatePending ->
                    call.respond(HttpStatusCode.Accepted, SuggestWordResponse(SuggestionStatus.PENDING.name))
                is SuggestOutcome.Rejected ->
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("rejected", outcome.reason))
                SuggestOutcome.OverCap ->
                    call.respond(HttpStatusCode.TooManyRequests, ApiErrorResponse("rate_limited", "Daily suggestion limit reached"))
                // Any non-2xx makes the current app show its "couldn't send" message; a later one can match the code.
                SuggestOutcome.Blocked ->
                    call.respond(HttpStatusCode.Forbidden, ApiErrorResponse(SuggestionErrors.BLOCKED, "Suggestions are blocked for this account"))
            }
        }
    }
}
