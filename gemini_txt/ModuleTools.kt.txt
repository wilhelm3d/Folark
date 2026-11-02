package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.root.RootShell

/**
 * Root helpers for Folark's Vector module (Settings ▸ Recents ▸ Split screen): restarting System UI
 * after the module was updated, and reading the module's own log lines on the phone. Nothing here
 * leaves the phone; the log is only shown, and copied only when the person taps Copy.
 */
object ModuleTools {
    /** Every line the module writes starts with this (XposedBridge.log in RecentsHook). */
    private const val TAG = "Folark:"
    private const val MAX_LINES = 300

    /** Restarts System UI with root (it comes back by itself in a few seconds). False without root. */
    fun restartSystemUi(): Boolean {
        if (!RootShell.available(prompt = true)) return false
        return RootShell.run("killall com.android.systemui || pkill -f com.android.systemui; echo done", 8_000L)
            ?.contains("done") == true
    }

    /**
     * The module's last log lines, newest last: from Android's log, else from Vector's (LSPosed's)
     * log files. Null without root; empty when there are none (the module hasn't run, or its lines
     * have scrolled out of the log).
     */
    fun moduleLog(): String? {
        if (!RootShell.available(prompt = true)) return null
        val fromLogcat = RootShell.run("logcat -d -v time -t 20000 2>/dev/null | grep -F '$TAG' | tail -n $MAX_LINES", 10_000L)
            ?.trim().orEmpty()
        if (fromLogcat.isNotEmpty()) return fromLogcat
        return RootShell.run(
            "for f in /data/adb/lspd/log/*.log /data/adb/lspd/log/*/*.log; do [ -f \"\$f\" ] && grep -hF '$TAG' \"\$f\"; done 2>/dev/null | tail -n $MAX_LINES",
            10_000L,
        )?.trim().orEmpty()
    }
}
