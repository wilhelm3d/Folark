package org.arkikeskus.launcher.feature.home.workspace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController

// What one page of the workspace does and looks like around its cells: the gestures on its
// background, the unfold animation, the drop preview, and the page turning while something is
// dragged (split out of Workspace.kt).

/**
 * Book layout: a sideways swipe turns both pages at once. It is watched on the way down to the pages
 * and left alone while anything is being dragged. [isEditing] says whether Edit home is on (an icon
 * then moves at once, so a touch on one is not a page swipe); [stepPage] reads the latest page count.
 */
internal suspend fun PointerInputScope.watchBookSwipe(
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    widgetDragController: WidgetDragController,
    isEditing: () -> Boolean,
    stepPage: (Int) -> Int,
) {
    val ws = grid.ws
    val dragController = env.dragController
    val pagerState = grid.pagerState
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Edit home: a touch on an app moves the app, not the pages.
        if (isEditing() && dragController.appAt?.invoke(dragController.gridBounds.topLeft + down.position) != null) {
            return@awaitEachGesture
        }
        var total = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            total += change.positionChange()
            val busy = ws.dragging != null || ws.draggingLocal != null || ws.editingWidget != null ||
                widgetDragController.active != null || dragController.moving || dragController.localGestureActive
            if (busy) { claimed = false; break }
            if (!claimed && abs(total.x) > slop && abs(total.x) > abs(total.y)) claimed = true
            if (claimed) change.consume()
        }
        if (claimed && abs(total.x) > slop * 2f && !pagerState.isScrollInProgress) {
            val target = stepPage(if (total.x < 0f) 1 else -1)
            if (target != pagerState.currentPage) env.scope.launch { pagerState.animateScrollToPage(target) }
        }
    }
}

/**
 * The detector on a page's background (only reached when no icon is under the finger). A still
 * long-press on empty space opens settings ([onMenu], with the press point and whether the menu
 * should open above it). The same detector also spots clean empty-area taps for double-tap-to-lock:
 * nothing is consumed and nothing fires on a single tap, so every existing gesture is untouched.
 * [isWidgetDragActive] reads the latest widget drag.
 */
internal suspend fun PointerInputScope.watchEmptyArea(
    page: Int,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    isWidgetDragActive: () -> Boolean,
    onMenu: (anchor: IntOffset, above: Boolean) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val ws = grid.ws
    // Still long-press on empty space opens settings. Times out a touch later than the icon
    // long-press, so an icon pickup (which consumes the pointer) always wins and suppresses this.
    var lastTapUpMs = 0L
    var lastTapPos = Offset.Zero
    val doubleTapSlopPx = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        var resolved = false
        var tapUpMs = -1L
        val held = withTimeoutOrNull(
            viewConfiguration.longPressTimeoutMillis + 180L,
        ) {
            while (!resolved) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() >
                    viewConfiguration.touchSlop
                ) {
                    // A clean finger-up (unconsumed, within slop) completed a tap.
                    if (change != null && !change.pressed && !change.isConsumed &&
                        (change.position - down.position).getDistance() <=
                        viewConfiguration.touchSlop
                    ) {
                        tapUpMs = change.uptimeMillis
                    }
                    resolved = true
                }
            }
        }
        // Fired only on a still empty-area hold. If an icon picked up
        // (dragging != null) the hold belonged to that icon, not settings.
        // A lifted widget owns the hold too: its gesture only OBSERVES the press (the
        // hosted view must keep its taps), so this detector still sees the still
        // dwell — without the check the pickup haptic was followed by this menu.
        // Also suppressed while a widget is in edit mode (editingWidget != null):
        // a still dwell on the widget during a move/remove drag must not also
        // open the home-options menu underneath the edit scrim.
        if (held == null && !resolved && ws.dragging == null && ws.editingWidget == null && !isWidgetDragActive()) {
            env.haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // Anchor the options popup at the press point (root coords); flip it
            // above when the press is in the lower half of the screen.
            val anchorPt = env.dragController.gridBounds.topLeft + down.position + Offset(grid.pageLeft(page), 0f)
            onMenu(
                IntOffset(anchorPt.x.roundToInt(), anchorPt.y.roundToInt()),
                anchorPt.y > env.windowHeightPx * 0.45f,
            )
        }
        if (tapUpMs >= 0 && ws.dragging == null && ws.editingWidget == null && !isWidgetDragActive()) {
            // Double-tap = previous tap's UP → this tap's DOWN within the
            // platform timeout, close enough together on screen.
            if (down.uptimeMillis - lastTapUpMs <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastTapPos).getDistance() <= doubleTapSlopPx
            ) {
                lastTapUpMs = 0L
                onDoubleTap()
            } else {
                lastTapUpMs = tapUpMs
                lastTapPos = down.position
            }
        }
    }
}

/**
 * Just unfolded ([unfold] below 1): the pages open out from the hinge (the left one from the right,
 * the right one from the left), or a single page grows into place.
 */
internal fun GraphicsLayerScope.openOutFromHinge(unfold: Float, isDualPage: Boolean, page: Int, pagerState: PagerState) {
    val rest = 1f - unfold
    alpha = 0.35f + 0.65f * unfold
    if (isDualPage) {
        val towardHinge = if (page == pagerState.currentPage) 1f else -1f
        translationX = towardHinge * rest * size.width * UNFOLD_SHIFT
        rotationY = -towardHinge * rest * UNFOLD_TILT_DEG
        cameraDistance = 12f * this.density
    } else {
        scaleX = 1f - rest * UNFOLD_SHRINK
        scaleY = 1f - rest * UNFOLD_SHRINK
    }
}

/** The unfold animation: how far a page starts toward the hinge (of its width), its tilt, and a single page's shrink. */
private const val UNFOLD_SHIFT = 0.22f
private const val UNFOLD_TILT_DEG = 14f
private const val UNFOLD_SHRINK = 0.08f

/**
 * The drop preview: a faint outline of the icon in the cell it would land in, whose cell has its
 * top-left at [cellTopLeft]. [iconPx] is the icon's size (0 until it is known) and [iconDy] how far
 * its centre sits above the cell's centre (the label under it).
 */
internal fun DrawScope.drawDropGhost(cellTopLeft: Offset, cellW: Float, cellH: Float, iconPx: Float, iconDy: Float) {
    val icon = iconPx.takeIf { it > 0f } ?: 52.dp.toPx()
    val topLeft = Offset(
        cellTopLeft.x + (cellW - icon) / 2f,
        cellTopLeft.y + (cellH - icon) / 2f + iconDy,
    )
    val box = Size(icon, icon)
    val corner = CornerRadius(icon * GHOST_CORNER)
    drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft, box, corner)
    drawRoundRect(
        Color.White.copy(alpha = 0.7f), topLeft, box, corner,
        style = Stroke(1.5.dp.toPx()),
    )
}

/** The drop preview's corner, as a share of the icon size. */
private const val GHOST_CORNER = 0.3f

/**
 * Where the drop preview is: [pos] is its cell's top-left (page px) and [page] the page it is on,
 * -1 while there is none. It glides between cells and appears in place (no glide in from the last
 * drag or another page).
 */
@Stable
internal class DropGhost {
    val pos = Animatable(Offset.Zero, Offset.VectorConverter)
    var page by mutableIntStateOf(-1)
}

/**
 * The [DropGhost] following the cell a drag would land in (page, x, y): our own app drag and
 * folder/shortcut moves use the grid's target cell; an app from the dock or the drawer is worked
 * out from the finger. Derived, so it changes only when the cell does, not with every finger move.
 */
@Composable
internal fun rememberDropGhost(grid: WorkspaceGrid, dragController: HomeDragController, cellW: Float, cellH: Float): DropGhost {
    val ws = grid.ws
    val pagerState = grid.pagerState
    val isDualPage = grid.isDualPage
    val ghostTarget by remember(isDualPage) {
        derivedStateOf {
            val tc = ws.targetCell
            when {
                ws.draggingLocal != null && ws.localMoving && tc != null ->
                    Triple(if (isDualPage) ws.targetCellPage else pagerState.currentPage, tc.x, tc.y)
                ws.dragging != null && dragController.moving && tc != null ->
                    if (dragController.isOverDock(dragController.rootPosition)) null
                    else Triple(if (isDualPage) ws.targetCellPage else pagerState.currentPage, tc.x, tc.y)
                dragController.moving &&
                    (dragController.source == DragSource.Dock || dragController.source == DragSource.Drawer) &&
                    dragController.isOverGrid(dragController.rootPosition) -> {
                    val (cellPage, cx, cy) = dragController.cellAt(dragController.rootPosition)
                    Triple(if (isDualPage) cellPage else pagerState.currentPage, cx, cy)
                }
                else -> null
            }
        }
    }
    val ghost = remember { DropGhost() }
    LaunchedEffect(cellW, cellH) {
        snapshotFlow { ghostTarget }.collectLatest { g ->
            if (g == null) {
                ghost.page = -1
                return@collectLatest
            }
            val to = Offset(g.second * cellW, g.third * cellH)
            if (ghost.page != g.first) {
                ghost.pos.snapTo(to)
                ghost.page = g.first
            } else {
                ghost.pos.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 1400f))
            }
        }
    }
    return ghost
}

/**
 * Turning the page while something is dragged: hold it in the edge strip for a moment and the page
 * turns once; keep holding and it turns again after a pause. One place for every drag — an app from
 * home, a folder or shortcut, and an app coming from the dock or the drawer — instead of turning
 * the moment the finger touched a thin strip (and again straight after). Runs until cancelled.
 * [stepPage] reads the latest page count.
 */
internal suspend fun turnPagesForItemDrag(
    grid: WorkspaceGrid,
    dragController: HomeDragController,
    haptics: HapticFeedback,
    stepPage: (Int) -> Int,
) {
    val ws = grid.ws
    val pagerState = grid.pagerState
    snapshotFlow {
        val crossSurface = dragController.moving &&
            (dragController.source == DragSource.Drawer || dragController.source == DragSource.Dock)
        val p: Offset? = when {
            ws.dragging != null && dragController.moving -> ws.dragPos
            ws.draggingLocal != null && ws.localMoving -> ws.localDragPos
            crossSurface -> dragController.rootPosition - dragController.gridBounds.topLeft
            else -> null
        }
        when {
            p == null || p.y < 0f || p.y > ws.gridSize.height -> 0
            p.x > ws.gridSize.width - grid.edgePx -> 1
            p.x < grid.edgePx -> -1
            else -> 0
        }
    }.collectLatest { direction ->
        if (direction == 0) return@collectLatest
        delay(EDGE_FLIP_HOLD_MS)
        while (true) {
            val next = stepPage(direction)
            if (next == pagerState.currentPage) break
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            pagerState.animateScrollToPage(next)
            delay(EDGE_FLIP_REPEAT_MS)
        }
    }
}
