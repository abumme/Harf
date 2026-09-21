package uz.abumme.harfgame.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderDto
import uz.abumme.harfgame.data.admin.suggestions.DeciderKind
import uz.abumme.harfgame.data.admin.suggestions.DecisionRequest
import uz.abumme.harfgame.data.admin.suggestions.ReviewReasons
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.words.AddWordRequest
import uz.abumme.harfgame.data.admin.words.BulkAddRequest
import uz.abumme.harfgame.data.admin.words.BulkAddResult
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.BulkLineResult
import uz.abumme.harfgame.data.admin.words.CheckWordRequest
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.EditWordRequest
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.admin.words.WordPageDto
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.lang.LaunchLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminWordsDtoSerializationTest {
    // The same settings as the backend's ContentNegotiation.
    private val json = Json { ignoreUnknownKeys = true }

    private val word = WordDto(
        id = "w-1",
        lang = "uz-latn",
        text = "oʻgʻil",
        graphemeCount = 4,
        status = WordStatus.REMOVED,
        source = WordSource.STAFF,
        suggestionId = "s-9",
        createdBy = StaffRefDto("st-1", "aziz", "Aziz"),
        createdAt = 1_700_000_000_000,
        updatedBy = StaffRefDto("st-2", "dilnoza"),
        updatedAt = 1_700_000_000_500,
        removedBy = StaffRefDto("st-2", "dilnoza"),
        removedAt = 1_700_000_000_500,
    )

    @Test
    fun wordDtosRoundTrip() {
        assertEquals(word, json.decodeFromString(json.encodeToString(word)))
        val restored = word.copy(status = WordStatus.ACTIVE, removedBy = null, removedAt = null, restored = true)
        assertEquals(restored, json.decodeFromString(json.encodeToString(restored)))

        val page: WordPageDto = PageDto(listOf(word), page = 2, size = 50, total = 23_001)
        assertEquals(page, json.decodeFromString<WordPageDto>(json.encodeToString(page)))

        val check = CheckWordRequest("ru", "Ёлка")
        assertEquals(check, json.decodeFromString(json.encodeToString(check)))
        val checked = CheckWordResult("елка", 4, WordCheckOutcome.RESTORABLE)
        assertEquals(checked, json.decodeFromString(json.encodeToString(checked)))
        val invalid = CheckWordResult("cat", 3, WordCheckOutcome.INVALID, WordReasons.BAD_LENGTH)
        assertEquals(invalid, json.decodeFromString(json.encodeToString(invalid)))

        val add = AddWordRequest("en", "Crane")
        assertEquals(add, json.decodeFromString(json.encodeToString(add)))
        val bulk = BulkAddRequest("en", listOf("crane", "", "quilt"))
        assertEquals(bulk, json.decodeFromString(json.encodeToString(bulk)))
        val bulkResult = BulkAddResult(
            results = listOf(
                BulkLineResult(1, "crane", BulkLineOutcome.ADDED),
                BulkLineResult(3, "cat", BulkLineOutcome.INVALID, WordReasons.BAD_LENGTH),
                BulkLineResult(4, null, BulkLineOutcome.INVALID, WordReasons.NOT_TOKENIZABLE),
            ),
            packVersion = "42",
        )
        assertEquals(bulkResult, json.decodeFromString(json.encodeToString(bulkResult)))
        val edit = EditWordRequest("quilt")
        assertEquals(edit, json.decodeFromString(json.encodeToString(edit)))
    }

    @Test
    fun suggestionDtosRoundTrip() {
        val suggestion = SuggestionDto(
            id = "s-1",
            lang = "kk",
            word = "кітап",
            author = "Аноним",
            createdAt = 1_700_000_000_000,
            reason = ReviewReasons.REMOVED_BY_STAFF,
            status = SuggestionStatus.ACCEPTED,
            decidedBy = DeciderDto(DeciderKind.STAFF, "Aziz"),
            decidedAt = 1_700_000_100_000,
        )
        assertEquals(suggestion, json.decodeFromString(json.encodeToString(suggestion)))
        val page = PageDto(listOf(suggestion, suggestion.copy(id = "s-2", reason = null, status = SuggestionStatus.PENDING, decidedBy = null, decidedAt = null)), 0, 50, 2)
        assertEquals(page, json.decodeFromString<PageDto<SuggestionDto>>(json.encodeToString(page)))
        val decision = DecisionRequest(accept = false)
        assertEquals(decision, json.decodeFromString(json.encodeToString(decision)))
    }

    @Test
    fun wordDtoCarriesNoDailyWordField() {
        val encoded = Json { encodeDefaults = true }.encodeToString(word.copy(restored = true))
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys
        for (key in keys) {
            val lower = key.lowercase()
            assertFalse("daily" in lower || "eligib" in lower || "schedule" in lower || "answer" in lower, "WordDto field $key")
        }
    }

    @Test
    fun wordRoutesAreUnderTheAdminPrefix() {
        assertEquals("/api/v1/admin/words", AdminRoutes.WORDS)
        assertEquals("/api/v1/admin/words/check", AdminRoutes.WORDS_CHECK)
        assertEquals("/api/v1/admin/words/bulk", AdminRoutes.WORDS_BULK)
        assertEquals("/api/v1/admin/words/w-1/remove", AdminRoutes.wordRemove("w-1"))
        assertEquals("/api/v1/admin/words/w-1/restore", AdminRoutes.wordRestore("w-1"))
        assertEquals("/api/v1/admin/suggestions/s-1/decision", AdminRoutes.suggestionDecision("s-1"))
    }

    @Test
    fun wordRulesNormalizeAndCountGraphemes() {
        val ru = WordRules.check(LaunchLanguages.ru, "  Ёлка ")
        assertEquals("елка", ru.normalized)
        assertTrue(ru.isValid)
        val uz = WordRules.check(LaunchLanguages.uzLatn, "O‘G’IL")
        assertEquals("oʻgʻil", uz.normalized)
        assertEquals(listOf("oʻ", "gʻ", "i", "l"), uz.graphemes)
        assertEquals(WordReasons.BAD_LENGTH, WordRules.check(LaunchLanguages.uzLatn, "choy").reason) // ch-o-y: 3 letters
        assertEquals(WordReasons.NOT_TOKENIZABLE, WordRules.check(LaunchLanguages.en, "naïve").reason)
        val empty = WordRules.check(LaunchLanguages.en, "   ")
        assertEquals(WordReasons.EMPTY, empty.reason)
        assertNull(empty.graphemes)
    }
}
