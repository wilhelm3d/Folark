package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.aquamorphicTouch
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalTaskbarInset
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// Drawer folders and tabs: the tile, the opened sheet, adding apps (split out of AppDrawerScreen.kt).

/**
 * A drawer folder tile: a rounded 2×2 preview of its first apps + label. Tap opens the folder (and
 * so does the end of a long press, which ticks the haptic like an app long-press); while another
 * app is being dragged inside the drawer, the tile lights up as the place to drop it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerFolderTile(
    folder: DrawerFolderUi,
    showLabel: Boolean,
    onClick: () -> Unit,
    drag: DrawerFolderDrag,
    tileSize: Dp = 56.dp,
    badgeCount: Int = 0,
    badgeShowCount: Boolean = true,
    badgeScale: Float = 1f,
) {
    val haptics = LocalHapticFeedback.current
    val dropping = drag.overFolder == folder.id
    val dragActive = drag.dragged != null
    // A folder tile is a label and a 2×2 preview; a screen reader should hear what it is.
    val description = stringResource(R.string.folder_tile_apps, folder.name, folder.apps.size)
    // A tile that scrolls away must stop being a drop target (the bounds it left behind could
    // otherwise catch a drop in the place it used to cover).
    androidx.compose.runtime.DisposableEffect(folder.id) {
        onDispose { drag.folderBounds.remove(folder.id) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { drag.folderBounds[folder.id] = it.boundsInRoot() }
            .graphicsLayer {
                val s = if (dropping) 1.1f else 1f
                scaleX = s
                scaleY = s
            }
            .border(
                width = if (dropping) 2.dp else 1.dp,
                color = when {
                    dropping -> LocalAccent.current
                    dragActive -> LocalAccent.current.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(22.dp),
            )
            .semantics { contentDescription = description }
            .aquamorphicTouch()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // EVERYTHING inside scales with the tile — padding and gaps included. A fixed padding/gap
        // with linearly scaled icons inverted the margin below ~45dp tiles (6–7 drawer columns on a
        // narrow screen), squeezing the 2×2 preview asymmetrically out of its card.
        val scale = tileSize / 56.dp
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(7.dp * scale),
                contentAlignment = Alignment.Center,
            ) {
                val mini = 18.dp * scale
                val gap = 2.dp * scale
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        FolderSlot(folder.apps.getOrNull(0), mini); FolderSlot(folder.apps.getOrNull(1), mini)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        FolderSlot(folder.apps.getOrNull(2), mini); FolderSlot(folder.apps.getOrNull(3), mini)
                    }
                }
            }
            NotificationBadge(count = badgeCount, showCount = badgeShowCount, scale = badgeScale)
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = folder.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = LocalAppLabelLines.current,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FolderSlot(app: AppItem?, size: Dp) {
    if (app == null) {
        Spacer(Modifier.size(size))
    } else {
        AppIcon(appItem = app, labelColor = Color.Transparent, showLabel = false, iconSize = size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerTabRow(
    tabs: List<Pair<Long, String>>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    drag: DrawerFolderDrag,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    /** Moves a tab from one place to another (the stored folder order is the tab order). */
    onMove: (Int, Int) -> Unit = { _, _ -> },
    /** Settings ▸ App drawer ▸ Lock the tab order: a chip can no longer be picked up. */
    reorderable: Boolean = true,
) {
    var renameId by remember { mutableStateOf<Long?>(null) }
    // The chip held down after a long press: it follows the finger, and the tab moves on release.
    // Released without a slide, the same long press renames it, which is where renaming was.
    var liftedId by remember { mutableStateOf<Long?>(null) }
    var liftOffset by remember { mutableStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val all: List<Pair<Long?, String>> =
            listOf<Pair<Long?, String>>(null to stringResource(R.string.drawer_tab_all)) + tabs
        all.forEach { (id, name) ->
            val isSelected = id == selected
            // An app dragged inside the drawer can be dropped straight onto a tab.
            val dropping = id != null && drag.overFolder == id
            DisposableEffect(id) {
                onDispose {
                    if (id != null) {
                        drag.folderBounds.remove(id)
                        drag.tabBounds.remove(id)
                    }
                }
            }
            val fill by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    isSelected -> accent
                    dropping   -> accent.copy(alpha = 0.22f)
                    else       -> palette.btn
                },
                animationSpec = androidx.compose.animation.core.tween(180),
                label = "tabFill",
            )
            Text(
                text = name,
                color = when {
                    isSelected -> Color.White
                    dropping   -> accent
                    else       -> palette.text
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .onGloballyPositioned { position ->
                        if (id != null) {
                            val rect = position.boundsInRoot()
                            drag.folderBounds[id] = rect
                            drag.tabBounds[id] = rect
                        }
                    }
                    .zIndex(if (id != null && liftedId == id) 1f else 0f)
                    .graphicsLayer {
                        val s = when {
                            dropping -> 1.13f
                            id != null && liftedId == id -> 1.06f
                            else -> 1f
                        }
                        scaleX = s
                        scaleY = s
                        if (id != null && liftedId == id) translationX = liftOffset
                    }
                    .background(fill, RoundedCornerShape(50))
                    .border(
                        width = when {
                            dropping -> 2.dp
                            drag.dragged != null && id != null -> 1.dp
                            else -> 0.dp
                        },
                        color = if (dropping) accent else accent.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelect(id) }
                    // Long press picks the chip up; sliding it over another one moves the tab there.
                    // Without a slide the same long press renames it (no trip through the card).
                    .pointerInput(id, reorderable, tabs) {
                        if (id == null || !reorderable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                liftedId = id
                                liftOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                liftOffset += amount.x
                            },
                            onDragEnd = {
                                val from = tabs.indexOfFirst { it.first == id }
                                val to = tabDropTarget(tabs, from, id, drag.tabBounds, liftOffset, horizontal = true)
                                val moved = kotlin.math.abs(liftOffset) > viewConfiguration.touchSlop
                                liftedId = null
                                liftOffset = 0f
                                when {
                                    !moved -> renameId = id
                                    from >= 0 && to != from -> onMove(from, to)
                                }
                            },
                            onDragCancel = {
                                liftedId = null
                                liftOffset = 0f
                            },
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    val renaming = renameId
    if (renaming != null) {
        FolderNameDialog(
            initial = tabs.firstOrNull { it.first == renaming }?.second.orEmpty(),
            onConfirm = { newName ->
                renameId = null
                onRename(renaming, newName)
            },
            onDismiss = { renameId = null },
        )
    }
}

/** Width of the two-pane drawer's tab list (inner screen). */
private val TAB_RAIL_WIDTH = 168.dp

/**
 * The two-pane drawer's left column (Settings ▸ App drawer ▸ Two-pane, unfolded): "All" and every
 * tab as a vertical list; the chosen one is filled with the accent colour, which slides softly
 * between them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerTabRail(
    tabs: List<Pair<Long, String>>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    drag: DrawerFolderDrag,
    onRename: (Long, String) -> Unit,
    onNewFolder: (() -> Unit)? = null,
    /** Moves a tab from one place to another (the stored folder order is the tab order). */
    onMove: (Int, Int) -> Unit = { _, _ -> },
    /** Settings ▸ App drawer ▸ Lock the tab order: a chip can no longer be picked up. */
    reorderable: Boolean = true,
) {
    var renameId by remember { mutableStateOf<Long?>(null) }
    var liftedId by remember { mutableStateOf<Long?>(null) }
    var liftOffset by remember { mutableStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    Column(
        modifier = Modifier
            .width(TAB_RAIL_WIDTH)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val all: List<Pair<Long?, String>> =
            listOf<Pair<Long?, String>>(null to stringResource(R.string.drawer_tab_all)) + tabs
        all.forEach { (id, name) ->
            val isSelected = id == selected
            val dropping = id != null && drag.overFolder == id
            DisposableEffect(id) {
                onDispose {
                    if (id != null) {
                        drag.folderBounds.remove(id)
                        drag.tabBounds.remove(id)
                    }
                }
            }
            val fill by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    isSelected -> accent
                    dropping   -> accent.copy(alpha = 0.22f)
                    else       -> Color.Transparent
                },
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "tabRailFill",
            )
            Text(
                text = name,
                color = when {
                    isSelected -> Color.White
                    dropping   -> accent
                    else       -> palette.text
                },
                fontSize = 15.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { position ->
                        if (id != null) {
                            val rect = position.boundsInRoot()
                            drag.folderBounds[id] = rect
                            drag.tabBounds[id] = rect
                        }
                    }
                    .zIndex(if (id != null && liftedId == id) 1f else 0f)
                    .graphicsLayer {
                        val s = when {
                            dropping -> 1.08f
                            id != null && liftedId == id -> 1.03f
                            else -> 1f
                        }
                        scaleX = s
                        scaleY = s
                        if (id != null && liftedId == id) translationY = liftOffset
                    }
                    .background(fill, RoundedCornerShape(16.dp))
                    .border(
                        width = when {
                            dropping -> 2.dp
                            drag.dragged != null && id != null -> 1.dp
                            else -> 0.dp
                        },
                        color = if (dropping) accent else accent.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(id) }
                    .pointerInput(id, reorderable, tabs) {
                        if (id == null || !reorderable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                liftedId = id
                                liftOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                liftOffset += amount.y
                            },
                            onDragEnd = {
                                val from = tabs.indexOfFirst { it.first == id }
                                val to = tabDropTarget(tabs, from, id, drag.tabBounds, liftOffset, horizontal = false)
                                val moved = kotlin.math.abs(liftOffset) > viewConfiguration.touchSlop
                                liftedId = null
                                liftOffset = 0f
                                when {
                                    !moved -> renameId = id
                                    from >= 0 && to != from -> onMove(from, to)
                                }
                            },
                            onDragCancel = {
                                liftedId = null
                                liftOffset = 0f
                            },
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
        // "+ New folder" at the rail's end, styled like a tab: creation lives where the tabs are.
        if (onNewFolder != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewFolder)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.folder_new),
                    tint = palette.text,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.folder_new),
                    color = palette.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        val renaming = renameId
        if (renaming != null) {
            FolderNameDialog(
                initial = tabs.firstOrNull { it.first == renaming }?.second.orEmpty(),
                onConfirm = { newName ->
                    renameId = null
                    onRename(renaming, newName)
                },
                onDismiss = { renameId = null },
            )
        }
    }
}

/**
 * Where a lifted tab chip lands: the number of other chips whose centre lies before the dragged
 * chip's centre along [horizontal] (or vertically, in the two-pane rail). That count is the index
 * the chip takes once it is lifted out of the list — what the stored order move expects.
 */
internal fun tabDropTarget(
    tabs: List<Pair<Long, String>>,
    from: Int,
    id: Long,
    bounds: Map<Long, Rect>,
    offset: Float,
    horizontal: Boolean,
): Int {
    if (from !in tabs.indices) return from
    val own = bounds[id] ?: return from
    val axis = (if (horizontal) own.center.x else own.center.y) + offset
    val others = tabs.mapNotNull { (otherId, _) -> if (otherId == id) null else otherId }
    val landed = others.count { otherId ->
        val rect = bounds[otherId] ?: return@count false
        (if (horizontal) rect.center.x else rect.center.y) < axis
    }
    return landed.coerceIn(0, others.size)
}

/** Renames a tab/folder in place — the same affordance as tapping the title inside the card. */
@Composable
private fun FolderNameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.folder_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_cancel))
            }
        },
    )
}

/**
 * "Add to folder" from an app's long-press menu: files [app] into one of the drawer's folders, or
 * makes a new folder holding just it. Dragging onto a tile or a tab is quicker, but organizing apps
 * should not require hitting a 56 dp target while dragging.
 */
@Composable
internal fun AddToFolderDialog(
    app: AppItem,
    folders: List<DrawerFolderUi>,
    onPick: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inFolderLabel = stringResource(R.string.folder_add_to_current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_add_to)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_add_to_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                folders.forEach { folder ->
                    // A check marks the folder the app is already in, so the list reads as a state.
                    val already = folder.apps.any { it.key == app.key }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(folder.id)
                                onDismiss()
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = folder.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (already) {
                            // A tick reads as "this one already has it" without a second sentence.
                            Text(
                                text = "✓",
                                color = LocalAccent.current,
                                fontSize = 15.sp,
                                modifier = Modifier.semantics { contentDescription = inFolderLabel },
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.folder_add_to_count, folder.apps.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate()
                onDismiss()
            }) {
                Text(stringResource(R.string.folder_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) }
        },
    )
}

/** Settings cog for a folder viewed as a tab: opens the folder editor, or deletes with confirm. */
@Composable
internal fun TabFolderSettingsMenu(
    folder: DrawerFolderUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.folder_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_editor_edit)) },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                // Deleting a folder is not undoable, so it asks first (the card does the same).
                onClick = { menuOpen = false; confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.folder_delete)) },
            text = { Text(stringResource(R.string.folder_delete_confirm, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.folder_cancel))
                }
            },
        )
    }
}
