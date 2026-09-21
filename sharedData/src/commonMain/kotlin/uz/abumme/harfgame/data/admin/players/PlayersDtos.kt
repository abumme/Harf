package uz.abumme.harfgame.data.admin.players

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.auth.OAuthProvider
import uz.abumme.harfgame.data.suggestion.SuggestionStatus

// The player-account admin API (ADMIN only). No DTO here has a field for a provider subject or any token, and every one
// is compiled into the panel's browser bundle too, so secrets cannot reach the panel by accident. Times are epoch
// milliseconds (UTC), like the rest of the admin API.

/** The account-type filter: no linked provider, or linked to Google / Apple (an account with both matches both). */
@Serializable
enum class PlayerType { ANONYMOUS, GOOGLE, APPLE }

/** One search result. [providers] are the linked provider types, sorted. */
@Serializable
data class PlayerSummaryDto(
    val id: String,
    val displayName: String? = null,
    val providers: List<OAuthProvider> = emptyList(),
    val createdAt: Long,
    val suggestionsBlocked: Boolean = false,
)

/** A page of `GET` [uz.abumme.harfgame.data.admin.AdminRoutes.PLAYERS], newest account first; [nextCursor] null on the last. */
@Serializable
data class PlayerSearchPageDto(
    val items: List<PlayerSummaryDto>,
    val nextCursor: String? = null,
)

/** One language's synced results, by the app's own rules (games, wins, win rate, current and best streak). */
@Serializable
data class PlayerLanguageStatsDto(
    val lang: String,
    val games: Int,
    val wins: Int,
    val winRate: Float,
    val currentStreak: Int,
    val bestStreak: Int,
)

@Serializable
data class SuggestionCountsDto(
    val pending: Long = 0,
    val accepted: Long = 0,
    val rejected: Long = 0,
)

/** One of a player's recent suggestions. */
@Serializable
data class PlayerSuggestionDto(
    val id: String,
    val lang: String,
    val word: String,
    val status: SuggestionStatus,
    val createdAt: Long,
    val decidedAt: Long? = null,
)

/** Who blocked the account's suggestions, and when. */
@Serializable
data class SuggestionBlockDto(
    val blockedAt: Long,
    val blockedBy: StaffRefDto,
)

/**
 * One player account as an ADMIN sees it. [lastStatsSnapshotAt] is the latest accepted stats snapshot's time;
 * [activeSessions] counts refresh tokens that are not revoked, not expired and not yet rotated; [languages] has one
 * entry per language with synced results; [recentSuggestions] are the newest [PlayerParams.RECENT_SUGGESTIONS];
 * [suggestionBlock] is null while the account may suggest.
 */
@Serializable
data class PlayerDetailDto(
    val id: String,
    val createdAt: Long,
    val displayName: String? = null,
    val providers: List<OAuthProvider> = emptyList(),
    val lastStatsSnapshotAt: Long? = null,
    val activeSessions: Long = 0,
    val languages: List<PlayerLanguageStatsDto> = emptyList(),
    val suggestionCounts: SuggestionCountsDto = SuggestionCountsDto(),
    val recentSuggestions: List<PlayerSuggestionDto> = emptyList(),
    val suggestionBlock: SuggestionBlockDto? = null,
)

/** `POST` [uz.abumme.harfgame.data.admin.AdminRoutes.playerDelete]: the account id again, or nothing is deleted (422). */
@Serializable
data class DeletePlayerRequest(
    val confirmAccountId: String,
)

/** `POST` [uz.abumme.harfgame.data.admin.AdminRoutes.playerEndSessions]: how many refresh tokens were revoked. */
@Serializable
data class EndSessionsResultDto(
    val revoked: Int,
)

/** Query parameter names and limits of the player search. */
object PlayerParams {
    const val Q = "q"
    const val TYPE = "type"
    const val CREATED_FROM = "createdFrom"
    const val CREATED_TO = "createdTo"
    const val BLOCKED = "blocked"
    const val CURSOR = "cursor"
    const val SIZE = "size"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 100

    /** A display-name search needs at least this many characters (an account id is always accepted). */
    const val MIN_NAME_QUERY = 2

    /** How many of the newest suggestions a player's detail lists. */
    const val RECENT_SUGGESTIONS = 20
}

/** The `reason` vocabulary of player-account field errors, besides [FieldReasons]. */
object PlayerReasons {
    /** A display-name search shorter than [PlayerParams.MIN_NAME_QUERY]. */
    const val TOO_SHORT = "too_short"

    /** A deletion whose `confirmAccountId` does not name the account. */
    const val MISMATCH = "mismatch"
}

/**
 * One player search, defined once for the panel (which keeps its filters in the page URL) and the server (which
 * parses the same parameters), so both build and read them identically.
 *
 * [q] is an account id (exact match, any other filter still applies) or part of a display name. [createdFrom] is
 * inclusive and [createdTo] exclusive, both Asia/Tashkent dates. [cursor] is the opaque [PlayerSearchPageDto.nextCursor]
 * of the previous page.
 */
data class PlayerSearchQuery(
    val q: String = "",
    val type: PlayerType? = null,
    val createdFrom: LocalDate? = null,
    val createdTo: LocalDate? = null,
    val blocked: Boolean? = null,
    val cursor: String? = null,
    val size: Int = PlayerParams.DEFAULT_SIZE,
) {
    /** The search text without surrounding blanks. */
    val text: String get() = q.trim()

    /** The account id searched for, when the text is shaped like one (lowercase, as ids are stored). */
    val accountId: String? get() = text.takeIf(::isAccountId)?.lowercase()

    /** The display-name fragment searched for, when the text is not an account id. */
    val namePart: String? get() = text.takeIf { it.isNotEmpty() && accountId == null }

    /** What the server refuses in this query, in parameter order; empty when it can be sent. */
    fun problems(): List<FieldError> = buildList {
        namePart?.let { if (it.length < PlayerParams.MIN_NAME_QUERY) add(FieldError(PlayerParams.Q, PlayerReasons.TOO_SHORT)) }
        // `this@…`: inside buildList a bare `size` is the list's own size.
        if (this@PlayerSearchQuery.size !in 1..PlayerParams.MAX_SIZE) add(FieldError(PlayerParams.SIZE, FieldReasons.INVALID))
    }

    /** The query parameters holding only what differs from the defaults, in a stable order. */
    fun toQueryParameters(): List<Pair<String, String>> = buildList {
        text.takeIf { it.isNotEmpty() }?.let { add(PlayerParams.Q to it) }
        type?.let { add(PlayerParams.TYPE to it.name.lowercase()) }
        createdFrom?.let { add(PlayerParams.CREATED_FROM to it.toString()) }
        createdTo?.let { add(PlayerParams.CREATED_TO to it.toString()) }
        blocked?.let { add(PlayerParams.BLOCKED to it.toString()) }
        cursor?.let { add(PlayerParams.CURSOR to it) }
        this@PlayerSearchQuery.size.takeIf { it != PlayerParams.DEFAULT_SIZE }?.let { add(PlayerParams.SIZE to it.toString()) }
    }

    companion object {
        private val ACCOUNT_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Whether [text] is shaped like an account id (a UUID). */
        fun isAccountId(text: String): Boolean = ACCOUNT_ID.matches(text)

        /**
         * The query [parameters] describe. A malformed value is left at its default and named in
         * [ParsedPlayerSearch.invalid]: the server refuses it, the panel just ignores it. Blank values count as absent.
         */
        fun fromQueryParameters(parameters: Map<String, String>): ParsedPlayerSearch {
            val invalid = mutableListOf<FieldError>()
            fun value(name: String) = parameters[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun <T> parsed(name: String, parse: (String) -> T?): T? = value(name)?.let { raw ->
                parse(raw) ?: null.also { invalid += FieldError(name, FieldReasons.INVALID) }
            }
            val query = PlayerSearchQuery(
                q = value(PlayerParams.Q).orEmpty(),
                type = parsed(PlayerParams.TYPE) { raw -> PlayerType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } },
                createdFrom = parsed(PlayerParams.CREATED_FROM, ::parseDate),
                createdTo = parsed(PlayerParams.CREATED_TO, ::parseDate),
                blocked = parsed(PlayerParams.BLOCKED) { it.lowercase().toBooleanStrictOrNull() },
                cursor = value(PlayerParams.CURSOR),
                size = parsed(PlayerParams.SIZE) { it.toIntOrNull() } ?: PlayerParams.DEFAULT_SIZE,
            )
            return ParsedPlayerSearch(query, invalid)
        }

        private fun parseDate(raw: String): LocalDate? = try {
            LocalDate.parse(raw)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** [query] as parsed, and the parameters whose values were malformed. */
data class ParsedPlayerSearch(val query: PlayerSearchQuery, val invalid: List<FieldError>)
