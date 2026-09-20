package uz.abumme.harfgame.backend.auth.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import uz.abumme.harfgame.data.auth.OAuthProvider
import java.net.URI
import java.security.MessageDigest

/**
 * Verifies Sign in with Apple identity tokens: signature (Apple JWKS), issuer, expiry, required
 * subject, that the audience is one of this app's registered [audiences] (App/Services IDs), and —
 * when the request bound a nonce — that the token's `nonce` claim matches. A valid signature alone
 * is not sufficient: a token minted for another app's audience, or missing an audience, is rejected.
 */
class AppleOAuthVerifier(
    val audiences: List<String> = emptyList(),
    val jwksUri: String = "https://appleid.apple.com/auth/keys",
    private val customJwkSource: JWKSource<SecurityContext>? = null,
) : OAuthVerifier {

    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> by lazy {
        val processor = DefaultJWTProcessor<SecurityContext>()
        // JWKSourceBuilder (nimbus 10.x) replaces the deprecated RemoteJWKSet and adds caching,
        // refresh-ahead and outage tolerance, so a transient Apple JWKS fetch failure no longer
        // fails every login.
        val keySource = customJwkSource ?: JWKSourceBuilder.create<SecurityContext>(URI(jwksUri).toURL()).build()
        val keySelector = JWSVerificationKeySelector(
            setOf(JWSAlgorithm.RS256),
            keySource
        )
        processor.jwsKeySelector = keySelector
        processor.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            null,
            setOf("sub", "iss", "exp", "aud")
        )
        processor
    }

    override suspend fun verify(idToken: String, expectedNonce: String?): OAuthIdentityResult? {
        return try {
            val claims = jwtProcessor.process(idToken, null)
            if (claims.issuer != "https://appleid.apple.com") return null
            // Expiry is enforced by DefaultJWTClaimsVerifier ("exp" is a required claim above), so
            // no manual expirationTime re-check is needed.

            // Audience must be explicitly allow-listed for this app; an unconfigured allowlist
            // rejects everything rather than accepting an unscoped token.
            val tokenAudiences = claims.audience ?: emptyList()
            if (audiences.isEmpty() || tokenAudiences.none { it in audiences }) return null

            // Apple stores the SHA-256 hex digest of the raw nonce in the `nonce` claim.
            if (expectedNonce != null) {
                val tokenNonce = claims.getStringClaim("nonce") ?: return null
                if (tokenNonce != sha256Hex(expectedNonce)) return null
            }

            val subject = claims.subject ?: return null
            OAuthIdentityResult(
                provider = OAuthProvider.APPLE,
                subject = subject,
                email = claims.getStringClaim("email")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
