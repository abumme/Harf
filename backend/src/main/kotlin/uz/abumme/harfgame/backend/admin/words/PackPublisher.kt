package uz.abumme.harfgame.backend.admin.words

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.calendar.CalendarDays
import uz.abumme.harfgame.backend.db.CalendarStateTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackIntegrity
import uz.abumme.harfgame.data.wordpack.WordPackSchedule
import uz.abumme.harfgame.lang.LanguageRegistry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** A language's `word_packs` row, locked by [PackPublisher.lock] for the rest of the transaction. */
class LockedPack internal constructor(
    val lang: String,
    val version: String,
    val effectiveFrom: Long,
    val anchorEpochDay: Long,
    /** The stored JSON arrays, as published. */
    internal val answersJson: String,
    internal val guessesJson: String,
    internal val scheduleJson: String,
    val firstPublicEpochDay: Long? = null,
)

/**
 * A rebuilt pack would be refused by the app's integrity check. Thrown inside the catalog transaction, so the change
 * that caused it rolls back and the previous pack stays published. [problems] are for logs only: they can name daily
 * words and must never reach a response.
 */
class PackIntegrityException(val lang: String, val problems: List<String>) :
    RuntimeException("Refusing to publish an invalid $lang pack: ${problems.take(5)}")

/**
 * Publishes a language's pack from the word catalog and its daily-word calendar, inside the transaction of the change
 * that requires it.
 *
 * Every writer first [lock]s the pack rows (`SELECT … FOR UPDATE` at READ COMMITTED) of the language's whole calendar
 * (both Uzbek scripts), always in the fixed order `en`, `kk`, `ru`, `uz-cyrl`, `uz-latn`: a second writer waits, then
 * sees the first one's rows instead of failing with a serialization error, and a calendar writer never deadlocks with a
 * catalog writer. [publish] then rebuilds the pack:
 *
 * - guesses: every ACTIVE word;
 * - answers: the answer pool, ACTIVE and daily-eligible words (for Uzbek, each complete pair's word in that script);
 * - once the language's calendar is initialized, the schedule from `daily_words`, contiguous from
 *   [WordPackSchedule.ANCHOR_EPOCH_DAY] (Cyrillic texts for `uz-cyrl`), and the effective date the day after tomorrow;
 *   before that, the stored schedule, anchor and effective date are kept.
 *
 * It checks the result with the app's own [WordPackIntegrity] and advances the version by one, but only when answers,
 * guesses or schedule differ from what is published: a change that publishes nothing new keeps the version.
 */
class PackPublisher(
    private val registry: LanguageRegistry = LanguageRegistry(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json

    /**
     * Locks every pack row of [lang]'s calendar (just [lang] for a language without one) until the transaction ends and
     * returns [lang]'s; null when the language has no pack.
     */
    fun lock(lang: String): LockedPack? {
        val group = DailyCalendars.calendarOf(lang)?.let(DailyCalendars::packLanguages)?.takeIf { lang in it } ?: listOf(lang)
        return group.sorted().map { member -> member to lockRow(member) }.toMap()[lang]
    }

    private fun lockRow(lang: String): LockedPack? =
        WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.let { row ->
                LockedPack(
                    lang = lang,
                    version = row[WordPacksTable.version],
                    effectiveFrom = row[WordPacksTable.effectiveFrom],
                    anchorEpochDay = row[WordPacksTable.anchorEpochDay],
                    answersJson = row[WordPacksTable.answers],
                    guessesJson = row[WordPacksTable.guesses],
                    scheduleJson = row[WordPacksTable.schedule],
                    firstPublicEpochDay = row[WordPacksTable.firstPublicEpochDay],
                )
            }

    /**
     * Rebuilds [pack] from the catalog and calendar at [now] and stores it as the next version when its content
     * changed. Returns the version published afterwards (the current one when nothing changed). Throws
     * [PackIntegrityException] (rolling the transaction back) when the app would refuse the result.
     */
    fun publish(pack: LockedPack, now: Instant = clock.instant()): String {
        val lang = pack.lang
        val calendar = DailyCalendars.calendarOf(lang)?.takeIf(::calendarInitialized)
        val guesses = activeTexts(lang, dailyEligibleOnly = false)
        val answers = if (calendar == DailyCalendars.UZ) pairAnswers(lang) else activeTexts(lang, dailyEligibleOnly = true)
        val storedSchedule: List<String> = json.decodeFromString(pack.scheduleJson)
        val schedule = if (calendar != null) calendarSchedule(calendar, lang) else storedSchedule
        val anchor = if (calendar != null) WordPackSchedule.ANCHOR_EPOCH_DAY else pack.anchorEpochDay

        val unchanged = anchor == pack.anchorEpochDay && schedule == storedSchedule &&
            answers == json.decodeFromString<List<String>>(pack.answersJson) &&
            guesses == json.decodeFromString<List<String>>(pack.guessesJson)
        if (unchanged) return pack.version

        val firstPublicEpochDay = if (calendar != null) {
            CalendarStateTable.select(CalendarStateTable.initializedOn)
                .where { CalendarStateTable.calendar eq calendar }
                .singleOrNull()?.get(CalendarStateTable.initializedOn)?.toEpochDay() ?: anchor
        } else {
            pack.firstPublicEpochDay ?: anchor
        }

        val effectiveFrom = if (calendar != null) CalendarDays.firstOpen(CalendarDays.today(calendar, now)).toEpochDay() else pack.effectiveFrom
        val version = nextVersion(pack.version)
        val dto = WordPackDto(lang, version, effectiveFrom, anchor, answers, guesses, schedule, firstPublicEpochDay)

        val config = registry.config(lang) ?: throw PackIntegrityException(lang, listOf("no language config"))
        val check = WordPackIntegrity.check(dto, config)
        if (check is WordPackIntegrity.Invalid) throw PackIntegrityException(lang, check.problems)

        WordPacksTable.update({ WordPacksTable.lang eq lang }) {
            it[WordPacksTable.answers] = json.encodeToString(answers)
            it[WordPacksTable.guesses] = json.encodeToString(guesses)
            it[WordPacksTable.schedule] = json.encodeToString(schedule)
            it[WordPacksTable.anchorEpochDay] = anchor
            it[WordPacksTable.effectiveFrom] = effectiveFrom
            it[WordPacksTable.version] = version
            it[WordPacksTable.firstPublicEpochDay] = firstPublicEpochDay
            it[updatedAt] = clock.instant()
        }
        return version
    }

    private fun calendarInitialized(calendar: String): Boolean =
        CalendarStateTable.select(CalendarStateTable.calendar).where { CalendarStateTable.calendar eq calendar }.any()

    private fun activeTexts(lang: String, dailyEligibleOnly: Boolean): List<String> =
        WordsTable.select(WordsTable.text)
            .where {
                val active = (WordsTable.lang eq lang) and (WordsTable.status eq WordStatus.ACTIVE.name)
                if (dailyEligibleOnly) active and (WordsTable.dailyEligible eq true) else active
            }
            .orderBy(WordsTable.text, SortOrder.ASC)
            .map { it[WordsTable.text] }

    /** The [lang] word of every Uzbek pair whose two words are both active, alphabetically. */
    private fun pairAnswers(lang: String): List<String> {
        val pairs = LexemePairsTable.selectAll().toList()
        if (pairs.isEmpty()) return emptyList()
        val ids = pairs.flatMap { listOf(it[LexemePairsTable.latnWordId], it[LexemePairsTable.cyrlWordId]) }
        val words = ids.chunked(IN_LIST_CHUNK).flatMap { chunk ->
            WordsTable.select(WordsTable.id, WordsTable.text, WordsTable.lang, WordsTable.status).where { WordsTable.id inList chunk }.toList()
        }.associateBy { it[WordsTable.id] }
        return pairs.mapNotNull { pair ->
            val both = listOfNotNull(words[pair[LexemePairsTable.latnWordId]], words[pair[LexemePairsTable.cyrlWordId]])
            if (both.size != 2 || both.any { it[WordsTable.status] != WordStatus.ACTIVE.name }) return@mapNotNull null
            both.firstOrNull { it[WordsTable.lang] == lang }?.get(WordsTable.text)
        }.sorted()
    }

    /** The calendar's words from the anchor day on, as long as the days are contiguous. */
    private fun calendarSchedule(calendar: String, lang: String): List<String> {
        val anchor = LocalDate.ofEpochDay(WordPackSchedule.ANCHOR_EPOCH_DAY)
        val cyrillic = lang == "uz-cyrl"
        val schedule = ArrayList<String>()
        var expected = anchor
        DailyWordsTable.select(DailyWordsTable.day, DailyWordsTable.text, DailyWordsTable.textCyrl)
            .where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day greaterEq anchor) }
            .orderBy(DailyWordsTable.day, SortOrder.ASC)
            .forEach { row ->
                if (row[DailyWordsTable.day] != expected) return schedule
                schedule += if (cyrillic) row[DailyWordsTable.textCyrl] ?: row[DailyWordsTable.text] else row[DailyWordsTable.text]
                expected = expected.plusDays(1)
            }
        return schedule
    }

    companion object {
        private const val IN_LIST_CHUNK = 500

        /** Versions are integer strings; a non-numeric legacy version restarts at 2. */
        fun nextVersion(current: String): String = (current.toIntOrNull()?.plus(1) ?: 2).toString()
    }
}
