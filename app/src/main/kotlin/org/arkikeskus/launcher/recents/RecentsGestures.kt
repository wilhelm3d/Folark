package org.arkikeskus.launcher.recents

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.recentsEnter
import org.arkikeskus.launcher.ui.component.recentsSelect
import kotlin.math.abs

/**
 * What every card does, in every theme: comes in with the chosen animation, answers a pick, and
 * answers the gestures set in Settings ▸ Recents ▸ Gestures:
 * - a tap opens it (or picks it for split view),
 * - a swipe up / down / left / right does what is set for it (close by default): up / down where
 *   the cards close along [closeAxis] = vertical, left / right where [sideSwipes] (the looks that
 *   scroll up and down, so a sideways swipe is free); a Close flings the card off that way,
 * - a double tap and a long press (held still, then let go) do what is set for them,
 * - held and moved, it can be dropped on another card (split or attached view).
 *
 * One gesture reader for all of it, so a long press or a swipe never also counts as a tap.
 */
@Composable
internal fun RecentsEnv.cardModifier(
    card: RecentsCard,
    index: Int,
    columns: Int,
    closeAxis: Orientation = Orientation.Vertical,
    sideSwipes: Boolean = closeAxis == Orientation.Horizontal,
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember(card.key) { Animatable(0f) }
    val offsetX = remember(card.key) { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 120.dp.toPx() }
    val slot = picked.indexOf(card)
    val dragging = drag.card?.key == card.key
    // A card's long-press menu is open: that card stays bright and lifts a little, the rest dim.
    val menuCard = menu.card
    val isMenuSource = menu.isOpen && menuCard?.key == card.key
    val dimForMenu = menu.isOpen && !isMenuSource
    // The card being dragged over lifts to meet the held one.
    val targeted = drag.target == card.key && !drag.settling
    val lift by animateFloatAsState(
        when {
            isMenuSource -> 1f
            targeted -> 0.75f
            else -> 0f
        },
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "lift",
    )
    val heldOut by animateFloatAsState(if (dragging) 1f else 0f, tween((160 / speed.coerceIn(0.25f, 4f)).toInt()), label = "held")
    val dim by animateFloatAsState(if (dimForMenu) 1f else 0f, tween((160 / speed.coerceIn(0.25f, 4f)).toInt()), label = "dim")
    var origin by remember { mutableStateOf(Offset.Zero) }
    val s = settings
    // The gestures of the screen Recents is on (the outer screen may have its own).
    val swipeUp = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(swipeUpAction, card)
    val swipeDown = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(swipeDownAction, card)
    // Up / down only where cards don't scroll up and down; left / right only where they don't scroll sideways.
    val vertical = closeAxis == Orientation.Vertical
    val swipeLeft = if (selecting || !sideSwipes) LauncherSettings.RECENTS_ACTION_NONE else usable(swipeLeftAction, card)
    val swipeRight = if (selecting || !sideSwipes) LauncherSettings.RECENTS_ACTION_NONE else usable(swipeRightAction, card)
    val longPress = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(longPressAction, card)
    val doubleTap = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(doubleTapAction, card)
    val canDrag = s.recents.dragDrop && !selecting && !card.isSplit
    val env = rememberUpdatedState(this)

    /** A finished swipe: flies away (off the screen, that way) for Close, springs back for anything else. */
    fun finishSwipe(action: String, towardStart: Boolean, sideways: Boolean) {
        val moving = if (sideways) offsetX else offset
        scope.launch {
            if (action == LauncherSettings.RECENTS_ACTION_CLOSE) {
                moving.animateTo(if (towardStart) -dismissPx * 8 else dismissPx * 8, tween(220))
                env.value.perform(action, card)
            } else {
                moving.animateTo(0f)
                env.value.perform(action, card)
            }
        }
    }

    return Modifier
        .onGloballyPositioned {
            val r = it.boundsInRoot()
            drag.bounds[card.key] = r
            origin = r.topLeft
        }
        .recentsEnter(s.recents.enterAnimation, index, columns.coerceIn(1, 6), speed, Unit)
        .recentsSelect(s.recents.selectAnimation, slot >= 0, slot, picked.size, accent, corner, speed)
        .graphicsLayer {
            translationX = offsetX.value
            translationY = offset.value
            // Held for a drag: lifted out of its place (faint, a little smaller) until it lands.
            val faint = (1f - 0.6f * heldOut) * (1f - 0.55f * dim)
            val away = maxOf(abs(offset.value), abs(offsetX.value))
            alpha = faint * (1f - (away / (dismissPx * 2f)).coerceIn(0f, 0.8f))
            val k = 1f + 0.04f * lift - 0.08f * heldOut
            scaleX = k; scaleY = k
            shadowElevation = 28f * lift
        }
        .semantics { onClick { env.value.onTap(card); true } }
        .pointerInput(card.key, swipeUp, swipeDown, swipeLeft, swipeRight, longPress, doubleTap, canDrag, closeAxis) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var moved = Offset.Zero
                // Phase 1: a tap, the start of a swipe, or a long press (held still).
                var released = false
                var left = false
                // The move that crossed the touch slop: taken (consumed) below if this card's swipe
                // owns the stroke, so the list behind doesn't start scrolling on that same move.
                var slopChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: run { left = true; return@withTimeoutOrNull true }
                        // The card takes the tap: the screen behind (a tap on empty space cancels
                        // picking, or goes home) must not see it too.
                        if (!change.pressed) { change.consume(); released = true; return@withTimeoutOrNull true }
                        if (change.isConsumed) { left = true; return@withTimeoutOrNull true }
                        moved += change.positionChange()
                        if (moved.getDistance() > slop) { slopChange = change; return@withTimeoutOrNull true }
                    }
                }
                if (left) return@awaitEachGesture

                if (decided == null) {
                    // ---------------------------------------------------- long press
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // The menu opens at once, under the finger; moving on drags the card instead.
                    val withMenu = longPress == LauncherSettings.RECENTS_ACTION_MENU
                    if (withMenu) env.value.menu.open(card, origin + down.position)
                    var travelled = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        val d = change.positionChange()
                        travelled += d.getDistance()
                        if (canDrag && !dragging && travelled > slop) {
                            dragging = true
                            env.value.menu.close()
                            drag.start(card, origin + change.position)
                        } else if (dragging) {
                            val before = drag.target
                            val beforeMode = drag.mode
                            drag.move(d)
                            // A tick on reaching a card, and again on moving between its two halves.
                            if (drag.target != null && (drag.target != before || drag.mode != beforeMode)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (dragging) change.consume()
                    }
                    if (dragging) {
                        // Lands first (DragLayer), then opens the two together.
                        drag.release()
                    } else if (!withMenu) {
                        env.value.perform(longPress, card)
                    }
                    return@awaitEachGesture
                }

                if (released) {
                    // ---------------------------------------------------- tap, or double tap
                    if (doubleTap != LauncherSettings.RECENTS_ACTION_NONE) {
                        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (second != null) {
                            // Wait for the second finger to lift, then the double tap.
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == second.id } ?: break
                                if (!change.pressed) { change.consume(); break }
                            }
                            env.value.perform(doubleTap, card)
                            return@awaitEachGesture
                        }
                    }
                    env.value.onTap(card)
                    return@awaitEachGesture
                }

                // -------------------------------------------------------- a swipe (or someone else's scroll)
                // Which way: up / down, or left / right — only when one clearly wins. A diagonal
                // stroke (neither direction [AXIS_LEAD] times the other) is left to the list's scroll.
                val axis = swipeAxis(moved.x, moved.y)
                val sideways = axis == SwipeAxis.SIDEWAYS
                val clear = axis != SwipeAxis.DIAGONAL
                val toStart = if (sideways) swipeLeft else swipeUp
                val toEnd = if (sideways) swipeRight else swipeDown
                val allowed = clear && (if (sideways) sideSwipes else vertical)
                if (!allowed || (toStart == LauncherSettings.RECENTS_ACTION_NONE && toEnd == LauncherSettings.RECENTS_ACTION_NONE)) {
                    return@awaitEachGesture // the list's scroll takes it
                }
                slopChange?.consume()
                val moving = if (sideways) offsetX else offset
                val tracker = VelocityTracker()
                var total = if (sideways) moved.x else moved.y
                scope.launch { moving.snapTo(total) }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) { change.consume(); break }
                    val d = change.positionChange()
                    change.consume()
                    total += if (sideways) d.x else d.y
                    // Only the directions that do something move freely; the others resist.
                    val dirAction = if (total < 0) toStart else toEnd
                    val shown = if (dirAction == LauncherSettings.RECENTS_ACTION_NONE) total * 0.25f else total
                    scope.launch { moving.snapTo(shown) }
                }
                val v = tracker.calculateVelocity()
                val speed = if (sideways) v.x else v.y
                val finalAction = if (total < 0) toStart else toEnd
                if (finalAction != LauncherSettings.RECENTS_ACTION_NONE && (abs(total) > dismissPx || abs(speed) > 2200f && speed * total > 0)) {
                    finishSwipe(finalAction, towardStart = total < 0, sideways = sideways)
                } else {
                    scope.launch { moving.animateTo(0f) }
                }
            }
        }
}

/**
 * [action] if it can be done on [card] now (Close and Close the others need root; Full screen a
 * split card; Split 3 apps the three-app split switch).
 */
private fun RecentsEnv.usable(action: String, card: RecentsCard): String = when (action) {
    LauncherSettings.RECENTS_ACTION_CLOSE -> if (canClose && !card.pinned) action else LauncherSettings.RECENTS_ACTION_NONE
    LauncherSettings.RECENTS_ACTION_UNSPLIT -> if (card.isSplit) action else LauncherSettings.RECENTS_ACTION_NONE
    LauncherSettings.RECENTS_ACTION_SPLIT, LauncherSettings.RECENTS_ACTION_POPUP ->
        if (card.isSplit) LauncherSettings.RECENTS_ACTION_NONE else action
    LauncherSettings.RECENTS_ACTION_SPLIT3 ->
        if (card.isSplit || !settings.launch.threeAppSplit) LauncherSettings.RECENTS_ACTION_NONE else action
    LauncherSettings.RECENTS_ACTION_CLOSE_OTHERS ->
        if (canClose && drag.cards.any { it.key != card.key && !it.pinned }) action else LauncherSettings.RECENTS_ACTION_NONE
    else -> action
}

/** Does a gesture's action on [card]. */
internal fun RecentsEnv.perform(action: String, card: RecentsCard) {
    when (action) {
        LauncherSettings.RECENTS_ACTION_CLOSE -> if (canClose) onClose(card)
        LauncherSettings.RECENTS_ACTION_SPLIT -> onSplit(card)
        LauncherSettings.RECENTS_ACTION_POPUP -> onPopUp(card)
        LauncherSettings.RECENTS_ACTION_UNSPLIT -> onUnsplit(card)
        LauncherSettings.RECENTS_ACTION_INFO -> onAppInfo(card)
        LauncherSettings.RECENTS_ACTION_PIN -> onTogglePin(card)
        LauncherSettings.RECENTS_ACTION_MENU -> menu.open(card, drag.bounds[card.key]?.center ?: Offset.Zero)
        LauncherSettings.RECENTS_ACTION_OPEN -> onTap(card)
        LauncherSettings.RECENTS_ACTION_PEEK -> if (card.isSplit) onPeekSplit(card) else onPeekPick(card)
        LauncherSettings.RECENTS_ACTION_SPLIT3 -> onSplitThree(card)
        LauncherSettings.RECENTS_ACTION_CLOSE_OTHERS ->
            if (canClose) drag.cards.filter { it.key != card.key && !it.pinned }.forEach(onClose)
        LauncherSettings.RECENTS_ACTION_CLOSE_ALL -> onCloseAll()
        else -> {}
    }
}

/** How much one direction must lead the other for a stroke to count as a card swipe (not a scroll). */
private const val AXIS_LEAD = 1.25f

internal enum class SwipeAxis { UP_DOWN, SIDEWAYS, DIAGONAL }

/** Which way a stroke that moved by ([dx], [dy]) goes: one direction must lead by [AXIS_LEAD]. */
internal fun swipeAxis(dx: Float, dy: Float): SwipeAxis {
    val x = abs(dx)
    val y = abs(dy)
    return when {
        x >= AXIS_LEAD * y && x > 0f -> SwipeAxis.SIDEWAYS
        y >= AXIS_LEAD * x && y > 0f -> SwipeAxis.UP_DOWN
        else -> SwipeAxis.DIAGONAL
    }
}
