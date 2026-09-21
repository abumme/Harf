package uz.abumme.harfgame.backend.admin.words

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.insertPack
import uz.abumme.harfgame.backend.insertSuggestion
import uz.abumme.harfgame.backend.resetSuggestionData
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogCarryOverTest {

    private val clock = MutableClock()
    private val catalog = WordCatalogService(WordPackServerService(), clock)
    private val autoDecidedAt = Instant.parse("2026-09-01T08:00:00Z")
    private val editorDecidedAt = Instant.parse("2026-09-02T09:30:00Z")
    private lateinit var autoId: String
    private lateinit var editorId: String

    @BeforeTest
    fun setup() {
        resetSuggestionData()
        insertPack(
            "en", "5",
            guesses = listOf("Apple", "apple", "crane", "naïve", "hello", "wharf"),
            answers = listOf("wharf", "Crane"),
            schedule = listOf("wharf", "crane", "wharf"),
            catalog = false,
        )
        autoId = insertSuggestion("en", "hello", SuggestionStatus.ACCEPTED, decidedVia = "AUTO", decidedAt = autoDecidedAt)
        editorId = insertSuggestion("en", "apple", SuggestionStatus.ACCEPTED, decidedVia = "EDITOR", decidedAt = editorDecidedAt)
        insertSuggestion("en", "crane") // pending: not an origin
    }

    private fun rows() = transaction(DatabaseFactory.init()) {
        WordsTable.selectAll().where { WordsTable.lang eq "en" }.toList()
    }

    @Test
    fun thePublishedPackBecomesActiveCatalogWordsWithoutAVersionChange() = runBlocking {
        val before = WordPackServerService().getPack("en")!!

        assertEquals(mapOf("en" to 5), catalog.carryOver())

        val words = rows()
        assertEquals(setOf("apple", "crane", "naïve", "hello", "wharf"), words.map { it[WordsTable.text] }.toSet())
        assertTrue(words.all { it[WordsTable.status] == WordStatus.ACTIVE.name })
        assertEquals(before, WordPackServerService().getPack("en"))

        // Answers are the daily-eligible words; everything else is not.
        assertEquals(setOf("crane", "wharf"), words.filter { it[WordsTable.dailyEligible] }.map { it[WordsTable.text] }.toSet())

        // A legacy word that no longer validates is still carried over.
        assertEquals(WordStatus.ACTIVE.name, wordRow("en", "naïve")!![WordsTable.status])

        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.WORD_CATALOG_IMPORTED }
        assertEquals("SYSTEM", entry[StaffAuditLogTable.actorKind])
        assertEquals("en", entry[StaffAuditLogTable.lang])
        val details = entry[StaffAuditLogTable.details]!!
        assertTrue("\"count\":5" in details && "\"invalid\":1" in details, details)
    }

    @Test
    fun acceptedSuggestionsKeepTheirOrigin() = runBlocking {
        catalog.carryOver()

        val hello = wordRow("en", "hello")!!
        assertEquals(WordSource.AUTO.name, hello[WordsTable.wordSource])
        assertEquals(autoId, hello[WordsTable.suggestionId])
        assertEquals(autoDecidedAt, hello[WordsTable.createdAt])

        val apple = wordRow("en", "apple")!!
        assertEquals(WordSource.SUGGESTION.name, apple[WordsTable.wordSource])
        assertEquals(editorId, apple[WordsTable.suggestionId])
        assertEquals(editorDecidedAt, apple[WordsTable.createdAt])

        val crane = wordRow("en", "crane")!!
        assertEquals(WordSource.BUNDLED.name, crane[WordsTable.wordSource])
        assertNull(crane[WordsTable.suggestionId])
        assertNull(crane[WordsTable.createdByStaffId])
        assertEquals(clock.now, crane[WordsTable.createdAt])
    }

    @Test
    fun aSecondRunAfterStaffChangesIsANoOp() = runBlocking {
        catalog.carryOver()
        val admin = StaffPrincipal(UUID.randomUUID().toString(), "boss", null, Role.ADMIN, emptySet(), "s")
        catalog.remove(admin, wordRow("en", "apple")!![WordsTable.id])
        val published = WordPackServerService().getPack("en")!!
        assertEquals("6", published.version)
        assertFalse("apple" in published.guesses)

        assertEquals(emptyMap(), catalog.carryOver())

        assertEquals(5, rows().size)
        assertEquals(WordStatus.REMOVED.name, wordRow("en", "apple")!![WordsTable.status])
        assertEquals(published, WordPackServerService().getPack("en"))
        assertEquals(1, auditRows().count { it[StaffAuditLogTable.action] == AuditActions.WORD_CATALOG_IMPORTED })
    }
}
