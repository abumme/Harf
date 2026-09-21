package uz.abumme.harfgame.backend.admin.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id password hashing (Bouncy Castle, pure Java). Hashes are PHC strings
 * (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`), so the parameters travel with each hash: raising them later
 * keeps old hashes verifiable, and [needsRehash] tells when to upgrade one after a successful sign-in.
 *
 * Each hash allocates [memoryKib] of memory; callers run it on [Dispatchers.IO] through the suspending wrappers,
 * and the login rate limit bounds how many run at once.
 */
class PasswordHasher(
    /** OWASP baseline: m = 19 MiB, t = 2, p = 1. */
    private val memoryKib: Int = 19_456,
    private val iterations: Int = 2,
    private val parallelism: Int = 1,
) {
    private val random = SecureRandom()

    /** Verified when the username is unknown, so the response takes as long as for a real account. */
    private val dummyHash: String by lazy { hashBlocking(randomPassword()) }

    fun hashBlocking(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, Params(memoryKib, iterations, parallelism), HASH_BYTES)
        return "\$argon2id\$v=19\$m=$memoryKib,t=$iterations,p=$parallelism\$${b64(salt)}\$${b64(hash)}"
    }

    /** True when [password] matches [phc]; false for a wrong password or a malformed or tampered hash string. */
    fun verifyBlocking(password: String, phc: String): Boolean {
        val parsed = parse(phc) ?: return false
        val actual = derive(password, parsed.salt, parsed.params, parsed.hash.size)
        return MessageDigest.isEqual(actual, parsed.hash)
    }

    /** Spends one verification against a fixed dummy hash; the result is always false. */
    fun verifyDummyBlocking(password: String): Boolean {
        verifyBlocking(password, dummyHash)
        return false
    }

    /** True when [phc] was made with other parameters than the current ones (or cannot be parsed). */
    fun needsRehash(phc: String): Boolean =
        parse(phc)?.params != Params(memoryKib, iterations, parallelism)

    suspend fun hash(password: String): String = withContext(Dispatchers.IO) { hashBlocking(password) }

    suspend fun verify(password: String, phc: String): Boolean = withContext(Dispatchers.IO) { verifyBlocking(password, phc) }

    suspend fun verifyDummy(password: String): Boolean = withContext(Dispatchers.IO) { verifyDummyBlocking(password) }

    internal data class Params(val memoryKib: Int, val iterations: Int, val parallelism: Int)

    internal class Parsed(val params: Params, val salt: ByteArray, val hash: ByteArray)

    internal fun parse(phc: String): Parsed? {
        // "", "argon2id", "v=19", "m=…,t=…,p=…", salt, hash
        val parts = phc.split('$')
        if (parts.size != 6 || parts[0].isNotEmpty() || parts[1] != "argon2id" || parts[2] != "v=19") return null
        val values = parts[3].split(',').associate { entry ->
            val (key, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return null
            key to (value.toIntOrNull() ?: return null)
        }
        val params = Params(
            memoryKib = values["m"]?.takeIf { it in 8..MAX_MEMORY_KIB } ?: return null,
            iterations = values["t"]?.takeIf { it in 1..MAX_ITERATIONS } ?: return null,
            parallelism = values["p"]?.takeIf { it in 1..MAX_PARALLELISM } ?: return null,
        )
        val salt = unb64(parts[4])?.takeIf { it.size >= 8 } ?: return null
        val hash = unb64(parts[5])?.takeIf { it.size >= 16 } ?: return null
        return Parsed(params, salt, hash)
    }

    private fun derive(password: String, salt: ByteArray, params: Params, length: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(params.memoryKib)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()
        val out = ByteArray(length)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private fun randomPassword(): String = b64(ByteArray(24).also(random::nextBytes))

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun unb64(text: String): ByteArray? =
        try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32

        // Bounds on parameters read from a stored hash, so a tampered row cannot make verification allocate
        // gigabytes or spin for minutes.
        const val MAX_MEMORY_KIB = 262_144
        const val MAX_ITERATIONS = 10
        const val MAX_PARALLELISM = 4
    }
}
