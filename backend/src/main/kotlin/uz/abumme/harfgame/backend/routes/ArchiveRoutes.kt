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
import uz.abumme.harfgame.backend.service.ArchiveServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.archive.ArchiveUploadRequest
import uz.abumme.harfgame.data.archive.ArchiveUploadResponse

fun Route.archiveRoutes(archiveService: ArchiveServerService) {
    authenticate("auth-jwt") {
        get(ApiRoutes.ARCHIVE_RUNS) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@get
            }
            val history = archiveService.listRuns(userId)
            call.respond(HttpStatusCode.OK, history)
        }

        post(ApiRoutes.ARCHIVE_RUNS) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("unauthorized", "Missing userId in token"))
                return@post
            }
            val incoming = call.receive<ArchiveUploadRequest>()
            val accepted = archiveService.uploadRuns(userId, incoming.runs)
            call.respond(HttpStatusCode.OK, ArchiveUploadResponse(acceptedCount = accepted))
        }
    }
}
