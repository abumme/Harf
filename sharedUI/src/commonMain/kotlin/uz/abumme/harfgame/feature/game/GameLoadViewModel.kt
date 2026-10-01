package uz.abumme.harfgame.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.abumme.harfgame.data.stats.InProgressRound
import uz.abumme.harfgame.engine.WordPack
import uz.abumme.harfgame.feature.daily.DailyPuzzle
import uz.abumme.harfgame.lang.LanguageConfig

/** Everything a round needs before its [GameViewModel] can be built. */
internal data class Loaded(
    val puzzle: DailyPuzzle,
    val config: LanguageConfig,
    val pack: WordPack,
    val restore: InProgressRound?,
    /** Which [GameLoadViewModel.select] produced it: a round's [GameViewModel] is keyed by this, not by script alone. */
    val generation: Int = 0,
)

/**
 * Loads the game's round outside composition and keeps it for as long as the Game entry is in the back stack, so a
 * return from the paywall finds it ready instead of drawing an empty page again.
 */
internal class GameLoadViewModel(private val load: suspend (script: String) -> Loaded) : ViewModel() {
    private val _selected = MutableStateFlow<String?>(null)

    /** The script the player asked for; [loaded] catches up with it once its round is ready. */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _loaded = MutableStateFlow<Loaded?>(null)
    val loaded: StateFlow<Loaded?> = _loaded.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    /** Loads [script]'s round, keeping the current one published until it is ready. Selecting it again is a no-op. */
    fun select(script: String) {
        if (_selected.value == script) return
        _selected.value = script
        job?.cancel()
        val round = ++generation
        job = viewModelScope.launch { _loaded.value = load(script).copy(generation = round) }
    }
}
