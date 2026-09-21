package uz.abumme.harfgame.backend.admin

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.api.ApiRoutes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminDevCorsTest {
    private val devOrigin = "http://localhost:8090"

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    private fun config(production: Boolean) =
        AdminConfig.fromEnv(env = mapOf("ADMIN_DEV_ORIGIN" to devOrigin)::get, production = production)

    @Test
    fun developmentPreflightFromTheDevServerIsAllowedWithCredentials() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient(adminBackend(config = config(production = false)))

        val preflight = client.options(AdminRoutes.STAFF) {
            header(HttpHeaders.Origin, devOrigin)
            header(HttpHeaders.AccessControlRequestMethod, "PATCH")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-xsrf-token")
        }
        assertEquals(HttpStatusCode.OK, preflight.status)
        assertEquals(devOrigin, preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("true", preflight.headers[HttpHeaders.AccessControlAllowCredentials])
        assertTrue(preflight.headers[HttpHeaders.AccessControlAllowMethods]!!.contains("PATCH"))
        val allowedHeaders = preflight.headers[HttpHeaders.AccessControlAllowHeaders]!!.lowercase()
        assertTrue("content-type" in allowedHeaders && "x-xsrf-token" in allowedHeaders, allowedHeaders)

        val session = client.signIn("boss")
        val actual = client.get(AdminRoutes.AUTH_ME) {
            header(HttpHeaders.Origin, devOrigin)
            withSession(session)
        }
        assertEquals(HttpStatusCode.OK, actual.status)
        assertEquals(devOrigin, actual.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("true", actual.headers[HttpHeaders.AccessControlAllowCredentials])
    }

    @Test
    fun anotherOriginGetsNoCorsHeaders() = testApplication {
        val client = adminClient(adminBackend(config = config(production = false)))

        val preflight = client.options(AdminRoutes.STAFF) {
            header(HttpHeaders.Origin, "http://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowCredentials])
    }

    @Test
    fun productionSendsNoCorsHeadersEvenWithTheVariableSet() = testApplication {
        val client = adminClient(adminBackend(config = config(production = true)))

        val preflight = client.options(AdminRoutes.STAFF) {
            header(HttpHeaders.Origin, devOrigin)
            header(HttpHeaders.AccessControlRequestMethod, "PATCH")
        }
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowCredentials])

        val get = client.get(AdminRoutes.AUTH_ME) { header(HttpHeaders.Origin, devOrigin) }
        assertNull(get.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun playerRoutesAreUntouchedByDevelopmentCors() = testApplication {
        val client = adminClient(adminBackend(config = config(production = false)))

        val response = client.get(ApiRoutes.wordpack("en")) { header(HttpHeaders.Origin, devOrigin) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])

        val preflight = client.options(ApiRoutes.wordpack("en")) {
            header(HttpHeaders.Origin, devOrigin)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
