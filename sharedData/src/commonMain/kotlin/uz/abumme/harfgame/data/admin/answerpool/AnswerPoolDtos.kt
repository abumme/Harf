package uz.abumme.harfgame.data.admin.answerpool

import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.calendar.ScheduledOnDto
import uz.abumme.harfgame.data.admin.words.StaffRefDto

/**
 * One entry of a calendar's answer pool: an eligible catalog word ([wordId], `en`/`ru`/`kk`) or an Uzbek pair
 * ([pairId] with both words). [active] is false while a word of the entry is removed: it is not picked until the word is
 * restored. [lastUsed] is the last counted day it was the daily word (through tomorrow); [scheduledOn] its next future
 * day.
 */
@Serializable
data class PoolWordDto(
    val wordId: String? = null,
    val pairId: String? = null,
    val text: String,
    val textCyrl: String? = null,
    val latnWordId: String? = null,
    val cyrlWordId: String? = null,
    val active: Boolean = true,
    val lastUsed: String? = null,
    val scheduledOn: ScheduledOnDto? = null,
)

/**
 * A page of the pool plus [unusedLeft]: eligible words never the daily word of a day from the history start through
 * tomorrow and not manually picked for a future day.
 */
@Serializable
data class PoolPageDto(
    val unusedLeft: Int,
    val page: PageDto<PoolWordDto>,
)

/** An active catalog word of a supported board length that is not yet eligible (for `uz`: a Latin word not in a pair). */
@Serializable
data class PoolCandidateDto(
    val wordId: String,
    val text: String,
    val graphemeCount: Int,
)

/** Words to mark, by catalog id or by spelling (a pasted list); at most `WordRules.MAX_BULK_LINES` entries in all. */
@Serializable
data class MarkWordsRequest(
    val wordIds: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
)

@Serializable
enum class MarkOutcome { MARKED, ALREADY_ELIGIBLE, NOT_IN_CATALOG, REMOVED, UNSUPPORTED_LENGTH }

/** The outcome of one entry: [input] is the id or line as sent, [text] the catalog spelling when found. */
@Serializable
data class MarkItemResultDto(
    val input: String,
    val wordId: String? = null,
    val text: String? = null,
    val outcome: MarkOutcome,
)

@Serializable
enum class CyrlStatus { ACTIVE, REMOVED, MISSING }

/**
 * The catalog state of a Cyrillic spelling for a new Uzbek pair: [normalized] is the spelling as it would be stored,
 * [pairedWith] the Latin word of the pair that already holds it.
 */
@Serializable
data class CyrlStatusDto(
    val normalized: String,
    val cyrlWordId: String? = null,
    val cyrlStatus: CyrlStatus,
    val pairedWith: String? = null,
)

/** A new Uzbek pair from a Latin catalog word and its confirmed Cyrillic spelling; [addCyrlToCatalog] adds or restores it. */
@Serializable
data class CreatePairRequest(
    val latnWordId: String,
    val cyrlText: String,
    val addCyrlToCatalog: Boolean = false,
)

@Serializable
data class PairDto(
    val id: String,
    val latnWordId: String,
    val latnText: String,
    val cyrlWordId: String,
    val cyrlText: String,
    val createdAt: Long,
    val createdBy: StaffRefDto? = null,
)

/** Query parameters of the answer-pool endpoints; [PAGE] is zero-based, [CYRL] the spelling to look up. */
object PoolParams {
    const val Q = "q"
    const val PAGE = "page"
    const val SIZE = "size"
    const val CYRL = "cyrl"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200
}

/** Reasons in answer-pool refusals (`field: reason`). Word validation reasons come from `WordReasons`. */
object PoolReasons {
    /** Uzbek eligibility is managed by pairs, not by single words. */
    const val PAIRS_ONLY = "pairs_only"

    /** More entries than one request may mark. */
    const val TOO_MANY_ITEMS = "too_many_items"

    /** The Latin word is not an active catalog word. */
    const val NOT_ACTIVE = "not_active"

    /** The Cyrillic spelling is not an active catalog word and was not to be added. */
    const val NOT_IN_CATALOG = "not_in_catalog"

    /** The Cyrillic spelling belongs to a removed word and was not to be restored. */
    const val REMOVED = "removed"

    /** 409: the word already belongs to a pair, named after the reason: `paired kitob/китоб`. */
    const val PAIRED = "paired"

    fun paired(latn: String, cyrl: String): String = "$PAIRED $latn/$cyrl"
}
