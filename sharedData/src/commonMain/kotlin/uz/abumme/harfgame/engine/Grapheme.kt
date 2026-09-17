package uz.abumme.harfgame.engine

/** One linguistic letter as a native speaker counts it (may be multiple characters, e.g. "sh"). */
typealias Grapheme = String

/** A word decomposed into ordered graphemes for a specific language. */
data class Word(
    val languageId: String,
    val graphemes: List<Grapheme>,
) {
    val length: Int get() = graphemes.size
}
