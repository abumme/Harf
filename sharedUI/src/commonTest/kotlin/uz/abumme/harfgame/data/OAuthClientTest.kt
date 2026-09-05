package uz.abumme.harfgame.data

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.auth.NoOpOAuthClient
import uz.abumme.harfgame.data.auth.OAuthResult
import kotlin.test.Test
import kotlin.test.assertEquals

class OAuthClientTest {

    @Test
    fun unconfiguredClientReportsNotConfiguredWithoutCrashing() = runTest {
        val client = NoOpOAuthClient(isGoogleSupported = true, isAppleSupported = true)
        assertEquals(OAuthResult.NotConfigured, client.signInWithGoogle())
        assertEquals(OAuthResult.NotConfigured, client.signInWithApple())
    }
}
