package org.arkikeskus.launcher.data.root

import android.content.Context
import android.content.pm.PackageManager

/**
 * Turning Motorola's system updates on or off (Settings ▸ Backup & restore). Updates are blocked by
 * disabling Motorola's OTA app and its two helpers with root, and allowed again by enabling them.
 *
 * This is for a phone whose root depends on a kernel that a system update would replace: blocking
 * updates holds that kernel (and so keeps root working), at the cost of staying on an older security
 * patch. Reading the current state needs no root; changing it does.
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

    private val SAFE = Regex("^[A-Za-z0-9_.]+$")

    /** Whether this phone even has Motorola's OTA app (so the toggle is worth showing). */
    fun available(context: Context): Boolean =
        runCatching { context.packageManager.getApplicationEnabledSetting(OTA) }.isSuccess

    /** Whether system updates are allowed right now (the OTA app is enabled). No root needed. */
    fun enabled(context: Context): Boolean {
        val state = runCatching { context.packageManager.getApplicationEnabledSetting(OTA) }.getOrNull()
            ?: return true
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    /**
     * Turns the update apps on ([enable] true) or off, with root. Only touches packages that are
     * installed. Returns true when every one was changed. Blocking: call off the main thread.
     */
    fun setEnabled(context: Context, enable: Boolean): Boolean {
        if (!RootShell.available(prompt = true)) return false
        val pm = context.packageManager
        var ok = true
        for (pkg in PACKAGES) {
            if (!SAFE.matches(pkg)) continue
            // Skip one this phone doesn't have (enable/disable of a missing package just fails).
            if (runCatching { pm.getApplicationEnabledSetting(pkg) }.isFailure) continue
            val command = if (enable) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
            if (RootShell.run(command) == null) ok = false
        }
        return ok
    }
}
