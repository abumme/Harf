package uz.abumme.harfgame.backend.admin

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffAuthIntegrationTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    @Test
    fun playerJwtIsRefusedOnAdminRoutes() = testApplication {
        transaction(DatabaseFactory.init()) { UsersTable.deleteAll() }
        val client = adminClient()

        val player = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        // The token is valid for the player API...
        val sync = client.get(ApiRoutes.SYNC_STATS) { header(HttpHeaders.Authorization, "Bearer ${player.tokens.accessToken}") }
        assertEquals(HttpStatusCode.OK, sync.status)

        // ...but opens nothing in the admin API.
        val me = client.get(AdminRoutes.AUTH_ME) { header(HttpHeaders.Authorization, "Bearer ${player.tokens.accessToken}") }
        assertEquals(HttpStatusCode.Unauthorized, me.status)
        assertEquals("unauthorized", me.error().error)
        val staff = client.get(AdminRoutes.STAFF) { header(HttpHeaders.Authorization, "Bearer ${player.tokens.accessToken}") }
        assertEquals(HttpStatusCode.Unauthorized, staff.status)
    }

    @Test
    fun successfulLoginReturnsMeAndSetsSessionCookies() = testApplication {
        val id = insertStaff("aziz", Role.WORDER, languages = listOf("uz-latn", "uz-cyrl"))
        val client = adminClient()

        val response = client.login("aziz")
        assertEquals(HttpStatusCode.OK, response.status)
        val me = response.body<MeDto>()
        assertEquals(id, me.id)
        assertEquals("aziz", me.username)
        assertEquals(Role.WORDER, me.role)
        assertEquals(listOf("uz-cyrl", "uz-latn"), me.languages)
        assertTrue(Permission.WORDS_WRITE in me.permissions)
        assertFalse(Permission.STAFF_MANAGE in me.permissions)
        assertFalse(Permission.AUDIT_READ_ALL in me.permissions)

        val cookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty().map(::parseServerSetCookieHeader).associateBy { it.name }
        val session = cookies.getValue(AdminRoutes.SESSION_COOKIE)
        val xsrf = cookies.getValue(AdminRoutes.XSRF_COOKIE)
        assertTrue(session.httpOnly)
        assertFalse(xsrf.httpOnly)
        assertTrue(session.value.isNotBlank() && xsrf.value.isNotBlank())
        assertNotNull(staffRow(id)[StaffTable.lastLoginAt])

        val meAgain = client.get(AdminRoutes.AUTH_ME) { withSession(response.session(), xsrf = null) }
        assertEquals(HttpStatusCode.OK, meAgain.status)
        assertEquals(me, meAgain.body<MeDto>())
    }

    @Test
    fun usernamesAreCaseInsensitive() = testApplication {
        insertStaff("aziz")
        val client = adminClient()

        assertEquals(HttpStatusCode.OK, client.login("Aziz").status)
        assertEquals(HttpStatusCode.OK, client.login("  AZIZ ").status)
    }

    @Test
    fun wrongPasswordUnknownUserAndDisabledAccountGetTheSameAnswer() = testApplication {
        insertStaff("aziz")
        insertStaff("olim", status = StaffStatus.DISABLED)
        val client = adminClient()

        val wrongPassword = client.login("aziz", "not the password!")
        val unknownUser = client.login("nobody", PASSWORD)
        val disabled = client.login("olim", PASSWORD)

        for (response in listOf(wrongPassword, unknownUser, disabled)) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(response.headers[HttpHeaders.SetCookie], "no session is started")
        }
        val body = wrongPassword.bodyAsText()
        assertEquals(body, unknownUser.bodyAsText())
        assertEquals(body, disabled.bodyAsText())
    }

    @Test
    fun logoutEndsTheSession() = testApplication {
        insertStaff("aziz")
        val client = adminClient()
        val session = client.signIn("aziz")

        val logout = client.post(AdminRoutes.AUTH_LOGOUT) { withSession(session) }
        assertEquals(HttpStatusCode.NoContent, logout.status)
        val cleared = logout.headers.getAll(HttpHeaders.SetCookie).orEmpty().map(::parseServerSetCookieHeader).associateBy { it.name }
        assertEquals(0, cleared.getValue(AdminRoutes.SESSION_COOKIE).maxAge)
        assertEquals(0, cleared.getValue(AdminRoutes.XSRF_COOKIE).maxAge)

        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(session) }.status)
    }

    @Test
    fun noSessionIsUnauthorized() = testApplication {
        val client = adminClient()

        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUDIT).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get(AdminRoutes.AUTH_ME) { header(HttpHeaders.Cookie, "${AdminRoutes.SESSION_COOKIE}=forged") }.status,
        )
    }

    @Test
    fun languageListIsScopedToTheCaller() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("dilnoza", Role.WORDER, languages = listOf("uz-latn", "uz-cyrl"))
        val client = adminClient()

        val adminLanguages = client.get(AdminRoutes.LANGUAGES) { withSession(client.signIn("boss")) }.body<List<String>>()
        assertEquals(PACK_LANGUAGES, adminLanguages)

        val worderLanguages = client.get(AdminRoutes.LANGUAGES) { withSession(client.signIn("dilnoza")) }.body<List<String>>()
        assertEquals(listOf("uz-cyrl", "uz-latn"), worderLanguages)

        val adminMe = client.get(AdminRoutes.AUTH_ME) { withSession(client.signIn("boss")) }.body<MeDto>()
        assertEquals(PACK_LANGUAGES, adminMe.languages)
        assertEquals(Permission.entries.toSet(), adminMe.permissions)
    }

    @Test
    fun fifthConsecutiveFailureLocksTheAccountForFifteenMinutes() = testApplication {
        val clock = MutableClock()
        val id = insertStaff("aziz")
        val client = adminClient(adminBackend(clock))

        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.login("aziz", "wrong password $it").status) }
        val fifth = client.login("aziz", "wrong password 5")
        assertEquals(HttpStatusCode.Locked, fifth.status)
        assertEquals("locked", fifth.error().error)
        assertEquals(clock.now.plus(Duration.ofMinutes(15)), staffRow(id)[StaffTable.lockedUntil])

        // The correct password is refused while locked.
        val sixth = client.login("aziz")
        assertEquals(HttpStatusCode.Locked, sixth.status)
        assertNull(sixth.headers[HttpHeaders.SetCookie])

        clock.advance(Duration.ofMinutes(14))
        assertEquals(HttpStatusCode.Locked, client.login("aziz").status)

        // The lock expires after 15 minutes.
        clock.advance(Duration.ofMinutes(1))
        assertEquals(HttpStatusCode.OK, client.login("aziz").status)
        assertEquals(0, staffRow(id)[StaffTable.failedLoginCount])
    }

    @Test
    fun successResetsTheFailureCount() = testApplication {
        val id = insertStaff("aziz")
        val client = adminClient()

        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.login("aziz", "wrong password $it").status) }
        assertEquals(4, staffRow(id)[StaffTable.failedLoginCount])
        assertEquals(HttpStatusCode.OK, client.login("aziz").status)
        assertEquals(0, staffRow(id)[StaffTable.failedLoginCount])

        // One later failure does not lock it.
        assertEquals(HttpStatusCode.Unauthorized, client.login("aziz", "wrong again!!").status)
        assertEquals(HttpStatusCode.OK, client.login("aziz").status)
    }

    @Test
    fun ownPasswordChangeKeepsTheCurrentSessionAndEndsTheOthers() = testApplication {
        insertStaff("aziz")
        val client = adminClient()
        val current = client.signIn("aziz")
        val other = client.signIn("aziz")
        val newPassword = "a brand new password"

        val changed = client.post(AdminRoutes.AUTH_PASSWORD) {
            withSession(current)
            jsonBody(ChangePasswordRequest(currentPassword = PASSWORD, newPassword = newPassword))
        }
        assertEquals(HttpStatusCode.NoContent, changed.status)

        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUTH_ME) { withSession(current) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminRoutes.AUTH_ME) { withSession(other) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("aziz", PASSWORD).status)
        assertEquals(HttpStatusCode.OK, client.login("aziz", newPassword).status)
    }

    @Test
    fun wrongCurrentPasswordOrShortNewPasswordIsRefusedWith422() = testApplication {
        insertStaff("aziz")
        val client = adminClient()
        val session = client.signIn("aziz")

        val wrongCurrent = client.post(AdminRoutes.AUTH_PASSWORD) {
            withSession(session)
            jsonBody(ChangePasswordRequest(currentPassword = "not my password", newPassword = "a brand new password"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, wrongCurrent.status)
        assertEquals("currentPassword: wrong", wrongCurrent.error().message)

        val tooShort = client.post(AdminRoutes.AUTH_PASSWORD) {
            withSession(session)
            jsonBody(ChangePasswordRequest(currentPassword = PASSWORD, newPassword = "x".repeat(11)))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, tooShort.status)
        assertEquals("newPassword: length", tooShort.error().message)

        // Nothing changed: still signed in, the old password still works.
        assertEquals(HttpStatusCode.OK, client.get(AdminRoutes.AUTH_ME) { withSession(session) }.status)
        assertEquals(HttpStatusCode.OK, client.login("aziz", PASSWORD).status)
    }

    @Test
    fun malformedLoginBodyIsABadRequest() = testApplication {
        val client = adminClient()

        val response = client.post(AdminRoutes.AUTH_LOGIN) { jsonBody(mapOf("username" to "aziz")) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
