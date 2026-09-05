package uz.abumme.harfgame.data.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the currently-resumed Activity so Credential Manager can present its sheet — it needs an
 * Activity context, not the Application. Registered once from the Android entry point.
 */
object CurrentActivityProvider {
    private var registered = false
    @Volatile
    private var ref = WeakReference<Activity>(null)

    fun current(): Activity? = ref.get()

    @Synchronized
    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                ref = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (ref.get() === activity) ref = WeakReference(null)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
