package org.arkikeskus.launcher.data.root

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RootMode
import org.arkikeskus.launcher.model.safeScope

/**
 * Settings ▸ Root, as plain values any part of Folark can read without waiting (the taskbar, the
 * accessibility service, the home gestures). Kept up to date from the settings by the app.
 * Process-wide on purpose: Android creates the accessibility service and the grant check runs in
 * onResume, and none of them can wait for DataStore; the app's settings stream is the one writer.
 */
object RootFeatures {
    @Volatile var lockMode: RootMode = RootMode.FALLBACK
        private set
    @Volatile var recentsMode: RootMode = RootMode.FALLBACK
        private set
    @Volatile var shadeMode: RootMode = RootMode.FALLBACK
        private set
    @Volatile var keep: Set<String> = emptySet()
        private set

    fun update(s: LauncherSettings) {
        RootShell.enabled = s.root.enabled
        lockMode = s.root.lockMode
        recentsMode = s.root.recentsMode
        shadeMode = s.root.shadeMode
        keep = if (!s.root.enabled) emptySet() else buildSet {
            if (s.root.keepDump) add(RootGrants.DUMP)
            if (s.root.keepUsage) add(RootGrants.USAGE)
            if (s.root.keepSecureSettings) add(RootGrants.SECURE_SETTINGS)
            if (s.root.keepNotifications) add(RootGrants.NOTIFICATIONS)
            if (s.root.keepAccessibility) add(RootGrants.ACCESSIBILITY)
            if (s.root.keepWidgets) add(RootGrants.WIDGETS)
        }
    }
}

/**
 * Does something either Android's way (the accessibility service, a system call) or with root,
 * as Settings ▸ Root says for it:
 * - [RootMode.OFF]: Android's way only.
 * - [RootMode.FALLBACK]: Android's way, and root when that can't (the service is off).
 * - [RootMode.ALWAYS]: root, and Android's way when root isn't there.
 *
 * Root commands run on their own thread (never the main thread); true means it was done or is on
 * its way.
 */
object RootActions {
    private val main = Handler(Looper.getMainLooper())

    fun run(mode: RootMode, android: () -> Boolean, rootCommand: String): Boolean {
        if (!RootShell.enabled || mode == RootMode.OFF) return android()
        if (RootShell.lastKnown == false && !RootShell.recheckDue()) return android()
        if (mode == RootMode.ALWAYS) {
            inBackground(rootCommand) { ok -> if (!ok) main.post { android() } }
            return true
        }
        if (android()) return true
        inBackground(rootCommand) { }
        return true
    }

    /** Root commands of the gestures and keys: off the main thread, one shared pool, failures logged. */
    private val worker = safeScope(Dispatchers.IO)

    private fun inBackground(command: String, done: (Boolean) -> Unit) {
        worker.launch {
            val ok = RootShell.available() && RootShell.run(command) != null
            done(ok)
        }
    }

    /** Android's keys, as root types them: lock (sleep), and the recents key. */
    const val LOCK = "input keyevent 223"
    const val RECENTS = "input keyevent 187"
    const val QUICK_SWITCH = "input keyevent 187; sleep 0.32; input keyevent 187"
    const val EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
}
