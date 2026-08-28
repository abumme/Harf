package uz.abumme.harfgame.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.service.SyncServerService
import uz.abumme.harfgame.backend.service.UploadStatsResult
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.sync.UserStatsDto

fun Route.syncRoutes(syncService: SyncServerService) {
    authenticate("auth-jwt") {
        get(ApiRoutes.SYNC_STATS) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@get
            }
            val stats = syncService.getStats(userId)
            call.respond(HttpStatusCode.OK, stats)
        }

        post(ApiRoutes.SYNC_STATS) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }
            val incoming = call.receive<UserStatsDto>()
            when (val result = syncService.uploadStats(userId, incoming)) {
                is UploadStatsResult.Success -> {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                }
                is UploadStatsResult.StoredSnapshotWon -> {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "stored_snapshot_kept"))
                }
                is UploadStatsResult.Rejected -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse("invalid_timestamp", result.message)
                    )
                }
            }
        }
    }
}
