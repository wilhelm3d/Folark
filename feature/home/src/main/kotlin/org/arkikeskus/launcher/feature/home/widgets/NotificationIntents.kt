package org.arkikeskus.launcher.feature.home.widgets

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import org.arkikeskus.launcher.model.catching

/**
 * Fires a notification's content intent the way a tap in the shade would. Android 14+ no longer
 * grants the sender's foreground privileges implicitly; the launcher is in the foreground on tap,
 * so the background-activity-start grant is ours to give. Returns false when the send failed
 * (a cancelled PendingIntent, typically).
 */
internal fun sendNotificationIntent(context: Context, intent: PendingIntent): Boolean = catching {
    val options = org.arkikeskus.launcher.data.PendingIntents.foregroundStartOptions()
    intent.send(context, 0, null, null, null, null, options.toBundle())
}.isSuccess
