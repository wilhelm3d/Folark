package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.drawer.readableOn
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import kotlin.math.abs

// The drawer panel's parts around the app list (split out of AppDrawerScreen.kt): pulling it
// closed, the scrim, its shape, the tab bar, swiping between tabs, the A-Z bar and the dragged icon.

/**
 * Pulling the list down past its top drags the drawer closed, and letting go settles it; with
 * Settings ▸ App drawer ▸ flick down to close, a fast downward flick closes it from anywhere.
 */
@Composable
internal fun rememberDrawerPullConnection(
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    drawerOpen: Boolean,
    drawerFlickClose: Boolean,
    onClose: () -> Unit,
): NestedScrollConnection {
    val flickClosePx = with(LocalDensity.current) { 2400.dp.toPx() }
    return remember(onDrawerDrag, onDrawerSettle, drawerOpen, drawerFlickClose, onClose) {
        object : NestedScrollConnection {
            private var pulling = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulling && available.y < 0f) {
                    onDrawerDrag(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pulling = true
                    onDrawerDrag(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Settings ▸ App drawer ▸ flick down to close: a fast downward flick closes the
                // drawer wherever the list is scrolled to, instead of flinging the list.
                if (!pulling && drawerFlickClose && available.y > flickClosePx) {
                    onClose()
                    return available
                }
                if (pulling) {
                    pulling = false
                    onDrawerSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pulling) {
                    pulling = false
                    onDrawerSettle(consumed.y + available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

/**
 * The dimming behind the drawer ([opacity] is Settings ▸ App drawer ▸ background dim). Present only
 * while the drawer is at least partly open (it would catch taps otherwise); its strength follows the
 * slide in the draw phase, and a tap on it closes the drawer.
 */
@Composable
internal fun DrawerScrim(opacity: Float, progress: () -> Float, onClose: () -> Unit) {
    val currentProgress by rememberUpdatedState(progress)
    // The tap detector lives as long as the scrim: it must call the newest callback.
    val currentOnClose by rememberUpdatedState(onClose)
    val scrimShown by remember { derivedStateOf { currentProgress() > 0f } }
    if (scrimShown) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (currentProgress() * opacity * 0.7f).coerceIn(0f, 0.8f) }
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { currentOnClose() }
                },
        )
    }
}

/**
 * The panel's outline. Settings ▸ App drawer ▸ corner radius ([cornerRadius] < 0 is "Auto": the
 * drawer style's own [styleCorner], and a square full-screen drawer); a sidebar rounds the side
 * that faces the screen.
 */
internal fun drawerPanelShape(
    sidebar: Boolean,
    cornerRadius: Int,
    styleCorner: Dp,
    heightFraction: Float,
    widthFraction: Float,
    alignment: DrawerAlignment,
): RoundedCornerShape {
    val customCorner = cornerRadius >= 0
    val corner = if (customCorner) cornerRadius.dp else styleCorner
    return when {
        !sidebar && !customCorner && heightFraction >= 1.0f && widthFraction >= 1.0f -> RoundedCornerShape(0.dp)
        !sidebar -> RoundedCornerShape(topStart = corner, topEnd = corner)
        alignment == DrawerAlignment.LEFT -> RoundedCornerShape(topEnd = corner, bottomEnd = corner)
        else -> RoundedCornerShape(topStart = corner, bottomStart = corner)
    }
}

/** The folder tabs above the apps, with "+" for a new folder tab and the open tab's settings menu. */
@Composable
internal fun DrawerTabBar(
    tabs: List<Pair<Long, String>>,
    selectedTab: Long?,
    onSelectTab: (Long?) -> Unit,
    folderDrag: DrawerFolderDrag,
    onRenameFolder: (Long, String) -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    tabsReorderable: Boolean,
    onNewFolder: () -> Unit,
    folders: List<DrawerFolderUi>,
    onEditFolder: (Long) -> Unit,
    onDeleteTab: (Long) -> Unit,
) {
    DrawerTabRow(
        tabs = tabs, selected = selectedTab, onSelect = onSelectTab,
        drag = folderDrag, onRename = onRenameFolder,
        modifier = Modifier.fillMaxWidth(),
        onMove = onMoveTab, reorderable = tabsReorderable,
        onNewFolder = onNewFolder,
    )
}

/** The settings menu of the open tab [selectedTab], when that tab is a folder. */
@Composable
internal fun OpenTabSettingsMenu(
    folders: List<DrawerFolderUi>,
    selectedTab: Long,
    onEditFolder: (Long) -> Unit,
    onDeleteTab: (Long) -> Unit,
    modifier: Modifier,
) {
    val folder = folders.firstOrNull { it.id == selectedTab } ?: return
    TabFolderSettingsMenu(
        folder = folder,
        onEdit = { onEditFolder(selectedTab) },
        onDelete = { onDeleteTab(selectedTab) },
        modifier = modifier,
    )
}

/**
 * The apps as runs that share a first letter, each under its letter, or one run without a letter
 * when there are no [headings] (Settings ▸ App drawer ▸ letter headings off, or a search showing).
 */
internal fun runsByLetter(apps: List<AppItem>, headings: Boolean): List<Pair<Char?, List<AppItem>>> {
    if (!headings) return listOf(null to apps)
    return buildList {
        var start = 0
        while (start < apps.size) {
            val letter = drawerLetterOf(apps[start])
            var end = start + 1
            while (end < apps.size && drawerLetterOf(apps[end]) == letter) end++
            add(letter to apps.subList(start, end))
            start = end
        }
    }
}

/**
 * Settings ▸ App drawer ▸ swipe sideways to change tab. Watched on the way down to the icons; a drag
 * that starts on a horizontal A-Z bar ([indexStrip] high at [azIndexPosition]), or while an icon is
 * being dragged out, is left alone. [selectedTab] and [onSelectTab] are read when a swipe ends, so
 * they must give the newest values.
 */
internal fun Modifier.swipeToChangeTab(
    enabled: Boolean,
    tabs: List<Pair<Long, String>>,
    searching: Boolean,
    azIndexPosition: AzPosition,
    indexStrip: Dp,
    dragController: HomeDragController,
    selectedTab: () -> Long?,
    onSelectTab: (Long?) -> Unit,
): Modifier = pointerInput(enabled, tabs, searching, azIndexPosition, indexStrip) {
    if (!enabled || tabs.isEmpty() || searching) return@pointerInput
    val slop = viewConfiguration.touchSlop
    val stripPx = indexStrip.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val onBar = (azIndexPosition == AzPosition.HORIZONTAL_BOTTOM && down.position.y > size.height - stripPx) ||
            (azIndexPosition == AzPosition.HORIZONTAL_TOP && down.position.y < stripPx)
        var total = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            total += change.positionChange()
            if (onBar || dragController.moving) { claimed = false; break }
            if (!claimed && abs(total.x) > slop * 2f && abs(total.x) > abs(total.y) * 1.5f) claimed = true
            if (claimed) change.consume()
        }
        if (claimed && abs(total.x) > slop * 3f) {
            val ids: List<Long?> = listOf<Long?>(null) + tabs.map { it.first }
            val at = ids.indexOf(selectedTab()).coerceAtLeast(0)
            val next = (at + if (total.x < 0f) 1 else -1).coerceIn(0, ids.lastIndex)
            if (next != at) onSelectTab(ids[next])
        }
    }
}

/**
 * Where the list scrolls for [char] on the A-Z bar: the item of the first app from that letter on
 * (the last app when none follows), counting the [itemsAbove] the apps and, with letter headings,
 * one heading per run before it.
 */
internal fun letterScrollTarget(
    char: Char,
    apps: List<AppItem>,
    letterRuns: List<Pair<Char?, List<AppItem>>>,
    itemsAbove: Int,
): Int {
    val matchIdx = if (char == '#') {
        0
    } else {
        apps.indexOfFirst {
            val firstChar = it.label.firstOrNull()?.uppercaseChar() ?: ' '
            firstChar >= char
            // No app from this letter on: go to the end, not back to the top.
        }.let { if (it < 0) (apps.size - 1).coerceAtLeast(0) else it }
    }
    // With letter headings every run before the target adds one more item.
    var headingsBefore = 0
    if (letterRuns.firstOrNull()?.first != null) {
        var seen = 0
        for ((_, run) in letterRuns) {
            if (seen > matchIdx) break
            headingsBefore++
            seen += run.size
        }
        headingsBefore = (headingsBefore - 1).coerceAtLeast(0)
    }
    return (itemsAbove + matchIdx + headingsBefore)
        .coerceIn(0, (itemsAbove + apps.size + letterRuns.size).coerceAtLeast(0))
}

/**
 * The A-Z bar over the apps at [position]: a letter scrolls the list to its first app ([itemsAbove]
 * being the rows drawn above the apps); held and slid ([holdScroll]), it scrolls smoothly instead;
 * with [largeLetterPopup] the letter under the finger shows large in the middle.
 */
@Composable
internal fun BoxScope.DrawerIndexOverlay(
    position: AzPosition,
    scale: Float,
    apps: List<AppItem>,
    letterRuns: List<Pair<Char?, List<AppItem>>>,
    itemsAbove: Int,
    listLayout: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    largeLetterPopup: Boolean,
    holdScroll: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    // The letter under the finger on the A-Z bar, shown large in the middle of the list.
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    val alignModifier = when (position) {
        AzPosition.VERTICAL_LEFT -> Modifier.align(Alignment.CenterStart)
        AzPosition.HORIZONTAL_TOP -> Modifier.align(Alignment.TopCenter)
        AzPosition.HORIZONTAL_BOTTOM -> Modifier.align(Alignment.BottomCenter)
        else -> Modifier.align(Alignment.CenterEnd)
    }
    DrawerIndexBar(
        position = position,
        scale = scale,
        available = remember(apps) { apps.mapTo(HashSet()) { drawerLetterOf(it) } },
        onLetterSelected = { char ->
            val targetIndex = letterScrollTarget(char, apps, letterRuns, itemsAbove)
            coroutineScope.launch {
                // Nearby: a short animated glide; far (A→Z while sliding): an instant
                // jump, which feels snappier than flying the whole list.
                if (listLayout) {
                    val near = abs(targetIndex - listState.firstVisibleItemIndex) <= 45
                    if (near) listState.animateScrollToItem(targetIndex)
                    else listState.scrollToItem(targetIndex)
                } else {
                    val near = abs(targetIndex - gridState.firstVisibleItemIndex) <= 45
                    if (near) gridState.animateScrollToItem(targetIndex)
                    else gridState.scrollToItem(targetIndex)
                }
            }
        },
        modifier = alignModifier,
        onActiveLetter = if (largeLetterPopup) ({ letter: Char? -> activeLetter = letter }) else null,
        // Held, then slid: a smooth scroll through the apps instead of letter jumps.
        onFineScroll = if (holdScroll) ({ by: Float ->
            coroutineScope.launch {
                if (listLayout) listState.scrollBy(by) else gridState.scrollBy(by)
            }
        }) else null,
    )
    activeLetter?.let { letter ->
        Surface(
            shape = CircleShape,
            color = LocalAccent.current,
            contentColor = readableOn(LocalAccent.current),
            shadowElevation = 8.dp,
            modifier = Modifier.size(104.dp).align(Alignment.Center),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = letter.toString(), fontSize = 46.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The icon being dragged stays in the drawer while the drag does: it follows the finger, so dropping
 * it on another app (or on a folder) reads as one motion. Once the drag leaves the panel this goes
 * away and the home drag layer shows the icon instead.
 */
@Composable
internal fun BoxScope.DrawerDragGhost(folderDrag: DrawerFolderDrag) {
    val dragged = folderDrag.dragged ?: return
    val ghostSize = 56.dp * LocalIconSizeScale.current
    val half = with(LocalDensity.current) { ghostSize.toPx() / 2f }
    Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopStart) {
        AppIcon(
            appItem = dragged,
            labelColor = Color.Transparent,
            showLabel = false,
            iconSize = ghostSize,
            modifier = Modifier.graphicsLayer {
                translationX = folderDrag.position.x - half
                translationY = folderDrag.position.y - half
                alpha = 0.95f
            },
        )
    }
}
