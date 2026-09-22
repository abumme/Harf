package uz.abumme.harfgame.backend.service

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.WordPacksTable
import uz.abumme.harfgame.data.wordpack.WordPackDto
import uz.abumme.harfgame.data.wordpack.WordPackSchedule

/**
 * Serves and seeds versioned word packs. Version 1 is seeded from maintained vocab files in
 * `resources/wordpacks/`, with a deterministic no-adjacent-repeat schedule from the shared
 * [WordPackSchedule] generator — so a fresh offline client (which runs the same generator on the
 * bundled vocab) agrees with a client synced to version 1. Future versions only append to the
 * schedule, keeping past days immutable.
 *
 * After seeding, a pack is the published snapshot of the word catalog: every later version is written by
 * [uz.abumme.harfgame.backend.admin.words.PackPublisher], never by appending to the stored lists.
 */
class WordPackServerService(
    private val anchorDay: Long = WordPackSchedule.ANCHOR_EPOCH_DAY,
    /** Raw lines of a bundled word-pack file by name; the resources on the classpath by default. */
    private val resourceLines: (name: String) -> List<String> = ::bundledWordPackLines,
) {
    private val json = Json

    suspend fun getPack(lang: String): WordPackDto? = DatabaseFactory.dbQuery {
        WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.singleOrNull()?.let { row ->
            WordPackDto(
                lang = row[WordPacksTable.lang],
                version = row[WordPacksTable.version],
                effectiveFrom = row[WordPacksTable.effectiveFrom],
                anchorEpochDay = row[WordPacksTable.anchorEpochDay],
                answers = json.decodeFromString(row[WordPacksTable.answers]),
                guesses = json.decodeFromString(row[WordPacksTable.guesses]),
                schedule = json.decodeFromString(row[WordPacksTable.schedule]),
                firstPublicEpochDay = row[WordPacksTable.firstPublicEpochDay],
            )
        }
    }

    /** Languages that have a word pack, alphabetically. */
    suspend fun languages(): List<String> = DatabaseFactory.dbQuery {
        WordPacksTable.select(WordPacksTable.lang).orderBy(WordPacksTable.lang).map { it[WordPacksTable.lang] }
    }

    /**
     * The words of [lang]'s deployed dictionaries (`<lang>_guess.txt`, then `<lang>_answers.txt`), trimmed, without
     * blank lines or `#` comments. The word catalog merges them at startup.
     */
    fun bundledWords(lang: String): List<String> = readLines("${lang}_guess.txt") + readLines("${lang}_answers.txt")

    /** The deployed Uzbek lexemes (`uz_lexemes.tsv`): a Latin and a Cyrillic spelling of one word each. */
    fun bundledLexemes(): List<Pair<String, String>> =
        readLines("uz_lexemes.tsv").map { it.split('\t') }.filter { it.size == 2 }.map { it[0].trim() to it[1].trim() }

    /** Insert version 1 for every language if absent. Idempotent: existing rows are left untouched. */
    suspend fun seed() {
        for (lang in listOf("en", "ru", "kk")) {
            val answers = readLines("${lang}_answers.txt")
            if (answers.isEmpty()) continue
            val schedule = WordPackSchedule.build(answers, WordPackSchedule.seedFor(lang))
            insertIfAbsent(lang, answers, readLines("${lang}_guess.txt"), schedule)
        }

        // Uzbek: one lexeme order materialized into both scripts so a day is the same lexeme in each.
        val pairs = readLines("uz_lexemes.tsv").map { it.split('\t') }.filter { it.size == 2 }
        if (pairs.isNotEmpty()) {
            val order = WordPackSchedule.buildOrder(pairs.size, WordPackSchedule.seedFor("uz"))
            insertIfAbsent("uz-latn", pairs.map { it[0] }, readLines("uz-latn_guess.txt"), order.map { pairs[it][0] })
            insertIfAbsent("uz-cyrl", pairs.map { it[1] }, readLines("uz-cyrl_guess.txt"), order.map { pairs[it][1] })
        }
    }

    private suspend fun insertIfAbsent(lang: String, answers: List<String>, guessesRaw: List<String>, schedule: List<String>) {
        DatabaseFactory.dbQuery {
            if (WordPacksTable.selectAll().where { WordPacksTable.lang eq lang }.any()) return@dbQuery
            val guesses = (guessesRaw + answers).distinct() // answers are always submittable guesses
            WordPacksTable.insert {
                it[WordPacksTable.lang] = lang
                it[WordPacksTable.version] = "1"
                it[WordPacksTable.effectiveFrom] = anchorDay
                it[WordPacksTable.anchorEpochDay] = anchorDay
                it[WordPacksTable.answers] = json.encodeToString(answers)
                it[WordPacksTable.guesses] = json.encodeToString(guesses)
                it[WordPacksTable.schedule] = json.encodeToString(schedule)
                it[WordPacksTable.updatedAt] = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
            }
        }
    }

    private fun readLines(name: String): List<String> =
        resourceLines(name).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
}

/** Raw lines of a bundled word-pack resource (`resources/wordpacks/<name>`); empty when the file is absent. */
private fun bundledWordPackLines(name: String): List<String> {
    val stream = WordPackServerService::class.java.getResourceAsStream("/wordpacks/$name") ?: return emptyList()
    return stream.bufferedReader().useLines { it.toList() }
}
