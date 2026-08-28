package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.billing.NoOpPurchaseController
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.data.auth.NoOpOAuthClient
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.feature.share.Sharer

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { WebSharer() }
    single<PurchaseController> { NoOpPurchaseController }
    single<OAuthClient> { NoOpOAuthClient() }
}

// Web feature investment is post-launch; copy/share are no-ops for now.
private class WebSharer : Sharer {
    override fun copy(text: String) {}
    override fun share(text: String) {}
}
