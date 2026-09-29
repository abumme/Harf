package uz.abumme.harfgame.tools.wordlists

import uz.abumme.harfgame.engine.Tokenizer
import uz.abumme.harfgame.lang.LaunchLanguages

/**
 * Regular Uzbek Latin word forms of a lemma: plural, possessive and case for nominals; tense, person, mood, negation,
 * converbs and participles for verb stems. It over-generates on purpose (both sides of a stem alternation, particles
 * on any form); the builder keeps only forms a corpus attests, so a wrong guess here costs nothing.
 */
object UzbekInflection {
    private val tokenizer = Tokenizer(LaunchLanguages.uzLatn)
    private val vowels = setOf("a", "e", "i", "o", "u", "oʻ")

    /** Case endings and particles that attach to a (possessed) nominal. The dative family varies after k and q. */
    private val caseEndings = listOf(
        "ning", "ni", "da", "dan", "dagi", "dek", "day", "cha", "li", "lik", "siz", "chi", "man", "san", "miz", "dir", "mi",
    )
    private val consonantPossessives = listOf("im", "ing", "i", "imiz", "ingiz")
    private val vowelPossessives = listOf("m", "ng", "si", "miz", "ngiz")
    private val personEndings = listOf("man", "san", "di", "miz", "siz", "dilar")
    private val pastEndings = listOf("", "m", "ng", "k", "ngiz", "lar")

    fun forms(lemma: String, pos: UzbekPos, maxTiles: Int = 5): Set<String> {
        val tiles = tokenizer.tokenize(lemma) ?: return emptySet()
        if (tiles.size > maxTiles) return emptySet()
        val raw = when (pos) {
            UzbekPos.NOUN, UzbekPos.PRONOUN -> nominal(lemma)
            UzbekPos.ADJECTIVE -> nominal(lemma) + nominal(lemma + "roq")
            UzbekPos.NUMERAL -> nominal(lemma) + nominal(lemma + if (endsInVowel(lemma)) "nchi" else "inchi")
            UzbekPos.VERB -> verb(lemma)
            UzbekPos.OTHER -> setOf(lemma)
        }
        return raw.filterTo(HashSet()) { form -> tokenizer.tokenize(form)?.let { it.size <= maxTiles } == true }
    }

    private fun nominal(stem: String): Set<String> {
        val possessed = LinkedHashSet<String>()
        for (base in listOf(stem, stem + "lar")) {
            possessed += base
            if (endsInVowel(base)) {
                vowelPossessives.forEach { possessed += base + it }
            } else {
                for (alternant in possessiveStems(base)) consonantPossessives.forEach { possessed += alternant + it }
            }
        }
        val forms = LinkedHashSet<String>()
        for (word in possessed) {
            forms += word
            caseEndings.forEach { forms += word + it }
            val g = velar(word)
            forms += word + g + "a" // dative: uyga, tilakka, qishloqqa
            forms += word + g + "acha"
            forms += word + g + "ina"
        }
        return forms
    }

    /**
     * Stems a vowel-initial possessive attaches to: the stem itself, final k/q voiced (yurak → yuragi, tuproq →
     * tuprogʻi), and the last narrow vowel dropped (ogʻiz → ogʻzi, burun → burni).
     */
    private fun possessiveStems(stem: String): Set<String> {
        val tiles = tokenizer.tokenize(stem) ?: return setOf(stem)
        val stems = linkedSetOf(stem)
        when (tiles.last()) {
            "k" -> stems += tiles.dropLast(1).joinToString("") + "g"
            "q" -> stems += tiles.dropLast(1).joinToString("") + "gʻ"
        }
        val n = tiles.size
        if (n >= 4 && tiles[n - 2] in setOf("i", "u", "a") && tiles[n - 1] !in vowels && tiles[n - 3] !in vowels &&
            tiles.dropLast(2).any { it in vowels }
        ) {
            stems += (tiles.subList(0, n - 2) + tiles[n - 1]).joinToString("")
        }
        return stems
    }

    private fun verb(stem: String): Set<String> {
        val vowelFinal = endsInVowel(stem)
        val g = velar(stem)
        val forms = LinkedHashSet<String>()
        forms += stem // imperative
        forms += stem + "moq"
        forms += stem + "moqda"
        forms += stem + "moqchi"
        forms += nominal(stem + if (vowelFinal) "sh" else "ish") // verbal noun: kelish, kelishi, ishlashga
        forms += nominal(stem + if (vowelFinal) "v" else "uv")
        forms += nominal(stem + g + "an") // participle: kelgan, kelganda, chiqqan
        forms += nominal(stem + "yotgan")
        forms += stem + (if (vowelFinal) "b" else "ib")
        forms += stem + (if (vowelFinal) "bdi" else "ibdi")
        forms += stem + g + "ach"
        forms += stem + g + "uncha"
        forms += stem + g + "ani"
        forms += stem + g + "in" // emphatic imperative
        forms += stem + "sin"
        forms += stem + (if (vowelFinal) "ng" else "ing")
        forms += stem + (if (vowelFinal) "r" else "ar")
        val present = stem + if (vowelFinal) "y" else "a" // kela, ishlay
        forms += present
        forms += present + "in"
        forms += present + "lik"
        personEndings.forEach { forms += present + it }
        forms += nominal(present + "digan")
        listOf("man", "san", "ti", "miz", "siz").forEach { forms += stem + "yap" + it } // kelyapti, ishlayapti
        pastEndings.forEach { forms += stem + "di" + it }
        pastEndings.forEach { forms += stem + "sa" + it }
        val negative = stem + "ma"
        forms += negative
        forms += negative + "ng"
        forms += negative + "s"
        forms += negative + "sin"
        forms += negative + "sdan"
        forms += negative + "y"
        pastEndings.forEach { forms += negative + "di" + it }
        pastEndings.forEach { forms += negative + "sa" + it }
        personEndings.forEach { forms += negative + "y" + it } // kelmaydi
        forms += nominal(negative + "gan")
        return forms
    }

    private fun endsInVowel(word: String): Boolean = tokenizer.tokenize(word)?.lastOrNull() in vowels

    /** The consonant opening -ga/-gan/-gach: k after k, q after q, g otherwise (tilakka, chiqqan, kelgan). */
    private fun velar(word: String): String = when (tokenizer.tokenize(word)?.lastOrNull()) {
        "k" -> "k"
        "q" -> "q"
        else -> "g"
    }
}
