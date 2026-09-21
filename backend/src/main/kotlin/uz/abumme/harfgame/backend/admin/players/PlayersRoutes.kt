package uz.abumme.harfgame.backend.admin.players

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery

/**
 * Player accounts (ADMIN): search and detail need `PLAYERS_READ`, the support actions `PLAYERS_WRITE`. Requests and
 * responses are the shared `data.admin.players` types only, so the panel reads exactly what is sent.
 */
fun Route.playersRoutes(admin: AdminBackend) {
    val players = adminPath(AdminRoutes.PLAYERS)
    requirePermission(Permission.PLAYERS_READ) {
        get(players) {
            val parameters = call.request.queryParameters
            val parsed = PlayerSearchQuery.fromQueryParameters(parameters.names().associateWith { parameters[it].orEmpty() })
            parsed.invalid.firstOrNull()?.let { throw AdminApiException.validation(it.field, it.reason) }
            call.respond(HttpStatusCode.OK, admin.players.search(parsed.query))
        }
        get("$players/{id}") {
            val id = call.playerId()
            call.respond(HttpStatusCode.OK, admin.players.detail(id) ?: throw AdminApiException.notFound("No player account $id"))
        }
    }
    requirePermission(Permission.PLAYERS_WRITE) {
        post("$players/{id}/delete") {
            val request = call.receiveAdmin<DeletePlayerRequest>()
            admin.players.delete(call.playerId(), request.confirmAccountId, call.actor())
            call.respond(HttpStatusCode.NoContent)
        }
        post("$players/{id}/end-sessions") {
            call.respond(HttpStatusCode.OK, admin.players.endSessions(call.playerId(), call.actor()))
        }
        put("$players/{id}/suggestion-block") {
            call.respond(HttpStatusCode.OK, admin.players.blockSuggestions(call.playerId(), call.actor()))
        }
        delete("$players/{id}/suggestion-block") {
            call.respond(HttpStatusCode.OK, admin.players.unblockSuggestions(call.playerId(), call.actor()))
        }
        delete("$players/{id}/display-name") {
            call.respond(HttpStatusCode.OK, admin.players.clearDisplayName(call.playerId(), call.actor()))
        }
    }
}

private fun RoutingCall.playerId(): String =
    parameters["id"]?.takeIf { it.isNotBlank() } ?: throw AdminApiException.notFound("Missing player account id")

private fun RoutingCall.actor(): AuditActor.Staff = AuditActor.Staff(staffPrincipal().staffId)
