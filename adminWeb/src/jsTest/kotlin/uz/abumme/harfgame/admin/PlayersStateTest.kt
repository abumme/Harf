package uz.abumme.harfgame.admin

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.HttpRequest
import uz.abumme.harfgame.admin.api.HttpResponse
import uz.abumme.harfgame.admin.api.HttpTransport
import uz.abumme.harfgame.admin.players.PlayerFilters
import uz.abumme.harfgame.admin.players.PlayerListState
import uz.abumme.harfgame.admin.players.TypeFilter
import uz.abumme.harfgame.admin.players.accountIdFromRoute
import uz.abumme.harfgame.admin.players.deleteConfirmationMatches
import uz.abumme.harfgame.admin.players.deleteRequestFor
import uz.abumme.harfgame.admin.players.shortAccountId
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.PlayerReasons
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerSummaryDto
import uz.abumme.harfgame.data.admin.players.PlayerType
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayersStateTest {
    private val id = "3f1c2a9e-7b4d-4c1a-9f0e-2d6b8a1c5e7f"

    private fun player(id: String) = PlayerSummaryDto(id = id, createdAt = 1)

    /** The page URL's query as the router hands it to the page. */
    private fun params(query: String): Map<String, String> =
        query.removePrefix("?").split('&').filter { it.isNotEmpty() }.associate { part ->
            part.substringBefore('=') to decodeURIComponent(part.substringAfter('=', ""))
        }

    @Test
    fun filtersMapToQueryParametersAndAreRestoredFromTheUrl() {
        val filters = PlayerFilters()
            .withSearch("Ali sher")
            .withType(TypeFilter.APPLE)
            .withCreatedFrom("2026-09-01")
            .withCreatedToInclusive("2026-09-17")
            .withBlockedOnly(true)

        val route = filters.toRouteQuery()
        assertEquals("?q=Ali%20sher&type=apple&createdFrom=2026-09-01&createdTo=2026-09-18&blocked=true", route)
        // The API request carries the same parameters: "created to" is sent as the exclusive next day.
        assertEquals(
            PlayerSearchQuery(q = "Ali sher", type = PlayerType.APPLE, createdFrom = LocalDate(2026, 9, 1), createdTo = LocalDate(2026, 9, 18), blocked = true),
            filters.toApiQuery(cursor = null),
        )

        val restored = PlayerFilters.fromRoute(params(route))
        assertEquals(filters, restored)
        assertEquals(TypeFilter.APPLE, restored.typeFilter)
        assertEquals("2026-09-01", restored.createdFrom)
        assertEquals("2026-09-17", restored.createdToInclusive, "the date input shows the last included day")
        assertTrue(restored.blockedOnly)
        assertTrue(restored.hasFilters)
    }

    @Test
    fun defaultsLeaveTheUrlEmptyAndMalformedValuesFallBack() {
        assertEquals("", PlayerFilters().toRouteQuery())
        assertFalse(PlayerFilters().hasFilters)
        val restored = PlayerFilters.fromRoute(mapOf("type" to "facebook", "createdFrom" to "soon", "blocked" to "false", "cursor" to "abc", "size" to "7"))
        assertEquals(PlayerFilters(), restored, "no cursor, size or 'only unblocked' survives from a URL")

        val cleared = PlayerFilters().withType(TypeFilter.GOOGLE).withCreatedFrom("2026-09-01").withBlockedOnly(true)
            .withType(TypeFilter.ALL).withCreatedFrom("").withBlockedOnly(false)
        assertEquals(PlayerFilters(), cleared)
        assertEquals(PlayerFilters(), PlayerFilters().withSearch("x").withoutFilters())
    }

    @Test
    fun aShortNameSearchIsNotSentButAnAccountIdIs() {
        assertEquals(PlayerReasons.TOO_SHORT, PlayerFilters().withSearch(" a ").searchProblem)
        assertNull(PlayerListState(PlayerFilters().withSearch("a")).nextRequest())
        assertNull(PlayerFilters().withSearch("al").searchProblem)
        assertNull(PlayerFilters().withSearch(id).searchProblem)
        assertEquals(id, PlayerListState(PlayerFilters().withSearch(" $id ")).nextRequest()!!.q)
    }

    @Test
    fun theCursorStateAppendsResetsAndStops() {
        val filters = PlayerFilters().withType(TypeFilter.GOOGLE)
        var state = PlayerListState(filters)
        assertFalse(state.hasMore)
        assertEquals(PlayerSearchQuery(type = PlayerType.GOOGLE), state.nextRequest(), "the first page has no cursor")

        state = state.append(filters, PlayerSearchPageDto(listOf(player("a"), player("b")), nextCursor = "c1"))
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertTrue(state.hasMore)
        assertEquals("c1", state.nextRequest()!!.cursor)

        state = state.append(filters, PlayerSearchPageDto(listOf(player("c")), nextCursor = null))
        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertFalse(state.hasMore)
        assertNull(state.nextRequest(), "no request after the last page")

        // A late answer for other filters is dropped; unchanged filters keep the list; new filters start over.
        val other = filters.withBlockedOnly(true)
        assertSame(state, state.append(other, PlayerSearchPageDto(listOf(player("z")))))
        assertSame(state, state.withFilters(filters))
        val reset = state.withFilters(other)
        assertEquals(PlayerListState(other), reset)
        assertEquals(emptyList(), reset.items)
        assertNull(reset.nextRequest()!!.cursor)
    }

    @Test
    fun theDeleteConfirmationMustBeTheExactAccountId() {
        assertTrue(deleteConfirmationMatches(id, id))
        assertTrue(deleteConfirmationMatches("  $id\n", id), "surrounding blanks are ignored")
        assertFalse(deleteConfirmationMatches(id.take(8), id), "partial")
        assertFalse(deleteConfirmationMatches(id.uppercase(), id), "different case")
        assertFalse(deleteConfirmationMatches("", id), "empty")
        assertFalse(deleteConfirmationMatches("", ""))

        assertNull(deleteRequestFor(id.dropLast(1), id))
        assertEquals(DeletePlayerRequest(confirmAccountId = id), deleteRequestFor(" $id ", id))
    }

    @Test
    fun theDeleteCallSendsTheConfirmationWithTheAntiForgeryHeader() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = AdminApi("/harf", CredentialsMode.SAME_ORIGIN, HttpTransport { request -> requests += request; HttpResponse(204, "") }, { "XSRF-TOKEN=tok-9" })

        val result = api.deletePlayer(id, deleteRequestFor(id, id)!!)

        assertIs<ApiResult.Success<Unit>>(result)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/harf/api/v1/admin/players/$id/delete", request.url)
        assertEquals("""{"confirmAccountId":"$id"}""", request.body)
        assertEquals("tok-9", request.headers["X-XSRF-TOKEN"])
    }

    @Test
    fun theSearchCallCarriesTheQueryAndTheActionsTheirMethods() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val detail = """{"id":"$id","createdAt":1}"""
        val api = AdminApi("/harf", CredentialsMode.SAME_ORIGIN, HttpTransport { request ->
            requests += request
            HttpResponse(200, if (request.url.contains("?") || request.url.endsWith("/players")) """{"items":[]}""" else detail)
        }, { "XSRF-TOKEN=tok-9" })

        api.players(PlayerSearchQuery(q = "ali", blocked = true, cursor = "c/1"))
        api.blockPlayerSuggestions(id)
        api.unblockPlayerSuggestions(id)
        api.clearPlayerDisplayName(id)

        assertEquals("/harf/api/v1/admin/players?q=ali&blocked=true&cursor=c%2F1", requests[0].url)
        assertEquals(listOf("GET", "PUT", "DELETE", "DELETE"), requests.map { it.method })
        assertEquals("/harf/api/v1/admin/players/$id/suggestion-block", requests[1].url)
        assertEquals("/harf/api/v1/admin/players/$id/display-name", requests[3].url)
    }

    @Test
    fun detailRoutesAndIds() {
        assertEquals("/players/$id", Routes.player(id))
        assertEquals(id, accountIdFromRoute(id.uppercase()))
        assertNull(accountIdFromRoute("not-an-id"), "a malformed id is not requested")
        assertNull(accountIdFromRoute(null))
        assertEquals("3f1c2a9e", shortAccountId(id))
    }
}

private external fun decodeURIComponent(encoded: String): String
