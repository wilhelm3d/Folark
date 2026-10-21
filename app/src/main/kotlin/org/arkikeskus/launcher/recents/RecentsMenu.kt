package org.arkikeskus.launcher.recents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.R
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.component.pressClick
import kotlin.math.roundToInt

/** The card whose long-press menu is open, and where the finger was (screen coordinates). */
@Stable
internal class RecentsMenuState {
    var card by mutableStateOf<RecentsCard?>(null)
        private set
    var at by mutableStateOf(Offset.Zero)
        private set

    fun open(card: RecentsCard, at: Offset) {
        this.card = card
        this.at = at
    }

    fun close() {
        card = null
    }
}

/**
 * A card's long-press menu, in Liquid Glass: Split screen, Freeform, Lock (pin) and Close app,
 * each only where it can be done (Close needs root and isn't offered for a locked app). It opens
 * under the finger, kept on screen; a tap outside closes it.
 */
@Composable
internal fun RecentsMenuLayer(menu: RecentsMenuState, env: RecentsEnv) {
    val card = menu.card ?: return
    val items = buildList {
        if (!card.isSplit) add(MenuItem(R.string.recents_menu_split, Glyph.SPLIT) { env.onSplit(card) })
        if (!card.isSplit) add(MenuItem(R.string.recents_menu_freeform, Glyph.FREEFORM) { env.onPopUp(card) })
        add(MenuItem(if (card.pinned) R.string.recents_menu_unlock else R.string.recents_menu_lock, Glyph.LOCK) { env.onTogglePin(card) })
        if (env.canClose && !card.pinned) add(MenuItem(R.string.recents_menu_close, Glyph.CLOSE) { env.onClose(card) })
    }
    val grow = remember(card.key) { Animatable(0.85f) }
    LaunchedEffect(card.key) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
    val shape = RoundedCornerShape(26.dp)
    val tint = env.settings.recentsGlassTint
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu.close() },
    ) {
        Column(
            Modifier
                .placeNear(menu.at)
                .graphicsLayer {
                    val k = grow.value
                    scaleX = k; scaleY = k
                    alpha = ((k - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    shadowElevation = 24f
                    this.shape = shape
                    clip = false
                }
                .width(236.dp)
                .liquidGlass(
                    shape = shape,
                    tint = Color(0xFF1B2326).copy(alpha = 0.42f + tint),
                    highlight = 1f,
                    rimBottom = 0.22f,
                )
                .padding(vertical = 10.dp),
        ) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressClick {
                            menu.close()
                            item.action()
                        }
                        .padding(horizontal = 22.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Canvas(Modifier.size(22.dp)) { drawGlyph(item.glyph, Ink, card.pinned) }
                    Text(stringResource(item.label), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class MenuItem(val label: Int, val glyph: Glyph, val action: () -> Unit)

private enum class Glyph { SPLIT, FREEFORM, LOCK, CLOSE }

/** Places the menu just below [at], centred on it, always fully on screen (above it if no room below). */
private fun Modifier.placeNear(at: Offset): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints())
    val margin = 16.dp.roundToPx()
    val gap = 12.dp.roundToPx()
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val x = (at.x.roundToInt() - p.width / 2).coerceIn(margin, (maxW - p.width - margin).coerceAtLeast(margin))
    val below = at.y.roundToInt() + gap
    val y = if (below + p.height + margin <= maxH) below else (at.y.roundToInt() - gap - p.height).coerceAtLeast(margin)
    layout(maxW, maxH) { p.place(x, y) }
}

/** Line icons in the style of the menu in the picture: split, a floating window, a padlock, ✕. */
private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, locked: Boolean) {
    val w = size.width
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    val r = CornerRadius(w * 0.12f)
    when (glyph) {
        Glyph.SPLIT -> {
            drawRoundRect(color, Offset(w * 0.06f, w * 0.14f), Size(w * 0.38f, w * 0.72f), r, style = stroke)
            drawRoundRect(color, Offset(w * 0.56f, w * 0.14f), Size(w * 0.38f, w * 0.72f), r, style = stroke)
        }
        Glyph.FREEFORM -> {
            drawRoundRect(color, Offset(w * 0.06f, w * 0.1f), Size(w * 0.88f, w * 0.8f), r, style = stroke)
            drawRoundRect(color, Offset(w * 0.34f, w * 0.34f), Size(w * 0.44f, w * 0.38f), CornerRadius(w * 0.08f))
        }
        Glyph.LOCK -> {
            drawRoundRect(color, Offset(w * 0.16f, w * 0.44f), Size(w * 0.68f, w * 0.5f), r, style = stroke)
            // The shackle: closed when locked, swung open when not.
            val left = w * 0.3f
            val right = w * 0.7f
            val top = w * 0.1f
            drawArc(color, 180f, 180f, false, Offset(left, top), Size(right - left, w * 0.36f), style = stroke)
            drawLine(color, Offset(left, top + w * 0.18f), Offset(left, w * 0.44f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(right, top + w * 0.18f), Offset(right, if (locked) w * 0.44f else w * 0.32f), stroke.width, StrokeCap.Round)
            drawCircle(color, w * 0.06f, Offset(w * 0.5f, w * 0.68f))
        }
        Glyph.CLOSE -> {
            drawLine(color, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.8f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.8f, w * 0.2f), Offset(w * 0.2f, w * 0.8f), stroke.width, StrokeCap.Round)
        }
    }
}
