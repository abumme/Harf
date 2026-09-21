package uz.abumme.harfgame.backend.admin.calendar

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.words.LockedPack
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.CalendarStateTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * First start of the daily-word calendars, once per calendar without a `calendar_state` row, one transaction per
 * calendar under its pack locks:
 *
 * 1. `uz`: a pair for each `uz_lexemes.tsv` line whose two words are active catalog words, both made daily-eligible; an
 *    Uzbek word outside any pair stops being eligible (for Uzbek, the pair is the eligibility).
 * 2. The stored schedule of every day from [WordPackSchedule.ANCHOR_EPOCH_DAY] through tomorrow (in the calendar's
 *    timezone) becomes LEGACY days, resolved exactly as the app resolves them, so today and tomorrow keep the words every
 *    device already has.
 * 3. `calendar_state.initialized_on` = today.
 * 4. Reconcile from the day after tomorrow and publish.
 *
 * A calendar whose packs are missing is skipped (and migrated on a later start, once they exist).
 */
class CalendarMigration(private val catalog: WordCatalogService) {

    data class Report(val calendar: String, val legacyDays: Int, val eligible: Int, val repeats: Int, val pairsCreated: Int)

    suspend fun run(now: Instant = catalog.clock.instant()): List<Report> {
        val reports = ArrayList<Report>()
        for (calendar in DailyCalendars.ALL.sorted()) {
            val report = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) { migrate(calendar, now) } ?: continue
            println(
                "Daily calendar ${report.calendar}: imported ${report.legacyDays} legacy days, ${report.eligible} eligible words" +
                    (if (report.pairsCreated > 0) " (${report.pairsCreated} Uzbek pairs)" else "") +
                    ", ${report.repeats} repeats scheduled"
            )
            reports += report
        }
        return reports
    }

    private fun migrate(calendar: String, now: Instant): Report? {
        val langs = DailyCalendars.packLanguages(calendar)
        val packs = langs.associateWith { catalog.publisher.lock(it) ?: return null }
        val calendars = catalog.calendars
        if (calendars.initializedOn(calendar) != null) return null

        val pairsCreated = if (calendar == DailyCalendars.UZ) createLexemePairs() else 0

        val today = CalendarDays.today(calendar, now)
        val tomorrow = today.plusDays(1)
        val anchor = LocalDate.ofEpochDay(WordPackSchedule.ANCHOR_EPOCH_DAY)
        val schedules = packs.mapValues { (_, pack) -> Json.decodeFromString<List<String>>(pack.scheduleJson) }
        if (schedules.values.any { it.isEmpty() }) {
            println("Daily calendar $calendar: a pack has no schedule; not migrated")
            return null
        }

        val days = generateSequence(anchor) { it.plusDays(1) }.takeWhile { !it.isAfter(tomorrow) }.toList()
        val legacy = if (calendar == DailyCalendars.UZ) {
            val pairIds = calendars.pairs().associate { (it.latnText to it.cyrlText) to it.id }
            days.map { day ->
                val latn = storedWord(packs.getValue("uz-latn"), schedules.getValue("uz-latn"), day)
                val cyrl = storedWord(packs.getValue("uz-cyrl"), schedules.getValue("uz-cyrl"), day)
                val key = calendars.key(calendar, latn) to normalize("uz-cyrl", cyrl)
                LegacyDay(day, latn, cyrl, wordId = null, lexemeId = pairIds[key])
            }
        } else {
            val texts = days.associateWith { storedWord(packs.getValue(calendar), schedules.getValue(calendar), it) }
            val keys = texts.values.map { calendars.key(calendar, it) }.distinct()
            val wordIds = keys.chunked(IN_LIST_CHUNK).flatMap { chunk ->
                WordsTable.select(WordsTable.id, WordsTable.text).where { (WordsTable.lang eq calendar) and (WordsTable.text inList chunk) }.toList()
            }.associate { it[WordsTable.text] to it[WordsTable.id] }
            days.map { day ->
                val text = texts.getValue(day)
                LegacyDay(day, text, null, wordId = wordIds[calendars.key(calendar, text)], lexemeId = null)
            }
        }

        DailyWordsTable.batchInsert(legacy, shouldReturnGeneratedValues = false) { entry ->
            this[DailyWordsTable.calendar] = calendar
            this[DailyWordsTable.day] = entry.day
            this[DailyWordsTable.wordId] = entry.wordId
            this[DailyWordsTable.lexemeId] = entry.lexemeId
            this[DailyWordsTable.text] = entry.text
            this[DailyWordsTable.textCyrl] = entry.textCyrl
            this[DailyWordsTable.daySource] = DaySource.LEGACY.name
            this[DailyWordsTable.isRepeat] = false
            this[DailyWordsTable.pickedByStaffId] = null
            this[DailyWordsTable.pickedAt] = now
        }
        CalendarStateTable.insert {
            it[CalendarStateTable.calendar] = calendar
            it[initializedOn] = today
        }

        calendars.reconcile(calendar, now)
        calendars.publish(calendar, now)

        val repeats = DailyWordsTable.select(DailyWordsTable.day)
            .where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day greaterEq CalendarDays.firstOpen(today)) and (DailyWordsTable.isRepeat eq true) }
            .count().toInt()
        return Report(calendar, legacy.size, calendars.eligible(calendar).size, repeats, pairsCreated)
    }

    private data class LegacyDay(val day: LocalDate, val text: String, val textCyrl: String?, val wordId: String?, val lexemeId: String?)

    /** The stored schedule's word for [day], exactly as the app resolves it (wrapping past the end). */
    private fun storedWord(pack: LockedPack, schedule: List<String>, day: LocalDate): String =
        WordPackSchedule.answerFor(schedule, pack.anchorEpochDay, day.toEpochDay())

    private fun normalize(lang: String, text: String): String =
        catalog.registry.tokenizer(lang)?.normalize(text.trim())?.trim() ?: text.trim().lowercase()

    /** Pairs from the deployed lexemes; returns how many were created. */
    private fun createLexemePairs(): Int {
        val paired = LexemePairsTable.selectAll().flatMap { listOf(it[LexemePairsTable.latnWordId], it[LexemePairsTable.cyrlWordId]) }.toMutableSet()
        var created = 0
        for ((latnRaw, cyrlRaw) in catalog.wordPacks.bundledLexemes()) {
            val latnId = activeWordId("uz-latn", normalize("uz-latn", latnRaw)) ?: continue
            val cyrlId = activeWordId("uz-cyrl", normalize("uz-cyrl", cyrlRaw)) ?: continue
            if (latnId in paired || cyrlId in paired) continue
            LexemePairsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[latnWordId] = latnId
                it[cyrlWordId] = cyrlId
                it[createdByStaffId] = null
                it[createdAt] = catalog.clock.instant()
            }
            paired += latnId
            paired += cyrlId
            created++
        }
        val uzLangs = listOf("uz-latn", "uz-cyrl")
        if (paired.isNotEmpty()) {
            WordsTable.update({ (WordsTable.lang inList uzLangs) and (WordsTable.id inList paired) }) { it[dailyEligible] = true }
            WordsTable.update({ (WordsTable.lang inList uzLangs) and (WordsTable.dailyEligible eq true) and (WordsTable.id notInList paired) }) {
                it[dailyEligible] = false
            }
        } else {
            WordsTable.update({ (WordsTable.lang inList uzLangs) and (WordsTable.dailyEligible eq true) }) { it[dailyEligible] = false }
        }
        return created
    }

    private fun activeWordId(lang: String, text: String): String? =
        WordsTable.select(WordsTable.id)
            .where { (WordsTable.lang eq lang) and (WordsTable.text eq text) and (WordsTable.status eq WordStatus.ACTIVE.name) }
            .singleOrNull()?.get(WordsTable.id)

    private companion object {
        const val IN_LIST_CHUNK = 500
    }
}
