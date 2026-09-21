package uz.abumme.harfgame.admin.players

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.PlayerParams
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.players.PlayerSummaryDto
import uz.abumme.harfgame.data.admin.players.PlayerType

/** The account-type toggles of the players list; [ALL] filters nothing. */
enum class TypeFilter(val type: PlayerType?) {
    ALL(null),
    ANONYMOUS(PlayerType.ANONYMOUS),
    GOOGLE(PlayerType.GOOGLE),
    APPLE(PlayerType.APPLE),
}

/**
 * The `/players` filters, held as the shared [PlayerSearchQuery] (without a cursor), so the page URL, the API request
 * and a reload all read the same parameters. The "created to" date input shows the last included day, while the query
 * keeps the API's exclusive end (the next day).
 */
data class PlayerFilters(val query: PlayerSearchQuery = PlayerSearchQuery()) {
    val search: String get() = query.q
    val typeFilter: TypeFilter get() = TypeFilter.entries.first { it.type == query.type }

    /** `yyyy-mm-dd` for the "created from" date input, or empty. */
    val createdFrom: String get() = query.createdFrom?.toString().orEmpty()

    /** `yyyy-mm-dd` for the "created to" date input: the last day included, or empty. */
    val createdToInclusive: String get() = query.createdTo?.minus(1, DateTimeUnit.DAY)?.toString().orEmpty()

    val blockedOnly: Boolean get() = query.blocked == true

    /** Whether anything but the defaults is set (the reset button shows then). */
    val hasFilters: Boolean get() = query != PlayerSearchQuery()

    /** Why the search cannot be sent yet (a name shorter than the minimum), as a `PlayerReasons` value; null when it can. */
    val searchProblem: String? get() = query.problems().firstOrNull { it.field == PlayerParams.Q }?.reason

    fun withSearch(text: String) = copy(query = query.copy(q = text))
    fun withType(filter: TypeFilter) = copy(query = query.copy(type = filter.type))
    fun withCreatedFrom(isoDate: String) = copy(query = query.copy(createdFrom = parseDate(isoDate)))
    fun withCreatedToInclusive(isoDate: String) = copy(query = query.copy(createdTo = parseDate(isoDate)?.plus(1, DateTimeUnit.DAY)))

    /** On: only accounts with blocked suggestions. Off: no block filter. */
    fun withBlockedOnly(on: Boolean) = copy(query = query.copy(blocked = if (on) true else null))

    fun withoutFilters() = PlayerFilters()

    /** The page URL's query (`?q=…&type=…`), holding only what differs from the defaults. */
    fun toRouteQuery(): String = queryString(*query.toQueryParameters().toTypedArray())

    /** The API request for the page after [cursor] (the first page when null). */
    fun toApiQuery(cursor: String?): PlayerSearchQuery = query.copy(q = query.text, cursor = cursor)

    companion object {
        /** The filters a page URL describes; malformed values fall back to their defaults. */
        fun fromRoute(params: Map<String, String>): PlayerFilters {
            val parsed = PlayerSearchQuery.fromQueryParameters(params).query
            return PlayerFilters(
                parsed.copy(cursor = null, size = PlayerParams.DEFAULT_SIZE, blocked = parsed.blocked.takeIf { it == true }),
            )
        }

        private fun parseDate(isoDate: String): LocalDate? = try {
            isoDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * The rows of one search, loaded page by page with "Показать ещё": the first request has no cursor, each next one
 * continues after [nextCursor], and the list ends when the server reports none. New filters start over.
 */
data class PlayerListState(
    val filters: PlayerFilters = PlayerFilters(),
    val items: List<PlayerSummaryDto> = emptyList(),
    val nextCursor: String? = null,
    val loaded: Boolean = false,
) {
    /** More results follow the loaded ones. */
    val hasMore: Boolean get() = loaded && nextCursor != null

    /** The request for the next page, or null when every page is loaded (or the search cannot be sent). */
    fun nextRequest(): PlayerSearchQuery? = when {
        filters.searchProblem != null -> null
        !loaded -> filters.toApiQuery(cursor = null)
        nextCursor != null -> filters.toApiQuery(nextCursor)
        else -> null
    }

    /** Appends a page answered for [requestedFilters]; a late answer for filters since changed is dropped. */
    fun append(requestedFilters: PlayerFilters, page: PlayerSearchPageDto): PlayerListState =
        if (requestedFilters != filters) this
        else copy(items = items + page.items.filter { new -> items.none { it.id == new.id } }, nextCursor = page.nextCursor, loaded = true)

    /** The same list for unchanged filters, an empty one for new filters. */
    fun withFilters(newFilters: PlayerFilters): PlayerListState = if (newFilters == filters) this else PlayerListState(newFilters)
}

/** The first characters of an account id, enough to tell rows apart at a glance. */
fun shortAccountId(id: String): String = id.substringBefore('-').ifEmpty { id.take(8) }

/** The account id a detail URL names, or null when it is not shaped like one (shown as not found, nothing requested). */
fun accountIdFromRoute(raw: String?): String? = raw?.trim()?.takeIf(PlayerSearchQuery::isAccountId)?.lowercase()

/** Whether the typed confirmation names the account: the whole id, exactly (case-sensitive), ignoring surrounding blanks. */
fun deleteConfirmationMatches(typed: String, accountId: String): Boolean = accountId.isNotEmpty() && typed.trim() == accountId

/** The deletion request once the confirmation matches; null keeps the delete button disabled. */
fun deleteRequestFor(typed: String, accountId: String): DeletePlayerRequest? =
    if (deleteConfirmationMatches(typed, accountId)) DeletePlayerRequest(confirmAccountId = accountId) else null
