package uz.abumme.harfgame.backend.admin

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.auth.Xsrf
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminXsrfAndCookiesTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private fun staffCount() = transaction(DatabaseFactory.init()) { StaffTable.selectAll().count() }

    @Test
    fun mutationWithoutTheTokenIsForbiddenAndChangesNothing() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val azizId = insertStaff("aziz")
        val client = adminClient()
        val session = client.signIn("boss")

        val create = client.post(AdminRoutes.STAFF) {
            withSession(session, xsrf = null)
            jsonBody(CreateStaffRequest("dilnoza", PASSWORD, Role.WORDER, listOf("ru")))
        }
        assertEquals(HttpStatusCode.Forbidden, create.status)
        assertEquals("forbidden", create.error().error)
        assertEquals(2, staffCount())

        val disable = client.post(AdminRoutes.staffDisable(azizId)) { withSession(session, xsrf = "") }
        assertEquals(HttpStatusCode.Forbidden, disable.status)
        assertEquals("ACTIVE", staffRow(azizId)[StaffTable.status])

        val logout = client.post(AdminRoutes.AUTH_LOGOUT) { withSession(session, xsrf = null) }
        assertEquals(HttpStatusCode.Forbidden, logout.status)
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUTH_ME) { withSession(session) }.status, "still signed in")
    }

    @Test
    fun tokenOfAnotherSessionIsForbidden() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val azizId = insertStaff("aziz")
        val client = adminClient()
        val mine = client.signIn("boss")
        val other = client.signIn("boss")
        assertNotEquals(mine.xsrf, other.xsrf)

        val response = client.patch(AdminRoutes.staff(azizId)) {
            withSession(mine, xsrf = other.xsrf)
            jsonBody(UpdateStaffRequest(displayName = Patch.Set("Aziz")))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(staffRow(azizId)[StaffTable.displayName])

        val allowed = client.patch(AdminRoutes.staff(azizId)) {
            withSession(mine)
            jsonBody(UpdateStaffRequest(displayName = Patch.Set("Aziz")))
        }
        assertEquals(HttpStatusCode.OK, allowed.status)
    }

    @Test
    fun readsDoNotNeedTheToken() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val session = client.signIn("boss")

        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUDIT) { withSession(session, xsrf = null) }.status)
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.STAFF) { withSession(session, xsrf = null) }.status)
    }

    @Test
    fun adminBodiesMustBeJson() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val session = client.signIn("boss")

        val formLogin = client.post(AdminRoutes.AUTH_LOGIN) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=boss&password=${PASSWORD.replace(' ', '+')}")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, formLogin.status)

        val textCreate = client.post(AdminRoutes.STAFF) {
            withSession(session)
            contentType(ContentType.Text.Plain)
            setBody("""{"username":"dilnoza","password":"$PASSWORD","role":"WORDER","languages":["ru"]}""")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, textCreate.status)
        assertEquals(1, staffCount())
    }

    @Test
    fun meReissuesAMissingXsrfCookie() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val session = client.signIn("boss")

        val withoutXsrfCookie = client.get(AdminRoutes.AUTH_ME) {
            header(HttpHeaders.Cookie, "${AdminRoutes.SESSION_COOKIE}=${session.token}")
        }
        assertEquals(HttpStatusCode.OK, withoutXsrfCookie.status)
        val setCookie = withoutXsrfCookie.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        assertTrue(setCookie.any { it.startsWith("${AdminRoutes.XSRF_COOKIE}=${session.xsrf};") }, setCookie.toString())
        assertEquals(Xsrf.tokenFor(session.token), session.xsrf)

        val withCookies = client.get(AdminRoutes.AUTH_ME) { withSession(session) }
        assertTrue(withCookies.headers.getAll(HttpHeaders.SetCookie).isNullOrEmpty())
    }

    @Test
    fun developmentCookiesArePathRootAndNotSecure() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient(adminBackend(config = AdminConfig(production = false, loginAttemptsPerMinute = 100)))

        val cookies = client.login("boss").headers.getAll(HttpHeaders.SetCookie).orEmpty().associateBy { it.substringBefore('=') }
        val session = attributes(cookies.getValue(AdminRoutes.SESSION_COOKIE))
        val xsrf = attributes(cookies.getValue(AdminRoutes.XSRF_COOKIE))

        assertTrue("httponly" in session)
        assertFalse("httponly" in xsrf)
        for (cookie in listOf(session, xsrf)) {
            assertEquals("strict", cookie["samesite"])
            assertEquals("/", cookie["path"])
            assertFalse("secure" in cookie)
            assertEquals("604800", cookie["max-age"])
        }
    }

    @Test
    fun productionCookiesAreSecureAndUseTheConfiguredPath() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val config = AdminConfig.fromEnv(
            env = mapOf("ADMIN_COOKIE_PATH" to "/harf", "ADMIN_DEV_ORIGIN" to "http://localhost:8090")::get,
            production = true,
        ).copy(loginAttemptsPerMinute = 100)
        val client = adminClient(adminBackend(config = config))

        val cookies = client.login("boss").headers.getAll(HttpHeaders.SetCookie).orEmpty().associateBy { it.substringBefore('=') }
        val session = attributes(cookies.getValue(AdminRoutes.SESSION_COOKIE))
        val xsrf = attributes(cookies.getValue(AdminRoutes.XSRF_COOKIE))

        assertTrue("httponly" in session)
        assertFalse("httponly" in xsrf)
        for (cookie in listOf(session, xsrf)) {
            assertEquals("strict", cookie["samesite"])
            assertEquals("/harf", cookie["path"])
            assertTrue("secure" in cookie)
        }
    }

    @Test
    fun configIsReadFromTheEnvironment() {
        val defaults = AdminConfig.fromEnv(env = { null }, production = false)
        assertEquals("/", defaults.cookiePath)
        assertFalse(defaults.trustProxyHeaders)
        assertNull(defaults.devCorsOrigin)
        assertNull(defaults.webDir)
        assertFalse(defaults.secureCookies)

        val env = mapOf(
            "ADMIN_COOKIE_PATH" to "/harf",
            "TRUST_PROXY_HEADERS" to "true",
            "ADMIN_DEV_ORIGIN" to "http://localhost:8090/",
            "ADMIN_WEB_DIR" to "/app/admin-web",
        )
        val development = AdminConfig.fromEnv(env = env::get, production = false)
        assertEquals("/harf", development.cookiePath)
        assertTrue(development.trustProxyHeaders)
        assertEquals("http://localhost:8090", development.devCorsOrigin)
        assertEquals(File("/app/admin-web"), development.webDir)

        val production = AdminConfig.fromEnv(env = env::get, production = true)
        assertNull(production.devCorsOrigin, "development CORS is never enabled in production")
        assertTrue(production.secureCookies)
    }

    /** Lower-cased `Set-Cookie` attributes; flags map to an empty value. */
    private fun attributes(header: String): Map<String, String> =
        header.split(';').drop(1).map { it.trim() }.filter { it.isNotEmpty() }.associate { part ->
            part.substringBefore('=').lowercase() to part.substringAfter('=', "").lowercase()
        }
}
