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
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

fun Route.suggestionRoutes(service: SuggestionServerService, telegram: TelegramBot) {
    authenticate("auth-jwt") {
        post(ApiRoutes.SUGGESTIONS) {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }
            val request = call.receive<SuggestWordRequest>()
            when (val outcome = service.suggest(userId, request.lang, request.word)) {
                is SuggestOutcome.Stored -> {
                    telegram.notifyPending(outcome.id, outcome.lang, outcome.word)
                    call.respond(HttpStatusCode.Accepted, SuggestWordResponse(SuggestionStatus.PENDING.name))
                }
                SuggestOutcome.DuplicatePending ->
                    call.respond(HttpStatusCode.Accepted, SuggestWordResponse(SuggestionStatus.PENDING.name))
                is SuggestOutcome.Rejected ->
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("rejected", outcome.reason))
                SuggestOutcome.OverCap ->
                    call.respond(HttpStatusCode.TooManyRequests, ApiErrorResponse("rate_limited", "Daily suggestion limit reached"))
            }
        }
    }
}
