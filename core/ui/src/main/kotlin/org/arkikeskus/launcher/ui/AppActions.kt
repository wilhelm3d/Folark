package org.arkikeskus.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.catching

/**
 * System app actions reachable from the long-press menus, targeted at the app's own user profile.
 *
 * A package can be installed in several profiles (personal, work, Private Space); a bare package
 * name doesn't identify which one, so these take the full [AppItem] and route through the
 * profile-aware [LauncherApps] APIs. Every call is wrapped in `catching` because an OS-blocked
 * profile action must never crash the launcher (it is the device's HOME).
 */
object AppActions {

    /** Opens the OS app-details screen for [appItem] in its own profile. */
    fun openAppInfo(context: Context, appItem: AppItem) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        var opened = launcherApps != null && catching {
            launcherApps.startAppDetailsActivity(appItem.componentName, appItem.user, null, null)
        }.isSuccess
        // Fallback for the personal profile ONLY: a bare package intent always resolves in the
        // launcher's own profile, so for a work / Private Space app it would open (and offer to
        // uninstall) the personal copy of the same package instead.
        if (!opened && appItem.user == Process.myUserHandle()) {
            opened = catching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", appItem.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
        }
        if (!opened) {
            catching { Toast.makeText(context, R.string.app_info_unavailable, Toast.LENGTH_SHORT).show() }
        }
    }

    /** Android's per-app aspect ratio page (Android 14+, where the phone maker left it on). */
    private const val ACTION_ASPECT_RATIO = "android.settings.MANAGE_USER_ASPECT_RATIO_SETTINGS"

    @Volatile private var aspectPage: Boolean? = null

    /**
     * Whether "Aspect ratio…" can be offered for [appItem]: the phone has Android's aspect ratio
     * page, and the app is in Folark's own profile (the page opens for that profile only).
     */
    fun canOpenAspectRatio(context: Context, appItem: AppItem): Boolean {
        if (appItem.user != Process.myUserHandle()) return false
        val known = aspectPage
        if (known != null) return known
        val found = catching {
            context.packageManager.resolveActivity(aspectRatioIntent(appItem.packageName), 0) != null
        }.getOrDefault(false)
        aspectPage = found
        return found
    }

    /** Opens Android's aspect ratio page for [appItem] (full screen, 16:9, 4:3… set by the phone maker). */
    fun openAspectRatio(context: Context, appItem: AppItem) {
        val opened = catching { context.startActivity(aspectRatioIntent(appItem.packageName)) }.isSuccess
        if (!opened) catching { Toast.makeText(context, R.string.app_aspect_ratio_unavailable, Toast.LENGTH_SHORT).show() }
    }

    private fun aspectRatioIntent(packageName: String) = Intent(ACTION_ASPECT_RATIO)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Whether [appItem] can be uninstalled at all: not an app that came with the phone (those can
     * only be turned off, and their updates removed, on Android's app page).
     */
    fun canUninstall(context: Context, appItem: AppItem): Boolean {
        val info = catching {
            context.getSystemService(LauncherApps::class.java)?.getApplicationInfo(appItem.packageName, 0, appItem.user)
        }.getOrNull() ?: return true
        val system = info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        val updated = info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return !system || updated
    }

    /**
     * Uninstalls [appItem]. The personal profile uses the system uninstall dialog directly; other
     * profiles (work / Private Space) have no reliable public profile-aware uninstall intent, so we
     * open that profile's app-details screen instead, from where the OS offers removal. This avoids
     * silently targeting (or uninstalling) the wrong profile's copy of the same package.
     */
    fun uninstall(context: Context, appItem: AppItem) {
        if (appItem.user == Process.myUserHandle()) {
            val started = catching {
                context.startActivity(
                    Intent(Intent.ACTION_DELETE)
                        .setData(Uri.fromParts("package", appItem.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (!started) openAppInfo(context, appItem)
        } else {
            openAppInfo(context, appItem)
        }
    }
}
