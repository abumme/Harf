package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.BuildConfig
import uz.abumme.harfgame.billing.NoOpPurchaseController
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.data.auth.JvmGoogleOAuthClient
import uz.abumme.harfgame.data.auth.NoOpOAuthClient
import uz.abumme.harfgame.data.auth.OAuthClient
import uz.abumme.harfgame.feature.share.Sharer
import uz.abumme.harfgame.games.GamesServices
import uz.abumme.harfgame.games.NoOpGamesServices
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { DesktopSharer() }
    single<PurchaseController> { NoOpPurchaseController }
    single<OAuthClient> {
        val googleClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        if (googleClientId.isNotBlank()) JvmGoogleOAuthClient(googleClientId)
        else NoOpOAuthClient(isGoogleSupported = true)
    }
    single<GamesServices> { NoOpGamesServices() }
}

private class DesktopSharer : Sharer {
    override fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // No native share sheet on desktop; best-effort = copy to clipboard.
    override fun share(text: String) = copy(text)
}
