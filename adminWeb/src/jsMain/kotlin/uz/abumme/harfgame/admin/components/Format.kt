package uz.abumme.harfgame.admin.components

import kotlin.js.Date
import kotlin.js.dateLocaleOptions

private const val LOCALE = "ru-RU"

/** "16 сент. 2026 г., 14:05" in the viewer's time zone. */
fun formatDateTime(epochMillis: Long): String = Date(epochMillis.toDouble()).toLocaleString(
    LOCALE,
    dateLocaleOptions {
        day = "numeric"
        month = "short"
        year = "numeric"
        hour = "2-digit"
        minute = "2-digit"
    },
)

/** Local midnight at the start of an `<input type="date">` value (`yyyy-mm-dd`), or null if it is not a date. */
fun startOfLocalDay(isoDate: String): Long? {
    val parts = isoDate.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return Date(parts[0], parts[1] - 1, parts[2]).getTime().toLong()
}

/** Local midnight at the end of that day: the exclusive upper bound of a range that includes it. */
fun endOfLocalDay(isoDate: String): Long? {
    val parts = isoDate.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return Date(parts[0], parts[1] - 1, parts[2] + 1).getTime().toLong()
}

/** "16 сент. 2026 г." in the viewer's time zone. */
fun formatDate(epochMillis: Long): String = Date(epochMillis.toDouble()).toLocaleDateString(
    LOCALE,
    dateLocaleOptions {
        day = "numeric"
        month = "short"
        year = "numeric"
    },
)
