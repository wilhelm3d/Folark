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
    /** Flips which half the first app sits in, from the header's corner (null hides the symbol). */
    onSwapSides: (() -> Unit)? = null,
    /** Offer the 67/33 · 50/50 · 33/67 sizes ([share] is still shown for [threeEven] alone). */
    sizes: Boolean = true,
    /** Offer 33/33/33 next to the sizes: [share] [SHARE_THREE] picks two more apps for three thirds. */
    threeEven: Boolean = false,
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
    // A new choice of how many apps to pick (the 33/33/33 button) starts over.
    LaunchedEffect(pickCount) { chosen.clear() }
    val badge: (AppItem) -> Int? = { a -> chosen.indexOfFirst { it.key == a.key }.takeIf { it >= 0 }?.plus(1) }

    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = realFolders.firstOrNull { it.id == open }
    val searching = look.search && query.isNotBlank()
    val results = remember(shown, query, searching) {
        if (!searching) emptyList() else org.arkikeskus.launcher.data.search.AppMatcher.rank(shown, query.trim()) { it.label }
    }

    // Zoom: the app being opened, from where its icon was (in this drawer's coordinates).
    var opening by remember { mutableStateOf<Opening?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    // The glow out of the tapped icon (the same as the taskbar's when an app starts from it).
    var glowing by remember { mutableStateOf<Opening?>(null) }
    val glowProgress = remember { Animatable(0f) }
    val zoom = pickCount == 1 && look.openAnim == PickerOpen.ZOOM && ValueAnimator.areAnimatorsEnabled()
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
        // Room the apps keep free at the bottom: Android's navigation bar, or Folark's taskbar
        // when it lies over this window (it is an overlay, so the window's insets don't know it).
        val bottomRoom = rememberPickerBottomRoom()
        CompositionLocalProvider(LocalPickerBottomRoom provides bottomRoom) {
        Column(
            Modifier
                .fillMaxSize()
                // The header and Size row start below the status bar (and a camera cutout).
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top),
                )
                .graphicsLayer {
                    // The drawer sinks back while the tapped app grows over it.
                    val q = if (opening != null) progress.value else 0f
                    val s = 1f - 0.06f * q
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.7f * q
                },
        ) {
            // The header also comes for the Swap symbol alone, so a phone without the divider
            // module can still choose which half its picks go to.
            if (pickCount > 1 || peek != null || share != null || onSwapSides != null) {
                Header(
                    text = if (pickCount > 1) stringResource(R.string.split_picker_choose_more, chosen.size, pickCount)
                    else stringResource(R.string.split_picker_choose),
                    peek = peek,
                    onPeekChange = onPeekChange,
                ) {
                    // In the corner: how the screen is shared, 67/33 · 50/50 · 33/67 as on screen
                    // (picking one turns peek off).
                    // In the corner with the sizes: swap which half the app being chosen goes to.
                    if (onSwapSides != null) {
                        SwapButton(onSwapSides)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (share != null && (pickCount == 1 || share == SHARE_THREE)) {
                        ShareButtons(share, pickedOnLeft, peek == true, onShareChange, sizes, threeEven)
                    }
                }
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
                if (look.recents != PickerRecents.OFF && recents.isNotEmpty()) {
                    RecentsRow(recents.take(look.recentsCount), previews = look.recents == PickerRecents.PREVIEWS, labels = look.labels, onPick = pick)
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
        }
        opening?.let {
            // The glow blooms out of the icon as the zoom begins.
            PickGlow(it.from, (progress.value * 1.8f).coerceAtMost(1f))
            ZoomOpen(it, progress.value, iconSize)
        }
        glowing?.let { PickGlow(it.from, glowProgress.value) }
    }
}
