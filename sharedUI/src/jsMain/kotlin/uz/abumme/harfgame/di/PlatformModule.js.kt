package uz.abumme.harfgame.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.feature.share.Sharer

actual val platformModule: Module = module {
    single { KSafe() }
    single<Sharer> { WebSharer() }
}

// Web feature investment is post-launch; copy/share are no-ops for now.
private class WebSharer : Sharer {
    override fun copy(text: String) {}
    override fun share(text: String) {}
}
