package org.arkikeskus.launcher.feature.home.workspace

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.widgets.*
import org.arkikeskus.launcher.feature.home.taskbar.*

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.arkikeskus.launcher.model.WidgetPlacement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.data.ReorderPlanner
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.aquamorphicTouch
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.iconSizeForCell
import org.arkikeskus.launcher.ui.component.labelBlockHeight
import org.arkikeskus.launcher.ui.component.labelFontFactor
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.VectorConverter

/**
 * One app on the home grid: its icon (with badge) at its cell, the tap that launches it, and the
 * long-press that lifts it into the shared drag (menu, move, folder, dock, taskbar, split, remove).
 * Split out of [Workspace] so an unchanged cell skips recomposition when other entries change.
 */
@Composable
internal fun WorkspaceAppCell(
    placed: PlacedApp,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    badges: BadgeCounts,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showLabels: Boolean,
    labelColor: Color,
    iconSize: Dp,
) {
    val density = LocalDensity.current
    val cellIconSize = iconSize
    // Edit home: marks on the icon and taps that select; a just-added app pops in.
    val edit = org.arkikeskus.launcher.feature.home.LocalHomeEdit.current
    val editing = edit?.editing == true
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(edit?.revealAt) {
        val e = edit ?: return@LaunchedEffect
        // Only right after the add (a page scrolled into view later must not pop again).
        if (e.revealKey == placed.app.key && System.nanoTime() - e.revealAt < REVEAL_WINDOW_NS) {
            pop.snapTo(0.3f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 260f))
        }
    }
    // A drop glides the icon from where it was let go into its cell (or back to where it was).
    val glide = remember { androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(placed.app.key) {
        snapshotFlow { ws.settle }.collectLatest { st ->
            if (st == null || st.key != placed.app.key) return@collectLatest
            if (System.nanoTime() - st.token > SETTLE_STALE_NS) return@collectLatest
            glide.snapTo(settleDelta(st, grid, ws))
            ws.settleStarted = st.token
            glide.animateTo(
                Offset.Zero,
                androidx.compose.animation.core.tween(SETTLE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
            if (ws.settle?.token == st.token) ws.settle = null
        }
    }
    with(ws) { with(grid) { with(env) {
    Box(
        modifier = Modifier
            .offset {
                // Safety net only: the repository reflows on column shrink,
                // but clamp here too so a stale out-of-range cell can never
                // draw off-screen for a frame before the reflow lands.
                val safeCellX = placed.cellX.coerceIn(0, columns - 1)
                val safeCellY = placed.cellY.coerceIn(0, rows - 1)
                IntOffset(
                    (safeCellX * cellW).roundToInt(),
                    (safeCellY * cellH).roundToInt(),
                )
            }
            .size(
                with(density) { cellW.toDp() },
                with(density) { cellH.toDp() },
            )
            // Hide (but keep in composition) the icon once it's actually
            // moving — the floating copy is drawn on top. While only lifted
            // (menu showing) it stays visible.
            .graphicsLayer {
                alpha = if (dragging?.app?.key == placed.app.key &&
                    dragController.moving
                ) 0f else 1f
                scaleX = pop.value
                scaleY = pop.value
                // Gliding in after a drop; until the glide starts, drawn where it was let go.
                val st = settle
                if (st != null && st.key == placed.app.key) {
                    val t = if (settleStarted == st.token) glide.value else settleDelta(st, grid, ws)
                    translationX = t.x
                    translationY = t.y
                }
            }
            .pointerInput(
                placed.app.key, placed.page, placed.cellX, placed.cellY,
                cellW, cellH, columns, rows, pageCount, lastPage, isDualPage, locked, editing,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Claim the gesture immediately: an icon touch belongs
                    // to the icon, so the pager and the page swipes can
                    // never steal it (not even from finger jitter during the
                    // long-press hold). Swipes for drawer/notifications start
                    // on empty space.
                    down.consume()
                    val slop = viewConfiguration.touchSlop
                    // Quick up = tap; movement = abandon (drag needs a
                    // long-press); still hold = pick up to drag.
                    var tapped = false
                    var swiped = false
                    // Edit mode: the minus on the icon (a child) took the tap itself.
                    var markTook = false
                    // Edit mode: moving an icon picks it up at once (no long-press needed).
                    var pickNow = false
                    // Where the finger is now (it may drift a little during the hold).
                    var fingerAt = down.position
                    withTimeoutOrNull(
                        viewConfiguration.longPressTimeoutMillis,
                    ) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null) {
                                swiped = true
                                return@withTimeoutOrNull
                            }
                            if (editing && !c.pressed && c.isConsumed) {
                                markTook = true
                                return@withTimeoutOrNull
                            }
                            // A home swipe (drawer, shade, book page turn) took the touch first.
                            if (c.pressed && c.isConsumed) {
                                swiped = true
                                return@withTimeoutOrNull
                            }
                            c.consume()
                            if (!c.pressed) {
                                tapped = true
                                return@withTimeoutOrNull
                            }
                            fingerAt = c.position
                            val moved = (c.position - down.position).getDistance()
                            if (editing && !locked && moved > slop) {
                                pickNow = true
                                return@withTimeoutOrNull
                            }
                            // A little drift (a thumb on the big screen) still picks up.
                            if (moved > slop * HOLD_DRIFT_SLOPS) {
                                swiped = true
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    if (markTook) return@awaitEachGesture
                    if (tapped) {
                        // Edit mode: a tap selects (several can be removed, filed or moved at once).
                        if (edit != null && edit.editing) edit.toggle(placed.app.key) else onAppClick(placed.app)
                        return@awaitEachGesture
                    }
                    if (swiped) return@awaitEachGesture
                    // Desktop locked: long-press does nothing (no menu, no drag); tap still launches.
                    if (locked) return@awaitEachGesture
                    // PICK UP
                    dragging = placed
                    dragPos = Offset(
                        pageLeft(placed.page) + placed.cellX * cellW + fingerAt.x,
                        placed.cellY * cellH + fingerAt.y,
                    )
                    // The icon keeps the spot it was grabbed by: from the finger to its centre.
                    grab = Offset(cellW / 2f, cellH / 2f + iconDy) - down.position
                    // Lift into the shared controller (not yet "moving").
                    dragController.start(
                        placed.app,
                        DragSource.Home,
                        dragController.gridBounds.topLeft + dragPos,
                        grab = grab,
                        size = cellIconSize,
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Edit mode: it is already moving.
                    if (pickNow) dragController.beginMove()
                    try {
                        // DRAG
                        val completed = drag(down.id) { change ->
                            val delta = change.positionChange()
                            change.consume()
                            dragPos += delta
                            // Any movement past the touch slop (drag() already
                            // enforces it) is a drag → start moving immediately so
                            // the floating icon tracks the finger from the start.
                            if (!dragController.moving) dragController.beginMove()
                            dragController.update(
                                dragController.gridBounds.topLeft + dragPos,
                            )
                            // (Page turns at the edges: Workspace's edge-flip effect.)
                            // Track the cell the icon would land in — under the icon's centre,
                            // which is what the eye follows, not under the finger.
                            val ic = iconCentre(dragPos + grab)
                            val cx = (xInPage(ic.x) / cellW).toInt()
                                .coerceIn(0, columns - 1)
                            val cy = (ic.y / cellH).toInt()
                                .coerceIn(0, rows - 1)
                            val nc = IntOffset(cx, cy)
                            if (nc != targetCell) targetCell = nc
                            targetCellPage = pageAtX(ic.x)
                        }
                        // DROP — only act on a real finger-up (completed),
                        // never on a cancellation, so the menu can't pop up
                        // under a still-pressed finger.
                        val d = dragging
                        if (d != null && completed && !dragController.moving) {
                            // Static long-press → show the menu next to the icon
                            // (after release, so it can't steal the drag). Anchor at
                            // the icon's TOP edge when it's in the lower half (popup
                            // flips above) and its BOTTOM edge otherwise (popup
                            // below) — so the popup never covers the icon.
                            val cellTop = dragController.gridBounds.top + d.cellY * cellH
                            // Bias toward opening upward: only icons in the top
                            // ~45% open downward (room below); the middle and
                            // everything lower open up so a tall popup never
                            // covers the dock.
                            val above = cellTop + cellH / 2f > windowHeightPx * 0.45f
                            val anchorX = dragController.gridBounds.left + pageLeft(d.page) + d.cellX * cellW + cellW / 2f
                            val anchorY = if (above) cellTop else cellTop + cellH
                            onAppMenu(
                                d.app,
                                IntOffset(anchorX.roundToInt(), anchorY.roundToInt()),
                                above,
                            )
                        } else if (d != null && completed && dragController.moving) {
                            val rootPos = dragController.gridBounds.topLeft + dragPos
                            // Where the floating icon's centre was let go (grid px), for the glide.
                            val dropAt = dragPos + grab
                            fun glideTo(page: Int, x: Int, y: Int) {
                                settle = Settle(d.app.key, dropAt, page, x, y, System.nanoTime())
                            }
                            fun glideBack() = glideTo(d.page, d.cellX, d.cellY)
                            when {
                                // Dropped on Uninstall or App info → that; the icon goes back.
                                dragController.dropOnSystemTarget(d.app, rootPos) -> {
                                    glideBack()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                // Dropped on the "split" target → pick a partner;
                                // the icon itself goes back where it was.
                                dragController.dropOnSplit(d.app, rootPos) -> {
                                    glideBack()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                // Dropped on the taskbar (shown on home) → pin it there; it stays on home too.
                                org.arkikeskus.launcher.ui.TaskbarDropTarget.isOver(rootPos.x, rootPos.y) -> {
                                    org.arkikeskus.launcher.ui.TaskbarDropTarget.pin(d.app)
                                    glideBack()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                // Dropped on the top "remove" zone → take it off home.
                                dragController.isOverRemove(rootPos) -> {
                                    removedKeys = removedKeys + d.app.key
                                    onRemoveFromHome(d.app)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                // Cross-surface: dropped on the dock.
                                dragController.isOverDock(rootPos) -> {
                                    if (dragController.dockHasSpace) {
                                        // Hide from home until the flow drops it.
                                        removedKeys = removedKeys + d.app.key
                                        onMoveToDock(
                                            d.app,
                                            dragController.dockIndexAt(rootPos),
                                        )
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                    } else {
                                        // Dock full → reject: the icon goes back on home.
                                        glideBack()
                                    }
                                }

                                else -> {
                                    val ic = iconCentre(dropAt)
                                    val tx = (xInPage(ic.x) / cellW).toInt()
                                        .coerceIn(0, columns - 1)
                                    val ty = (ic.y / cellH).toInt()
                                        .coerceIn(0, rows - 1)
                                    val targetPage = pageAtX(ic.x)
                                    val occupant = latestEntries.firstOrNull {
                                        it.page == targetPage && it.cellX == tx && it.cellY == ty &&
                                            !(it is PlacedApp && it.app.key == d.app.key)
                                    }
                                    when (occupant) {
                                        // Drop on another app → create a folder.
                                        is PlacedApp -> {
                                            removedKeys = removedKeys + d.app.key
                                            onCreateFolder(occupant.app, d.app)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        // Drop on a folder → add the app to it.
                                        is PlacedFolder -> {
                                            removedKeys = removedKeys + d.app.key
                                            onAddToFolder(d.app, occupant.id)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        // Free cell → move (optimistic; roll back if rejected).
                                        else -> {
                                            optimistic = optimistic + (d.app.key to Triple(targetPage, tx, ty))
                                            glideTo(targetPage, tx, ty)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                if (!onMove(d.app, targetPage, tx, ty)) {
                                                    optimistic = optimistic - d.app.key
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        // Reset even on cancellation (node disposed / pointerInput
                        // restarted), mirroring the local-drag path below, so a dead
                        // gesture can't leave the shared controller lifted.
                        targetCell = null
                        dragController.stop()
                        dragging = null
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            WithBadge({ badges[placed.app.badgeKey] ?: 0 }) { badgeCount ->
                AppIcon(
                    appItem = placed.app,
                    labelColor = labelColor,
                    showLabel = showLabels,
                    iconSize = cellIconSize,
                    // Fixed centred cell: reserve the full label block so a name that
                    // wraps does not lift its icon above its neighbours'.
                    reserveLabelLines = true,
                    badgeCount = badgeCount,
                    badgeShowCount = badgeShowCount,
                    badgeScale = badgeScale,
                )
            }
            if (edit != null && edit.editing) {
                org.arkikeskus.launcher.feature.home.EditMarks(
                    selected = placed.app.key in edit.selected,
                    iconSize = cellIconSize,
                    onRemove = { onRemoveFromHome(placed.app) },
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-6).dp),
                )
            }
        }
    }
    } } }
}

/** How soon after an "Add to home" the added app still pops in when its cell appears. */
private const val REVEAL_WINDOW_NS = 2_000_000_000L

/** A point kept inside the grid, so an icon held past an edge still lands in the nearest cell. */
private fun WorkspaceDragState.iconCentre(p: Offset): Offset = Offset(
    p.x.coerceIn(0f, (gridSize.width - 1).coerceAtLeast(0).toFloat()),
    p.y.coerceIn(0f, (gridSize.height - 1).coerceAtLeast(0).toFloat()),
)

/** From the cell's resting icon to where a [Settle] starts (grid px). */
internal fun settleDelta(st: Settle, grid: WorkspaceGrid, ws: WorkspaceDragState): Offset {
    val rest = Offset(
        grid.pageLeft(st.page) + (st.x + 0.5f) * grid.cellW,
        (st.y + 0.5f) * grid.cellH + ws.iconDy,
    )
    return st.from - rest
}
