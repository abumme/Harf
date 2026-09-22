package uz.abumme.harfgame.data

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.data.auth.NoOpOAuthClient
import uz.abumme.harfgame.data.auth.OAuthResult
import uz.abumme.harfgame.data.auth.WebGoogleOAuthClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossPlatformAuthTest {

    @Test
    fun unconfigured_clients_report_not_configured_without_crashing() = runTest {
        val blankWeb = WebGoogleOAuthClient("")
        assertFalse(blankWeb.isGoogleSupported)
        assertEquals(OAuthResult.NotConfigured, blankWeb.signInWithGoogle())
        assertEquals(OAuthResult.NotConfigured, blankWeb.signInWithApple())

        val configuredWeb = WebGoogleOAuthClient("test-google-client-id")
        assertTrue(configuredWeb.isGoogleSupported)
        assertFalse(configuredWeb.isAppleSupported)
    }

    @Test
    fun noop_oauth_client_handles_unsupported_providers() = runTest {
        val noop = NoOpOAuthClient(isGoogleSupported = false, isAppleSupported = false)
        assertFalse(noop.isGoogleSupported)
        assertFalse(noop.isAppleSupported)
        assertEquals(OAuthResult.NotConfigured, noop.signInWithGoogle())
        assertEquals(OAuthResult.NotConfigured, noop.signInWithApple())
    }
}
