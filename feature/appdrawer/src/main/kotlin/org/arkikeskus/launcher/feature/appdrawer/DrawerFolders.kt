package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.arkikeskus.launcher.ui.component.CommitWhenIdle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.aquamorphicTouch
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// Drawer folders and tabs: the tile, the opened sheet, adding apps (split out of AppDrawerScreen.kt).

/** A drawer folder tile: a rounded 2×2 preview of its first apps + label; tap (or long-press, which
 *  also ticks the haptic like an app long-press) opens the folder. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerFolderTile(
    folder: DrawerFolderUi,
    showLabel: Boolean,
    onClick: () -> Unit,
    tileSize: Dp = 56.dp,
    badgeCount: Int = 0,
    badgeShowCount: Boolean = true,
    badgeScale: Float = 1f,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

/** The open-folder sheet: rename, member apps (tap launches, long-press removes), add apps, delete. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DrawerFolderSheet(
    folder: DrawerFolderUi,
    candidateApps: List<AppItem>,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    onRename: (String) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onRemoveApp: (AppItem) -> Unit,
    onAddApps: (List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            var name by remember(folder.id) { mutableStateOf(folder.name) }
            CommitWhenIdle(folder.id, name, commit = onRename)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (folder.apps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.folder_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    items(items = folder.apps, key = { it.key }, contentType = { "app" }) { app ->
                        AppIcon(
                            appItem = app,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            showLabel = true,
                            badgeCount = badges[app.badgeKey] ?: 0,
                            badgeShowCount = badgeShowCount,
                            badgeScale = badgeScale,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onRemoveApp(app) },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showAdd = true }) { Text(stringResource(R.string.folder_add_apps)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.folder_delete)) }
            }
        }
    }
    if (showAdd) {
        AddAppsDialog(
            apps = candidateApps,
            onConfirm = {
                onAddApps(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/** Multi-select picker of apps to add to a drawer folder. */
@Composable
private fun AddAppsDialog(apps: List<AppItem>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_add_apps)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                apps.forEach { app ->
                    val checked = app.key in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(app.key) else selected.add(app.key) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { if (it) selected.add(app.key) else selected.remove(app.key) },
                        )
                        AppIcon(appItem = app, labelColor = MaterialTheme.colorScheme.onSurface, showLabel = false, iconSize = 32.dp)
                        Text(
                            text = app.label,
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text(stringResource(R.string.folder_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_cancel)) } },
    )
}

/** The drawer's folders as a row of tabs: "All" plus one per folder; the open one is filled. */
@Composable
internal fun DrawerTabRow(tabs: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val all: List<Pair<Long?, String>> =
            listOf<Pair<Long?, String>>(null to stringResource(R.string.drawer_tab_all)) + tabs
        all.forEach { (id, name) ->
            val isSelected = id == selected
            Text(
                text = name,
                color = if (isSelected) Color.White else palette.text,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(if (isSelected) accent else palette.btn, RoundedCornerShape(50))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
