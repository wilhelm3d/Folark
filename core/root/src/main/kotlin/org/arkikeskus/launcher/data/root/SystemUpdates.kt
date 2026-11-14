package org.arkikeskus.launcher.data.root

import android.content.Context
import android.content.pm.PackageManager
import org.arkikeskus.launcher.model.catching

/**
 * Turning Motorola's system updates on or off (Settings ▸ Backup & restore). Updates are blocked by
 * disabling Motorola's OTA app and its two helpers with root, and allowed again by enabling them.
 *
 * This is for a phone whose root depends on a kernel that a system update would replace: blocking
 * updates holds that kernel (and so keeps root working), at the cost of staying on an older security
 * patch. Changing the state needs root; so does reading it fully (see [allowed]).
 *
 * Every package name here is Folark's own fixed list, safe to put in a root command.
 */
object SystemUpdates {
    /** Motorola's over-the-air update app: the one that checks, downloads and installs updates. */
    const val OTA = "com.motorola.ccc.ota"

    /** The OTA app, plus the parts that notify about updates and check in for them. */
    val PACKAGES = listOf(
        OTA,
        "com.motorola.ccc.notification",
        "com.motorola.ccc.devicemanagement",
    )

    /** The AppOps that block a package from running in the background or holding wake locks. */
    private val OPS = listOf("RUN_IN_BACKGROUND", "WAKE_LOCK")

    /** How `cmd appops get` reports each of [OPS]. */
    private val MODE_LINES = OPS.associateWith { op -> Regex("""$op:\s*(\w+)""") }

    /** The modes `cmd appops` reports and accepts. */
    private val MODES = setOf("allow", "ignore", "deny", "default", "foreground", "errored")

    /**
     * The mode each AppOp had before Folark blocked it, so enabling puts back what the phone had
     * rather than a guess (keyed by "package/op", kept only while the updates are blocked), and
     * [BLOCKED]: Folark's own note that it blocked the updates.
     */
    private const val PREFS = "system_updates_appops"
    private const val BLOCKED = "blocked"

    /** Whether this phone even has Motorola's OTA app (so the toggle is worth showing). */
    fun available(context: Context): Boolean =
        catching { context.packageManager.getApplicationEnabledSetting(OTA) }.isSuccess

    /**
     * Whether system updates are allowed, as far as can be told without root: the OTA app is
     * enabled, and Folark has not noted blocking them. Cheap, fine on the main thread; [allowed]
     * is the full answer.
     */
    fun enabled(context: Context): Boolean =
        decide(packageOn = packageOn(context), backgroundMode = null, blockedByFolark = blockedByFolark(context))

    /**
     * Whether system updates are allowed right now. Motorola protects the OTA app from being
     * disabled, so on its phones the block is in the app's AppOps and its package state alone says
     * "on" while updates are blocked. With root those AppOps are read (and win over Folark's own
     * note, which another tool may have made stale); without root the note is all there is.
     * Blocking: call off the main thread.
     */
    fun allowed(context: Context): Boolean {
        val mode = if (RootShell.enabled && RootShell.available()) readMode(OTA, OPS.first()) else null
        return decide(packageOn(context), mode, blockedByFolark(context))
    }

    /** [allowed]'s decision: a disabled OTA app blocks; else its background AppOp, when known; else the note. */
    internal fun decide(packageOn: Boolean, backgroundMode: String?, blockedByFolark: Boolean): Boolean = when {
        !packageOn -> false
        backgroundMode != null -> backgroundMode != "ignore"
        else -> !blockedByFolark
    }

    /**
     * Turns the update apps on ([enable] true) or off, with root. Motorola protects these packages
     * from pm disable, pm hide, and pm suspend, even for root, so the AppOps that let them run in
     * the background or hold wake locks are set to ignore while they are off. Their previous modes
     * are remembered and put back on enable. Returns true when every one was changed.
     * Blocking: call off the main thread.
     */
    fun setEnabled(context: Context, enable: Boolean): Boolean {
        if (!RootShell.available(prompt = true)) return false
        val pm = context.packageManager
        val prefs = prefs(context)
        var ok = true
        for (pkg in PACKAGES) {
            if (!ShellSafe.isName(pkg)) continue
            // Skip one this phone doesn't have.
            if (catching { pm.getApplicationEnabledSetting(pkg) }.isFailure) continue

            // Remember the real modes before blocking. A mode of "ignore" is our own doing, so it is
            // never stored: an earlier save of the real mode stays in place.
            if (!enable) {
                val edit = prefs.edit()
                for (op in OPS) {
                    val current = readMode(pkg, op) ?: continue
                    if (current != "ignore") edit.putString(key(pkg, op), current)
                }
                edit.commit()
            }

            // Some helper packages might still allow being disabled directly.
            val disableCommand = if (enable) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
            val disableOk = RootShell.run(disableCommand) != null

            val appOpsCommand = OPS.joinToString("; ") { op ->
                val mode = if (enable) prefs.getString(key(pkg, op), null) ?: "default" else "ignore"
                "cmd appops set $pkg $op $mode"
            }
            val appOpsOk = RootShell.run(appOpsCommand) != null
            if (enable && appOpsOk) {
                prefs.edit().apply { OPS.forEach { remove(key(pkg, it)) } }.commit()
            }

            if (!disableOk && !appOpsOk) ok = false
        }
        if (ok) prefs.edit().putBoolean(BLOCKED, !enable).commit()
        return ok
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun packageOn(context: Context): Boolean {
        val state = catching { context.packageManager.getApplicationEnabledSetting(OTA) }.getOrNull()
            ?: return true
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun blockedByFolark(context: Context): Boolean = prefs(context).getBoolean(BLOCKED, false)

    private fun key(pkg: String, op: String) = "$pkg/$op"

    /** The mode an AppOp is in now, or null when it cannot be read or is not a known mode. */
    private fun readMode(pkg: String, op: String): String? {
        val out = RootShell.run("cmd appops get $pkg $op") ?: return null
        val mode = MODE_LINES.getValue(op).find(out)?.groupValues?.get(1) ?: return null
        return mode.takeIf { it in MODES }
    }
}
