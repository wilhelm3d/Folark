package org.arkikeskus.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far the app drawer is open, 0 (closed) … 1 (open), and what moves it (split out of
 * LauncherShell.kt): a drag follows the finger, letting go settles it (a fling goes the way it was
 * flung), and open / close animate. One coroutine moves it at a time: each new drag or settle
 * cancels the one before, so a fast flick can't pile up competing jobs that leave the drawer
 * lagging behind the finger.
 */
@Stable
internal class DrawerSlide(private val scope: CoroutineScope) {
    // Bounds keep a high-initial-velocity settle (the fling-carried spring below) from overshooting
    // past the ends — an overshoot past 1 would briefly translate the drawer above the screen top.
    private val animated = Animatable(0f).apply { updateBounds(0f, 1f) }
    private val settleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    private var dragging by mutableStateOf(false)
    private var dragProgress by mutableFloatStateOf(0f)
    private var job: Job? = null

    /** The height a drag covers from closed to open: the launcher window's. */
    var heightPx by mutableFloatStateOf(1f)

    /** Where the drawer is now: under the finger while dragged, else where the animation has it. */
    val progress: Float by derivedStateOf { if (dragging) dragProgress else animated.value }

    /** Open at all (or being pulled up). */
    val isOpen: Boolean by derivedStateOf { progress > 0.001f }

    /** Animates to [target] (1 open, 0 closed). */
    fun animateTo(target: Float) {
        dragging = false
        job?.cancel()
        job = scope.launch { animated.animateTo(target, settleSpring) }
    }

    /** The finger moved [dyPx] (up is negative, and opens). */
    fun drag(dyPx: Float) {
        if (!dragging) {
            dragging = true
            dragProgress = animated.value
        }
        val height = heightPx.coerceAtLeast(1f)
        val target = (dragProgress - dyPx / height).coerceIn(0f, 1f)
        dragProgress = target
        job?.cancel()
        job = scope.launch { animated.snapTo(target) }
    }

    /**
     * The finger let go at [velocityPxPerSec]. Launcher3-style: a fling (release speed past the
     * threshold) goes the way it was flung whatever the distance; otherwise the drawer settles by
     * how far it was dragged.
     */
    fun settle(velocityPxPerSec: Float) {
        val current = if (dragging) dragProgress else animated.value
        dragging = false
        val height = heightPx.coerceAtLeast(1f)
        val target = when {
            velocityPxPerSec < -FLING_THRESHOLD -> 1f // fast up → open
            velocityPxPerSec > FLING_THRESHOLD -> 0f // fast down → close
            else -> if (current > 0.4f) 1f else 0f
        }
        job?.cancel()
        job = scope.launch {
            animated.snapTo(current)
            // Carry the finger's release velocity into the spring (converted to progress units/s;
            // downward px velocity = negative progress velocity). Starting the spring from rest made a
            // fling-to-close visibly hesitate before accelerating — the "drawer lags coming down" feel.
            animated.animateTo(target, settleSpring, initialVelocity = -velocityPxPerSec / height)
        }
    }

    /** An app is being dragged out: the drawer is gone at once (it stays composed for the drag). */
    fun snapClosed() {
        dragging = false
        scope.launch { animated.snapTo(0f) }
    }

    /** Follows the system back gesture: [value] 1 open … 0 closed, as the swipe goes. */
    suspend fun scrubTo(value: Float) = animated.snapTo(value)

    private companion object {
        /** Release speed (px/s) from which a release counts as a fling. */
        const val FLING_THRESHOLD = 600f
    }
}

@Composable
internal fun rememberDrawerSlide(): DrawerSlide {
    val scope = rememberCoroutineScope()
    return remember { DrawerSlide(scope) }
}
