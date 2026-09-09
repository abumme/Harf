package uz.abumme.harfgame.androidApp

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk
import uz.abumme.harfgame.di.initKoinAndroid

class HarfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(this)
        PlayGamesSdk.initialize(this)
    }
}
