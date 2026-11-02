package org.arkikeskus.launcher.feature.home

import android.annotation.SuppressLint
import android.content.Context
import org.arkikeskus.launcher.data.root.RootActions
import org.arkikeskus.launcher.data.root.RootFeatures

/**
 * Opens the system notification shade via the hidden StatusBarManager API. Requires the normal
 * EXPAND_STATUS_BAR permission. No-ops on OEMs that don't expose the method.
 */
object NotificationShade {
    /** Pulls the shade down, Android's way or with root (Settings ▸ Root). */
    fun expand(context: Context) {
        RootActions.run(
            RootFeatures.shadeMode,
            android = { expandAndroid(context) },
            rootCommand = RootActions.EXPAND_NOTIFICATIONS,
        )
    }

    @SuppressLint("WrongConstant")
    private fun expandAndroid(context: Context): Boolean = try {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
        true
    } catch (e: Exception) {
        false // the gesture simply does nothing if unavailable (or root takes over)
    }
}
