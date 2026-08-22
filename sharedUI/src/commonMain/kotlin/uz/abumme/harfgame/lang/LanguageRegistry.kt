package uz.abumme.harfgame.lang

import uz.abumme.harfgame.engine.Tokenizer

/** Central access to language configs and their tokenizers. */
class LanguageRegistry(
    private val configs: Map<String, LanguageConfig> = LaunchLanguages.all,
) {
    val ids: Set<String> get() = configs.keys

    fun config(id: String): LanguageConfig? = configs[id]

    fun tokenizer(id: String): Tokenizer? = config(id)?.let { Tokenizer(it) }
}
