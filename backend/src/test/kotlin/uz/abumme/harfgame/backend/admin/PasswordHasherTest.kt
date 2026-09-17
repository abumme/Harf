package uz.abumme.harfgame.backend.admin

import uz.abumme.harfgame.backend.admin.auth.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun hashRoundTripsAndUsesOwaspParameters() {
        val hash = hasher.hashBlocking("correct horse battery staple")

        assertTrue(hash.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"), hash)
        assertTrue(hasher.verifyBlocking("correct horse battery staple", hash))
        assertFalse(hasher.needsRehash(hash))
    }

    @Test
    fun wrongPasswordIsRefused() {
        val hash = hasher.hashBlocking("correct horse battery staple")

        assertFalse(hasher.verifyBlocking("correct horse battery stapler", hash))
        assertFalse(hasher.verifyBlocking("", hash))
    }

    @Test
    fun saltMakesEveryHashDifferentAndNoneContainsThePassword() {
        val password = "correct horse battery staple"
        val first = hasher.hashBlocking(password)
        val second = hasher.hashBlocking(password)

        assertNotEquals(first, second)
        assertNotEquals(password, first)
        assertFalse(first.contains(password))
    }

    @Test
    fun tamperedOrMalformedHashIsRefused() {
        val password = "correct horse battery staple"
        val hash = hasher.hashBlocking(password)
        val parts = hash.split('$')

        // Flip one character of the stored hash.
        val digest = parts[5]
        val flipped = (if (digest[0] == 'A') 'B' else 'A') + digest.substring(1)
        assertFalse(hasher.verifyBlocking(password, parts.take(5).joinToString("$") + "$" + flipped))

        // A different salt.
        val salt = parts[4]
        val otherSalt = (if (salt[0] == 'A') 'B' else 'A') + salt.substring(1)
        assertFalse(hasher.verifyBlocking(password, listOf("", parts[1], parts[2], parts[3], otherSalt, digest).joinToString("$")))

        assertFalse(hasher.verifyBlocking(password, hash.replace("argon2id", "argon2i")))
        assertFalse(hasher.verifyBlocking(password, "not a hash"))
        assertFalse(hasher.verifyBlocking(password, ""))
        assertFalse(hasher.verifyBlocking(password, hash.replace("m=19456", "m=abc")))
        // Parameters outside sane bounds are never used to derive.
        assertFalse(hasher.verifyBlocking(password, hash.replace("m=19456", "m=99999999")))
    }

    @Test
    fun parsesParametersAndDetectsOutdatedOnes() {
        val weaker = PasswordHasher(memoryKib = 8_192, iterations = 1, parallelism = 1)
        val oldHash = weaker.hashBlocking("correct horse battery staple")

        val parsed = assertNotNull(hasher.parse(oldHash))
        assertEquals(8_192, parsed.params.memoryKib)
        assertEquals(1, parsed.params.iterations)
        assertEquals(1, parsed.params.parallelism)
        assertEquals(16, parsed.salt.size)
        assertEquals(32, parsed.hash.size)

        // An old hash still verifies, but should be upgraded.
        assertTrue(hasher.verifyBlocking("correct horse battery staple", oldHash))
        assertTrue(hasher.needsRehash(oldHash))
        assertTrue(hasher.needsRehash("garbage"))
        assertNull(hasher.parse("\$argon2id\$v=19\$m=19456,t=2\$c2FsdHNhbHQ\$aGFzaGhhc2hoYXNoaGFzaA"))
    }

    @Test
    fun dummyVerificationNeverSucceeds() {
        assertFalse(hasher.verifyDummyBlocking("anything at all"))
    }
}
