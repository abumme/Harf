package uz.abumme.harfgame.backend.admin.words

import uz.abumme.harfgame.lang.LanguageRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * The per-language blocklists (`resources/blocklists/<lang>_block.txt`), loaded once per language and normalized with
 * the language's rules, so "Ё"/"ё" or an apostrophe variant of a blocked word is blocked too. Shared by suggestions and
 * the word catalog. Blocklists are files: a change needs a deploy.
 */
class Blocklists(
    private val registry: LanguageRegistry = LanguageRegistry(),
    /** Raw lines of a language's blocklist; the classpath resources by default. */
    private val lines: (lang: String) -> List<String> = ::bundledBlocklistLines,
) {
    private val cache = ConcurrentHashMap<String, Set<String>>()

    /** [lang]'s blocked words in normalized form. */
    fun of(lang: String): Set<String> = cache.getOrPut(lang) {
        val tokenizer = registry.tokenizer(lang)
        lines(lang).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapTo(HashSet()) { word -> tokenizer?.normalize(word)?.trim() ?: word.lowercase() }
    }

    /** Whether [word] (raw or normalized) is blocked in [lang]. */
    fun isBlocked(lang: String, word: String): Boolean {
        val normalized = registry.tokenizer(lang)?.normalize(word.trim())?.trim() ?: word.trim().lowercase()
        return normalized in of(lang)
    }

    companion object {
        /** The deployed blocklists, shared by every service in the process. */
        val default: Blocklists by lazy { Blocklists() }
    }
}

private fun bundledBlocklistLines(lang: String): List<String> {
    val stream = Blocklists::class.java.getResourceAsStream("/blocklists/${lang}_block.txt") ?: return emptyList()
    return stream.bufferedReader().useLines { it.toList() }
}
