package org.arkikeskus.launcher.recents

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
 * - a swipe up / down (sideways in the Compact list) does what is set for it (close by default),
 * - a double tap and a long press (held still, then let go) do what is set for them,
 * - held and moved, it can be dropped on another card (split or attached view).
 *
 * One gesture reader for all of it, so a long press or a swipe never also counts as a tap.
 */
@Composable
internal fun RecentsEnv.cardModifier(card: RecentsCard, index: Int, columns: Int, closeAxis: Orientation = Orientation.Vertical): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember(card.key) { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 120.dp.toPx() }
    val slot = picked.indexOf(card)
    val dragging = drag.card?.key == card.key
    var origin by remember { mutableStateOf(Offset.Zero) }
    val s = settings
    val swipeUp = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(s.recentsSwipeUpAction, card)
    val swipeDown = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(s.recentsSwipeDownAction, card)
    val longPress = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(s.recentsLongPressAction, card)
    val doubleTap = if (selecting) LauncherSettings.RECENTS_ACTION_NONE else usable(s.recentsDoubleTapAction, card)
    val canDrag = s.recentsDragDrop && !selecting && !card.isSplit
    val env = rememberUpdatedState(this)

    /** A finished swipe: flies away for Close, springs back for anything else. */
    fun finishSwipe(action: String, towardStart: Boolean) {
        scope.launch {
            if (action == LauncherSettings.RECENTS_ACTION_CLOSE) {
                offset.animateTo(if (towardStart) -dismissPx * 4 else dismissPx * 4)
                env.value.perform(action, card)
            } else {
                offset.animateTo(0f)
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
        .recentsEnter(s.recentsEnterAnimation, index, columns.coerceIn(1, 6), speed, Unit)
        .recentsSelect(s.recentsSelectAnimation, slot >= 0, slot, picked.size, accent, corner, speed)
        .graphicsLayer {
            if (closeAxis == Orientation.Vertical) translationY = offset.value else translationX = offset.value
            alpha = (if (dragging) 0.35f else 1f) * (1f - (abs(offset.value) / (dismissPx * 2f)).coerceIn(0f, 0.8f))
        }
        .semantics { onClick { env.value.onTap(card); true } }
        .pointerInput(card.key, swipeUp, swipeDown, longPress, doubleTap, canDrag, closeAxis) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var moved = Offset.Zero
                // Phase 1: a tap, the start of a swipe, or a long press (held still).
                var released = false
                var left = false
                val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: run { left = true; return@withTimeoutOrNull true }
                        if (!change.pressed) { released = true; return@withTimeoutOrNull true }
                        if (change.isConsumed) { left = true; return@withTimeoutOrNull true }
                        moved += change.positionChange()
                        if (moved.getDistance() > slop) return@withTimeoutOrNull true
                    }
                }
                if (left) return@awaitEachGesture

                if (decided == null) {
                    // ---------------------------------------------------- long press
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (canDrag) drag.start(card, origin + down.position)
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.positionChange()
                        travelled += d.getDistance()
                        if (canDrag) {
                            change.consume()
                            val before = drag.target
                            drag.move(d)
                            if (drag.target != before && drag.target != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    if (canDrag && travelled > slop) {
                        drag.drop()?.let { (onto, how) -> env.value.onCombine(card, onto, how) }
                    } else {
                        if (canDrag) drag.cancel()
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
                val along = if (closeAxis == Orientation.Vertical) moved.y else moved.x
                val across = if (closeAxis == Orientation.Vertical) moved.x else moved.y
                if (abs(along) <= abs(across) || (swipeUp == LauncherSettings.RECENTS_ACTION_NONE && swipeDown == LauncherSettings.RECENTS_ACTION_NONE)) {
                    return@awaitEachGesture // the list's scroll takes it
                }
                val tracker = VelocityTracker()
                var total = along
                scope.launch { offset.snapTo(total) }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) break
                    val d = change.positionChange()
                    change.consume()
                    total += if (closeAxis == Orientation.Vertical) d.y else d.x
                    // Only the directions that do something move freely; the others resist.
                    val dirAction = if (total < 0) swipeUp else swipeDown
                    val shown = if (dirAction == LauncherSettings.RECENTS_ACTION_NONE) total * 0.25f else total
                    scope.launch { offset.snapTo(shown) }
                }
                val v = tracker.calculateVelocity()
                val speed = if (closeAxis == Orientation.Vertical) v.y else v.x
                val finalAction = if (total < 0) swipeUp else swipeDown
                if (finalAction != LauncherSettings.RECENTS_ACTION_NONE && (abs(total) > dismissPx || abs(speed) > 2200f && speed * total > 0)) {
                    finishSwipe(finalAction, towardStart = total < 0)
                } else {
                    scope.launch { offset.animateTo(0f) }
                }
            }
        }
}

/** [action] if it can be done on [card] now (Close needs root; Full screen a split card). */
private fun RecentsEnv.usable(action: String, card: RecentsCard): String = when (action) {
    LauncherSettings.RECENTS_ACTION_CLOSE -> if (canClose && !card.pinned) action else LauncherSettings.RECENTS_ACTION_NONE
    LauncherSettings.RECENTS_ACTION_UNSPLIT -> if (card.isSplit) action else LauncherSettings.RECENTS_ACTION_NONE
    LauncherSettings.RECENTS_ACTION_SPLIT, LauncherSettings.RECENTS_ACTION_POPUP ->
        if (card.isSplit) LauncherSettings.RECENTS_ACTION_NONE else action
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
        LauncherSettings.RECENTS_ACTION_OPEN -> onTap(card)
        else -> {}
    }
}
