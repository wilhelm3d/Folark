package org.arkikeskus.launcher.data.root

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.arkikeskus.launcher.data.ShellSplit

/**
 * What root can do once instead of a computer and ADB, or instead of a trip to Android's settings
 * (Settings ▸ Root). A grant stays after root is gone (until Folark is uninstalled); "keep" means
 * Folark checks now and then and gives it again if Android took it away.
 *
 * Every command is fixed text with Folark's own package and component names. Blocking: call off the
 * main thread.
 */
object RootGrants {
    const val DUMP = "dump"
    const val USAGE = "usage"
    const val SECURE_SETTINGS = "secure_settings"
    const val NOTIFICATIONS = "notifications"
    const val ACCESSIBILITY = "accessibility"
    const val WIDGETS = "widgets"
    val ALL = listOf(DUMP, USAGE, SECURE_SETTINGS, NOTIFICATIONS, ACCESSIBILITY, WIDGETS)

    private const val LISTENER = "org.arkikeskus.launcher.notifications.NotificationDotListenerService"
    private const val ACCESSIBILITY_SERVICE = "org.arkikeskus.launcher.feature.home.LockAccessibilityService"
    private const val HOME = "org.arkikeskus.launcher.LauncherActivity"

    /** Whether [grant] is in place now (no root needed to look). Null when Android can't tell. */
    fun granted(context: Context, grant: String): Boolean? = when (grant) {
        DUMP -> has(context, Manifest.permission.DUMP)
        USAGE -> ShellSplit.hasUsageAccess(context)
        SECURE_SETTINGS -> has(context, Manifest.permission.WRITE_SECURE_SETTINGS)
        NOTIFICATIONS -> secureList(context, "enabled_notification_listeners")
            .any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        ACCESSIBILITY -> secureList(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .any { ComponentName.unflattenFromString(it) == ComponentName(context.packageName, ACCESSIBILITY_SERVICE) }
        else -> null // Widgets: Android has no way to ask.
    }

    /** Gives [grant] with root; true when root did it. */
    fun grant(context: Context, grant: String): Boolean {
        val pkg = context.packageName
        if (!SAFE.matches(pkg)) return false
        val command = when (grant) {
            DUMP -> "pm grant $pkg android.permission.DUMP"
            USAGE -> "pm grant $pkg android.permission.PACKAGE_USAGE_STATS; appops set $pkg GET_USAGE_STATS allow"
            SECURE_SETTINGS -> "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
            NOTIFICATIONS -> "cmd notification allow_listener $pkg/$LISTENER"
            ACCESSIBILITY -> {
                val mine = "$pkg/$ACCESSIBILITY_SERVICE"
                val list = secureList(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .filter { it.isNotBlank() && it != mine && SAFE_COMPONENT.matches(it) } + mine
                "settings put secure enabled_accessibility_services '${list.joinToString(":")}'; settings put secure accessibility_enabled 1"
            }
            WIDGETS -> "cmd appwidget grantbind --package $pkg --user current"
            else -> return false
        }
        return RootShell.available() && RootShell.run(command) != null
    }

    /** Makes Folark the default home app. */
    fun makeDefaultHome(context: Context): Boolean {
        val pkg = context.packageName
        if (!SAFE.matches(pkg)) return false
        return RootShell.available() && RootShell.run("cmd package set-home-activity $pkg/$HOME") != null
    }

    private var lastRestore = 0L

    /**
     * Gives back the grants Settings ▸ Root keeps, if Android took any away (at most once a
     * minute; root is only asked when something is missing).
     */
    fun restoreIfNeeded(context: Context) {
        val keep = RootFeatures.keep
        if (keep.isEmpty() || !RootShell.enabled) return
        val now = System.currentTimeMillis()
        if (now - lastRestore < RESTORE_EVERY_MS) return
        lastRestore = now
        val app = context.applicationContext
        Thread {
            val missing = keep.filter { if (it == WIDGETS) !widgetsDone else granted(app, it) != true }
            if (missing.isEmpty() || !RootShell.available()) return@Thread
            for (g in missing) {
                val ok = grant(app, g)
                if (g == WIDGETS && ok) widgetsDone = true
            }
        }.apply { name = "folark-root-grants"; isDaemon = true }.start()
    }

    /** Widgets can't be checked, so they are given once per run of Folark. */
    @Volatile private var widgetsDone = false

    private fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun secureList(context: Context, key: String): List<String> =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()
            ?.split(':')?.filter { it.isNotBlank() }.orEmpty()

    private val SAFE = Regex("^[A-Za-z0-9_.]+$")
    private val SAFE_COMPONENT = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")
    private const val RESTORE_EVERY_MS = 60_000L
}
