package uz.abumme.harfgame.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.auth.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSerializationTest {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testAnonymousAuthResponseSerialization() {
        val original = AnonymousAuthResponse(
            userId = "user-123",
            tokens = TokenPairDto(
                accessToken = "access-token-abc",
                refreshToken = "refresh-token-xyz"
            )
        )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<AnonymousAuthResponse>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun testLinkAccountRequestSerialization() {
        val originalGoogle = LinkAccountRequest(
            provider = OAuthProvider.GOOGLE,
            idToken = "google-id-token"
        )
        val serializedGoogle = json.encodeToString(originalGoogle)
        val deserializedGoogle = json.decodeFromString<LinkAccountRequest>(serializedGoogle)
        assertEquals(originalGoogle, deserializedGoogle)

        val originalApple = LinkAccountRequest(
            provider = OAuthProvider.APPLE,
            idToken = "apple-id-token"
        )
        val serializedApple = json.encodeToString(originalApple)
        val deserializedApple = json.decodeFromString<LinkAccountRequest>(serializedApple)
        assertEquals(originalApple, deserializedApple)
    }

    @Test
    fun testLinkAccountDisplayNameRoundTrip() {
        // With a confirmed display name.
        val withName = LinkAccountRequest(
            provider = OAuthProvider.GOOGLE,
            idToken = "google-id-token",
            nonce = "nonce-1",
            displayName = "Ada Lovelace",
        )
        assertEquals(withName, json.decodeFromString(json.encodeToString(withName)))

        // Absent display name stays null.
        val withoutName = LinkAccountRequest(provider = OAuthProvider.APPLE, idToken = "apple-id-token")
        val decoded = json.decodeFromString<LinkAccountRequest>(json.encodeToString(withoutName))
        assertEquals(withoutName, decoded)
        assertEquals(null, decoded.displayName)

        val respWithName = LinkAccountResponse(
            userId = "user-1",
            tokens = TokenPairDto(accessToken = "a", refreshToken = "r"),
            displayName = "Ada Lovelace",
        )
        assertEquals(respWithName, json.decodeFromString(json.encodeToString(respWithName)))

        val respWithoutName = LinkAccountResponse(
            userId = "user-1",
            tokens = TokenPairDto(accessToken = "a", refreshToken = "r"),
        )
        val respDecoded = json.decodeFromString<LinkAccountResponse>(json.encodeToString(respWithoutName))
        assertEquals(respWithoutName, respDecoded)
        assertEquals(null, respDecoded.displayName)
    }

    @Test
    fun testRefreshRequestAndResponseSerialization() {
        val req = RefreshRequest(refreshToken = "refresh-token-xyz")
        val reqSerialized = json.encodeToString(req)
        val reqDeserialized = json.decodeFromString<RefreshRequest>(reqSerialized)
        assertEquals(req, reqDeserialized)

        val resp = RefreshResponse(
            tokens = TokenPairDto(
                accessToken = "new-access",
                refreshToken = "new-refresh"
            )
        )
        val respSerialized = json.encodeToString(resp)
        val respDeserialized = json.decodeFromString<RefreshResponse>(respSerialized)
        assertEquals(resp, respDeserialized)
    }
}
