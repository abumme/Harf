package uz.abumme.harfgame.backend.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class JwtService(
    val secret: String = System.getenv("JWT_SECRET") ?: "harf-dev-jwt-secret-key-must-be-long-enough-32-bytes",
    val issuer: String = System.getenv("JWT_ISSUER") ?: "harf-backend",
    val audience: String = System.getenv("JWT_AUDIENCE") ?: "harf-client",
    val validityDuration: Duration = 15.minutes,
) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateAccessToken(userId: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withSubject(userId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + validityDuration.inWholeMilliseconds))
            .sign(algorithm)
    }

    fun verifyToken(token: String): String? {
        return try {
            val decoded = verifier.verify(token)
            decoded.subject
        } catch (_: Exception) {
            null
        }
    }
}
