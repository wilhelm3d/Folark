package org.arkikeskus.launcher.feature.home.workspace

import org.arkikeskus.launcher.data.ReorderPlanner
import org.arkikeskus.launcher.feature.home.HomeEntry
import org.arkikeskus.launcher.feature.home.PendingWidget
import org.arkikeskus.launcher.feature.home.WidgetEntry
import org.arkikeskus.launcher.feature.home.widgets.widgetRects
import org.arkikeskus.launcher.model.WidgetPlacement

// Where a widget can go on the home grid, worked out from the placements on it. Plain functions of
// the entries (no screen state), so a drag asks them for every frame and tests ask them directly.

/**
 * Client-side mirror of rectFitsForRow over the rendered [entries], for the live drag placeholder:
 * whether the [spanX]×[spanY] cells at ([x], [y]) on [page] lie inside the [columns]×[rows] grid and
 * are clear of everything but the widget [excludeRowId].
 */
internal fun isRectFree(
    entries: List<HomeEntry>,
    columns: Int,
    rows: Int,
    excludeRowId: Long,
    page: Int,
    x: Int,
    y: Int,
    spanX: Int,
    spanY: Int,
): Boolean {
    if (x < 0 || y < 0 || x + spanX > columns || y + spanY > rows) return false
    val occupied = HashSet<Triple<Int, Int, Int>>()
    for (e in entries) {
        val w = e as? WidgetEntry
        if (w != null && w.rowId == excludeRowId) continue
        val ex = w?.spanX ?: 1
        val ey = w?.spanY ?: 1
        for (dx in 0 until ex) for (dy in 0 until ey) occupied += Triple(e.page, e.cellX + dx, e.cellY + dy)
    }
    for (dx in 0 until spanX) for (dy in 0 until spanY) if (Triple(page, x + dx, y + dy) in occupied) return false
    return true
}

/** Whether a widget at [p] fits the [columns]×[rows] grid, the others making room if they can. */
internal fun canPlaceWidget(
    entries: List<HomeEntry>,
    columns: Int,
    rows: Int,
    p: WidgetPlacement,
    rowId: Long = Long.MIN_VALUE,
): Boolean =
    ReorderPlanner.planFit(entries.widgetRects(), rowId, p.page, p.cellX, p.cellY, p.spanX, p.spanY, columns, rows) != null

/**
 * The widget (or stack) that the dragged widget [draggedRowId] would be stacked onto if dropped at
 * [p]: the one it covers most, provided it covers MORE than half of the smaller of the two
 * footprints. A lighter overlap stays an ordinary move that pushes the neighbour aside, so
 * nudging a widget next to another one never stacks them by accident.
 */
internal fun stackTargetFor(entries: List<HomeEntry>, draggedRowId: Long, p: WidgetPlacement): HomeEntry? {
    var best: HomeEntry? = null
    var bestOverlap = 0
    for (e in entries) {
        // Only a placed widget, built-in or stack takes a stack (not apps, nor a pending placeholder).
        if (e.page != p.page || e !is WidgetEntry || e is PendingWidget || e.rowId == draggedRowId) continue
        val sx = e.spanX
        val sy = e.spanY
        val overlapX = minOf(e.cellX + sx, p.cellX + p.spanX) - maxOf(e.cellX, p.cellX)
        val overlapY = minOf(e.cellY + sy, p.cellY + p.spanY) - maxOf(e.cellY, p.cellY)
        if (overlapX <= 0 || overlapY <= 0) continue
        val overlap = overlapX * overlapY
        if (overlap * 2 > minOf(sx * sy, p.spanX * p.spanY) && overlap > bestOverlap) {
            best = e
            bestOverlap = overlap
        }
    }
    return best
}
