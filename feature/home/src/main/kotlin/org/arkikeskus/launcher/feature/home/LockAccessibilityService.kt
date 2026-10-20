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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        taskbar?.stop()
        taskbar = null
        super.onDestroy()
    }

    companion object {
        private var instance: LockAccessibilityService? = null

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
