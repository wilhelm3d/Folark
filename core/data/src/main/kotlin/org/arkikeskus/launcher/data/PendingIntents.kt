package org.arkikeskus.launcher.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** Sending other apps' PendingIntents (a notification's tap action) the way the shade does. */
object PendingIntents {
    /**
     * Sends [intent] with Folark's right to start an activity passed on (Android 14+ no longer does
     * that by itself); Folark is in front (or its taskbar is) when this is used. False when the
     * send failed (a cancelled PendingIntent, typically).
     */
    fun sendFromForeground(context: Context, intent: PendingIntent): Boolean = runCatching {
        intent.send(context, 0, null, null, null, null, foregroundStartOptions().toBundle())
    }.isSuccess

    /**
     * Options that pass Folark's right to start an activity on to the app it sends for. Android 16
     * deprecated MODE_BACKGROUND_ACTIVITY_START_ALLOWED (it now means "only while the sender has a
     * visible window"); the taskbar sends from overlay windows over another app, so from Android
     * 16 the explicit ALLOW_ALWAYS is used. Only ever called for a tap by the user.
     */
    fun foregroundStartOptions(): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        when {
            Build.VERSION.SDK_INT >= 36 -> options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            )
            Build.VERSION.SDK_INT >= 34 -> @Suppress("DEPRECATION") options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }
        return options
    }
}
