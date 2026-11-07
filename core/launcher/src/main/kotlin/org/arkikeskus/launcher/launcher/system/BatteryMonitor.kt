package org.arkikeskus.launcher.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.arkikeskus.launcher.model.BatteryStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/** Streams battery percent + charging state from the sticky ACTION_BATTERY_CHANGED broadcast. */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sharing = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val status: Flow<BatteryStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let { trySend(parse(it)) }
            }
        }
        // ACTION_BATTERY_CHANGED is sticky and registerReceiver RETURNS the current sticky value —
        // send it ourselves instead of trusting the registration-time redelivery: on API < 33 the
        // ContextCompat NOT_EXPORTED emulation suppresses that redelivery on some OEMs (Samsung
        // A40: the battery widget stayed blank until the next battery tick, about a minute).
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { trySend(parse(it)) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
        // One receiver however many widgets show the battery (the clock, the status bar, the
        // battery widget each ask), kept for a few seconds across a screen change.
        .shareIn(sharing, SharingStarted.WhileSubscribed(5_000L), replay = 1)

    private fun parse(intent: Intent): BatteryStatus {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        return BatteryStatus(
            percent = percent,
            charging = charging,
            temperatureC = temperatureC,
            fullInMinutes = predictFullInMinutes(percent, charging),
        )
    }

    // Charge prediction: the last few minutes of (time, percent) readings while charging give a
    // charge rate; the missing percent at that rate is the estimate. In memory only — the broadcast
    // ticks every few seconds while charging, so the history has depth within a minute.
    private val chargeHistory = ArrayDeque<Pair<Long, Int>>()

    private fun predictFullInMinutes(percent: Int, charging: Boolean): Int? {
        if (!charging || percent < 0) {
            chargeHistory.clear()
            return null
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val last = chargeHistory.lastOrNull()
        if (last == null || percent != last.second || now - last.first >= 5_000L) {
            chargeHistory.addLast(now to percent)
        }
        while (chargeHistory.size > 2 && now - chargeHistory.first().first > HISTORY_MS) {
            chargeHistory.removeFirst()
        }
        val first = chargeHistory.firstOrNull() ?: return null
        val spanMs = now - first.first
        if (chargeHistory.size < 2 || spanMs < 10_000L) return null
        val gained = percent - first.second
        if (gained <= 0) return null
        val remaining = (100 - percent).coerceAtLeast(0)
        if (remaining == 0) return 0
        val minutes = (remaining / gained.toDouble() * spanMs / 60_000.0).toInt()
        // A wildly optimistic rate (just plugged in) would promise minutes that never come; the
        // cap keeps the line plausible until the history has real depth.
        return minutes.takeIf { it in 1..FULL_CAP_MINUTES }
    }

    private companion object {
        /** How much charging history the rate estimate looks at. */
        const val HISTORY_MS = 10 * 60_000L
        /** The estimate never claims more than this many minutes to full. */
        const val FULL_CAP_MINUTES = 8 * 60
    }
}
