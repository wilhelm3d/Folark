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
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        intent.send(context, 0, null, null, null, null, options.toBundle())
    }.isSuccess
}
