package org.arkikeskus.launcher.feature.home

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
            }
            .pointerInput(
                placed.app.key, placed.page, placed.cellX, placed.cellY,
                cellW, cellH, columns, rows, pageCount, lastPage, isDualPage, locked,
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
                            c.consume()
                            if (!c.pressed) {
                                tapped = true
                                return@withTimeoutOrNull
                            }
                            if ((c.position - down.position).getDistance() > slop) {
                                swiped = true
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    if (tapped) {
                        onAppClick(placed.app)
                        return@awaitEachGesture
                    }
                    if (swiped) return@awaitEachGesture
                    // Desktop locked: long-press does nothing (no menu, no drag); tap still launches.
                    if (locked) return@awaitEachGesture
                    // PICK UP
                    dragging = placed
                    dragPos = Offset(
                        pageLeft(placed.page) + placed.cellX * cellW + down.position.x,
                        placed.cellY * cellH + down.position.y,
                    )
                    // Lift into the shared controller (not yet "moving").
                    dragController.start(
                        placed.app,
                        DragSource.Home,
                        dragController.gridBounds.topLeft + dragPos,
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            if (!pagerState.isScrollInProgress) {
                                if (dragPos.x > gridSize.width - edgePx &&
                                    stepPage(1) != pagerState.currentPage
                                ) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            stepPage(1),
                                        )
                                    }
                                } else if (dragPos.x < edgePx &&
                                    pagerState.currentPage > 0
                                ) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            stepPage(-1),
                                        )
                                    }
                                }
                            }
                            // Track the cell the icon would land in.
                            val cx = (xInPage(dragPos.x) / cellW).toInt()
                                .coerceIn(0, columns - 1)
                            val cy = (dragPos.y / cellH).toInt()
                                .coerceIn(0, rows - 1)
                            val nc = IntOffset(cx, cy)
                            if (nc != targetCell) targetCell = nc
                            targetCellPage = pageAtX(dragPos.x)
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
                            when {
                                // Dropped on the "split" target → pick a partner;
                                // the icon itself stays where it was.
                                dragController.dropOnSplit(d.app, rootPos) -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                // Dropped on the taskbar (shown on home) → pin it there; it stays on home too.
                                org.arkikeskus.launcher.ui.TaskbarDropTarget.isOver(rootPos.x, rootPos.y) -> {
                                    org.arkikeskus.launcher.ui.TaskbarDropTarget.pin(d.app)
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
                                    }
                                    // Dock full → reject: icon stays on home.
                                }

                                else -> {
                                    val tx = (xInPage(dragPos.x) / cellW).toInt()
                                        .coerceIn(0, columns - 1)
                                    val ty = (dragPos.y / cellH).toInt()
                                        .coerceIn(0, rows - 1)
                                    val targetPage = pageAtX(dragPos.x)
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
    }
    } } }
}
