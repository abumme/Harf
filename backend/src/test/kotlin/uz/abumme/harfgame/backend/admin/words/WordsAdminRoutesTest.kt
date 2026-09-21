package uz.abumme.harfgame.backend.admin.words

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.StaffSession
import uz.abumme.harfgame.backend.admin.adminClient
import uz.abumme.harfgame.backend.admin.error
import uz.abumme.harfgame.backend.admin.insertStaff
import uz.abumme.harfgame.backend.admin.jsonBody
import uz.abumme.harfgame.backend.admin.resetAdminData
import uz.abumme.harfgame.backend.admin.signIn
import uz.abumme.harfgame.backend.admin.withSession
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.wordRow
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.Patch
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import uz.abumme.harfgame.data.admin.words.AddWordRequest
import uz.abumme.harfgame.data.admin.words.BulkAddRequest
import uz.abumme.harfgame.data.admin.words.BulkAddResult
import uz.abumme.harfgame.data.admin.words.CheckWordRequest
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.EditWordRequest
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.admin.words.WordParams
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.wordpack.WordPackDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordsAdminRoutesTest {

    @BeforeTest
    fun setup() {
        // One valid pack per language, each with a single daily answer ("wharf" in English, "өзен" in Kazakh).
        resetAdminData()
    }

    private suspend fun HttpClient.addWord(session: StaffSession, lang: String, text: String): HttpResponse =
        post(AdminRoutes.WORDS) { withSession(session); jsonBody(AddWordRequest(lang, text)) }

    private suspend fun HttpClient.list(session: StaffSession, query: String): HttpResponse =
        get("${AdminRoutes.WORDS}?$query") { withSession(session) }

    private suspend fun packVersion(lang: String) = WordPackServerService().getPack(lang)!!.version

    @Test
    fun anAdminWorksInEveryLanguage() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        for ((lang, word) in mapOf("kk" to "қалам", "ru" to "Ёлка", "uz-latn" to "o'g'il", "en" to "Crane")) {
            val response = client.addWord(boss, lang, word)
            assertEquals(HttpStatusCode.Created, response.status, lang)
            val added = response.body<WordDto>()
            assertEquals(WordStatus.ACTIVE, added.status)
            assertFalse(added.restored)
            assertEquals("2", packVersion(lang), lang)
        }

        val page = client.list(boss, "${WordParams.LANG}=uz-latn&${WordParams.Q}=o%27g").body<PageDto<WordDto>>()
        assertEquals(listOf("oʻgʻil"), page.items.map { it.text })
        assertEquals(2, client.list(boss, "${WordParams.LANG}=en").body<PageDto<WordDto>>().total)
    }

    @Test
    fun aWorderIsRefusedOutsideTheirLanguagesAndNothingChanges() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("dilnoza", Role.WORDER, listOf("ru"))
        val client = adminClient()
        val boss = client.signIn("boss")
        val worder = client.signIn("dilnoza")
        val kazakh = client.addWord(boss, "kk", "қалам").body<WordDto>()
        val version = packVersion("kk")

        val attempts: List<suspend () -> HttpResponse> = listOf(
            { client.addWord(worder, "kk", "кітап") },
            { client.post(AdminRoutes.WORDS_BULK) { withSession(worder); jsonBody(BulkAddRequest("kk", listOf("кітап"))) } },
            { client.patch(AdminRoutes.word(kazakh.id)) { withSession(worder); jsonBody(EditWordRequest("кітап")) } },
            { client.post(AdminRoutes.wordRemove(kazakh.id)) { withSession(worder) } },
            { client.post(AdminRoutes.wordRestore(kazakh.id)) { withSession(worder) } },
            { client.list(worder, "${WordParams.LANG}=kk") },
            { client.post(AdminRoutes.WORDS_CHECK) { withSession(worder); jsonBody(CheckWordRequest("kk", "кітап")) } },
        )
        for (attempt in attempts) {
            val response = attempt()
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("forbidden", response.error().error)
        }
        assertEquals(version, packVersion("kk"))
        assertEquals(WordStatus.ACTIVE.name, wordRow("kk", "қалам")!![WordsTable.status])
        assertEquals(null, wordRow("kk", "кітап"))

        assertEquals(HttpStatusCode.Created, client.addWord(worder, "ru", "книга").status)
    }

    @Test
    fun refusalsCarryTheirReasons() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")
        val crane = client.addWord(boss, "en", "crane").body<WordDto>()
        val plumb = client.addWord(boss, "en", "plumb").body<WordDto>()
        client.post(AdminRoutes.wordRemove(plumb.id)) { withSession(boss) }

        val invalid = client.addWord(boss, "en", "cat")
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertEquals(ApiErrorResponse("validation_failed", "text: bad_length"), invalid.error())

        assertEquals(ApiErrorResponse("validation_failed", "text: not_tokenizable"), client.addWord(boss, "en", "crâne").error())

        val duplicate = client.addWord(boss, "en", "CRANE")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ApiErrorResponse("conflict", "text: duplicate"), duplicate.error())

        val removedExists = client.patch(AdminRoutes.word(crane.id)) { withSession(boss); jsonBody(EditWordRequest("plumb")) }
        assertEquals(HttpStatusCode.Conflict, removedExists.status)
        assertEquals("text: removed_exists", removedExists.error().message)

        val oversized = client.post(AdminRoutes.WORDS_BULK) { withSession(boss); jsonBody(BulkAddRequest("en", List(1001) { "quilt" })) }
        assertEquals(HttpStatusCode.UnprocessableEntity, oversized.status)
        assertEquals("lines: too_many_lines", oversized.error().message)
        assertEquals(null, wordRow("en", "quilt"))

        val integrity = client.post(AdminRoutes.wordRemove(wordRow("en", "wharf")!![WordsTable.id])) { withSession(boss) }
        assertEquals(HttpStatusCode.UnprocessableEntity, integrity.status)
        assertEquals("pack: pack_integrity", integrity.error().message)

        assertEquals("lang: required", client.get(AdminRoutes.WORDS) { withSession(boss) }.error().message)
        assertEquals(HttpStatusCode.NotFound, client.post(AdminRoutes.wordRemove("no-such-word")) { withSession(boss) }.status)
        assertEquals(HttpStatusCode.NotFound, client.addWord(boss, "de", "kranz").status)
    }

    @Test
    fun bulkAndCheckAnswerPerLineAndWithoutSideEffects() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        val check = client.post(AdminRoutes.WORDS_CHECK) { withSession(boss); jsonBody(CheckWordRequest("en", "Quilt")) }.body<CheckWordResult>()
        assertEquals(CheckWordResult("quilt", 5, WordCheckOutcome.VALID), check)
        assertEquals("1", packVersion("en"))

        val bulk = client.post(AdminRoutes.WORDS_BULK) { withSession(boss); jsonBody(BulkAddRequest("en", listOf("quilt", "crane", "wharf"))) }
        assertEquals(HttpStatusCode.OK, bulk.status)
        val result = bulk.body<BulkAddResult>()
        assertEquals("2", result.packVersion)
        assertEquals(3, result.results.size)
    }

    @Test
    fun aLanguageRemovedFromAWorderIsRefusedOnTheirNextRequest() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val worderId = insertStaff("dilnoza", Role.WORDER, listOf("ru", "kk"))
        val client = adminClient()
        val boss = client.signIn("boss")
        val worder = client.signIn("dilnoza")
        assertEquals(HttpStatusCode.Created, client.addWord(worder, "kk", "қалам").status)

        client.patch(AdminRoutes.staff(worderId)) { withSession(boss); jsonBody(UpdateStaffRequest(languages = Patch.Set(listOf("ru")))) }

        assertEquals(HttpStatusCode.Forbidden, client.addWord(worder, "kk", "кітап").status)
    }

    @Test
    fun sessionsAndAntiForgeryAreRequired() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")
        assertEquals(HttpStatusCode.Unauthorized, client.get("${AdminRoutes.WORDS}?lang=en").status)
        val noXsrf = client.post(AdminRoutes.WORDS) { withSession(boss, xsrf = null); jsonBody(AddWordRequest("en", "crane")) }
        assertEquals(HttpStatusCode.Forbidden, noXsrf.status)
        assertEquals(null, wordRow("en", "crane"))
    }

    @Test
    fun aSavedChangeReachesThePublicPackImmediately() = testApplication {
        insertStaff("boss", Role.ADMIN)
        val client = adminClient()
        val boss = client.signIn("boss")

        val crane = client.addWord(boss, "en", "crane").body<WordDto>()
        var pack = client.get(ApiRoutes.wordpack("en")).body<WordPackDto>()
        assertEquals("2", pack.version)
        assertTrue("crane" in pack.guesses)

        client.post(AdminRoutes.wordRemove(crane.id)) { withSession(boss) }
        pack = client.get(ApiRoutes.wordpack("en")).body<WordPackDto>()
        assertEquals("3", pack.version)
        assertFalse("crane" in pack.guesses)
    }

    // --- no daily-word information (6.3) ---

    private val dailyMarkers = listOf("daily", "eligib", "schedule", "answer")

    private fun assertNoDailyInformation(body: String) {
        val lower = body.lowercase()
        for (marker in dailyMarkers) assertFalse(marker in lower, "'$marker' in $body")
    }

    @Test
    fun wordResponsesCarryNoDailyWordInformationForAnyRole() = testApplication {
        insertStaff("boss", Role.ADMIN)
        insertStaff("maria", Role.WORDER, listOf("en"))
        val client = adminClient()
        // "wharf" is today's scheduled answer; make "crane" daily-eligible too, so "wharf" can be removed.
        val boss = client.signIn("boss")
        val maria = client.signIn("maria")
        client.addWord(boss, "en", "crane")
        transaction(DatabaseFactory.init()) {
            WordsTable.update({ (WordsTable.lang eq "en") and (WordsTable.text eq "crane") }) { it[dailyEligible] = true }
        }

        for (session in listOf(maria, boss)) {
            val bodies = mutableListOf<String>()
            bodies += client.list(session, "${WordParams.LANG}=en&${WordParams.STATUS}=ALL").bodyAsText()
            bodies += client.list(session, "${WordParams.LANG}=en&${WordParams.Q}=wharf").bodyAsText()
            val added = client.addWord(session, "en", if (session == maria) "quilt" else "plumb")
            bodies += added.bodyAsText()
            val id = Json.decodeFromString<WordDto>(bodies.last()).id
            bodies += client.patch(AdminRoutes.word(id)) { withSession(session); jsonBody(EditWordRequest(if (session == maria) "quilts" else "plumbs")) }.bodyAsText()
            bodies += client.post(AdminRoutes.WORDS_CHECK) { withSession(session); jsonBody(CheckWordRequest("en", "wharf")) }.bodyAsText()
            bodies += client.post(AdminRoutes.WORDS_BULK) { withSession(session); jsonBody(BulkAddRequest("en", listOf("wharf", "zebra"))) }.bodyAsText()
            bodies.forEach(::assertNoDailyInformation)
        }

        // Removing the scheduled answer answers exactly like removing any other bundled word.
        runBlocking {
            WordCatalogService(WordPackServerService(resourceLines = { if (it == "en_guess.txt") listOf("ghost") else emptyList() }))
                .mergeBundled()
        }
        val scheduled = client.post(AdminRoutes.wordRemove(wordRow("en", "wharf")!![WordsTable.id])) { withSession(maria) }
        val plain = client.post(AdminRoutes.wordRemove(wordRow("en", "ghost")!![WordsTable.id])) { withSession(maria) }
        assertEquals(HttpStatusCode.OK, scheduled.status)
        assertEquals(plain.status, scheduled.status)
        val scheduledBody = scheduled.bodyAsText()
        assertNoDailyInformation(scheduledBody)
        assertEquals(
            Json.parseToJsonElement(plain.bodyAsText()).jsonObject.keys,
            Json.parseToJsonElement(scheduledBody).jsonObject.keys,
        )
        runBlocking { assertEquals(listOf("wharf"), WordPackServerService().getPack("en")!!.schedule) }
    }
}
