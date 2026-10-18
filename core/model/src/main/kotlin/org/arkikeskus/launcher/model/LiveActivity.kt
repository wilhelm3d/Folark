package org.arkikeskus.launcher.model

import android.app.PendingIntent
import android.graphics.drawable.Icon

/**
 * Something going on right now that an app shows as an ongoing notification: a timer or stopwatch,
 * navigation, a call, a download or upload, a workout, a delivery or ride (Android 16 "Live
 * updates"). Folark shows it as a small live pill on home and on the taskbar.
 */
data class LiveActivity(
    /** StatusBarNotification.key. */
    val key: String,
    val packageName: String,
    val userSerial: Long,
    /** The notification's small (status bar) icon. */
    val icon: Icon?,
    /** The app's colour for it, or null when it set none. */
    val color: Int?,
    /** Notification title (a destination, a timer's name, a file name). */
    val title: String,
    /** Notification text (the next turn, "2 min away"). */
    val text: String,
    /** Android 16's short "critical" text (an ETA, a distance), the best few characters to show. */
    val critical: String?,
    /** A ticking time: the clock time it counts from (or to, when [countDown]); 0 = none. */
    val chronometerBase: Long,
    val countDown: Boolean,
    /** Progress 0..1, or null when it has none; [indeterminate] for a busy bar without an end. */
    val progress: Float?,
    val indeterminate: Boolean,
    val category: String?,
    val postTime: Long,
    val contentIntent: PendingIntent?,
) {
    // PendingIntent and Icon compare by identity and are unparcelled afresh on every snapshot.
    private val fingerprint: List<Any?>
        get() = listOf(key, packageName, userSerial, color, title, text, critical, chronometerBase, countDown,
            progress, indeterminate, category, postTime, contentIntent != null, icon != null)

    override fun equals(other: Any?): Boolean = other is LiveActivity && fingerprint == other.fingerprint
    override fun hashCode(): Int = fingerprint.hashCode()

    /** The few characters the pill shows, at [now] (epoch ms). */
    fun shortText(now: Long): String {
        critical?.takeIf { it.isNotBlank() }?.let { return it }
        if (chronometerBase > 0L) {
            val ms = if (countDown) chronometerBase - now else now - chronometerBase
            return formatClock(ms.coerceAtLeast(0L))
        }
        if (progress != null && !indeterminate) return "${(progress * 100).toInt()}%"
        return text.ifBlank { title }
    }

    /** Whether the text changes on its own every second (a running clock). */
    val ticks: Boolean get() = (critical.isNullOrBlank()) && chronometerBase > 0L

    companion object {
        fun formatClock(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
