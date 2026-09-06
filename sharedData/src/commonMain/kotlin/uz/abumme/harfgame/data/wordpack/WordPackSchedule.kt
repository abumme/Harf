package uz.abumme.harfgame.data.wordpack

import kotlinx.datetime.LocalDate
import kotlin.random.Random

/**
 * The shared, deterministic schedule generator. The backend uses it to seed version 1 and the client
 * uses it to build the **bundled baseline** schedule. Because both sides run the identical algorithm
 * with the same seed, anchor, and answer order, a fresh offline client and a client synced to
 * version 1 agree on every day's word.
 */
object WordPackSchedule {

    /** Day `schedule[0]` maps to. Fixed so all clients agree. */
    val ANCHOR_EPOCH_DAY: Long = LocalDate(2026, 1, 1).toEpochDays()

    /** How many days each seeded/baseline schedule covers. */
    const val HORIZON: Int = 800

    /** Stable per-language seed so the generated order is reproducible. */
    fun seedFor(lang: String): Long = lang.hashCode().toLong()

    /** Deterministic play order over [answers] with no adjacent repeat, [horizon] entries. */
    fun build(answers: List<String>, seed: Long, horizon: Int = HORIZON): List<String> {
        if (answers.size <= 1) return List(horizon) { answers.first() }
        val rnd = Random(seed)
        val out = ArrayList<String>(horizon)
        while (out.size < horizon) {
            for (w in answers.shuffled(rnd)) {
                if (out.isNotEmpty() && out.last() == w) continue
                out.add(w)
                if (out.size >= horizon) break
            }
        }
        return out
    }

    /** Same as [build] but over indices — for paired (multi-script) vocab kept in lockstep. */
    fun buildOrder(size: Int, seed: Long, horizon: Int = HORIZON): List<Int> {
        if (size <= 1) return List(horizon) { 0 }
        val rnd = Random(seed)
        val idx = (0 until size).toList()
        val out = ArrayList<Int>(horizon)
        while (out.size < horizon) {
            for (i in idx.shuffled(rnd)) {
                if (out.isNotEmpty() && out.last() == i) continue
                out.add(i)
                if (out.size >= horizon) break
            }
        }
        return out
    }

    /** The answer for [epochDay] given a schedule anchored at [anchorEpochDay]; wraps past the horizon. */
    fun <T> answerFor(schedule: List<T>, anchorEpochDay: Long, epochDay: Long): T {
        val i = epochDay - anchorEpochDay
        val idx = if (i in 0 until schedule.size.toLong()) i.toInt() else i.mod(schedule.size)
        return schedule[idx]
    }
}
