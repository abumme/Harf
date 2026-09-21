package uz.abumme.harfgame.backend.admin.answerpool

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.access.requirePermission
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.calendar.page
import uz.abumme.harfgame.backend.admin.calendar.pathParameter
import uz.abumme.harfgame.backend.admin.calendar.size
import uz.abumme.harfgame.backend.admin.receiveAdmin
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PoolParams

/** The daily answer pools (ADMIN only: `DAILY_POOL_MANAGE`; a WORDER is refused before any handler runs). */
fun Route.answerPoolRoutes(admin: AdminBackend) {
    val base = adminPath(AdminRoutes.ANSWER_POOL)
    requirePermission(Permission.DAILY_POOL_MANAGE) {
        get(adminPath(AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS)) {
            call.respond(HttpStatusCode.OK, admin.answerPool.cyrlStatus(call.request.queryParameters[PoolParams.CYRL].orEmpty()))
        }
        post(adminPath(AdminRoutes.ANSWER_POOL_UZ_PAIRS)) {
            val request = call.receiveAdmin<CreatePairRequest>()
            call.respond(HttpStatusCode.Created, admin.answerPool.createPair(call.staffPrincipal(), request))
        }
        delete("${adminPath(AdminRoutes.ANSWER_POOL_UZ_PAIRS)}/{pairId}") {
            admin.answerPool.removePair(call.staffPrincipal(), call.pathParameter("pairId"))
            call.respond(HttpStatusCode.NoContent)
        }
        get("$base/{calendar}") {
            val parameters = call.request.queryParameters
            val page = admin.answerPool.list(call.pathParameter("calendar"), parameters[PoolParams.Q], parameters.page(), parameters.size())
            call.respond(HttpStatusCode.OK, page)
        }
        get("$base/{calendar}/candidates") {
            val parameters = call.request.queryParameters
            val page = admin.answerPool.candidates(call.pathParameter("calendar"), parameters[PoolParams.Q], parameters.page(), parameters.size())
            call.respond(HttpStatusCode.OK, page)
        }
        post("$base/{calendar}/words") {
            val request = call.receiveAdmin<MarkWordsRequest>()
            call.respond(HttpStatusCode.OK, admin.answerPool.mark(call.staffPrincipal(), call.pathParameter("calendar"), request))
        }
        delete("$base/{calendar}/words/{wordId}") {
            admin.answerPool.unmark(call.staffPrincipal(), call.pathParameter("calendar"), call.pathParameter("wordId"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
