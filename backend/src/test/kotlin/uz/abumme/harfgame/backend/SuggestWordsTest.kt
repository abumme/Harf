package uz.abumme.harfgame.backend

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.service.DecideOutcome
import uz.abumme.harfgame.backend.service.SuggestOutcome
import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import uz.abumme.harfgame.data.api.ApiRoutes
import uz.abumme.harfgame.data.auth.AnonymousAuthResponse
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestWordsTest {

    private val jsonc = Json

    @BeforeTest
    fun setup() {
        val db = DatabaseFactory.init()
        transaction(db) {
            WordSuggestionsTable.deleteAll()
            WordPacksTable.deleteAll()
            UsersTable.deleteAll()
            UsersTable.insert { it[id] = "u1"; it[createdAt] = Instant.now() }
        }
    }

    private fun svc(cap: Int = 10) = SuggestionServerService(WordPackServerService(), dailyCap = cap)

    private fun insertPack(lang: String, version: String, guesses: List<String>) {
        transaction(DatabaseFactory.init()) {
            WordPacksTable.insert {
                it[WordPacksTable.lang] = lang
                it[WordPacksTable.version] = version
                it[effectiveFrom] = 0L
                it[anchorEpochDay] = 0L
                it[answers] = jsonc.encodeToString(listOf<String>())
                it[WordPacksTable.guesses] = jsonc.encodeToString(guesses)
                it[schedule] = jsonc.encodeToString(listOf<String>())
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun insertPending(lang: String, word: String, author: String? = "u1"): String {
        val id = UUID.randomUUID().toString()
        transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.insert {
                it[WordSuggestionsTable.id] = id
                it[WordSuggestionsTable.lang] = lang
                it[WordSuggestionsTable.word] = word
                it[suggestedBy] = author
                it[status] = SuggestionStatus.PENDING.name
                it[createdAt] = Instant.now()
            }
        }
        return id
    }

    private fun statusOf(id: String): String = transaction(DatabaseFactory.init()) {
        WordSuggestionsTable.selectAll().where { WordSuggestionsTable.id eq id }
            .single()[WordSuggestionsTable.status]
    }

    // ---- validation matrix (2.3) ----

    @Test
    fun validSuggestionIsStored() = runBlocking {
        val out = svc().suggest("u1", "en", "hello")
        assertTrue(out is SuggestOutcome.Stored)
    }

    @Test
    fun badLengthRejected() = runBlocking {
        assertEquals(SuggestOutcome.Rejected("bad_length"), svc().suggest("u1", "en", "a"))
    }

    @Test
    fun offensiveRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "shit")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "offensive")
    }

    @Test
    fun gibberishRepeatRunRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "haaad")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "repeat_run")
    }

    @Test
    fun gibberishTooFewDistinctRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "abab")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "too_few_distinct")
    }

    @Test
    fun gibberishNoVowelRejected() = runBlocking {
        val out = svc().suggest("u1", "en", "bcdfg")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "no_vowel")
    }

    @Test
    fun alreadyInPackRejected() = runBlocking {
        insertPack("en", "1", listOf("salom"))
        val out = svc().suggest("u1", "en", "salom")
        assertTrue(out is SuggestOutcome.Rejected && out.reason == "already_present")
    }

    @Test
    fun duplicatePendingIsCollapsed() = runBlocking {
        insertPending("en", "table")
        assertEquals(SuggestOutcome.DuplicatePending, svc().suggest("u1", "en", "table"))
        // No second pending row created.
        val count = transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.selectAll().where { WordSuggestionsTable.word eq "table" }.count()
        }
        assertEquals(1, count)
    }

    @Test
    fun overDailyCapRejected() = runBlocking {
        assertEquals(SuggestOutcome.OverCap, svc(cap = 0).suggest("u1", "en", "hello"))
    }

    // ---- decision / accept path (3.6) ----

    @Test
    fun acceptAddsWordToPackAndBumpsVersion() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val out = svc().decide(id, accept = true, editor = "42")
        assertTrue(out is DecideOutcome.Applied && out.accepted)
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
        val pack = WordPackServerService().getPack("en")!!
        assertTrue("hello" in pack.guesses)
        assertEquals("2", pack.version)
    }

    @Test
    fun rejectLeavesPackUnchanged() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val out = svc().decide(id, accept = false, editor = "42")
        assertTrue(out is DecideOutcome.Applied && !out.accepted)
        assertEquals(SuggestionStatus.REJECTED.name, statusOf(id))
        val pack = WordPackServerService().getPack("en")!!
        assertFalse("hello" in pack.guesses)
        assertEquals("1", pack.version)
    }

    @Test
    fun secondDecisionIsNoOp() = runBlocking {
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        svc().decide(id, accept = true, editor = "42")
        assertEquals(DecideOutcome.AlreadyDecided, svc().decide(id, accept = false, editor = "42"))
    }

    @Test
    fun decideUnknownIsNotFound() = runBlocking {
        assertEquals(DecideOutcome.NotFound, svc().decide("nope", accept = true, editor = "42"))
    }

    // ---- Telegram editor authorization (3.6) ----

    @Test
    fun nonEditorCallbackChangesNothing() = runBlocking {
        val service = svc()
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val bot = TelegramBot(botToken = "", editorChatId = "", editorIds = setOf(42L), suggestions = service)
        val reply = bot.onCallback(fromId = 999L, data = "accept:$id")
        assertEquals("Недостаточно прав", reply)
        assertEquals(SuggestionStatus.PENDING.name, statusOf(id))
    }

    @Test
    fun editorCallbackAcceptsSuggestion() = runBlocking {
        val service = svc()
        insertPack("en", "1", listOf("x"))
        val id = insertPending("en", "hello")
        val bot = TelegramBot(botToken = "", editorChatId = "", editorIds = setOf(42L), suggestions = service)
        val reply = bot.onCallback(fromId = 42L, data = "accept:$id")
        assertTrue(reply.contains("Принято"))
        assertEquals(SuggestionStatus.ACCEPTED.name, statusOf(id))
    }

    // ---- Telegram forum topics ----

    @Test
    fun suggestionRoutedToLanguageTopic() {
        val bot = TelegramBot(
            botToken = "", editorChatId = "chat", editorIds = setOf(42L),
            suggestions = svc(), topics = mapOf("uz-latn" to 7),
        )
        val mapped = bot.suggestionMessage("id1", "uz-latn", "salom")
        assertEquals(7, mapped["message_thread_id"]?.jsonPrimitive?.int)

        val unmapped = bot.suggestionMessage("id2", "en", "hello")
        assertEquals(null, unmapped["message_thread_id"])
    }

    @Test
    fun parseTelegramTopicsParsesPairsAndSkipsJunk() {
        assertEquals(
            mapOf("en" to 2, "ru" to 3, "uz-latn" to 4),
            parseTelegramTopics("en=2, ru=3 ,uz-latn=4, bad, x=notint, =5")
        )
        assertEquals(emptyMap(), parseTelegramTopics(null))
        assertEquals(emptyMap(), parseTelegramTopics(""))
    }

    // ---- route integration (4.2) ----

    @Test
    fun authenticatedSuggestionStoredStoreOnly() = testApplication {
        application { module() } // no Telegram env -> store-only, no crash
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val anon = client.post(ApiRoutes.AUTH_ANONYMOUS).body<AnonymousAuthResponse>()
        val resp = client.post(ApiRoutes.SUGGESTIONS) {
            header(HttpHeaders.Authorization, "Bearer ${anon.tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertEquals(SuggestionStatus.PENDING.name, resp.body<SuggestWordResponse>().status)

        val stored = transaction(DatabaseFactory.init()) {
            WordSuggestionsTable.selectAll().where { WordSuggestionsTable.word eq "world" }.count()
        }
        assertEquals(1, stored)
    }

    @Test
    fun unauthenticatedSuggestionRejected() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val resp = client.post(ApiRoutes.SUGGESTIONS) {
            contentType(ContentType.Application.Json)
            setBody(SuggestWordRequest(lang = "en", word = "world"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
