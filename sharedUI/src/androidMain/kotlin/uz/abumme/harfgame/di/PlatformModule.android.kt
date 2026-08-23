package uz.abumme.harfgame.di

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import eu.anifantakis.lib.ksafe.KSafe
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module
import uz.abumme.harfgame.BuildConfig
import uz.abumme.harfgame.billing.PurchaseController
import uz.abumme.harfgame.billing.RevenueCatPurchaseController
import uz.abumme.harfgame.feature.share.Sharer

actual val platformModule: Module = module {
    single { KSafe(androidApplication()) }
    single<Sharer> { AndroidSharer(androidApplication()) }
    single<PurchaseController> { RevenueCatPurchaseController(BuildConfig.REVENUECAT_ANDROID_KEY) }
}

private class AndroidSharer(private val context: Context) : Sharer {
    override fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Harf", text))
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
