package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Fluid Dynamic Motion touch feedback modifier applying a bouncy spring scale-down (0.92f)
 * on touch down using spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
 * with an organic liquid press indication.
 */
fun Modifier.aquamorphicTouch(
    enabled: Boolean = true,
    interactionSource: InteractionSource? = null,
    onTap: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled) return@composed Modifier

    var isPressedByPointer by remember { mutableStateOf(false) }

    val isPressed = if (interactionSource != null) {
        val pressedState by interactionSource.collectIsPressedAsState()
        pressedState || isPressedByPointer
    } else {
        isPressedByPointer
    }

    // The same springs as before, started only when the press changes: at rest an icon keeps no
    // animation coroutine alive (animate*AsState kept two per icon, on every page and in the drawer).
    val scale = remember { Animatable(if (isPressed) 0.92f else 1.0f) }
    val fade = remember { Animatable(if (isPressed) 0.88f else 1.0f) }
    val scope = rememberCoroutineScope()
    val lastPressed = remember { booleanArrayOf(isPressed) }
    SideEffect {
        if (lastPressed[0] != isPressed) {
            lastPressed[0] = isPressed
            scope.launch { scale.animateTo(if (isPressed) 0.92f else 1.0f, PressSpring) }
            scope.launch { fade.animateTo(if (isPressed) 0.88f else 1.0f, PressSpring) }
        }
    }

    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressedByPointer = true
                    var isTap = true
                    val slop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            if ((change.position - down.position).getDistance() > slop) {
                                isTap = false
                            }
                            if (!change.pressed) {
                                isPressedByPointer = false
                            }
                        } else {
                            isPressedByPointer = false
                        }
                    } while (event.changes.any { it.pressed })
                    if (isTap && onTap != null) {
                        onTap()
                    }
                } finally {
                    isPressedByPointer = false
                }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = fade.value
        }
}

private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * App launch spring scale-zoom feedback modifier when tapping an icon to launch an app.
 */
fun Modifier.appLaunchZoom(
    launching: Boolean,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed Modifier

    val target = if (launching) 1.25f else 1.0f
    val scale = remember { Animatable(target) }
    val scope = rememberCoroutineScope()
    val requested = remember { floatArrayOf(target) }
    SideEffect {
        if (requested[0] != target) {
            requested[0] = target
            scope.launch {
                scale.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }

    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
