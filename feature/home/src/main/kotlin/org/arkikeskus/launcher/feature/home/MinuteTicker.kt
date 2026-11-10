package org.arkikeskus.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.arkikeskus.launcher.model.catching

/**
 * One `ACTION_TIME_TICK` receiver for the whole app (E8), which also refreshes on screen-on and
 * on time or time-zone changes. Clocks, "x minutes ago" texts and the
 * date all read this clock, so the system sends one broadcast a minute instead of one per
 * composable (the status bar and a clock widget on each display used to be three or four).
 */
private object MinuteClock {
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private var watchers = 0
    private var receiver: BroadcastReceiver? = null

    fun acquire(context: Context) {
        watchers++
        if (receiver != null) return
        val app = context.applicationContext
        val listening = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                // Each of these means "what the clocks show may be stale now".
                if (intent?.action in WAKE_ACTIONS) _now.value = System.currentTimeMillis()
            }
        }
        // Not only the minute tick: no tick arrives while the screen is off, so the first minute
        // after the screen comes on (and after the time or time zone was changed) would show the
        // old time until the next tick.
        val filter = IntentFilter().apply { WAKE_ACTIONS.forEach(::addAction) }
        ContextCompat.registerReceiver(app, listening, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = listening
        _now.value = System.currentTimeMillis()
    }

    private val WAKE_ACTIONS = listOf(
        Intent.ACTION_TIME_TICK,
        Intent.ACTION_SCREEN_ON,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    fun release(context: Context) {
        watchers = (watchers - 1).coerceAtLeast(0)
        if (watchers > 0) return
        receiver?.let { catching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }
}

/**
 * Calls [onTick] now and then at the exact start of every minute, while something on screen wants
 * it. Every caller shares one system receiver; the last one leaving unregisters it.
 */
@Composable
internal fun MinuteTicker(onTick: (Long) -> Unit) {
    val context = LocalContext.current
    val currentOnTick by rememberUpdatedState(onTick)
    val now by MinuteClock.now.collectAsStateWithLifecycle()

    DisposableEffect(context) {
        MinuteClock.acquire(context)
        onDispose { MinuteClock.release(context) }
    }
    LaunchedEffect(now) { currentOnTick(now) }
}
