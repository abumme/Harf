package uz.abumme.harfgame.backend

import uz.abumme.harfgame.backend.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
