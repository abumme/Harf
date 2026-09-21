package uz.abumme.harfgame.backend.admin.languages

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.abumme.harfgame.backend.admin.AdminBackend
import uz.abumme.harfgame.backend.admin.adminPath
import uz.abumme.harfgame.backend.admin.staffPrincipal
import uz.abumme.harfgame.data.admin.AdminRoutes

/** `GET /languages`: the pack languages the caller may work in (all of them for an ADMIN). */
fun Route.languagesRoutes(admin: AdminBackend) {
    get(adminPath(AdminRoutes.LANGUAGES)) {
        call.respond(HttpStatusCode.OK, call.staffPrincipal().languagesWithin(admin.packLanguages()))
    }
}
