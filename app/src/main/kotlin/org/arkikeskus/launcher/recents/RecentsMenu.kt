package org.arkikeskus.launcher.recents

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Path
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

/**
 * The card whose long-press menu is open, and where the finger was (screen coordinates). Closing
 * is animated: the menu stays drawn while [closing], and [finish] removes it once it has gone.
 */
@Stable
internal class RecentsMenuState {
    var card by mutableStateOf<RecentsCard?>(null)
        private set
    var at by mutableStateOf(Offset.Zero)
        private set
    var closing by mutableStateOf(false)
        private set

    /** Open and not on its way out (the cards behind it dim and the source card lifts only then). */
    val isOpen: Boolean get() = card != null && !closing

    fun open(card: RecentsCard, at: Offset) {
        this.card = card
        this.at = at
        closing = false
    }

    fun close() {
        if (card != null) closing = true
    }

    fun finish() {
        card = null
        closing = false
    }
}

/** Where the menu grows from, as a fraction of its own size (set while it is placed). */
private class Pivot {
    var x = 0.5f
    var y = 0f
}

/**
 * A card's long-press menu, in Liquid Glass. It names the app at the top, then the things you can
 * do with it: Open, Split screen, Freeform, Lock, App info, Close app — each shown only where it
 * can be done (Close needs root and isn't offered for a locked app). It grows out of the point
 * under the finger with a little spring, its rows slide in one after another, and it shrinks back
 * toward that point when it closes (Settings ▸ Recents ▸ Animate the long-press menu; off: it just
 * appears and goes). A tap outside closes it.
 */
@Composable
internal fun RecentsMenuLayer(menu: RecentsMenuState, env: RecentsEnv) {
    val card = menu.card ?: return
    val items = buildList {
        add(MenuItem(R.string.recents_menu_open, Glyph.OPEN) { env.onTap(card) })
        if (!card.isSplit) add(MenuItem(R.string.recents_menu_split, Glyph.SPLIT) { env.onSplit(card) })
        if (!card.isSplit && env.settings.launch.threeAppSplit) {
            add(MenuItem(R.string.recents_menu_split_three, Glyph.SPLIT3) { env.onSplitThree(card) })
        }
        // Android's peek view: one app big, the other peeking in from the edge (a strip to tap).
        add(MenuItem(R.string.recents_menu_peek, Glyph.PEEK) { if (card.isSplit) env.onPeekSplit(card) else env.onPeekPick(card) })
        // Motorola's own split picker (only when native three-app split is on): pick the other app
        // from the system's picker instead of Folark's. On a split, pick the third; else pick a second.
        if (env.settings.launch.threeAppNative) {
            if (card.isSplit) add(MenuItem(R.string.recents_menu_native_third, Glyph.SPLIT3) { env.onNativeThird(card) })
            else add(MenuItem(R.string.recents_menu_native_split, Glyph.SPLIT) { env.onNativeSplit(card) })
        }
        if (!card.isSplit) add(MenuItem(R.string.recents_menu_freeform, Glyph.FREEFORM) { env.onPopUp(card) })
        if (card.isSplit) add(MenuItem(R.string.recents_menu_unsplit, Glyph.UNSPLIT) { env.onUnsplit(card) })
        // Keep this split: saved as an app pair, opened together again from home or the taskbar.
        if (card.isSplit && card.apps.none { it == null }) add(MenuItem(R.string.recents_menu_save_pair, Glyph.SAVE) { env.onSavePair(card) })
        add(MenuItem(if (card.pinned) R.string.recents_menu_unlock else R.string.recents_menu_lock, Glyph.LOCK) { env.onTogglePin(card) })
        add(MenuItem(R.string.recents_menu_info, Glyph.INFO) { env.onAppInfo(card) })
        // Persistent per-app mute: stays muted across restarts (appops PLAY_AUDIO on root).
        run {
            val muted = env.isMuted(card)
            add(MenuItem(if (muted) R.string.recents_menu_unmute else R.string.recents_menu_mute, if (muted) Glyph.UNMUTE else Glyph.MUTE) { env.onToggleMute(card) })
        }
        if (env.canClose && !card.pinned) add(MenuItem(R.string.recents_menu_close, Glyph.CLOSE, danger = true) { env.onClose(card) })
    }
    val motion = env.settings.recents.menuMotion
    val speed = env.speed.coerceIn(0.25f, 4f)
    val progress = remember(card.key) { Animatable(if (motion) 0f else 1f) }
    val pivot = remember(card.key) { Pivot() }
    LaunchedEffect(card.key, menu.closing) {
        if (!menu.closing) {
            if (motion) progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f * speed * speed))
            else progress.snapTo(1f)
        } else {
            if (motion) progress.animateTo(0f, tween((150 / speed).toInt(), easing = FastOutLinearInEasing))
            menu.finish()
        }
    }
    val shape = RoundedCornerShape(26.dp)
    val tint = env.settings.recents.glassTint
    val accent = env.accent
    val rise = with(LocalDensity.current) { 10.dp.toPx() }
    /** A row's own share of the animation: each starts a little after the one above. */
    fun rowIn(i: Int): Float = ((progress.value - 0.12f - i * 0.07f) / 0.45f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            // A soft dim behind the menu makes it the focus; tapping it closes the menu.
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.32f * progress.value.coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu.close() },
    ) {
        Column(
            Modifier
                .placeNear(menu.at, pivot)
                .graphicsLayer {
                    val k = progress.value
                    val sc = 0.55f + 0.45f * k
                    scaleX = sc; scaleY = sc
                    alpha = (k * 1.8f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivot.x, pivot.y)
                    shadowElevation = 30f * k.coerceIn(0f, 1f)
                    this.shape = shape
                    clip = false
                }
                .width(248.dp)
                .liquidGlass(
                    shape = shape,
                    tint = Color(0xFF1B2326).copy(alpha = 0.5f + tint),
                    highlight = 1f,
                    rimBottom = 0.22f,
                )
                .padding(vertical = 8.dp),
        ) {
            // Header: which app (or split pair) this menu is about.
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { val t = rowIn(0); alpha = t; translationY = (1f - t) * rise }
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    card.apps.forEach { AppBadge(it, 30.dp) }
                }
                Text(card.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                if (card.pinned) PinGlyph(accent)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            items.forEachIndexed { i, item ->
                val color = if (item.danger) Color(0xFFFF6B6B) else Ink
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { val t = rowIn(i + 1); alpha = t; translationY = (1f - t) * rise }
                        .pressClick {
                            if (menu.closing) return@pressClick
                            menu.close()
                            item.action()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Canvas(Modifier.size(22.dp)) { drawGlyph(item.glyph, color, card.pinned) }
                    Text(stringResource(item.label), color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class MenuItem(val label: Int, val glyph: Glyph, val danger: Boolean = false, val action: () -> Unit)

private enum class Glyph { OPEN, SPLIT, SPLIT3, PEEK, FREEFORM, UNSPLIT, LOCK, INFO, CLOSE, SAVE, MUTE, UNMUTE }

/**
 * Places the menu just below [at], centred on it, always fully on screen (above it if no room
 * below), and notes in [pivot] where [at] falls on the menu, so it grows out of that very point.
 */
private fun Modifier.placeNear(at: Offset, pivot: Pivot): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints())
    val margin = 16.dp.roundToPx()
    val gap = 12.dp.roundToPx()
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val x = (at.x.roundToInt() - p.width / 2).coerceIn(margin, (maxW - p.width - margin).coerceAtLeast(margin))
    val below = at.y.roundToInt() + gap
    val y = if (below + p.height + margin <= maxH) below else (at.y.roundToInt() - gap - p.height).coerceAtLeast(margin)
    pivot.x = ((at.x - x) / p.width.coerceAtLeast(1)).coerceIn(0f, 1f)
    pivot.y = ((at.y - y) / p.height.coerceAtLeast(1)).coerceIn(0f, 1f)
    layout(maxW, maxH) { p.place(x, y) }
}

/** Line icons in the style of the menu in the picture: split, a floating window, a padlock, ✕. */
private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, locked: Boolean) {
    val w = size.width
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    val r = CornerRadius(w * 0.12f)
    when (glyph) {
        Glyph.OPEN -> {
            // A card with an arrow going up-right (open / launch).
            drawRoundRect(color, Offset(w * 0.1f, w * 0.1f), Size(w * 0.8f, w * 0.8f), r, style = stroke)
            drawLine(color, Offset(w * 0.4f, w * 0.6f), Offset(w * 0.68f, w * 0.32f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, w * 0.32f), Offset(w * 0.68f, w * 0.32f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.68f, w * 0.32f), Offset(w * 0.68f, w * 0.5f), stroke.width, StrokeCap.Round)
        }
        Glyph.SAVE -> {
            // Two panes with a plus: keep this pair.
            drawRoundRect(color, Offset(w * 0.06f, w * 0.14f), Size(w * 0.3f, w * 0.72f), r, style = stroke)
            drawRoundRect(color, Offset(w * 0.44f, w * 0.14f), Size(w * 0.3f, w * 0.72f), r, style = stroke)
            drawLine(color, Offset(w * 0.86f, w * 0.38f), Offset(w * 0.86f, w * 0.62f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.74f, w * 0.5f), Offset(w * 0.98f, w * 0.5f), stroke.width, StrokeCap.Round)
        }
        Glyph.UNSPLIT -> {
            // One full box (back to full screen).
            drawRoundRect(color, Offset(w * 0.12f, w * 0.14f), Size(w * 0.76f, w * 0.72f), r, style = stroke)
        }
        Glyph.INFO -> {
            drawCircle(color, w * 0.4f, Offset(w * 0.5f, w * 0.5f), style = stroke)
            drawCircle(color, w * 0.045f, Offset(w * 0.5f, w * 0.32f))
            drawLine(color, Offset(w * 0.5f, w * 0.44f), Offset(w * 0.5f, w * 0.66f), stroke.width, StrokeCap.Round)
        }
        Glyph.SPLIT -> {
            drawRoundRect(color, Offset(w * 0.06f, w * 0.14f), Size(w * 0.38f, w * 0.72f), r, style = stroke)
            drawRoundRect(color, Offset(w * 0.56f, w * 0.14f), Size(w * 0.38f, w * 0.72f), r, style = stroke)
        }
        Glyph.SPLIT3 -> {
            // Three panes side by side.
            val pw = w * 0.25f
            for (i in 0..2) drawRoundRect(color, Offset(w * 0.06f + i * (pw + w * 0.065f), w * 0.14f), Size(pw, w * 0.72f), r, style = stroke)
        }
        Glyph.PEEK -> {
            // A big pane and a thin strip at its edge.
            drawRoundRect(color, Offset(w * 0.26f, w * 0.14f), Size(w * 0.68f, w * 0.72f), r, style = stroke)
            drawRoundRect(color, Offset(w * 0.06f, w * 0.22f), Size(w * 0.1f, w * 0.56f), CornerRadius(w * 0.05f))
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
        Glyph.MUTE, Glyph.UNMUTE -> {
            // A speaker, with a slash (muted) or sound waves (unmuted) to its right.
            val speaker = Path().apply {
                moveTo(w * 0.08f, w * 0.4f)
                lineTo(w * 0.22f, w * 0.4f)
                lineTo(w * 0.42f, w * 0.22f)
                lineTo(w * 0.42f, w * 0.78f)
                lineTo(w * 0.22f, w * 0.6f)
                lineTo(w * 0.08f, w * 0.6f)
                close()
            }
            drawPath(speaker, color, style = stroke)
            if (glyph == Glyph.MUTE) {
                drawLine(color, Offset(w * 0.58f, w * 0.38f), Offset(w * 0.9f, w * 0.62f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.9f, w * 0.38f), Offset(w * 0.58f, w * 0.62f), stroke.width, StrokeCap.Round)
            } else {
                drawArc(color, -50f, 100f, false, Offset(w * 0.4f, w * 0.3f), Size(w * 0.28f, w * 0.4f), style = stroke)
                drawArc(color, -50f, 100f, false, Offset(w * 0.46f, w * 0.18f), Size(w * 0.44f, w * 0.64f), style = stroke)
            }
        }
    }
}
