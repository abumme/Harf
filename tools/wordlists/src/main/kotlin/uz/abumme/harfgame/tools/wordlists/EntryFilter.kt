package uz.abumme.harfgame.tools.wordlists

/**
 * Which raw spellings a dictionary entry contributes to a guess list: its headword and inflected forms, or nothing
 * when the entry isn't an ordinary word (design.md, "Selection rules"). Spellings are raw; [PlayableWordFilter]
 * normalizes them and applies the language's alphabet and board length.
 */
object EntryFilter {
    private val nonWordPos = setOf(
        "name", "character", "prefix", "suffix", "infix", "interfix", "phrase", "proverb", "symbol", "punct",
        "abbrev", "romanization", "combining_form",
    )

    /** On the entry or on any sense, this means the spelling isn't a word to type. */
    private const val MISSPELLING = "misspelling"

    /** These drop a word only on the entry itself or on every sense: общий stays although one sense is a clipping. */
    private val abbreviation = setOf("abbreviation", "acronym", "initialism")

    /** These drop a word only on the entry itself or on every sense: one rude figurative sense doesn't. */
    private val rude = setOf("vulgar", "offensive", "derogatory", "slur")

    /** Rows of the inflection table that aren't spellings of the word. */
    private val nonWordForms = setOf("romanization", "table-tags", "inflection-template", "class")

    fun candidates(entry: KaikkiEntry): List<String> {
        if (entry.pos in nonWordPos) return emptyList()
        if ((listOf(entry.tags) + entry.senseTags).any { tags -> MISSPELLING in tags }) return emptyList()
        if (entry.onlyTagged(abbreviation) || entry.onlyTagged(rude)) return emptyList()
        val spellings = listOf(entry.word) + entry.forms.filter { form -> form.tags.none(nonWordForms::contains) }.map { it.form }
        // Wiktionary capitalizes proper nouns and acronyms without always tagging them (the plural entry MTOCs),
        // so only spellings already written in lowercase are ordinary words.
        return spellings.filter { spelling -> spelling == spelling.lowercase() }
    }

    /** True when one of [labels] tags the entry itself or every one of its senses. */
    private fun KaikkiEntry.onlyTagged(labels: Set<String>): Boolean =
        tags.any(labels::contains) || (senseTags.isNotEmpty() && senseTags.all { sense -> sense.any(labels::contains) })
}
