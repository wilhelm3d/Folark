package org.arkikeskus.launcher.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.hypot

/**
 * Home appearing as the phone is unlocked, in one of the unlock styles (Settings ▸ each display ▸
 * Also when unlocking ▸ Unlock style). [progress] runs 0 → 1 from the moment the lock screen starts
 * to leave (told by SystemUI when Folark's Vector module runs there). At 1 nothing is drawn
 * differently, so this costs nothing the rest of the time.
 *
 * - Zoom: home comes from slightly in front of the glass and settles into place.
 * - Rise: home rises a little from below as it fades in.
 * - Bloom: home opens out as a soft circle from the bottom, where the finger lifted the lock screen.
 * - Sweep: home is uncovered from the bottom up behind a soft edge, like a blind going up.
 * - Focus: home comes into focus, from blurred to sharp.
 */
fun Modifier.unlockReveal(style: String, progress: () -> Float): Modifier = this
    .graphicsLayer {
        val t = progress()
        if (t >= 1f) return@graphicsLayer
        val fade = (t * 1.6f).coerceIn(0f, 1f)
        when (style) {
            LauncherSettings.UNLOCK_STYLE_ZOOM -> {
                val k = 1.12f - 0.12f * t
                scaleX = k; scaleY = k
                alpha = fade
            }
            LauncherSettings.UNLOCK_STYLE_RISE -> {
                translationY = (1f - t) * size.height * 0.07f
                val k = 0.97f + 0.03f * t
                scaleX = k; scaleY = k
                alpha = fade
            }
            LauncherSettings.UNLOCK_STYLE_FOCUS -> {
                val k = 1.04f - 0.04f * t
                scaleX = k; scaleY = k
                alpha = fade
                val blur = (1f - t) * 28f * density
                if (Build.VERSION.SDK_INT >= 31 && blur > 0.5f) {
                    renderEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }
            LauncherSettings.UNLOCK_STYLE_BLOOM, LauncherSettings.UNLOCK_STYLE_SWEEP -> {
                // Drawn into its own layer, so the mask below cuts home out over the wallpaper.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            else -> alpha = t
        }
    }
    .drawWithContent {
        val t = progress()
        if (t >= 1f || (style != LauncherSettings.UNLOCK_STYLE_BLOOM && style != LauncherSettings.UNLOCK_STYLE_SWEEP)) {
            drawContent()
            return@drawWithContent
        }
        drawContent()
        if (style == LauncherSettings.UNLOCK_STYLE_BLOOM) {
            val center = Offset(size.width / 2f, size.height * 0.94f)
            val far = hypot(size.width / 2f, size.height * 0.94f) * 1.1f
            val radius = (far * t).coerceAtLeast(1f)
            drawRect(
                Brush.radialGradient(
                    0f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent,
                    center = center, radius = radius,
                ),
                blendMode = BlendMode.DstIn,
            )
        } else {
            // The edge moves from below the bottom to above the top, with a soft band.
            val band = size.height * 0.22f
            val edge = size.height + band - (size.height + band * 2) * t
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent, 1f to Color.Black,
                    startY = edge - band / 2, endY = edge + band / 2,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
