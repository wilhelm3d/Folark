package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// App rows and cells of the drawer list, and the frequent apps card (split out of AppDrawerScreen.kt).

@Composable
internal fun AppListRow(
    app: AppItem,
    badgeCount: Int,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showLabels: Boolean,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, Rect) -> Unit,
    dragController: HomeDragController,
    sourceTab: Long?,
    onDragOutStart: () -> Unit,
    onDropOnHome: (AppItem, Int, Int, Int) -> Unit,
    onDropOnDock: (AppItem) -> Unit,
    onDropOnTab: (AppItem, Long?) -> Unit,
    haptics: HapticFeedback,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    // Where the icon is, read when a long press or drag needs it (not on every scroll frame).
    val placedAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = { placedAt[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero }
    // Keyboard navigation (D-pad, a hardware keyboard's arrows): the row is focusable through its
    // click handling already; this draws where the focus sits and gives the list a soft glow.
    var rowFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .onPlaced { placedAt[0] = it }
            .pointerInput(app.key, locked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var outcome = 0
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null) {
                                outcome = 2
                                return@withTimeoutOrNull
                            }
                            if (!c.pressed) {
                                outcome = 1
                                return@withTimeoutOrNull
                            }
                            if (c.isConsumed || (c.position - down.position).getDistance() > slop) {
                                outcome = 2
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    when (outcome) {
                        1 -> {
                            onAppClick(app)
                            return@awaitEachGesture
                        }
                        2 -> return@awaitEachGesture
                    }
                    if (locked) return@awaitEachGesture
                    dragController.start(app, DragSource.Drawer, bounds().topLeft + down.position, sourceTab)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        val completed = drag(down.id) { change ->
                            change.consume()
                            if (!dragController.moving && (change.position - down.position).getDistance() > slop) {
                                dragController.beginMove()
                                onDragOutStart()
                            }
                            if (dragController.moving) {
                                val root = bounds().topLeft + change.position
                                dragController.update(root)
                                // The folder tab under the finger lights up while the drag moves.
                                dragController.setHoverFolderTab(root)
                            }
                        }
                        if (completed && dragController.moving) {
                            val root = dragController.rootPosition
                            // Dragged out of a folder tab: wherever it lands outside the drawer, it
                            // also leaves the folder (a chip, the Remove pill, home, dock, split).
                            val pullOut = dragController.dragFromTab != null
                            when {
                                // Onto a folder chip over home: into that folder (the All chip pulls out).
                                dragController.isOverFolderTab(root) ->
                                    onDropOnTab(app, dragController.folderTabAt(root))
                                // The Remove pill (a folder tab open): take the app out of its folder.
                                dragController.isOverRemove(root) && pullOut ->
                                    onDropOnTab(app, null)
                                // The "split" target on home: the partner picker opens.
                                dragController.dropOnSplit(app, root) -> if (pullOut) onDropOnTab(app, null)
                                // Onto the taskbar (shown on home): pin it there.
                                org.arkikeskus.launcher.ui.TaskbarDropTarget.isOver(root.x, root.y) -> {
                                    org.arkikeskus.launcher.ui.TaskbarDropTarget.pin(app)
                                    if (pullOut) onDropOnTab(app, null)
                                }
                                dragController.isOverDock(root) && dragController.dockHasSpace -> {
                                    onDropOnDock(app)
                                    if (pullOut) onDropOnTab(app, null)
                                }
                                dragController.isOverGrid(root) -> {
                                    val (page, cx, cy) = dragController.cellAt(root)
                                    onDropOnHome(app, page, cx, cy)
                                    if (pullOut) onDropOnTab(app, null)
                                }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (completed) {
                            onAppLongClick(app, bounds())
                        }
                    } finally {
                        dragController.stop()
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            appItem = app,
            labelColor = Color.Transparent,
            showLabel = false,
            iconSize = 44.dp,
            badgeCount = badgeCount,
            badgeShowCount = badgeShowCount,
            badgeScale = badgeScale,
            modifier = Modifier.border(
                width = 2.dp,
                color = if (rowFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            ),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = LocalAppLabelLines.current,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Visual pull-down affordance: a small pill below the status bar. */
@Composable
internal fun DragHandle(width: Dp = 36.dp, arrow: Boolean = false, progress: () -> Float = { 1f }) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (arrow) {
            // A small white chevron: it points up while the drawer is on its way open, flattens as
            // the drawer arrives, and points down once it is fully open ("pull down to close").
            // The position is read while drawing, so the turn follows the finger without recomposing.
            Canvas(Modifier.size(width = 26.dp, height = 12.dp)) {
                val bend = (((1f - progress()) * 4f).coerceIn(0f, 1f) * 2f - 1f) * 3.dp.toPx()
                val centre = Offset(size.width / 2f, size.height / 2f - bend)
                val reach = 9.dp.toPx()
                val stroke = 2.5.dp.toPx()
                drawLine(Color.White, centre, Offset(centre.x - reach, size.height / 2f + bend), stroke, StrokeCap.Round)
                drawLine(Color.White, centre, Offset(centre.x + reach, size.height / 2f + bend), stroke, StrokeCap.Round)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(width = width, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** The "most used" apps in a rounded Version C card above the alphabetical grid. The same apps also
 *  stay in the A–Z list below; this card is just a distinct quick-access shortcut row. Icons span
 *  the drawer columns (left-aligned), so the row tracks the icon-count setting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FrequentAppsCard(
    apps: List<AppItem>,
    columns: Int,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showLabels: Boolean,
    iconSize: Dp,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, Rect) -> Unit,
) {
    val p = LocalExpressivePalette.current
    Surface(
        color = p.surfaceHi,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = p.shadow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.drawer_frequent_apps),
                color = p.dim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                apps.forEach { app ->
                    // Where the icon is, read when a long press or drag needs it (not on every scroll frame).
                    val placedAt = remember(app.key) { arrayOfNulls<LayoutCoordinates>(1) }
                    val bounds = { placedAt[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero }
                    AppIcon(
                        appItem = app,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        showLabel = showLabels,
                        iconSize = iconSize,
                        badgeCount = badges[app.badgeKey] ?: 0,
                        badgeShowCount = badgeShowCount,
                        badgeScale = badgeScale,
                        modifier = Modifier
                            .weight(1f)
                            .onPlaced { placedAt[0] = it }
                            .combinedClickable(
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app, bounds()) },
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
                // Keep icons aligned to the leftmost columns when fewer than a full row are used.
                if (apps.size < columns) {
                    Spacer(Modifier.weight((columns - apps.size).toFloat()))
                }
            }
        }
    }
}

/** The drawer app-grid cell, shared by the idle and searching layouts. */
internal fun LazyGridScope.appCells(
    apps: List<AppItem>,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showLabels: Boolean,
    iconSize: Dp,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, Rect) -> Unit,
    dragController: HomeDragController,
    sourceTab: Long?,
    onDragOutStart: () -> Unit,
    onDropOnHome: (AppItem, Int, Int, Int) -> Unit,
    onDropOnTab: (AppItem, Long?) -> Unit,
    onDropOnDock: (AppItem) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    locked: Boolean,
) {
    items(items = apps, key = { it.key }, contentType = { "app" }) { app ->
        // Where the icon is, read when a long press or drag needs it (not on every scroll frame).
        val placedAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val bounds = { placedAt[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero }
        // Keyboard focus indicator (D-pad / hardware keyboard; the Lazy grid scrolls to it).
        var cellFocused by remember { mutableStateOf(false) }
        AppIcon(
            appItem = app,
            labelColor = MaterialTheme.colorScheme.onSurface,
            showLabel = showLabels,
            iconSize = iconSize,
            badgeCount = badges[app.badgeKey] ?: 0,
            badgeShowCount = badgeShowCount,
            badgeScale = badgeScale,
            modifier = Modifier
                .onFocusChanged { cellFocused = it.isFocused }
                .border(
                    width = 2.dp,
                    color = if (cellFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .onPlaced { placedAt[0] = it }
                // One unified gesture (like Workspace/Dock) but non-consuming until the
                // long-press fires, so a quick drag still scrolls the grid: quick tap
                // launches; a still long-press lifts → drag out to home/dock, or with no
                // movement opens the long-press menu.
                .pointerInput(app.key, locked) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        // 0 = long-press (timed out still), 1 = tap, 2 = scroll/abandon.
                        var outcome = 0
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null) {
                                    outcome = 2
                                    return@withTimeoutOrNull
                                }
                                if (!c.pressed) {
                                    outcome = 1
                                    return@withTimeoutOrNull
                                }
                                // Don't consume: let the LazyGrid scroll a quick drag.
                                if (c.isConsumed ||
                                    (c.position - down.position).getDistance() > slop
                                ) {
                                    outcome = 2
                                    return@withTimeoutOrNull
                                }
                            }
                        }
                        when (outcome) {
                            1 -> {
                                onAppClick(app)
                                return@awaitEachGesture
                            }
                            2 -> return@awaitEachGesture
                        }
                        // Desktop locked: long-press does nothing (no menu, no drag-out); tap still launches.
                        if (locked) return@awaitEachGesture
                        // LONG PRESS → lift into the shared controller (drawer source).
                        // The finger's root position is the icon's [bounds] top-left
                        // (captured while the drawer is open) plus the pointer's local
                        // position. The drawer is hidden with alpha (not translated) during
                        // the drag, so its local coordinate space stays put and this stays
                        // accurate.
                        dragController.start(app, DragSource.Drawer, bounds().topLeft + down.position, sourceTab)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            val completed = drag(down.id) { change ->
                                change.consume()
                                // Only commit to a drag-out once the finger has moved past the
                                // slop — otherwise a still long-press (with finger jitter) would
                                // collapse the drawer instead of just showing the menu.
                                if (!dragController.moving &&
                                    (change.position - down.position).getDistance() > slop
                                ) {
                                    dragController.beginMove()
                                    onDragOutStart()
                                }
                                if (dragController.moving) {
                                    val root = bounds().topLeft + change.position
                                    dragController.update(root)
                                    // The folder tab under the finger lights up while the drag moves.
                                    dragController.setHoverFolderTab(root)
                                }
                            }
                            if (completed && dragController.moving) {
                                val root = dragController.rootPosition
                                // Dragged out of a folder tab: wherever it lands outside the drawer,
                                // it also leaves the folder (a chip, the Remove pill, home, dock, split).
                                val pullOut = dragController.dragFromTab != null
                                when {
                                    // Onto a folder chip over home: into that folder (All pulls out).
                                    dragController.isOverFolderTab(root) ->
                                        onDropOnTab(app, dragController.folderTabAt(root))
                                    // The Remove pill (a folder tab open): take the app out of its folder.
                                    dragController.isOverRemove(root) && pullOut ->
                                        onDropOnTab(app, null)
                                    // The "split" target on home: the partner picker opens.
                                    dragController.dropOnSplit(app, root) -> if (pullOut) onDropOnTab(app, null)
                                    // Onto the taskbar (shown on home): pin it there.
                                    org.arkikeskus.launcher.ui.TaskbarDropTarget.isOver(root.x, root.y) -> {
                                        org.arkikeskus.launcher.ui.TaskbarDropTarget.pin(app)
                                        if (pullOut) onDropOnTab(app, null)
                                    }
                                    dragController.isOverDock(root) && dragController.dockHasSpace -> {
                                        onDropOnDock(app)
                                        if (pullOut) onDropOnTab(app, null)
                                    }
                                    dragController.isOverGrid(root) -> {
                                        val (page, cx, cy) = dragController.cellAt(root)
                                        onDropOnHome(app, page, cx, cy)
                                        if (pullOut) onDropOnTab(app, null)
                                    }
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (completed) {
                                // No movement → static long-press → show the menu by the icon.
                                onAppLongClick(app, bounds())
                            }
                        } finally {
                            // Reset even on cancellation (node disposed / pointerInput restarted),
                            // mirroring Workspace's local-drag path, so a dead gesture can't leave
                            // the shared controller lifted.
                            dragController.stop()
                        }
                    }
                }
                .padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}
