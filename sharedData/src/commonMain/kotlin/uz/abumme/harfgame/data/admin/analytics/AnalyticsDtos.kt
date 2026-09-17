package uz.abumme.harfgame.data.admin.analytics

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.StaffRefDto

// The analytics admin API (ADMIN only). Aggregates only: no DTO here has a field for a player account id, display name
// or provider subject. Days and dates are ISO `yyyy-mm-dd`. A result metric's day is the puzzle day (the day in the
// language's own timezone); every other metric's date is the Asia/Tashkent date of the event. A value the server does
// not know is null, never 0. `provisional` marks a day still inside its 7-day settle window; `partial` marks today's
// live values.

/** Query parameter names and limits of the analytics endpoints. */
object AnalyticsParams {
    /** First day of the range, inclusive (ISO date). */
    const val FROM = "from"

    /** Last day of the range, inclusive (ISO date). */
    const val TO = "to"

    /** A pack language (`en`, `ru`, `kk`, `uz-latn`, `uz-cyrl`); absent means every language. */
    const val LANG = "lang"

    /** A daily-word calendar (`en`, `ru`, `kk`, `uz`) for word difficulty; absent means every calendar. */
    const val CALENDAR = "calendar"

    /** Word difficulty order: one of [WordSort]'s parameter values. */
    const val SORT = "sort"

    /** `asc` or `desc` (default). */
    const val DIR = "dir"

    const val DIR_ASC = "asc"
    const val DIR_DESC = "desc"

    /** Without `from`/`to` the server shows this many closed days, ending with the last closed one. */
    const val DEFAULT_DAYS = 30

    /** The longest range, in days, a request may ask for. */
    const val MAX_DAYS = 366
}

/** The `reason` vocabulary of analytics field errors (besides [FieldReasons.INVALID] and [FieldReasons.UNKNOWN]). */
object AnalyticsReasons {
    /** `to: before_from`: the range ends before it starts. */
    const val BEFORE_FROM = "before_from"

    /** `to: range_too_long`: more than [AnalyticsParams.MAX_DAYS] days. */
    const val RANGE_TOO_LONG = "range_too_long"
}

/** The range rules both the panel (before any request) and the server (before any query) apply. */
object AnalyticsRange {
    /** Days in the inclusive range [from]..[to]. */
    fun days(from: LocalDate, to: LocalDate): Long = to.toEpochDays().toLong() - from.toEpochDays().toLong() + 1

    /** Why [from]..[to] is refused, or null when it may be queried. */
    fun problem(from: LocalDate, to: LocalDate): FieldError? = when {
        from > to -> FieldError(AnalyticsParams.TO, AnalyticsReasons.BEFORE_FROM)
        days(from, to) > AnalyticsParams.MAX_DAYS -> FieldError(AnalyticsParams.TO, AnalyticsReasons.RANGE_TOO_LONG)
        else -> null
    }

    /** The default range: [AnalyticsParams.DEFAULT_DAYS] days ending with [lastClosed]. */
    fun defaultFrom(lastClosed: LocalDate): LocalDate =
        LocalDate.fromEpochDays(lastClosed.toEpochDays() - (AnalyticsParams.DEFAULT_DAYS - 1))
}

/** The analytics tables, each with a JSON endpoint and a CSV export at [path]. */
@Serializable
enum class AnalyticsTable(val path: String) {
    ACCOUNTS("accounts"),
    ACTIVITY("activity"),
    RETENTION("retention"),
    STREAKS("streaks"),
    OUTCOMES("outcomes"),
    WORDS("words"),
    SUGGESTIONS("suggestions"),
    CONTENT("content"),
    STAFF("staff"),
}

/** How the word difficulty table is ordered; [DAY] (newest first) is the default. */
enum class WordSort(val param: String) {
    DAY("day"),
    PLAYERS("players"),
    WIN_RATE("winRate"),
    AVG_ATTEMPTS("avgAttempts");

    companion object {
        fun fromParam(value: String?): WordSort? = entries.firstOrNull { it.param == value }
    }
}

/** The inclusive range a response covers. */
@Serializable
data class DayRangeDto(val from: String, val to: String)

// --- overview -----------------------------------------------------------------------------------------------------

/** A KPI tile: the value of [day] and of the day before it, for the delta. */
@Serializable
data class KpiDto(
    val day: String?,
    val value: Double?,
    val previousDay: String?,
    val previous: Double?,
    val provisional: Boolean,
)

/** Players and games of a language's current puzzle day so far (live; always partial). */
@Serializable
data class TodaySoFarDto(
    val lang: String,
    val day: String,
    val players: Long,
    val games: Long,
    val partial: Boolean,
)

/**
 * The overview tiles. Result tiles ([dau], [wau], [mau], [games], [winRate]) show the last closed puzzle day, event
 * tiles ([newAccounts], [totalAccounts], [backlog]) the last closed Asia/Tashkent date.
 */
@Serializable
data class OverviewDto(
    val dau: KpiDto,
    val wau: KpiDto,
    val mau: KpiDto,
    val games: KpiDto,
    val winRate: KpiDto,
    val newAccounts: KpiDto,
    val totalAccounts: KpiDto,
    val backlog: KpiDto,
    val today: List<TodaySoFarDto>,
)

// --- accounts -----------------------------------------------------------------------------------------------------

/**
 * One date of account metrics. Links are null for dates before link times were recorded; the end-of-day totals are
 * null for dates before the rollup first ran (they cannot be reconstructed later). [anonymous] is [total] − [linked].
 */
@Serializable
data class AccountsDayDto(
    val date: String,
    val newAccounts: Long,
    val linksGoogle: Long?,
    val linksApple: Long?,
    val deletions: Long,
    val total: Long?,
    val linked: Long?,
    val googleLinked: Long?,
    val appleLinked: Long?,
    val anonymous: Long?,
    val provisional: Boolean,
)

/** Account metrics are not per language: [languageFiltered] is always false. */
@Serializable
data class AccountsDto(
    val range: DayRangeDto,
    val languageFiltered: Boolean,
    val days: List<AccountsDayDto>,
)

// --- activity -----------------------------------------------------------------------------------------------------

/** Distinct accounts with a result ([players]) and results ([games]) of one language's puzzle day. */
@Serializable
data class ActivityLangDayDto(
    val lang: String,
    val day: String,
    val players: Long,
    val games: Long,
    val provisional: Boolean,
)

/** Distinct accounts with a result in any language on the day, in the 7 and in the 30 days ending with it. */
@Serializable
data class GlobalDayDto(
    val day: String,
    val dau: Long,
    val wau: Long,
    val mau: Long,
    val provisional: Boolean,
)

/**
 * Per-language players and games (filtered by `lang`), the cross-language DAU/WAU/MAU ([global], never filtered:
 * [languageFiltered] is false) and today's partial values.
 */
@Serializable
data class ActivityDto(
    val range: DayRangeDto,
    val lang: String?,
    val languages: List<ActivityLangDayDto>,
    val global: List<GlobalDayDto>,
    val languageFiltered: Boolean,
    val today: List<TodaySoFarDto>,
)

// --- retention ----------------------------------------------------------------------------------------------------

/**
 * The accounts whose earliest result (any language) is on [day], and how many of them have a result exactly 1, 7 and
 * 30 days later. A value is null until that target day has closed.
 */
@Serializable
data class CohortDto(
    val day: String,
    val size: Long,
    val d1: Long?,
    val d7: Long?,
    val d30: Long?,
    val provisional: Boolean,
)

@Serializable
data class RetentionDto(
    val range: DayRangeDto,
    val languageFiltered: Boolean,
    val cohorts: List<CohortDto>,
)

// --- streaks ------------------------------------------------------------------------------------------------------

/** Accounts by current streak on a language's puzzle day, by the app's streak rule with that day as today. */
@Serializable
data class StreakDayDto(
    val lang: String,
    val day: String,
    val streak1: Long,
    val streak2to6: Long,
    val streak7to29: Long,
    val streak30plus: Long,
    val provisional: Boolean,
)

@Serializable
data class StreaksDto(
    val range: DayRangeDto,
    val lang: String?,
    val days: List<StreakDayDto>,
)

// --- outcomes -----------------------------------------------------------------------------------------------------

/**
 * Outcomes of a language's puzzle day. [distribution] holds wins solved in 1..6 attempts (six entries); [winRate] is
 * wins / games (null without games) and [avgAttempts] the mean attempts of wins (null without wins).
 */
@Serializable
data class OutcomeDayDto(
    val lang: String,
    val day: String,
    val games: Long,
    val wins: Long,
    val losses: Long,
    val distribution: List<Long>,
    val winRate: Double?,
    val avgAttempts: Double?,
    val provisional: Boolean,
)

/** The same over the whole range, per language, plus one row for every language together ([lang] null). */
@Serializable
data class OutcomeTotalsDto(
    val lang: String?,
    val games: Long,
    val wins: Long,
    val losses: Long,
    val distribution: List<Long>,
    val winRate: Double?,
    val avgAttempts: Double?,
    val provisional: Boolean,
)

@Serializable
data class OutcomesDto(
    val range: DayRangeDto,
    val lang: String?,
    val days: List<OutcomeDayDto>,
    val totals: List<OutcomeTotalsDto>,
)

// --- word difficulty ----------------------------------------------------------------------------------------------

/**
 * A past daily word of a calendar. Uzbek combines both scripts ([word] Latin, [wordCyrl] Cyrillic). [marker] is how the
 * calendar picked it (MANUAL or AUTO), null for a day the calendar did not pick; [repeat] marks a reused word.
 */
@Serializable
data class WordDayDto(
    val calendar: String,
    val day: String,
    val word: String,
    val wordCyrl: String?,
    val marker: DaySource?,
    val repeat: Boolean,
    val players: Long,
    val wins: Long,
    val winRate: Double?,
    val avgAttempts: Double?,
    val provisional: Boolean,
)

@Serializable
data class WordDifficultyDto(
    val range: DayRangeDto,
    val calendar: String?,
    val sort: String,
    val dir: String,
    val rows: List<WordDayDto>,
)

// --- suggestions --------------------------------------------------------------------------------------------------

/**
 * Suggestions of one language on one date: stored that day, decided that day (automatic, editor-accepted, rejected),
 * pending at the end of the day, and the median seconds from submission to decision for automatic and for editor
 * decisions made that day (null without such decisions).
 */
@Serializable
data class SuggestionDayDto(
    val date: String,
    val lang: String,
    val submitted: Long,
    val autoAccepted: Long,
    val editorAccepted: Long,
    val rejected: Long,
    val backlogEnd: Long,
    val medianAutoSeconds: Double?,
    val medianEditorSeconds: Double?,
    val provisional: Boolean,
)

@Serializable
data class SuggestionsAnalyticsDto(
    val range: DayRangeDto,
    val lang: String?,
    val days: List<SuggestionDayDto>,
)

// --- content ------------------------------------------------------------------------------------------------------

/** Catalog words of one language on one date: active at the end of the day, added by source, removed and restored. */
@Serializable
data class ContentDayDto(
    val date: String,
    val lang: String,
    val activeWords: Long?,
    val addedBundled: Long,
    val addedSuggestion: Long,
    val addedAuto: Long,
    val addedStaff: Long,
    val removed: Long,
    val restored: Long,
    val provisional: Boolean,
)

/** A calendar's answer pool at the end of a date (Uzbek counts pairs) and its never-used eligible words. */
@Serializable
data class PoolDayDto(
    val date: String,
    val calendar: String,
    val poolSize: Long?,
    val unusedLeft: Long?,
    val provisional: Boolean,
)

/** A language's active catalog words now. */
@Serializable
data class ContentCurrentDto(
    val lang: String,
    val activeWords: Long,
)

/**
 * A calendar now: pool size and never-used words (null before the calendar's first run), repeat days in the range up to
 * today, and repeat days scheduled after today.
 */
@Serializable
data class CalendarContentDto(
    val calendar: String,
    val poolSize: Long?,
    val unusedLeft: Long?,
    val repeatDays: Long,
    val upcomingRepeatDays: Long,
)

@Serializable
data class ContentDto(
    val range: DayRangeDto,
    val lang: String?,
    val days: List<ContentDayDto>,
    val pool: List<PoolDayDto>,
    val current: List<ContentCurrentDto>,
    val calendars: List<CalendarContentDto>,
)

// --- staff activity -----------------------------------------------------------------------------------------------

/**
 * What one staff member did in one language on one date: words added (restores included), edited and removed, and
 * suggestions decided in the panel or through their linked Telegram account. Telegram decisions of no staff member
 * have [staff] null and [telegramUnlinked] true. Automatic acceptances count for nobody.
 */
@Serializable
data class StaffDayDto(
    val date: String,
    val staff: StaffRefDto?,
    val telegramUnlinked: Boolean,
    val lang: String,
    val added: Long,
    val edited: Long,
    val removed: Long,
    val decided: Long,
    val provisional: Boolean,
)

@Serializable
data class StaffActivityDto(
    val range: DayRangeDto,
    val lang: String?,
    val rows: List<StaffDayDto>,
)
