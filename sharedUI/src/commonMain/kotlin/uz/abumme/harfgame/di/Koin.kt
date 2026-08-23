package uz.abumme.harfgame.di

import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.settings.AppSettings

/** Platform-provided bindings (KSafe needs a Context on Android, nothing elsewhere). */
expect val platformModule: Module

/** Shared, platform-agnostic bindings. */
val appModule: Module = module {
    single { AppSettings(get()) }
    single { ResultLog(get()) }
    single { RoundStore(get()) }
}

/**
 * Initialize DI. Each platform entry point calls this once before the first screen.
 * Idempotent: a second call is a no-op. Android passes `androidContext(...)`
 * via [appDeclaration].
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        appDeclaration()
        modules(platformModule, appModule, languageModule)
    }
}
