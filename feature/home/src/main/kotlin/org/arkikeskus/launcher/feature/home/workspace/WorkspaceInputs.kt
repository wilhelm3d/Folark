package org.arkikeskus.launcher.feature.home.workspace

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import org.arkikeskus.launcher.feature.home.PendingWidget
import org.arkikeskus.launcher.feature.home.PlacedFolder
import org.arkikeskus.launcher.feature.home.PlacedShortcut
import org.arkikeskus.launcher.feature.home.WidgetLook
import org.arkikeskus.launcher.feature.home.widgets.WidgetChoice
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.MinusOne
import org.arkikeskus.launcher.model.PageEffect
import org.arkikeskus.launcher.model.WidgetPlacement

// What [Workspace] is handed, in two bundles instead of fifty loose parameters: the settings it
// follows (plain values, so an unchanged bundle compares equal and the workspace skips) and what it
// tells its host. Both are data classes for that reason: equal contents, equal bundles. Neither has
// defaults: the one host hands every value, so a forgotten callback is a compile error, not a dead tap.

/** The home settings the workspace follows: the grid, how cells look, how the pages move. */
@Immutable
data class WorkspaceSettings(
    val columns: Int,
    val rows: Int,
    val showLabels: Boolean,
    val labelColor: Color,
    val badgeShowCount: Boolean,
    val badgeScale: Float,
    val glassBlurRadius: Float,
    val glassDarkTint: Float,
    val showPageIndicator: Boolean,
    val locked: Boolean,
    /** Book layout: two half-width pages side by side (inner display). */
    val isDualPage: Boolean,
    val pageBounceEnabled: Boolean,
    /** Swipe effect of the pages. */
    val pageScrollEffect: PageEffect,
    /** Whether a drag past the last page may open a new one (never in the book layout). */
    val allowNewPage: Boolean,
    val minusOneScreen: MinusOne,
    /** The animation speed multiplier (the page settle follows it). */
    val animationSpeed: Float,
)

/** What the workspace tells its host: taps, moves, drops and the widget edits. */
@Immutable
data class WorkspaceActions(
    val onAppClick: (AppItem) -> Unit,
    /** Long-press menu of an app: the app, where to anchor the menu, and whether it opens upwards. */
    val onAppMenu: (AppItem, IntOffset, Boolean) -> Unit,
    /** Moves an app to (page, cellX, cellY); false when storage refused it. */
    val onMove: suspend (AppItem, Int, Int, Int) -> Boolean,
    /** Moves a folder (by id) to (page, cellX, cellY); false when storage refused it. */
    val onMoveFolder: suspend (Long, Int, Int, Int) -> Boolean,
    val onMoveToDock: (AppItem, Int) -> Unit,
    val onRemoveFromHome: (AppItem) -> Unit,
    val onOpenFolder: (PlacedFolder) -> Unit,
    /** A plain tap on a folder. Opens it by default; the host may launch its first app instead
     *  (Settings ▸ Folders), in which case a still long-press is what opens the folder. */
    val onFolderTap: (PlacedFolder) -> Unit,
    /** An app tapped right on a folder's tile (Settings ▸ Folders ▸ Open apps from the tile). */
    val onFolderAppTap: (AppItem) -> Unit,
    val onLaunchShortcut: (PlacedShortcut) -> Unit,
    val onRemoveShortcut: (Long) -> Unit,
    val onCreateFolder: (target: AppItem, dropped: AppItem) -> Unit,
    val onAddToFolder: (app: AppItem, folderId: Long) -> Unit,
    /** Long-press on empty space: where to anchor the menu, and whether it opens upwards. */
    val onEmptyAreaMenu: (IntOffset, Boolean) -> Unit,
    /** Two clean taps on empty space within the platform double-tap timeout. The host decides what
     *  (if anything) happens; the workspace only reports the gesture. */
    val onEmptyAreaDoubleTap: () -> Unit,
    val onAddWidget: (WidgetChoice, WidgetPlacement) -> Unit,
    /** Removes a widget-like row from the grid; appWidgetId is null for a built-in widget (no host id). */
    val onRemoveWidget: (rowId: Long, appWidgetId: Int?) -> Unit,
    val onSetWidgetBounds: suspend (rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean,
    val onReconfigureWidget: (appWidgetId: Int) -> Unit,
    val onWidgetLookChange: (rowId: Long, look: WidgetLook?) -> Unit,
    /** Tap a restored-widget placeholder to re-bind it; long-press to discard the placeholder row. */
    val onRestorePendingWidget: (PendingWidget) -> Unit,
    val onRemovePendingWidget: (rowId: Long) -> Unit,
    val onCreateWidgetStack: (targetRowId: Long, droppedRowId: Long) -> Unit,
    val onAddToWidgetStack: (stackId: Long, widgetRowId: Long) -> Unit,
    val onRemoveFromWidgetStack: (stackId: Long, itemRowId: Long) -> Unit,
)
