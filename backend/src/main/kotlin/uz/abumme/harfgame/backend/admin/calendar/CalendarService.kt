package uz.abumme.harfgame.backend.admin.calendar

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.admin.words.PackIntegrityException
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.db.CalendarNoticesTable
import uz.abumme.harfgame.backend.db.DailyWordsTable
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.CalendarReasons
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.admin.calendar.NoticeKind
import uz.abumme.harfgame.data.admin.calendar.NoticeReason
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordReasons
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate

/** Filters of one calendar range query: inclusive days, an optional [source], [repeatsOnly]; [page] zero-based. */
data class DayQuery(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val source: DaySource? = null,
    val repeatsOnly: Boolean = false,
    val page: Int = 0,
    val size: Int = 50,
)

/**
 * The ADMIN's daily-word calendars: day ranges, the picker's candidates, manual picks and unpicks, notices. Every change
 * runs at READ COMMITTED under the calendar's pack locks, reconciles the calendar and publishes its packs in the same
 * transaction; `today` is computed once per request from the backend clock, in the calendar's timezone.
 */
class CalendarService(private val catalog: WordCatalogService) {
    private val calendars get() = catalog.calendars
    private val clock get() = catalog.clock
    private val transactions = CalendarTransactions(catalog)

    // --- reads --------------------------------------------------------------------------------------------------

    suspend fun days(calendar: String, query: DayQuery): PageDto<DayDto> = DatabaseFactory.dbQuery {
        requireCalendar(calendar)
        val today = CalendarDays.today(calendar, clock.instant())
        val usage = CalendarUsage.load(calendars, calendar, today)
        val previousUses = usage.previousUses()
        val from = query.from ?: today
        val to = query.to ?: CalendarDays.horizonEnd(today)
        val matching = usage.rows.filter { row ->
            val day = row[DailyWordsTable.day]
            day in from..to &&
                (query.source == null || row[DailyWordsTable.daySource] == query.source.name) &&
                (!query.repeatsOnly || row[DailyWordsTable.isRepeat])
        }
        val pageRows = matching.drop(query.page * query.size).take(query.size)
        PageDto(dayDtos(calendar, pageRows, today, previousUses), query.page, query.size, matching.size.toLong())
    }

    /** Eligible words for picking [day]: never-used first, then by oldest last use; [q] matches either script. */
    suspend fun candidates(calendar: String, day: LocalDate?, q: String?, page: Int, size: Int): PageDto<CalendarCandidateDto> =
        DatabaseFactory.dbQuery {
            requireCalendar(calendar)
            val today = CalendarDays.today(calendar, clock.instant())
            val usage = CalendarUsage.load(calendars, calendar, today)
            val needle = q?.trim()?.takeIf { it.isNotEmpty() }
            val all = calendars.eligible(calendar)
                .filter { candidate ->
                    needle == null || needle.let { calendars.key(calendar, it) } in candidate.text ||
                        candidate.textCyrl?.contains(needle.lowercase()) == true
                }
                .map { candidate ->
                    val uz = calendar == DailyCalendars.UZ
                    CalendarCandidateDto(
                        wordId = candidate.id.takeUnless { uz },
                        pairId = candidate.id.takeIf { uz },
                        text = candidate.text,
                        textCyrl = candidate.textCyrl,
                        neverUsed = candidate.text !in usage.usedDays,
                        lastUsed = usage.lastUsed(candidate.text)?.toString(),
                        scheduledOn = usage.scheduledOn(candidate.text, except = day),
                    )
                }
                .sortedWith(compareBy<CalendarCandidateDto>({ !it.neverUsed }, { it.lastUsed ?: "" }, { it.text }))
            PageDto(all.drop(page * size).take(size), page, size, all.size.toLong())
        }

    suspend fun notices(includeDismissed: Boolean): List<CalendarNoticeDto> = DatabaseFactory.dbQuery {
        val query = if (includeDismissed) {
            CalendarNoticesTable.selectAll()
        } else {
            CalendarNoticesTable.selectAll().where { CalendarNoticesTable.dismissedAt.isNull() }
        }
        val rows = query
            .orderBy(CalendarNoticesTable.createdAt to SortOrder.DESC, CalendarNoticesTable.id to SortOrder.DESC)
            .limit(NOTICES_MAX)
            .toList()
        val staff = staffRefs(rows.mapNotNull { it[CalendarNoticesTable.dismissedByStaffId] })
        rows.map { row ->
            CalendarNoticeDto(
                id = row[CalendarNoticesTable.id],
                calendar = row[CalendarNoticesTable.calendar],
                day = row[CalendarNoticesTable.day].toString(),
                kind = NoticeKind.valueOf(row[CalendarNoticesTable.kind]),
                wordText = row[CalendarNoticesTable.wordText],
                reason = NoticeReason.valueOf(row[CalendarNoticesTable.reason]),
                createdAt = row[CalendarNoticesTable.createdAt].toEpochMilli(),
                dismissedAt = row[CalendarNoticesTable.dismissedAt]?.toEpochMilli(),
                dismissedBy = row[CalendarNoticesTable.dismissedByStaffId]?.let(staff::get),
            )
        }
    }

    // --- writes -------------------------------------------------------------------------------------------------

    /**
     * Picks a pool word (or Uzbek pair) for [day]. Refused: a locked day or one too far ahead (422), a word not in the
     * pool (422), a word that was the word of a counted day or is manually picked for another day (409 naming the days).
     * A word automatically scheduled elsewhere moves: that day gets a new automatic pick.
     */
    suspend fun pick(principal: StaffPrincipal, calendar: String, day: LocalDate, request: PickDayRequest): DayDto =
        write(calendar) { now, today ->
            if (CalendarDays.isLocked(day, today)) throw AdminApiException.validation("day", CalendarReasons.LOCKED)
            if (CalendarDays.isTooFar(day, today)) throw AdminApiException.validation("day", CalendarReasons.TOO_FAR)
            val uz = calendar == DailyCalendars.UZ
            val id = (if (uz) request.pairId else request.wordId)?.takeIf { it.isNotBlank() }
                ?: throw AdminApiException.validation("word", FieldReasons.REQUIRED)
            val candidate = calendars.eligible(calendar).firstOrNull { it.id == id }
                ?: throw AdminApiException.validation("word", CalendarReasons.NOT_ELIGIBLE)

            val usage = CalendarUsage.load(calendars, calendar, today)
            usage.usedDays[candidate.text]?.let { days ->
                throw AdminApiException.conflict("word", CalendarReasons.conflict(CalendarReasons.USED, days.map(LocalDate::toString)))
            }
            val current = usage.rows.firstOrNull { it[DailyWordsTable.day] == day }
            val pickedElsewhere = usage.rows.filter { row ->
                row[DailyWordsTable.day] >= usage.firstOpen && row[DailyWordsTable.day] != day &&
                    row[DailyWordsTable.daySource] == DaySource.MANUAL.name &&
                    (CalendarReconciler.refId(calendar, row) == candidate.id || usage.key(row[DailyWordsTable.day]) == candidate.text)
            }
            if (pickedElsewhere.isNotEmpty()) {
                throw AdminApiException.conflict("word", CalendarReasons.conflict(CalendarReasons.PICKED, pickedElsewhere.map { it[DailyWordsTable.day].toString() }))
            }
            val alreadyPicked = current != null && current[DailyWordsTable.daySource] == DaySource.MANUAL.name &&
                CalendarReconciler.refId(calendar, current) == candidate.id
            if (!alreadyPicked) {
                if (current == null) {
                    DailyWordsTable.insert {
                        it[DailyWordsTable.calendar] = calendar
                        it[DailyWordsTable.day] = day
                        it[wordId] = candidate.id.takeUnless { uz }
                        it[lexemeId] = candidate.id.takeIf { uz }
                        it[text] = candidate.text
                        it[textCyrl] = candidate.textCyrl
                        it[daySource] = DaySource.MANUAL.name
                        it[isRepeat] = false
                        it[pickedByStaffId] = principal.staffId
                        it[pickedAt] = now
                    }
                } else {
                    DailyWordsTable.update({ (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }) {
                        it[wordId] = candidate.id.takeUnless { uz }
                        it[lexemeId] = candidate.id.takeIf { uz }
                        it[text] = candidate.text
                        it[textCyrl] = candidate.textCyrl
                        it[daySource] = DaySource.MANUAL.name
                        it[isRepeat] = false
                        it[pickedByStaffId] = principal.staffId
                        it[pickedAt] = now
                    }
                }
                catalog.audit.record(
                    AuditActor.Staff(principal.staffId), AuditActions.DAILY_WORD_PICKED, AuditTargets.DAILY_DAY,
                    CalendarReconciler.dayTarget(calendar, day), lang = calendar,
                    details = auditDetails {
                        fact("day", jsonOf(day.toString()))
                        fact("word", jsonOf(wordLabel(candidate.text, candidate.textCyrl)))
                        current?.let {
                            fact("previous", jsonOf(wordLabel(it[DailyWordsTable.text], it[DailyWordsTable.textCyrl])))
                            fact("previousSource", jsonOf(it[DailyWordsTable.daySource]))
                        }
                    },
                )
                calendars.reconcile(calendar, now)
                calendars.publish(calendar, now)
            }
            dayDto(calendar, day, today)
        }

    /** Returns an unlocked manually picked day to automatic; the day gets an automatic pick at once. */
    suspend fun unpick(principal: StaffPrincipal, calendar: String, day: LocalDate): DayDto = write(calendar) { now, today ->
        if (CalendarDays.isLocked(day, today)) throw AdminApiException.validation("day", CalendarReasons.LOCKED)
        val current = DailyWordsTable.selectAll()
            .where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }
            .singleOrNull()
        if (current == null || current[DailyWordsTable.daySource] != DaySource.MANUAL.name) {
            throw AdminApiException.validation("day", CalendarReasons.NOT_MANUAL)
        }
        DailyWordsTable.deleteWhere { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day eq day) }
        catalog.audit.record(
            AuditActor.Staff(principal.staffId), AuditActions.DAILY_WORD_UNPICKED, AuditTargets.DAILY_DAY,
            CalendarReconciler.dayTarget(calendar, day), lang = calendar,
            details = auditDetails {
                fact("day", jsonOf(day.toString()))
                fact("word", jsonOf(wordLabel(current[DailyWordsTable.text], current[DailyWordsTable.textCyrl])))
            },
        )
        calendars.reconcile(calendar, now)
        calendars.publish(calendar, now)
        dayDto(calendar, day, today)
    }

    /** Hides a notice for every ADMIN; dismissing a dismissed notice changes nothing. */
    suspend fun dismiss(principal: StaffPrincipal, id: String) = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
        val row = CalendarNoticesTable.selectAll().where { CalendarNoticesTable.id eq id }
            .forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
            ?: throw AdminApiException.notFound("No notice $id")
        if (row[CalendarNoticesTable.dismissedAt] != null) return@dbQuery
        CalendarNoticesTable.update({ CalendarNoticesTable.id eq id }) {
            it[dismissedAt] = clock.instant()
            it[dismissedByStaffId] = principal.staffId
        }
        catalog.audit.record(
            AuditActor.Staff(principal.staffId), AuditActions.DAILY_NOTICE_DISMISSED, AuditTargets.CALENDAR_NOTICE, id,
            lang = row[CalendarNoticesTable.calendar],
            details = auditDetails {
                fact("day", jsonOf(row[CalendarNoticesTable.day].toString()))
                fact("word", jsonOf(row[CalendarNoticesTable.wordText]))
            },
        )
    }

    // --- periodic -----------------------------------------------------------------------------------------------

    /**
     * Brings [calendar] up to date at [now] when [dayChanged] or its horizon is short: reconcile and publish (a no-op
     * publishes nothing). Returns whether it reconciled; an uninitialized calendar is skipped.
     */
    suspend fun refresh(calendar: String, now: Instant, dayChanged: Boolean): Boolean {
        val today = CalendarDays.today(calendar, now)
        if (!dayChanged && !DatabaseFactory.dbQuery { horizonShort(calendar, today) }) return false
        return DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
            if (!lockCalendar(calendar) || calendars.initializedOn(calendar) == null) return@dbQuery false
            calendars.reconcile(calendar, now)
            calendars.publish(calendar, now)
            true
        }
    }

    /** Fewer stored days than today through the horizon (a new day has begun, or the horizon was never filled). */
    private fun horizonShort(calendar: String, today: LocalDate): Boolean {
        val horizonEnd = CalendarDays.horizonEnd(today)
        val stored = DailyWordsTable.select(DailyWordsTable.day)
            .where { (DailyWordsTable.calendar eq calendar) and (DailyWordsTable.day greaterEq today) and (DailyWordsTable.day lessEq horizonEnd) }
            .count()
        return stored < DailyCalendars.HORIZON_DAYS + 1
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private suspend fun <T> write(calendar: String, block: (now: Instant, today: LocalDate) -> T): T =
        transactions.write(calendar, block)

    private fun lockCalendar(calendar: String): Boolean = transactions.lockCalendar(calendar)

    private fun requireCalendar(calendar: String) = transactions.requireCalendar(calendar)

    private fun dayDto(calendar: String, day: LocalDate, today: LocalDate): DayDto {
        val usage = CalendarUsage.load(calendars, calendar, today)
        val row = usage.rows.firstOrNull { it[DailyWordsTable.day] == day } ?: throw AdminApiException.notFound("No word for $day")
        return dayDtos(calendar, listOf(row), today, usage.previousUses()).single()
    }

    private fun dayDtos(calendar: String, rows: List<ResultRow>, today: LocalDate, previousUses: Map<LocalDate, LocalDate>): List<DayDto> {
        val staff = staffRefs(rows.mapNotNull { it[DailyWordsTable.pickedByStaffId] })
        return rows.map { row ->
            val day = row[DailyWordsTable.day]
            DayDto(
                day = day.toString(),
                text = row[DailyWordsTable.text],
                textCyrl = row[DailyWordsTable.textCyrl],
                source = DaySource.valueOf(row[DailyWordsTable.daySource]),
                isRepeat = row[DailyWordsTable.isRepeat],
                lastUsed = previousUses[day]?.toString(),
                locked = CalendarDays.isLocked(day, today),
                wordId = row[DailyWordsTable.wordId],
                pairId = row[DailyWordsTable.lexemeId],
                pickedBy = row[DailyWordsTable.pickedByStaffId]?.let(staff::get),
                pickedAt = row[DailyWordsTable.pickedAt].toEpochMilli(),
            )
        }
    }

    private fun staffRefs(ids: Collection<String>): Map<String, StaffRefDto> {
        if (ids.isEmpty()) return emptyMap()
        return StaffTable.select(StaffTable.id, StaffTable.username, StaffTable.displayName)
            .where { StaffTable.id inList ids.toSet() }
            .associate { it[StaffTable.id] to StaffRefDto(it[StaffTable.id], it[StaffTable.username], it[StaffTable.displayName]) }
    }

    private companion object {
        const val NOTICES_MAX = 200

        fun wordLabel(text: String, cyrl: String?) = if (cyrl == null) text else "$text / $cyrl"
    }
}
