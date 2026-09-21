package uz.abumme.harfgame.backend.admin.words

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.words.AddWordRequest
import uz.abumme.harfgame.data.admin.words.BulkAddRequest
import uz.abumme.harfgame.data.admin.words.CheckWordRequest
import uz.abumme.harfgame.data.admin.words.EditWordRequest

/** The word catalog (WORDER and ADMIN), each request limited to the caller's languages by [WordCatalogService]. */
fun Route.wordsRoutes(admin: AdminBackend) {
    val words = adminPath(AdminRoutes.WORDS)
    requirePermission(Permission.WORDS_READ) {
        get(words) {
            call.respond(HttpStatusCode.OK, admin.catalog.list(call.staffPrincipal(), WordQuery.parse(call.request.queryParameters)))
        }
        post(adminPath(AdminRoutes.WORDS_CHECK)) {
            val request = call.receiveAdmin<CheckWordRequest>()
            call.respond(HttpStatusCode.OK, admin.catalog.check(call.staffPrincipal(), request.lang, request.text))
        }
    }
    requirePermission(Permission.WORDS_WRITE) {
        post(words) {
            val request = call.receiveAdmin<AddWordRequest>()
            call.respond(HttpStatusCode.Created, admin.catalog.add(call.staffPrincipal(), request.lang, request.text))
        }
        post(adminPath(AdminRoutes.WORDS_BULK)) {
            val request = call.receiveAdmin<BulkAddRequest>()
            call.respond(HttpStatusCode.OK, admin.catalog.bulkAdd(call.staffPrincipal(), request.lang, request.lines))
        }
        patch("$words/{id}") {
            val request = call.receiveAdmin<EditWordRequest>()
            call.respond(HttpStatusCode.OK, admin.catalog.edit(call.staffPrincipal(), call.wordId(), request.text))
        }
        post("$words/{id}/remove") {
            call.respond(HttpStatusCode.OK, admin.catalog.remove(call.staffPrincipal(), call.wordId()))
        }
        post("$words/{id}/restore") {
            call.respond(HttpStatusCode.OK, admin.catalog.restore(call.staffPrincipal(), call.wordId()))
        }
    }
}

private fun RoutingCall.wordId(): String =
    parameters["id"]?.takeIf { it.isNotBlank() } ?: throw AdminApiException.notFound("Missing word id")
