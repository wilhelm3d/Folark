package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** How buttons answer a press (Settings ▸ Recents ▸ Animations ▸ Button press). */
data class PressStyle(val effect: String, val accent: Color, val speed: Float = 1f)

/** The press style for buttons under this subtree; null = a plain tap (Android's ripple). */
val LocalPressStyle = compositionLocalOf<PressStyle?> { null }

/**
 * A tap target that answers the finger with [LocalPressStyle]'s effect (one of PRESS_*):
 * - Shrink: sinks in while held, springs back.
 * - Bounce: sinks deeper and overshoots on release.
 * - Glow: a halo of the accent colour while held.
 * - Ripple: a ring of the accent colour spreading from the finger.
 * - Tilt: leans in towards the finger, like a physical key.
 * - Jelly: squashes and wobbles back on release.
 * Without a style (or None) it is a plain [clickable].
 */
fun Modifier.pressClick(onClick: () -> Unit): Modifier = composed {
    val style = LocalPressStyle.current
    if (style == null || style.effect == LauncherSettings.PRESS_NONE) return@composed clickable(onClick = onClick)
    val source = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val held = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    var at by remember { mutableStateOf(Offset.Unspecified) }
    val speed = style.speed.coerceIn(0.25f, 3f)
    fun ms(base: Int) = (base / speed).toInt().coerceAtLeast(1)
    LaunchedEffect(source, style.effect) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    at = interaction.pressPosition
                    scope.launch { held.animateTo(1f, tween(ms(110))) }
                    if (style.effect == LauncherSettings.PRESS_RIPPLE) scope.launch { pulse.snapTo(0f); pulse.animateTo(1f, tween(ms(520))) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        when (style.effect) {
                            LauncherSettings.PRESS_BOUNCE ->
                                held.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium * speed))
                            else -> held.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow * speed))
                        }
                    }
                    if (style.effect == LauncherSettings.PRESS_JELLY) scope.launch { pulse.snapTo(0f); pulse.animateTo(1f, tween(ms(560))) }
                }
            }
        }
    }
    this
        .graphicsLayer {
            val h = held.value
            when (style.effect) {
                LauncherSettings.PRESS_SHRINK -> { val k = 1f - 0.07f * h; scaleX = k; scaleY = k }
                LauncherSettings.PRESS_BOUNCE -> { val k = 1f - 0.12f * h; scaleX = k; scaleY = k }
                LauncherSettings.PRESS_GLOW -> { val k = 1f + 0.03f * h; scaleX = k; scaleY = k }
                LauncherSettings.PRESS_TILT -> {
                    // Lean towards where the finger is.
                    val p = at
                    if (p != Offset.Unspecified && size.width > 0f && size.height > 0f) {
                        val nx = (p.x / size.width - 0.5f) * 2f
                        val ny = (p.y / size.height - 0.5f) * 2f
                        rotationY = nx * 14f * h
                        rotationX = -ny * 14f * h
                        cameraDistance = 12f * density
                    }
                    val k = 1f - 0.03f * h
                    scaleX = k; scaleY = k
                }
                LauncherSettings.PRESS_JELLY -> {
                    val q = pulse.value
                    val wobble = if (q < 1f) sin(q * 3f * PI).toFloat() * 0.12f * exp(-3f * q) else 0f
                    scaleX = 1f + 0.08f * h + wobble
                    scaleY = 1f - 0.1f * h - wobble
                }
                else -> {}
            }
        }
        .drawWithContent {
            when (style.effect) {
                LauncherSettings.PRESS_GLOW -> {
                    val h = held.value
                    if (h > 0.01f) {
                        val r = size.minDimension / 2f
                        for (i in 1..3) {
                            val g = i * 3f * density * h
                            drawRoundRect(
                                style.accent.copy(alpha = (0.32f - i * 0.08f) * h),
                                topLeft = Offset(-g, -g),
                                size = androidx.compose.ui.geometry.Size(size.width + 2 * g, size.height + 2 * g),
                                cornerRadius = CornerRadius(r + g),
                            )
                        }
                    }
                    drawContent()
                }
                LauncherSettings.PRESS_RIPPLE -> {
                    drawContent()
                    val q = pulse.value
                    if (q < 1f && at != Offset.Unspecified) {
                        val far = hypot(size.width, size.height)
                        clipRect {
                            drawCircle(style.accent.copy(alpha = 0.35f * (1f - q)), radius = far * q, center = at)
                        }
                    }
                }
                else -> drawContent()
            }
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}
