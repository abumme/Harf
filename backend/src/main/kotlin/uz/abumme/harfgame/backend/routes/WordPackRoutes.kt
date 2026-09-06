package uz.abumme.harfgame.backend.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes

/** Public, cacheable word-pack distribution. `If-None-Match: <version>` yields 304. */
fun Route.wordPackRoutes(service: WordPackServerService) {
    get("${ApiRoutes.WORDPACKS}/{lang}") {
        val lang = call.parameters["lang"]
        if (lang.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("bad_request", "Missing language"))
            return@get
        }
        val pack = service.getPack(lang)
        if (pack == null) {
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse("not_found", "No word pack for $lang"))
            return@get
        }
        val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)?.trim('"')
        if (ifNoneMatch == pack.version) {
            call.respond(HttpStatusCode.NotModified)
            return@get
        }
        call.response.header(HttpHeaders.ETag, pack.version)
        call.respond(HttpStatusCode.OK, pack)
    }
}
