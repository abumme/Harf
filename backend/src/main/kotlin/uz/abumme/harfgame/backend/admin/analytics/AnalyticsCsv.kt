package uz.abumme.harfgame.backend.admin.analytics

import uz.abumme.harfgame.data.admin.analytics.AccountsDto
import uz.abumme.harfgame.data.admin.analytics.ActivityDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.ContentDto
import uz.abumme.harfgame.data.admin.analytics.OutcomesDto
import uz.abumme.harfgame.data.admin.analytics.RetentionDto
import uz.abumme.harfgame.data.admin.analytics.StaffActivityDto
import uz.abumme.harfgame.data.admin.analytics.StreaksDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionsAnalyticsDto
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The CSV exports, written from the same DTOs the dashboard reads, so values and provisional/partial markers match.
 * UTF-8 with a byte-order mark (Excel then opens Cyrillic correctly), a header row, CRLF line ends, RFC 4180 quoting;
 * an unavailable value is an empty field, never 0.
 */
object AnalyticsCsv {
    data class Sheet(val header: List<String>, val rows: List<List<Any?>>)

    /** `harf-<table>-<from>-<to>.csv`. */
    fun fileName(table: AnalyticsTable, query: AnalyticsQuery): String = "harf-${table.path}-${query.from}-${query.to}.csv"

    fun encode(sheet: Sheet): ByteArray {
        val text = buildString {
            append('﻿')
            appendRow(sheet.header)
            sheet.rows.forEach { appendRow(it) }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun StringBuilder.appendRow(values: List<Any?>) {
        values.joinTo(this, ",") { field(it) }
        append("\r\n")
    }

    private fun field(value: Any?): String {
        val text = when (value) {
            null -> ""
            is Double -> BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            else -> value.toString()
        }
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    fun accounts(dto: AccountsDto) = Sheet(
        listOf("date", "new_accounts", "links_google", "links_apple", "deletions", "total", "linked", "google_linked", "apple_linked", "anonymous", "provisional"),
        dto.days.map { listOf(it.date, it.newAccounts, it.linksGoogle, it.linksApple, it.deletions, it.total, it.linked, it.googleLinked, it.appleLinked, it.anonymous, it.provisional) },
    )

    /** Per-language rows, then the cross-language rows (`all`), then today's partial rows. */
    fun activity(dto: ActivityDto) = Sheet(
        listOf("day", "language", "players", "games", "dau", "wau", "mau", "provisional", "partial"),
        dto.languages.map { listOf(it.day, it.lang, it.players, it.games, null, null, null, it.provisional, false) } +
            dto.global.map { listOf(it.day, ALL, null, null, it.dau, it.wau, it.mau, it.provisional, false) } +
            dto.today.map { listOf(it.day, it.lang, it.players, it.games, null, null, null, true, it.partial) },
    )

    fun retention(dto: RetentionDto) = Sheet(
        listOf("cohort_day", "size", "d1", "d7", "d30", "d1_rate", "d7_rate", "d30_rate", "provisional"),
        dto.cohorts.map {
            fun share(value: Long?) = value?.let { v -> AnalyticsService.rate(v, it.size) }
            listOf(it.day, it.size, it.d1, it.d7, it.d30, share(it.d1), share(it.d7), share(it.d30), it.provisional)
        },
    )

    fun streaks(dto: StreaksDto) = Sheet(
        listOf("day", "language", "streak_1", "streak_2_6", "streak_7_29", "streak_30_plus", "provisional"),
        dto.days.map { listOf(it.day, it.lang, it.streak1, it.streak2to6, it.streak7to29, it.streak30plus, it.provisional) },
    )

    /** Day rows, then the range totals (`total`, per language and `all`). */
    fun outcomes(dto: OutcomesDto) = Sheet(
        listOf("day", "language", "games", "wins", "losses", "won_1", "won_2", "won_3", "won_4", "won_5", "won_6", "win_rate", "avg_attempts", "provisional"),
        dto.days.map { listOf(it.day, it.lang, it.games, it.wins, it.losses) + it.distribution + listOf(it.winRate, it.avgAttempts, it.provisional) } +
            dto.totals.map { listOf(TOTAL, it.lang ?: ALL, it.games, it.wins, it.losses) + it.distribution + listOf(it.winRate, it.avgAttempts, it.provisional) },
    )

    fun words(dto: WordDifficultyDto) = Sheet(
        listOf("calendar", "day", "word", "word_cyrl", "marker", "repeat", "players", "wins", "win_rate", "avg_attempts", "provisional"),
        dto.rows.map { listOf(it.calendar, it.day, it.word, it.wordCyrl, it.marker?.name?.lowercase(), it.repeat, it.players, it.wins, it.winRate, it.avgAttempts, it.provisional) },
    )

    fun suggestions(dto: SuggestionsAnalyticsDto) = Sheet(
        listOf("date", "language", "submitted", "auto_accepted", "editor_accepted", "rejected", "backlog_end", "median_auto_seconds", "median_editor_seconds", "provisional"),
        dto.days.map { listOf(it.date, it.lang, it.submitted, it.autoAccepted, it.editorAccepted, it.rejected, it.backlogEnd, it.medianAutoSeconds, it.medianEditorSeconds, it.provisional) },
    )

    /** Per-language catalog rows (scope = the language), then answer pool rows (scope `calendar:<id>`). */
    fun content(dto: ContentDto) = Sheet(
        listOf("date", "scope", "active_words", "added_bundled", "added_suggestion", "added_auto", "added_staff", "removed", "restored", "pool_size", "unused_left", "provisional"),
        dto.days.map { listOf(it.date, it.lang, it.activeWords, it.addedBundled, it.addedSuggestion, it.addedAuto, it.addedStaff, it.removed, it.restored, null, null, it.provisional) } +
            dto.pool.map { listOf(it.date, "calendar:${it.calendar}", null, null, null, null, null, null, null, it.poolSize, it.unusedLeft, it.provisional) },
    )

    fun staff(dto: StaffActivityDto) = Sheet(
        listOf("date", "staff_id", "username", "display_name", "language", "added", "edited", "removed", "decided", "provisional"),
        dto.rows.map {
            listOf(
                it.date, it.staff?.id, it.staff?.username ?: RollupComputations.TELEGRAM_UNLINKED, it.staff?.displayName,
                it.lang, it.added, it.edited, it.removed, it.decided, it.provisional,
            )
        },
    )

    private const val ALL = "all"
    private const val TOTAL = "total"
}
