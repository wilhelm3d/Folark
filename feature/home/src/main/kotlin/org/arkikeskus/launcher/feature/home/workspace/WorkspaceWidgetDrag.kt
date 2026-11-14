package org.arkikeskus.launcher.feature.home.workspace

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Density
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.feature.home.HomeEntry
import org.arkikeskus.launcher.feature.home.PlacedApp
import org.arkikeskus.launcher.feature.home.PlacedBuiltin
import org.arkikeskus.launcher.feature.home.PlacedWidget
import org.arkikeskus.launcher.feature.home.PlacedWidgetStack
import org.arkikeskus.launcher.feature.home.widgets.EditingItem
import org.arkikeskus.launcher.feature.home.widgets.WidgetChoice
import org.arkikeskus.launcher.feature.home.widgets.WidgetDrag
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.feature.home.widgets.widgetDropPlacement
import org.arkikeskus.launcher.model.WidgetPlacement
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.HomeDragController

// How a widget is picked up on the home grid, where it may drop, and what a drop does. [Workspace]
// points the widget drag at its grid with these (split out of Workspace.kt).

/** Starts dragging the widget or stack [item] (already on the grid) from where the finger is. */
internal fun startWidgetLift(
    context: Context,
    haptics: HapticFeedback,
    widgetDragController: WidgetDragController,
    widgetViews: Map<Int, AppWidgetHostView>,
    onGrid: WorkspaceEntries,
    item: EditingItem,
    root: Offset,
    fraction: Offset,
    owner: Any,
) {
    // A second finger on another widget: the first drag keeps the controller (no snapshot, no buzz).
    if (widgetDragController.active != null) return
    // A stack has no content of its own: its drag preview is the first widget in it.
    val stackFront = onGrid.stacks.firstOrNull { it.rowId == item.rowId }?.items?.firstOrNull()
    val previewWidgetId = item.appWidgetId ?: (stackFront as? PlacedWidget)?.appWidgetId
    val bitmap = previewWidgetId?.let { id ->
        widgetViews[id]?.let { view -> catching { view.drawToBitmap() }.getOrNull() }
    }
    val builtin = onGrid.builtins.firstOrNull { it.rowId == item.rowId } ?: (stackFront as? PlacedBuiltin)
    val choice = builtin?.let { WidgetChoice.Builtin(it.type) } ?: previewWidgetId?.let { id ->
        catching { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }.getOrNull()
            ?.let { WidgetChoice.App(it) }
    }
    widgetDragController.start(
        WidgetDrag(item, choice,
            item.spanX, item.spanY, fraction, bitmap), root, owner,
    )
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}

/**
 * Points [widgetDragController] (and the swipe-up-on-an-icon lookup of [dragController]) at this
 * grid: the cell size, where a dragged widget may drop, where a new one finds room, and what a
 * still hold or a drop does. [entries] is the grid as drawn; the handlers read the latest
 * placements through [env], as they outlive the composition that installed them.
 */
internal fun installWidgetDragHandlers(
    widgetDragController: WidgetDragController,
    dragController: HomeDragController,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    density: Density,
    cellW: Float,
    cellH: Float,
    entries: List<HomeEntry>,
    dropTargetCache: DropTargetCache,
    onAddWidget: (WidgetChoice, WidgetPlacement) -> Unit,
    onRemoveWidget: (rowId: Long, appWidgetId: Int?) -> Unit,
    onCreateWidgetStack: (targetRowId: Long, droppedRowId: Long) -> Unit,
    onAddToWidgetStack: (stackId: Long, widgetRowId: Long) -> Unit,
    onSetWidgetBounds: suspend (rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean,
) {
    val ws = grid.ws
    widgetDragController.cellWidthDp = cellW / density.density
    widgetDragController.cellHeightDp = cellH / density.density
    widgetDragController.target = {
        val drag = widgetDragController.active
        if (drag == null || grid.locked || grid.pagerState.isScrollInProgress) null else {
            val pos = widgetDragController.position - dragController.gridBounds.topLeft
            val raw = widgetDropPlacement(grid.pageAtX(pos.x), grid.xInPage(pos.x), pos.y,
                grid.pageWidthPx(), ws.gridSize.height.toFloat(), grid.columns, grid.rows,
                drag.spanX, drag.spanY, drag.grabFraction.x, drag.grabFraction.y
            )
            val rowId = drag.item?.rowId
            // This runs for every frame of a drag, but the answer only changes when the finger
            // crosses into another cell (or the layout changes): the stack check and the
            // push-aside plan are worked out once per cell, not once per frame.
            val cache = dropTargetCache
            val latest = env.latestEntries
            if (raw != cache.placement || rowId != cache.rowId || latest !== cache.entries) {
                cache.placement = raw
                cache.rowId = rowId
                cache.entries = latest
                cache.valid = raw != null && (
                    // Dropping onto another widget stacks the two, which needs no free cells at all.
                    (rowId != null && stackTargetFor(latest, rowId, raw) != null) ||
                        canPlaceWidget(latest, grid.columns, grid.rows, raw, rowId ?: Long.MIN_VALUE)
                    )
            }
            raw?.takeIf { cache.valid }
        }
    }
    widgetDragController.findSpace = { sx, sy ->
        val current = grid.pagerState.currentPage
        val pages = listOf(current) + (0..grid.lastPage).filter { it != current }
        pages.firstNotNullOfOrNull { page ->
            val candidates = (0..(grid.rows - sy)).flatMap { y ->
                (0..(grid.columns - sx)).map { x -> WidgetPlacement(page, x, y, sx, sy) }
            }
            candidates.firstOrNull {
                isRectFree(entries, grid.columns, grid.rows, Long.MIN_VALUE, it.page, it.cellX, it.cellY, sx, sy)
            } ?: candidates.firstOrNull { canPlaceWidget(env.latestEntries, grid.columns, grid.rows, it) }
        }
    }
    widgetDragController.keepsGestureLock = { ws.editingWidget != null }
    // For the swipe-up-on-an-icon gesture: the app whose cell is under a root position.
    dragController.appAt = { p ->
        if (!dragController.isOverGrid(p)) null else {
            val (page, cellX, cellY) = dragController.cellAt(p)
            env.latestEntries.firstNotNullOfOrNull { e ->
                (e as? PlacedApp)?.takeIf { it.page == page && it.cellX == cellX && it.cellY == cellY }?.app
            }
        }
    }
    widgetDragController.onStill = { drag ->
        drag.item?.let { ws.editingWidget = it }
    }
    widgetDragController.onDrop = { drag, target ->
        val item = drag.item
        if (!grid.locked && item != null && dragController.isOverRemove(widgetDragController.position)) {
            onRemoveWidget(item.rowId, item.appWidgetId)
            ws.editingWidget = null
        } else if (!grid.locked && target != null) {
            if (item != null) {
                val occupant = stackTargetFor(env.latestEntries, item.rowId, target)
                when (occupant) {
                    is PlacedWidget -> {
                        ws.editingWidget = null
                        onCreateWidgetStack(occupant.rowId, item.rowId)
                    }
                    is PlacedBuiltin -> {
                        ws.editingWidget = null
                        onCreateWidgetStack(occupant.rowId, item.rowId)
                    }
                    is PlacedWidgetStack -> {
                        ws.editingWidget = null
                        onAddToWidgetStack(occupant.rowId, item.rowId)
                    }
                    else -> {
                        ws.editingWidget = null
                        ws.widgetOptimistic = item.rowId to WidgetBounds(target.page, target.cellX, target.cellY, target.spanX, target.spanY)
                        env.scope.launch {
                            if (onSetWidgetBounds(item.rowId, target.page, target.cellX, target.cellY, target.spanX, target.spanY)) {
                                ws.editingWidget = item.copy(page = target.page, cellX = target.cellX, cellY = target.cellY,
                                    spanX = target.spanX, spanY = target.spanY)
                            } else {
                                ws.widgetOptimistic = null
                            }
                        }
                    }
                }
            } else {
                drag.choice?.let { onAddWidget(it, target) }
            }
        }
    }
}

/** Takes the handlers [installWidgetDragHandlers] set back off, and ends a drag still under way. */
internal fun clearWidgetDragHandlers(widgetDragController: WidgetDragController, dragController: HomeDragController) {
    widgetDragController.cancel()
    widgetDragController.target = null
    widgetDragController.findSpace = null
    widgetDragController.onDrop = null
    widgetDragController.onStill = null
    widgetDragController.keepsGestureLock = { false }
    dragController.appAt = null
}

/**
 * While a widget is dragged against either side of the grid, holds there and turns the page, again
 * after each pause. Runs until cancelled (the effect restarts when the drag changes).
 */
internal suspend fun turnPagesForWidgetDrag(
    widgetDragController: WidgetDragController,
    dragController: HomeDragController,
    grid: WorkspaceGrid,
) {
    if (!widgetDragController.moving) return
    val ws = grid.ws
    snapshotFlow {
        val p = widgetDragController.position - dragController.gridBounds.topLeft
        when {
            p.y < 0 || p.y >= ws.gridSize.height -> 0
            p.x < grid.edgePx -> -1
            p.x > ws.gridSize.width - grid.edgePx -> 1
            else -> 0
        }
    }.collectLatest { direction ->
        if (direction == 0) return@collectLatest
        while (widgetDragController.moving) {
            delay(550)
            val next = grid.stepPage(direction)
            if (next == grid.pagerState.currentPage) break
            grid.pagerState.animateScrollToPage(next)
        }
    }
}
