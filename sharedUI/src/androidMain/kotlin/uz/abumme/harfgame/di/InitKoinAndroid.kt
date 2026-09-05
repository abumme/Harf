package uz.abumme.harfgame.di

import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import uz.abumme.harfgame.data.auth.CurrentActivityProvider

/** Android entry helper so the app module doesn't need Koin on its own classpath. */
fun initKoinAndroid(context: Context) {
    // Track the current Activity so native Google sign-in (Credential Manager) can present its UI.
    (context.applicationContext as? Application)?.let(CurrentActivityProvider::register)
    initKoin {
        androidContext(context.applicationContext)
    }
}
