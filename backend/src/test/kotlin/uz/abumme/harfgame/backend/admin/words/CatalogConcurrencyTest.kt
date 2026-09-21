package uz.abumme.harfgame.backend.admin.words

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.resetAdminData
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.words.WordStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Writers of one language are serialized by the pack row lock: every change lands and publishes its own version. */
class CatalogConcurrencyTest {

    private lateinit var admin: StaffPrincipal
    private lateinit var other: StaffPrincipal
    private val catalog = WordCatalogService(WordPackServerService())

    @BeforeTest
    fun setup() {
        resetAdminData()
        admin = StaffPrincipal(insertStaff("boss", Role.ADMIN), "boss", null, Role.ADMIN, emptySet(), "s1")
        other = StaffPrincipal(insertStaff("maria", Role.WORDER, listOf("en")), "maria", null, Role.WORDER, setOf("en"), "s2")
    }

    private fun <T> together(vararg blocks: suspend () -> T): List<T> = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val jobs = blocks.map { block -> async(Dispatchers.IO) { gate.await(); block() } }
        gate.complete(Unit)
        jobs.awaitAll()
    }

    private suspend fun version() = WordPackServerService().getPack("en")!!.version.toInt()

    @Test
    fun concurrentAddsOfDifferentWordsBothLand() = runBlocking {
        repeat(5) { round ->
            val before = version()
            val (first, second) = listOf("crane", "quilt").map { it.dropLast(1) + "abcde"[round] }
            together({ catalog.add(admin, "en", first) }, { catalog.add(other, "en", second) })

            assertEquals(before + 2, version(), "round $round")
            val guesses = WordPackServerService().getPack("en")!!.guesses
            assertTrue(first in guesses && second in guesses, "round $round: $guesses")
        }
    }

    @Test
    fun anEditRacingARemoveBothLand() = runBlocking {
        val qwilt = catalog.add(admin, "en", "qwilt")
        val plumb = catalog.add(admin, "en", "plumb")
        val before = version()

        together({ catalog.edit(admin, qwilt.id, "quilt") }, { catalog.remove(other, plumb.id) })

        assertEquals(before + 2, version())
        val guesses = WordPackServerService().getPack("en")!!.guesses
        assertTrue("quilt" in guesses)
        assertFalse("qwilt" in guesses || "plumb" in guesses)
        assertEquals(WordStatus.REMOVED.name, wordRow("en", "plumb")!![WordsTable.status])
        assertEquals(WordStatus.REMOVED.name, wordRow("en", "qwilt")!![WordsTable.status])
    }

    @Test
    fun anEditRacingARemoveOfTheSameWordNeverFailsWithASerializationError() = runBlocking {
        val qwilt = catalog.add(admin, "en", "qwilt")
        val before = version()

        val outcomes = together(
            { runCatching { catalog.edit(admin, qwilt.id, "quilt") } },
            { runCatching { catalog.remove(other, qwilt.id) } },
        )

        // Whichever ran second saw the first one's change: remove after edit removes "quilt"; edit after remove is a 409.
        outcomes.forEach { outcome ->
            val error = outcome.exceptionOrNull()
            assertTrue(error == null || error is uz.abumme.harfgame.backend.admin.AdminApiException, "unexpected $error")
        }
        assertEquals(WordStatus.REMOVED.name, wordRow("en", "qwilt")!![WordsTable.status])
        assertTrue(version() in before + 1..before + 2)
    }
}
