package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetHostView
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * What the grid's item cells (WorkspaceItemCells.kt) need from [Workspace] beyond the shared drag
 * state, grid and environment: how items look, the widget host views, and the host's callbacks.
 * Callbacks that long-lived gestures call read the latest ones (Workspace passes lambdas that do).
 */
@Stable
internal class WorkspaceCellDeps(
    val widgetDragController: WidgetDragController,
    val widgetLooks: Map<Long, WidgetLook>,
    val widgetViews: Map<Int, AppWidgetHostView>,
    val widgetScrollableById: Map<Int, Boolean>,
    val liftWidget: (item: EditingItem, root: Offset, fraction: Offset, owner: Any) -> Unit,
    val showLabels: Boolean,
    val labelColor: Color,
    val cellIconSize: Dp,
    val badges: BadgeCounts,
    val badgeShowCount: Boolean,
    val badgeScale: Float,
    val glassBlurRadius: Float,
    val glassDarkTint: Float,
    val currentFolderTap: (PlacedFolder) -> Unit,
    val currentOpenFolder: (PlacedFolder) -> Unit,
    /** An app tapped right on a folder's tile (Settings ▸ Folders ▸ Open apps from the tile). */
    val currentFolderAppTap: (org.arkikeskus.launcher.model.AppItem) -> Unit = {},
    val currentLaunchShortcut: (PlacedShortcut) -> Unit,
    val currentRemoveShortcut: (Long) -> Unit,
    val onRestorePendingWidget: (PendingWidget) -> Unit,
    val onRemovePendingWidget: (rowId: Long) -> Unit,
    val onRemoveWidget: (rowId: Long, appWidgetId: Int?) -> Unit,
    val onAddToWidgetStack: (stackId: Long, widgetRowId: Long) -> Unit,
    val onRemoveFromWidgetStack: (stackId: Long, itemRowId: Long) -> Unit,
)
