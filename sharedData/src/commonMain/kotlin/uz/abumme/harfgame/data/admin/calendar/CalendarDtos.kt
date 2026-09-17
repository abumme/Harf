package uz.abumme.harfgame.data.admin.calendar

import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.words.StaffRefDto

/**
 * The daily-word calendars: one per language, and one for Uzbek that publishes the same lexeme in both scripts. Days
 * are counted in each calendar's language timezone (`PuzzleDays`); a day is an ISO date `yyyy-mm-dd`.
 */
object DailyCalendars {
    const val EN = "en"
    const val RU = "ru"
    const val KK = "kk"
    const val UZ = "uz"

    /** Every calendar, in the panel's order. */
    val ALL: List<String> = listOf(EN, RU, KK, UZ)

    /** How many days after today every calendar keeps filled. */
    const val HORIZON_DAYS = 60

    /** The farthest day, counted from today, an ADMIN may pick a word for. */
    const val MAX_PICK_DAYS = 365

    /** Past days, today and tomorrow are locked: the earliest day that can change is today + this. */
    const val FIRST_OPEN_OFFSET = 2

    fun isCalendar(id: String): Boolean = id in ALL

    /** The pack languages a calendar publishes, in the fixed order their pack rows are locked. */
    fun packLanguages(calendar: String): List<String> = when (calendar) {
        UZ -> listOf("uz-cyrl", "uz-latn")
        EN, RU, KK -> listOf(calendar)
        else -> emptyList()
    }

    /** The calendar a pack language belongs to, or null for a language without one. */
    fun calendarOf(lang: String): String? = when (lang) {
        "uz-latn", "uz-cyrl" -> UZ
        EN, RU, KK -> lang
        else -> null
    }

    /** The pack language whose rules and timezone a calendar's words follow: Uzbek days are known by their Latin word. */
    fun wordLanguage(calendar: String): String = if (calendar == UZ) "uz-latn" else calendar
}

/** How a day got its word: an ADMIN's pick, an automatic pick, or the schedule published before the calendar existed. */
@Serializable
enum class DaySource { MANUAL, AUTO, LEGACY }

/**
 * One calendar day. [text] is the published word (the Latin one for `uz`, with [textCyrl]); [lastUsed] is the previous
 * counted use of a repeat's word; [locked] days (past, today, tomorrow) cannot change. [wordId] or [pairId] names the
 * catalog word or Uzbek pair a pick was made from (null for a legacy word not in the catalog). [pickedAt] is epoch ms.
 */
@Serializable
data class DayDto(
    val day: String,
    val text: String,
    val textCyrl: String? = null,
    val source: DaySource,
    val isRepeat: Boolean = false,
    val lastUsed: String? = null,
    val locked: Boolean,
    val wordId: String? = null,
    val pairId: String? = null,
    val pickedBy: StaffRefDto? = null,
    val pickedAt: Long,
)

/** A manual pick: a catalog word ([wordId], `en`/`ru`/`kk`) or an Uzbek pair ([pairId]). */
@Serializable
data class PickDayRequest(
    val wordId: String? = null,
    val pairId: String? = null,
)

/** A future day a word is scheduled on. */
@Serializable
data class ScheduledOnDto(
    val day: String,
    val source: DaySource,
)

/** An eligible word offered when picking a day, with how it has been used. */
@Serializable
data class CalendarCandidateDto(
    val wordId: String? = null,
    val pairId: String? = null,
    val text: String,
    val textCyrl: String? = null,
    /** Never the daily word of a counted day through tomorrow. */
    val neverUsed: Boolean,
    val lastUsed: String? = null,
    val scheduledOn: ScheduledOnDto? = null,
)

@Serializable
enum class NoticeKind { MANUAL_PICK_REPLACED }

/** Why a manual pick was replaced: its word was removed from the catalog, or it left the answer pool. */
@Serializable
enum class NoticeReason { REMOVED, INELIGIBLE }

/** Something ADMINs should know about a calendar; shown until dismissed. Times are epoch ms. */
@Serializable
data class CalendarNoticeDto(
    val id: String,
    val calendar: String,
    val day: String,
    val kind: NoticeKind,
    val wordText: String,
    val reason: NoticeReason,
    val createdAt: Long,
    val dismissedAt: Long? = null,
    val dismissedBy: StaffRefDto? = null,
)

/**
 * Query parameters of the calendar endpoints. [FROM]/[TO] are inclusive ISO dates, [SOURCE] a [DaySource] name,
 * [REPEATS_ONLY] `true` to list repeat days only, [PAGE] zero-based.
 */
object CalendarParams {
    const val FROM = "from"
    const val TO = "to"
    const val SOURCE = "source"
    const val REPEATS_ONLY = "repeatsOnly"
    const val DAY = "day"
    const val Q = "q"
    const val PAGE = "page"
    const val SIZE = "size"
    const val INCLUDE_DISMISSED = "includeDismissed"

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200
}

/** Reasons in calendar refusals (`field: reason` in `ApiErrorResponse.message`, fields `day`, `word`, `calendar`). */
object CalendarReasons {
    /** Past, today or tomorrow. */
    const val LOCKED = "locked"

    /** More than [DailyCalendars.MAX_PICK_DAYS] days ahead. */
    const val TOO_FAR = "too_far"

    /** Not in the calendar's answer pool (or not an active word). */
    const val NOT_ELIGIBLE = "not_eligible"

    /** Only a manual pick can be unpicked. */
    const val NOT_MANUAL = "not_manual"

    /** 409: the word was the daily word of the listed counted days. */
    const val USED = "used"

    /** 409: the word is manually picked for the listed day. */
    const val PICKED = "picked"

    /** A conflict reason naming days: `used 2026-09-14,2026-09-15`. */
    fun conflict(kind: String, days: List<String>): String = "$kind ${days.joinToString(",")}"

    /** The kind and days of a [conflict] reason, or null for any other reason. */
    fun parseConflict(reason: String): Pair<String, List<String>>? {
        val kind = reason.substringBefore(' ')
        if ((kind != USED && kind != PICKED) || ' ' !in reason) return null
        return kind to reason.substringAfter(' ').split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
