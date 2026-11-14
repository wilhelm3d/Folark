package org.arkikeskus.launcher.feature.home.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.arkikeskus.launcher.feature.home.HomeEntry
import org.arkikeskus.launcher.feature.home.PendingWidget
import org.arkikeskus.launcher.feature.home.PlacedApp
import org.arkikeskus.launcher.feature.home.PlacedBuiltin
import org.arkikeskus.launcher.feature.home.PlacedFolder
import org.arkikeskus.launcher.feature.home.PlacedShortcut
import org.arkikeskus.launcher.feature.home.PlacedWidget
import org.arkikeskus.launcher.feature.home.PlacedWidgetStack

/**
 * What is on the grid: [all] of it (what is drawn and what blocks a cell), and the built-in widgets
 * and widget stacks on their own, which a widget drag looks up. Moves the database has not
 * reported yet are already applied (see [rememberWorkspaceEntries]).
 */
@Stable
internal class WorkspaceEntries(
    val all: List<HomeEntry>,
    val builtins: List<PlacedBuiltin>,
    val stacks: List<PlacedWidgetStack>,
)

/**
 * The stored [entries] with the drag's optimistic moves applied on top, so a dropped icon, folder,
 * shortcut or widget shows at its new cell at once instead of flashing at the old one until the
 * database flow catches up. Each override is released once the flow reports the entry there.
 */
@Composable
internal fun rememberWorkspaceEntries(entries: List<HomeEntry>, ws: WorkspaceDragState): WorkspaceEntries {
    val placedApps = remember(entries) { entries.filterIsInstance<PlacedApp>() }
    val folders = remember(entries) { entries.filterIsInstance<PlacedFolder>() }
    val placedShortcuts = remember(entries) { entries.filterIsInstance<PlacedShortcut>() }
    val placedWidgets = remember(entries) { entries.filterIsInstance<PlacedWidget>() }
    // Restored-but-unbound widget placeholders (tap to set up). No optimistic/drag handling — they are
    // transient until the user binds (→ PlacedWidget) or removes them.
    val pendingWidgets = remember(entries) { entries.filterIsInstance<PendingWidget>() }
    val builtins = remember(entries) { entries.filterIsInstance<PlacedBuiltin>() }
    val stacks = remember(entries) { entries.filterIsInstance<PlacedWidgetStack>() }
    val widgetOptimistic = ws.widgetOptimistic
    val effectiveWidgets = remember(placedWidgets, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) placedWidgets else placedWidgets.map { w ->
            if (w.rowId == opt.first) {
                w.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else w
        }
    }
    val effectiveBuiltins = remember(builtins, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) builtins else builtins.map { s ->
            if (s.rowId == opt.first) {
                s.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else s
        }
    }
    val effectiveStacks = remember(stacks, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) stacks else stacks.map { s ->
            if (s.rowId == opt.first) {
                s.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else s
        }
    }
    // clear the optimistic override once the DB flow reports the widget at its new bounds. Keyed on
    // the override too: a drop (or resize) back onto the SAME bounds writes nothing, so the flow never
    // emits and the override used to stay forever — pinning the widget to that cell on screen even
    // after a later add pushed its row elsewhere.
    LaunchedEffect(placedWidgets, builtins, stacks, widgetOptimistic) {
        val opt = ws.widgetOptimistic ?: return@LaunchedEffect
        val landed = (placedWidgets.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) } +
            builtins.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) } +
            stacks.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) })
            .any { it == opt }
        if (landed) ws.widgetOptimistic = null
    }

    // Optimistic placements (key -> page/cellX/cellY) applied on top of [placedApps] until the
    // database flow catches up, so an icon doesn't flash at its old cell for a frame on drop.
    // Keys optimistically removed from home (dragged into the dock or a folder) are hidden until the
    // flow drops them, so the icon doesn't reappear at its old cell for a frame.
    val optimistic = ws.optimistic
    val removedKeys = ws.removedKeys
    // Keyed on the overrides too (see the widget override above): an icon dropped back onto its own
    // cell writes nothing, so without this its override would never be released.
    LaunchedEffect(placedApps, optimistic) {
        if (ws.optimistic.isNotEmpty()) {
            ws.optimistic = ws.optimistic.filterNot { (key, pos) ->
                placedApps.any {
                    it.app.key == key && it.page == pos.first &&
                        it.cellX == pos.second && it.cellY == pos.third
                }
            }
        }
        if (ws.removedKeys.isNotEmpty()) {
            ws.removedKeys = ws.removedKeys.filterTo(mutableSetOf()) { rk -> placedApps.any { it.app.key == rk } }
        }
    }
    val effectiveApps = remember(placedApps, optimistic, removedKeys) {
        placedApps
            .filterNot { it.app.key in removedKeys }
            .map { p ->
                optimistic[p.app.key]?.let { (pg, x, y) -> p.copy(page = pg, cellX = x, cellY = y) } ?: p
            }
    }
    // Clear the local optimistic override once the DB flow reports the entry (folder or shortcut) at
    // its new cell.
    val localOptimistic = ws.localOptimistic
    LaunchedEffect(folders, placedShortcuts) {
        val opt = ws.localOptimistic ?: return@LaunchedEffect
        val (id, pos) = opt
        val landed = folders.any { it.id == id && it.page == pos.first && it.cellX == pos.second && it.cellY == pos.third } ||
            placedShortcuts.any { it.rowId == id && it.page == pos.first && it.cellX == pos.second && it.cellY == pos.third }
        if (landed) ws.localOptimistic = null
    }
    val effectiveFolders = remember(folders, localOptimistic) {
        val opt = localOptimistic
        if (opt == null) folders else folders.map { f ->
            if (f.id == opt.first) f.copy(page = opt.second.first, cellX = opt.second.second, cellY = opt.second.third) else f
        }
    }
    val effectiveShortcuts = remember(placedShortcuts, localOptimistic) {
        val opt = localOptimistic
        if (opt == null) placedShortcuts else placedShortcuts.map { s ->
            if (s.rowId == opt.first) s.copy(page = opt.second.first, cellX = opt.second.second, cellY = opt.second.third) else s
        }
    }
    // Apps + folders + pinned shortcuts + widgets + widget stacks together — what's actually on the
    // grid (rendering + occupants). An entry type missing here is never drawn and never blocks a cell.
    val all: List<HomeEntry> = remember(effectiveApps, effectiveFolders, effectiveShortcuts, effectiveWidgets, pendingWidgets, effectiveBuiltins, effectiveStacks) {
        effectiveApps + effectiveFolders + effectiveShortcuts + effectiveWidgets + pendingWidgets + effectiveBuiltins + effectiveStacks
    }
    return remember(all, builtins, stacks) { WorkspaceEntries(all, builtins, stacks) }
}
