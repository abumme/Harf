package uz.abumme.harfgame.backend.admin.words

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.backend.admin.access.StaffPrincipal
import uz.abumme.harfgame.backend.admin.audit.AuditActor
import uz.abumme.harfgame.backend.admin.audit.AuditLog
import uz.abumme.harfgame.backend.admin.audit.auditDetails
import uz.abumme.harfgame.backend.admin.audit.jsonOf
import uz.abumme.harfgame.backend.admin.calendar.CalendarReconciler
import uz.abumme.harfgame.backend.admin.calendar.DailySettings
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.StaffTable
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.backend.db.WordSuggestionsTable
import uz.abumme.harfgame.backend.db.WordsTable
import uz.abumme.harfgame.backend.service.DecidedVia
import uz.abumme.harfgame.backend.service.WordPackServerService
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.calendar.DailyCalendars
import uz.abumme.harfgame.data.admin.words.BulkAddResult
import uz.abumme.harfgame.data.admin.words.BulkLineOutcome
import uz.abumme.harfgame.data.admin.words.BulkLineResult
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.StaffRefDto
import uz.abumme.harfgame.data.admin.words.WordCheckOutcome
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.admin.words.WordReasons
import uz.abumme.harfgame.data.admin.words.WordRules
import uz.abumme.harfgame.data.admin.words.WordSource
import uz.abumme.harfgame.data.admin.words.WordStatus
import uz.abumme.harfgame.data.suggestion.SuggestionStatus
import uz.abumme.harfgame.lang.LanguageRegistry
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The word catalog: every write to a language's vocabulary, each published in the same transaction.
 *
 * Staff operations (list, check, add, bulk add, edit, remove, restore) check the caller's language scope inside their
 * transaction and refuse with [AdminApiException]s. The system paths — accepting a suggestion, the one-time carry-over
 * of the published packs and the startup merge of deployed dictionaries — use the same rows, lock and publisher.
 *
 * A change that leaves nothing different publishes nothing; a change the app would refuse rolls back entirely
 * (`422 pack: pack_integrity`, a message that says nothing about daily words). Word responses never carry
 * daily-eligibility data.
 */
class WordCatalogService(
    internal val wordPacks: WordPackServerService = WordPackServerService(),
    internal val clock: Clock = Clock.systemUTC(),
    internal val registry: LanguageRegistry = LanguageRegistry(),
    val blocklists: Blocklists = Blocklists.default,
    /** The daily-word calendar settings (`DAILY_HISTORY_START`, the random source of automatic picks). */
    internal val daily: DailySettings = DailySettings.fromEnv(),
) {
    internal val audit = AuditLog(clock)
    internal val publisher = PackPublisher(registry, clock)

    /** The daily-word calendars; every catalog change reconciles its language's calendar before publishing. */
    internal val calendars = CalendarReconciler(registry, daily, audit, publisher)

    fun validator(lang: String): WordValidator = WordValidator(lang, registry, blocklists)

    /** [raw] in [lang]'s normalized form (lowercased when the language has no rules). */
    fun normalize(lang: String, raw: String): String =
        registry.tokenizer(lang)?.normalize(raw.trim())?.trim() ?: raw.trim().lowercase()

    // --- reads --------------------------------------------------------------------------------------------------

    suspend fun list(principal: StaffPrincipal, query: WordQuery): PageDto<WordDto> = DatabaseFactory.dbQuery {
        principal.requireLanguage(query.lang)
        requirePackExists(query.lang)
        val condition = conditionFor(query)
        val total = WordsTable.selectAll().where(condition).count()
        val sortColumn = when (query.sort) {
            WordQuery.Sort.TEXT -> WordsTable.text
            WordQuery.Sort.CREATED -> WordsTable.createdAt
            WordQuery.Sort.UPDATED -> WordsTable.updatedAt
        }
        val order = if (query.descending) SortOrder.DESC else SortOrder.ASC
        val rows = WordsTable.selectAll().where(condition)
            .orderBy(sortColumn to order, WordsTable.id to order)
            .limit(query.size)
            .offset(query.page.toLong() * query.size)
            .toList()
        PageDto(items = toDtos(rows), page = query.page, size = query.size, total = total)
    }

    /** What adding [text] to [lang] would do; changes nothing. */
    suspend fun check(principal: StaffPrincipal, lang: String, text: String): CheckWordResult = DatabaseFactory.dbQuery {
        principal.requireLanguage(lang)
        requirePackExists(lang)
        when (val result = validator(lang).validate(text)) {
            is WordValidator.Invalid -> CheckWordResult(
                normalized = result.normalized,
                graphemeCount = result.graphemes?.size,
                outcome = if (result.reason == WordReasons.BLOCKLISTED) WordCheckOutcome.BLOCKLISTED else WordCheckOutcome.INVALID,
                reason = result.reason,
            )
            is WordValidator.Valid -> CheckWordResult(
                normalized = result.normalized,
                graphemeCount = result.graphemes.size,
                outcome = when (findRow(lang, result.normalized)?.status()) {
                    null -> WordCheckOutcome.VALID
                    WordStatus.ACTIVE -> WordCheckOutcome.DUPLICATE
                    WordStatus.REMOVED -> WordCheckOutcome.RESTORABLE
                },
            )
        }
    }

    /** The catalog status of [word] (raw or normalized) in [lang], or null when the catalog has no such spelling. */
    suspend fun statusOf(lang: String, word: String): WordStatus? = DatabaseFactory.dbQuery {
        findRow(lang, normalize(lang, word))?.status()
    }

    // --- staff writes -------------------------------------------------------------------------------------------

    /** Adds a word as STAFF, or restores the removed word with that spelling (`restored = true`). */
    suspend fun add(principal: StaffPrincipal, lang: String, text: String): WordDto = writeLanguage(principal, lang) { pack ->
        val valid = requireValid(lang, text)
        val actor = AuditActor.Staff(principal.staffId)
        val existing = findRow(lang, valid.normalized)
        val restored = when (existing?.status()) {
            null -> false
            WordStatus.ACTIVE -> throw AdminApiException.conflict("text", WordReasons.DUPLICATE)
            WordStatus.REMOVED -> true
        }
        val id = if (existing == null) {
            insertWord(lang, valid.normalized, WordSource.STAFF, principal.staffId, actor)
        } else {
            restoreRow(existing, principal.staffId, actor)
        }
        publishChange(pack)
        loadDto(id).copy(restored = restored)
    }

    /**
     * Adds up to [WordRules.MAX_BULK_LINES] lines, each with its own outcome; valid lines apply even when others fail.
     * Publishes at most once, and not at all when nothing was added or restored.
     */
    suspend fun bulkAdd(principal: StaffPrincipal, lang: String, lines: List<String>): BulkAddResult {
        if (lines.size > WordRules.MAX_BULK_LINES) throw AdminApiException.validation("lines", WordReasons.TOO_MANY_LINES)
        return writeLanguage(principal, lang) { pack ->
            val validator = validator(lang)
            val checked = lines.withIndex().filter { it.value.isNotBlank() }.map { (index, line) -> index + 1 to validator.validate(line) }
            val existing = findRows(lang, checked.mapNotNull { (it.second as? WordValidator.Valid)?.normalized }.distinct())
            val actor = AuditActor.Staff(principal.staffId)
            val seen = HashSet<String>()
            var changed = false

            val results = checked.map { (line, result) ->
                when (result) {
                    is WordValidator.Invalid -> BulkLineResult(
                        line = line,
                        text = result.normalized.ifEmpty { null },
                        outcome = if (result.reason == WordReasons.BLOCKLISTED) BulkLineOutcome.BLOCKLISTED else BulkLineOutcome.INVALID,
                        reason = result.reason,
                    )
                    is WordValidator.Valid -> {
                        val text = result.normalized
                        val row = existing[text]
                        val outcome = when {
                            !seen.add(text) -> BulkLineOutcome.DUPLICATE
                            row == null -> {
                                insertWord(lang, text, WordSource.STAFF, principal.staffId, actor)
                                BulkLineOutcome.ADDED
                            }
                            row.status() == WordStatus.ACTIVE -> BulkLineOutcome.DUPLICATE
                            else -> {
                                restoreRow(row, principal.staffId, actor)
                                BulkLineOutcome.RESTORED
                            }
                        }
                        if (outcome != BulkLineOutcome.DUPLICATE) changed = true
                        BulkLineResult(line = line, text = text, outcome = outcome)
                    }
                }
            }
            BulkAddResult(results, packVersion = if (changed) publishChange(pack) else pack.version)
        }
    }

    /**
     * Respells an active word in place (id, source and provenance survive) and keeps the previous spelling as a
     * REMOVED tombstone, so a bundled old spelling is never merged back. A spelling held by a removed word is refused
     * with `removed_exists`: restore that word instead.
     */
    suspend fun edit(principal: StaffPrincipal, id: String, text: String): WordDto = writeWord(principal, id) { pack, row ->
        if (row.status() != WordStatus.ACTIVE) throw AdminApiException.conflict("text", WordReasons.NOT_ACTIVE)
        val lang = row[WordsTable.lang]
        val valid = requireValid(lang, text)
        val previous = row[WordsTable.text]
        if (valid.normalized != previous) {
            when (findRow(lang, valid.normalized)?.status()) {
                null -> Unit
                WordStatus.ACTIVE -> throw AdminApiException.conflict("text", WordReasons.DUPLICATE)
                WordStatus.REMOVED -> throw AdminApiException.conflict("text", WordReasons.REMOVED_EXISTS)
            }
            val now = clock.instant()
            WordsTable.update({ WordsTable.id eq id }) {
                it[WordsTable.text] = valid.normalized
                it[updatedByStaffId] = principal.staffId
                it[updatedAt] = now
            }
            WordsTable.insert {
                it[WordsTable.id] = UUID.randomUUID().toString()
                it[WordsTable.lang] = lang
                it[WordsTable.text] = previous
                it[status] = WordStatus.REMOVED.name
                it[wordSource] = row[WordsTable.wordSource]
                it[suggestionId] = row[WordsTable.suggestionId]
                it[dailyEligible] = false
                it[createdByStaffId] = row[WordsTable.createdByStaffId]
                it[createdAt] = row[WordsTable.createdAt]
                it[updatedByStaffId] = principal.staffId
                it[updatedAt] = now
                it[removedByStaffId] = principal.staffId
                it[removedAt] = now
            }
            audit.record(
                AuditActor.Staff(principal.staffId), AuditActions.WORD_EDITED, AuditTargets.WORD, id, lang,
                auditDetails { change("text", jsonOf(previous), jsonOf(valid.normalized)) },
            )
            publishChange(pack)
        }
        loadDto(id)
    }

    /** Marks an active word REMOVED (a no-op for a removed one); the next pack no longer contains it. */
    suspend fun remove(principal: StaffPrincipal, id: String): WordDto = writeWord(principal, id) { pack, row ->
        if (row.status() == WordStatus.ACTIVE) {
            val now = clock.instant()
            WordsTable.update({ WordsTable.id eq id }) {
                it[status] = WordStatus.REMOVED.name
                it[removedByStaffId] = principal.staffId
                it[removedAt] = now
                it[updatedByStaffId] = principal.staffId
                it[updatedAt] = now
            }
            audit.record(
                AuditActor.Staff(principal.staffId), AuditActions.WORD_REMOVED, AuditTargets.WORD, id, row[WordsTable.lang],
                auditDetails { fact("text", jsonOf(row[WordsTable.text])) },
            )
            publishChange(pack)
        }
        loadDto(id)
    }

    /** Makes a removed word active again if it still passes validation (a no-op for an active one). */
    suspend fun restore(principal: StaffPrincipal, id: String): WordDto = writeWord(principal, id) { pack, row ->
        if (row.status() == WordStatus.REMOVED) {
            requireValid(row[WordsTable.lang], row[WordsTable.text])
            restoreRow(row, principal.staffId, AuditActor.Staff(principal.staffId))
            publishChange(pack)
        }
        loadDto(id)
    }

    // --- system writes ------------------------------------------------------------------------------------------

    /**
     * Makes an accepted suggestion's [normalized] word active in [lang]: inserted as AUTO or SUGGESTION, a removed
     * word restored, an active word left alone. Runs inside the caller's READ COMMITTED transaction (the suggestion's
     * decision), publishes only when the catalog changed and returns whether it did.
     */
    internal fun acceptSuggestionInTransaction(
        lang: String,
        normalized: String,
        suggestionId: String,
        via: DecidedVia,
        actor: AuditActor,
    ): Boolean {
        val pack = publisher.lock(lang) ?: return false
        val existing = findRow(lang, normalized)
        when (existing?.status()) {
            WordStatus.ACTIVE -> return false
            WordStatus.REMOVED -> restoreRow(checkNotNull(existing), staffId = null, actor = actor, suggestionId = suggestionId)
            null -> {
                val source = if (via == DecidedVia.AUTO) WordSource.AUTO else WordSource.SUGGESTION
                insertWord(lang, normalized, source, staffId = null, actor = actor, suggestionId = suggestionId)
            }
        }
        publishChange(pack)
        return true
    }

    /**
     * One-time carry-over of the published packs into the catalog, for every language whose pack row exists and whose
     * catalog is still empty (that emptiness is the idempotency guard; one transaction per language, so an import is
     * never partial). Guesses and answers are normalized and deduplicated; a word matching an accepted suggestion keeps
     * that origin (AUTO or SUGGESTION, decided time), everything else is BUNDLED; words of the pack's answers are
     * daily-eligible. Words failing today's validation are still imported (staff can remove them) and counted. Nothing
     * is published: the packs already hold these words. Returns the number of imported words per language.
     */
    suspend fun carryOver(): Map<String, Int> {
        val imported = LinkedHashMap<String, Int>()
        for (lang in wordPacks.languages()) {
            val report = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                val pack = publisher.lock(lang) ?: return@dbQuery null
                if (WordsTable.select(WordsTable.id).where { WordsTable.lang eq lang }.limit(1).any()) return@dbQuery null
                importPack(pack)
            } ?: continue
            imported[lang] = report.count
            println(
                "Word catalog $lang: carried over ${report.count} words from pack version ${report.version} " +
                    "(${report.invalid} fail validation, ${report.collisions} spelling collisions, ${report.skipped} skipped)"
            )
        }
        return imported
    }

    /**
     * Adds every word of each language's deployed dictionaries (`<lang>_guess.txt`, `<lang>_answers.txt`) that the
     * catalog lacks, as BUNDLED and not daily-eligible, publishing once per language that gained words. A spelling the
     * catalog already holds in either status is skipped, so staff removals and respellings survive every restart;
     * invalid lines are skipped. Returns how many words each changed language gained.
     */
    suspend fun mergeBundled(): Map<String, Int> {
        val merged = LinkedHashMap<String, Int>()
        for (lang in wordPacks.languages()) {
            val bundled = wordPacks.bundledWords(lang)
            if (bundled.isEmpty()) continue
            val validator = validator(lang)
            val candidates = LinkedHashSet<String>()
            var invalid = 0
            for (line in bundled) {
                when (val result = validator.validate(line)) {
                    is WordValidator.Valid -> candidates += result.normalized
                    is WordValidator.Invalid -> invalid++
                }
            }
            if (invalid > 0) println("Word catalog $lang: skipped $invalid invalid bundled dictionary lines")

            val added = DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                val pack = publisher.lock(lang) ?: return@dbQuery 0
                val known = WordsTable.select(WordsTable.text).where { WordsTable.lang eq lang }.mapTo(HashSet()) { it[WordsTable.text] }
                val missing = candidates.filter { it !in known }
                if (missing.isEmpty()) return@dbQuery 0
                val now = clock.instant()
                WordsTable.batchInsert(missing, shouldReturnGeneratedValues = false) { text ->
                    this[WordsTable.id] = UUID.randomUUID().toString()
                    this[WordsTable.lang] = lang
                    this[WordsTable.text] = text
                    this[WordsTable.status] = WordStatus.ACTIVE.name
                    this[WordsTable.wordSource] = WordSource.BUNDLED.name
                    this[WordsTable.suggestionId] = null
                    this[WordsTable.dailyEligible] = false
                    this[WordsTable.createdByStaffId] = null
                    this[WordsTable.createdAt] = now
                    this[WordsTable.updatedByStaffId] = null
                    this[WordsTable.updatedAt] = now
                    this[WordsTable.removedByStaffId] = null
                    this[WordsTable.removedAt] = null
                }
                audit.record(
                    AuditActor.System, AuditActions.WORD_BUNDLED_MERGED, lang = lang,
                    details = auditDetails {
                        fact("count", jsonOf(missing.size.toLong()))
                        fact("words", JsonArray(missing.take(AUDITED_WORDS_MAX).map(::JsonPrimitive)))
                    },
                )
                publishChange(pack)
                missing.size
            }
            if (added > 0) merged[lang] = added
        }
        return merged
    }

    /**
     * Makes [normalized] an ACTIVE word of [lang] inside the caller's transaction (whose pack lock it must hold): an
     * active word is returned as is, a removed one is restored, a missing one is added as STAFF by [staffId]. The spelling
     * must already have passed [validator]. Publishes nothing: the caller does.
     */
    internal fun ensureActiveInTransaction(lang: String, normalized: String, staffId: String): String {
        val existing = findRow(lang, normalized)
        val actor = AuditActor.Staff(staffId)
        return when (existing?.status()) {
            WordStatus.ACTIVE -> existing[WordsTable.id]
            WordStatus.REMOVED -> restoreRow(existing, staffId, actor)
            null -> insertWord(lang, normalized, WordSource.STAFF, staffId, actor)
        }
    }

    /**
     * Publishes a catalog change of [pack]'s language in the same transaction: the language's calendar reacts first
     * (automatic days re-pick, a lost manual pick is replaced with an ADMIN notice), then every pack of that calendar is
     * rebuilt. The catalog response reflects none of it.
     */
    private fun publishChange(pack: LockedPack): String {
        val now = clock.instant()
        val calendar = DailyCalendars.calendarOf(pack.lang)
        if (calendar != null && calendars.initializedOn(calendar) != null) {
            calendars.reconcile(calendar, now)
            // An Uzbek change can alter the other script too: its pair answers and its schedule.
            for (sibling in DailyCalendars.packLanguages(calendar)) {
                if (sibling != pack.lang) publisher.lock(sibling)?.let { publisher.publish(it, now) }
            }
        }
        return publisher.publish(pack, now)
    }

    // --- transactions -------------------------------------------------------------------------------------------

    /** A staff write to [lang]: scope check, then the pack lock (404 without a pack), then [block]. */
    private suspend fun <T> writeLanguage(principal: StaffPrincipal, lang: String, block: (LockedPack) -> T): T =
        integrityRefusalsAsValidation {
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                principal.requireLanguage(lang)
                val pack = publisher.lock(lang) ?: throw AdminApiException.notFound("No word pack for $lang")
                block(pack)
            }
        }

    /** A staff write to word [id]: its language is resolved first, then scope, pack lock, and a fresh read of the row. */
    private suspend fun <T> writeWord(principal: StaffPrincipal, id: String, block: (LockedPack, ResultRow) -> T): T =
        integrityRefusalsAsValidation {
            DatabaseFactory.dbQuery(Connection.TRANSACTION_READ_COMMITTED) {
                val lang = WordsTable.select(WordsTable.lang).where { WordsTable.id eq id }.singleOrNull()?.get(WordsTable.lang)
                    ?: throw AdminApiException.notFound("No word $id")
                principal.requireLanguage(lang)
                val pack = publisher.lock(lang) ?: throw AdminApiException.notFound("No word pack for $lang")
                // Re-read under the language lock: a concurrent writer may have changed the row meanwhile.
                val row = WordsTable.selectAll().where { WordsTable.id eq id }.single()
                block(pack, row)
            }
        }

    private suspend fun <T> integrityRefusalsAsValidation(block: suspend () -> T): T =
        try {
            block()
        } catch (e: PackIntegrityException) {
            System.err.println(e.message)
            throw AdminApiException.validation("pack", WordReasons.PACK_INTEGRITY)
        }

    // --- row helpers (inside a transaction) ---------------------------------------------------------------------

    private fun requirePackExists(lang: String) {
        val exists = WordPacksTable.select(WordPacksTable.lang).where { WordPacksTable.lang eq lang }.any()
        if (!exists) throw AdminApiException.notFound("No word pack for $lang")
    }

    private fun requireValid(lang: String, text: String): WordValidator.Valid =
        when (val result = validator(lang).validate(text)) {
            is WordValidator.Valid -> result
            is WordValidator.Invalid -> throw AdminApiException.validation("text", result.reason)
        }

    private fun findRow(lang: String, normalized: String): ResultRow? =
        WordsTable.selectAll().where { (WordsTable.lang eq lang) and (WordsTable.text eq normalized) }.singleOrNull()

    private fun findRows(lang: String, texts: List<String>): Map<String, ResultRow> =
        texts.chunked(IN_LIST_CHUNK).flatMap { chunk ->
            WordsTable.selectAll().where { (WordsTable.lang eq lang) and (WordsTable.text inList chunk) }.toList()
        }.associateBy { it[WordsTable.text] }

    private fun ResultRow.status(): WordStatus = WordStatus.valueOf(this[WordsTable.status])

    private fun insertWord(
        lang: String,
        text: String,
        source: WordSource,
        staffId: String?,
        actor: AuditActor,
        suggestionId: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = clock.instant()
        WordsTable.insert {
            it[WordsTable.id] = id
            it[WordsTable.lang] = lang
            it[WordsTable.text] = text
            it[status] = WordStatus.ACTIVE.name
            it[WordsTable.wordSource] = source.name
            it[WordsTable.suggestionId] = suggestionId
            it[dailyEligible] = false
            it[createdByStaffId] = staffId
            it[createdAt] = now
            it[updatedByStaffId] = staffId
            it[updatedAt] = now
            it[removedByStaffId] = null
            it[removedAt] = null
        }
        audit.record(
            actor, AuditActions.WORD_ADDED, AuditTargets.WORD, id, lang,
            auditDetails {
                fact("text", jsonOf(text))
                fact("source", jsonOf(source.name))
            },
        )
        return id
    }

    /**
     * Makes a removed row active again. [suggestionId] names the accepted suggestion that restored it, so the audit entry
     * tells a staff restore from a decision's (analytics counts only the first as a staff member's added word).
     */
    private fun restoreRow(row: ResultRow, staffId: String?, actor: AuditActor, suggestionId: String? = null): String {
        val id = row[WordsTable.id]
        WordsTable.update({ WordsTable.id eq id }) {
            it[status] = WordStatus.ACTIVE.name
            it[removedByStaffId] = null
            it[removedAt] = null
            it[updatedByStaffId] = staffId
            it[updatedAt] = clock.instant()
        }
        audit.record(
            actor, AuditActions.WORD_RESTORED, AuditTargets.WORD, id, row[WordsTable.lang],
            auditDetails {
                fact("text", jsonOf(row[WordsTable.text]))
                if (suggestionId != null) fact("suggestion", jsonOf(suggestionId))
            },
        )
        return id
    }

    private fun loadDto(id: String): WordDto =
        toDtos(listOf(WordsTable.selectAll().where { WordsTable.id eq id }.single())).single()

    private fun toDtos(rows: List<ResultRow>): List<WordDto> {
        val staffIds = rows.flatMap {
            listOfNotNull(it[WordsTable.createdByStaffId], it[WordsTable.updatedByStaffId], it[WordsTable.removedByStaffId])
        }.toSet()
        val staff = if (staffIds.isEmpty()) emptyMap() else {
            StaffTable.select(StaffTable.id, StaffTable.username, StaffTable.displayName)
                .where { StaffTable.id inList staffIds }
                .associate { it[StaffTable.id] to StaffRefDto(it[StaffTable.id], it[StaffTable.username], it[StaffTable.displayName]) }
        }
        val tokenizers = rows.map { it[WordsTable.lang] }.distinct().associateWith { registry.tokenizer(it) }
        return rows.map { row ->
            WordDto(
                id = row[WordsTable.id],
                lang = row[WordsTable.lang],
                text = row[WordsTable.text],
                graphemeCount = tokenizers[row[WordsTable.lang]]?.tokenize(row[WordsTable.text])?.size,
                status = row.status(),
                source = WordSource.valueOf(row[WordsTable.wordSource]),
                suggestionId = row[WordsTable.suggestionId],
                createdBy = row[WordsTable.createdByStaffId]?.let(staff::get),
                createdAt = row[WordsTable.createdAt].toEpochMilli(),
                updatedBy = row[WordsTable.updatedByStaffId]?.let(staff::get),
                updatedAt = row[WordsTable.updatedAt].toEpochMilli(),
                removedBy = row[WordsTable.removedByStaffId]?.let(staff::get),
                removedAt = row[WordsTable.removedAt]?.toEpochMilli(),
            )
        }
    }

    private fun conditionFor(query: WordQuery): Op<Boolean> {
        val conditions = listOfNotNull(
            WordsTable.lang eq query.lang,
            query.status?.let { WordsTable.status eq it.name },
            query.source?.let { WordsTable.wordSource eq it.name },
            query.addedBy?.let { WordsTable.createdByStaffId eq it },
            query.addedFrom?.let { WordsTable.createdAt greaterEq it },
            query.addedTo?.let { WordsTable.createdAt less it },
            query.q?.let { normalize(query.lang, it) }?.takeIf { it.isNotEmpty() }?.let { q ->
                WordsTable.text like LikePattern("%" + escapeLike(q) + "%", LIKE_ESCAPE)
            },
        )
        return conditions.reduce { acc, op -> acc and op }
    }

    private class ImportReport(val count: Int, val invalid: Int, val collisions: Int, val skipped: Int, val version: String)

    private fun importPack(pack: LockedPack): ImportReport {
        val lang = pack.lang
        val json = Json
        val answers: List<String> = json.decodeFromString(pack.answersJson)
        val guesses: List<String> = json.decodeFromString(pack.guessesJson)
        val answerTexts = answers.mapTo(HashSet()) { normalize(lang, it) }

        // normalized spelling -> the first raw spelling seen for it
        val spellings = LinkedHashMap<String, String>()
        var collisions = 0
        var skipped = 0
        for (raw in guesses + answers) {
            val text = normalize(lang, raw)
            if (text.isEmpty() || text.length > TEXT_MAX) {
                skipped++
                continue
            }
            val first = spellings.putIfAbsent(text, raw)
            if (first != null && first != raw) collisions++
        }

        // Accepted suggestions, earliest decision first, keep their origin.
        val accepted = HashMap<String, ResultRow>()
        WordSuggestionsTable.selectAll()
            .where { (WordSuggestionsTable.lang eq lang) and (WordSuggestionsTable.status eq SuggestionStatus.ACCEPTED.name) }
            .orderBy(WordSuggestionsTable.decidedAt, SortOrder.ASC)
            .forEach { row -> accepted.putIfAbsent(normalize(lang, row[WordSuggestionsTable.word]), row) }

        val validator = validator(lang)
        val invalid = spellings.keys.count { validator.validate(it) !is WordValidator.Valid }
        val now = clock.instant()
        WordsTable.batchInsert(spellings.keys, shouldReturnGeneratedValues = false) { text ->
            val suggestion = accepted[text]
            val decidedAt: Instant? = suggestion?.get(WordSuggestionsTable.decidedAt)
            this[WordsTable.id] = UUID.randomUUID().toString()
            this[WordsTable.lang] = lang
            this[WordsTable.text] = text
            this[WordsTable.status] = WordStatus.ACTIVE.name
            this[WordsTable.wordSource] = when {
                suggestion == null -> WordSource.BUNDLED.name
                suggestion[WordSuggestionsTable.decidedVia] == DecidedVia.AUTO.name -> WordSource.AUTO.name
                else -> WordSource.SUGGESTION.name
            }
            this[WordsTable.suggestionId] = suggestion?.get(WordSuggestionsTable.id)
            this[WordsTable.dailyEligible] = text in answerTexts
            this[WordsTable.createdByStaffId] = null
            this[WordsTable.createdAt] = decidedAt ?: now
            this[WordsTable.updatedByStaffId] = null
            this[WordsTable.updatedAt] = decidedAt ?: now
            this[WordsTable.removedByStaffId] = null
            this[WordsTable.removedAt] = null
        }
        audit.record(
            AuditActor.System, AuditActions.WORD_CATALOG_IMPORTED, lang = lang,
            details = auditDetails {
                fact("count", jsonOf(spellings.size.toLong()))
                fact("invalid", jsonOf(invalid.toLong()))
                fact("packVersion", jsonOf(pack.version))
            },
        )
        return ImportReport(spellings.size, invalid, collisions, skipped, pack.version)
    }

    private companion object {
        const val IN_LIST_CHUNK = 500
        const val TEXT_MAX = 64

        /** A merge audit entry lists at most this many of the added words, plus the count. */
        const val AUDITED_WORDS_MAX = 200
        const val LIKE_ESCAPE = '\\'

        fun escapeLike(value: String): String =
            value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
