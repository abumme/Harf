package uz.abumme.harfgame.backend.auth.oauth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

/**
 * Apple's token endpoints for Sign in with Apple: the authorization-code exchange at link time and
 * the revocation Apple requires when the account is deleted (TN3194). Both authenticate with a
 * short-lived client secret this app signs with its Sign in with Apple key.
 *
 * Revocation needs a token, not the user's subject, so the refresh token from the exchange is what
 * deletion revokes; an account linked before the key existed has none and is deleted without a
 * revocation call.
 */
interface AppleAuthClient {
    /** The refresh token Apple returns for [authorizationCode], or null when the exchange fails. */
    suspend fun exchangeCode(authorizationCode: String): String?

    /** Revokes [refreshToken] and every token Apple issued with it. Never throws. */
    suspend fun revoke(refreshToken: String)
}

/** Wired when no Sign in with Apple key is configured: linking still works, nothing is revoked. */
object NoOpAppleAuthClient : AppleAuthClient {
    override suspend fun exchangeCode(authorizationCode: String): String? = null
    override suspend fun revoke(refreshToken: String) { /* no key, nothing to revoke with */ }
}

/**
 * [AppleAuthClient] over `appleid.apple.com`. [clientId] is the audience the identity tokens carry —
 * for native iOS the app's bundle id — and [privateKeyPem] the `.p8` contents of the Sign in with
 * Apple key identified by [keyId], issued to [teamId].
 */
class HttpAppleAuthClient(
    private val clientId: String,
    private val teamId: String,
    private val keyId: String,
    privateKeyPem: String,
    private val baseUrl: String = "https://appleid.apple.com",
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    private val clock: Clock = Clock.systemUTC(),
) : AppleAuthClient {

    private val privateKey: ECPrivateKey = parsePkcs8(privateKeyPem)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchangeCode(authorizationCode: String): String? {
        val body = form(
            "client_id" to clientId,
            "client_secret" to clientSecret(),
            "code" to authorizationCode,
            "grant_type" to "authorization_code",
        )
        val response = post("/auth/token", body) ?: return null
        if (response.statusCode() != 200) {
            println("Apple code exchange failed: HTTP ${response.statusCode()} ${response.body()?.take(200)}")
            return null
        }
        return runCatching {
            json.parseToJsonElement(response.body()).jsonObject["refresh_token"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    override suspend fun revoke(refreshToken: String) {
        val body = form(
            "client_id" to clientId,
            "client_secret" to clientSecret(),
            "token" to refreshToken,
            "token_type_hint" to "refresh_token",
        )
        val response = post("/auth/revoke", body) ?: return
        // Apple answers 200 with an empty body on success; anything else is worth seeing in the log,
        // but never fails the deletion — the account must go either way.
        if (response.statusCode() != 200) {
            println("Apple token revocation failed: HTTP ${response.statusCode()} ${response.body()?.take(200)}")
        }
    }

    private fun post(path: String, body: String): HttpResponse<String>? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        http.send(request, HttpResponse.BodyHandlers.ofString())
    }.getOrElse { e ->
        println("Apple token request to $path failed: ${e::class.simpleName}: ${e.message}")
        null
    }

    /** The ES256 client secret Apple accepts in place of a password, signed with the `.p8` key. */
    internal fun clientSecret(): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .issuer(teamId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .audience("https://appleid.apple.com")
            .subject(clientId)
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(keyId)
            .type(JOSEObjectType.JWT)
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(privateKey)) }.serialize()
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }

    private companion object {
        /** The `.p8` body as a key, with or without the PEM armour and line breaks Apple ships. */
        fun parsePkcs8(pem: String): ECPrivateKey {
            val base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "\n")
                .filterNot { it.isWhitespace() }
            val der = Base64.getDecoder().decode(base64)
            return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der)) as ECPrivateKey
        }
    }
}
