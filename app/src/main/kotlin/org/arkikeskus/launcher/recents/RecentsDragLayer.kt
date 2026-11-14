package org.arkikeskus.launcher.recents

import org.arkikeskus.launcher.data.LaunchOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.R
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.IconRequest
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RecentsAction
import org.arkikeskus.launcher.model.RecentsAttachedSide
import org.arkikeskus.launcher.model.RecentsCardLayout
import org.arkikeskus.launcher.ui.component.pressClick
import org.arkikeskus.launcher.ui.component.PressStyle
import org.arkikeskus.launcher.ui.component.LocalPressStyle
import androidx.compose.runtime.CompositionLocalProvider
import org.arkikeskus.launcher.ui.component.rememberRecentsBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

// The drag of a held card onto another, or to a screen edge (split out of RecentsScreen.kt).

/**
 * The held card as a small copy of its own picture, following the finger on a spring and leaning
 * into the direction it moves. The card under it lifts and shows its two halves — split view and
 * attached view — with the half under the finger lit; moving across slides the light over. Let go
 * and the copy flies into that card (then the two open together), or home when over nothing.
 */
@Composable
internal fun DragLayer(drag: RecentsDrag, env: RecentsEnv) {
    val held = drag.card ?: return
    val density = LocalDensity.current
    val speed = env.speed.coerceIn(0.25f, 4f)
    val origin = drag.origin
    // The miniature: about half the card, never bigger than 190 dp or smaller than 110 dp wide.
    val maxW = with(density) { 190.dp.toPx() }
    val minW = with(density) { 110.dp.toPx() }
    val baseW = ((origin?.width ?: maxW) * 0.55f).coerceIn(minW, maxW)
    val baseH = baseW * ((origin?.height ?: baseW) / (origin?.width ?: baseW).coerceAtLeast(1f)).coerceIn(0.4f, 2.4f)

    val pos = remember(held.key) { Animatable(drag.pointer, Offset.VectorConverter) }
    val land = remember(held.key) { Animatable(0f) }
    LaunchedEffect(held.key) {
        snapshotFlow { drag.pointer }.collect { p ->
            if (!drag.settling) launch { pos.animateTo(p, spring(dampingRatio = 0.82f, stiffness = 1100f * speed * speed)) }
        }
    }
    LaunchedEffect(held.key, drag.settling) {
        if (!drag.settling) return@LaunchedEffect
        try {
            val to = drag.landing
            if (to != null) {
                launch { pos.animateTo(to.center, spring(dampingRatio = 0.86f, stiffness = 520f * speed * speed)) }
                land.animateTo(1f, tween((260 / speed).toInt(), easing = FastOutSlowInEasing))
            }
        } finally {
            // Only this drag's landing (a new hold may have started meanwhile).
            if (drag.card?.key == held.key && drag.settling) {
                val combine = drag.finish()
                if (combine != null) {
                    val (from, onto, how) = combine
                    // Replace carries which pane was dropped on, encoded onto the mode string.
                    if (how == COMBINE_REPLACE) env.onCombine(from, onto, "$COMBINE_REPLACE:${drag.droppedPane}")
                    else env.onCombine(from, onto, how)
                } else if (drag.droppedEdge != EDGE_NONE) {
                    drag.edgeCard?.let { c ->
                        when (drag.droppedEdge) {
                            // Peek and three-app use Folark's in-Recents picker (seeded with this app).
                            EDGE_PEEK -> env.onPeekPick(c)
                            // A split card: pick its third app among the cards; one app: Folark's
                            // picker chooses two more apps (any app, open or not).
                            EDGE_THREE -> if (c.isSplit) env.onSplitThree(c) else env.onPlaceAtEdge(c, EDGE_THREE)
                            // Left/right open the app on that side with Folark's split picker on the
                            // other; top = full screen; bottom = floating window (all via placeAtEdge).
                            else -> env.onPlaceAtEdge(c, drag.droppedEdge)
                        }
                    }
                }
            }
        }
    }

    // The card under the finger: a dark veil with the two ways to combine.
    var shownRect by remember(held.key) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val targetRect = drag.target?.let { drag.bounds[it] }
    if (targetRect != null) shownRect = targetRect
    val veil by animateFloatAsState(if (targetRect != null) 1f else 0f, tween((150 / speed).toInt()), label = "veil")
    val attached by animateFloatAsState(
        if (drag.mode == COMBINE_ATTACHED) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 700f * speed * speed),
        label = "half",
    )
    Box(Modifier.fillMaxSize()) {
        val r = shownRect
        if (r != null && veil > 0.01f) {
            val w = with(density) { r.width.toDp() }
            val h = with(density) { r.height.toDp() }
            Row(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(w, h)
                    .graphicsLayer { alpha = veil; scaleX = 1.03f; scaleY = 1.03f }
                    .clip(RoundedCornerShape(env.corner))
                    .background(Color(0xD905090A)),
            ) {
                if (drag.mode == COMBINE_REPLACE) {
                    // A three-app card: the pane under the finger lights up — the held app replaces it.
                    val n = (drag.cards.firstOrNull { it.key == drag.target }?.tasks?.size ?: 3).coerceIn(2, 4)
                    repeat(n) { i ->
                        DropHalf(R.string.recents_drop_replace, if (i == drag.paneIndex) 1f else 0.18f, env.accent, attachedDiagram = false, env, Modifier.weight(1f), panes = n)
                    }
                } else if (drag.mode == COMBINE_THIRD) {
                    // A split card: the held app joins it as the third.
                    DropHalf(R.string.recents_drop_third, 1f, env.accent, attachedDiagram = false, env, Modifier.weight(1f), panes = 3)
                } else {
                    DropHalf(R.string.recents_drop_split, 1f - attached, env.accent, attachedDiagram = false, env, Modifier.weight(1f))
                    DropHalf(R.string.recents_drop_attached, attached, env.accent, attachedDiagram = true, env, Modifier.weight(1f))
                }
            }
        }

        // Aero snap preview (inner screen): while a card is long-press-dragged to an edge, a rounded
        // glass region shows where the app will land — left/right half, or full screen for the top,
        // Windows-snap style. Only the targeted region is shown.
        if (drag.allowEdge && drag.card != null && drag.target == null && drag.edge != EDGE_NONE) {
            SnapPreview(drag.edge, env.accent)
        }

        // The miniature of the held card.
        val k = land.value
        val to = drag.landing
        val scaleTo = if (to != null) (to.width / baseW).coerceIn(0.3f, 3f) else 1f
        val scale = 1.06f + (scaleTo - 1.06f) * k
        val app = held.apps.firstOrNull()
        Box(
            Modifier
                .offset { IntOffset((pos.value.x - baseW / 2).roundToInt(), (pos.value.y - baseH / 2).roundToInt()) }
                .size(with(density) { baseW.toDp() }, with(density) { baseH.toDp() })
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    // Leans into the movement, settles upright when it lands.
                    rotationZ = (pos.velocity.x / 260f).coerceIn(-9f, 9f) * (1f - k)
                    alpha = 1f - 0.85f * k * k
                    shadowElevation = 34f * (1f - k)
                    shape = RoundedCornerShape(env.corner * 0.7f)
                    clip = true
                },
        ) {
            CardPicture(held, env, Modifier.matchParentSize(), env.corner * 0.7f, iconSize = 40.dp)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { AppBadge(app, 30.dp) }
        }
    }
}

/** One half of the drop target: a small drawing of the result and its name; lit by [active] 0..1. */
@Composable
private fun BoxScope.SnapPreview(edge: Int, accent: Color) {
    // A rounded glass preview of where the app will land, with an icon and label — Windows-snap style.
    // Left/right = that split half; top = full screen; bottom = a floating window; top-centre = peek
    // view; bottom-centre = three-app split.
    val pop by animateFloatAsState(1f, tween(130), label = "snap")
    val align = when (edge) {
        EDGE_RIGHT -> Alignment.CenterEnd
        EDGE_LEFT, EDGE_PEEK -> Alignment.CenterStart
        EDGE_BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val widthFraction = when (edge) {
        EDGE_LEFT, EDGE_RIGHT -> 0.5f
        EDGE_PEEK -> 0.88f
        EDGE_BOTTOM -> 0.5f
        else -> 1f
    }
    val heightFraction = if (edge == EDGE_BOTTOM) 0.42f else 1f
    val label = stringResource(
        when (edge) {
            EDGE_LEFT -> R.string.recents_edge_left
            EDGE_RIGHT -> R.string.recents_edge_right
            EDGE_TOP -> R.string.recents_edge_top
            EDGE_BOTTOM -> R.string.recents_edge_bottom
            EDGE_PEEK -> R.string.recents_edge_peek
            else -> R.string.recents_edge_three
        },
    )
    Box(
        Modifier
            .align(align)
            .fillMaxWidth(widthFraction)
            .fillMaxHeight(heightFraction)
            .padding(12.dp)
            .graphicsLayer { val s = 0.97f + 0.03f * pop; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(26.dp))
            .background(accent.copy(alpha = 0.20f))
            .border(2.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Three-app: hint the three panes behind the label.
        if (edge == EDGE_THREE) {
            Row(Modifier.matchParentSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.14f))) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC05090A))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Canvas(Modifier.size(22.dp)) { drawEdgeGlyph(edge, accent) }
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The little landing icon shown on a snap preview, matching the drop target. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeGlyph(edge: Int, accent: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
    val r = CornerRadius(w * 0.14f)
    fun pane(x: Float, pw: Float, filled: Boolean) {
        val top = w * 0.14f
        val h = w * 0.72f
        if (filled) drawRoundRect(accent, Offset(x, top), Size(pw, h), r)
        else drawRoundRect(accent, Offset(x, top), Size(pw, h), r, style = stroke)
    }
    when (edge) {
        EDGE_LEFT -> { pane(w * 0.08f, w * 0.38f, true); pane(w * 0.54f, w * 0.38f, false) }
        EDGE_RIGHT -> { pane(w * 0.08f, w * 0.38f, false); pane(w * 0.54f, w * 0.38f, true) }
        EDGE_TOP -> drawRoundRect(accent, Offset(w * 0.1f, w * 0.14f), Size(w * 0.8f, w * 0.72f), r, style = stroke)
        EDGE_BOTTOM -> {
            // A floating window: a small centred box with corner ticks.
            drawRoundRect(accent, Offset(w * 0.24f, w * 0.3f), Size(w * 0.52f, w * 0.4f), r, style = stroke)
            drawLine(accent, Offset(w * 0.12f, w * 0.2f), Offset(w * 0.24f, w * 0.2f), stroke.width, StrokeCap.Round)
            drawLine(accent, Offset(w * 0.12f, w * 0.2f), Offset(w * 0.12f, w * 0.32f), stroke.width, StrokeCap.Round)
        }
        EDGE_PEEK -> { pane(w * 0.08f, w * 0.64f, false); drawRoundRect(accent, Offset(w * 0.8f, w * 0.26f), Size(w * 0.12f, w * 0.48f), CornerRadius(w * 0.06f)) }
        else -> { // EDGE_THREE
            val pw = w * 0.24f
            for (i in 0..2) drawRoundRect(accent, Offset(w * 0.08f + i * (pw + w * 0.07f), w * 0.14f), Size(pw, w * 0.72f), r, style = stroke)
        }
    }
}

@Composable
private fun DropHalf(label: Int, active: Float, accent: Color, attachedDiagram: Boolean, env: RecentsEnv, modifier: Modifier, panes: Int = 2) {
    val attachedLeft = env.settings.recents.attachedSide == RecentsAttachedSide.LEFT
    Column(
        modifier
            .fillMaxHeight()
            .padding(8.dp)
            .graphicsLayer { val k = 0.94f + 0.06f * active; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.06f + 0.30f * active)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        val ink = Color.White.copy(alpha = 0.45f + 0.55f * active)
        Canvas(Modifier.size(52.dp, 40.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx())
            val r = CornerRadius(5.dp.toPx())
            val gap = 4.dp.toPx()
            if (!attachedDiagram) {
                val pane = (size.width - gap * (panes - 1)) / panes
                for (i in 0 until panes) {
                    // In the three-app drawing the new (third) pane is the filled one.
                    val fill = panes == 3 && i == 2
                    drawRoundRect(
                        if (fill) ink.copy(alpha = ink.alpha * 0.7f) else ink, Offset(i * (pane + gap), 0f), Size(pane, size.height), r,
                        style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
                    )
                }
            } else {
                // The main app, with the other one peeking in at the chosen side.
                val peek = size.width * 0.26f
                val main = size.width - peek - gap
                val mainX = if (attachedLeft) peek + gap else 0f
                val peekX = if (attachedLeft) 0f else main + gap
                drawRoundRect(ink, Offset(mainX, 0f), Size(main, size.height), r, style = stroke)
                drawRoundRect(ink.copy(alpha = ink.alpha * 0.7f), Offset(peekX, 0f), Size(peek, size.height), r)
            }
        }
        Text(
            stringResource(label),
            color = if (active > 0.5f) Ink else Muted,
            fontSize = 14.sp,
            fontWeight = if (active > 0.5f) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

