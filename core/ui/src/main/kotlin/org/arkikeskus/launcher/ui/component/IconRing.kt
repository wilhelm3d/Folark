package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        // A running timer: the shared second clock is read while drawing only (in the ring's
        // draw below), so each tick redraws the ring and nothing recomposes.
        val timerBase = ring?.chronometerBase?.takeIf { it != 0L && (ring.progress == null || ring.indeterminate) }
        val tick = if (timerBase != null) rememberSecondTick(ticking = true) else null
        val fixedSweep = when {
            ring == null -> 1f
            ring.progress != null && !ring.indeterminate -> animateFloatAsState(ring.progress.coerceIn(0f, 1f), label = "ring").value
            timerBase != null -> 0f
            else -> 0.28f
        }
        // Busy without an end: a short arc going round for a while after it appears, then resting
        // (a download can stay busy for minutes; every frame would redraw the icon). The angle is
        // read only while drawing, so only the ring is drawn again, nothing recomposes.
        val busy = ring?.indeterminate == true || (ring != null && ring.progress == null && ring.chronometerBase == 0L)
        if (busy) {
            // Every busy icon used to run its own frame loop. They share one clock now (and one
            // awake window): one ring at a time drives the frames from its own composition scope
            // (which carries Compose's frame clock); when it goes away, another busy ring takes over.
            LaunchedEffect(Unit) { BusyRingClock.run() }
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
                startAngle = -90f + (if (busy) BusyRingClock.angle.floatValue else 0f),
                sweepAngle = 360f * (if (tick != null && timerBase != null) secondsSweep(tick.value, timerBase) else fixedSweep),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(if (ring == null) stroke * 0.7f else stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** 0..1 through the current minute of a timer that started (or ends) at [base], at [now]. */
private fun secondsSweep(now: Long, base: Long): Float {
    val seconds = (kotlin.math.abs(now - base) / 1000L) % 60L
    return (seconds + 1) / 60f
}

/** One turn of the busy arc, and how long it turns before it rests. */
private const val SPIN_PERIOD_MS = 1400f
private const val SPIN_AWAKE_NS = 30_000_000_000L

/**
 * The one clock behind every busy ring: the angle is shared by all of them (they spin in step),
 * and frames run only while at least one ring is busy, for [SPIN_AWAKE_NS] after the last ring
 * became busy. Reads of [angle] happen in the draw phase, so only the rings redraw — nothing
 * recomposes. Main thread only (each ring's LaunchedEffect).
 */
private object BusyRingClock {
    val angle = mutableFloatStateOf(0f)

    /** The ring driving the frames now, or null: the next ring to look takes over. */
    private val driver = MutableStateFlow<Any?>(null)

    /** Bumped whenever a ring becomes busy: the awake window starts again from that moment. */
    private val joined = MutableStateFlow(0)

    /** Frame time (ns) the awake window started at; reset when a ring joins. */
    private var windowStart = -1L

    /** The life of one busy ring: drive the frames when nobody else does, wait otherwise. */
    suspend fun run() {
        val me = Any()
        joined.value++
        windowStart = -1L
        try {
            while (true) {
                if (driver.compareAndSet(null, me)) {
                    try {
                        drive()
                    } finally {
                        driver.compareAndSet(me, null)
                    }
                } else {
                    driver.first { it == null }
                }
            }
        } finally {
            driver.compareAndSet(me, null)
        }
    }

    /** Spins until the window is over, then rests until another ring becomes busy; never returns
     *  on its own (the ring's effect is cancelled when it stops being busy or leaves the screen). */
    private suspend fun drive() {
        while (true) {
            val seen = joined.value
            while (true) {
                val now = withFrameNanos { it }
                if (windowStart < 0) windowStart = now - (angle.floatValue / 360f * SPIN_PERIOD_MS * 1_000_000f).toLong()
                if (now - windowStart > SPIN_AWAKE_NS) break
                angle.floatValue = ((now - windowStart) / 1_000_000f / SPIN_PERIOD_MS * 360f) % 360f
            }
            // Resting: no frames until a new ring joins (which also resets the window).
            joined.first { it != seen }
        }
    }
}
