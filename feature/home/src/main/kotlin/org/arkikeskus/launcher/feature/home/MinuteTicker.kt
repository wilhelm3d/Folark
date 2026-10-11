package org.arkikeskus.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Calls [onTick] now and then at the start of every minute, but only while the launcher is on
 * screen: the loop is cancelled when the activity stops (another app in front, screen off) and
 * starts again, with an immediate tick, when it returns. Clocks and "x minutes ago" texts use it
 * instead of a loop of their own that kept waking the process in the background.
 */
@Composable
internal fun MinuteTicker(onTick: (Long) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTick by rememberUpdatedState(onTick)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                currentOnTick(now)
                delay(60_000L - now % 60_000L + 50L)
            }
        }
    }
}
