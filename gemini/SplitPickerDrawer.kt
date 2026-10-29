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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon

data class SplitRecentItem(
    val taskId: Int,
    val app: AppItem,
    val inactiveMs: Long? = null,
    val thumbnail: ImageBitmap? = null,
)

@Composable
fun SplitPickerDrawer(
    apps: List<AppItem>,
    folders: List<DrawerFolderUi>,
    onPick: (AppItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 5,
    recents: List<SplitRecentItem> = emptyList(),
    firstPackageToHide: String = "",
) {
    // Improvement #2: Filter out firstPackage and launcher package
    val shownApps = remember(apps, firstPackageToHide) {
        apps.filter { !it.isHidden && it.packageName != firstPackageToHide }.sortedBy { it.label.lowercase() }
    }
    val realFolders = remember(folders) { folders.filter { it.apps.isNotEmpty() } }

    var open by remember { mutableStateOf<Long?>(null) }
    val current = realFolders.firstOrNull { f -> f.id == open }

    Column(modifier.fillMaxSize().background(Color(0xF20A0E12))) {
        // Improvement #4: Centered Recent Apps Row at the top
        if (recents.isNotEmpty() && open == null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "RECENT APPS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(recents, key = { "rec_${it.taskId}" }) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(item.app) }
                                .padding(4.dp),
                        ) {
                            AppIcon(item.app, labelColor = Color.White, iconSize = 42.dp, showLabel = true, maxLabelLines = 1)
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.10f),
                )
            }
        }

        if (current != null) {
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
                items(realFolders, key = { "f${it.id}" }) { folder -> FolderTile(folder) { open = folder.id } }
                val pad = (columns - realFolders.size % columns) % columns
                if (pad > 0) item(span = { GridItemSpan(pad) }) { Box(Modifier) }
                // Improvement #4: All apps remain listed fully in the main grid below
                items(shownApps, key = { it.key }) { app -> AppTile(app) { onPick(app) } }
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
