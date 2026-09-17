package uz.abumme.harfgame.backend.admin.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFile
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.io.File

/** Where the backend serves the exported panel. Caddy strips `/harf`, so the public URL is `/harf/admin/`. */
const val ADMIN_WEB_PATH = "/admin"

/**
 * Serves the statically exported Kobweb panel from [webDir] at [ADMIN_WEB_PATH], or nothing when [webDir] is null or
 * missing.
 *
 * Each request resolves like Kobweb's documented `try_files {path}.html {path}` and then falls back to `index.html`:
 * `/admin/staff` serves `staff.html` even though a `staff/` folder (with `new.html`) exists, and an unknown address
 * serves `index.html`, whose script routes client-side and shows the not-found page. (Ktor's `staticFiles` would pick
 * the folder and fall back to `index.html` for `/admin/staff`.)
 */
fun Route.adminWebRoutes(webDir: File?) {
    if (webDir == null) return
    if (!webDir.isDirectory) {
        log.warn("ADMIN_WEB_DIR {} is not a directory; the admin panel is not served.", webDir)
        return
    }
    val root = webDir.canonicalFile

    route(ADMIN_WEB_PATH) {
        install(AdminWebHeaders)
        install(ConditionalHeaders)

        // Exactly `/admin`: a relative redirect makes the public `/harf/admin` become `/harf/admin/` without the
        // backend knowing the proxy prefix.
        get { call.respondRedirect("admin/", permanent = false) }

        route("{path...}") {
            get { call.respondPanelFile(root, head = false) }
            method(HttpMethod.Head) { handle { call.respondPanelFile(root, head = true) } }
        }
    }
}

private suspend fun RoutingCall.respondPanelFile(root: File, head: Boolean) {
    val relative = parameters.getAll("path").orEmpty().filter { it.isNotEmpty() }.joinToString("/")
    val file = resolvePanelFile(root, relative)
    // Kobweb names the script `adminWeb.js` without a content hash, so the script and the pages always revalidate
    // and a deploy is picked up on the next load; other resources may be cached for an hour.
    val revalidate = file.extension == "html" || file.name == "adminWeb.js"
    response.header(HttpHeaders.CacheControl, if (revalidate) "no-cache" else "max-age=3600")
    response.header(HttpHeaders.ETag, "\"${file.length().toString(16)}-${file.lastModified().toString(16)}\"")
    val content = LocalFileContent(file, ContentType.defaultForFile(file))
    respond(if (head) HeadContent(content) else content)
}

/** `{path}.html`, then `{path}` as a file, then `index.html`; never a file outside [root]. */
internal fun resolvePanelFile(root: File, relative: String): File {
    if (relative.isNotEmpty()) {
        for (candidate in listOf("$relative.html", relative)) {
            val file = File(root, candidate).canonicalFile
            if (file.isFile && file.path.startsWith(root.path + File.separator)) return file
        }
    }
    return File(root, "index.html")
}

/** A HEAD answer: the GET response's status and headers without its body. */
private class HeadContent(private val original: OutgoingContent) : OutgoingContent.NoContent() {
    override val contentType: ContentType? get() = original.contentType
    override val contentLength: Long? get() = original.contentLength
    override val status: HttpStatusCode? get() = original.status
    override val headers get() = original.headers
    override fun <T : Any> getProperty(key: io.ktor.util.AttributeKey<T>): T? = original.getProperty(key)
}

/**
 * Security headers on every panel response. Inline styles are allowed because Silk injects its style rules at
 * runtime and exported pages carry `<style>` blocks; scripts load only from the panel's own origin.
 */
private val AdminWebHeaders = createRouteScopedPlugin("AdminWebHeaders") {
    onCall { call ->
        with(call.response) {
            header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
            header("X-Frame-Options", "DENY")
            header("Referrer-Policy", "same-origin")
            header("X-Content-Type-Options", "nosniff")
        }
    }
}

internal const val CONTENT_SECURITY_POLICY =
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
        "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"

private val log = LoggerFactory.getLogger("uz.abumme.harfgame.backend.admin.web.AdminWebRoutes")
