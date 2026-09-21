package uz.abumme.harfgame.backend.admin.analytics

import kotlinx.coroutines.yield
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.AnalyticsCohortDayTable
import uz.abumme.harfgame.backend.db.AnalyticsGlobalDayTable
import uz.abumme.harfgame.backend.db.AnalyticsLangDayTable
import uz.abumme.harfgame.backend.db.AnalyticsRollupDaysTable
import uz.abumme.harfgame.backend.db.AnalyticsWordDayTable
import uz.abumme.harfgame.backend.db.AnalyticsAccountsDayTable
import uz.abumme.harfgame.backend.db.AnalyticsContentDayTable
import uz.abumme.harfgame.backend.db.AnalyticsPoolDayTable
import uz.abumme.harfgame.backend.db.AnalyticsStaffDayTable
import uz.abumme.harfgame.backend.db.AnalyticsSuggestionsDayTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * A family of daily aggregates recomputed together from raw data; [key] is its `analytics_rollup_days.grp`.
 */
sealed class RollupGroup(val key: String) {
    /** When [day] ends; the day is listed once it has. */
    abstract fun close(day: Long): Instant

    /** The latest closed day at [now]. */
    abstract fun lastClosed(now: Instant): Long

    /** When [day] stops changing. */
    open fun finalAt(day: Long): Instant = close(day).plus(AnalyticsDays.SETTLE)

    /** Players, games, outcomes and streak buckets of one language's puzzle day. */
    class Lang(val lang: String) : RollupGroup("lang:$lang") {
        override fun close(day: Long) = AnalyticsDays.closeOfPuzzleDay(lang, day)
        override fun lastClosed(now: Instant) = AnalyticsDays.closedPuzzleDays(lang, now)
    }

    /** DAU, WAU and MAU across languages. */
    data object Global : RollupGroup("global") {
        override fun close(day: Long) = AnalyticsDays.closeOfGlobalDay(day)
        override fun lastClosed(now: Instant) = AnalyticsDays.closedGlobalPuzzleDay(now)
    }

    /** Retention of the cohort that started on the day; final once its D30 target day is final. */
    data object Cohort : RollupGroup("cohort") {
        override fun close(day: Long) = AnalyticsDays.closeOfGlobalDay(day)
        override fun lastClosed(now: Instant) = AnalyticsDays.closedGlobalPuzzleDay(now)
        override fun finalAt(day: Long): Instant = close(day + RETENTION_DAYS.last()).plus(AnalyticsDays.SETTLE)
    }

    /** A calendar's daily word and how it was played. */
    class Word(val calendar: String) : RollupGroup("word:$calendar") {
        private val lang = DailyCalendars.wordLanguage(calendar)
        override fun close(day: Long) = AnalyticsDays.closeOfPuzzleDay(lang, day)
        override fun lastClosed(now: Instant) = AnalyticsDays.closedPuzzleDays(lang, now)
    }

    /** Accounts, suggestions, content, answer pools and staff activity of an Asia/Tashkent date (as an epoch day). */
    data object Events : RollupGroup("events") {
        override fun close(day: Long) = AnalyticsDays.endOfEventDate(LocalDate.ofEpochDay(day))
        override fun lastClosed(now: Instant) = AnalyticsDays.closedEventDates(now).toEpochDay()
    }

    override fun toString(): String = key

    companion object {
        /** The retention offsets, in days. */
        val RETENTION_DAYS = listOf(1L, 7L, 30L)
    }
}

/**
 * Computes the daily analytics rollups: every closed day, recomputed whole from the raw tables until its settle window
 * has passed, then final.
 *
 * Each [runOnce] (every 15 minutes, and once at startup) first runs the snapshot [sweep], then lists, per group, the
 * closed days from its first data day that still need work and recomputes up to [maxGroupDaysPerTick] of them, each in
 * its own transaction (delete + insert, with a statement timeout), holding one connection at a time:
 *
 * 1. days never computed, oldest first, so a newly closed day and every day missed during downtime appear at once;
 * 2. provisional days whose settle window has passed, which get their last computation and become final (a day first
 *    computed after its window goes straight to final);
 * 3. provisional days last computed at least [recomputeAfter] ago, which absorb late syncs.
 *
 * Recomputing a day equals computing it once from the rows stored at that time, so late syncs, catch-up and repair all
 * take this one path. End-of-day states that cannot be rebuilt later are captured on a date's first computation and
 * kept (see [RollupComputations]).
 */
class AnalyticsRollupJob(
    catalog: WordCatalogService,
    private val sweep: ResultsSweep,
    private val maxGroupDaysPerTick: Int = 60,
    private val recomputeAfter: Duration = Duration.ofHours(1),
) {
    private val computations = RollupComputations(catalog)

    /** One day of one group that needs computing, and why ([priority] 0 missing, 1 finalizing, 2 refreshing). */
    data class Pending(val group: RollupGroup, val day: Long, val priority: Int)

    data class Report(val sweep: ResultsSweep.Report, val computed: List<Pending>, val remaining: Int)

    suspend fun runOnce(now: Instant): Report {
        val swept = sweep.run(now)
        val pending = DatabaseFactory.dbQuery {
            ensureLinkTracking(now)
            plan(now)
        }
        val batch = pending.take(maxGroupDaysPerTick)
        for (item in batch) {
            DatabaseFactory.dbQuery {
                AnalyticsSql.limitStatementTime()
                compute(item.group, item.day, now)
            }
            yield()
        }
        return Report(swept, batch, pending.size - batch.size)
    }

    /** Everything that needs computing at [now], in the order [runOnce] takes it. Inside a transaction. */
    internal fun plan(now: Instant): List<Pending> {
        val languages = WordPacksTable.select(WordPacksTable.lang).map { it[WordPacksTable.lang] }.sorted()
        val groups = languages.map { RollupGroup.Lang(it) } + RollupGroup.Global + RollupGroup.Cohort +
            DailyCalendars.ALL.map { RollupGroup.Word(it) } + RollupGroup.Events
        val pending = ArrayList<Pending>()
        for (group in groups) {
            val first = computations.firstDataDay(group) ?: continue
            val start = maxOf(first, AnalyticsDays.HISTORY_START_DAY)
            val last = group.lastClosed(now)
            if (start > last) continue
            val stored = AnalyticsRollupDaysTable.selectAll()
                .where { (AnalyticsRollupDaysTable.grp eq group.key) and (AnalyticsRollupDaysTable.day greaterEq start) }
                .associate { it[AnalyticsRollupDaysTable.day] to (it[AnalyticsRollupDaysTable.computedAt] to it[AnalyticsRollupDaysTable.final]) }
            for (day in start..last) {
                val (computedAt, final) = stored[day] ?: (null to false)
                val priority = when {
                    computedAt == null -> 0
                    final -> continue
                    !now.isBefore(group.finalAt(day)) -> 1
                    !computedAt.isAfter(now.minus(recomputeAfter)) -> 2
                    else -> continue
                }
                pending += Pending(group, day, priority)
            }
        }
        return pending.sortedWith(compareBy({ it.priority }, { it.day }))
    }

    /** Recomputes [day] of [group] at [now] and records it; inside a transaction. */
    internal fun compute(group: RollupGroup, day: Long, now: Instant) {
        val firstComputation = AnalyticsRollupDaysTable.selectAll()
            .where { (AnalyticsRollupDaysTable.grp eq group.key) and (AnalyticsRollupDaysTable.day eq day) }
            .empty()
        when (group) {
            is RollupGroup.Lang -> computations.lang(group.lang, day)
            RollupGroup.Global -> computations.global(day)
            RollupGroup.Cohort -> computations.cohort(day, now)
            is RollupGroup.Word -> computations.word(group.calendar, day)
            RollupGroup.Events -> computations.events(LocalDate.ofEpochDay(day), now, firstComputation)
        }
        AnalyticsRollupDaysTable.deleteWhere { (AnalyticsRollupDaysTable.grp eq group.key) and (AnalyticsRollupDaysTable.day eq day) }
        AnalyticsRollupDaysTable.insert {
            it[grp] = group.key
            it[AnalyticsRollupDaysTable.day] = day
            it[computedAt] = now
            it[final] = !now.isBefore(group.finalAt(day))
        }
    }

    companion object {
        /** Every rollup table: aggregates only, never a player id. */
        val ROLLUP_TABLES = listOf(
            AnalyticsLangDayTable,
            AnalyticsGlobalDayTable,
            AnalyticsCohortDayTable,
            AnalyticsWordDayTable,
            AnalyticsAccountsDayTable,
            AnalyticsSuggestionsDayTable,
            AnalyticsContentDayTable,
            AnalyticsPoolDayTable,
            AnalyticsStaffDayTable,
        )

        /** Since when `oauth_identities.linked_at` is recorded; links per day before that are unknown. */
        const val LINKS_RECORDED_SINCE_KEY = "links_recorded_since"

        /** Marks the first run as the start of link-time recording (the column arrives with this deployment). */
        internal fun ensureLinkTracking(now: Instant) {
            if (readMeta(LINKS_RECORDED_SINCE_KEY) == null) writeMeta(LINKS_RECORDED_SINCE_KEY, now.toString())
        }
    }
}
