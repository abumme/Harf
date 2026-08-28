package uz.abumme.harfgame.backend.auth.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import uz.abumme.harfgame.data.auth.OAuthProvider
import java.net.URI
import java.util.Date

class AppleOAuthVerifier(
    val jwksUri: String = "https://appleid.apple.com/auth/keys",
    private val customJwkSource: JWKSource<SecurityContext>? = null,
) : OAuthVerifier {

    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> by lazy {
        val processor = DefaultJWTProcessor<SecurityContext>()
        val keySource = customJwkSource ?: RemoteJWKSet(URI(jwksUri).toURL())
        val keySelector = JWSVerificationKeySelector(
            setOf(JWSAlgorithm.RS256),
            keySource
        )
        processor.jwsKeySelector = keySelector
        processor.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            null,
            setOf("sub", "iss", "exp")
        )
        processor
    }

    override suspend fun verify(idToken: String): OAuthIdentityResult? {
        return try {
            val claims = jwtProcessor.process(idToken, null)
            if (claims.issuer != "https://appleid.apple.com") return null
            if (claims.expirationTime != null && claims.expirationTime.before(Date())) return null
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
}
