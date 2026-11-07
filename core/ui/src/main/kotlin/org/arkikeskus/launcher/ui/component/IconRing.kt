package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * What a ring around an app's icon shows: the progress of something it is doing (a download, an
 * upload: [progress] 0..1, or [indeterminate]), a running timer or stopwatch ([chronometerBase],
 * the ring ticks round once a minute), in the app's [color] when it gave one.
 */
@Immutable
data class IconRing(
    val progress: Float? = null,
    val indeterminate: Boolean = false,
    val chronometerBase: Long = 0L,
    val color: Int? = null,
)

/**
 * The rings on this surface: [mode] (LauncherSettings.RING_*) and the running ones by badge key.
 * Each app's ring is its own state, so a download ticking along redraws that one icon, not every
 * icon on home and in the drawer.
 */
@androidx.compose.runtime.Stable
class IconRings(val mode: String) {
    private val states = HashMap<String, androidx.compose.runtime.MutableState<IconRing?>>()

    /** [key]'s ring now (read in composition: only that icon follows it). */
    fun ring(key: String): IconRing? = states.getOrPut(key) { androidx.compose.runtime.mutableStateOf(null) }.value

    /** The running rings are [next] now; only the ones that changed are written. */
    fun update(next: Map<String, IconRing>) {
        for ((key, state) in states) {
            val value = next[key]
            if (state.value != value) state.value = value
        }
        for ((key, value) in next) if (key !in states) states[key] = androidx.compose.runtime.mutableStateOf(value)
    }
}

val LocalIconRings = compositionLocalOf<IconRings?> { null }

/**
 * Draws the ring for [ring] (or, with [unread] and "progress & unread", a full thin ring) just
 * outside the icon. Nothing at all while there is nothing to show, so icons without a ring cost
 * one lookup.
 */
fun Modifier.iconRing(ring: IconRing?, unread: Boolean, mode: String, accent: Color): Modifier {
    if (mode == LauncherSettings.RING_OFF) return this
    val showUnread = unread && mode == LauncherSettings.RING_ALL
    if (ring == null && !showUnread) return this
    return composed {
        val color = ring?.color?.let { Color(it).copy(alpha = 1f) } ?: accent
        // How full the ring is: the progress, the seconds of a running timer, or all of it.
        val sweep = when {
            ring == null -> 1f
            ring.progress != null && !ring.indeterminate -> animateFloatAsState(ring.progress.coerceIn(0f, 1f), label = "ring").value
            ring.chronometerBase != 0L -> rememberSecondsSweep(ring.chronometerBase)
            else -> 0.28f
        }
        // Busy without an end: a short arc going round for a while after it appears, then resting
        // (a download can stay busy for minutes; every frame would redraw the icon). The angle is
        // read only while drawing, so only the ring is drawn again, nothing recomposes.
        val busy = ring?.indeterminate == true || (ring != null && ring.progress == null && ring.chronometerBase == 0L)
        val spinState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        if (busy) {
            LaunchedEffect(Unit) {
                val start = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    if (now - start > SPIN_AWAKE_NS) break
                    spinState.floatValue = ((now - start) / 1_000_000f / SPIN_PERIOD_MS * 360f) % 360f
                }
            }
        }
        drawWithContent {
            drawContent()
            val stroke = (size.minDimension * 0.055f).coerceAtLeast(2.dp.toPx())
            val gap = 2.5.dp.toPx() + stroke / 2f
            val topLeft = Offset(-gap, -gap)
            val arcSize = Size(size.width + gap * 2, size.height + gap * 2)
            // The track: faint, so a partly full ring reads as "so far of all".
            if (ring != null) {
                drawArc(
                    color = color.copy(alpha = 0.22f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke),
                )
            }
            drawArc(
                color = if (ring == null) color.copy(alpha = 0.85f) else color,
                startAngle = -90f + (if (busy) spinState.floatValue else 0f),
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(if (ring == null) stroke * 0.7f else stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** 0..1 through the current minute of a timer that started (or ends) at [base], ticking each second
 *  on the shared clock (paused while home is behind an app). */
@Composable
private fun rememberSecondsSweep(base: Long): Float {
    val now by rememberSecondTick(ticking = true)
    val seconds = (kotlin.math.abs(now - base) / 1000L) % 60L
    return (seconds + 1) / 60f
}

/** One turn of the busy arc, and how long it turns before it rests. */
private const val SPIN_PERIOD_MS = 1400f
private const val SPIN_AWAKE_NS = 30_000_000_000L
