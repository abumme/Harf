package uz.abumme.harfgame.backend.admin.calendar

import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import java.time.LocalDate
import java.util.SortedMap
import java.util.TreeMap
import kotlin.random.Random

/**
 * The daily-word calendar's rules as one pure function: no clock, database or randomness of its own, so every rule is
 * a unit test. [CalendarService] loads the inputs, runs [plan] and writes the differences.
 *
 * Words are compared by normalized text (the Latin text for `uz`): "used" means the text was the word of a counted day,
 * so an edit that turns one word into another word's former spelling still counts as used.
 */
object CalendarEngine {

    /** An eligible word, or an Uzbek pair: [id] identifies it, [text] is its normalized (Latin) spelling. */
    data class Candidate(val id: String, val text: String, val textCyrl: String? = null)

    /** A stored manual pick: what it was made from, the text it last published, and whether that word is still active. */
    data class ManualPick(val candidateId: String, val text: String, val wordActive: Boolean)

    /** A stored automatic pick of an unlocked day ([text] normalized). */
    data class AutoPick(val text: String, val isRepeat: Boolean)

    enum class Source { MANUAL, AUTO }

    /** A day's word. [lastUse] is the previous use of a repeat's word. */
    data class Assignment(
        val candidate: Candidate,
        val source: Source,
        val isRepeat: Boolean = false,
        val lastUse: LocalDate? = null,
    )

    /** A manual pick whose word was removed or left the pool; its day was filled automatically. */
    data class ReplacedPick(val day: LocalDate, val text: String, val reason: NoticeReason)

    /**
     * [days]: every day from the day after tomorrow through the horizon (plus days missing before it, and kept manual
     * picks beyond it), in order. Locked days that exist are never in it.
     */
    data class Plan(val days: SortedMap<LocalDate, Assignment>, val replacedManual: List<ReplacedPick>)

    /**
     * Plans a calendar's unlocked days.
     *
     * 1. A manual pick whose candidate (by id) is no longer eligible is dropped with a notice: `REMOVED` when its word
     *    is not active, otherwise `INELIGIBLE`. A kept manual pick publishes its candidate's current text.
     * 2. `used` = texts of [frozen] days on or after [historyStart]; `taken` = used, manual texts at any distance, and
     *    texts already assigned by this plan.
     * 3. Each day from the day after tomorrow (or the first day missing after the last frozen day) to [horizonEnd]:
     *    a manual pick is kept; a previous automatic pick stays while valid (eligible and not taken, or a repeat while no
     *    never-used word is left and the word is not manually picked and differs from the day before); otherwise a
     *    random never-used word, else the least recently used word (never-used ranks oldest, ties random) as a repeat.
     *    A re-pick avoids words later days still hold, and a repeat avoids the next day's word, so a change re-picks
     *    only the days it affects. Consecutive days get different words while the pool has more than one.
     */
    fun plan(
        today: LocalDate,
        historyStart: LocalDate,
        frozen: Map<LocalDate, String>,
        manual: Map<LocalDate, ManualPick>,
        previousAuto: Map<LocalDate, AutoPick>,
        eligible: Collection<Candidate>,
        random: Random,
        horizonEnd: LocalDate = today.plusDays(DailyCalendars.HORIZON_DAYS.toLong()),
    ): Plan {
        val firstOpen = today.plusDays(DailyCalendars.FIRST_OPEN_OFFSET.toLong())
        val pool = eligible.distinctBy { it.text }.sortedBy { it.text }
        val byId = pool.associateBy { it.id }
        val byText = pool.associateBy { it.text }

        // 1. Manual picks.
        val keptManual = TreeMap<LocalDate, Candidate>()
        val replaced = ArrayList<ReplacedPick>()
        for ((day, pick) in manual.toSortedMap()) {
            if (day < firstOpen) continue
            val candidate = byId[pick.candidateId]
            if (candidate == null) {
                replaced += ReplacedPick(day, pick.text, if (pick.wordActive) NoticeReason.INELIGIBLE else NoticeReason.REMOVED)
            } else {
                keptManual[day] = candidate
            }
        }

        // 2. Usage.
        val used = HashSet<String>()
        val lastUse = HashMap<String, LocalDate>()
        for ((day, text) in frozen) {
            if (day < historyStart) continue
            used += text
            if (lastUse[text]?.let { day > it } != false) lastUse[text] = day
        }
        val reserved = keptManual.values.mapTo(HashSet()) { it.text }
        val assigned = HashSet<String>()
        fun taken(text: String) = text in used || text in reserved || text in assigned

        // Later days whose previous automatic word is still a candidate hold it against earlier re-picks.
        val heldUntil = HashMap<String, LocalDate>()
        for ((day, pick) in previousAuto) {
            if (day < firstOpen || day > horizonEnd || day in keptManual) continue
            if (pick.text !in byText || pick.text in reserved) continue
            if (heldUntil[pick.text]?.let { day > it } != false) heldUntil[pick.text] = day
        }

        val lastFrozen = frozen.keys.maxOrNull()
        val start = if (lastFrozen == null || lastFrozen >= firstOpen.minusDays(1)) firstOpen else minOf(firstOpen, lastFrozen.plusDays(1))
        val days = TreeMap<LocalDate, Assignment>()
        var previousText: String? = frozen[start.minusDays(1)]

        var day = start
        while (!day.isAfter(horizonEnd)) {
            val open = day >= firstOpen
            val manualPick = if (open) keptManual[day] else null
            val assignment = when {
                manualPick != null -> Assignment(manualPick, Source.MANUAL)
                pool.isEmpty() -> null
                else -> keepPrevious(if (open) previousAuto[day] else null, byText, pool, previousText, ::taken, reserved, lastUse)
                    ?: pickNew(day, pool, previousText, previousAuto[day.plusDays(1)]?.text, ::taken, reserved, heldUntil, lastUse, random)
            }
            if (assignment == null) {
                previousText = null
            } else {
                val text = assignment.candidate.text
                days[day] = assignment
                if (assignment.source == Source.AUTO) assigned += text
                lastUse[text] = day
                previousText = text
            }
            day = day.plusDays(1)
        }
        // Manual picks past the horizon keep their day (with their current text) until the horizon reaches them.
        keptManual.tailMap(horizonEnd, false).forEach { (manualDay, candidate) -> days[manualDay] = Assignment(candidate, Source.MANUAL) }

        return Plan(days, replaced)
    }

    private fun keepPrevious(
        previous: AutoPick?,
        byText: Map<String, Candidate>,
        pool: List<Candidate>,
        previousText: String?,
        taken: (String) -> Boolean,
        reserved: Set<String>,
        lastUse: Map<String, LocalDate>,
    ): Assignment? {
        val candidate = previous?.let { byText[it.text] } ?: return null
        if (!taken(candidate.text)) return Assignment(candidate, Source.AUTO)
        val neverUsedLeft = pool.any { !taken(it.text) }
        val keepsRepeat = previous.isRepeat && !neverUsedLeft && candidate.text !in reserved &&
            (candidate.text != previousText || pool.size == 1)
        return if (keepsRepeat) Assignment(candidate, Source.AUTO, isRepeat = true, lastUse = lastUse[candidate.text]) else null
    }

    private fun pickNew(
        day: LocalDate,
        pool: List<Candidate>,
        previousText: String?,
        nextDayText: String?,
        taken: (String) -> Boolean,
        reserved: Set<String>,
        heldUntil: Map<String, LocalDate>,
        lastUse: Map<String, LocalDate>,
        random: Random,
    ): Assignment {
        val fresh = pool.filter { !taken(it.text) }
        if (fresh.isNotEmpty()) {
            val unheld = fresh.filter { heldUntil[it.text]?.isAfter(day) != true }
            return Assignment(unheld.ifEmpty { fresh }.random(random), Source.AUTO)
        }
        val candidates = sequenceOf(
            pool.filter { it.text !in reserved && it.text != previousText && it.text != nextDayText },
            pool.filter { it.text !in reserved && it.text != previousText },
            pool.filter { it.text != previousText },
            pool,
        ).first { it.isNotEmpty() }
        val oldest = candidates.minOf { lastUse[it.text] ?: LocalDate.MIN }
        val choice = candidates.filter { (lastUse[it.text] ?: LocalDate.MIN) == oldest }.random(random)
        return Assignment(choice, Source.AUTO, isRepeat = true, lastUse = lastUse[choice.text])
    }
}
