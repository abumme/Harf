package uz.abumme.harfgame.feature.cellstyles

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

enum class StyleChoiceSource { Experiment, Settings }

@Serializable
data class StyleChoice(val styleId: String, val source: String, val at: Long)

@Serializable
data class StyleChoiceLogData(val version: Int = 1, val choices: List<StyleChoice> = emptyList())

/** Local, offline record of every mark-style selection for later analysis. No network. */
class StyleChoiceLog(private val ksafe: KSafe) {
    private val mutex = Mutex()

    suspend fun all(): List<StyleChoice> = ksafe.get(KEY, StyleChoiceLogData()).choices

    suspend fun record(styleId: String, source: StyleChoiceSource, at: Long) = mutex.withLock {
        val data = ksafe.get(KEY, StyleChoiceLogData())
        ksafe.put(KEY, data.copy(choices = data.choices + StyleChoice(styleId, source.name, at)))
    }

    companion object {
        private const val KEY = "cellStyles.choiceLog"
    }
}
