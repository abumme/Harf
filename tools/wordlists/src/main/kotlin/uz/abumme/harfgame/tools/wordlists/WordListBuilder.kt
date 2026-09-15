package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.lang.LaunchLanguages
import java.nio.file.Path
import kotlin.io.path.exists

/** The pipeline for one kaikki.org dump: read entries → select words → keep playable spellings → write the pack files. */
object WordListBuilder {

    /** Dump language → kaikki.org language name. `uz` builds both Uzbek scripts from one dump. */
    val dumpLanguages: Map<String, String> = linkedMapOf("en" to "English", "ru" to "Russian", "kk" to "Kazakh", "uz" to "Uzbek")

    fun build(dumpLanguage: String, dumpsDir: Path, writer: GuessListWriter): List<GuessListReport> {
        val name = requireNotNull(dumpLanguages[dumpLanguage]) {
            "unknown dump language '$dumpLanguage'; expected one of ${dumpLanguages.keys}"
        }
        val dump = dumpsDir.resolve("kaikki.org-dictionary-$name.jsonl")
        require(dump.exists()) { "missing dump $dump: download https://kaikki.org/dictionary/$name/kaikki.org-dictionary-$name.jsonl" }

        if (dumpLanguage == "uz") {
            // Cyrillic needs the raw spellings: tile counts are recomputed after transliteration.
            val raw = KaikkiReader.useEntries(dump) { entries -> entries.flatMap { EntryFilter.candidates(it) }.toList() }
            val latin = PlayableWordFilter(LaunchLanguages.uzLatn)
            return listOf(
                writer.write("uz-latn", raw.mapNotNullTo(HashSet()) { latin.playable(it) }),
                writer.write("uz-cyrl", UzbekCyrillic.words(raw)),
            )
        }
        val filter = PlayableWordFilter(LaunchLanguages.all.getValue(dumpLanguage))
        // Streams: only playable words are held in memory, so multi-GB dumps stay small.
        val words = KaikkiReader.useEntries(dump) { entries ->
            entries.flatMap { EntryFilter.candidates(it) }
                .mapNotNull { filter.playable(it) }
                .filterTo(HashSet()) { looksLikeAWord(dumpLanguage, it) }
        }
        return listOf(writer.write(dumpLanguage, words))
    }

    /**
     * Language-specific spellings the dictionary tags don't catch: pre-1918 Russian orthography ends words in a hard
     * sign (кормъ), and English abbreviation plurals and sound effects have no vowel (bldgs, brrrm; y counts, as in lynch).
     */
    private fun looksLikeAWord(lang: String, word: String): Boolean = when (lang) {
        "ru" -> !word.endsWith("ъ")
        "en" -> word.any { it in "aeiouy" }
        else -> true
    }
}
