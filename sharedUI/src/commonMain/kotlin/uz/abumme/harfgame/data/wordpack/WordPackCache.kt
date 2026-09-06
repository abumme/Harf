package uz.abumme.harfgame.data.wordpack

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.serialization.Serializable

/** Durable per-language cache of the last valid fetched word pack. Survives restart via KSafe. */
class WordPackCache(private val ksafe: KSafe) {

    suspend fun get(lang: String): WordPackDto? = ksafe.get(key(lang), Holder()).pack

    suspend fun put(pack: WordPackDto) = ksafe.put(key(pack.lang), Holder(pack))

    private fun key(lang: String) = "$KEY.$lang"

    @Serializable
    private data class Holder(val pack: WordPackDto? = null)

    companion object {
        private const val KEY = "wordpack.cache"
    }
}
