package uz.abumme.harfgame.backend.admin.words

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.MutableClock
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.auditRows
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.resetAdminData
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffAuditLogTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.BulkLineResult
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordCatalogServiceTest {

    private val clock = MutableClock()
    private lateinit var admin: StaffPrincipal
    private lateinit var worder: StaffPrincipal

    /** "badly" is blocklisted in English. */
    private fun catalog(blocked: List<String> = listOf("badly")) =
        WordCatalogService(WordPackServerService(), clock, blocklists = Blocklists(lines = { if (it == "en") blocked else emptyList() }))

    private val service get() = catalog()

    @BeforeTest
    fun setup() {
        // Every pack holds one daily answer ("wharf" in English), carried into the catalog.
        resetAdminData()
        admin = principal(insertStaff("boss", Role.ADMIN), "boss", Role.ADMIN, emptySet())
        worder = principal(insertStaff("dilnoza", Role.WORDER, listOf("ru")), "dilnoza", Role.WORDER, setOf("ru"))
    }

    private fun principal(id: String, username: String, role: Role, languages: Set<String>) =
        StaffPrincipal(id, username, null, role, languages, "session-$username")

    private suspend fun pack(lang: String = "en") = WordPackServerService().getPack(lang)!!

    private fun idOf(text: String, lang: String = "en") = wordRow(lang, text)!![WordsTable.id]

    private fun statusOf(text: String, lang: String = "en") = wordRow(lang, text)?.get(WordsTable.status)

    private fun actions() = auditRows().map { it[StaffAuditLogTable.action] }

    private suspend fun refusal(block: suspend () -> Unit): AdminApiException {
        val error = runCatching { block() }.exceptionOrNull()
        return error as? AdminApiException ?: throw AssertionError("expected a refusal, got $error")
    }

    // --- add and publish (2.4, 2.5) ---

    @Test
    fun addStoresTheNormalizedWordAsStaffAndPublishesTheCatalog() = runBlocking {
        val word = service.add(admin, "en", "  CRANE ")

        assertEquals("crane", word.text)
        assertEquals(5, word.graphemeCount)
        assertEquals(WordStatus.ACTIVE, word.status)
        assertEquals(WordSource.STAFF, word.source)
        assertEquals(admin.staffId, word.createdBy?.id)
        assertEquals("boss", word.createdBy?.username)
        assertEquals(clock.now.toEpochMilli(), word.createdAt)
        assertFalse(word.restored)

        val published = pack()
        assertEquals("2", published.version)
        assertEquals(listOf("crane", "wharf"), published.guesses)
        assertEquals(listOf("wharf"), published.answers)
        assertEquals(listOf("wharf"), published.schedule)

        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.WORD_ADDED }
        assertEquals("STAFF", entry[StaffAuditLogTable.actorKind])
        assertEquals(admin.staffId, entry[StaffAuditLogTable.actorStaffId])
        assertEquals(word.id, entry[StaffAuditLogTable.targetId])
        assertEquals("en", entry[StaffAuditLogTable.lang])
        assertTrue("\"crane\"" in entry[StaffAuditLogTable.details]!!)
    }

    @Test
    fun removingTheLastDailyWordIsRefusedAndRollsBackEverything() = runBlocking {
        val error = refusal { service.remove(admin, idOf("wharf")) }

        assertEquals(HttpStatusCode.UnprocessableEntity, error.status)
        assertEquals("pack: ${WordReasons.PACK_INTEGRITY}", error.message)
        assertFalse("wharf" in error.message.orEmpty() || "daily" in error.message.orEmpty())
        assertEquals(WordStatus.ACTIVE.name, statusOf("wharf"))
        assertEquals("1", pack().version)
        assertFalse(AuditActions.WORD_REMOVED in actions())
    }

    @Test
    fun addingARemovedSpellingRestoresTheSameWord() = runBlocking {
        val added = service.add(admin, "en", "crane")
        // Distinct instants per action: auditRows() orders by `at` alone, so entries sharing one
        // come back in any order and the ordering assertion below would flake.
        clock.advance(Duration.ofMinutes(1))
        service.remove(admin, added.id)
        clock.advance(Duration.ofMinutes(1))

        val restored = service.add(admin, "en", "Crane")

        assertTrue(restored.restored)
        assertEquals(added.id, restored.id)
        assertEquals(WordStatus.ACTIVE, restored.status)
        assertNull(restored.removedBy)
        assertEquals(1, transaction(DatabaseFactory.init()) { WordsTable.selectAll().where { WordsTable.text eq "crane" }.count() })
        assertEquals("4", pack().version)
        assertTrue("crane" in pack().guesses)
        assertEquals(listOf(AuditActions.WORD_ADDED, AuditActions.WORD_REMOVED, AuditActions.WORD_RESTORED), actions())
    }

    @Test
    fun invalidBlocklistedAndDuplicateWordsChangeNothing() = runBlocking {
        service.add(admin, "en", "crane")

        assertEquals("text: ${WordReasons.BAD_LENGTH}", refusal { service.add(admin, "en", "cat") }.message)
        assertEquals("text: ${WordReasons.NOT_TOKENIZABLE}", refusal { service.add(admin, "en", "crâne") }.message)
        assertEquals("text: ${WordReasons.BLOCKLISTED}", refusal { service.add(admin, "en", "Badly") }.message)
        val duplicate = refusal { service.add(admin, "en", "CRANE") }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("text: ${WordReasons.DUPLICATE}", duplicate.message)

        assertEquals("2", pack().version)
        assertNull(wordRow("en", "cat"))
        assertEquals(listOf(AuditActions.WORD_ADDED), actions())
    }

    @Test
    fun aLanguageWithoutAPackIsNotEditable() = runBlocking {
        assertEquals(HttpStatusCode.NotFound, refusal { service.add(admin, "de", "kranz") }.status)
    }

    // --- bulk (2.5) ---

    @Test
    fun bulkAddReportsEachLineAndPublishesOnce() = runBlocking {
        service.add(admin, "en", "quilt")
        service.remove(admin, service.add(admin, "en", "plumb").id)
        val before = pack().version

        val result = service.bulkAdd(admin, "en", listOf("crane", "", "quilt", " CRANE", "naïve", "badly", "plumb", "cat"))

        assertEquals(
            listOf(
                BulkLineResult(1, "crane", BulkLineOutcome.ADDED),
                BulkLineResult(3, "quilt", BulkLineOutcome.DUPLICATE),
                BulkLineResult(4, "crane", BulkLineOutcome.DUPLICATE),
                BulkLineResult(5, "naïve", BulkLineOutcome.INVALID, WordReasons.NOT_TOKENIZABLE),
                BulkLineResult(6, "badly", BulkLineOutcome.BLOCKLISTED, WordReasons.BLOCKLISTED),
                BulkLineResult(7, "plumb", BulkLineOutcome.RESTORED),
                BulkLineResult(8, "cat", BulkLineOutcome.INVALID, WordReasons.BAD_LENGTH),
            ),
            result.results,
        )
        val expected = (before.toInt() + 1).toString()
        assertEquals(expected, result.packVersion)
        assertEquals(expected, pack().version)
        assertEquals(listOf("crane", "plumb", "quilt", "wharf"), pack().guesses)
        assertEquals(WordSource.STAFF.name, wordRow("en", "crane")!![WordsTable.wordSource])
    }

    @Test
    fun bulkAddWithNothingNewPublishesNothing() = runBlocking {
        val result = service.bulkAdd(admin, "en", listOf("wharf", "WHARF", "cat", "badly", "  "))

        assertEquals("1", result.packVersion)
        assertEquals("1", pack().version)
        assertEquals(
            listOf(BulkLineOutcome.DUPLICATE, BulkLineOutcome.DUPLICATE, BulkLineOutcome.INVALID, BulkLineOutcome.BLOCKLISTED),
            result.results.map { it.outcome },
        )
        assertEquals(emptyList(), actions())
    }

    @Test
    fun anOversizedPasteIsRefusedWhole() = runBlocking {
        val lines = List(1001) { i -> "w" + "abcdefghij"[i % 10] + "rds" + "abcdefghij"[i / 100 % 10] }

        assertEquals("lines: ${WordReasons.TOO_MANY_LINES}", refusal { service.bulkAdd(admin, "en", lines) }.message)
        assertEquals("1", pack().version)
        assertEquals(listOf("wharf"), pack().guesses)
    }

    // --- edit, remove, restore (2.5) ---

    @Test
    fun editRespellsInPlaceAndKeepsTheOldSpellingAsRemoved() = runBlocking {
        val added = service.add(admin, "en", "qwilt")
        clock.advance(Duration.ofMinutes(5))

        val edited = service.edit(worderFor("en"), added.id, "Quilt")

        assertEquals(added.id, edited.id)
        assertEquals("quilt", edited.text)
        assertEquals(WordSource.STAFF, edited.source)
        assertEquals(admin.staffId, edited.createdBy?.id)
        assertEquals("maria", edited.updatedBy?.username)
        val tombstone = wordRow("en", "qwilt")!!
        assertEquals(WordStatus.REMOVED.name, tombstone[WordsTable.status])
        assertEquals(WordSource.STAFF.name, tombstone[WordsTable.wordSource])
        assertNotEquals(added.id, tombstone[WordsTable.id])
        assertEquals(clock.now, tombstone[WordsTable.removedAt])
        assertTrue("quilt" in pack().guesses && "qwilt" !in pack().guesses)
        assertEquals("3", pack().version)

        val entry = auditRows().single { it[StaffAuditLogTable.action] == AuditActions.WORD_EDITED }
        val change = Json.parseToJsonElement(entry[StaffAuditLogTable.details]!!).jsonObject["text"]!!.jsonObject
        assertEquals("\"qwilt\"", change["from"].toString())
        assertEquals("\"quilt\"", change["to"].toString())
        assertEquals(added.id, entry[StaffAuditLogTable.targetId])
    }

    @Test
    fun editConflictsChangeNothing() = runBlocking {
        service.add(admin, "en", "crane")
        val quilt = service.add(admin, "en", "quilt")
        val plumb = service.add(admin, "en", "plumb")
        service.remove(admin, plumb.id)
        val version = pack().version

        assertEquals("text: ${WordReasons.DUPLICATE}", refusal { service.edit(admin, quilt.id, "CRANE") }.message)
        val removed = refusal { service.edit(admin, quilt.id, "plumb") }
        assertEquals(HttpStatusCode.Conflict, removed.status)
        assertEquals("text: ${WordReasons.REMOVED_EXISTS}", removed.message)
        assertEquals("text: ${WordReasons.NOT_ACTIVE}", refusal { service.edit(admin, plumb.id, "zebra") }.message)
        assertEquals("text: ${WordReasons.BAD_LENGTH}", refusal { service.edit(admin, quilt.id, "cat") }.message)

        assertEquals(version, pack().version)
        assertEquals(WordStatus.ACTIVE.name, statusOf("quilt"))
        assertNull(wordRow("en", "zebra"))
    }

    @Test
    fun editingToTheSameNormalizedSpellingChangesNothing() = runBlocking {
        val crane = service.add(admin, "en", "crane")
        assertEquals("crane", service.edit(admin, crane.id, " CRANE ").text)
        assertEquals("2", pack().version)
    }

    @Test
    fun removedWordsLeaveThePackAndComeBackOnRestore() = runBlocking {
        val crane = service.add(admin, "en", "crane")
        clock.advance(Duration.ofMinutes(1))

        val removed = service.remove(admin, crane.id)
        assertEquals(WordStatus.REMOVED, removed.status)
        assertEquals(admin.staffId, removed.removedBy?.id)
        assertEquals(clock.now.toEpochMilli(), removed.removedAt)
        assertFalse("crane" in pack().guesses)
        assertEquals("3", pack().version)

        service.remove(admin, crane.id) // already removed: nothing to publish
        assertEquals("3", pack().version)

        val restored = service.restore(admin, crane.id)
        assertEquals(WordStatus.ACTIVE, restored.status)
        assertNull(restored.removedAt)
        assertTrue("crane" in pack().guesses)
        assertEquals("4", pack().version)
        assertEquals(
            listOf(AuditActions.WORD_ADDED, AuditActions.WORD_REMOVED, AuditActions.WORD_RESTORED),
            actions(),
        )
    }

    @Test
    fun restoringAWordBlocklistedSinceIsRefused() = runBlocking {
        val gnome = catalog(blocked = emptyList()).add(admin, "en", "gnome")
        catalog(blocked = emptyList()).remove(admin, gnome.id)

        val error = refusal { catalog(blocked = listOf("gnome")).restore(admin, gnome.id) }

        assertEquals("text: ${WordReasons.BLOCKLISTED}", error.message)
        assertEquals(WordStatus.REMOVED.name, statusOf("gnome"))
        assertEquals("3", pack().version)
    }

    // --- scope ---

    @Test
    fun aWorderChangesOnlyTheirLanguages() = runBlocking {
        val crane = service.add(admin, "en", "crane")
        val version = pack().version

        for (attempt in listOf<suspend () -> Unit>(
            { service.add(worder, "en", "quilt") },
            { service.bulkAdd(worder, "en", listOf("quilt")) },
            { service.edit(worder, crane.id, "quilt") },
            { service.remove(worder, crane.id) },
            { service.restore(worder, crane.id) },
            { service.list(worder, WordQuery("en")) },
            { service.check(worder, "en", "quilt") },
        )) {
            assertEquals(HttpStatusCode.Forbidden, refusal(attempt).status)
        }
        assertEquals(version, pack().version)
        assertEquals(WordStatus.ACTIVE.name, statusOf("crane"))
        assertNull(wordRow("en", "quilt"))

        val russian = service.add(worder, "ru", "Ёлка")
        assertEquals("елка", russian.text)
        assertEquals("2", pack("ru").version)
    }

    // --- reads ---

    @Test
    fun listFiltersSearchesSortsAndPages() = runBlocking {
        for (word in listOf("crane", "quilt", "plumb")) {
            service.add(admin, "en", word)
            clock.advance(Duration.ofMinutes(1))
        }
        service.remove(admin, idOf("plumb"))
        service.add(worder, "ru", "ёлка")

        val active = service.list(admin, WordQuery("en", size = 2))
        assertEquals(3, active.total)
        assertEquals(listOf("crane", "quilt"), active.items.map { it.text })
        assertEquals(listOf("wharf"), service.list(admin, WordQuery("en", size = 2, page = 1)).items.map { it.text })

        val removed = service.list(admin, WordQuery("en", status = WordStatus.REMOVED)).items.single()
        assertEquals("plumb", removed.text)
        assertEquals("boss", removed.removedBy?.username)

        assertEquals(4, service.list(admin, WordQuery("en", status = null)).total)
        assertEquals(listOf("quilt", "crane"), service.list(admin, WordQuery("en", source = WordSource.STAFF, sort = WordQuery.Sort.CREATED, descending = true)).items.map { it.text })
        assertEquals(listOf("wharf"), service.list(admin, WordQuery("en", source = WordSource.BUNDLED)).items.map { it.text })
        assertEquals(2, service.list(admin, WordQuery("en", addedBy = admin.staffId)).total)
        // crane, quilt and plumb were added one minute apart; the window [quilt, plumb) holds only quilt.
        val window = WordQuery("en", source = WordSource.STAFF, addedFrom = clock.now.minus(Duration.ofMinutes(2)), addedTo = clock.now.minus(Duration.ofMinutes(1)))
        assertEquals(listOf("quilt"), service.list(admin, window).items.map { it.text })
        assertEquals(listOf("quilt"), service.list(admin, WordQuery("en", q = "UIL")).items.map { it.text })
        assertEquals(0, service.list(admin, WordQuery("en", q = "%")).total)

        // A search is normalized with the language's rules: "Ё" finds words stored with "е".
        assertEquals(listOf("елка"), service.list(worder, WordQuery("ru", q = "Ёл")).items.map { it.text })
    }

    @Test
    fun checkReportsWhatAnAddWouldDoWithoutDoingIt() = runBlocking {
        service.add(admin, "en", "crane")
        service.remove(admin, service.add(admin, "en", "plumb").id)
        val version = pack().version

        assertEquals(WordCheckOutcome.VALID, service.check(admin, "en", "Quilt").outcome)
        assertEquals("quilt", service.check(admin, "en", "Quilt").normalized)
        assertEquals(WordCheckOutcome.DUPLICATE, service.check(admin, "en", "CRANE").outcome)
        assertEquals(WordCheckOutcome.RESTORABLE, service.check(admin, "en", "plumb").outcome)
        val invalid = service.check(admin, "en", "cat")
        assertEquals(WordCheckOutcome.INVALID to WordReasons.BAD_LENGTH, invalid.outcome to invalid.reason)
        assertEquals(3, invalid.graphemeCount)
        assertEquals(WordCheckOutcome.BLOCKLISTED, service.check(admin, "en", "badly").outcome)

        assertEquals(version, pack().version)
        assertNull(wordRow("en", "quilt"))
    }

    /** A WORDER with English, for attribution that differs from the ADMIN. */
    private fun worderFor(lang: String) =
        principal(insertStaff("maria", Role.WORDER, listOf(lang)), "maria", Role.WORDER, setOf(lang))
}
