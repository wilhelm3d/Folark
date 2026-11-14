package org.arkikeskus.launcher.feature.appdrawer

import org.arkikeskus.launcher.ui.expressive.LocalAccent
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.PickerOpen
import org.arkikeskus.launcher.model.PickerRecents
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.ui.component.AppIcon
import kotlin.math.roundToInt

// The pieces of the split picker: header, Peek and swap buttons, share buttons, the rows of
// apps and recent apps, search, tiles and folders (split out of SplitPickerDrawer.kt).

/** The picker's long-press menu: what it offers, and where the choice goes. */
internal class TileMenu(val isFavorite: (AppItem) -> Boolean, val onAction: (AppItem, SplitPickAction) -> Unit)

/** The picker's accent: the same as the app drawer's (Folark's accent unless the drawer sets its own). */
internal val ACCENT: Color
    @Composable get() = LocalAccent.current

/** "Choose an app" / "Choose 2 apps · 1 chosen", with the Peek switch when there is one. */
@Composable
internal fun Header(text: String, peek: Boolean?, onPeekChange: (Boolean) -> Unit, corner: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        corner()
        if (peek != null) {
            Spacer(Modifier.width(6.dp))
            PeekButton(peek) { onPeekChange(!peek) }
        }
    }
}

/**
 * Peek view as a symbol in the size buttons' style: a big pane with a thin strip peeking in at
 * its edge, lit when on, "Peek" beneath.
 */
@Composable
internal fun PeekButton(on: Boolean, onClick: () -> Unit) {
    val border by androidx.compose.animation.animateColorAsState(
        if (on) ACCENT else Color.White.copy(alpha = 0.18f), label = "peekBorder",
    )
    val label = stringResource(R.string.split_picker_peek)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.5.dp, border, RoundedCornerShape(6.dp))
                .padding(2.5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier
                    .weight(0.12f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (on) ACCENT else Color.White.copy(alpha = 0.45f)),
            )
            Box(
                Modifier
                    .weight(0.88f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        }
        Text(
            label,
            color = if (on) ACCENT else Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * "Swap" as a symbol in the size buttons' style: two panes with the bigger one changing sides,
 * lit softly, with its label beneath. Tapping it puts the app being chosen in the other half.
 */
@Composable
internal fun SwapButton(onClick: () -> Unit) {
    val label = stringResource(R.string.split_picker_swap)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(2.5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier.weight(0.34f).fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp)).background(Color.White.copy(alpha = 0.16f)),
            )
            Box(
                Modifier.weight(0.66f).fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp)).background(Color.White.copy(alpha = 0.55f)),
            )
        }
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** [SplitPickerDrawer]'s share for three apps a third each (two more apps to pick). */
const val SHARE_THREE = 0

/**
 * The ways to share the screen, in the header's corner: 67/33, 50/50 and 33/67 (the sizes
 * Motorola's split screen lays apps out at), read left to
 * right as on screen, and with [threeEven] 33/33/33 (three apps); each a tiny preview with its
 * label, the chosen one lit. [share] is the picked app's part ([SHARE_THREE] for three apps).
 */
@Composable
internal fun ShareButtons(
    share: Int,
    pickedOnLeft: Boolean,
    peekOn: Boolean,
    onShareChange: (Int) -> Unit,
    sizes: Boolean = true,
    threeEven: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        (if (sizes) listOf(67, 50, 33) else emptyList()).forEach { left ->
            // The picked app's part for this layout.
            val option = if (pickedOnLeft) left else 100 - left
            val selected = option == share && !peekOn
            val border by androidx.compose.animation.animateColorAsState(
                if (selected) ACCENT else Color.White.copy(alpha = 0.18f), label = "shareBorder",
            )
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShareChange(option) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .size(width = 38.dp, height = 22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.5.dp, border, RoundedCornerShape(6.dp))
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(true to left, false to 100 - left).forEach { (isLeft, part) ->
                        val isPick = isLeft == pickedOnLeft
                        Box(
                            Modifier
                                .weight(part / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(if (isPick) (if (selected) ACCENT else Color.White.copy(alpha = 0.45f)) else Color.White.copy(alpha = 0.16f)),
                        )
                    }
                }
                Text(
                    "$left/${100 - left}",
                    color = if (selected) ACCENT else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (threeEven) {
            // Three apps a third each: the first app's third stays dim, the two to pick are lit.
            val selected = share == SHARE_THREE
            val border by androidx.compose.animation.animateColorAsState(
                if (selected) ACCENT else Color.White.copy(alpha = 0.18f), label = "threeBorder",
            )
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShareChange(if (selected) 50 else SHARE_THREE) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .size(width = 44.dp, height = 22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.5.dp, border, RoundedCornerShape(6.dp))
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(3) { i ->
                        val isFirst = (i == 2) == pickedOnLeft
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    if (isFirst) Color.White.copy(alpha = 0.16f)
                                    else if (selected) ACCENT else Color.White.copy(alpha = 0.45f),
                                ),
                        )
                    }
                }
                Text(
                    "33/33/33",
                    color = if (selected) ACCENT else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** A titled row of apps (favorites, paired before), like the recent apps row. */
@Composable
internal fun AppRow(
    title: String,
    apps: List<AppItem>,
    labels: Boolean,
    menu: TileMenu?,
    badge: (AppItem) -> Int?,
    onPick: (AppItem, Rect?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(apps, key = { "${title}_${it.key}" }) { app ->
                AppTile(app, 42.dp, labels, menu, badge(app)) { onPick(app, it) }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White.copy(alpha = 0.10f),
        )
    }
}

@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.app_drawer_search_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_clear),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp).clickable { onQuery("") },
            )
        }
    }
}

@Composable
internal fun RecentsRow(items: List<SplitRecentItem>, previews: Boolean, labels: Boolean, onPick: (AppItem, Rect?) -> Unit) {
    val density = LocalDensity.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.split_picker_recent),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (previews) 10.dp else 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items, key = { "rec_${it.taskId}" }) { item ->
                val thumb = item.thumbnail
                var bounds by remember { mutableStateOf<Rect?>(null) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(item.app, bounds) }
                        .padding(4.dp),
                ) {
                    if (previews && thumb != null) {
                        Box {
                            Image(
                                bitmap = thumb,
                                contentDescription = item.app.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(104.dp)
                                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                                AppIcon(item.app, labelColor = Color.White, iconSize = 22.dp, showLabel = false)
                            }
                        }
                        if (labels) {
                            Text(
                                item.app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.width(72.dp).padding(top = 3.dp),
                            )
                        }
                    } else {
                        Box(Modifier.onGloballyPositioned { bounds = iconRect(it.boundsInRoot(), 42.dp, density) }) {
                            AppIcon(item.app, labelColor = Color.White, iconSize = 42.dp, showLabel = labels, maxLabelLines = 1)
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White.copy(alpha = 0.10f),
        )
    }
}

/**
 * One app: tap to pick (with where its icon is, for the zoom), long-press for [menu]. [badge]: its
 * number among the apps chosen for a three-app split.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppTile(app: AppItem, iconSize: Dp, labels: Boolean, menu: TileMenu?, badge: Int?, onClick: (Rect?) -> Unit) {
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onClick(bounds) },
                onLongClick = if (menu != null) ({ expanded = true }) else null,
            )
            .padding(2.dp)
            .onGloballyPositioned { bounds = iconRect(it.boundsInRoot(), iconSize, density) },
        contentAlignment = Alignment.TopCenter,
    ) {
        AppIcon(app, labelColor = Color.White, iconSize = iconSize, showLabel = labels, maxLabelLines = 1)
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(ACCENT),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        if (menu != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.split_picker_open_popup)) },
                    onClick = { expanded = false; menu.onAction(app, SplitPickAction.POPUP) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.split_picker_open_full)) },
                    onClick = { expanded = false; menu.onAction(app, SplitPickAction.FULL_SCREEN) },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (menu.isFavorite(app)) R.string.split_picker_unfavorite else R.string.split_picker_favorite))
                    },
                    onClick = { expanded = false; menu.onAction(app, SplitPickAction.FAVORITE) },
                )
            }
        }
    }
}

/** Where the icon sits in a tile laid out at [tile] (the icon at the top, centred, label below). */
internal fun iconRect(tile: Rect, icon: Dp, density: androidx.compose.ui.unit.Density): Rect {
    val s = with(density) { icon.toPx() }.coerceAtMost(tile.width)
    val left = tile.center.x - s / 2f
    return Rect(left, tile.top, left + s, tile.top + s)
}

@Composable
internal fun FolderTile(folder: DrawerFolderUi, iconSize: Dp, labels: Boolean, onClick: () -> Unit) {
    val mini = (iconSize - 14.dp) / 2
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.3f)).background(Color.White.copy(alpha = 0.10f)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                folder.apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pair.forEach { a -> AppIcon(a, labelColor = Color.White, iconSize = mini, showLabel = false) }
                    }
                }
            }
        }
        if (labels) {
            Text(
                folder.name,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
