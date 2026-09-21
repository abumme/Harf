package uz.abumme.harfgame.backend.admin.answerpool

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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
import uz.abumme.harfgame.backend.admin.calendar.CalendarDays
import uz.abumme.harfgame.backend.admin.calendar.CalendarTransactions
import uz.abumme.harfgame.backend.admin.calendar.CalendarUsage
import uz.abumme.harfgame.backend.admin.words.WordCatalogService
import uz.abumme.harfgame.backend.admin.words.WordValidator
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.LexemePairsTable
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatus
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatusDto
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkOutcome
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PairDto
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolPageDto
import uz.abumme.harfgame.data.admin.answerpool.PoolReasons
import uz.abumme.harfgame.data.admin.answerpool.PoolWordDto
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordStatus
import java.util.UUID

/**
 * The ADMIN's answer pools: which catalog words may become a calendar's daily word. For `en`, `ru` and `kk` a word is
 * marked daily-eligible; for `uz` an Uzbek Latin and an Uzbek Cyrillic word form a pair, which is the eligibility of
 * both. Every change reconciles the calendar and publishes its packs in the same transaction, and is audited.
 * Eligibility survives a word's removal (the word is simply not picked or published while removed).
 */
class AnswerPoolService(private val catalog: WordCatalogService) {
    private val calendars get() = catalog.calendars
    private val transactions = CalendarTransactions(catalog)

    /** One page of the pool (by spelling, removed entries included and marked), with the never-used counter. */
    suspend fun list(calendar: String, q: String?, page: Int, size: Int): PoolPageDto = DatabaseFactory.dbQuery {
        transactions.requireCalendar(calendar)
        val today = CalendarDays.today(calendar, catalog.clock.instant())
        val usage = CalendarUsage.load(calendars, calendar, today)
        val needle = q?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val entries = if (calendar == DailyCalendars.UZ) {
            calendars.pairs().map { pair ->
                PoolWordDto(
                    pairId = pair.id,
                    text = pair.latnText,
                    textCyrl = pair.cyrlText,
                    latnWordId = pair.latnWordId,
                    cyrlWordId = pair.cyrlWordId,
                    active = pair.complete,
                )
            }
        } else {
            WordsTable.select(WordsTable.id, WordsTable.text, WordsTable.status)
                .where { (WordsTable.lang eq calendar) and (WordsTable.dailyEligible eq true) }
                .orderBy(WordsTable.text, SortOrder.ASC)
                .map { PoolWordDto(wordId = it[WordsTable.id], text = it[WordsTable.text], active = it[WordsTable.status] == WordStatus.ACTIVE.name) }
        }
        val unusedLeft = entries.count { it.active && usage.isUnused(it.text) }
        val matching = entries.filter { entry ->
            needle == null || calendars.key(calendar, needle) in entry.text || entry.textCyrl?.contains(needle) == true
        }
        val items = matching.drop(page * size).take(size).map { entry ->
            entry.copy(lastUsed = usage.lastUsed(entry.text)?.toString(), scheduledOn = usage.scheduledOn(entry.text))
        }
        PoolPageDto(unusedLeft, PageDto(items, page, size, matching.size.toLong()))
    }

    /**
     * Active catalog words of a supported board length that are not yet eligible (for `uz`, Uzbek Latin words not in a
     * pair), alphabetically.
     */
    suspend fun candidates(calendar: String, q: String?, page: Int, size: Int): PageDto<PoolCandidateDto> = DatabaseFactory.dbQuery {
        transactions.requireCalendar(calendar)
        val lang = DailyCalendars.wordLanguage(calendar)
        val config = catalog.registry.config(lang) ?: throw AdminApiException.notFound("No rules for $lang")
        val tokenizer = catalog.registry.tokenizer(lang) ?: throw AdminApiException.notFound("No rules for $lang")
        val needle = q?.let { catalog.normalize(lang, it) }?.takeIf { it.isNotEmpty() }
        val paired = if (calendar == DailyCalendars.UZ) {
            LexemePairsTable.select(LexemePairsTable.latnWordId).mapTo(HashSet()) { it[LexemePairsTable.latnWordId] }
        } else {
            emptySet()
        }
        val all = WordsTable.select(WordsTable.id, WordsTable.text)
            .where {
                val base = (WordsTable.lang eq lang) and (WordsTable.status eq WordStatus.ACTIVE.name)
                if (calendar == DailyCalendars.UZ) base else base and (WordsTable.dailyEligible eq false)
            }
            .orderBy(WordsTable.text, SortOrder.ASC)
            .asSequence()
            .filter { it[WordsTable.id] !in paired && (needle == null || needle in it[WordsTable.text]) }
            .mapNotNull { row ->
                val count = tokenizer.tokenize(row[WordsTable.text])?.size ?: return@mapNotNull null
                if (count !in config.minLength..config.maxLength) return@mapNotNull null
                PoolCandidateDto(row[WordsTable.id], row[WordsTable.text], count)
            }
            .toList()
        PageDto(all.drop(page * size).take(size), page, size, all.size.toLong())
    }

    /**
     * Marks catalog words (by id or spelling) daily-eligible for `en`, `ru` or `kk`, each with its own outcome; the valid
     * ones apply even when others fail, and the calendar is reconciled and published once.
     */
    suspend fun mark(principal: StaffPrincipal, calendar: String, request: MarkWordsRequest): List<MarkItemResultDto> {
        transactions.requireCalendarId(calendar)
        if (calendar == DailyCalendars.UZ) throw AdminApiException.validation("calendar", PoolReasons.PAIRS_ONLY)
        val count = request.wordIds.size + request.texts.size
        if (count == 0) throw AdminApiException.validation("items", FieldReasons.REQUIRED)
        if (count > WordRules.MAX_BULK_LINES) throw AdminApiException.validation("items", PoolReasons.TOO_MANY_ITEMS)
        return transactions.write(calendar) { now, _ ->
            val config = catalog.registry.config(calendar) ?: throw AdminApiException.notFound("No rules for $calendar")
            val tokenizer = catalog.registry.tokenizer(calendar) ?: throw AdminApiException.notFound("No rules for $calendar")
            val marked = HashSet<String>()

            fun outcome(input: String, row: ResultRow?): MarkItemResultDto {
                if (row == null) return MarkItemResultDto(input, outcome = MarkOutcome.NOT_IN_CATALOG)
                val id = row[WordsTable.id]
                val text = row[WordsTable.text]
                val result = when {
                    row[WordsTable.status] != WordStatus.ACTIVE.name -> MarkOutcome.REMOVED
                    tokenizer.tokenize(text)?.size?.let { it in config.minLength..config.maxLength } != true -> MarkOutcome.UNSUPPORTED_LENGTH
                    row[WordsTable.dailyEligible] || id in marked -> MarkOutcome.ALREADY_ELIGIBLE
                    else -> {
                        WordsTable.update({ WordsTable.id eq id }) { it[dailyEligible] = true }
                        catalog.audit.record(
                            AuditActor.Staff(principal.staffId), AuditActions.DAILY_ELIGIBILITY_MARKED, AuditTargets.WORD, id,
                            lang = calendar, details = auditDetails { fact("text", jsonOf(text)) },
                        )
                        marked += id
                        MarkOutcome.MARKED
                    }
                }
                return MarkItemResultDto(input, id, text, result)
            }

            val results = request.wordIds.map { id ->
                outcome(id, WordsTable.selectAll().where { (WordsTable.id eq id) and (WordsTable.lang eq calendar) }.singleOrNull())
            } + request.texts.filter { it.isNotBlank() }.map { line ->
                val text = catalog.normalize(calendar, line)
                outcome(line, WordsTable.selectAll().where { (WordsTable.lang eq calendar) and (WordsTable.text eq text) }.singleOrNull())
            }
            if (marked.isNotEmpty()) {
                calendars.reconcile(calendar, now)
                calendars.publish(calendar, now)
            }
            results
        }
    }

    /** Takes a word out of an `en`, `ru` or `kk` pool; a word that is not eligible is left as it is. */
    suspend fun unmark(principal: StaffPrincipal, calendar: String, wordId: String) {
        transactions.requireCalendarId(calendar)
        if (calendar == DailyCalendars.UZ) throw AdminApiException.validation("calendar", PoolReasons.PAIRS_ONLY)
        transactions.write(calendar) { now, _ ->
            val row = WordsTable.selectAll().where { (WordsTable.id eq wordId) and (WordsTable.lang eq calendar) }.singleOrNull()
                ?: throw AdminApiException.notFound("No word $wordId in $calendar")
            if (!row[WordsTable.dailyEligible]) return@write
            WordsTable.update({ WordsTable.id eq wordId }) { it[dailyEligible] = false }
            catalog.audit.record(
                AuditActor.Staff(principal.staffId), AuditActions.DAILY_ELIGIBILITY_UNMARKED, AuditTargets.WORD, wordId,
                lang = calendar, details = auditDetails { fact("text", jsonOf(row[WordsTable.text])) },
            )
            calendars.reconcile(calendar, now)
            calendars.publish(calendar, now)
        }
    }

    /** The catalog state of a Cyrillic spelling for a new pair: active, removed or missing, and the pair holding it. */
    suspend fun cyrlStatus(cyrl: String): CyrlStatusDto = DatabaseFactory.dbQuery {
        val normalized = catalog.normalize(CYRL, cyrl)
        if (normalized.isEmpty()) throw AdminApiException.validation("cyrl", FieldReasons.REQUIRED)
        val row = WordsTable.select(WordsTable.id, WordsTable.status)
            .where { (WordsTable.lang eq CYRL) and (WordsTable.text eq normalized) }
            .singleOrNull()
        CyrlStatusDto(
            normalized = normalized,
            cyrlWordId = row?.get(WordsTable.id),
            cyrlStatus = when (row?.get(WordsTable.status)) {
                null -> CyrlStatus.MISSING
                WordStatus.ACTIVE.name -> CyrlStatus.ACTIVE
                else -> CyrlStatus.REMOVED
            },
            pairedWith = row?.let { pairHolding(it[WordsTable.id]) }?.latnText,
        )
    }

    /**
     * Creates an Uzbek pair from an active Latin word and a confirmed Cyrillic spelling, which must pass the catalog's
     * validation; with [CreatePairRequest.addCyrlToCatalog] a missing spelling is added and a removed one restored.
     * A word already in a pair answers 409 naming that pair.
     */
    suspend fun createPair(principal: StaffPrincipal, request: CreatePairRequest): PairDto = transactions.write(DailyCalendars.UZ) { now, _ ->
        val latn = WordsTable.selectAll().where { (WordsTable.id eq request.latnWordId) and (WordsTable.lang eq LATN) }.singleOrNull()
            ?: throw AdminApiException.validation("latnWordId", PoolReasons.NOT_ACTIVE)
        if (latn[WordsTable.status] != WordStatus.ACTIVE.name) throw AdminApiException.validation("latnWordId", PoolReasons.NOT_ACTIVE)
        val latnConfig = catalog.registry.config(LATN) ?: throw AdminApiException.notFound("No rules for $LATN")
        val latnCount = catalog.registry.tokenizer(LATN)?.tokenize(latn[WordsTable.text])?.size
        if (latnCount == null || latnCount !in latnConfig.minLength..latnConfig.maxLength) {
            throw AdminApiException.validation("latnWordId", WordReasons.BAD_LENGTH)
        }
        pairHolding(latn[WordsTable.id])?.let { throw AdminApiException.conflict("latnWordId", PoolReasons.paired(it.latnText, it.cyrlText)) }

        val cyrl = when (val result = catalog.validator(CYRL).validate(request.cyrlText)) {
            is WordValidator.Invalid -> throw AdminApiException.validation("cyrlText", result.reason)
            is WordValidator.Valid -> result.normalized
        }
        val existing = WordsTable.selectAll().where { (WordsTable.lang eq CYRL) and (WordsTable.text eq cyrl) }.singleOrNull()
        existing?.let { row -> pairHolding(row[WordsTable.id])?.let { throw AdminApiException.conflict("cyrlText", PoolReasons.paired(it.latnText, it.cyrlText)) } }
        val cyrlId = when {
            existing != null && existing[WordsTable.status] == WordStatus.ACTIVE.name -> existing[WordsTable.id]
            !request.addCyrlToCatalog ->
                throw AdminApiException.validation("cyrlText", if (existing == null) PoolReasons.NOT_IN_CATALOG else PoolReasons.REMOVED)
            else -> catalog.ensureActiveInTransaction(CYRL, cyrl, principal.staffId)
        }

        val id = UUID.randomUUID().toString()
        LexemePairsTable.insert {
            it[LexemePairsTable.id] = id
            it[latnWordId] = latn[WordsTable.id]
            it[cyrlWordId] = cyrlId
            it[createdByStaffId] = principal.staffId
            it[createdAt] = now
        }
        WordsTable.update({ WordsTable.id inList listOf(latn[WordsTable.id], cyrlId) }) { it[dailyEligible] = true }
        catalog.audit.record(
            AuditActor.Staff(principal.staffId), AuditActions.DAILY_PAIR_CREATED, AuditTargets.LEXEME_PAIR, id,
            lang = DailyCalendars.UZ,
            details = auditDetails {
                fact("latn", jsonOf(latn[WordsTable.text]))
                fact("cyrl", jsonOf(cyrl))
                if (existing?.get(WordsTable.status) != WordStatus.ACTIVE.name) fact("cyrlAdded", jsonOf(if (existing == null) "added" else "restored"))
            },
        )
        calendars.reconcile(DailyCalendars.UZ, now)
        calendars.publish(DailyCalendars.UZ, now)
        PairDto(
            id = id,
            latnWordId = latn[WordsTable.id],
            latnText = latn[WordsTable.text],
            cyrlWordId = cyrlId,
            cyrlText = cyrl,
            createdAt = now.toEpochMilli(),
            createdBy = StaffTable.select(StaffTable.id, StaffTable.username, StaffTable.displayName)
                .where { StaffTable.id eq principal.staffId }
                .singleOrNull()
                ?.let { StaffRefDto(it[StaffTable.id], it[StaffTable.username], it[StaffTable.displayName]) },
        )
    }

    /** Removes a pair and with it the eligibility of both words. */
    suspend fun removePair(principal: StaffPrincipal, pairId: String) {
        transactions.write(DailyCalendars.UZ) { now, _ ->
            val pair = calendars.pairs().firstOrNull { it.id == pairId } ?: throw AdminApiException.notFound("No pair $pairId")
            LexemePairsTable.deleteWhere { LexemePairsTable.id eq pairId }
            WordsTable.update({ WordsTable.id inList listOf(pair.latnWordId, pair.cyrlWordId) }) { it[dailyEligible] = false }
            catalog.audit.record(
                AuditActor.Staff(principal.staffId), AuditActions.DAILY_PAIR_REMOVED, AuditTargets.LEXEME_PAIR, pairId,
                lang = DailyCalendars.UZ,
                details = auditDetails {
                    fact("latn", jsonOf(pair.latnText))
                    fact("cyrl", jsonOf(pair.cyrlText))
                },
            )
            calendars.reconcile(DailyCalendars.UZ, now)
            calendars.publish(DailyCalendars.UZ, now)
        }
    }

    private fun pairHolding(wordId: String) =
        calendars.pairs().firstOrNull { it.latnWordId == wordId || it.cyrlWordId == wordId }

    private companion object {
        const val LATN = "uz-latn"
        const val CYRL = "uz-cyrl"
    }
}
