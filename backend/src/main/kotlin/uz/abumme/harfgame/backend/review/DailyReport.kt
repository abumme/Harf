package uz.abumme.harfgame.backend.review

import uz.abumme.harfgame.backend.service.DailySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The editors' clock: every language's daily report covers an Asia/Tashkent calendar day. */
internal val REPORT_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/** Characters of word list per group; three groups plus the other lines stay under Telegram's 4096. */
private const val GROUP_BUDGET_CHARS = 1_200

/** The Asia/Tashkent day a report due at [now] covers: the most recent day that has fully ended. */
internal fun reportDay(now: Instant): LocalDate = LocalDate.ofInstant(now, REPORT_ZONE).minusDays(1)

/** [day] in Asia/Tashkent as the half-open window [start, end). */
internal fun reportWindow(day: LocalDate): Pair<Instant, Instant> =
    day.atStartOfDay(REPORT_ZONE).toInstant() to day.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant()

/** Report text for [lang]'s [day], or null for a quiet day (nothing decided, nothing pending). */
internal fun renderReport(lang: String, day: LocalDate, summary: DailySummary): String? {
    val quiet = summary.autoAccepted.isEmpty() && summary.editorAccepted.isEmpty() &&
        summary.rejected.isEmpty() && summary.pending == 0L
    if (quiet) return null
    return "📊 Отчёт за ${day.format(DAY_FORMAT)} · $lang\n\n" +
        "🤖 Автопринято ${group(summary.autoAccepted)}\n" +
        "✅ Принято редакторами ${group(summary.editorAccepted)}\n" +
        "❌ Отклонено ${group(summary.rejected)}\n" +
        "⏳ Ждут решения: ${summary.pending}"
}

/** `(count): w1, w2 …и ещё N` — words in decision order until the length budget is spent (always at least one). */
private fun group(words: List<String>): String {
    if (words.isEmpty()) return "(0): —"
    val shown = mutableListOf<String>()
    var length = 0
    for (word in words) {
        val added = word.length + if (shown.isEmpty()) 0 else ", ".length
        if (shown.isNotEmpty() && length + added > GROUP_BUDGET_CHARS) break
        shown += word
        length += added
    }
    val omitted = words.size - shown.size
    return "(${words.size}): ${shown.joinToString(", ")}" + if (omitted > 0) " …и ещё $omitted" else ""
}
