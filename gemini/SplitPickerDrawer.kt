package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon

/**
 * The compact app picker shown in the empty pane during a split drop (approach B). A five-column
 * scrollable grid: the first row is the drawer's folders (tap one to pick from inside it), then
 * every app that is NOT already in a folder — so apps in folders aren't listed twice. Tapping an
 * app calls [onPick]; the host then launches it into the empty split pane.
 */
@Composable
fun SplitPickerDrawer(
    apps: List<AppItem>,
    folders: List<DrawerFolderUi>,
    onPick: (AppItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    // Every app key that lives in a folder — those are kept out of the loose list below.
    val inFolders = remember(folders) { folders.flatMap { f -> f.apps.map { it.key } }.toHashSet() }
    val loose = remember(apps, inFolders) {
        apps.filter { !it.isHidden && it.key !in inFolders }.sortedBy { it.label.lowercase() }
    }
    val realFolders = remember(folders) { folders.filter { it.apps.isNotEmpty() } }

    var open by remember { mutableStateOf<Long?>(null) }
    val current = realFolders.firstOrNull { it.id == open }

    Column(modifier.fillMaxSize().background(Color(0xF20A0E12))) {
        if (current != null) {
            // Inside a folder: a back row, then its apps.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { open = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(current.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(current.apps, key = { it.key }) { app -> AppTile(app) { onPick(app) } }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // First row(s): the folders only.
                items(realFolders, key = { "f${it.id}" }) { folder -> FolderTile(folder) { open = folder.id } }
                // Pad to the row boundary so the loose apps always start on a fresh row.
                val pad = (columns - realFolders.size % columns) % columns
                if (pad > 0) item(span = { GridItemSpan(pad) }) { Box(Modifier) }
                // Every app that isn't in a folder.
                items(loose, key = { it.key }) { app -> AppTile(app) { onPick(app) } }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppItem, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick).padding(2.dp), contentAlignment = Alignment.TopCenter) {
        AppIcon(app, labelColor = Color.White, iconSize = 46.dp, showLabel = true, maxLabelLines = 1)
    }
}

/** A folder as a rounded tile with a 2×2 preview of its first apps and the folder's name below. */
@Composable
private fun FolderTile(folder: DrawerFolderUi, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                folder.apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pair.forEach { a -> AppIcon(a, labelColor = Color.White, iconSize = 16.dp, showLabel = false) }
                    }
                }
            }
        }
        Text(
            folder.name,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
