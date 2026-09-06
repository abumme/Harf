package uz.abumme.harfgame.backend.service

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
 */
class WordPackServerService(
    private val anchorDay: Long = WordPackSchedule.ANCHOR_EPOCH_DAY,
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
            )
        }
    }

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

    private fun readLines(name: String): List<String> {
        val stream = javaClass.getResourceAsStream("/wordpacks/$name") ?: return emptyList()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
    }
}
