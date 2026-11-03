package org.arkikeskus.launcher.feature.appdrawer

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
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.ui.component.AppIcon
import kotlin.math.roundToInt

/** What a long-press in the split picker offers instead of putting the app in the split. */
enum class SplitPickAction { POPUP, FULL_SCREEN, FAVORITE }

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
 *
 * Above the apps, each as set in [look]: your favorites, the apps the dragged one was last put
 * beside ([partners]), and the recently used ones. With [pickCount] 2 (a three-app split) taps
 * choose two apps, numbered, and [onPickAll] gets both. [peek] non-null shows a Peek switch;
 * [onAction] (with the long-press menu on) gets "pop-up", "full screen" and favorite choices.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplitPickerDrawer(
    apps: List<AppItem>,
    folders: List<DrawerFolderUi>,
    onPick: (AppItem) -> Unit,
    modifier: Modifier = Modifier,
    look: SplitPickerLook = SplitPickerLook(),
    recents: List<SplitRecentItem> = emptyList(),
    firstPackageToHide: String = "",
    partners: List<AppItem> = emptyList(),
    pickCount: Int = 1,
    onPickAll: (List<AppItem>) -> Unit = {},
    peek: Boolean? = null,
    onPeekChange: (Boolean) -> Unit = {},
    onAction: ((AppItem, SplitPickAction) -> Unit)? = null,
    /** The picked app's share of the split in percent (33, 50, 66), shown as a little preview to
     *  choose from; null hides it (no divider control on this phone). [pickedOnLeft]: which half
     *  the pick goes to, for the preview. */
    share: Int? = null,
    pickedOnLeft: Boolean = false,
    onShareChange: (Int) -> Unit = {},
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
    val favorites = remember(shown, look.favorites, look.favoriteKeys) {
        if (!look.favorites) emptyList()
        else shown.associateBy { it.key }.let { byKey -> look.favoriteKeys.mapNotNull { byKey[it] } }
    }
    val pairedBefore = remember(partners, firstPackageToHide, look.recentPairs) {
        if (!look.recentPairs) emptyList() else partners.filter { it.packageName != firstPackageToHide }
    }
    val menu = if (look.longPress && onAction != null) {
        TileMenu(isFavorite = { it.key in look.favoriteKeys }, onAction = onAction)
    } else {
        null
    }
    // A three-app split: the apps chosen so far, in order.
    val chosen = remember { mutableStateListOf<AppItem>() }
    val badge: (AppItem) -> Int? = { a -> chosen.indexOfFirst { it.key == a.key }.takeIf { it >= 0 }?.plus(1) }

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
    // The glow out of the tapped icon (the same as the taskbar's when an app starts from it).
    var glowing by remember { mutableStateOf<Opening?>(null) }
    val glowProgress = remember { Animatable(0f) }
    val zoom = pickCount == 1 && look.openAnim == SplitPickerLook.OPEN_ZOOM && ValueAnimator.areAnimatorsEnabled()
    val pick: (AppItem, Rect?) -> Unit = pick@{ app, from ->
        if (opening != null || glowing != null) return@pick
        if (pickCount > 1) {
            val at = chosen.indexOfFirst { it.key == app.key }
            if (at >= 0) chosen.removeAt(at) else chosen.add(app)
            if (chosen.size >= pickCount) onPickAll(chosen.toList())
            return@pick
        }
        when {
            zoom && from != null -> opening = Opening(app, from.translate(-origin))
            // No zoom: a short glow out of the icon first, then the app opens.
            from != null && ValueAnimator.areAnimatorsEnabled() -> glowing = Opening(app, from.translate(-origin))
            else -> onPick(app)
        }
    }
    LaunchedEffect(glowing) {
        val g = glowing ?: return@LaunchedEffect
        glowProgress.snapTo(0f)
        glowProgress.animateTo(1f, tween(GLOW_MS, easing = FastOutSlowInEasing))
        onPick(g.app)
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
            if (pickCount > 1 || peek != null || share != null) {
                Header(
                    text = if (pickCount > 1) stringResource(R.string.split_picker_choose_more, chosen.size, pickCount)
                    else stringResource(R.string.split_picker_choose),
                    peek = peek,
                    onPeekChange = onPeekChange,
                )
            }
            // How big the pick will be: ⅓, ½ or ⅔, as small previews of the split (not with peek).
            if (share != null && pickCount == 1 && peek != true) {
                SharePicker(share, pickedOnLeft, onShareChange)
            }
            if (look.search && current == null) {
                SearchField(query = query, onQuery = { query = it })
            }
            if (!searching && current == null) {
                if (favorites.isNotEmpty()) {
                    AppRow(stringResource(R.string.split_picker_favorites), favorites, look.labels, menu, badge, pick)
                }
                if (pairedBefore.isNotEmpty()) {
                    AppRow(stringResource(R.string.split_picker_paired), pairedBefore, look.labels, menu, badge, pick)
                }
                if (look.recents != SplitPickerLook.RECENTS_OFF && recents.isNotEmpty()) {
                    RecentsRow(recents.take(look.recentsCount), previews = look.recents == SplitPickerLook.RECENTS_PREVIEWS, labels = look.labels, onPick = pick)
                }
            }

            when {
                searching -> AppGrid(columns) {
                    items(results, key = { it.key }) { app -> AppTile(app, iconSize, look.labels, menu, badge(app)) { pick(app, it) } }
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
                        items(current.apps, key = { it.key }) { app -> AppTile(app, iconSize, look.labels, menu, badge(app)) { pick(app, it) } }
                    }
                }
                else -> AppGrid(columns) {
                    items(realFolders, key = { "f${it.id}" }) { folder -> FolderTile(folder, iconSize, look.labels) { open = folder.id } }
                    val pad = (columns - realFolders.size % columns) % columns
                    if (realFolders.isNotEmpty() && pad > 0) item(span = { GridItemSpan(pad) }) { Box(Modifier) }
                    items(loose, key = { it.key }) { app -> AppTile(app, iconSize, look.labels, menu, badge(app)) { pick(app, it) } }
                }
            }
        }
        opening?.let {
            // The glow blooms out of the icon as the zoom begins.
            PickGlow(it.from, (progress.value * 1.8f).coerceAtMost(1f))
            ZoomOpen(it, progress.value, iconSize)
        }
        glowing?.let { PickGlow(it.from, glowProgress.value) }
    }
}

private const val GLOW_MS = 260

/**
 * A soft accent glow blooming out of the picked icon at [from] and fading, with a thin ring
 * spreading past it ([p] 0..1) — the taskbar's launch glow, in the picker.
 */
@Composable
private fun PickGlow(from: Rect, p: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val centre = from.center
        val base = maxOf(from.width, from.height) / 2f
        val fade = (1f - p) * (1f - p)
        val bloom = if (p < 0.2f) p / 0.2f else 1f
        val halo = base * (1f + 0.8f * p)
        if (halo > 0f) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to ACCENT.copy(alpha = 0.85f * bloom * fade),
                    0.55f to ACCENT.copy(alpha = 0.4f * bloom * fade),
                    1f to Color.Transparent,
                    center = centre,
                    radius = halo,
                ),
                radius = halo,
                center = centre,
            )
        }
        drawCircle(
            color = ACCENT.copy(alpha = 0.75f * fade),
            radius = base * (1f + 1.1f * p),
            center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * 0.1f * (1f - p) + 1f),
        )
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

/** The picker's long-press menu: what it offers, and where the choice goes. */
private class TileMenu(val isFavorite: (AppItem) -> Boolean, val onAction: (AppItem, SplitPickAction) -> Unit)

private val ACCENT = Color(0xFF5A9BF2)

/** "Choose an app" / "Choose 2 apps · 1 chosen", with the Peek switch when there is one. */
@Composable
private fun Header(text: String, peek: Boolean?, onPeekChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (peek != null) {
            FilterChip(
                selected = peek,
                onClick = { onPeekChange(!peek) },
                label = { Text(stringResource(R.string.split_picker_peek)) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.White.copy(alpha = 0.8f),
                    selectedContainerColor = ACCENT.copy(alpha = 0.35f),
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

/** The picked app's share of the screen, as three tiny split previews; the chosen one is lit. */
@Composable
private fun SharePicker(share: Int, pickedOnLeft: Boolean, onShareChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.split_picker_size), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        listOf(33, 50, 66).forEach { option ->
            val selected = option == share
            val border by androidx.compose.animation.animateColorAsState(
                if (selected) ACCENT else Color.White.copy(alpha = 0.18f), label = "shareBorder",
            )
            Row(
                Modifier
                    .size(width = 46.dp, height = 28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.5.dp, border, RoundedCornerShape(7.dp))
                    .clickable { onShareChange(option) }
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val mine = option / 100f
                val parts = if (pickedOnLeft) listOf(true to mine, false to 1f - mine) else listOf(false to 1f - mine, true to mine)
                parts.forEach { (isPick, w) ->
                    Box(
                        Modifier
                            .weight(w)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isPick) (if (selected) ACCENT else Color.White.copy(alpha = 0.45f)) else Color.White.copy(alpha = 0.16f)),
                    )
                }
            }
        }
    }
}

/** A titled row of apps (favorites, paired before), like the recent apps row. */
@Composable
private fun AppRow(
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

/**
 * One app: tap to pick (with where its icon is, for the zoom), long-press for [menu]. [badge]: its
 * number among the apps chosen for a three-app split.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: AppItem, iconSize: Dp, labels: Boolean, menu: TileMenu?, badge: Int?, onClick: (Rect?) -> Unit) {
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
