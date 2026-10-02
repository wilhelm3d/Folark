package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Oppo Aquamorphic touch feedback modifier applying a bouncy spring scale-down (0.92f)
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

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "aquamorphic_scale",
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "aquamorphic_alpha",
    )

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
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}

/**
 * App launch spring scale-zoom feedback modifier when tapping an icon to launch an app.
 */
fun Modifier.appLaunchZoom(
    launching: Boolean,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed Modifier

    val scale by animateFloatAsState(
        targetValue = if (launching) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "app_launch_zoom_scale",
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
