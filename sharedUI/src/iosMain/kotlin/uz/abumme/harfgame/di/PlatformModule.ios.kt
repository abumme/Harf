package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import uz.abumme.harfgame.BuildConfig
import uz.abumme.harfgame.data.keyWindow
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.RevenueCatPurchaseController
import uz.abumme.harfgame.data.auth.IosAppleOAuthClient
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.feature.share.Sharer
import uz.abumme.harfgame.games.GamesServices
import uz.abumme.harfgame.games.NoOpGamesServices

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { IosSharer() }
    single<PurchaseController> { RevenueCatPurchaseController(BuildConfig.REVENUECAT_IOS_KEY) }
    single<OAuthClient> { IosAppleOAuthClient() }
    single<GamesServices> { NoOpGamesServices() }
}

private class IosSharer : Sharer {
    override fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun share(text: String) {
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        keyWindow()?.rootViewController?.presentViewController(controller, animated = true, completion = null)
    }
}
