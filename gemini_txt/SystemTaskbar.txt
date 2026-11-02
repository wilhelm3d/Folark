package org.arkikeskus.launcher.data.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources

/**
 * Android's own taskbar, as the system launcher draws it (Motorola's on the Razr; the launcher that
 * also runs gesture navigation and quick switch). Motorola keeps its mode in the launcher's
 * `boot_aware_prefs`, key `TASKBAR_MODE_NO_BUTTON_KEY`: 1 always shown, 2 shown by a swipe up,
 * 4 not shown (Android's "pinning" key is ignored there).
 */
object SystemTaskbar {
    const val MODE_ALWAYS = 1
    const val MODE_GESTURE = 2
    const val MODE_OFF = 4

    /** The system launcher's package (the one that is Android's Recents and gesture engine). */
    fun launcherPackage(): String =
        runCatching {
            val res = Resources.getSystem()
            val id = res.getIdentifier("config_recentsComponentName", "string", "android")
            if (id == 0) null else ComponentName.unflattenFromString(res.getString(id))?.packageName
        }.getOrNull() ?: "com.motorola.launcher3"

    /** Opens Motorola's own taskbar settings page; false where there is none. */
    fun openSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent()
                .setComponent(ComponentName(launcherPackage(), "com.android.launcher3.taskbar.TaskbarSettings"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /**
     * Sets the mode with root and restarts the system launcher so it reads it (gestures pause for
     * a second). Blocking: call off the main thread. False without root.
     */
    fun apply(mode: Int): Boolean {
        if (mode !in listOf(MODE_ALWAYS, MODE_GESTURE, MODE_OFF)) return false
        val pkg = launcherPackage()
        if (!Regex("^[A-Za-z0-9_.]+$").matches(pkg)) return false
        val script = """
            F=/data/user_de/0/$pkg/shared_prefs/boot_aware_prefs.xml
            D=${'$'}(dirname "${'$'}F")
            [ -d "${'$'}D" ] || exit 1
            [ -f "${'$'}F" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n</map>\n' > "${'$'}F"
            if grep -q 'name="TASKBAR_MODE_NO_BUTTON_KEY"' "${'$'}F"; then
              sed -i 's|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="[0-9]*" />|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="$mode" />|' "${'$'}F"
            else
              sed -i 's|</map>|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="$mode" /></map>|' "${'$'}F"
            fi
            chown ${'$'}(stat -c %u:%g "${'$'}D") "${'$'}F"
            chmod 660 "${'$'}F"
            restorecon "${'$'}F" 2>/dev/null
            am force-stop $pkg
            grep -o 'name="TASKBAR_MODE_NO_BUTTON_KEY" value="[0-9]*"' "${'$'}F"
        """.trimIndent()
        return RootShell.available() && RootShell.run(script, APPLY_TIMEOUT_MS)?.contains("value=\"$mode\"") == true
    }

    private const val APPLY_TIMEOUT_MS = 10_000L
}
