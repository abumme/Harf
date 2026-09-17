package uz.abumme.harfgame.backend.admin.suggestions

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.suggestions.DecisionRequest

/** Player suggestion review (WORDER and ADMIN), limited to the caller's languages. */
fun Route.suggestionsRoutes(admin: AdminBackend) {
    val suggestions = adminPath(AdminRoutes.SUGGESTIONS)
    requirePermission(Permission.SUGGESTIONS_REVIEW) {
        get(suggestions) {
            call.respond(HttpStatusCode.OK, admin.suggestions.list(call.staffPrincipal(), SuggestionQuery.parse(call.request.queryParameters)))
        }
        post("$suggestions/{id}/decision") {
            val id = call.parameters["id"]?.takeIf { it.isNotBlank() } ?: throw AdminApiException.notFound("Missing suggestion id")
            val request = call.receiveAdmin<DecisionRequest>()
            call.respond(HttpStatusCode.OK, admin.suggestions.decide(call.staffPrincipal(), id, request.accept))
        }
    }
}
