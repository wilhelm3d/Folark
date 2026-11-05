package org.arkikeskus.launcher.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.flow.first

/**
 * Whether something is inside the window right now (E28): looping previews in a scrolled list
 * run only while they can be seen. Put [onScreen] on the thing, read the state, or [awaitOnScreen]
 * before each loop.
 */
@Composable
fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(true) }

fun Modifier.onScreen(state: MutableState<Boolean>): Modifier = onGloballyPositioned { c ->
    val top = c.positionInWindow().y
    val bottom = top + c.size.height
    val windowHeight = c.findRootCoordinates().size.height
    val visible = bottom > 0f && top < windowHeight
    if (state.value != visible) state.value = visible
}

/** Suspends while [state] says it can't be seen. */
suspend fun awaitOnScreen(state: MutableState<Boolean>) {
    if (!state.value) snapshotFlow { state.value }.first { it }
}
