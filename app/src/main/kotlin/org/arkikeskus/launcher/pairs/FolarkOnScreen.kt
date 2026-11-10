package org.arkikeskus.launcher.pairs

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether one of Folark's own screens (home, Settings, Recents…) is resumed right now — the
 * user is looking at Folark. Kept from activity callbacks, so it is right in this process and
 * simply false after a cold start (an alarm waking the app finds nothing on screen).
 */
object FolarkOnScreen : Application.ActivityLifecycleCallbacks {
    private var count = 0

    /** True while at least one Folark activity is resumed (main thread). */
    @Volatile var resumed: Boolean = false
        private set

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityResumed(activity: Activity) {
        count++
        resumed = true
    }

    override fun onActivityPaused(activity: Activity) {
        count = (count - 1).coerceAtLeast(0)
        resumed = count > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
