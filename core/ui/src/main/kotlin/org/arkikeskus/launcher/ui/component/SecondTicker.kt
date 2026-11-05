package org.arkikeskus.launcher.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * One clock for every live pill and card that shows a running time (E21): it ticks on each whole
 * second while anything watches, instead of one loop per pill.
 */
object SecondTicker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val now: StateFlow<Long> = flow {
        while (true) {
            val t = System.currentTimeMillis()
            emit(t)
            delay(1_000L - t % 1_000L)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(1_000), System.currentTimeMillis())
}

/** The current time, updated every second while [ticking]; a fixed time otherwise. */
@Composable
fun rememberSecondTick(ticking: Boolean): State<Long> =
    if (ticking) SecondTicker.now.collectAsState() else remember { mutableLongStateOf(System.currentTimeMillis()) }
