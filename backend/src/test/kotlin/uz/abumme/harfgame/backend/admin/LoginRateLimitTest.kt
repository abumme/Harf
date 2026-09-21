package uz.abumme.harfgame.backend.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.Role
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginRateLimitTest {

    @BeforeTest
    fun setup() {
        resetAdminData()
    }

    @Test
    fun eleventhAttemptFromOneAddressIsRateLimitedWithoutCheckingCredentials() = testApplication {
        val bossId = insertStaff("boss", Role.ADMIN)
        val client = adminClient(adminBackend(config = AdminConfig()))

        repeat(10) { assertEquals(HttpStatusCode.Unauthorized, client.login("nobody-$it").status, "attempt ${it + 1}") }
        val auditBefore = auditRows().size

        val eleventh = client.login("boss")
        assertEquals(HttpStatusCode.TooManyRequests, eleventh.status)
        assertEquals("rate_limited", eleventh.error().error)
        assertNull(eleventh.headers[HttpHeaders.SetCookie])
        // No credential check happened: no sign-in, no audit entry.
        assertNull(staffRow(bossId)[StaffTable.lastLoginAt])
        assertEquals(auditBefore, auditRows().size)
    }

    @Test
    fun forwardedAddressesAreIgnoredUnlessTheProxyIsTrusted() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient(adminBackend(config = AdminConfig(trustProxyHeaders = false)))

        repeat(10) { client.login("nobody", forwardedFor = "203.0.113.${it + 1}") }
        assertEquals(HttpStatusCode.TooManyRequests, client.login("boss", forwardedFor = "198.51.100.1").status)
    }

    @Test
    fun clientsBehindTheTrustedProxyAreLimitedSeparately() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient(adminBackend(config = AdminConfig(trustProxyHeaders = true)))

        repeat(10) {
            assertEquals(HttpStatusCode.Unauthorized, client.login("nobody", forwardedFor = "203.0.113.10").status)
            // Caddy appends the client it saw; the last entry is the one that counts.
            assertEquals(HttpStatusCode.Unauthorized, client.login("nobody", forwardedFor = "10.9.9.9, 203.0.113.20").status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.login("boss", forwardedFor = "203.0.113.10").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.login("boss", forwardedFor = "203.0.113.20").status)
        assertEquals(HttpStatusCode.OK, client.login("boss", forwardedFor = "203.0.113.30").status)
    }
}
