package uz.abumme.harfgame.admin.analytics

import kotlinx.datetime.LocalDate
import uz.abumme.harfgame.admin.api.queryString
import uz.abumme.harfgame.admin.calendar.dateInZone
import uz.abumme.harfgame.admin.components.TableSort
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.analytics.AnalyticsParams
import uz.abumme.harfgame.data.admin.analytics.AnalyticsRange
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.WordSort
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.wordpack.PuzzleDays
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The last puzzle day that has ended in every language's zone at [epochMillis], the server's default range end. Dates
 * come from the browser's `Intl` data (kotlinx-datetime on Kotlin/JS has no time zones).
 */
fun lastClosedDay(epochMillis: Double): LocalDate {
    val earliestToday = PuzzleDays.allZones.minOf { dateInZone(it, epochMillis) }
    return LocalDate.fromEpochDays(earliestToday.toEpochDays() - 1)
}

/**
 * The dashboard's filter: an inclusive range of ISO dates (as the date inputs hold them) and a pack language, null for
 * all languages. Every section request and every CSV link takes its query from [queryFor], so they always agree.
 */
data class AnalyticsFilter(val from: String, val to: String, val lang: String? = null) {

    fun withFrom(value: String) = copy(from = value)
    fun withTo(value: String) = copy(to = value)

    /** `ALL_LANGUAGES` (or blank) clears the language. */
    fun withLang(value: String?) = copy(lang = value?.takeIf { it.isNotBlank() && it != ALL_LANGUAGES })

    /** Why the range cannot be requested (checked before any request), or null. */
    val problem: FieldError?
        get() {
            val start = parse(from) ?: return FieldError(AnalyticsParams.FROM, FieldReasons.INVALID)
            val end = parse(to) ?: return FieldError(AnalyticsParams.TO, FieldReasons.INVALID)
            return AnalyticsRange.problem(start, end)
        }

    val isValid: Boolean get() = problem == null

    /** The Uzbek scripts share one calendar; word difficulty filters by calendar. */
    val calendar: String? get() = lang?.let(DailyCalendars::calendarOf)

    /**
     * The query parameters of [table], identical for its JSON request and its CSV export. Account and retention metrics
     * are not per language and take no `lang`; word difficulty takes the calendar and the table's sort.
     */
    fun parameters(table: AnalyticsTable, sort: TableSort? = null): List<Pair<String, String>> = buildList {
        add(AnalyticsParams.FROM to from)
        add(AnalyticsParams.TO to to)
        when (table) {
            AnalyticsTable.ACCOUNTS, AnalyticsTable.RETENTION -> Unit
            AnalyticsTable.WORDS -> {
                calendar?.let { add(AnalyticsParams.CALENDAR to it) }
                addAll(wordSortParameters(sort))
            }
            else -> lang?.let { add(AnalyticsParams.LANG to it) }
        }
    }

    fun queryFor(table: AnalyticsTable, sort: TableSort? = null): String = queryString(*parameters(table, sort).toTypedArray())

    /** The page URL's query, so a reload or a shared link keeps the filter. */
    fun toRouteQuery(): String = queryString(AnalyticsParams.FROM to from, AnalyticsParams.TO to to, AnalyticsParams.LANG to lang)

    companion object {
        const val ALL_LANGUAGES = "all"

        /** The last [AnalyticsParams.DEFAULT_DAYS] closed days across all languages. */
        fun default(lastClosed: LocalDate) =
            AnalyticsFilter(AnalyticsRange.defaultFrom(lastClosed).toString(), lastClosed.toString(), null)

        /** The filter in the page URL, falling back to the default for what is missing. */
        fun fromRoute(params: Map<String, String>, lastClosed: LocalDate): AnalyticsFilter {
            val fallback = default(lastClosed)
            return AnalyticsFilter(
                from = params[AnalyticsParams.FROM]?.takeIf { it.isNotBlank() } ?: fallback.from,
                to = params[AnalyticsParams.TO]?.takeIf { it.isNotBlank() } ?: fallback.to,
                lang = params[AnalyticsParams.LANG]?.takeIf { it.isNotBlank() && it != ALL_LANGUAGES },
            )
        }

        private fun parse(value: String): LocalDate? = try {
            LocalDate.parse(value)
        } catch (e: Exception) {
            null
        }
    }
}

/** The word difficulty table's sortable columns (their `TableColumn.sortKey`s are the parameter values). */
val WORD_SORT_KEYS: List<String> = listOf(WordSort.PLAYERS.param, WordSort.WIN_RATE.param, WordSort.AVG_ATTEMPTS.param)

/** `sort` and `dir` for a table sort; none for the default order (newest day first). */
fun wordSortParameters(sort: TableSort?): List<Pair<String, String>> {
    val known = sort?.takeIf { it.key in WORD_SORT_KEYS } ?: return emptyList()
    return listOf(
        AnalyticsParams.SORT to known.key,
        AnalyticsParams.DIR to if (known.descending) AnalyticsParams.DIR_DESC else AnalyticsParams.DIR_ASC,
    )
}

/** A click on a column header: a new column starts descending (most players first), the same one flips. */
fun nextWordSort(current: TableSort?, key: String): TableSort =
    if (current?.key == key) current.copy(descending = !current.descending) else TableSort(key, descending = true)

/**
 * The download link of [table]'s CSV export: the API's base (`/harf` in the exported panel) plus the table's CSV path
 * from `AdminRoutes` and the current filter's query. A same-origin GET, so the session cookie goes with it.
 */
fun csvHref(apiBase: String, table: AnalyticsTable, filter: AnalyticsFilter, sort: TableSort? = null): String =
    apiBase.trimEnd('/') + AdminRoutes.analyticsCsv(table) + filter.queryFor(table, sort)

/** Every ISO day of the inclusive range, oldest first: a chart's label axis. Empty for an invalid range. */
fun daysInRange(from: String, to: String): List<String> {
    val start = runCatching { LocalDate.parse(from) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(to) }.getOrNull() ?: return emptyList()
    if (start > end) return emptyList()
    return (start.toEpochDays()..end.toEpochDays()).map { LocalDate.fromEpochDays(it).toString() }
}

/** One value per day of [days] from [byDay]; a day without a value is null (a gap, never 0). */
fun <T> perDay(days: List<String>, byDay: Map<String, T>, value: (T) -> Double?): List<Double?> =
    days.map { day -> byDay[day]?.let(value) }

/** Which of [days] are provisional, from the days whose rows say so. */
fun dimmedDays(days: List<String>, provisional: Set<String>): List<Boolean> = days.map { it in provisional }

// --- formatting ----------------------------------------------------------------------------------------------------

private const val DASH = "—"
private const val NBSP = ' '
private const val MINUS = '−'

/** "12 345" with a non-breaking space between thousands; "—" for an unknown value. */
fun formatCount(value: Long?): String {
    if (value == null) return DASH
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    return if (value < 0) "$MINUS$grouped" else grouped
}

fun formatCount(value: Double?): String = formatCount(value?.roundToLong())

/** [value] with [decimals] digits after a decimal comma ("3,0"); "—" when unknown. */
fun formatDecimal(value: Double?, decimals: Int = 1): String {
    if (value == null || value.isNaN()) return DASH
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val whole = formatCount(scaled / factor)
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    val sign = if (value < 0 && scaled != 0L) MINUS.toString() else ""
    return if (decimals == 0) "$sign$whole" else "$sign$whole,$fraction"
}

/** A share (0..1) as a percentage: "80 %", "75,5 %"; "—" when unknown. */
fun formatPercent(share: Double?): String {
    if (share == null || share.isNaN()) return DASH
    val percent = share * 100
    val rounded = floor(percent * 10 + 0.5) / 10
    val text = if (rounded == floor(rounded)) formatDecimal(rounded, 0) else formatDecimal(rounded, 1)
    return "$text$NBSP%"
}

/** The change from [previous] to [value]: "+12", "−3", "0"; "—" unless both are known. */
fun formatDelta(value: Double?, previous: Double?, share: Boolean = false): String {
    if (value == null || previous == null) return DASH
    val difference = value - previous
    if (share) {
        val points = floor(abs(difference) * 1000 + 0.5) / 10
        if (points == 0.0) return "0"
        val text = if (points == floor(points)) formatDecimal(points, 0) else formatDecimal(points, 1)
        return (if (difference > 0) "+" else MINUS.toString()) + text + "${NBSP}п.п."
    }
    val rounded = difference.roundToLong()
    return when {
        rounded > 0 -> "+" + formatCount(rounded)
        rounded < 0 -> "$MINUS" + formatCount(-rounded)
        else -> "0"
    }
}

/** A duration in seconds, briefly: "45 с", "10 мин", "2 ч 5 мин", "1 д 3 ч"; "—" when unknown. */
fun formatDuration(seconds: Double?): String {
    if (seconds == null || seconds.isNaN()) return DASH
    val total = seconds.roundToLong()
    val days = total / 86_400
    val hours = total % 86_400 / 3_600
    val minutes = total % 3_600 / 60
    return when {
        days > 0 -> if (hours > 0) "$days${NBSP}д $hours${NBSP}ч" else "$days${NBSP}д"
        hours > 0 -> if (minutes > 0) "$hours${NBSP}ч $minutes${NBSP}мин" else "$hours${NBSP}ч"
        minutes > 0 -> "$minutes${NBSP}мин"
        else -> "$total${NBSP}с"
    }
}

/** [part] of [whole] as a share, or null when either is unknown or [whole] is 0. */
fun share(part: Long?, whole: Long?): Double? = if (part == null || whole == null || whole == 0L) null else part.toDouble() / whole

/**
 * The shade of a retention cell: 0 (no one came back) to 4 (half or more), null for a value not available yet (shown as
 * "—", unshaded).
 */
fun retentionShade(rate: Double?): Int? = when {
    rate == null -> null
    rate <= 0.0 -> 0
    rate <= 0.10 -> 1
    rate <= 0.25 -> 2
    rate < 0.50 -> 3
    else -> 4
}
