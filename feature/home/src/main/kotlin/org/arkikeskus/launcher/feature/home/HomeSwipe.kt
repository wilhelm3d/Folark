package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.HomeDragController
import kotlin.math.abs

// Home's swipe gestures (up, down, edges) (split out of HomeScreen.kt).

/**
 * Root-level home swipe-up/down detector — the Pixel-style fix for "the drawer swipe only catches on
 * empty space". Applied to HomeScreen's root Box and run in [PointerEventPass.Initial] (parent before
 * children), so it sees movement *before* the icons, folders, shortcuts, dock and the HorizontalPager.
 *
 * It does NOT consume the DOWN and only consumes once the gesture is clearly vertical past the touch
 * slop — so taps, long-presses and horizontal page swipes pass through untouched, while a flick up
 * from anywhere on the home surface reliably "catches" and drives the drawer (the root cause of the
 * old bug: the detector lived on the page background, so icons/dock won the gesture first).
 *
 * - Swipe up (with [swipeUpAction]) → finger-following drawer via [onDrawerDrag] / [onDrawerSettle].
 * - Swipe down (with [swipeDownAction]) → [onAction] (one-shot).
 * - A real long-press drag — an app/dock item ([HomeDragController.isDragging]) or a folder/shortcut
 *   ([HomeDragController.localGestureActive] / [HomeDragController.localDragging]) — is never stolen;
 *   the detector bails out so the drag owns the gesture.
 *
 * The velocity tracker is fed the UP position *before* `calculateVelocity()` so a fast flick reports
 * its true release speed (the old detector computed velocity without the final sample, which made fast
 * flicks under-settle and snap shut).
 */
@Composable
internal fun Modifier.pixelHomeSwipe(
    swipeUpAction: String,
    swipeDownAction: String,
    dragController: HomeDragController,
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    onAction: (String) -> Unit,
    leftEdgeEnabled: Boolean = false,
    atLeftEdge: () -> Boolean = { false },
    onLeftEdgeAction: () -> Unit = {},
    rightEdgeEnabled: Boolean = false,
    atRightEdge: () -> Boolean = { false },
    onRightEdgeAction: () -> Unit = {},
    iconSwipeEnabled: Boolean = false,
    onIconSwipe: (AppItem, Offset) -> Unit = { _, _ -> },
): Modifier {
    val latestSwipeUpAction by rememberUpdatedState(swipeUpAction)
    val latestSwipeDownAction by rememberUpdatedState(swipeDownAction)
    val latestOnDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val latestOnDrawerSettle by rememberUpdatedState(onDrawerSettle)
    val latestOnAction by rememberUpdatedState(onAction)
    val latestOnLeftEdgeAction by rememberUpdatedState(onLeftEdgeAction)
    val latestOnRightEdgeAction by rememberUpdatedState(onRightEdgeAction)
    val latestAtLeftEdge by rememberUpdatedState(atLeftEdge)
    val latestAtRightEdge by rememberUpdatedState(atRightEdge)
    val latestIconSwipeEnabled by rememberUpdatedState(iconSwipeEnabled)
    val latestOnIconSwipe by rememberUpdatedState(onIconSwipe)

    return pointerInput(swipeUpAction, swipeDownAction, leftEdgeEnabled, rightEdgeEnabled, dragController) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // A touch that starts inside a scrollable collection widget (e.g. WhatsApp's chat list)
            // belongs to that widget's own list — bail without consuming so the scroll falls through to
            // the embedded AppWidgetHostView instead of the swipe-up stealing the vertical drag.
            if (dragController.isOverScrollableWidget(down.position)) return@awaitEachGesture
            // A touch that starts on the dock belongs to the dock's own gestures (tap / long-press for
            // the shortcut popup / drag to reorder or onto home). Bail so the dock owns it end-to-end —
            // otherwise this Initial-pass detector steals a small upward drift during a dock long-press,
            // cancelling the popup and half-opening the drawer (the "2/10 on Samsung" flakiness). Swipe
            // up to open the drawer still works from anywhere above the dock.
            if (dragController.isOverDock(down.position)) return@awaitEachGesture
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            // 0 = undecided, 1 = drawer up-drag, 2 = left-edge action, 3 = notification shade, 4 = right-edge action
            // 5 = swipe up that started on an app icon (see [iconSwipeEnabled]).
            var mode = 0
            var swipedApp: AppItem? = null
            var lastY = down.position.y

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                // Never steal a real long-press drag (an app/dock item, or a folder/shortcut).
                if (dragController.isDragging ||
                    dragController.localGestureActive ||
                    dragController.localDragging
                ) {
                    break
                }

                if (!change.pressed) {
                    if (mode == 1) {
                        val finalDy = change.position.y - lastY
                        if (finalDy != 0f) latestOnDrawerDrag(finalDy)
                        latestOnDrawerSettle(velocityTracker.calculateVelocity().y)
                        mode = 0
                    } else if (mode == 2) {
                        if (change.position.x - down.position.x > size.width * 0.25f) latestOnLeftEdgeAction()
                    } else if (mode == 4) {
                        if (down.position.x - change.position.x > size.width * 0.25f) latestOnRightEdgeAction()
                    } else if (mode == 5) {
                        val app = swipedApp
                        // A deliberate flick, not a twitch: the finger has to travel a little way up.
                        if (app != null && down.position.y - change.position.y > 40.dp.toPx()) {
                            latestOnIconSwipe(app, down.position)
                        }
                    }
                    break
                }

                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y

                if (mode == 0) {
                    when {
                        abs(dy) > touchSlop && abs(dy) > abs(dx) -> when {
                            dy < 0f -> {
                                val action = latestSwipeUpAction
                                val iconApp = if (latestIconSwipeEnabled) dragController.appAt?.invoke(down.position) else null
                                if (iconApp != null) {
                                    // The swipe started on an app icon that has its own swipe action.
                                    swipedApp = iconApp
                                    mode = 5
                                    change.consume()
                                } else if ((action == LauncherSettings.GESTURE_DRAWER || action == LauncherSettings.GESTURE_SEARCH) &&
                                    down.position.y > size.height * 0.15f
                                ) {
                                    mode = 1
                                    change.consume()
                                    latestOnDrawerDrag(dy) // jump to the finger, including the slop moved
                                    lastY = change.position.y
                                } else {
                                    break
                                }
                            }
                            dy > 0f -> {
                                val action = latestSwipeDownAction
                                if (action != LauncherSettings.GESTURE_NONE) {
                                    mode = 3
                                    change.consume()
                                    latestOnAction(action)
                                } else {
                                    break
                                }
                            }
                            else -> {
                                break
                            }
                        }
                        // Horizontal gesture.
                        abs(dx) > touchSlop && abs(dx) >= abs(dy) -> {
                            if (leftEdgeEnabled && dx > 0f && latestAtLeftEdge()) {
                                mode = 2
                                change.consume()
                            } else if (rightEdgeEnabled && dx < 0f && latestAtRightEdge()) {
                                mode = 4
                                change.consume()
                            } else {
                                break
                            }
                        }
                    }
                } else if (mode == 1) {
                    change.consume()
                    latestOnDrawerDrag(change.position.y - lastY)
                    lastY = change.position.y
                } else {
                    // mode >= 2: keep consuming so pager never grabs the rest of the gesture.
                    change.consume()
                }
            }
            if (mode == 1) {
                latestOnDrawerSettle(velocityTracker.calculateVelocity().y)
            }
        }
    }
}
