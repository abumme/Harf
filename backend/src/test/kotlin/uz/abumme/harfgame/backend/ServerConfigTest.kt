package uz.abumme.harfgame.backend

import uz.abumme.harfgame.backend.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerConfigTest {

    @Test
    fun productionWithoutJwtSecretFailsFast() {
        assertFailsWith<IllegalStateException> {
            ServerConfig.requireSecureProductionConfig(
                env = { name -> if (name == "JWT_SECRET") null else "x" },
                production = true,
            )
        }
    }

    @Test
    fun productionWithJwtSecretStarts() {
        // Should not throw.
        ServerConfig.requireSecureProductionConfig(
            env = { "a-real-production-secret" },
            production = true,
        )
    }

    @Test
    fun developmentAllowsMissingSecret() {
        // Dev is the default; missing secret must not block startup.
        ServerConfig.requireSecureProductionConfig(
            env = { null },
            production = false,
        )
    }

    // An empty audience allowlist makes the verifier reject every token silently, which reads as a
    // broken client. It cost a debugging round once; the warning is the only signal there is.
    @Test
    fun emptyAudienceAllowlistsAreWarnedAbout() {
        val both = oauthConfigWarnings(googleClientIds = emptyList(), appleAudiences = emptyList())
        assertTrue(both.any { it.contains("GOOGLE_CLIENT_IDS") }, "Expected a Google warning in $both")
        assertTrue(both.any { it.contains("APPLE_AUDIENCES") }, "Expected an Apple warning in $both")

        val appleOnly = oauthConfigWarnings(googleClientIds = listOf("google-client"), appleAudiences = emptyList())
        assertEquals(1, appleOnly.size, "Only the empty allowlist warns: $appleOnly")
        assertContains(appleOnly.single(), "APPLE_AUDIENCES")
    }

    @Test
    fun configuredAudienceAllowlistsAreSilent() {
        assertEquals(
            emptyList(),
            oauthConfigWarnings(googleClientIds = listOf("google-client"), appleAudiences = listOf("uz.abumme.harfgame")),
        )
    }
}
