package uz.abumme.harfgame.admin

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.AuditQuery
import uz.abumme.harfgame.admin.api.ClientErrors
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpRequest
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.api.HttpTransport
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.api.readCookie
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminApiTest {
    private val meJson =
        """{"id":"s-1","username":"aziz","role":"WORDER","languages":["ru"],"permissions":["WORDS_READ","ACCOUNT_SELF"]}"""

    /** Records requests and answers each with [respond]. */
    private class FakeTransport(var respond: (HttpRequest) -> HttpResponse = { HttpResponse(200, "{}") }) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            return respond(request)
        }
    }

    private fun api(
        transport: HttpTransport,
        credentials: CredentialsMode = CredentialsMode.SAME_ORIGIN,
        cookies: String = "theme=dark; XSRF-TOKEN=tok-123; other=1",
        onUnauthorized: () -> Unit = {},
    ) = AdminApi("/harf", credentials, transport, { cookies }, onUnauthorized)

    @Test
    fun xsrfHeaderIsSentOnMutatingMethodsOnly() = runTest {
        val transport = FakeTransport { HttpResponse(200, "[]") }
        val api = api(transport)

        api.staffList()
        api.logout()
        api.updateStaff("s-1", UpdateStaffRequest(displayName = Patch.Set("Aziz")))
        api.disableStaff("s-1")

        val (get, post, patch, disable) = transport.requests
        assertEquals("GET", get.method)
        assertFalse("X-XSRF-TOKEN" in get.headers)
        assertEquals("tok-123", post.headers["X-XSRF-TOKEN"])
        assertEquals("PATCH", patch.method)
        assertEquals("tok-123", patch.headers["X-XSRF-TOKEN"])
        assertEquals("tok-123", disable.headers["X-XSRF-TOKEN"])
        assertEquals("/harf/api/v1/admin/staff/s-1/disable", disable.url)

        assertEquals("tok-123", api.buildRequest("DELETE", "/api/v1/admin/x", null).headers["X-XSRF-TOKEN"])
        assertEquals("tok-123", api.buildRequest("put", "/api/v1/admin/x", "{}").headers["X-XSRF-TOKEN"])
    }

    @Test
    fun jsonBodyAndContentTypeOnlyWhenThereIsABody() = runTest {
        val transport = FakeTransport { HttpResponse(200, meJson) }
        val api = api(transport)

        api.updateStaff("s-1", UpdateStaffRequest(telegramUserId = Patch.Set(null)))
        api.logout()

        val (patch, logout) = transport.requests
        assertEquals("application/json", patch.headers["Content-Type"])
        assertEquals("""{"telegramUserId":null}""", patch.body)
        assertNull(logout.body)
        assertFalse("Content-Type" in logout.headers)
    }

    @Test
    fun credentialsModeFollowsTheBuildTarget() = runTest {
        val release = FakeTransport { HttpResponse(200, meJson) }
        api(release, CredentialsMode.SAME_ORIGIN).me()
        assertEquals("same-origin", release.requests.single().credentials)
        assertEquals("/harf/api/v1/admin/auth/me", release.requests.single().url)

        val debug = FakeTransport { HttpResponse(200, meJson) }
        AdminApi("http://localhost:8080/", CredentialsMode.INCLUDE, debug, { "" }).me()
        assertEquals("include", debug.requests.single().credentials)
        assertEquals("http://localhost:8080/api/v1/admin/auth/me", debug.requests.single().url)
    }

    @Test
    fun successBodiesDecodeIntoSharedDtos() = runTest {
        val api = api(FakeTransport { HttpResponse(200, meJson) })

        val me = assertIs<ApiResult.Success<MeDto>>(api.me()).data
        assertEquals("aziz", me.username)
        assertEquals(Role.WORDER, me.role)
        assertEquals(setOf(Permission.WORDS_READ, Permission.ACCOUNT_SELF), me.permissions)
    }

    @Test
    fun errorResponsesAreParsedAndFieldReasonsSplit() = runTest {
        val transport = FakeTransport { HttpResponse(409, """{"error":"conflict","message":"username: taken"}""") }
        val api = api(transport)

        val error = assertIs<ApiResult.Error>(api.staffList())
        assertEquals("conflict", error.code)
        assertEquals(FieldError("username", "taken"), error.fieldError())

        transport.respond = { HttpResponse(422, """{"error":"validation_failed","message":"currentPassword: wrong"}""") }
        assertEquals(FieldError("currentPassword", "wrong"), assertIs<ApiResult.Error>(api.logout()).fieldError())

        transport.respond = { HttpResponse(429, "") }
        assertEquals("rate_limited", assertIs<ApiResult.Error>(api.staffList()).code)

        transport.respond = { HttpResponse(502, "<html>Bad gateway</html>") }
        val gateway = assertIs<ApiResult.Error>(api.staffList())
        assertEquals(ClientErrors.SERVER, gateway.code)
        assertNull(gateway.fieldError())

        transport.respond = { HttpResponse(0, "") }
        assertEquals(ClientErrors.NETWORK, assertIs<ApiResult.Error>(api.staffList()).code)

        transport.respond = { HttpResponse(200, "not json") }
        assertEquals(ClientErrors.UNEXPECTED_RESPONSE, assertIs<ApiResult.Error>(api.me()).code)
    }

    @Test
    fun unauthorizedHookFiresForEveryCallButLogin() = runTest {
        var fired = 0
        val transport = FakeTransport {
            HttpResponse(401, """{"error":"unauthorized","message":"Invalid username or password"}""")
        }
        val api = api(transport, onUnauthorized = { fired++ })

        val login = assertIs<ApiResult.Error>(api.login(LoginRequest("aziz", "wrong")))
        assertEquals("unauthorized", login.code)
        assertEquals(0, fired, "a refused sign-in is not an ended session")

        api.staffList()
        assertEquals(1, fired)
        api.changePassword(ChangePasswordRequest("a", "b"))
        assertEquals(2, fired)

        transport.respond = { HttpResponse(403, """{"error":"forbidden","message":"Not permitted"}""") }
        assertEquals("forbidden", assertIs<ApiResult.Error>(api.staffList()).code)
        assertEquals(2, fired, "403 is not a sign-out")
    }

    @Test
    fun loginWithoutAnXsrfCookieSendsNoToken() = runTest {
        val transport = FakeTransport { HttpResponse(200, meJson) }
        api(transport, cookies = "").login(LoginRequest("aziz", "correct horse battery"))

        val request = transport.requests.single()
        assertFalse("X-XSRF-TOKEN" in request.headers)
        assertTrue(request.body!!.contains("\"username\":\"aziz\""))
    }

    @Test
    fun cookiesAndAuditQueryStrings() {
        assertEquals("tok", readCookie("a=1; XSRF-TOKEN=tok", "XSRF-TOKEN"))
        assertEquals("tok=with=equals", readCookie("XSRF-TOKEN=tok=with=equals", "XSRF-TOKEN"))
        assertNull(readCookie("MY-XSRF-TOKEN=nope", "XSRF-TOKEN"))
        assertNull(readCookie("", "XSRF-TOKEN"))

        assertEquals("?page=0&size=50", AuditQuery().toQueryString())
        assertEquals(
            "?actor=s%201&action=STAFF_CREATED&lang=uz-latn&from=1000&to=2000&page=2&size=20",
            AuditQuery("s 1", "STAFF_CREATED", "uz-latn", 1000, 2000, 2, 20).toQueryString(),
        )
    }
}
