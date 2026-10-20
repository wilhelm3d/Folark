package org.arkikeskus.launcher.data.root

import android.os.Handler
import android.os.Looper
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Settings ▸ Root, as plain values any part of Folark can read without waiting (the taskbar, the
 * accessibility service, the home gestures). Kept up to date from the settings by the app.
 */
object RootFeatures {
    @Volatile var lockMode: String = LauncherSettings.ROOT_MODE_FALLBACK
        private set
    @Volatile var recentsMode: String = LauncherSettings.ROOT_MODE_FALLBACK
        private set
    @Volatile var shadeMode: String = LauncherSettings.ROOT_MODE_FALLBACK
        private set
    @Volatile var keep: Set<String> = emptySet()
        private set

    fun update(s: LauncherSettings) {
        RootShell.enabled = s.rootEnabled
        lockMode = s.rootLockMode
        recentsMode = s.rootRecentsMode
        shadeMode = s.rootShadeMode
        keep = if (!s.rootEnabled) emptySet() else buildSet {
            if (s.rootKeepDump) add(RootGrants.DUMP)
            if (s.rootKeepUsage) add(RootGrants.USAGE)
            if (s.rootKeepSecureSettings) add(RootGrants.SECURE_SETTINGS)
            if (s.rootKeepNotifications) add(RootGrants.NOTIFICATIONS)
            if (s.rootKeepAccessibility) add(RootGrants.ACCESSIBILITY)
            if (s.rootKeepWidgets) add(RootGrants.WIDGETS)
        }
    }
}

/**
 * Does something either Android's way (the accessibility service, a system call) or with root,
 * as Settings ▸ Root says for it:
 * - [LauncherSettings.ROOT_MODE_OFF]: Android's way only.
 * - [LauncherSettings.ROOT_MODE_FALLBACK]: Android's way, and root when that can't (the service is off).
 * - [LauncherSettings.ROOT_MODE_ALWAYS]: root, and Android's way when root isn't there.
 *
 * Root commands run on their own thread (never the main thread); true means it was done or is on
 * its way.
 */
object RootActions {
    private val main = Handler(Looper.getMainLooper())

    fun run(mode: String, android: () -> Boolean, rootCommand: String): Boolean {
        if (!RootShell.enabled || mode == LauncherSettings.ROOT_MODE_OFF) return android()
        if (RootShell.lastKnown == false && !RootShell.recheckDue()) return android()
        if (mode == LauncherSettings.ROOT_MODE_ALWAYS) {
            inBackground(rootCommand) { ok -> if (!ok) main.post { android() } }
            return true
        }
        if (android()) return true
        inBackground(rootCommand) { }
        return true
    }

    private fun inBackground(command: String, done: (Boolean) -> Unit) {
        Thread {
            val ok = RootShell.available() && RootShell.run(command) != null
            done(ok)
        }.apply { name = "folark-root"; isDaemon = true }.start()
    }

    /** Android's keys, as root types them: lock (sleep), and the recents key. */
    const val LOCK = "input keyevent 223"
    const val RECENTS = "input keyevent 187"
    const val QUICK_SWITCH = "input keyevent 187; sleep 0.32; input keyevent 187"
    const val EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
}
