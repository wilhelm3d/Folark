package org.arkikeskus.launcher.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass surface treatment: a translucent [tint] fill, a soft top sheen, an off-centre
 * specular glow and a light-catching rim — the cues that make a panel read as a slab of glass.
 *
 * This modifier does NOT blur what is behind it, and nothing drawn inside a launcher window can:
 * the wallpaper lives in a separate system window. The frosted backdrop comes from the window-level
 * blur ([WindowBlurBehind]), which blurs the wallpaper behind the whole launcher window; this
 * modifier supplies the glass body on top of it. On devices without window blur the tint alone
 * keeps content legible, so the look degrades to tinted glass rather than breaking.
 *
 * @param tint fill colour including its alpha (lower alpha = clearer glass).
 * @param highlight 0..1 strength of the sheen, glow and rim; 0 gives a plain tinted panel.
 * @param rimBottom how bright the rim is along the bottom edge (the top edge is 0.42).
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color,
    highlight: Float = 1f,
    rimWidth: Dp = 1.dp,
    rimBottom: Float = 0.16f,
): Modifier {
    val strength = highlight.coerceIn(0f, 1f)
    val base = this
        .clip(shape)
        .background(tint)
    if (strength <= 0f) return base
    return base
        .drawWithCache {
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.16f * strength),
                0.28f to Color.White.copy(alpha = 0.05f * strength),
                1f to Color.Transparent,
            )
            val glow = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f * strength), Color.Transparent),
                center = Offset(size.width * 0.18f, 0f),
                radius = (size.maxDimension * 0.6f).coerceAtLeast(1f),
            )
            onDrawWithContent {
                drawRect(sheen)
                drawRect(glow)
                drawContent()
            }
        }
        .border(
            width = rimWidth,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.42f * strength),
                0.5f to Color.White.copy(alpha = 0.08f * strength),
                1f to Color.White.copy(alpha = rimBottom.coerceIn(0f, 1f) * strength),
            ),
            shape = shape,
        )
}

/**
 * Glass container used by home folders and the folder dialog.
 *
 * [blurRadiusDp] controls how frosted (milky) the glass looks and [darkTintAlpha] how dark it is.
 * Earlier this applied a RenderEffect blur to its own flat background, which blurred nothing behind
 * the card and only smeared the card's edges and border; see [liquidGlass] for where real backdrop
 * blur comes from. The content is drawn on top, untouched.
 */
@Composable
fun LiquidGlassContainer(
    blurRadiusDp: Float,
    darkTintAlpha: Float,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    val frost = (blurRadiusDp / 50f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            // Frost first (a light veil), then the dark tint on top of it.
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f * frost))
            .liquidGlass(
                shape = shape,
                tint = Color.Black.copy(alpha = darkTintAlpha.coerceIn(0f, 0.9f)),
                highlight = 0.35f + 0.65f * frost,
                rimWidth = borderWidth,
            ),
    ) {
        content()
    }
}
