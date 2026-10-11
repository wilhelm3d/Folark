package org.arkikeskus.launcher.ui.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
