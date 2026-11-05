package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.arkikeskus.launcher.model.WidgetPlacement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.data.ReorderPlanner
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.aquamorphicTouch
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.iconSizeForCell
import org.arkikeskus.launcher.ui.component.labelBlockHeight
import org.arkikeskus.launcher.ui.component.labelFontFactor
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

// Folders, shortcuts, widgets, placeholders and widget stacks on the home grid (split out of Workspace.kt).

/** One PlacedFolder on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspaceFolderCell(
    entry: PlacedFolder,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            // Apps opened right from the tile: where each place of it is, and where this cell is.
            val folderStyle = LocalFolderStyle.current
            val directApps = folderStyle.directApps
            // Started afresh when the tile changes from 2×2 to 3×3 (or back): old places must not count.
            val slotRects = remember(entry.id, folderStyle.preview) { HashMap<Int, androidx.compose.ui.geometry.Rect>() }
            var cellOrigin by remember(entry.id) { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .offset {
                        val sx = entry.cellX.coerceIn(0, columns - 1)
                        val sy = entry.cellY.coerceIn(0, rows - 1)
                        IntOffset((sx * cellW).roundToInt(), (sy * cellH).roundToInt())
                    }
                    .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                    // Hide (but keep composed) the in-grid folder once it's moving;
                    // the floating copy is drawn on top. While only lifted it stays.
                    .graphicsLayer {
                        alpha = if ((draggingLocal as? PlacedFolder)?.id == entry.id && localMoving) 0f else 1f
                    }
                    .onGloballyPositioned { cellOrigin = it.boundsInRoot().topLeft }
                    .aquamorphicTouch()
                    .localEntryDrag(
                        entry = entry,
                        rowId = entry.id,
                        removable = false,
                        onTap = {
                            // On an app of the tile: that app; anywhere else (the label, a free place,
                            // "+N"): the folder, as before.
                            val at = cellOrigin + lastTapLocal
                            val index = if (directApps) slotRects.entries.firstOrNull { it.value.contains(at) }?.key else null
                            val slots = slotRects.size
                            val hasMore = slots > 0 && entry.apps.size > slots
                            val app = index?.takeUnless { hasMore && it == slots - 1 }?.let { entry.apps.getOrNull(it) }
                            if (app != null) currentFolderAppTap(app) else currentFolderTap(entry)
                        },
                        onStillPress = { currentOpenFolder(entry) },
                        onRemove = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                WithBadge({ entry.apps.sumOf { badges[it.badgeKey] ?: 0 } }) { folderBadge ->
                    FolderIcon(
                        name = entry.name,
                        apps = entry.apps,
                        showLabel = showLabels,
                        badgeCount = folderBadge,
                        badgeShowCount = badgeShowCount,
                        badgeScale = badgeScale,
                        labelColor = labelColor,
                        size = cellIconSize,
                        glassBlurRadius = glassBlurRadius,
                        glassDarkTint = glassDarkTint,
                        onSlotBounds = if (directApps) ({ i, r -> slotRects[i] = r }) else null,
                    )
                }
            }
    } } } }
}

/** One PlacedShortcut on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspaceShortcutCell(
    entry: PlacedShortcut,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            Box(
                modifier = Modifier
                    .offset {
                        val sx = entry.cellX.coerceIn(0, columns - 1)
                        val sy = entry.cellY.coerceIn(0, rows - 1)
                        IntOffset((sx * cellW).roundToInt(), (sy * cellH).roundToInt())
                    }
                    .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                    .graphicsLayer {
                        alpha = if ((draggingLocal as? PlacedShortcut)?.rowId == entry.rowId && localMoving) 0f else 1f
                    }
                    .aquamorphicTouch()
                    // Removable: drag up to the "Poista" zone to take it off home
                    // (no in-place menu — the remove zone replaces it).
                    .localEntryDrag(
                        entry = entry,
                        rowId = entry.rowId,
                        removable = true,
                        onTap = { currentLaunchShortcut(entry) },
                        onStillPress = {},
                        onRemove = { currentRemoveShortcut(entry.rowId) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ShortcutIconContent(entry, showLabels, labelColor, cellIconSize)
            }
    } } } }
}

/** One PlacedWidget on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspaceWidgetCell(
    entry: PlacedWidget,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            val widget = entry
            val ctx = LocalContext.current
            val info = remember(widget.appWidgetId) {
                AppWidgetManager.getInstance(ctx).getAppWidgetInfo(widget.appWidgetId)
            }
            // The persistent host view (created + kept alive in HomeScreen so its
            // AppWidgetHost listener survives page scrolling) and its scrollability —
            // the root bounds are published to the controller so the swipe-up detector
            // yields to a collection widget's own scroll instead of opening the drawer.
            val hostView = widgetViews[widget.appWidgetId]
            val widgetScrollable = widgetScrollableById[widget.appWidgetId] ?: false
            var widgetRect by remember(widget.rowId) { mutableStateOf(Rect.Zero) }
            LaunchedEffect(widget.rowId, widgetScrollable) {
                if (widgetScrollable && !widgetRect.isEmpty) {
                    dragController.scrollableWidgetRects[widget.rowId] = widgetRect
                } else {
                    dragController.scrollableWidgetRects.remove(widget.rowId)
                }
            }
            DisposableEffect(widget.rowId) {
                onDispose { dragController.scrollableWidgetRects.remove(widget.rowId) }
            }
            // Being resized: the frame that follows the finger (live resize), else its cells.
            val liveRect = remember(widget.rowId) { androidx.compose.runtime.derivedStateOf { widgetLive?.takeIf { it.first == widget.rowId }?.second } }.value
            val boxW = liveRect?.width ?: (widget.spanX * cellW)
            val boxH = liveRect?.height ?: (widget.spanY * cellH)
            Box(
                modifier = Modifier
                    .offset {
                        if (liveRect != null) {
                            IntOffset(liveRect.left.roundToInt(), liveRect.top.roundToInt())
                        } else {
                            IntOffset(
                                (widget.cellX.coerceIn(0, columns - 1) * cellW).roundToInt(),
                                (widget.cellY.coerceIn(0, rows - 1) * cellH).roundToInt(),
                            )
                        }
                    }
                    .size(
                        with(density) { boxW.toDp() },
                        with(density) { boxH.toDp() },
                    )
                    .onGloballyPositioned {
                        widgetRect = it.boundsInRoot()
                        if (widgetScrollable) dragController.scrollableWidgetRects[widget.rowId] = widgetRect
                    }
                    .graphicsLayer {
                        alpha = if (widgetDragController.moving &&
                            widgetDragController.active?.item?.rowId == widget.rowId) 0f else 1f
                    }
                    .widgetDragGesture(widget.rowId, !locked, widgetDragController) { root, fraction, owner ->
                        liftWidget(EditingItem(widget.rowId, widget.appWidgetId, widget.page,
                            widget.cellX, widget.cellY, widget.spanX, widget.spanY), root, fraction, owner)
                    }
                    .widgetLook(widgetLooks[widget.rowId]),
            ) {
                if (hostView != null) {
                    // Host the persistent AppWidgetHostView inside a container, re-parenting
                    // it into this page's container whenever the page (re)composes. The view
                    // object — and its AppWidgetHost listener — is never destroyed by a page
                    // scroll, so a collection widget's deferred row updates aren't dropped.
                    AndroidView(
                        factory = { c -> android.widget.FrameLayout(c) },
                        update = { container ->
                            // While resizing, the size in steps of a few dp: the widget's app lays it
                            // out again as the frame moves, without a call for every pixel.
                            val step = if (liveRect != null) LIVE_SIZE_STEP_DP else 1
                            val wDp = (boxW / density.density).toInt() / step * step
                            val hDp = (boxH / density.density).toInt() / step * step
                            pushWidgetSize(hostView, wDp, hDp)
                            if (hostView.parent !== container) {
                                (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
                                container.removeAllViews()
                                container.addView(
                                    hostView,
                                    android.widget.FrameLayout.LayoutParams(
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (info == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.widget_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // else: the host view is still being created (brief) — render nothing.
            }
    } } } }
}

/** One PendingWidget on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspacePendingWidgetCell(
    entry: PendingWidget,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            val pending = entry
            val ctxP = LocalContext.current
            // Resolve the provider's label + icon (falls back to the app icon). The
            // system caches these, so a plain remember keyed on the provider is fine.
            // Read in the background (E18): the provider list and the icon cost a binder trip each.
            val visual by androidx.compose.runtime.produceState<Pair<String, androidx.compose.ui.graphics.ImageBitmap?>>(
                initialValue = pending.provider.packageName to null, pending.provider,
            ) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val info = AppWidgetManager.getInstance(ctxP).installedProviders
                        .firstOrNull { it.provider == pending.provider }
                    val label = info?.loadLabel(ctxP.packageManager)
                        ?: pending.provider.packageName
                    val icon = runCatching {
                        (info?.loadIcon(ctxP, ctxP.resources.displayMetrics.densityDpi)
                            ?: ctxP.packageManager.getApplicationIcon(pending.provider.packageName))
                            .toBitmap().asImageBitmap()
                    }.getOrNull()
                    label to icon
                }
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (pending.cellX.coerceIn(0, columns - 1) * cellW).roundToInt(),
                            (pending.cellY.coerceIn(0, rows - 1) * cellH).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { (pending.spanX * cellW).toDp() },
                        with(density) { (pending.spanY * cellH).toDp() },
                    )
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .pointerInput(pending.rowId, locked) {
                        if (locked) return@pointerInput
                        detectTapGestures(
                            onTap = { onRestorePendingWidget(pending) },
                            onLongPress = { onRemovePendingWidget(pending.rowId) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp),
                ) {
                    visual.second?.let { bmp ->
                        Image(bmp, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        visual.first,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.widget_restore_tap),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
    } } } }
}

/** One PlacedBuiltin on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspaceBuiltinCell(
    entry: PlacedBuiltin,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            val space = entry
            val isScrollableBuiltin = space.type == HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS ||
                space.type == HomeItemEntity.BUILTIN_NOTIFICATIONS ||
                space.type == HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET
            var builtinRect by remember(space.rowId) { mutableStateOf(Rect.Zero) }
            LaunchedEffect(space.rowId, isScrollableBuiltin) {
                if (isScrollableBuiltin && !builtinRect.isEmpty) {
                    dragController.scrollableWidgetRects[space.rowId] = builtinRect
                } else {
                    dragController.scrollableWidgetRects.remove(space.rowId)
                }
            }
            DisposableEffect(space.rowId) {
                onDispose { dragController.scrollableWidgetRects.remove(space.rowId) }
            }
            // Being resized: the frame that follows the finger (live resize), else its cells.
            val liveRect = remember(space.rowId) { androidx.compose.runtime.derivedStateOf { widgetLive?.takeIf { it.first == space.rowId }?.second } }.value
            Box(
                modifier = Modifier
                    .offset {
                        if (liveRect != null) {
                            IntOffset(liveRect.left.roundToInt(), liveRect.top.roundToInt())
                        } else {
                            IntOffset(
                                (space.cellX.coerceIn(0, columns - 1) * cellW).roundToInt(),
                                (space.cellY.coerceIn(0, rows - 1) * cellH).roundToInt(),
                            )
                        }
                    }
                    .size(
                        with(density) { (liveRect?.width ?: (space.spanX * cellW)).toDp() },
                        with(density) { (liveRect?.height ?: (space.spanY * cellH)).toDp() },
                    )
                    .onGloballyPositioned {
                        builtinRect = it.boundsInRoot()
                        if (isScrollableBuiltin) dragController.scrollableWidgetRects[space.rowId] = builtinRect
                    }
                    .graphicsLayer {
                        alpha = if (widgetDragController.moving &&
                            widgetDragController.active?.item?.rowId == space.rowId) 0f else 1f
                    }
                    .widgetDragGesture(space.rowId, !locked, widgetDragController) { root, fraction, owner ->
                        liftWidget(EditingItem(space.rowId, null, space.page,
                            space.cellX, space.cellY, space.spanX, space.spanY), root, fraction, owner)
                    }
                    .widgetLook(widgetLooks[space.rowId]),
            ) {
                CompositionLocalProvider(LocalWidgetDragController provides widgetDragController) {
                    when (space.type) {
                        HomeItemEntity.BUILTIN_NOTIFICATIONS ->
                            NotificationsWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET ->
                            SamsungNotificationWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_BATTERY ->
                            BatteryWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_PEOPLE ->
                            PeopleWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_NOTHING_CLOCK ->
                            NothingClockWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_SAMSUNG_WEATHER ->
                            SamsungWeatherWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS ->
                            InteractiveNotificationWidget(modifier = Modifier.fillMaxSize())
                        HomeItemEntity.BUILTIN_MEDIA, HomeItemEntity.BUILTIN_TOP_APPS,
                        HomeItemEntity.BUILTIN_STATS, HomeItemEntity.BUILTIN_NOTE, HomeItemEntity.BUILTIN_CLIPBOARD, HomeItemEntity.BUILTIN_MIXER,
            HomeItemEntity.BUILTIN_WEATHER_DAY, HomeItemEntity.BUILTIN_WEATHER_BIG ->
                            BuiltinWidget(space.type, Modifier.fillMaxSize())
                        else -> SmartspaceWidget(modifier = Modifier.fillMaxSize())
                    }
                }
            }
    } } } }
}

/** One PlacedWidgetStack on the home grid (split out of [Workspace]; it skips recomposition while unchanged). */
@Composable
internal fun WorkspaceWidgetStackCell(
    entry: PlacedWidgetStack,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
) {
    val density = LocalDensity.current
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)
    with(ws) { with(grid) { with(env) { with(deps) {
            val stack = entry
            var stackRect by remember(stack.rowId) { mutableStateOf(Rect.Zero) }
            LaunchedEffect(stack.rowId, stack.items.size) {
                if (stack.items.size > 1 && !stackRect.isEmpty) {
                    dragController.scrollableWidgetRects[stack.rowId] = stackRect
                } else {
                    dragController.scrollableWidgetRects.remove(stack.rowId)
                }
            }
            DisposableEffect(stack.rowId) {
                onDispose { dragController.scrollableWidgetRects.remove(stack.rowId) }
            }
            // Being resized: the frame that follows the finger (live resize), else its cells.
            val liveRect = remember(stack.rowId) { androidx.compose.runtime.derivedStateOf { widgetLive?.takeIf { it.first == stack.rowId }?.second } }.value
            Box(
                modifier = Modifier
                    .offset {
                        if (liveRect != null) {
                            IntOffset(liveRect.left.roundToInt(), liveRect.top.roundToInt())
                        } else {
                            IntOffset(
                                (stack.cellX.coerceIn(0, columns - 1) * cellW).roundToInt(),
                                (stack.cellY.coerceIn(0, rows - 1) * cellH).roundToInt(),
                            )
                        }
                    }
                    .size(
                        with(density) { (liveRect?.width ?: (stack.spanX * cellW)).toDp() },
                        with(density) { (liveRect?.height ?: (stack.spanY * cellH)).toDp() },
                    )
                    .onGloballyPositioned {
                        stackRect = it.boundsInRoot()
                        if (stack.items.size > 1) {
                            dragController.scrollableWidgetRects[stack.rowId] = stackRect
                        }
                    }
                    .graphicsLayer {
                        alpha = if (widgetDragController.moving &&
                            widgetDragController.active?.item?.rowId == stack.rowId) 0f else 1f
                    }
                    .widgetDragGesture(stack.rowId, !locked, widgetDragController) { root, fraction, owner ->
                        liftWidget(EditingItem(stack.rowId, null, stack.page,
                            stack.cellX, stack.cellY, stack.spanX, stack.spanY), root, fraction, owner)
                    }
                    .widgetLook(widgetLooks[stack.rowId]),
            ) {
                WidgetStackContainer(
                    stack = stack,
                    widthDp = (stack.spanX * cellW / density.density).toInt(),
                    heightDp = (stack.spanY * cellH / density.density).toInt(),
                    widgetViews = widgetViews,
                    widgetDragController = widgetDragController,
                    onRestorePendingWidget = onRestorePendingWidget,
                    onAddToStack = { stackId ->
                        onAddToWidgetStack(stackId, -1L)
                    },
                    onRemoveFromStack = onRemoveFromWidgetStack,
                    onDeleteStack = { id -> onRemoveWidget(id, null) },
                    locked = locked,
                    modifier = Modifier.fillMaxSize(),
                )
            }
    } } } }
}

/**
 * The edit frame over the widget [ew] (resize handles, move to a page, style, reconfigure, remove),
 * split out of [Workspace]. A resize shows at once ([WorkspaceDragState.widgetOptimistic]) and is
 * rolled back if the repository refuses it.
 */
@Composable
internal fun WorkspaceWidgetEditFrame(
    ew: EditingItem,
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    deps: WorkspaceCellDeps,
    effectiveEntries: List<HomeEntry>,
    rectFreeOnGrid: (excludeRowId: Long, page: Int, x: Int, y: Int, spanX: Int, spanY: Int) -> Boolean,
    onSetWidgetBounds: suspend (rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean,
    onReconfigureWidget: (appWidgetId: Int) -> Unit,
) {
    val density = LocalDensity.current
    with(ws) { with(grid) { with(env) { with(deps) {
    val ctxE = LocalContext.current
    // A built-in widget (appWidgetId == null) has no provider info: it resizes freely
    // within the grid (with minSpanX = 1, minSpanY = 1) and has nothing to reconfigure.
    val info = remember(ew.appWidgetId) {
        ew.appWidgetId?.let { AppWidgetManager.getInstance(ctxE).getAppWidgetInfo(it) }
    }
    val range = remember(ew.rowId, columns, rows, cellW, cellH) {
        if (ew.appWidgetId == null) {
            WidgetResizeRange(
                minX = 1, minY = 1,
                maxX = columns, maxY = rows,
                horizontal = true, vertical = true,
            )
        } else {
            info?.let { widgetResizeRange(it, ctxE, columns, rows, cellW / density.density, cellH / density.density) }
                ?.copy(minX = 1, minY = 1)
                ?.takeIf { it.isResizable }
        }
    }
    val reconfigurable = remember(ew.appWidgetId) { info?.let { isReconfigurableWidget(it) } ?: false }
    key(ew) { Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = if (widgetDragController.moving) 0f else 1f
    }) { WidgetEditOverlay(
        widget = ew,
        widgetDragController = widgetDragController,
        commitScope = scope,
        onCancelPreview = { widgetOptimistic = null },
        onLift = liftWidget,
        range = range,
        reconfigurable = reconfigurable,
        cellW = cellW, cellH = cellH, columns = columns, rows = rows, density = density,
        // A resize "fits" if the cells are free OR the overlapping items can be pushed
        // aside (same plan the repository commits on release), so the handle only grows
        // when the commit will actually succeed.
        canFit = { x, y, sx, sy ->
            rectFreeOnGrid(ew.rowId, ew.page, x, y, sx, sy) ||
                ReorderPlanner.planFit(
                    effectiveEntries.mapIndexed { i, e ->
                        val (esx, esy) = when (e) {
                            is PlacedWidget -> e.spanX to e.spanY
                            is PendingWidget -> e.spanX to e.spanY
                            is PlacedBuiltin -> e.spanX to e.spanY
                            is PlacedWidgetStack -> e.spanX to e.spanY
                            else -> 1 to 1
                        }
                        // The edited row must carry its REAL id so planFit can exclude
                        // it from its own collision set (widget or built-in alike).
                        val erow = when (e) {
                            is PlacedWidget -> e.rowId
                            is PlacedBuiltin -> e.rowId
                            is PlacedWidgetStack -> e.rowId
                            else -> -(i.toLong() + 1)
                        }
                        ReorderPlanner.Rect(erow, e.page, e.cellX, e.cellY, esx, esy)
                    },
                    ew.rowId, ew.page, x, y, sx, sy, columns, rows,
                ) != null
        },
        // Optimistic like the app-drag path: the new bounds show immediately (the
        // hosted content too, not just the edit frame) and a repository rejection
        // rolls the override back; the overlay also resets its frame on `false`.
        onSetBounds = { x, y, sx, sy ->
            widgetOptimistic = ew.rowId to WidgetBounds(ew.page, x, y, sx, sy)
            val ok = onSetWidgetBounds(ew.rowId, ew.page, x, y, sx, sy)
            if (!ok) widgetOptimistic = null
            ok
        },
        // Size-only preview while a resize handle is mid-drag: no DB write yet, the
        // commit (or the overlay's reset) lands on release.
        onPreviewBounds = { x, y, sx, sy ->
            widgetOptimistic = ew.rowId to WidgetBounds(ew.page, x, y, sx, sy)
        },
        onLiveBounds = { r -> widgetLive = r?.let { ew.rowId to it } },
        onReconfigure = { ew.appWidgetId?.let(onReconfigureWidget); editingWidget = null },
        onStyle = { lookEditing = ew.rowId; editingWidget = null },
        onDeleteWidget = { onRemoveWidget(ew.rowId, ew.appWidgetId); editingWidget = null },
        onExit = { editingWidget = null },
        canMovePrev = ew.page > 0,
        canMoveNext = ew.page < lastPage,
        onMoveToPage = { targetPage, msx, msy ->
            // Place at the first free cell on the target page; commit, exit edit and
            // scroll there. No room → no-op (stay in edit).
            run loop@{
                for (fy in 0..(rows - msy).coerceAtLeast(0)) {
                    for (fx in 0..(columns - msx).coerceAtLeast(0)) {
                        if (rectFreeOnGrid(ew.rowId, targetPage, fx, fy, msx, msy)) {
                            widgetOptimistic = ew.rowId to WidgetBounds(targetPage, fx, fy, msx, msy)
                            scope.launch {
                                if (!onSetWidgetBounds(ew.rowId, targetPage, fx, fy, msx, msy)) widgetOptimistic = null
                            }
                            editingWidget = null
                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                            return@loop
                        }
                    }
                }
            }
        },
    ) } }

    } } } }
}

/** Live resize: the size given to a widget changes in steps this big (dp) while its frame is dragged. */
private const val LIVE_SIZE_STEP_DP = 16
