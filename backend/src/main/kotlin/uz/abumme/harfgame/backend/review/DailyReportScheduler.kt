package uz.abumme.harfgame.backend.review

import uz.abumme.harfgame.backend.service.SuggestionServerService
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.backend.telegram.TelegramBot
import java.time.Instant

/**
 * Sends each language's daily report once, shortly after 00:00 Asia/Tashkent. Called every minute, one
 * idempotent check covers the regular send, a retry after Telegram failed, and catching up the previous day
 * after downtime. A day is recorded only once its report was delivered (or found quiet): a crash between the
 * two can duplicate a report, never lose one.
 */
class DailyReportScheduler(
    private val suggestions: SuggestionServerService,
    private val wordPacks: WordPackServerService,
    private val telegram: TelegramBot,
) {
    suspend fun sendDue(now: Instant) {
        val day = reportDay(now)
        val (from, to) = reportWindow(day)
        for (lang in wordPacks.languages()) {
            if (suggestions.reportRecorded(lang, day)) continue
            val text = renderReport(lang, day, suggestions.dailySummary(lang, from, to))
            if (text == null || telegram.sendReport(lang, text)) suggestions.recordReport(lang, day)
        }
    }
}
