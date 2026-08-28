package uz.abumme.harfgame.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.backend.auth.oauth.AppleOAuthVerifier
import uz.abumme.harfgame.backend.auth.oauth.GoogleOAuthVerifier
import uz.abumme.harfgame.backend.auth.oauth.OAuthIdentityResult
import uz.abumme.harfgame.data.auth.OAuthProvider
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OAuthVerificationTest {

    @Test
    fun testAppleOAuthVerifierValidAndInvalidTokens() = runBlocking {
        // Generate test RSA Key Pair
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID("apple-test-key-id")
            .build()

        val jwkSource = ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(JWKSet(rsaKey))
        val appleVerifier = AppleOAuthVerifier(customJwkSource = jwkSource)

        // 1. Build valid Apple token
        val now = System.currentTimeMillis()
        val claims = JWTClaimsSet.Builder()
            .issuer("https://appleid.apple.com")
            .subject("apple-user-subject-999")
            .audience("uz.abumme.harfgame")
            .issueTime(Date(now))
            .expirationTime(Date(now + 3600_000))
            .claim("email", "player@apple.test")
            .build()

        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple-test-key-id").build(),
            claims
        )
        signedJWT.sign(RSASSASigner(rsaKey))
        val validToken = signedJWT.serialize()

        val result = appleVerifier.verify(validToken)
        assertNotNull(result)
        assertEquals(OAuthProvider.APPLE, result.provider)
        assertEquals("apple-user-subject-999", result.subject)
        assertEquals("player@apple.test", result.email)

        // 2. Build expired Apple token
        val expiredClaims = JWTClaimsSet.Builder()
            .issuer("https://appleid.apple.com")
            .subject("apple-user-subject-999")
            .expirationTime(Date(now - 10_000))
            .build()
        val expiredSignedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple-test-key-id").build(),
            expiredClaims
        )
        expiredSignedJWT.sign(RSASSASigner(rsaKey))
        val expiredToken = expiredSignedJWT.serialize()

        assertNull(appleVerifier.verify(expiredToken))

        // 3. Build token with wrong issuer
        val wrongIssuerClaims = JWTClaimsSet.Builder()
            .issuer("https://wrong.issuer.com")
            .subject("apple-user-subject-999")
            .expirationTime(Date(now + 3600_000))
            .build()
        val wrongIssuerSigned = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple-test-key-id").build(),
            wrongIssuerClaims
        )
        wrongIssuerSigned.sign(RSASSASigner(rsaKey))

        assertNull(appleVerifier.verify(wrongIssuerSigned.serialize()))

        // 4. Invalid garbage token
        assertNull(appleVerifier.verify("not-a-valid-token"))
    }

    @Test
    fun testGoogleOAuthVerifierRejectsInvalidTokens() = runBlocking {
        val googleVerifier = GoogleOAuthVerifier(
            audiences = listOf("android-client-id", "ios-client-id", "desktop-client-id")
        )
        // Without mocked Google servers, any random garbage token fails gracefully with null
        assertNull(googleVerifier.verify("invalid.google.token"))
    }
}
