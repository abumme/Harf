package uz.abumme.harfgame.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import uz.abumme.harfgame.BuildConfig
import uz.abumme.harfgame.billing.EntitlementRepository
import uz.abumme.harfgame.data.archive.ArchiveHistoryManager
import uz.abumme.harfgame.data.archive.ArchiveRoundStore
import uz.abumme.harfgame.data.auth.SessionStore
import uz.abumme.harfgame.data.network.KtorArchiveService
import uz.abumme.harfgame.data.network.KtorAuthService
import uz.abumme.harfgame.data.network.KtorSuggestionService
import uz.abumme.harfgame.data.network.KtorSyncService
import uz.abumme.harfgame.data.service.ArchiveService
import uz.abumme.harfgame.data.service.AuthService
import uz.abumme.harfgame.data.service.SuggestionService
import uz.abumme.harfgame.data.service.SyncService
import uz.abumme.harfgame.data.stats.PendingUploadStore
import uz.abumme.harfgame.data.stats.ResultLog
import uz.abumme.harfgame.data.stats.RoundStore
import uz.abumme.harfgame.data.stats.SyncManager
import uz.abumme.harfgame.data.wordpack.WordPackCache
import uz.abumme.harfgame.data.wordpack.WordPackSyncManager
import uz.abumme.harfgame.feature.cellstyles.StyleChoiceLog
import uz.abumme.harfgame.feature.cellstyles.StyleExperimentController
import uz.abumme.harfgame.settings.AppSettings

/** Platform-provided bindings (KSafe needs a Context on Android, nothing elsewhere). */
expect val platformModule: Module

/** Shared, platform-agnostic bindings. */
val appModule: Module = module {
    single { AppSettings(get()) }
    single { ResultLog(get()) }
    single { RoundStore(get()) }
    single { ArchiveRoundStore(get()) }
    single { PendingUploadStore(get()) }
    single { WordPackCache(get()) }
    single {
        WordPackSyncManager(
            httpClient = get(),
            baseUrl = BuildConfig.API_BASE_URL,
            cache = get(),
            repository = get(),
            registry = get(),
        )
    }
    single { StyleChoiceLog(get()) }
    single { StyleExperimentController(get(), get()) }
    single { EntitlementRepository(get(), get()) } // PurchaseController from platformModule
    single { SessionStore(get()) }
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    single<AuthService> { KtorAuthService(get(), baseUrl = BuildConfig.API_BASE_URL, sessionStore = get()) }
    single<SyncService> {
        KtorSyncService(get(), baseUrl = BuildConfig.API_BASE_URL, sessionStore = get(), authService = get())
    }
    single<ArchiveService> {
        KtorArchiveService(get(), baseUrl = BuildConfig.API_BASE_URL, sessionStore = get(), authService = get())
    }
    single {
        ArchiveHistoryManager(
            ksafe = get(),
            sessionStore = get(),
            archiveService = get(),
        )
    }
    single<SuggestionService> { KtorSuggestionService(get(), baseUrl = BuildConfig.API_BASE_URL) }
    single {
        SyncManager(
            resultLog = get(),
            sessionStore = get(),
            authService = get(),
            syncService = get(),
            suggestionService = get(),
            pendingStore = get(),
            roundStore = get(),
            languageRegistry = get(),
        )
    }
}

/**
 * Initialize DI. Each platform entry point calls this once before the first screen.
 * Idempotent: a second call is a no-op. Android passes `androidContext(...)`
 * via [appDeclaration].
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        appDeclaration()
        modules(platformModule, appModule, languageModule)
    }
}
