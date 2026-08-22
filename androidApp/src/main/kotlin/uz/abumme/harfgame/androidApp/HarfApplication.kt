package uz.abumme.harfgame.androidApp

import android.app.Application
import uz.abumme.harfgame.di.initKoinAndroid

class HarfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(this)
    }
}
