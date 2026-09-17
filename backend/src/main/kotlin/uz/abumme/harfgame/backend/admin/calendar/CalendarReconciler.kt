package uz.abumme.harfgame.backend.admin.calendar

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.admin.words.PackPublisher
import uz.abumme.harfgame.backend.db.CalendarNoticesTable
import uz.abumme.harfgame.backend.db.CalendarStateTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeKind
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.lang.LanguageRegistry
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The calendar's one entry point for change, used inside the transaction of whatever caused it: a staff calendar or
 * pool action, a catalog mutation (any role), the startup migration or the periodic tick. The caller holds the pack
 * locks of the calendar ([PackPublisher.lock]), which serializes every writer of a calendar.
 *
 * [reconcile] loads the calendar, runs the pure [CalendarEngine], writes only the differences to `daily_words` and
 * records a notice plus a SYSTEM audit entry for every manual pick it had to replace; [publish] then rebuilds the
 * calendar's packs, which advance their versions only when something published changed.
 */
class CalendarReconciler(
    private val registry: LanguageRegistry,
    private val settings: DailySettings,
    private val audit: AuditLog,
    private val publisher: PackPublisher,
) {
    /** The day a calendar was first run, or null before its migration. */
    fun initializedOn(calendar: String): LocalDate? =
        CalendarStateTable.select(CalendarStateTable.initializedOn)
            .where { CalendarStateTable.calendar eq calendar }
            .singleOrNull()?.get(CalendarStateTable.initializedOn)

    /** Days before this never count as used: `DAILY_HISTORY_START`, else the calendar's first run. */
    fun historyStart(calendar: String): LocalDate? = settings.historyStart ?: initializedOn(calendar)

    /** [text] in the calendar's normalized form (the key "used" compares; Latin rules for `uz`). */
    fun key(calendar: String, text: String): String {
        val lang = DailyCalendars.wordLanguage(calendar)
        return registry.tokenizer(lang)?.normalize(text.trim())?.trim() ?: text.trim().lowercase()
    }

    /**
     * The calendar's answer pool as engine candidates: active daily-eligible words, or for `uz` every pair whose two
     * words are both active (id = pair id, text = Latin, cyrl = Cyrillic).
     */
    fun eligible(calendar: String): List<CalendarEngine.Candidate> =
        if (calendar == DailyCalendars.UZ) {
            pairs().filter { it.complete }.map { CalendarEngine.Candidate(it.id, it.latnText, it.cyrlText) }
        } else {
            WordsTable.select(WordsTable.id, WordsTable.text)
                .where {
                    (WordsTable.lang eq calendar) and (WordsTable.status eq WordStatus.ACTIVE.name) and (WordsTable.dailyEligible eq true)
                }
                .map { CalendarEngine.Candidate(it[WordsTable.id], it[WordsTable.text]) }
        }

    /** An Uzbek pair with its two catalog words. */
    data class LexemePair(
        val id: String,
        val latnWordId: String,
        val latnText: String,
        val latnActive: Boolean,
        val cyrlWordId: String,
        val cyrlText: String,
        val cyrlActive: Boolean,
        val createdByStaffId: String?,
        val createdAt: Instant,
    ) {
        val complete: Boolean get() = latnActive && cyrlActive
    }

    /** Every Uzbek pair, by Latin spelling. */
    fun pairs(): List<LexemePair> {
        val rows = LexemePairsTable.selectAll().toList()
        if (rows.isEmpty()) return emptyList()
        val wordIds = rows.flatMap { listOf(it[LexemePairsTable.latnWordId], it[LexemePairsTable.cyrlWordId]) }
        val words = wordIds.chunked(IN_LIST_CHUNK).flatMap { chunk ->
            WordsTable.select(WordsTable.id, WordsTable.text, WordsTable.status).where { WordsTable.id inList chunk }.toList()
        }.associateBy { it[WordsTable.id] }
        return rows.mapNotNull { row ->
            val latn = words[row[LexemePairsTable.latnWordId]] ?: return@mapNotNull null
            val cyrl = words[row[LexemePairsTable.cyrlWordId]] ?: return@mapNotNull null
            LexemePair(
                id = row[LexemePairsTable.id],
                latnWordId = latn[WordsTable.id],
                latnText = latn[WordsTable.text],
                latnActive = latn[WordsTable.status] == WordStatus.ACTIVE.name,
                cyrlWordId = cyrl[WordsTable.id],
                cyrlText = cyrl[WordsTable.text],
                cyrlActive = cyrl[WordsTable.status] == WordStatus.ACTIVE.name,
                createdByStaffId = row[LexemePairsTable.createdByStaffId],
                createdAt = row[LexemePairsTable.createdAt],
            )
        }.sortedBy { it.latnText }
    }

    /**
     * Plans [calendar] at [now] and writes what differs. Returns whether `daily_words` changed; an uninitialized
     * calendar is left alone (false).
     */
    fun reconcile(calendar: String, now: Instant): Boolean {
        val initializedOn = initializedOn(calendar) ?: return false
        val today = CalendarDays.today(calendar, now)
        val firstOpen = CalendarDays.firstOpen(today)
        val rows = DailyWordsTable.selectAll().where { DailyWordsTable.calendar eq calendar }.toList()
        val uz = calendar == DailyCalendars.UZ

        val frozen = HashMap<LocalDate, String>()
        val manual = HashMap<LocalDate, CalendarEngine.ManualPick>()
        val previousAuto = HashMap<LocalDate, CalendarEngine.AutoPick>()
        for (row in rows) {
            val day = row[DailyWordsTable.day]
            val text = key(calendar, row[DailyWordsTable.text])
            when {
                day < firstOpen -> frozen[day] = text
                row[DailyWordsTable.daySource] == DaySource.MANUAL.name -> {
                    val id = (if (uz) row[DailyWordsTable.lexemeId] else row[DailyWordsTable.wordId]) ?: continue
                    manual[day] = CalendarEngine.ManualPick(id, row[DailyWordsTable.text], wordActive = wordActive(calendar, id))
                }
                else -> previousAuto[day] = CalendarEngine.AutoPick(text, row[DailyWordsTable.isRepeat])
            }
        }

        val plan = CalendarEngine.plan(
            today = today,
            historyStart = settings.historyStart ?: initializedOn,
            frozen = frozen,
            manual = manual,
            previousAuto = previousAuto,
            eligible = eligible(calendar),
            random = settings.random,
        )

        var changed = false
        val existing = rows.associateBy { it[DailyWordsTable.day] }
        for ((day, assignment) in plan.days) {
            val row = existing[day]
            val candidate = assignment.candidate
            val wordId = if (uz) null else candidate.id
            val lexemeId = if (uz) candidate.id else null
            val source = if (assignment.source == CalendarEngine.Source.MANUAL) DaySource.MANUAL else DaySource.AUTO
            if (row == null) {
                DailyWordsTable.insert {
                    it[DailyWordsTable.calendar] = calendar
                    it[DailyWordsTable.day] = day
                    it[DailyWordsTable.wordId] = wordId
                    it[DailyWordsTable.lexemeId] = lexemeId
                    it[DailyWordsTable.text] = candidate.text
                    it[DailyWordsTable.textCyrl] = candidate.textCyrl
                    it[daySource] = source.name
                    it[isRepeat] = assignment.isRepeat
                    it[pickedByStaffId] = null
                    it[pickedAt] = now
                }
                changed = true
                continue
            }
            val same = row[DailyWordsTable.text] == candidate.text && row[DailyWordsTable.textCyrl] == candidate.textCyrl &&
                row[DailyWordsTable.wordId] == wordId && row[DailyWordsTable.lexemeId] == lexemeId &&
                row[DailyWordsTable.daySource] == source.name && row[DailyWordsTable.isRepeat] == assignment.isRepeat
            if (same) continue
            // A manual pick that only follows its word's new spelling keeps who picked it and when.
            val keepsPick = source == DaySource.MANUAL && row[DailyWordsTable.daySource] == DaySource.MANUAL.name
            DailyWordsTable.update({ (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }) {
                it[DailyWordsTable.wordId] = wordId
                it[DailyWordsTable.lexemeId] = lexemeId
                it[DailyWordsTable.text] = candidate.text
                it[DailyWordsTable.textCyrl] = candidate.textCyrl
                it[daySource] = source.name
                it[isRepeat] = assignment.isRepeat
                if (!keepsPick) {
                    it[pickedByStaffId] = null
                    it[pickedAt] = now
                }
            }
            changed = true
        }
        // Unlocked days the plan no longer fills (e.g. a far manual pick that lost its word).
        for (row in rows) {
            val day = row[DailyWordsTable.day]
            if (day < firstOpen || day in plan.days) continue
            DailyWordsTable.deleteWhere { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }
            changed = true
        }

        for (replaced in plan.replacedManual) {
            CalendarNoticesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[CalendarNoticesTable.calendar] = calendar
                it[CalendarNoticesTable.day] = replaced.day
                it[kind] = NoticeKind.MANUAL_PICK_REPLACED.name
                it[wordText] = replaced.text
                it[reason] = replaced.reason.name
                it[createdAt] = now
                it[dismissedAt] = null
                it[dismissedByStaffId] = null
            }
            audit.record(
                AuditActor.System, AuditActions.DAILY_MANUAL_PICK_REPLACED, AuditTargets.DAILY_DAY, dayTarget(calendar, replaced.day),
                lang = calendar,
                details = auditDetails {
                    fact("day", jsonOf(replaced.day.toString()))
                    fact("word", jsonOf(replaced.text))
                    fact("reason", jsonOf(replaced.reason.name))
                },
            )
        }
        return changed
    }

    /** Rebuilds every pack of [calendar] (their rows must already be locked); returns each pack's version afterwards. */
    fun publish(calendar: String, now: Instant): Map<String, String> =
        DailyCalendars.packLanguages(calendar).mapNotNull { lang ->
            publisher.lock(lang)?.let { lang to publisher.publish(it, now) }
        }.toMap()

    /** Whether a manual pick's word is still active; for `uz`, a pair that no longer exists counts as ineligible, not removed. */
    private fun wordActive(calendar: String, id: String): Boolean {
        if (calendar == DailyCalendars.UZ) {
            val pair = LexemePairsTable.selectAll().where { LexemePairsTable.id eq id }.singleOrNull() ?: return true
            val ids = listOf(pair[LexemePairsTable.latnWordId], pair[LexemePairsTable.cyrlWordId])
            return WordsTable.select(WordsTable.status).where { WordsTable.id inList ids }
                .count { it[WordsTable.status] == WordStatus.ACTIVE.name } == 2
        }
        return WordsTable.select(WordsTable.status).where { WordsTable.id eq id }.singleOrNull()
            ?.get(WordsTable.status) == WordStatus.ACTIVE.name
    }

    companion object {
        private const val IN_LIST_CHUNK = 500

        /** The audit target id of a calendar day. */
        fun dayTarget(calendar: String, day: LocalDate): String = "$calendar:$day"

        /** The pick reference of a stored day: its word id, or its pair id for `uz`. */
        fun refId(calendar: String, row: ResultRow): String? =
            if (calendar == DailyCalendars.UZ) row[DailyWordsTable.lexemeId] else row[DailyWordsTable.wordId]
    }
}
