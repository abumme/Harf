package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines

/** Word classes that inflect differently; everything else (adverbs, particles, interjections, …) doesn't inflect. */
enum class UzbekPos { NOUN, ADJECTIVE, NUMERAL, PRONOUN, VERB, OTHER }

/** A dictionary form in canonical Uzbek Latin spelling; verbs are stems without -moq (kel, ishla). */
data class UzbekLemma(val word: String, val pos: UzbekPos)

/**
 * Reads UzbekLemmaStems-POS-Dataset (github.com/MaksudSharipov/UzbekLemmaStems-POS-Dataset, Apache-2.0): one CSV per
 * part of speech, each row a stem and the quoted, comma-separated lemmas built on it.
 */
object UzbekLemmas {
    private val latin = PlayableWordFilter(LaunchLanguages.uzLatn, lengths = (1..64).toSet())

    private val posByFile = mapOf(
        "Nouns" to UzbekPos.NOUN,
        "Adjectives" to UzbekPos.ADJECTIVE,
        "Numerals" to UzbekPos.NUMERAL,
        "Pronouns" to UzbekPos.PRONOUN,
        "Verbs" to UzbekPos.VERB,
    )

    fun read(csvDir: Path): List<UzbekLemma> =
        csvDir.listDirectoryEntries("*.csv").sorted().flatMap { file ->
            val pos = posByFile[file.nameWithoutExtension] ?: UzbekPos.OTHER
            file.readLines().flatMap { line -> lemmaSpellings(line.removePrefix("﻿")) }
                .mapNotNull { spelling -> word(spelling)?.let { UzbekLemma(it, pos) } }
        }.distinct()

    /** The second CSV field, unquoted and split: `bo‘l,"bo‘l, bo‘ldir"` → [bo‘l, bo‘ldir]. */
    private fun lemmaSpellings(line: String): List<String> {
        val comma = line.indexOf(',')
        if (comma < 0) return emptyList()
        return line.substring(comma + 1).trim().removeSurrounding("\"").split(',').map { it.trim() }
    }

    /**
     * Canonical spelling, or null when it can't be typed: slashes mark morpheme boundaries (chaq/moq is chaqmoq),
     * while hyphenated compounds and letters outside the board's alphabet (the glottal stop in ta'lim) can't be played.
     */
    private fun word(spelling: String): String? {
        val joined = spelling.replace("/", "")
        if (joined.isEmpty() || joined.any { it == '-' || it.isWhitespace() }) return null
        return latin.playable(joined)
    }
}
