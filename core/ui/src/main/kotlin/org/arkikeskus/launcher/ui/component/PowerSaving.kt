package org.arkikeskus.launcher.ui.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True while Folark's battery saver is in effect (Settings ▸ Battery saver: always, or together
 * with the system's own battery saver). Everything that only costs power for looks checks it: the
 * weather animation, wallpaper parallax, blur, widget stacks switching by themselves, and the
 * high refresh rate.
 */
val LocalBatterySaver = staticCompositionLocalOf { false }

/** True while the app drawer fully covers the home screen, so nothing under it needs to animate. */
val LocalHomeCovered = compositionLocalOf { false }

/** Whether the system's battery saver is on, kept current. */
@Composable
fun rememberSystemPowerSave(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var on by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                on = power?.isPowerSaveMode == true
            }
        }
        // A protected system broadcast, so no export flag is needed.
        val registered = runCatching {
            context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }.isSuccess
        on = power?.isPowerSaveMode == true
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

/**
 * True while the battery is at or below [thresholdPercent] and the phone is not charging — so the
 * saver can switch itself on to stretch what's left. Reads the sticky battery broadcast.
 */
@Composable
fun rememberBatteryLow(thresholdPercent: Int): Boolean {
    val context = LocalContext.current
    var low by remember { mutableStateOf(false) }
    DisposableEffect(context, thresholdPercent) {
        fun eval(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
            low = !plugged && percent <= thresholdPercent
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = eval(intent)
        }
        // ACTION_BATTERY_CHANGED is sticky and a protected broadcast (no export flag needed).
        val sticky = runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        eval(sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return low
}

/**
 * True while Android reports the phone is running hot (thermal throttling at MODERATE or worse), so
 * the saver can ease off blur, animations and the high refresh rate to let it cool.
 */
@Composable
fun rememberDeviceHot(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var hot by remember { mutableStateOf(false) }
    DisposableEffect(power) {
        if (power == null) {
            hot = false
            return@DisposableEffect onDispose { }
        }
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            hot = status >= PowerManager.THERMAL_STATUS_MODERATE
        }
        val added = runCatching { power.addThermalStatusListener(listener); true }.getOrDefault(false)
        hot = runCatching { power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE }.getOrDefault(false)
        onDispose { if (added) runCatching { power.removeThermalStatusListener(listener) } }
    }
    return hot
}
