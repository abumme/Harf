package uz.abumme.harfgame.backend.admin

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uz.abumme.harfgame.backend.admin.web.CONTENT_SECURITY_POLICY
import uz.abumme.harfgame.backend.admin.web.resolvePanelFile
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminWebServingTest {
    private lateinit var site: File

    @BeforeTest
    fun setup() {
        site = Files.createTempDirectory("admin-web").toFile()
        File(site, "index.html").writeText("<html>index</html>")
        File(site, "staff.html").writeText("<html>staff</html>")
        File(site, "staff").mkdirs()
        File(site, "staff/new.html").writeText("<html>staff new</html>")
        File(site, "calendar.html").writeText("<html>calendar</html>")
        File(site, "calendar").mkdirs()
        File(site, "calendar/table.html").writeText("<html>calendar table</html>")
        File(site, "answer-pool.html").writeText("<html>answer pool</html>")
        File(site, "players.html").writeText("<html>players</html>")
        File(site, "adminWeb.js").writeText("console.log('panel')")
        File(site, "favicon.ico").writeBytes(byteArrayOf(0, 0, 1, 0))
        File(site.parentFile, "secret.txt").writeText("outside the site")
    }

    @AfterTest
    fun cleanup() {
        site.deleteRecursively()
        File(site.parentFile, "secret.txt").delete()
    }

    private fun backend(webDir: File?) = adminBackend(config = AdminConfig(webDir = webDir))

    @Test
    fun pagesResolveToTheirHtmlAndUnknownAddressesToIndex() = testApplication {
        val client = adminClient(backend(site))

        assertEquals("<html>staff</html>", client.get("/admin/staff").bodyAsText())
        assertEquals("<html>staff new</html>", client.get("/admin/staff/new").bodyAsText())
        // The daily-word pages: a page with a folder of the same name, and a kebab-case page.
        assertEquals("<html>calendar</html>", client.get("/admin/calendar").bodyAsText())
        assertEquals("<html>calendar table</html>", client.get("/admin/calendar/table").bodyAsText())
        assertEquals("<html>answer pool</html>", client.get("/admin/answer-pool").bodyAsText())
        // The players list is exported; a player's detail is a dynamic route with no file, so a direct load or refresh
        // gets index.html and the panel's router renders the detail from the URL.
        assertEquals("<html>players</html>", client.get("/admin/players").bodyAsText())
        val detail = client.get("/admin/players/3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f")
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals("<html>index</html>", detail.bodyAsText())
        assertEquals("no-cache", detail.headers[HttpHeaders.CacheControl])
        assertEquals("<html>index</html>", client.get("/admin/").bodyAsText())
        assertEquals("<html>staff</html>", client.get("/admin/staff.html").bodyAsText())

        val unknown = client.get("/admin/unknown/page")
        assertEquals(HttpStatusCode.OK, unknown.status)
        assertEquals("<html>index</html>", unknown.bodyAsText())

        val script = client.get("/admin/adminWeb.js")
        assertEquals("console.log('panel')", script.bodyAsText())
        assertTrue(script.headers[HttpHeaders.ContentType]!!.contains("javascript"))

        // Never a file outside the site.
        assertEquals("<html>index</html>", client.get("/admin/../secret.txt").bodyAsText())
        assertEquals("<html>index</html>", client.get("/admin/%2E%2E/secret.txt").bodyAsText())
        assertEquals(File(site, "index.html").canonicalFile, resolvePanelFile(site.canonicalFile, "../secret.txt"))
    }

    @Test
    fun bareAdminPathRedirectsRelatively() = testApplication {
        val client = adminClient(backend(site))
        val noRedirects = createClient { followRedirects = false }

        val response = noRedirects.get("/admin")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("admin/", response.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.OK, client.get("/admin/").status)
    }

    @Test
    fun cachingAndSecurityHeaders() = testApplication {
        val client = adminClient(backend(site))

        val page = client.get("/admin/staff")
        assertEquals("no-cache", page.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", client.get("/admin/adminWeb.js").headers[HttpHeaders.CacheControl])
        assertEquals("max-age=3600", client.get("/admin/favicon.ico").headers[HttpHeaders.CacheControl])

        for (response in listOf(page, client.get("/admin/favicon.ico"), client.get("/admin/nowhere"))) {
            assertEquals(CONTENT_SECURITY_POLICY, response.headers["Content-Security-Policy"])
            assertTrue("frame-ancestors 'none'" in response.headers["Content-Security-Policy"]!!)
            assertEquals("DENY", response.headers["X-Frame-Options"])
            assertEquals("same-origin", response.headers["Referrer-Policy"])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }
    }

    @Test
    fun conditionalRequestsGetNotModified() = testApplication {
        val client = adminClient(backend(site))

        val first = client.get("/admin/staff")
        val etag = first.headers[HttpHeaders.ETag]!!
        val lastModified = first.headers[HttpHeaders.LastModified]!!

        val byEtag = client.get("/admin/staff") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, byEtag.status)
        assertEquals("no-cache", byEtag.headers[HttpHeaders.CacheControl])

        val byDate = client.get("/admin/adminWeb.js") { header(HttpHeaders.IfModifiedSince, lastModified) }
        assertEquals(HttpStatusCode.NotModified, byDate.status)

        File(site, "staff.html").apply { writeText("<html>staff v2</html>"); setLastModified(lastModified() + 5_000) }
        assertEquals(HttpStatusCode.OK, client.get("/admin/staff") { header(HttpHeaders.IfNoneMatch, etag) }.status)
    }

    @Test
    fun headRequestsAnswerWithoutABody() = testApplication {
        val client = adminClient(backend(site))

        val response = client.head("/admin/staff")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun nothingIsServedWithoutAdminWebDir() = testApplication {
        val client = adminClient(backend(null))

        assertEquals(HttpStatusCode.NotFound, client.get("/admin/staff").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/admin").status)
    }

    @Test
    fun missingAdminWebDirServesNothing() = testApplication {
        val client = adminClient(backend(File(site, "does-not-exist")))

        assertEquals(HttpStatusCode.NotFound, client.get("/admin/").status)
    }
}
