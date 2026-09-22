package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.BuildConfig
import uz.abumme.harfgame.billing.NoOpPurchaseController
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.data.auth.NoOpOAuthClient
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.data.auth.WebGoogleOAuthClient
import uz.abumme.harfgame.feature.share.Sharer
import uz.abumme.harfgame.games.GamesServices
import uz.abumme.harfgame.games.NoOpGamesServices

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { WebSharer() }
    single<PurchaseController> { NoOpPurchaseController }
    single<OAuthClient> {
        val googleClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        if (googleClientId.isNotBlank()) WebGoogleOAuthClient(googleClientId)
        else NoOpOAuthClient(isGoogleSupported = true)
    }
    single<GamesServices> { NoOpGamesServices() }
}

// Web feature investment is post-launch; copy/share are no-ops for now.
private class WebSharer : Sharer {
    override fun copy(text: String) {}
    override fun share(text: String) {}
}
