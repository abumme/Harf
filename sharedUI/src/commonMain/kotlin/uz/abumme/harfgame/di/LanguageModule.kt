package uz.abumme.harfgame.di

import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.feature.daily.DailyPuzzleProvider
import uz.abumme.harfgame.lang.LanguageRegistry

/** Tile-engine + daily-puzzle bindings: language configs, word packs, daily selection. */
val languageModule: Module = module {
    single { LanguageRegistry() }
    single { WordPackRepository(get(), get()) }
    // The pack sync (appModule) lets a fresh install wait briefly for the published calendar; absent, no wait.
    single { DailyPuzzleProvider(get(), get(), getOrNull<WordPackSyncManager>()) }
}
