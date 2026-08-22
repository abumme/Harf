package uz.abumme.harfgame.di

import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.engine.WordPackRepository
import uz.abumme.harfgame.lang.LanguageRegistry

/** Tile-engine bindings: language configs + word packs. */
val languageModule: Module = module {
    single { LanguageRegistry() }
    single { WordPackRepository(get()) }
}
