package org.arkikeskus.launcher.feature.home.workspace

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.widgets.*
import org.arkikeskus.launcher.feature.home.taskbar.*

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
import androidx.compose.animation.core.VectorConverter
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
import org.arkikeskus.launcher.model.catching

/**
 * The paged home workspace: a [HorizontalPager] of cell grids with a floating drag overlay.
 *
 * Gestures are separated by location so they never fight each other:
 * - **Icon** (child node): tap launches; long-press lifts the icon (haptic tick) and the drag
 *   consumes the pointer immediately, so neither the pager nor the page swipes can steal it. Drop
 *   on a cell to move it (a second tick); drop without moving opens [onAppMenu]; drag to the screen
 *   edge flips the page.
 * - **Page background** (parent node, only reached when no icon is under the finger): a still
 *   long-press opens settings. (The drawer/notifications swipe-up was hoisted to HomeScreen's root —
 *   see Modifier.pixelHomeSwipe — so it wins over icons and the dock too.)
 * - **Pager**: horizontal swipes change pages (disabled while an icon is being dragged).
 *
 * [homeSignals] (HOME pressed / home gesture) exits widget edit mode; when the press happened while
 * the launcher was already foreground (the Boolean payload) it also scrolls back to the first page.
 */
@Composable
fun Workspace(
    pageCount: Int,
    columns: Int,
    rows: Int,
    entries: List<HomeEntry>,
    badges: BadgeCounts,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showLabels: Boolean,
    labelColor: Color = Color.White,
    glassBlurRadius: Float = 25f,
    glassDarkTint: Float = 0.45f,
    showPageIndicator: Boolean,
    locked: Boolean,
    isDualPage: Boolean = false,
    pageBounceEnabled: Boolean = true,
    /** Swipe effect of the pages (LauncherSettings.PAGE_EFFECT_*) and the animation speed multiplier. */
    pageScrollEffect: String = LauncherSettings.PAGE_EFFECT_SLIDE,
    /** How far the unfold animation is (0 → 1; 1 = at rest). Read while drawing, so it never recomposes. */
    unfoldProgress: () -> Float = { 1f },
    /** Whether a drag past the last page may open a new one (never in the book layout). */
    allowNewPage: Boolean = false,
    minusOneScreen: String = LauncherSettings.MINUS_ONE_NONE,
    animationSpeed: Float = 1f,
    onWidgetLookChange: (rowId: Long, look: WidgetLook?) -> Unit = { _, _ -> },
    homeSignals: Flow<Boolean>,
    /** The page HOME returns to and the launcher opens on; null until settings have been read. */
    homePage: Int? = null,
    /** Pages the host wants shown (after adding/removing a page from the menu). */
    pageRequests: Flow<Int> = emptyFlow(),
    dragController: HomeDragController,
    widgetDragController: WidgetDragController,
    onAddWidget: (WidgetChoice, WidgetPlacement) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppMenu: (AppItem, IntOffset, Boolean) -> Unit,
    onMove: suspend (AppItem, Int, Int, Int) -> Boolean,
    onMoveFolder: suspend (Long, Int, Int, Int) -> Boolean,
    onMoveToDock: (AppItem, Int) -> Unit,
    onRemoveFromHome: (AppItem) -> Unit,
    onOpenFolder: (PlacedFolder) -> Unit,
    // A plain tap on a folder. Opens it by default; the host may launch its first app instead
    // (Settings ▸ Folders), in which case a still long-press is what opens the folder.
    onFolderTap: (PlacedFolder) -> Unit = onOpenFolder,
    // An app tapped right on a folder's tile (Settings ▸ Folders ▸ Open apps from the tile).
    onFolderAppTap: (AppItem) -> Unit = {},
    onLaunchShortcut: (PlacedShortcut) -> Unit,
    onRemoveShortcut: (Long) -> Unit,
    onCreateFolder: (target: AppItem, dropped: AppItem) -> Unit,
    onAddToFolder: (app: AppItem, folderId: Long) -> Unit,
    onCreateWidgetStack: (targetRowId: Long, droppedRowId: Long) -> Unit = { _, _ -> },
    onAddToWidgetStack: (stackId: Long, widgetRowId: Long) -> Unit = { _, _ -> },
    onRemoveFromWidgetStack: (stackId: Long, itemRowId: Long) -> Unit = { _, _ -> },
    onEmptyAreaMenu: (IntOffset, Boolean) -> Unit,
    // Two clean taps on empty space within the platform double-tap timeout. The caller decides what
    // (if anything) happens — Workspace only reports the gesture.
    onEmptyAreaDoubleTap: () -> Unit = {},
    // Removes a widget-like row from the grid; [appWidgetId] is null for a built-in widget (no host id).
    onRemoveWidget: (rowId: Long, appWidgetId: Int?) -> Unit = { _, _ -> },
    // Tap a restored-widget placeholder to re-bind it; long-press to discard the placeholder row.
    onRestorePendingWidget: (PendingWidget) -> Unit = {},
    onRemovePendingWidget: (rowId: Long) -> Unit = {},
    onSetWidgetBounds: suspend (rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean =
        { _, _, _, _, _, _ -> false },
    onReconfigureWidget: (appWidgetId: Int) -> Unit = {},
    // Persistent, page-independent host views (created once in HomeScreen, kept alive so their
    // AppWidgetHost listener survives page scrolling — otherwise a collection widget's deferred row
    // update is dropped). Keyed by appWidgetId. [widgetScrollableById] mirrors per-id scrollability.
    widgetViews: Map<Int, android.appwidget.AppWidgetHostView> = emptyMap(),
    widgetScrollableById: Map<Int, Boolean> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    // The drag and placement state lives in one holder, so the cells and gestures split out into
    // WorkspaceCells.kt / WorkspaceDrag.kt share it with this function.
    val ws = remember { WorkspaceDragState() }
    var gridSize by ws::gridSize
    var dragging by ws::dragging
    var dragPos by ws::dragPos
    var editingWidget by ws::editingWidget
    // The widget whose "Widget style" dialog is open (row id), and every saved look.
    var lookEditing by ws::lookEditing
    val widgetLooks = LocalWidgetLooks.current
    lookEditing?.let { id ->
        WidgetLookDialog(
            look = widgetLooks[id] ?: WidgetLook(),
            onChange = { onWidgetLookChange(id, it) },
            onDismiss = { lookEditing = null },
        )
    }
    // While the edit frame is open, the root swipe-up detector must yield (it bails on localGestureActive),
    // so a handle/scrim drag can never open the drawer. Reset when the frame closes.
    LaunchedEffect(editingWidget, widgetDragController.active) {
        dragController.localGestureActive = editingWidget != null || widgetDragController.active != null
    }
    var widgetOptimistic by ws::widgetOptimistic

    // Relocation of a folder or pinned shortcut, kept local: neither travels to the dock/drawer, so
    // they don't use the shared cross-surface controller — Workspace owns the gesture, floating
    // preview and drop, and moves them by their home_items row id (folder.id / shortcut.rowId).
    var draggingLocal by ws::draggingLocal
    var localMoving by ws::localMoving
    var localDragPos by ws::localDragPos
    // The entry being moved, shown optimistically at its new cell (by row id) until the flow catches up.
    var localOptimistic by ws::localOptimistic

    // The pager ALWAYS carries one extra trailing page (so an icon can be dragged onto a brand new
    // page). The count is kept stable — never toggled by [dragging] — because changing it at pickup
    // forced a pager relayout exactly as the drag began, making pickup stutter and miss moves. The
    // trailing page is hidden from the dots and the workspace snaps back off it when not dragging,
    // so it reads as a single page until an icon actually lands there.
    // The spare page exists only where it is wanted: with "new page when dragging" on, or in the book
    // layout as the blank right half of an odd last page. [lastPage] is the highest pager page;
    // [lastLeftPage] the highest page the pager may rest on (book layout: the left page of a pair).
    val sparePages = when {
        isDualPage -> pageCount % 2
        allowNewPage -> 1
        else -> 0
    }
    val lastPage = (pageCount + sparePages - 1).coerceAtLeast(0)
    val lastLeftPage = if (isDualPage) (lastPage / 2) * 2 else lastPage
    fun restPage(page: Int): Int = (if (isDualPage) page - page % 2 else page).coerceIn(0, lastLeftPage)
    val pagerState = rememberPagerState(initialPage = restPage(homePage ?: 0), pageCount = { lastPage + 1 })
    /** The page a step forward (+1) or back (-1) lands on; the book layout turns two pages at once. */
    // Page edge for flipping while dragging: a strip at each side that turns the page after a short
    // hold there (see the edge-flip effect below).
    val edgePx = with(density) { EDGE_FLIP_DP.dp.toPx() }
    val grid = WorkspaceGrid(columns, rows, pageCount, lastPage, lastLeftPage, isDualPage, locked, edgePx, pagerState, ws)
    fun stepPage(direction: Int): Int = grid.stepPage(direction)
    // For the long-lived swipe detector, which must see the page count of the latest composition.
    val currentStepPage by rememberUpdatedState(::stepPage)
    // Settings arrive a beat after the first frame: open on the home page once they are known.
    // Saved across recreation so a restored pager (rotation, locale change, process death) keeps
    // the page the user was on. Later home-page changes never move the pager — the page menu's
    // insert/remove rewrite the stored index while the user is on another page on purpose.
    var openedOnHome by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(homePage != null) {
        val target = homePage ?: return@LaunchedEffect
        if (openedOnHome) return@LaunchedEffect
        openedOnHome = true
        if (pagerState.currentPage != restPage(target)) pagerState.scrollToPage(restPage(target))
    }
    // The pager's own count is the live bound: this collector never restarts, so the pageCount
    // parameter it closed over would be the first composition's.
    LaunchedEffect(pageRequests) {
        pageRequests.collect { page -> pagerState.animateScrollToPage(page.coerceIn(0, pagerState.pageCount - 1)) }
    }
    // The cell the dragged icon would drop into — shown as a placeholder while dragging.
    var targetCell by ws::targetCell
    // The page [targetCell] is on (with two pages side by side it is not always the current one).
    var targetCellPage by ws::targetCellPage

    val placedApps = remember(entries) { entries.filterIsInstance<PlacedApp>() }
    val folders = remember(entries) { entries.filterIsInstance<PlacedFolder>() }
    val placedShortcuts = remember(entries) { entries.filterIsInstance<PlacedShortcut>() }
    val placedWidgets = remember(entries) { entries.filterIsInstance<PlacedWidget>() }
    // Restored-but-unbound widget placeholders (tap to set up). No optimistic/drag handling — they are
    // transient until the user binds (→ PlacedWidget) or removes them.
    val pendingWidgets = remember(entries) { entries.filterIsInstance<PendingWidget>() }
    val builtins = remember(entries) { entries.filterIsInstance<PlacedBuiltin>() }
    val stacks = remember(entries) { entries.filterIsInstance<PlacedWidgetStack>() }
    val effectiveWidgets = remember(placedWidgets, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) placedWidgets else placedWidgets.map { w ->
            if (w.rowId == opt.first) {
                w.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else w
        }
    }
    val effectiveBuiltins = remember(builtins, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) builtins else builtins.map { s ->
            if (s.rowId == opt.first) {
                s.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else s
        }
    }
    val effectiveStacks = remember(stacks, widgetOptimistic) {
        val opt = widgetOptimistic
        if (opt == null) stacks else stacks.map { s ->
            if (s.rowId == opt.first) {
                s.copy(page = opt.second.page, cellX = opt.second.cellX, cellY = opt.second.cellY,
                    spanX = opt.second.spanX, spanY = opt.second.spanY)
            } else s
        }
    }
    // clear the optimistic override once the DB flow reports the widget at its new bounds. Keyed on
    // the override too: a drop (or resize) back onto the SAME bounds writes nothing, so the flow never
    // emits and the override used to stay forever — pinning the widget to that cell on screen even
    // after a later add pushed its row elsewhere.
    LaunchedEffect(placedWidgets, builtins, stacks, widgetOptimistic) {
        val opt = widgetOptimistic ?: return@LaunchedEffect
        val landed = (placedWidgets.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) } +
            builtins.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) } +
            stacks.map { it.rowId to WidgetBounds(it.page, it.cellX, it.cellY, it.spanX, it.spanY) })
            .any { it == opt }
        if (landed) widgetOptimistic = null
    }

    // Optimistic placements (key -> page/cellX/cellY) applied on top of [placedApps] until the
    // database flow catches up, so an icon doesn't flash at its old cell for a frame on drop.
    var optimistic by ws::optimistic
    // Keys optimistically removed from home (dragged into the dock or a folder) — hidden until the
    // flow drops them, so the icon doesn't reappear at its old cell for a frame.
    var removedKeys by ws::removedKeys
    // Keyed on the overrides too (see the widget override above): an icon dropped back onto its own
    // cell writes nothing, so without this its override would never be released.
    LaunchedEffect(placedApps, optimistic) {
        if (optimistic.isNotEmpty()) {
            optimistic = optimistic.filterNot { (key, pos) ->
                placedApps.any {
                    it.app.key == key && it.page == pos.first &&
                        it.cellX == pos.second && it.cellY == pos.third
                }
            }
        }
        if (removedKeys.isNotEmpty()) {
            removedKeys = removedKeys.filterTo(mutableSetOf()) { rk -> placedApps.any { it.app.key == rk } }
        }
    }
    val effectiveApps = remember(placedApps, optimistic, removedKeys) {
        placedApps
            .filterNot { it.app.key in removedKeys }
            .map { p ->
                optimistic[p.app.key]?.let { (pg, x, y) -> p.copy(page = pg, cellX = x, cellY = y) } ?: p
            }
    }
    // Clear the local optimistic override once the DB flow reports the entry (folder or shortcut) at
    // its new cell.
    LaunchedEffect(folders, placedShortcuts) {
        val opt = localOptimistic ?: return@LaunchedEffect
        val (id, pos) = opt
        val landed = folders.any { it.id == id && it.page == pos.first && it.cellX == pos.second && it.cellY == pos.third } ||
            placedShortcuts.any { it.rowId == id && it.page == pos.first && it.cellX == pos.second && it.cellY == pos.third }
        if (landed) localOptimistic = null
    }
    val effectiveFolders = remember(folders, localOptimistic) {
        val opt = localOptimistic
        if (opt == null) folders else folders.map { f ->
            if (f.id == opt.first) f.copy(page = opt.second.first, cellX = opt.second.second, cellY = opt.second.third) else f
        }
    }
    val effectiveShortcuts = remember(placedShortcuts, localOptimistic) {
        val opt = localOptimistic
        if (opt == null) placedShortcuts else placedShortcuts.map { s ->
            if (s.rowId == opt.first) s.copy(page = opt.second.first, cellX = opt.second.second, cellY = opt.second.third) else s
        }
    }
    // Apps + folders + pinned shortcuts + widgets + widget stacks together — what's actually on the
    // grid (rendering + occupants). An entry type missing here is never drawn and never blocks a cell.
    val effectiveEntries: List<HomeEntry> = remember(effectiveApps, effectiveFolders, effectiveShortcuts, effectiveWidgets, pendingWidgets, effectiveBuiltins, effectiveStacks) {
        effectiveApps + effectiveFolders + effectiveShortcuts + effectiveWidgets + pendingWidgets + effectiveBuiltins + effectiveStacks
    }
    // The drag gesture's pointerInput block outlives recomposition (its keys don't include the entry
    // list), so it must read the *latest* placements through this state, not a stale closure capture.
    val latestEntriesState = rememberUpdatedState(effectiveEntries)
    val latestEntries by latestEntriesState
    val env = WorkspaceEnv(
        scope = scope,
        haptics = haptics,
        dragController = dragController,
        windowHeightPx = windowHeightPx,
        latestEntriesState = latestEntriesState,
        onAppClick = onAppClick,
        onAppMenu = onAppMenu,
        onMove = onMove,
        onMoveFolder = onMoveFolder,
        onMoveToDock = onMoveToDock,
        onRemoveFromHome = onRemoveFromHome,
        onCreateFolder = onCreateFolder,
        onAddToFolder = onAddToFolder,
    )
    // The empty-area detector below is a pointerInput(Unit) that lives as long as the page; read the
    // callbacks through rememberUpdatedState so a recomposition with a new lambda (a changed setting)
    // reaches it instead of the lambda captured at first composition.
    val currentEmptyAreaMenu by rememberUpdatedState(onEmptyAreaMenu)
    val currentEmptyAreaDoubleTap by rememberUpdatedState(onEmptyAreaDoubleTap)
    val currentWidgetDrag by rememberUpdatedState(widgetDragController)
    val currentFolderTap by rememberUpdatedState(onFolderTap)
    val currentFolderAppTap by rememberUpdatedState(onFolderAppTap)
    val currentOpenFolder by rememberUpdatedState(onOpenFolder)
    val currentLaunchShortcut by rememberUpdatedState(onLaunchShortcut)
    val currentRemoveShortcut by rememberUpdatedState(onRemoveShortcut)

    // Book layout (inner display): the pager shows two half-width pages side by side, so a page is
    // half the grid wide and a point in the right half belongs to the page after the current one.
    // "Grid x" below is measured from the left edge of the whole pager.
    fun pageWidthPx(): Float = grid.pageWidthPx()
    fun pageAtX(gridX: Float): Int = grid.pageAtX(gridX)
    fun xInPage(gridX: Float): Float = grid.xInPage(gridX)
    fun pageLeft(page: Int): Float = grid.pageLeft(page)
    val cellW = grid.cellW
    val cellH = grid.cellH
    // Icon/folder/shortcut size derived from the cell so 6–7 columns fit a narrow screen (a fixed
    // 52dp icon bled into neighbouring cells there) AND so a whole label line fits under it in a
    // short cell (7–8 rows on an enlarged display size). 52dp until the grid reports its real size.
    val labelBlock = labelBlockHeight(
        showLabel = showLabels,
        labelScale = LocalAppLabelScale.current,
        fontFactor = labelFontFactor(density.fontScale),
        lines = LocalAppLabelLines.current,
    )
    // The user's icon-size multiplier scales the preferred size; the cell still has the last word.
    val preferredIconSize = 52.dp * LocalIconSizeScale.current
    val cellIconSize = if (gridSize.width > 0 && gridSize.height > 0) {
        iconSizeForCell(with(density) { cellW.toDp() }, preferredIconSize, with(density) { cellH.toDp() }, labelBlock)
    } else {
        preferredIconSize
    }
    // Where an app icon sits in its cell, for the floating icon, the drop preview and the glide in.
    val iconPxNow = with(density) { cellIconSize.toPx() }
    val iconDyNow = -with(density) { labelBlock.toPx() } / 2f
    SideEffect {
        ws.iconPx = iconPxNow
        ws.iconDy = iconDyNow
    }
    // Edit home: an icon moves at once, so the book-layout page swipe must leave an icon touch alone.
    val editingNow by rememberUpdatedState(org.arkikeskus.launcher.feature.home.LocalHomeEdit.current?.editing == true)

    // Client-side mirror of rectFitsForRow over the rendered entries, for the live drag placeholder.
    fun rectFreeOnGrid(excludeRowId: Long, page: Int, x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        if (x < 0 || y < 0 || x + spanX > columns || y + spanY > rows) return false
        val occupied = HashSet<Triple<Int, Int, Int>>()
        for (e in effectiveEntries) {
            val (ex, ey) = when (e) {
                is PlacedWidget -> e.spanX to e.spanY
                is PendingWidget -> e.spanX to e.spanY
                is PlacedBuiltin -> e.spanX to e.spanY
                is PlacedWidgetStack -> e.spanX to e.spanY
                else -> 1 to 1
            }
            val erow = when (e) {
                is PlacedWidget -> e.rowId
                is PendingWidget -> e.rowId
                is PlacedBuiltin -> e.rowId
                is PlacedWidgetStack -> e.rowId
                else -> -2L
            }
            if (erow == excludeRowId) continue
            for (dx in 0 until ex) for (dy in 0 until ey) occupied += Triple(e.page, e.cellX + dx, e.cellY + dy)
        }
        for (dx in 0 until spanX) for (dy in 0 until spanY) if (Triple(page, x + dx, y + dy) in occupied) return false
        return true
    }

    val dropTargetCache = remember { DropTargetCache() }

    fun canPlaceWidget(p: WidgetPlacement, rowId: Long = Long.MIN_VALUE): Boolean =
        ReorderPlanner.planFit(latestEntries.widgetRects(), rowId, p.page, p.cellX, p.cellY,
            p.spanX, p.spanY, columns, rows) != null

    /**
     * The widget (or stack) that the dragged widget [draggedRowId] would be stacked onto if dropped at
     * [p]: the one it covers most, provided it covers MORE than half of the smaller of the two
     * footprints. A lighter overlap stays an ordinary move that pushes the neighbour aside, so
     * nudging a widget next to another one never stacks them by accident.
     */
    fun stackTargetFor(draggedRowId: Long, p: WidgetPlacement): HomeEntry? {
        var best: HomeEntry? = null
        var bestOverlap = 0
        for (e in latestEntries) {
            if (e.page != p.page) continue
            val footprint = when (e) {
                is PlacedWidget -> Triple(e.rowId, e.spanX, e.spanY)
                is PlacedBuiltin -> Triple(e.rowId, e.spanX, e.spanY)
                is PlacedWidgetStack -> Triple(e.rowId, e.spanX, e.spanY)
                else -> null
            } ?: continue
            val (rowId, sx, sy) = footprint
            if (rowId == draggedRowId) continue
            val overlapX = minOf(e.cellX + sx, p.cellX + p.spanX) - maxOf(e.cellX, p.cellX)
            val overlapY = minOf(e.cellY + sy, p.cellY + p.spanY) - maxOf(e.cellY, p.cellY)
            if (overlapX <= 0 || overlapY <= 0) continue
            val overlap = overlapX * overlapY
            if (overlap * 2 > minOf(sx * sy, p.spanX * p.spanY) && overlap > bestOverlap) {
                best = e
                bestOverlap = overlap
            }
        }
        return best
    }

    fun liftWidget(item: EditingItem, root: Offset, fraction: Offset, owner: Any) {
        // A second finger on another widget: the first drag keeps the controller (no snapshot, no buzz).
        if (widgetDragController.active != null) return
        // A stack has no content of its own: its drag preview is the first widget in it.
        val stackFront = stacks.firstOrNull { it.rowId == item.rowId }?.items?.firstOrNull()
        val previewWidgetId = item.appWidgetId ?: (stackFront as? PlacedWidget)?.appWidgetId
        val bitmap = previewWidgetId?.let { id ->
            widgetViews[id]?.let { view -> catching { view.drawToBitmap() }.getOrNull() }
        }
        val builtin = builtins.firstOrNull { it.rowId == item.rowId } ?: (stackFront as? PlacedBuiltin)
        val choice = builtin?.let { WidgetChoice.Builtin(it.type) } ?: previewWidgetId?.let { id ->
            catching { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }.getOrNull()
                ?.let { WidgetChoice.App(it) }
        }
        widgetDragController.start(
            WidgetDrag(item, choice,
                item.spanX, item.spanY, fraction, bitmap), root, owner,
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    SideEffect {
        widgetDragController.cellWidthDp = cellW / density.density
        widgetDragController.cellHeightDp = cellH / density.density
        widgetDragController.target = {
            val drag = widgetDragController.active
            if (drag == null || locked || pagerState.isScrollInProgress) null else {
                val pos = widgetDragController.position - dragController.gridBounds.topLeft
                val raw = widgetDropPlacement(pageAtX(pos.x), xInPage(pos.x), pos.y,
                    pageWidthPx(), gridSize.height.toFloat(), columns, rows,
                    drag.spanX, drag.spanY, drag.grabFraction.x, drag.grabFraction.y
                )
                val rowId = drag.item?.rowId
                // This runs for every frame of a drag, but the answer only changes when the finger
                // crosses into another cell (or the layout changes): the stack check and the
                // push-aside plan are worked out once per cell, not once per frame.
                val cache = dropTargetCache
                if (raw != cache.placement || rowId != cache.rowId || latestEntries !== cache.entries) {
                    cache.placement = raw
                    cache.rowId = rowId
                    cache.entries = latestEntries
                    cache.valid = raw != null && (
                        // Dropping onto another widget stacks the two, which needs no free cells at all.
                        (rowId != null && stackTargetFor(rowId, raw) != null) ||
                            canPlaceWidget(raw, rowId ?: Long.MIN_VALUE)
                        )
                }
                raw?.takeIf { cache.valid }
            }
        }
        widgetDragController.findSpace = { sx, sy ->
            val pages = listOf(pagerState.currentPage) + (0..lastPage).filter { it != pagerState.currentPage }
            pages.firstNotNullOfOrNull { page ->
                val candidates = (0..(rows - sy)).flatMap { y ->
                    (0..(columns - sx)).map { x -> WidgetPlacement(page, x, y, sx, sy) }
                }
                candidates.firstOrNull { rectFreeOnGrid(Long.MIN_VALUE, it.page, it.cellX, it.cellY, sx, sy) }
                    ?: candidates.firstOrNull { canPlaceWidget(it) }
            }
        }
        widgetDragController.keepsGestureLock = { editingWidget != null }
        // For the swipe-up-on-an-icon gesture: the app whose cell is under a root position.
        dragController.appAt = { p ->
            if (!dragController.isOverGrid(p)) null else {
                val (page, cellX, cellY) = dragController.cellAt(p)
                latestEntries.firstNotNullOfOrNull { e ->
                    (e as? PlacedApp)?.takeIf { it.page == page && it.cellX == cellX && it.cellY == cellY }?.app
                }
            }
        }
        widgetDragController.onStill = { drag ->
            drag.item?.let { editingWidget = it }
        }
        widgetDragController.onDrop = { drag, target ->
            val item = drag.item
            if (!locked && item != null && dragController.isOverRemove(widgetDragController.position)) {
                onRemoveWidget(item.rowId, item.appWidgetId)
                editingWidget = null
            } else if (!locked && target != null) {
                if (item != null) {
                    val occupant = stackTargetFor(item.rowId, target)
                    when (occupant) {
                        is PlacedWidget -> {
                            editingWidget = null
                            onCreateWidgetStack(occupant.rowId, item.rowId)
                        }
                        is PlacedBuiltin -> {
                            editingWidget = null
                            onCreateWidgetStack(occupant.rowId, item.rowId)
                        }
                        is PlacedWidgetStack -> {
                            editingWidget = null
                            onAddToWidgetStack(occupant.rowId, item.rowId)
                        }
                        else -> {
                            editingWidget = null
                            widgetOptimistic = item.rowId to WidgetBounds(target.page, target.cellX, target.cellY, target.spanX, target.spanY)
                            scope.launch {
                                if (onSetWidgetBounds(item.rowId, target.page, target.cellX, target.cellY, target.spanX, target.spanY)) {
                                    editingWidget = item.copy(page = target.page, cellX = target.cellX, cellY = target.cellY,
                                        spanX = target.spanX, spanY = target.spanY)
                                } else {
                                    widgetOptimistic = null
                                }
                            }
                        }
                    }
                } else {
                    drag.choice?.let { onAddWidget(it, target) }
                }
            }
        }
    }
    DisposableEffect(widgetDragController) {
        onDispose {
            widgetDragController.cancel()
            widgetDragController.target = null
            widgetDragController.findSpace = null
            widgetDragController.onDrop = null
            widgetDragController.onStill = null
            widgetDragController.keepsGestureLock = { false }
            dragController.appAt = null
        }
    }
    LaunchedEffect(widgetDragController.active, widgetDragController.moving, pageCount) {
        if (!widgetDragController.moving) return@LaunchedEffect
        snapshotFlow {
            val p = widgetDragController.position - dragController.gridBounds.topLeft
            when {
                p.y < 0 || p.y >= gridSize.height -> 0
                p.x < edgePx -> -1
                p.x > gridSize.width - edgePx -> 1
                else -> 0
            }
        }.collectLatest { direction ->
            if (direction == 0) return@collectLatest
            while (widgetDragController.moving) {
                delay(550)
                val next = stepPage(direction)
                if (next == pagerState.currentPage) break
                pagerState.animateScrollToPage(next)
            }
        }
    }

    // Shared drag for a local home entry (folder or pinned shortcut): long-press lifts; drag moves it
    // by [rowId] to a cell (swapping any occupant); a tap is [onTap]; a still long-press is
    // [onStillPress] (open folder / show shortcut menu). Mirrors the app drag but stays on the grid.
    fun Modifier.localEntryDrag(
        entry: HomeEntry,
        rowId: Long,
        removable: Boolean,
        onTap: () -> Unit,
        onStillPress: () -> Unit,
        onRemove: () -> Unit,
    ): Modifier = workspaceEntryDrag(ws, grid, env, entry, rowId, removable, onTap, onStillPress, onRemove)

    androidx.activity.compose.BackHandler(enabled = editingWidget != null || widgetDragController.active != null) {
        widgetDragController.cancel()
        editingWidget = null
    }

    // HOME button / home gesture: always leave widget edit mode, but snap to the first page only
    // when HOME was pressed while the launcher was already foreground (alreadyOnHome). Coming home
    // from an app keeps the page the app was launched from — the Pixel Launcher convention.
    val homeIndex = homePage ?: 0
    LaunchedEffect(homeSignals, pageCount, homeIndex, isDualPage) {
        // Book layout: "home" is the pair that holds the home page.
        val homeRest = if (isDualPage) homeIndex - homeIndex % 2 else homeIndex
        homeSignals.collect { alreadyOnHome ->
            widgetDragController.cancel()
            editingWidget = null
            if (alreadyOnHome && pagerState.currentPage != homeRest) pagerState.animateScrollToPage(homeRest)
        }
    }

    // Only retreat from the temporary trailing page. Pages explicitly added from the menu are
    // permanent even when empty, including pages beyond the last icon or widget.
    // A drawer/dock→home drag flips to an empty page ON PURPOSE so the app can be dropped there; the
    // retreat must yield to it (otherwise the page snaps back to the front under the finger — the
    // "it won't stay on the other page" bug). The in-home icon/local drags are already covered by
    // dragging/draggingLocal.
    val crossSurfaceDrag = dragController.moving &&
        (dragController.source == DragSource.Drawer || dragController.source == DragSource.Dock)
    // The settled page is followed inside the effects (not read here), so a page swipe doesn't
    // recompose the whole workspace.
    LaunchedEffect(pageCount, dragging, draggingLocal, crossSurfaceDrag, widgetDragController.active) {
        if (dragging != null || draggingLocal != null || crossSurfaceDrag || widgetDragController.active != null) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collectLatest { settledPage ->
            if (settledPage < pageCount) return@collectLatest
            // After a cross-surface drag ends, the dropped app reaches this page via the DB flow a beat
            // later — wait briefly before deciding the page is empty, so a valid drop isn't undone.
            // A page-count change cancels this wait: the trailing page may have become permanent.
            if (latestEntries.none { it.page == settledPage }) kotlinx.coroutines.delay(180)
            if (dragging == null && draggingLocal == null && widgetDragController.active == null) {
                val target = emptyPageReturnTarget(
                    settledPage = settledPage,
                    permanentPageCount = pageCount,
                    hasContent = latestEntries.any { it.page == settledPage },
                )
                if (target != null) pagerState.animateScrollToPage(target)
            }
        }
    }

    // Book layout: whatever moved the pager (home button, a drag, a page request), it comes to rest
    // on the left page of a pair.
    LaunchedEffect(isDualPage, pageCount, dragging, draggingLocal, crossSurfaceDrag, widgetDragController.active) {
        if (!isDualPage || dragging != null || draggingLocal != null || crossSurfaceDrag || widgetDragController.active != null) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collectLatest { settledPage ->
            // Pages turn in pairs (1|2, 3|4 …): never rest on 2|3.
            val rest = (settledPage - settledPage % 2).coerceIn(0, lastLeftPage)
            if (settledPage != rest) pagerState.animateScrollToPage(rest)
        }
    }
    // Publish the grid metrics so a dock→home drop can map the finger's root position to a cell.
    LaunchedEffect(columns, rows, isDualPage) {
        dragController.columns = columns
        dragController.rows = rows
        dragController.isDualPage = isDualPage
    }
    LaunchedEffect(pagerState, pageCount, isDualPage, lastPage) {
        // Allow the always-present trailing page (index == pageCount) so a drawer/dock drop can land on
        // a BRAND-NEW page. Clamping to pageCount-1 made cellAt() report the last existing page, so an
        // app dragged from the drawer onto a new page always saved to the front page instead.
        snapshotFlow { pagerState.currentPage }
            .collect { dragController.currentPage = it.coerceIn(0, lastPage) }
    }
    // The cell a drag would land in (page, x, y) — our own app drag and folder/shortcut moves use
    // [targetCell]; an app from the dock or the drawer is worked out from the finger. Derived, so
    // it changes only when the cell does, not with every finger move.
    val ghostTarget by remember(isDualPage) {
        derivedStateOf {
            val tc = targetCell
            when {
                draggingLocal != null && localMoving && tc != null ->
                    Triple(if (isDualPage) targetCellPage else pagerState.currentPage, tc.x, tc.y)
                dragging != null && dragController.moving && tc != null ->
                    if (dragController.isOverDock(dragController.rootPosition)) null
                    else Triple(if (isDualPage) targetCellPage else pagerState.currentPage, tc.x, tc.y)
                dragController.moving &&
                    (dragController.source == DragSource.Dock || dragController.source == DragSource.Drawer) &&
                    dragController.isOverGrid(dragController.rootPosition) -> {
                    val (cellPage, cx, cy) = dragController.cellAt(dragController.rootPosition)
                    Triple(if (isDualPage) cellPage else pagerState.currentPage, cx, cy)
                }
                else -> null
            }
        }
    }
    // The preview's top-left (page px) and page; -1 while there is none. It glides between cells
    // and appears in place (no glide in from the last drag or another page).
    val ghostPos = remember { androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter) }
    var ghostPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(cellW, cellH) {
        snapshotFlow { ghostTarget }.collectLatest { g ->
            if (g == null) {
                ghostPage = -1
                return@collectLatest
            }
            val to = Offset(g.second * cellW, g.third * cellH)
            if (ghostPage != g.first) {
                ghostPos.snapTo(to)
                ghostPage = g.first
            } else {
                ghostPos.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 1400f))
            }
        }
    }

    // Turning the page while something is dragged: hold it in the edge strip for a moment and the
    // page turns once; keep holding and it turns again after a pause. One place for every drag — an
    // app from home, a folder or shortcut, and an app coming from the dock or the drawer — instead of
    // turning the moment the finger touched a thin strip (and again straight after).
    LaunchedEffect(pagerState, pageCount, isDualPage, lastPage, edgePx) {
        snapshotFlow {
            val crossSurface = dragController.moving &&
                (dragController.source == DragSource.Drawer || dragController.source == DragSource.Dock)
            val p: Offset? = when {
                dragging != null && dragController.moving -> dragPos
                draggingLocal != null && localMoving -> localDragPos
                crossSurface -> dragController.rootPosition - dragController.gridBounds.topLeft
                else -> null
            }
            when {
                p == null || p.y < 0f || p.y > gridSize.height -> 0
                p.x > gridSize.width - edgePx -> 1
                p.x < edgePx -> -1
                else -> 0
            }
        }.collectLatest { direction ->
            if (direction == 0) return@collectLatest
            delay(EDGE_FLIP_HOLD_MS)
            while (true) {
                val next = currentStepPage(direction)
                if (next == pagerState.currentPage) break
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                pagerState.animateScrollToPage(next)
                delay(EDGE_FLIP_REPEAT_MS)
            }
        }
    }

    val overscrollPx by remember {
        derivedStateOf {
            if (!pageBounceEnabled || pagerState.pageCount <= 0) 0f
            else {
                val currentPage = pagerState.currentPage
                val offset = pagerState.currentPageOffsetFraction
                val totalPages = pagerState.pageCount
                when {
                    currentPage == 0 && offset < 0f -> {
                        (-offset * 160f).coerceAtMost(60f)
                    }
                    currentPage == totalPages - 1 && offset > 0f -> {
                        (-offset * 160f).coerceAtLeast(-60f)
                    }
                    else -> 0f
                }
            }
        }
    }

    val animatedOverscrollPx by animateFloatAsState(
        targetValue = overscrollPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "workspace_page_bounce",
    )

    LaunchedEffect(overscrollPx) {
        if (overscrollPx > 60f && pagerState.currentPage == 0 && !pagerState.isScrollInProgress) {
            when (minusOneScreen) {
                LauncherSettings.MINUS_ONE_GOOGLE -> {
                    catching {
                        context.startActivity(
                            android.content.Intent("com.google.android.googlequicksearchbox.TEXT_ASSIST")
                                .setPackage("com.google.android.googlequicksearchbox")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                LauncherSettings.MINUS_ONE_SMART_FEED -> {
                    catching {
                        context.startActivity(
                            android.content.Intent("com.motorola.smartfeed.action.START")
                                .setPackage("com.motorola.smartfeed")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    val cellDeps = WorkspaceCellDeps(
        widgetDragController = widgetDragController,
        widgetLooks = widgetLooks,
        widgetViews = widgetViews,
        widgetScrollableById = widgetScrollableById,
        liftWidget = ::liftWidget,
        showLabels = showLabels,
        labelColor = labelColor,
        cellIconSize = cellIconSize,
        badges = badges,
        badgeShowCount = badgeShowCount,
        badgeScale = badgeScale,
        glassBlurRadius = glassBlurRadius,
        glassDarkTint = glassDarkTint,
        // Read at call time: the cells' gestures outlive compositions.
        currentFolderTap = { currentFolderTap(it) },
        currentOpenFolder = { currentOpenFolder(it) },
        currentFolderAppTap = { currentFolderAppTap(it) },
        currentLaunchShortcut = { currentLaunchShortcut(it) },
        currentRemoveShortcut = { currentRemoveShortcut(it) },
        onRestorePendingWidget = onRestorePendingWidget,
        onRemovePendingWidget = onRemovePendingWidget,
        onRemoveWidget = onRemoveWidget,
        onAddToWidgetStack = onAddToWidgetStack,
        onRemoveFromWidgetStack = onRemoveFromWidgetStack,
    )
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isDualPage && dragging == null && draggingLocal == null && editingWidget == null && widgetDragController.active == null,
                // While dragging, keep every page composed so flipping to another page can't
                // dispose the dragged item's node (which would cancel the in-progress drag). Also kept
                // composed during a drawer/dock→home drag so its target page renders as pages flip.
                beyondViewportPageCount = if (
                    dragging != null || draggingLocal != null || widgetDragController.active != null ||
                    (dragController.moving &&
                        (dragController.source == DragSource.Drawer || dragController.source == DragSource.Dock))
                ) pageCount.coerceAtLeast(0) else 0,
                pageSize = if (isDualPage) HalfPageSize else PageSize.Fill,
                // The settle after a swipe follows the animation speed setting.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow * animationSpeed * animationSpeed,
                        visibilityThreshold = 1f,
                    ),
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = animatedOverscrollPx
                    }
                    .onSizeChanged { gridSize = it }
                    .onGloballyPositioned { dragController.gridBounds = it.boundsInRoot() }
                    // Book layout: a sideways swipe turns both pages at once. It is watched on the
                    // way down to the pages and left alone while anything is being dragged.
                    .pointerInput(isDualPage) {
                        if (!isDualPage) return@pointerInput
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            // Edit home: a touch on an app moves the app, not the pages.
                            if (editingNow && dragController.appAt?.invoke(dragController.gridBounds.topLeft + down.position) != null) {
                                return@awaitEachGesture
                            }
                            var total = Offset.Zero
                            var claimed = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                total += change.positionChange()
                                val busy = dragging != null || draggingLocal != null || editingWidget != null ||
                                    widgetDragController.active != null || dragController.moving || dragController.localGestureActive
                                if (busy) { claimed = false; break }
                                if (!claimed && abs(total.x) > slop && abs(total.x) > abs(total.y)) claimed = true
                                if (claimed) change.consume()
                            }
                            if (claimed && abs(total.x) > slop * 2f && !pagerState.isScrollInProgress) {
                                val target = currentStepPage(if (total.x < 0f) 1 else -1)
                                if (target != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }
                    },
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Just unfolded: the pages open out from the hinge (the left one from the
                            // right, the right one from the left), or a single page grows into place.
                            val unfold = unfoldProgress()
                            if (unfold < 1f) {
                                val rest = 1f - unfold
                                alpha = 0.35f + 0.65f * unfold
                                if (isDualPage) {
                                    val towardHinge = if (page == pagerState.currentPage) 1f else -1f
                                    translationX = towardHinge * rest * size.width * UNFOLD_SHIFT
                                    rotationY = -towardHinge * rest * UNFOLD_TILT_DEG
                                    cameraDistance = 12f * this.density
                                } else {
                                    scaleX = 1f - rest * UNFOLD_SHRINK
                                    scaleY = 1f - rest * UNFOLD_SHRINK
                                }
                            }
                            // Two pages are on screen at once in the book layout, so no per-page effect there.
                            if (!isDualPage && pageScrollEffect != LauncherSettings.PAGE_EFFECT_SLIDE) {
                                pageScrollTransform(
                                    pageScrollEffect,
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                                )
                            }
                        }
                        // Drop placeholder, drawn (not composed) so moving it across cells during a
                        // drag never triggers recomposition — keeps the drag smooth.
                        .drawBehind {
                            // The drop preview: a faint outline of the icon in the cell it would
                            // land in, gliding from cell to cell (see [ghostPos]).
                            if (ghostPage == page && cellW > 1f) {
                                val gp = ghostPos.value
                                val icon = ws.iconPx.takeIf { it > 0f } ?: 52.dp.toPx()
                                val topLeft = Offset(
                                    gp.x + (cellW - icon) / 2f,
                                    gp.y + (cellH - icon) / 2f + ws.iconDy,
                                )
                                val box = androidx.compose.ui.geometry.Size(icon, icon)
                                val corner = androidx.compose.ui.geometry.CornerRadius(icon * GHOST_CORNER)
                                drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft, box, corner)
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.7f), topLeft, box, corner,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                        // NOTE: the drawer/notifications swipe-up detector used to live here, on the
                        // page background, so it only caught swipes from empty space. It was hoisted to
                        // HomeScreen's root Box (see Modifier.pixelHomeSwipe) and runs in the Initial
                        // pointer pass, so a flick now wins the gesture even over icons, folders,
                        // shortcuts and the dock — matching Pixel Launcher. Only the still-long-press
                        // settings detector remains a page-background gesture.
                        .pointerInput(isDualPage) {
                            // Still long-press on empty space opens settings. Times out a touch
                            // later than the icon long-press, so an icon pickup (which consumes the
                            // pointer) always wins and suppresses this. The same detector also spots
                            // clean empty-area taps for double-tap-to-lock: nothing is consumed and
                            // nothing fires on a single tap, so every existing gesture is untouched.
                            var lastTapUpMs = 0L
                            var lastTapPos = Offset.Zero
                            val doubleTapSlopPx = 48.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                var resolved = false
                                var tapUpMs = -1L
                                val held = withTimeoutOrNull(
                                    viewConfiguration.longPressTimeoutMillis + 180L,
                                ) {
                                    while (!resolved) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed || change.isConsumed ||
                                            (change.position - down.position).getDistance() >
                                            viewConfiguration.touchSlop
                                        ) {
                                            // A clean finger-up (unconsumed, within slop) completed a tap.
                                            if (change != null && !change.pressed && !change.isConsumed &&
                                                (change.position - down.position).getDistance() <=
                                                viewConfiguration.touchSlop
                                            ) {
                                                tapUpMs = change.uptimeMillis
                                            }
                                            resolved = true
                                        }
                                    }
                                }
                                // Fired only on a still empty-area hold. If an icon picked up
                                // (dragging != null) the hold belonged to that icon, not settings.
                                // A lifted widget owns the hold too: its gesture only OBSERVES the press (the
                                // hosted view must keep its taps), so this detector still sees the still
                                // dwell — without the check the pickup haptic was followed by this menu.
                                // Also suppressed while a widget is in edit mode (editingWidget != null):
                                // a still dwell on the widget during a move/remove drag must not also
                                // open the home-options menu underneath the edit scrim.
                                if (held == null && !resolved && dragging == null && editingWidget == null && currentWidgetDrag.active == null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Anchor the options popup at the press point (root coords); flip it
                                    // above when the press is in the lower half of the screen.
                                    val anchorPt = dragController.gridBounds.topLeft + down.position + Offset(pageLeft(page), 0f)
                                    currentEmptyAreaMenu(
                                        IntOffset(anchorPt.x.roundToInt(), anchorPt.y.roundToInt()),
                                        anchorPt.y > windowHeightPx * 0.45f,
                                    )
                                }
                                if (tapUpMs >= 0 && dragging == null && editingWidget == null && currentWidgetDrag.active == null) {
                                    // Double-tap = previous tap's UP → this tap's DOWN within the
                                    // platform timeout, close enough together on screen.
                                    if (down.uptimeMillis - lastTapUpMs <= viewConfiguration.doubleTapTimeoutMillis &&
                                        (down.position - lastTapPos).getDistance() <= doubleTapSlopPx
                                    ) {
                                        lastTapUpMs = 0L
                                        currentEmptyAreaDoubleTap()
                                    } else {
                                        lastTapUpMs = tapUpMs
                                        lastTapPos = down.position
                                    }
                                }
                            }
                        },
                ) {
                    effectiveEntries.asSequence()
                        .filter { it.page == page }
                        .forEach { entry ->
                            key(entry.slotKey()) {
                            when (entry) {
                            is PlacedFolder -> WorkspaceFolderCell(entry, ws, grid, env, cellDeps)
                            is PlacedApp -> WorkspaceAppCell(
                                placed = entry,
                                ws = ws,
                                grid = grid,
                                env = env,
                                badges = badges,
                                badgeShowCount = badgeShowCount,
                                badgeScale = badgeScale,
                                showLabels = showLabels,
                                labelColor = labelColor,
                                iconSize = cellIconSize,
                            )
                            is PlacedShortcut -> WorkspaceShortcutCell(entry, ws, grid, env, cellDeps)
                            is PlacedWidget -> WorkspaceWidgetCell(entry, ws, grid, env, cellDeps)
                            is PendingWidget -> WorkspacePendingWidgetCell(entry, ws, grid, env, cellDeps)
                            is PlacedBuiltin -> WorkspaceBuiltinCell(entry, ws, grid, env, cellDeps)
                            is PlacedWidgetStack -> WorkspaceWidgetStackCell(entry, ws, grid, env, cellDeps)
                            }
                            }
                        }
                    val ew = editingWidget
                    if (ew != null && ew.page == page) {
                        WorkspaceWidgetEditFrame(
                            ew = ew,
                            ws = ws,
                            grid = grid,
                            env = env,
                            deps = cellDeps,
                            effectiveEntries = effectiveEntries,
                            rectFreeOnGrid = ::rectFreeOnGrid,
                            onSetWidgetBounds = onSetWidgetBounds,
                            onReconfigureWidget = onReconfigureWidget,
                        )
                    }
                }
            }

            val indicatorCount = if (isDualPage) (pageCount + 1) / 2 else pageCount
            if (showPageIndicator && indicatorCount > 1) {
                PageDots(
                    count = indicatorCount,
                    // Read inside the dots: a page swipe recomposes them, not the workspace.
                    current = { if (isDualPage) pagerState.currentPage / 2 else pagerState.currentPage },
                    home = if (isDualPage) homeIndex / 2 else homeIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        WidgetDragLayer(widgetDragController, dragController, cellW, cellH, pageLeft = ::pageLeft)
        // The floating dragged *app icon* is drawn by LauncherShell (above the workspace, dock and the
        // drawer) so it can travel across surfaces. A dragged folder/shortcut never leaves the grid, so
        // its floating preview is drawn here locally, following the finger ([localDragPos], grid coords).
        val dl = draggingLocal
        if (dl != null && localMoving) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (localDragPos.x - cellW / 2f).roundToInt(),
                            (localDragPos.y - cellH / 2f).roundToInt(),
                        )
                    }
                    .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                    .graphicsLayer {
                        alpha = 0.92f
                        scaleX = 1.1f
                        scaleY = 1.1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (dl) {
                    is PlacedFolder -> FolderIcon(
                        name = dl.name,
                        apps = dl.apps,
                        showLabel = showLabels,
                        badgeCount = dl.apps.sumOf { badges[it.badgeKey] ?: 0 },
                        badgeShowCount = badgeShowCount,
                        badgeScale = badgeScale,
                        labelColor = labelColor,
                        size = cellIconSize,
                        glassBlurRadius = glassBlurRadius,
                        glassDarkTint = glassDarkTint,
                    )
                    is PlacedShortcut -> ShortcutIconContent(dl, showLabels, labelColor, cellIconSize)
                    else -> Unit
                }
            }
        }
    }
}

/** The unfold animation: how far a page starts toward the hinge (of its width), its tilt, and a single page's shrink. */
private const val UNFOLD_SHIFT = 0.22f
private const val UNFOLD_TILT_DEG = 14f
private const val UNFOLD_SHRINK = 0.08f

/** The drop preview's corner, as a share of the icon size. */
private const val GHOST_CORNER = 0.3f
