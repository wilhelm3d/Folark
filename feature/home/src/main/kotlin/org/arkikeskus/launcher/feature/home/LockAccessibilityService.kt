package org.arkikeskus.launcher.feature.home

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import org.arkikeskus.launcher.data.root.RootActions
import org.arkikeskus.launcher.data.root.RootFeatures

/**
 * Double-tap-to-lock, and the host of the optional taskbar over other apps.
 *
 * Locking: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` when the home screen detects a
 * double-tap on empty space — locking the screen simply isn't possible for a normal app any other
 * way (short of a device-admin policy that breaks fingerprint unlock).
 *
 * Taskbar (Settings ▸ off by default): [AppTaskbar] draws Folark's own handle and app row in an
 * accessibility overlay window, the only window an app may keep above other apps without the
 * "display over other apps" permission.
 *
 * Either way the service reads no accessibility events and no screen content: see
 * res/xml/lock_accessibility_service.xml (no event types, no window content).
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        taskbar = AppTaskbar(this).also { it.start() }
    }

    private var taskbar: AppTaskbar? = null

    /** Folding or unfolding: the taskbar only belongs on the large display. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        taskbar?.onConfigurationChanged()
    }

    /**
     * Only while Settings ▸ Taskbar has per-app rules (see [watchApps]): which app came to the
     * front, by package name. Nothing else is read from the event.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (replaceSystemRecents && isSystemRecents(pkg, event.className?.toString())) {
            handOverRecents()
            return
        }
        // System UI (shade, Recents) and the keyboard aren't apps in front.
        if (pkg == "com.android.systemui" || pkg == keyboardPackage()) return
        taskbar?.onForegroundApp(pkg)
    }

    /** Android's Recents screen: the system's Recents app (Motorola's launcher here) showing its Recents activity. */
    private fun isSystemRecents(pkg: String, cls: String?): Boolean {
        if (pkg == packageName || cls == null) return false
        val recents = systemRecents
        return if (recents != null) pkg == recents.packageName && cls == recents.className
        else cls.endsWith(".RecentsActivity")
    }

    /** "package/class" of Android's Recents, from the system's own configuration. */
    private val systemRecents: ComponentName? by lazy {
        runCatching {
            val res = android.content.res.Resources.getSystem()
            val id = res.getIdentifier("config_recentsComponentName", "string", "android")
            if (id == 0) null else ComponentName.unflattenFromString(res.getString(id))
        }.getOrNull()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var handing = false

    /**
     * Android's Recents opened: after a moment, if it is still the activity in front (a swipe
     * home passes through it too, and must not be taken), go home and open Folark Recents.
     */
    private fun handOverRecents() {
        if (handing) return
        handing = true
        handler.postDelayed({
            Thread {
                val top = org.arkikeskus.launcher.data.OpenTasks.topResumed(this)
                val stillRecents = top != null && top.substringBefore('/') != packageName &&
                    (systemRecents?.let { top == it.flattenToString() } ?: top.endsWith(".RecentsActivity"))
                handler.post {
                    if (stillRecents && performGlobalAction(GLOBAL_ACTION_HOME)) {
                        handler.postDelayed({
                            org.arkikeskus.launcher.data.FolarkRecents.open(this)
                            handing = false
                        }, HOME_SETTLE_MS)
                    } else {
                        handing = false
                    }
                }
            }.apply { name = "folark-recents-handover"; isDaemon = true }.start()
        }, RECENTS_SETTLE_MS)
    }

    private fun keyboardPackage(): String? = runCatching {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
    }.getOrNull()

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        taskbar?.stop()
        taskbar = null
        super.onDestroy()
    }

    companion object {
        private var instance: LockAccessibilityService? = null

        /** Settings ▸ Recents ▸ Replace Android's Recents (kept up to date by the taskbar's settings). */
        @Volatile var replaceSystemRecents: Boolean = false

        /** How long Android's Recents must stay in front before it is taken over, and the pause after going home. */
        private const val RECENTS_SETTLE_MS = 280L
        private const val HOME_SETTLE_MS = 90L

        /** Tells the taskbar whether Folark's home screen is in front (it hides there). */
        fun setHomeVisible(visible: Boolean) {
            if (AppTaskbar.homeVisible == visible) return
            AppTaskbar.homeVisible = visible
            instance?.taskbar?.onHomeVisibilityChanged()
        }

        /**
         * Asks Android to split the screen with the app in front (the system's own split-screen
         * action, open to accessibility services). False when the service isn't running.
         */
        fun toggleSplitScreen(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false

        /** Opens Android's recents screen (on this phone, Motorola's own); false without the service. */
        fun openRecents(): Boolean = RootActions.run(
            RootFeatures.recentsMode,
            android = { instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false },
            rootCommand = RootActions.RECENTS,
        )

        /**
         * Back to the last app: recents twice, like a double press of the recents key (the second
         * press, while recents is showing, opens the app before the current one).
         */
        fun quickSwitch(): Boolean = RootActions.run(
            RootFeatures.recentsMode,
            android = ::quickSwitchService,
            rootCommand = RootActions.QUICK_SWITCH,
        )

        private fun quickSwitchService(): Boolean {
            val service = instance ?: return false
            if (!service.performGlobalAction(GLOBAL_ACTION_RECENTS)) return false
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) },
                QUICK_SWITCH_DELAY_MS,
            )
            return true
        }

        private const val QUICK_SWITCH_DELAY_MS = 320L

        /**
         * A touch from (x0, y0) to (x1, y1) over [durationMs], as a finger would do it (Recents'
         * attached view moves the split screen's divider with it). False without the service.
         */
        fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long): Boolean {
            val service = instance ?: return false
            // A zero-length path (start == end) makes StrokeDescription throw, so nudge the end a
            // pixel when the two points coincide; the whole build+dispatch is guarded regardless so
            // a bad gesture can never escape as an uncaught exception.
            return runCatching {
                val ex = if (x1 == x0 && y1 == y0) x1 + 1f else x1
                val path = android.graphics.Path().apply {
                    moveTo(x0, y0)
                    lineTo(ex, y1)
                }
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                service.dispatchGesture(gesture, null, null)
            }.getOrDefault(false)
        }

        /** Hears which app comes to the front ([on]), or nothing at all (the default). */
        fun watchApps(on: Boolean) {
            val service = instance ?: return
            runCatching {
                val info = service.serviceInfo ?: return
                val types = if (on) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
                if (info.eventTypes == types) return
                info.eventTypes = types
                info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
                service.serviceInfo = info
            }
        }

        /** Locks the screen; false when the service isn't enabled (or not yet connected). */
        fun lock(): Boolean = RootActions.run(
            RootFeatures.lockMode,
            android = { instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false },
            rootCommand = RootActions.LOCK,
        )

        /** Whether the user has allowed the service in the system accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val target = ComponentName(context, LockAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
        }
    }
}
