package uz.abumme.harfgame.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import uz.abumme.harfgame.backend.auth.oauth.HttpAppleAuthClient
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The client secret Apple accepts in place of a password, as its token endpoints require it. */
class AppleClientSecretTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val pem = "-----BEGIN PRIVATE KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) +
        "\n-----END PRIVATE KEY-----\n"

    private val now = Instant.parse("2026-09-30T10:00:00Z")

    private fun client() = HttpAppleAuthClient(
        clientId = "uz.abumme.harfgame",
        teamId = "3827YQ294K",
        keyId = "KEY123",
        privateKeyPem = pem,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun secretIsSignedWithTheKeyAndCarriesApplesClaims() {
        val jwt = SignedJWT.parse(client().clientSecret())

        assertEquals(JWSAlgorithm.ES256, jwt.header.algorithm)
        assertEquals("KEY123", jwt.header.keyID, "Apple routes the signature by key id")
        assertTrue(jwt.verify(ECDSAVerifier(keyPair.public as ECPublicKey)), "signed with the .p8 key")

        val claims = jwt.jwtClaimsSet
        assertEquals("3827YQ294K", claims.issuer, "the team owns the key")
        assertEquals("uz.abumme.harfgame", claims.subject, "the client is the app the tokens are for")
        assertEquals(listOf("https://appleid.apple.com"), claims.audience)
        assertEquals(now, claims.issueTime.toInstant())
        assertTrue(claims.expirationTime.toInstant().isAfter(now), "short-lived, but not already expired")
    }

    @Test
    fun theKeyIsReadWithOrWithoutItsPemArmour() {
        val bare = Base64.getEncoder().encodeToString((keyPair.private as ECPrivateKey).encoded)
        val fromBare = HttpAppleAuthClient("app", "team", "kid", bare, clock = Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(
            SignedJWT.parse(client().clientSecret()).jwtClaimsSet.audience,
            SignedJWT.parse(fromBare.clientSecret()).jwtClaimsSet.audience,
        )
    }
}
