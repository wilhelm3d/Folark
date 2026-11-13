package org.arkikeskus.launcher.data.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import org.arkikeskus.launcher.model.catching

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
        catching {
            val res = Resources.getSystem()
            val id = res.getIdentifier("config_recentsComponentName", "string", "android")
            if (id == 0) null else ComponentName.unflattenFromString(res.getString(id))?.packageName
        }.getOrNull() ?: "com.motorola.launcher3"

    /** Opens Motorola's own taskbar settings page; false where there is none. */
    fun openSettings(context: Context): Boolean = catching {
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
        if (!SAFE_PACKAGE.matches(pkg)) return false
        // The launcher is stopped BEFORE the edit (a running launcher writes its prefs back from
        // memory and would undo it), and again after it, in case Android restarted it in between
        // (it is the bound Recents/gesture service) and it read the old file.
        val script = """
            U=${'$'}(am get-current-user 2>/dev/null | tr -cd '0-9')
            [ -n "${'$'}U" ] || U=0
            F=/data/user_de/${'$'}U/$pkg/shared_prefs/boot_aware_prefs.xml
            D=${'$'}(dirname "${'$'}F")
            [ -d "${'$'}D" ] || exit 1
            am force-stop --user ${'$'}U $pkg
            [ -f "${'$'}F" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n</map>\n' > "${'$'}F"
            if grep -q 'name="TASKBAR_MODE_NO_BUTTON_KEY"' "${'$'}F"; then
              sed -i 's|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="[0-9]*" />|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="$mode" />|' "${'$'}F"
            else
              sed -i 's|</map>|<int name="TASKBAR_MODE_NO_BUTTON_KEY" value="$mode" /></map>|' "${'$'}F"
            fi
            chown ${'$'}(stat -c %u:%g "${'$'}D") "${'$'}F"
            chmod 660 "${'$'}F"
            restorecon "${'$'}F" 2>/dev/null
            am force-stop --user ${'$'}U $pkg
            grep -o 'name="TASKBAR_MODE_NO_BUTTON_KEY" value="[0-9]*"' "${'$'}F"
        """.trimIndent()
        return RootShell.available() && RootShell.run(script, APPLY_TIMEOUT_MS)?.contains("value=\"$mode\"") == true
    }

    private const val APPLY_TIMEOUT_MS = 10_000L

    /** Built once (E10). */
    private val SAFE_PACKAGE = Regex("^[A-Za-z0-9_.]+$")
}
