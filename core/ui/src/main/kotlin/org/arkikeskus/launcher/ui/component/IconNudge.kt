package org.arkikeskus.launcher.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** A new notification for an app (its badgeKey), to nudge its icon; [id] tells repeats apart. */
data class IconNudgeEvent(val badgeKey: String, val color: Int?, val time: Long, val id: Long)

/** The nudges under this subtree, the effect they play and how soft (Settings ▸ Notifications); null = off. */
class IconNudges(val effect: String, val softness: Float, val events: SnapshotStateMap<String, IconNudgeEvent>)

val LocalIconNudges = compositionLocalOf<IconNudges?> { null }

/**
 * One moment of a nudge: how the icon moves and the ring around it. Plain numbers so the home
 * screen (Compose) and the taskbar (views) play the same effect.
 */
class NudgeFrame {
    /** Shift as a fraction of the icon's size. */
    var dx = 0f
    var dy = 0f
    var scaleX = 1f
    var scaleY = 1f
    var rotation = 0f
    /** Ring around the icon: its size relative to the icon (1 = touching), opacity, and whether it
     *  is a soft halo (glow) rather than a thin ring (ripple). */
    var ringScale = 1f
    var ringAlpha = 0f
    var ringSoft = false
    /** Where the halo's colour starts fading (0..1 of its radius): lower is softer. */
    var ringCore = 0.55f
    /** How strongly the icon is washed with the app's colour (0..1). */
    var tintAlpha = 0f

    fun reset() {
        dx = 0f; dy = 0f; scaleX = 1f; scaleY = 1f; rotation = 0f; ringScale = 1f; ringAlpha = 0f; ringSoft = false
        ringCore = 0.55f; tintAlpha = 0f
    }
}

object IconNudge {
    /** How long one nudge lasts. */
    const val DURATION_MS = 1_100

    /** A nudge older than this (home wasn't in view when it came) is not played late. */
    const val STALE_MS = 2_500L

    /** Fills [out] for [effect] at [t] (0..1); [softness] (0..1) makes Glow and Pulse gentler. */
    fun frame(effect: String, t: Float, out: NudgeFrame, softness: Float = 0.5f) {
        val soft = softness.coerceIn(0f, 1f)
        out.reset()
        if (t <= 0f || t >= 1f) return
        val fade = 1f - t
        when (effect) {
            LauncherSettings.NUDGE_BOUNCE -> {
                // Two hops, the second smaller, squashing a little on landing.
                val hop = abs(sin(2.0 * PI * t).toFloat())
                out.dy = -0.26f * hop * fade
                val land = (1f - hop).let { it * it * it } * fade
                out.scaleX = 1f + 0.10f * land
                out.scaleY = 1f - 0.10f * land
            }
            LauncherSettings.NUDGE_WIGGLE -> {
                out.rotation = 13f * sin(2.0 * PI * 3.0 * t).toFloat() * fade
            }
            LauncherSettings.NUDGE_PULSE -> {
                // Two heartbeats: a sharp thump when hard, a gentle swell when soft.
                val raw = abs(sin(2.0 * PI * t).toFloat())
                val beat = raw.toDouble().pow(1.0 + 2.5 * (1.0 - soft)).toFloat()
                val s = 1f + (0.24f - 0.17f * soft) * beat * (1f - 0.4f * t)
                out.scaleX = s
                out.scaleY = s
            }
            LauncherSettings.NUDGE_GLOW -> {
                // A halo in the app's colour swelling and fading: tight and bright when hard,
                // wide and faint when soft.
                out.ringSoft = true
                out.ringAlpha = sin(PI * t).toFloat() * (1f - 0.45f * soft)
                out.ringScale = 1.02f + (0.08f + 0.4f * soft) * t + 0.15f * soft
                out.ringCore = 0.8f - 0.65f * soft
                val s = 1f + 0.05f * sin(PI * t).toFloat()
                out.scaleX = s
                out.scaleY = s
            }
            LauncherSettings.NUDGE_TINT -> {
                // The icon fills with the app's colour, twice, then clears.
                val wave = abs(sin(2.0 * PI * t).toFloat())
                out.tintAlpha = (0.85f - 0.45f * soft) * wave * (1f - 0.3f * t)
            }
            LauncherSettings.NUDGE_RIPPLE -> {
                // Two rings spreading out from the icon.
                val wave = (t * 2f) % 1f
                out.ringScale = 1f + 0.9f * wave
                out.ringAlpha = (1f - wave) * (if (t < 0.5f) 1f else 0.6f)
            }
            LauncherSettings.NUDGE_JELLY -> {
                // Squash and stretch, settling.
                val w = sin(2.0 * PI * 2.0 * t).toFloat() * fade
                out.scaleX = 1f + 0.18f * w
                out.scaleY = 1f - 0.18f * w
            }
        }
    }
}

/** Plays [effect] on this icon each time [event] changes (a new notification for its app). */
@Composable
fun Modifier.iconNudge(event: IconNudgeEvent?, effect: String, softness: Float = 0.5f, enabled: Boolean = true): Modifier {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(event?.id) {
        if (event == null || !enabled) return@LaunchedEffect
        if (System.currentTimeMillis() - event.time > IconNudge.STALE_MS) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(IconNudge.DURATION_MS, easing = LinearEasing))
    }
    val frame = remember { NudgeFrame() }
    val color = Color(event?.color ?: DEFAULT_NUDGE_COLOR)
    return this
        .drawBehind {
            val t = progress.value
            if (t >= 1f) return@drawBehind
            IconNudge.frame(effect, t, frame, softness)
            if (frame.ringAlpha <= 0f) return@drawBehind
            val radius = size.minDimension / 2f * frame.ringScale
            val center = Offset(size.width / 2f, size.height / 2f)
            if (frame.ringSoft) {
                drawCircle(
                    Brush.radialGradient(
                        frame.ringCore to color.copy(alpha = 0.6f * frame.ringAlpha),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius * 1.25f,
                    ),
                    radius = radius * 1.25f,
                    center = center,
                )
            } else {
                drawCircle(color.copy(alpha = 0.8f * frame.ringAlpha), radius, center, style = Stroke(width = size.minDimension * 0.05f))
            }
        }
        .graphicsLayer {
            val t = progress.value
            if (t >= 1f) return@graphicsLayer
            IconNudge.frame(effect, t, frame, softness)
            translationX = frame.dx * size.width
            translationY = frame.dy * size.height
            scaleX = frame.scaleX
            scaleY = frame.scaleY
            rotationZ = frame.rotation
            // Squash from the bottom, as if landing.
            transformOrigin = if (effect == LauncherSettings.NUDGE_BOUNCE) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
            // The colour wash may only touch the icon's own pixels: draw it in a layer of its own.
            compositingStrategy = if (frame.tintAlpha > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val t = progress.value
            if (t >= 1f || effect != LauncherSettings.NUDGE_TINT) return@drawWithContent
            IconNudge.frame(effect, t, frame, softness)
            if (frame.tintAlpha > 0f) drawRect(color.copy(alpha = frame.tintAlpha), blendMode = BlendMode.SrcAtop)
        }
}

/** The ring's colour when the app set none. */
const val DEFAULT_NUDGE_COLOR = 0xFF8AB4F8.toInt()
