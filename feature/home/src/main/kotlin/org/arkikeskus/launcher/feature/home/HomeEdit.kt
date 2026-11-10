package org.arkikeskus.launcher.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.component.AppIcon

/**
 * Home's edit mode and "show me where it went", shared by the grid's cells (LocalHomeEdit):
 *  - [editing]: icons show a minus to take them off home and a tap selects them; the bar at the
 *    bottom works on the selection (remove, folder, move to a page) or adds apps;
 *  - [revealKey]/[revealAt]: the app that was just added pops in where it landed.
 */
@Stable
class HomeEditState {
    var editing by mutableStateOf(false)
        private set
    val selected = mutableStateListOf<String>()

    var revealKey by mutableStateOf<String?>(null)
        private set
    var revealAt by mutableLongStateOf(0L)
        private set

    fun start() {
        selected.clear()
        editing = true
    }

    fun stop() {
        selected.clear()
        editing = false
    }

    fun toggle(key: String) {
        if (!selected.remove(key)) selected.add(key)
    }

    fun reveal(key: String) {
        revealKey = key
        revealAt = System.nanoTime()
    }
}

val LocalHomeEdit = staticCompositionLocalOf<HomeEditState?> { null }

/** The edit-mode marks on an app cell: a minus that takes it off home (top start), a tick when selected. */
@Composable
internal fun EditMarks(selected: Boolean, iconSize: androidx.compose.ui.unit.Dp, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(iconSize + 12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(EDIT_BADGE_TOUCH)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(
                modifier = Modifier.size(22.dp).background(Color(0xE6202124), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(LauncherIcons.Remove),
                    contentDescription = stringResource(R.string.edit_home_remove),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (selected) {
            Icon(
                painterResource(LauncherIcons.CheckCircle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(Color.White, CircleShape),
            )
        }
    }
}

/** How big the minus is to the finger (its area at the cell's top-start corner). */
internal val EDIT_BADGE_TOUCH = 36.dp

/**
 * The bar at the bottom in edit mode: what is selected and what can be done with it, Add apps,
 * and Done. Above the dock, so the dock stays reachable.
 */
@Composable
internal fun EditHomeBar(
    edit: HomeEditState,
    onRemove: () -> Unit,
    onFolder: () -> Unit,
    onMove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = edit.editing,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier.navigationBarsPadding().padding(bottom = 104.dp, start = 12.dp, end = 12.dp),
    ) {
        val count = edit.selected.size
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF0202124), shadowElevation = 8.dp, modifier = Modifier.widthIn(max = 640.dp)) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    if (count == 0) stringResource(R.string.edit_home_hint) else pluralStringResource(R.plurals.edit_home_selected, count, count),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    if (count > 0) {
                        EditBarButton(LauncherIcons.Close, stringResource(R.string.edit_home_remove), onRemove)
                        if (count >= 2) EditBarButton(LauncherIcons.Folder, stringResource(R.string.edit_home_folder), onFolder)
                        EditBarButton(LauncherIcons.SwapHoriz, stringResource(R.string.edit_home_move), onMove)
                    } else {
                        EditBarButton(LauncherIcons.Add, stringResource(R.string.edit_home_add), onAdd)
                    }
                    EditBarButton(LauncherIcons.DoneAll, stringResource(R.string.edit_home_done)) { edit.stop() }
                }
            }
        }
    }
}

@Composable
private fun EditBarButton(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** "Move to page": the pages there are, and one new page after them. */
@Composable
internal fun MoveToPageDialog(pageCount: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_home_move_title)) },
        text = {
            Column {
                (0 until pageCount).forEach { page ->
                    Text(
                        stringResource(R.string.edit_home_page, page + 1),
                        modifier = Modifier.fillMaxWidth().clickable { onPick(page) }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    stringResource(R.string.edit_home_new_page),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(pageCount) }.padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_home_cancel)) } },
    )
}

/** "Add apps": every app not on home yet, with a search and ticks; Add puts the ticked ones on home. */
@Composable
internal fun AddAppsDialog(apps: List<AppItem>, onAdd: (List<AppItem>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_home_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_home_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        val ticked = app.key in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (ticked) chosen.remove(app.key) else chosen.add(app.key) }
                                .padding(vertical = 6.dp),
                        ) {
                            AppIcon(appItem = app, labelColor = Color.Unspecified, showLabel = false, iconSize = 36.dp)
                            Text(app.label, modifier = Modifier.weight(1f).padding(start = 14.dp))
                            Checkbox(checked = ticked, onCheckedChange = { if (it) chosen.add(app.key) else chosen.remove(app.key) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onAdd(apps.filter { it.key in chosen }) }) {
                Text(pluralStringResource(R.plurals.edit_home_add_count, chosen.size.coerceAtLeast(1), chosen.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_home_cancel)) } },
    )
}
