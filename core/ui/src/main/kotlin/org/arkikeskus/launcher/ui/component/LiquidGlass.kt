package org.arkikeskus.launcher.ui.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism Aesthetics frosted Liquid Glass container sheet.
 *
 * Uses hardware-accelerated [RenderEffect.createBlurEffect] on API 31+ with frosted
 * translucency fallback on API < 31.
 *
 * Features liquid glass visual cues:
 * - Frosted dark tint overlay ([darkTintAlpha]).
 * - Subtle top glass border highlight ([borderWidth] with [borderBrush]).
 * - Smooth rounded glass corners ([shape]).
 *
 * CRITICAL: The blur and frosted glass tint apply ONLY to the background card, NOT to the
 * child content. Icons, text, and other foreground elements inside [content] remain 100% crisp
 * and razor-sharp on top.
 */
@Composable
fun LiquidGlassContainer(
    blurRadiusDp: Float,
    darkTintAlpha: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    borderWidth: Dp = 1.dp,
    borderBrush: Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.3f),
            Color.Transparent,
        )
    ),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        // 1. Background Liquid Glass Sheet
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusDp > 0f) {
                        val radiusPx = blurRadiusDp.dp.toPx()
                        if (radiusPx > 0f) {
                            renderEffect = RenderEffect.createBlurEffect(
                                radiusPx,
                                radiusPx,
                                Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
                }
                .then(
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurRadiusDp > 0f) {
                        Modifier.blur(blurRadiusDp.dp)
                    } else {
                        Modifier
                    }
                )
                .background(
                    color = Color.Black.copy(alpha = darkTintAlpha.coerceIn(0f, 0.9f)),
                    shape = shape,
                )
                .border(
                    width = borderWidth,
                    brush = borderBrush,
                    shape = shape,
                )
        )

        // 2. Crisp Foreground Content (NOT blurred or tinted)
        content()
    }
}
