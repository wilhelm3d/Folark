package org.arkikeskus.launcher.feature.appdrawer

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.ui.component.AppIcon
import kotlin.math.roundToInt

/** A recently used app for the picker's top row; [thumbnail] only with previews on (and root). */
data class SplitRecentItem(
    val taskId: Int,
    val app: AppItem,
    val inactiveMs: Long? = null,
    val thumbnail: ImageBitmap? = null,
)

/**
 * The split picker's drawer: optional search, optional row of recent apps, the drawer folders as
 * the first row (their apps not listed again), then every other app — all as set in [look].
 * [apps] come in the order to show them; the dragged app ([firstPackageToHide]) is left out.
 *
 * With the Zoom opening animation the tapped icon grows to fill the drawer first (the drawer sinks
 * back behind it) and [onPick] comes once it has; otherwise [onPick] comes at once.
 */
@Composable
fun SplitPickerDrawer(
    apps: List<AppItem>,
    folders: List<DrawerFolderUi>,
    onPick: (AppItem) -> Unit,
    modifier: Modifier = Modifier,
    look: SplitPickerLook = SplitPickerLook(),
    recents: List<SplitRecentItem> = emptyList(),
    firstPackageToHide: String = "",
) {
    val columns = look.columns.coerceIn(SplitPickerLook.MIN_COLUMNS, SplitPickerLook.MAX_COLUMNS)
    val iconSize = look.iconSize.coerceIn(SplitPickerLook.MIN_ICON, SplitPickerLook.MAX_ICON).dp
    val shown = remember(apps, firstPackageToHide) { apps.filter { it.packageName != firstPackageToHide } }
    val realFolders = remember(folders, firstPackageToHide, look.folders) {
        if (!look.folders) emptyList()
        else folders.map { f -> f.copy(apps = f.apps.filter { it.packageName != firstPackageToHide }) }
            .filter { it.apps.isNotEmpty() }
    }
    val loose = remember(shown, realFolders) {
        val inFolders = realFolders.flatMapTo(HashSet()) { f -> f.apps.map { it.key } }
        shown.filter { it.key !in inFolders }
    }

    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = realFolders.firstOrNull { it.id == open }
    val searching = look.search && query.isNotBlank()
    val results = remember(shown, query, searching) {
        if (!searching) emptyList() else shown.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    // Zoom: the app being opened, from where its icon was (in this drawer's coordinates).
    var opening by remember { mutableStateOf<Opening?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    val zoom = look.openAnim == SplitPickerLook.OPEN_ZOOM && ValueAnimator.areAnimatorsEnabled()
    val pick: (AppItem, Rect?) -> Unit = pick@{ app, from ->
        if (opening != null) return@pick
        if (zoom && from != null) opening = Opening(app, from.translate(-origin)) else onPick(app)
    }
    LaunchedEffect(opening) {
        val o = opening ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ZOOM_MS, easing = FastOutSlowInEasing))
        onPick(o.app)
    }

    BackHandler(enabled = opening == null && (current != null || query.isNotEmpty())) {
        if (current != null) open = null else query = ""
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .background(Color(0xFF0A0E12).copy(alpha = look.opacity)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The drawer sinks back while the tapped app grows over it.
                    val q = if (opening != null) progress.value else 0f
                    val s = 1f - 0.06f * q
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.7f * q
                },
        ) {
            if (look.search && current == null) {
                SearchField(query = query, onQuery = { query = it })
            }
            if (!searching && current == null && look.recents != SplitPickerLook.RECENTS_OFF && recents.isNotEmpty()) {
                RecentsRow(recents.take(look.recentsCount), previews = look.recents == SplitPickerLook.RECENTS_PREVIEWS, labels = look.labels, onPick = pick)
            }

            when {
                searching -> AppGrid(columns) {
                    items(results, key = { it.key }) { app -> AppTile(app, iconSize, look.labels) { pick(app, it) } }
                }
                current != null -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { open = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.split_picker_back), tint = Color.White)
                        }
                        Text(current.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    AppGrid(columns) {
                        items(current.apps, key = { it.key }) { app -> AppTile(app, iconSize, look.labels) { pick(app, it) } }
                    }
                }
                else -> AppGrid(columns) {
                    items(realFolders, key = { "f${it.id}" }) { folder -> FolderTile(folder, iconSize, look.labels) { open = folder.id } }
                    val pad = (columns - realFolders.size % columns) % columns
                    if (realFolders.isNotEmpty() && pad > 0) item(span = { GridItemSpan(pad) }) { Box(Modifier) }
                    items(loose, key = { it.key }) { app -> AppTile(app, iconSize, look.labels) { pick(app, it) } }
                }
            }
        }
        opening?.let { ZoomOpen(it, progress.value, iconSize) }
    }
}

private class Opening(val app: AppItem, val from: Rect)

private const val ZOOM_MS = 280
/** How big the icon ends up in the middle (about Android's splash screen icon). */
private val ZOOM_ICON_END = 88.dp
/** The panel's colour once it fills the drawer (dark, like most app start screens at night). */
private val ZOOM_PANEL = Color(0xFF1B2127)

/**
 * The tapped app growing out of its icon: a panel opens from the icon's place to the whole drawer,
 * its corners straightening, with the icon gliding to the middle and growing — so the app's own
 * start screen (its icon in the middle) carries straight on from it.
 */
@Composable
private fun ZoomOpen(opening: Opening, p: Float, iconSize: Dp) {
    val density = LocalDensity.current
    var full by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { c -> full = Rect(Offset.Zero, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())) }
            // Nothing below can be tapped or scrolled while the app opens.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        val end = full ?: return@Box
        val r = lerp(opening.from, end, p)
        val corner = lerpDp(iconSize * 0.3f, 0.dp, p)
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(r.width.toDp(), r.height.toDp())
                    .clip(RoundedCornerShape(corner))
                    .background(lerpColor(Color.White.copy(alpha = 0.16f), ZOOM_PANEL, (p * 1.6f).coerceAtMost(1f))),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(opening.app, labelColor = Color.White, iconSize = lerpDp(iconSize, ZOOM_ICON_END, p), showLabel = false)
            }
        }
    }
}

@Composable
private fun AppGrid(columns: Int, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
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
private fun RecentsRow(items: List<SplitRecentItem>, previews: Boolean, labels: Boolean, onPick: (AppItem, Rect?) -> Unit) {
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

@Composable
private fun AppTile(app: AppItem, iconSize: Dp, labels: Boolean, onClick: (Rect?) -> Unit) {
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(bounds) }
            .padding(2.dp)
            .onGloballyPositioned { bounds = iconRect(it.boundsInRoot(), iconSize, density) },
        contentAlignment = Alignment.TopCenter,
    ) {
        AppIcon(app, labelColor = Color.White, iconSize = iconSize, showLabel = labels, maxLabelLines = 1)
    }
}

/** Where the icon sits in a tile laid out at [tile] (the icon at the top, centred, label below). */
private fun iconRect(tile: Rect, icon: Dp, density: androidx.compose.ui.unit.Density): Rect {
    val s = with(density) { icon.toPx() }.coerceAtMost(tile.width)
    val left = tile.center.x - s / 2f
    return Rect(left, tile.top, left + s, tile.top + s)
}

@Composable
private fun FolderTile(folder: DrawerFolderUi, iconSize: Dp, labels: Boolean, onClick: () -> Unit) {
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
