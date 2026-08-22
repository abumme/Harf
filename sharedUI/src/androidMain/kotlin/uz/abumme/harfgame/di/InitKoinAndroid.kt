package uz.abumme.harfgame.di

import android.content.Context
import org.koin.android.ext.koin.androidContext

/** Android entry helper so the app module doesn't need Koin on its own classpath. */
fun initKoinAndroid(context: Context) = initKoin {
    androidContext(context.applicationContext)
}
