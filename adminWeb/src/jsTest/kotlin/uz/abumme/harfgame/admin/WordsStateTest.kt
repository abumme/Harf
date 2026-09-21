package uz.abumme.harfgame.admin

import kotlinx.coroutines.test.runTest
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpRequest
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.components.startOfLocalDay
import uz.abumme.harfgame.admin.words.BulkGroupItem
import uz.abumme.harfgame.admin.words.BulkInput
import uz.abumme.harfgame.admin.words.StatusFilter
import uz.abumme.harfgame.admin.words.WordPreview
import uz.abumme.harfgame.admin.words.WordSort
import uz.abumme.harfgame.admin.words.WordsQueryState
import uz.abumme.harfgame.admin.words.canAdd
import uz.abumme.harfgame.admin.words.groupBulkResults
import uz.abumme.harfgame.admin.words.previewMessage
import uz.abumme.harfgame.admin.words.wordErrorMessage
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.BulkLineResult
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordsStateTest {
    private val languages = listOf("en", "ru", "uz-latn")

    // --- query state (8.1) ---

    @Test
    fun theDefaultViewNeedsOnlyTheLanguageInTheUrl() {
        val state = WordsQueryState("ru")
        assertEquals("?lang=ru", state.toRouteQuery())
        assertEquals("?lang=ru&status=ACTIVE&sort=text&dir=asc&page=0&size=50", state.toApiQuery(50))
        assertFalse(state.hasFilters)
    }

    @Test
    fun filtersBecomeQueryParametersAndReadBackFromTheUrl() {
        val state = WordsQueryState("uz-latn")
            .withSearch("o'g")
            .withStatus(StatusFilter.REMOVED)
            .withSource("STAFF")
            .withAddedBy("s-1")
            .withAddedFrom("2026-09-01")
            .withAddedTo("2026-09-15")
            .withSort(WordSort.CREATED)
            .withPage(2)

        val route = state.toRouteQuery()
        assertEquals("?lang=uz-latn&q=o%27g&status=REMOVED&source=STAFF&addedBy=s-1&from=2026-09-01&to=2026-09-15&sort=created&dir=desc&page=3", route)
        val params = route.removePrefix("?").split('&').associate { part ->
            val (name, value) = part.split('=')
            name to decodeURIComponent(value)
        }
        assertEquals(state, WordsQueryState.fromParams(params, languages))
        assertTrue(state.hasFilters)

        val api = state.toApiQuery(50)
        assertTrue("q=o%27g" in api && "status=REMOVED" in api && "source=STAFF" in api && "addedBy=s-1" in api, api)
        assertTrue("addedFrom=${startOfLocalDay("2026-09-01")}" in api, api)
        assertTrue("sort=created&dir=desc&page=2&size=50" in api, api)
    }

    @Test
    fun everyFilterOrSortChangeStartsFromTheFirstPage() {
        val onPage3 = WordsQueryState("en").withPage(3)
        assertEquals(0, onPage3.withSearch("cr").page)
        assertEquals(0, onPage3.withStatus(StatusFilter.ALL).page)
        assertEquals(0, onPage3.withSource("BUNDLED").page)
        assertEquals(0, onPage3.withAddedBy("s-1").page)
        assertEquals(0, onPage3.withAddedFrom("2026-01-01").page)
        assertEquals(0, onPage3.withAddedTo("2026-01-01").page)
        assertEquals(0, onPage3.withSort(WordSort.UPDATED).page)
        assertEquals(3, onPage3.withSearch("").page) // unchanged search keeps the page
        assertEquals(WordsQueryState("ru"), onPage3.withSearch("cr").withLanguage("ru"))
        assertEquals(0, WordsQueryState("en").withPage(-4).page)
    }

    @Test
    fun sortingTheSameColumnFlipsTheDirection() {
        val byText = WordsQueryState("en")
        assertEquals(true, byText.withSort(WordSort.TEXT).descending)
        val byDate = byText.withSort(WordSort.CREATED)
        assertEquals(WordSort.CREATED to true, byDate.sort to byDate.descending)
        assertEquals(false, byDate.withSort(WordSort.CREATED).descending)
        assertEquals(false, byDate.withSort(WordSort.TEXT).descending)
    }

    @Test
    fun aUrlIsLimitedToTheMembersLanguagesAndSaneValues() {
        assertEquals(WordsQueryState("en"), WordsQueryState.fromParams(mapOf("lang" to "kk"), languages))
        assertEquals(WordsQueryState("en"), WordsQueryState.fromParams(emptyMap(), languages))
        assertNull(WordsQueryState.fromParams(mapOf("lang" to "en"), emptyList()))
        assertEquals(
            WordsQueryState("ru"),
            WordsQueryState.fromParams(
                mapOf("lang" to "ru", "status" to "BOGUS", "source" to "NOPE", "sort" to "x", "page" to "-2", "from" to "yesterday"),
                languages,
            ),
        )
    }

    // --- bulk input and results (8.2) ---

    @Test
    fun bulkInputTrimsSkipsBlankLinesAndCounts() {
        val input = BulkInput.parse("  crane \n\n\tquilt\r\n   \nplumb")
        assertEquals(listOf("crane", "quilt", "plumb"), input.lines)
        assertEquals(3, input.count)
        assertTrue(input.canSubmit)
        assertFalse(input.tooMany)

        assertFalse(BulkInput.parse(" \n \n").canSubmit)
        val limit = BulkInput.parse(List(1000) { "w$it" }.joinToString("\n"))
        assertTrue(limit.canSubmit && !limit.tooMany)
        val over = BulkInput.parse(List(1001) { "w$it" }.joinToString("\n"))
        assertTrue(over.tooMany)
        assertFalse(over.canSubmit)
    }

    @Test
    fun bulkResultsAreGroupedByOutcomeInPastedOrder() {
        val submitted = listOf("Crane", "quilt", "crane", "naïve", "badly", "plumb", "x y")
        val results = listOf(
            BulkLineResult(1, "crane", BulkLineOutcome.ADDED),
            BulkLineResult(2, "quilt", BulkLineOutcome.DUPLICATE),
            BulkLineResult(3, "crane", BulkLineOutcome.DUPLICATE),
            BulkLineResult(4, "naïve", BulkLineOutcome.INVALID, WordReasons.NOT_TOKENIZABLE),
            BulkLineResult(5, "badly", BulkLineOutcome.BLOCKLISTED, WordReasons.BLOCKLISTED),
            BulkLineResult(6, "plumb", BulkLineOutcome.RESTORED),
            BulkLineResult(7, null, BulkLineOutcome.INVALID, WordReasons.NOT_TOKENIZABLE),
        )

        val groups = groupBulkResults(results, submitted)

        assertEquals(
            listOf(BulkLineOutcome.ADDED, BulkLineOutcome.RESTORED, BulkLineOutcome.DUPLICATE, BulkLineOutcome.INVALID, BulkLineOutcome.BLOCKLISTED),
            groups.map { it.outcome },
        )
        assertEquals(listOf(1, 1, 2, 2, 1), groups.map { it.count })
        assertEquals(
            listOf(BulkGroupItem(4, "naïve", WordReasons.NOT_TOKENIZABLE), BulkGroupItem(7, "x y", WordReasons.NOT_TOKENIZABLE)),
            groups[3].items,
        )
        assertEquals(emptyList(), groupBulkResults(emptyList(), emptyList()))
    }

    // --- local preview (8.2) ---

    @Test
    fun previewNormalizesUzbekApostrophesAndCountsDigraphsAsOneLetter() {
        val preview = assertNotNull(WordPreview.of("uz-latn", "O'G‘IL"))
        assertEquals("oʻgʻil", preview.normalized)
        assertEquals(4, preview.graphemeCount)
        assertTrue(preview.tokenizes && preview.lengthOk && preview.isValid)

        val choy = assertNotNull(WordPreview.of("uz-latn", "choy"))
        assertEquals(3, choy.graphemeCount)
        assertFalse(choy.lengthOk)
        assertEquals(WordReasons.BAD_LENGTH, choy.reason)
    }

    @Test
    fun previewFoldsRussianYoAndReportsForeignLetters() {
        val yolka = assertNotNull(WordPreview.of("ru", "Ёлка"))
        assertEquals("елка", yolka.normalized)
        assertTrue(yolka.isValid)

        val latin = assertNotNull(WordPreview.of("ru", "кофe"))
        assertFalse(latin.tokenizes)
        assertEquals(WordReasons.NOT_TOKENIZABLE, latin.reason)
        assertNull(WordPreview.of("xx", "word"))
        assertEquals(WordReasons.EMPTY, WordPreview.of("en", "  ")?.reason)
    }

    @Test
    fun previewMessagesAndSubmitFollowTheLocalRulesThenTheServerCheck() {
        val crane = WordPreview.of("en", "Crane")
        assertEquals(Strings.Words.CHECKING, previewMessage(crane, null, checking = true))
        assertTrue(canAdd(crane, null))

        val valid = CheckWordResult("crane", 5, WordCheckOutcome.VALID)
        assertEquals(Strings.Words.CHECK_VALID, previewMessage(crane, valid, checking = false))
        assertTrue(canAdd(crane, valid))
        assertTrue(canAdd(crane, valid.copy(outcome = WordCheckOutcome.RESTORABLE)))
        assertFalse(canAdd(crane, valid.copy(outcome = WordCheckOutcome.DUPLICATE)))
        assertFalse(canAdd(crane, valid.copy(outcome = WordCheckOutcome.BLOCKLISTED, reason = WordReasons.BLOCKLISTED)))
        assertEquals(Strings.Words.reason(WordReasons.BLOCKLISTED), previewMessage(crane, valid.copy(outcome = WordCheckOutcome.BLOCKLISTED, reason = WordReasons.BLOCKLISTED), false))

        val cat = WordPreview.of("en", "cat")
        assertEquals(Strings.Words.reason(WordReasons.BAD_LENGTH, 4, 7), previewMessage(cat, null, checking = false))
        assertFalse(canAdd(cat, null))
    }

    @Test
    fun wordRefusalsExplainTheReasonWithoutMentioningDailyWords() {
        val removed = ApiResult.Error("conflict", "text: removed_exists")
        assertEquals(Strings.Words.reason(WordReasons.REMOVED_EXISTS), wordErrorMessage(removed, "en"))
        assertTrue("восстановите" in wordErrorMessage(removed, "en"))
        val integrity = wordErrorMessage(ApiResult.Error("validation_failed", "pack: pack_integrity"), "kk")
        assertEquals(Strings.Words.reason(WordReasons.PACK_INTEGRITY), integrity)
        assertFalse("дня" in integrity || "daily" in integrity)
        assertEquals(Strings.Common.FORBIDDEN_ACTION, wordErrorMessage(ApiResult.Error("forbidden", "Language kk is not assigned to you"), "kk"))
    }

    @Test
    fun wordRequestsUseTheSharedRoutesAndSendTheAntiForgeryHeaderOnWrites() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = AdminApi("/harf", CredentialsMode.SAME_ORIGIN, { request -> requests += request; HttpResponse(500, "") }, { "XSRF-TOKEN=tok" })

        api.words("?lang=en&page=0")
        api.checkWord("en", "crane")
        api.addWords("en", listOf("crane"))
        api.editWord("w 1", "quilt")
        api.restoreWord("w-1")
        api.decideSuggestion("s-1", accept = true)

        assertEquals(
            listOf(
                "GET /harf/api/v1/admin/words?lang=en&page=0",
                "POST /harf/api/v1/admin/words/check",
                "POST /harf/api/v1/admin/words/bulk",
                "PATCH /harf/api/v1/admin/words/w%201",
                "POST /harf/api/v1/admin/words/w-1/restore",
                "POST /harf/api/v1/admin/suggestions/s-1/decision",
            ),
            requests.map { "${it.method} ${it.url}" },
        )
        assertNull(requests.first().headers["X-XSRF-TOKEN"])
        assertTrue(requests.drop(1).all { it.headers["X-XSRF-TOKEN"] == "tok" })
        assertEquals("""{"lang":"en","lines":["crane"]}""", requests[2].body)
        assertEquals("""{"accept":true}""", requests[5].body)
    }
}

private external fun decodeURIComponent(encoded: String): String
