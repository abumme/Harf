package uz.abumme.harfgame.backend

import kotlinx.coroutines.runBlocking
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The startup merge of deployed dictionaries into the word catalog. */
class WordPackMergeTest {

    @BeforeTest
    fun setup() = resetSuggestionData()

    /** A catalog whose bundled word-pack resources are exactly [files] (file name → lines). */
    private fun catalogWith(vararg files: Pair<String, List<String>>): WordCatalogService {
        val resources = files.toMap()
        return WordCatalogService(WordPackServerService(resourceLines = { name -> resources[name].orEmpty() }))
    }

    private val admin = StaffPrincipal(UUID.randomUUID().toString(), "boss", null, Role.ADMIN, emptySet(), "session")

    private suspend fun pack(lang: String) = WordPackServerService().getPack(lang)

    @Test
    fun missingWordsAreAddedWithOneVersionAdvance() = runBlocking {
        insertPack("en", "3", listOf("apple", "crane"))

        val added = catalogWith("en_guess.txt" to listOf("apple", "crane", "quilt", "zebra")).mergeBundled()

        assertEquals(mapOf("en" to 2), added)
        assertEquals(setOf("apple", "crane", "quilt", "zebra", "wharf"), pack("en")!!.guesses.toSet()) // wharf: the fixture's daily answer
        assertEquals("4", pack("en")!!.version)
        assertEquals(WordSource.BUNDLED.name, wordRow("en", "zebra")!![WordsTable.wordSource])
        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.WORD_BUNDLED_MERGED }
        assertEquals("SYSTEM", entry[StaffAuditLogTable.actorKind])
        assertEquals("en", entry[StaffAuditLogTable.lang])
        assertTrue("\"count\":2" in entry[StaffAuditLogTable.details]!!, entry[StaffAuditLogTable.details])
    }

    @Test
    fun aRestartWithNothingNewKeepsTheVersion() = runBlocking {
        insertPack("en", "3", listOf("apple"))
        val catalog = catalogWith("en_guess.txt" to listOf("apple", "quilt"))

        catalog.mergeBundled()
        assertEquals(emptyMap(), catalog.mergeBundled())
        assertEquals("4", pack("en")!!.version)
    }

    @Test
    fun storedWordsMissingFromTheResourcesAreKept() = runBlocking {
        insertPack("ru", "7", listOf("книга", "бырка")) // бырка: accepted by an editor after the build

        catalogWith("ru_guess.txt" to listOf("книга", "книги")).mergeBundled()

        assertEquals(setOf("книга", "бырка", "книги", "берег"), pack("ru")!!.guesses.toSet()) // берег: the fixture's daily answer
    }

    @Test
    fun answersScheduleAndEffectiveDateAreUntouched() = runBlocking {
        insertPack(
            "en", "1", listOf("apple", "crane"),
            answers = listOf("apple", "crane"), schedule = listOf("crane", "apple"), effectiveFrom = 20_454L,
        )

        catalogWith("en_guess.txt" to listOf("zebra")).mergeBundled()

        val merged = pack("en")!!
        assertEquals(listOf("apple", "crane"), merged.answers)
        assertEquals(listOf("crane", "apple"), merged.schedule)
        assertEquals(20_454L, merged.effectiveFrom)
        assertFalse(wordRow("en", "zebra")!![WordsTable.dailyEligible])
    }

    @Test
    fun answersFileWordsReachTheGuessesToo() = runBlocking {
        insertPack("en", "1", listOf("apple"))

        assertEquals(mapOf("en" to 1), catalogWith("en_answers.txt" to listOf("plumb")).mergeBundled())
        assertTrue("plumb" in pack("en")!!.guesses)
    }

    @Test
    fun wordsDifferingOnlyInCaseOrSpacingAreNotDuplicated() = runBlocking {
        insertPack("en", "2", listOf("Apple"))

        assertEquals(emptyMap(), catalogWith("en_guess.txt" to listOf("apple", " APPLE ", "", "# header")).mergeBundled())
        assertEquals("2", pack("en")!!.version)
    }

    @Test
    fun invalidBundledLinesAreSkipped() = runBlocking {
        insertPack("en", "1", listOf("apple"))

        assertEquals(mapOf("en" to 1), catalogWith("en_guess.txt" to listOf("cat", "naïve", "quilt")).mergeBundled())
        assertNull(wordRow("en", "cat"))
        assertNull(wordRow("en", "naïve"))
    }

    @Test
    fun languagesWithoutAStoredPackAreLeftToSeeding() = runBlocking {
        assertEquals(emptyMap(), catalogWith("kk_guess.txt" to listOf("кітап")).mergeBundled())
        assertNull(pack("kk"))
    }

    @Test
    fun aRemovedWordIsNotReAdded() = runBlocking {
        insertPack("en", "1", listOf("apple", "quilt"))
        val catalog = catalogWith("en_guess.txt" to listOf("apple", "quilt"))
        val quilt = wordRow("en", "quilt")!![WordsTable.id]
        catalog.remove(admin, quilt)
        val version = pack("en")!!.version

        assertEquals(emptyMap(), catalog.mergeBundled())

        assertFalse("quilt" in pack("en")!!.guesses)
        assertEquals(version, pack("en")!!.version)
        assertEquals(WordStatus.REMOVED.name, wordRow("en", "quilt")!![WordsTable.status])
    }

    @Test
    fun theOldSpellingOfAnEditedWordIsNotReAdded() = runBlocking {
        insertPack("en", "1", listOf("apple", "qwilt"))
        val catalog = catalogWith("en_guess.txt" to listOf("apple", "qwilt"))
        catalog.edit(admin, wordRow("en", "qwilt")!![WordsTable.id], "quilt")
        val version = pack("en")!!.version

        assertEquals(emptyMap(), catalog.mergeBundled())

        val guesses = pack("en")!!.guesses
        assertTrue("quilt" in guesses)
        assertFalse("qwilt" in guesses)
        assertEquals(version, pack("en")!!.version)
    }
}
