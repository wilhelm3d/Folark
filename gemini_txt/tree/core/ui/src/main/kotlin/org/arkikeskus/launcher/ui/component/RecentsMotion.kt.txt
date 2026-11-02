package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The motion of Folark Recents (Settings ▸ Recents ▸ Animations): how the cards come in, and how
 * a card answers when it is picked for split view. Shared by Recents and its settings preview.
 *
 * Everything runs in the draw phase (graphicsLayer / drawWithContent), so a playing animation
 * never recomposes the cards.
 */
object RecentsMotion {
    /** How long one card's entrance takes at speed 1, and the delay between cards. */
    const val ENTER_MS = 420
    const val STAGGER_MS = 45
    const val SELECT_MS = 520

    fun durationMs(base: Int, speed: Float): Int = (base / speed.coerceIn(0.25f, 3f)).toInt().coerceAtLeast(1)
}

/**
 * The card at [index] (in a grid of [columns]) comes in with [effect], one of RECENTS_ENTER_*.
 * [playKey] restarts it (a new opening of Recents, or the preview's replay).
 */
fun Modifier.recentsEnter(effect: String, index: Int, columns: Int, speed: Float, playKey: Any?): Modifier = composed {
    if (effect == LauncherSettings.RECENTS_ENTER_NONE) return@composed this
    val progress = remember(playKey) { Animatable(0f) }
    val col = if (columns > 0) index % columns else 0
    val row = if (columns > 0) index / columns else index
    LaunchedEffect(playKey, effect) {
        progress.snapTo(0f)
        val order = when (effect) {
            LauncherSettings.RECENTS_ENTER_CASCADE -> row + col
            LauncherSettings.RECENTS_ENTER_ZOOM -> 0
            else -> index
        }
        delay(RecentsMotion.durationMs(RecentsMotion.STAGGER_MS, speed).toLong() * order.coerceAtMost(12))
        val ms = RecentsMotion.durationMs(RecentsMotion.ENTER_MS, speed)
        when (effect) {
            LauncherSettings.RECENTS_ENTER_FAN, LauncherSettings.RECENTS_ENTER_CASCADE ->
                progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow * speed.coerceIn(0.4f, 3f)))
            else -> progress.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
        }
    }
    val center = (columns - 1) / 2f
    graphicsLayer {
        val p = progress.value
        val q = 1f - p
        when (effect) {
            LauncherSettings.RECENTS_ENTER_RISE -> {
                translationY = q * 90.dp.toPx()
                alpha = p.coerceIn(0f, 1f)
            }
            LauncherSettings.RECENTS_ENTER_ZOOM -> {
                val s = 0.82f + 0.18f * p
                scaleX = s; scaleY = s
                alpha = p.coerceIn(0f, 1f)
            }
            LauncherSettings.RECENTS_ENTER_CASCADE -> {
                translationY = -q * 70.dp.toPx()
                rotationZ = -q * 7f
                alpha = p.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
            }
            LauncherSettings.RECENTS_ENTER_FLIP -> {
                rotationY = q * 90f
                cameraDistance = 14f * density
                alpha = if (p < 0.05f) 0f else 1f
            }
            LauncherSettings.RECENTS_ENTER_FAN -> {
                // From a stack in the middle out to its place.
                translationX = q * (center - col) * size.width
                translationY = q * 40.dp.toPx()
                rotationZ = q * (col - center) * 9f
                val s = 0.6f + 0.4f * p
                scaleX = s; scaleY = s
                alpha = (p * 1.6f).coerceIn(0f, 1f)
            }
            LauncherSettings.RECENTS_ENTER_FOCUS -> {
                val r = q * 26.dp.toPx()
                renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
                val s = 1.08f - 0.08f * p
                scaleX = s; scaleY = s
                alpha = p.coerceIn(0f, 1f)
            }
        }
    }
}

/** The backdrop of Recents fades in with the cards. */
@Composable
fun rememberRecentsBackdrop(effect: String, speed: Float, playKey: Any?): () -> Float {
    val a = remember(playKey) { Animatable(if (effect == LauncherSettings.RECENTS_ENTER_NONE) 1f else 0f) }
    LaunchedEffect(playKey) {
        a.animateTo(1f, tween(RecentsMotion.durationMs(RecentsMotion.ENTER_MS, speed), easing = LinearEasing))
    }
    return { a.value }
}

/**
 * A card answering a pick in split view with [effect] (one of RECENTS_SELECT_*). [slot] is its
 * place: 0 left, 1 middle or right, ... (it decides which way Tilt leans; -1 when not picked).
 */
fun Modifier.recentsSelect(
    effect: String,
    selected: Boolean,
    slot: Int,
    slots: Int,
    accent: Color,
    corner: Dp,
    speed: Float,
): Modifier = composed {
    if (effect == LauncherSettings.RECENTS_SELECT_NONE) return@composed this
    val ms = RecentsMotion.durationMs(RecentsMotion.SELECT_MS, speed)
    // A one-shot 0→1 each time the card is picked; and a held 0/1 for as long as it stays picked.
    val shot = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            shot.snapTo(0f)
            shot.animateTo(1f, tween(ms, easing = LinearEasing))
        }
    }
    val held by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow * speed.coerceIn(0.4f, 3f)),
        label = "held",
    )
    val breathe = if (effect == LauncherSettings.RECENTS_SELECT_GLOW && selected) {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween((ms * 2.4f).toInt()), RepeatMode.Reverse), label = "breathe")
    } else null
    // Which way Tilt leans: the left slot to the left, the last split slot to the right.
    val lean = when {
        slot < 0 -> 0f
        slot == 0 -> -1f
        slots >= 3 && slot == 1 -> 0f
        else -> 1f
    }

    this
        .graphicsLayer {
            val s = shot.value
            when (effect) {
                LauncherSettings.RECENTS_SELECT_POP -> {
                    val bump = if (selected) sin(s * PI).toFloat() * 0.07f else 0f
                    val k = 1f + bump - held * 0.02f
                    scaleX = k; scaleY = k
                }
                LauncherSettings.RECENTS_SELECT_LIFT -> {
                    translationY = -held * 12.dp.toPx()
                    shadowElevation = held * 18.dp.toPx()
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                    clip = false
                    val k = 1f + held * 0.025f
                    scaleX = k; scaleY = k
                }
                LauncherSettings.RECENTS_SELECT_TILT -> {
                    rotationY = held * lean * 8f
                    rotationX = held * (if (lean == 0f) -5f else 0f)
                    cameraDistance = 16f * density
                    val k = 1f - held * 0.03f
                    scaleX = k; scaleY = k
                }
                LauncherSettings.RECENTS_SELECT_JIGGLE -> {
                    // A shake that dies away.
                    rotationZ = if (selected) sin(s * 6f * PI).toFloat() * 4.5f * exp(-3.2f * s) else 0f
                }
                else -> {}
            }
        }
        .drawWithContent {
            drawContent()
            val r = corner.toPx()
            when (effect) {
                LauncherSettings.RECENTS_SELECT_RING -> if (selected && shot.value < 1f) {
                    // A ring of the accent colour spreading out from the card.
                    val s = shot.value
                    val grow = s * 22.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = (1f - s) * 0.9f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(r + grow),
                        style = Stroke(width = (3.dp.toPx() * (1f - s)).coerceAtLeast(0.5f)),
                    )
                }
                LauncherSettings.RECENTS_SELECT_GLOW -> if (held > 0.01f) {
                    // Soft halo: a few widening strokes, breathing while picked.
                    val b = breathe?.value ?: 0f
                    for (i in 1..4) {
                        val g = i * (2.5f + b * 2f).dp.toPx()
                        drawRoundRect(
                            color = accent.copy(alpha = held * (0.34f - i * 0.07f).coerceAtLeast(0.03f)),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + g * 2, size.height + g * 2),
                            cornerRadius = CornerRadius(r + g),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
                else -> {}
            }
        }
}
