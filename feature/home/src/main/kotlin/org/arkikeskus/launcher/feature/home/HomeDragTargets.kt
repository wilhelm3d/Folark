package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.LauncherIcons

// The drop targets that appear at the top of home while an icon is dragged (split out of HomeScreen.kt).

/**
 * Drop targets at the top while an icon is dragged, side by side:
 *  - "Remove" during any removable home drag (an app from home/dock, or a pinned shortcut via
 *    [HomeDragController.localDragging]); dropping an icon on it removes it from home.
 *  - "Split" during any app drag (home, dock or drawer) when the setting is on; dropping an
 *    app on it opens the partner picker for a split screen.
 * Each lights up while the dragged icon is over it and publishes its bounds for the hit-test.
 */
@Composable
internal fun BoxScope.HomeDragTargets(dragController: HomeDragController, settings: LauncherSettings) {
    val context = LocalContext.current
    val showRemoveTarget = (dragController.moving &&
        (dragController.source == DragSource.Home || dragController.source == DragSource.Dock)) ||
        dragController.localDragging
    val showSplitTarget = dragController.moving && settings.launch.splitEnabled
    // Folder chips while an app is dragged out of the drawer and it has folder tabs: a chip
    // takes the app into that folder, the All chip (with a folder tab open) pulls it out.
    val showFolderChips = dragController.moving &&
        dragController.source == DragSource.Drawer &&
        dragController.folderTargets.isNotEmpty()
    // Unfolded: a pill per split drop zone that is on (Left / Right / Three); folded, or with
    // those three zones off: the single "Split" pill and its partner picker.
    val zones = settings.recents.dropZones
    val splitZones = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) {
        listOfNotNull(
            DropZones.LEFT.takeIf { zones.left },
            DropZones.RIGHT.takeIf { zones.right },
            DropZones.THREE.takeIf { zones.three },
        )
    } else {
        emptyList()
    }
    LaunchedEffect(showSplitTarget, splitZones) {
        dragController.dropZones = if (splitZones.isEmpty()) DropZones(left = false, right = false, three = false) else zones
        // Bounds of pills that aren't shown must never catch a drop.
        if (!showSplitTarget || DropZones.LEFT !in splitZones) dragController.splitLeftBounds = Rect.Zero
        if (!showSplitTarget || DropZones.RIGHT !in splitZones) dragController.splitRightBounds = Rect.Zero
        if (!showSplitTarget || DropZones.THREE !in splitZones) dragController.splitThreeBounds = Rect.Zero
        if (!showSplitTarget || splitZones.isNotEmpty()) dragController.splitBounds = Rect.Zero
    }
    // Bounds of folder chips that aren't shown must never catch a drop.
    LaunchedEffect(showFolderChips) {
        if (!showFolderChips) dragController.drawerTabBounds = emptyMap()
    }
    // Uninstall and App info for any app being dragged (home, dock or drawer), as on Pixel;
    // Uninstall only for an app that can be uninstalled.
    val draggedApp = dragController.draggedApp
    val showInfoTarget = dragController.moving && draggedApp != null && !dragController.localDragging
    val canUninstallDragged = remember(draggedApp?.key) {
        draggedApp?.let { AppActions.canUninstall(context, it) } ?: false
    }
    val showUninstallTarget = showInfoTarget && canUninstallDragged
    LaunchedEffect(showInfoTarget, showUninstallTarget) {
        if (!showInfoTarget) dragController.infoBounds = Rect.Zero
        if (!showUninstallTarget) dragController.uninstallBounds = Rect.Zero
    }
    if (showRemoveTarget || showSplitTarget || showFolderChips || showInfoTarget) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                // Clear the themed status bar (when shown) so the pills don't overlap it.
                .padding(top = if (settings.statusBar.enabled) 44.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Derived, so a drag recomposes this row only when the finger enters or leaves a target
            // (reading the position here re-ran the whole home screen on every pointer move).
            val overRemove by remember(dragController) {
                derivedStateOf { dragController.isOverRemove(dragController.rootPosition) }
            }
            val overSplit by remember(dragController) {
                derivedStateOf { dragController.isOverSplit(dragController.rootPosition) }
            }
            val overZone by remember(dragController) {
                derivedStateOf { dragController.splitSideAt(dragController.rootPosition) }
            }
            // A tick on entering a drop target (Settings ▸ Recents ▸ Split screen ▸ Drop zones).
            val zoneHaptics = LocalHapticFeedback.current
            val overFolder by remember(dragController) {
                derivedStateOf { dragController.isOverFolderTab(dragController.rootPosition) }
            }
            LaunchedEffect(overZone, overSplit, overRemove, overFolder) {
                if (zones.haptics && (overZone != null || overSplit || overRemove || overFolder)) {
                    zoneHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
            if (showRemoveTarget) {
                DragTargetPill(
                    icon = LauncherIcons.Close,
                    label = stringResource(R.string.drag_remove),
                    active = overRemove,
                    activeColor = Color(0xFFD32F2F),
                    modifier = Modifier.onGloballyPositioned { dragController.removeBounds = it.boundsInRoot() },
                )
            }
            if (showUninstallTarget) {
                val overUninstall by remember(dragController) {
                    derivedStateOf { dragController.isOverUninstall(dragController.rootPosition) }
                }
                DragTargetPill(
                    icon = LauncherIcons.Delete,
                    label = stringResource(R.string.drag_uninstall),
                    active = overUninstall,
                    activeColor = Color(0xFFD32F2F),
                    modifier = Modifier.onGloballyPositioned { dragController.uninstallBounds = it.boundsInRoot() },
                )
            }
            if (showInfoTarget) {
                val overInfo by remember(dragController) {
                    derivedStateOf { dragController.isOverInfo(dragController.rootPosition) }
                }
                DragTargetPill(
                    icon = LauncherIcons.Info,
                    label = stringResource(R.string.drag_app_info),
                    active = overInfo,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.onGloballyPositioned { dragController.infoBounds = it.boundsInRoot() },
                )
            }
            if (showSplitTarget && splitZones.isEmpty()) {
                DragTargetPill(
                    icon = LauncherIcons.SwapHoriz,
                    label = stringResource(R.string.drag_split),
                    active = overSplit,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.onGloballyPositioned { dragController.splitBounds = it.boundsInRoot() },
                )
            }
            if (showSplitTarget) {
                splitZones.forEach { zone ->
                    DragTargetPill(
                        icon = LauncherIcons.SwapHoriz,
                        label = stringResource(
                            when (zone) {
                                DropZones.LEFT -> R.string.drag_split_left
                                DropZones.RIGHT -> R.string.drag_split_right
                                else -> R.string.drag_split_three
                            },
                        ),
                        active = overZone == zone,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.onGloballyPositioned { c ->
                            val r = c.boundsInRoot()
                            when (zone) {
                                DropZones.LEFT -> dragController.splitLeftBounds = r
                                DropZones.RIGHT -> dragController.splitRightBounds = r
                                else -> dragController.splitThreeBounds = r
                            }
                        },
                    )
                }
            }
            // Folder chips (dragging out of the drawer, which has folder tabs): one per folder —
            // dropping on it moves the app in — plus an All chip when the drag came out of a
            // folder, which pulls the app out to All. Lights up under the finger like the pills.
            if (showFolderChips) {
                val hoverTab = dragController.hoverFolderTab
                // The All chip first (a pull-out is the more common "put it back"), then the folders.
                if (dragController.dragFromTab != null) {
                    DragTargetPill(
                        icon = LauncherIcons.Folder,
                        label = stringResource(R.string.drag_folder_all),
                        active = hoverTab == null && dragController.isOverFolderTab(dragController.rootPosition),
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.onGloballyPositioned { c ->
                            dragController.drawerTabBounds =
                                dragController.drawerTabBounds + (null to c.boundsInRoot())
                        },
                    )
                }
                dragController.folderTargets.forEach { (id, name) ->
                    if (id == dragController.dragFromTab) return@forEach
                    DragTargetPill(
                        icon = LauncherIcons.Folder,
                        label = name,
                        active = hoverTab == id,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.onGloballyPositioned { c ->
                            dragController.drawerTabBounds =
                                dragController.drawerTabBounds + (id to c.boundsInRoot())
                        },
                    )
                }
            }
        }
    }
}
