package org.arkikeskus.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Calls [onTick] now and then at the exact start of every minute using Android's system broadcast, 
 * but only while the launcher is on screen. Clocks and "x minutes ago" texts use it
 * to ensure all UI elements update perfectly in sync without fragmenting CPU wakeups.
 */
@Composable
internal fun MinuteTicker(onTick: (Long) -> Unit) {
    val context = LocalContext.current
    val currentOnTick by rememberUpdatedState(onTick)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK) {
                    currentOnTick(System.currentTimeMillis())
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Initial tick
        currentOnTick(System.currentTimeMillis())

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
