package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.billing.NoOpPurchaseController
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.feature.share.Sharer
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { DesktopSharer() }
    single<PurchaseController> { NoOpPurchaseController }
}

private class DesktopSharer : Sharer {
    override fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // No native share sheet on desktop; best-effort = copy to clipboard.
    override fun share(text: String) = copy(text)
}
