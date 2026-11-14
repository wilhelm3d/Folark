package org.arkikeskus.launcher.feature.home.workspace

import org.arkikeskus.launcher.feature.home.HomeEntry
import org.arkikeskus.launcher.feature.home.LocalWidgetLooks
import org.arkikeskus.launcher.feature.home.PendingWidget
import org.arkikeskus.launcher.feature.home.PlacedApp
import org.arkikeskus.launcher.feature.home.PlacedBuiltin
import org.arkikeskus.launcher.feature.home.PlacedFolder
import org.arkikeskus.launcher.feature.home.PlacedShortcut
import org.arkikeskus.launcher.feature.home.PlacedWidget
import org.arkikeskus.launcher.feature.home.PlacedWidgetStack
import org.arkikeskus.launcher.feature.home.WidgetLook
import org.arkikeskus.launcher.feature.home.WidgetLookDialog
import org.arkikeskus.launcher.feature.home.pageScrollTransform
import org.arkikeskus.launcher.feature.home.widgets.BadgeCounts
import org.arkikeskus.launcher.feature.home.widgets.HalfPageSize
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.iconSizeForCell
import org.arkikeskus.launcher.ui.component.labelBlockHeight
import org.arkikeskus.launcher.ui.component.labelFontFactor

/**
 * The paged home workspace: a [HorizontalPager] of cell grids with a floating drag overlay.
 *
 * Gestures are separated by location so they never fight each other:
 * - **Icon** (child node): tap launches; long-press lifts the icon (haptic tick) and the drag
 *   consumes the pointer immediately, so neither the pager nor the page swipes can steal it. Drop
 *   on a cell to move it (a second tick); drop without moving opens [WorkspaceActions.onAppMenu]; drag to the screen
 *   edge flips the page.
 * - **Page background** (parent node, only reached when no icon is under the finger): a still
 *   long-press opens settings. (The drawer/notifications swipe-up was hoisted to HomeScreen's root —
 *   see Modifier.pixelHomeSwipe — so it wins over icons and the dock too.)
 * - **Pager**: horizontal swipes change pages (disabled while an icon is being dragged).
 *
 * [homeSignals] (HOME pressed / home gesture) exits widget edit mode; when the press happened while
 * the launcher was already foreground (the Boolean payload) it also scrolls back to the first page.
 *
 * The work is shared out: the cells are in WorkspaceCells.kt and WorkspaceItemCells.kt, the gestures
 * and the page's look in WorkspacePage.kt, the widget drag in WorkspaceWidgetDrag.kt, the entries
 * with their pending moves in WorkspaceEntries.kt, and the pager's own behaviour in
 * WorkspacePagerEffects.kt. This function wires them together.
 */
@Composable
fun Workspace(
    pageCount: Int,
    entries: List<HomeEntry>,
    badges: BadgeCounts,
    settings: WorkspaceSettings,
    actions: WorkspaceActions,
    homeSignals: Flow<Boolean>,
    dragController: HomeDragController,
    widgetDragController: WidgetDragController,
    /** The page HOME returns to and the launcher opens on; null until settings have been read. */
    homePage: Int? = null,
    /** Pages the host wants shown (after adding/removing a page from the menu). */
    pageRequests: Flow<Int> = emptyFlow(),
    /** How far the unfold animation is (0 → 1; 1 = at rest). Read while drawing, so it never recomposes. */
    unfoldProgress: () -> Float = { 1f },
    // Persistent, page-independent host views (created once in HomeScreen, kept alive so their
    // AppWidgetHost listener survives page scrolling — otherwise a collection widget's deferred row
    // update is dropped). Keyed by appWidgetId. [widgetScrollableById] mirrors per-id scrollability.
    widgetViews: Map<Int, android.appwidget.AppWidgetHostView> = emptyMap(),
    widgetScrollableById: Map<Int, Boolean> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    // Book layout (two half-width pages side by side): asked all through this function.
    val isDualPage = settings.isDualPage
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    // The drag and placement state lives in one holder, so the cells and gestures split out into
    // WorkspaceCells.kt / WorkspaceDrag.kt share it with this function.
    val ws = remember { WorkspaceDragState() }
    var gridSize by ws::gridSize
    val dragging by ws::dragging
    // A folder or pinned shortcut being moved: it never travels to the dock/drawer, so it doesn't use
    // the shared cross-surface controller — Workspace owns the gesture, floating preview and drop.
    val draggingLocal by ws::draggingLocal
    val localMoving by ws::localMoving
    var editingWidget by ws::editingWidget
    // The widget whose "Widget style" dialog is open (row id), and every saved look.
    var lookEditing by ws::lookEditing
    val widgetLooks = LocalWidgetLooks.current
    lookEditing?.let { id ->
        WidgetLookDialog(
            look = widgetLooks[id] ?: WidgetLook(),
            onChange = { actions.onWidgetLookChange(id, it) },
            onDismiss = { lookEditing = null },
        )
    }
    // While the edit frame is open, the root swipe-up detector must yield (it bails on localGestureActive),
    // so a handle/scrim drag can never open the drawer. Reset when the frame closes.
    LaunchedEffect(editingWidget, widgetDragController.active) {
        dragController.localGestureActive = editingWidget != null || widgetDragController.active != null
    }

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
        settings.allowNewPage -> 1
        else -> 0
    }
    val lastPage = (pageCount + sparePages - 1).coerceAtLeast(0)
    val lastLeftPage = if (isDualPage) (lastPage / 2) * 2 else lastPage
    fun restPage(page: Int): Int = (if (isDualPage) page - page % 2 else page).coerceIn(0, lastLeftPage)
    val pagerState = rememberPagerState(initialPage = restPage(homePage ?: 0), pageCount = { lastPage + 1 })
    // Page edge for flipping while dragging: a strip at each side that turns the page after a short
    // hold there (see the edge-flip effect below).
    val edgePx = with(density) { EDGE_FLIP_DP.dp.toPx() }
    val grid = WorkspaceGrid(
        settings.columns, settings.rows, pageCount, lastPage, lastLeftPage, isDualPage, settings.locked, edgePx, pagerState, ws,
    )
    // For the long-lived swipe detectors, which must see the page count of the latest composition.
    val currentStepPage by rememberUpdatedState(grid::stepPage)
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

    // What is on the grid, with the drag's pending moves applied (see WorkspaceEntries.kt).
    val onGrid = rememberWorkspaceEntries(entries, ws)
    val effectiveEntries = onGrid.all
    // The drag gesture's pointerInput block outlives recomposition (its keys don't include the entry
    // list), so it must read the *latest* placements through this state, not a stale closure capture.
    val latestEntriesState = rememberUpdatedState(effectiveEntries)
    val env = WorkspaceEnv(
        scope = scope,
        haptics = haptics,
        dragController = dragController,
        windowHeightPx = windowHeightPx,
        latestEntriesState = latestEntriesState,
        onAppClick = actions.onAppClick,
        onAppMenu = actions.onAppMenu,
        onMove = actions.onMove,
        onMoveFolder = actions.onMoveFolder,
        onMoveToDock = actions.onMoveToDock,
        onRemoveFromHome = actions.onRemoveFromHome,
        onCreateFolder = actions.onCreateFolder,
        onAddToFolder = actions.onAddToFolder,
    )
    // The empty-area detector below is a pointerInput(Unit) that lives as long as the page; read the
    // callbacks through rememberUpdatedState so a recomposition with a new lambda (a changed setting)
    // reaches it instead of the lambda captured at first composition.
    val currentEmptyAreaMenu by rememberUpdatedState(actions.onEmptyAreaMenu)
    val currentEmptyAreaDoubleTap by rememberUpdatedState(actions.onEmptyAreaDoubleTap)
    val currentWidgetDrag by rememberUpdatedState(widgetDragController)
    val currentFolderTap by rememberUpdatedState(actions.onFolderTap)
    val currentFolderAppTap by rememberUpdatedState(actions.onFolderAppTap)
    val currentOpenFolder by rememberUpdatedState(actions.onOpenFolder)
    val currentLaunchShortcut by rememberUpdatedState(actions.onLaunchShortcut)
    val currentRemoveShortcut by rememberUpdatedState(actions.onRemoveShortcut)

    // Book layout (inner display): the pager shows two half-width pages side by side, so a page is
    // half the grid wide and a point in the right half belongs to the page after the current one
    // (see WorkspaceGrid).
    val cellW = grid.cellW
    val cellH = grid.cellH
    // Icon/folder/shortcut size derived from the cell so 6–7 columns fit a narrow screen (a fixed
    // 52dp icon bled into neighbouring cells there) AND so a whole label line fits under it in a
    // short cell (7–8 rows on an enlarged display size). 52dp until the grid reports its real size.
    val labelBlock = labelBlockHeight(
        showLabel = settings.showLabels,
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

    val dropTargetCache = remember { DropTargetCache() }
    SideEffect {
        installWidgetDragHandlers(
            widgetDragController = widgetDragController,
            dragController = dragController,
            grid = grid,
            env = env,
            density = density,
            cellW = cellW,
            cellH = cellH,
            entries = effectiveEntries,
            dropTargetCache = dropTargetCache,
            onAddWidget = actions.onAddWidget,
            onRemoveWidget = actions.onRemoveWidget,
            onCreateWidgetStack = actions.onCreateWidgetStack,
            onAddToWidgetStack = actions.onAddToWidgetStack,
            onSetWidgetBounds = actions.onSetWidgetBounds,
        )
    }
    DisposableEffect(widgetDragController) {
        onDispose { clearWidgetDragHandlers(widgetDragController, dragController) }
    }
    LaunchedEffect(widgetDragController.active, widgetDragController.moving, pageCount) {
        turnPagesForWidgetDrag(widgetDragController, dragController, grid)
    }

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

    // Only retreat from the temporary trailing page (see leaveEmptyTrailingPage); yield to a
    // drawer/dock→home drag, which flips to an empty page on purpose.
    val crossSurfaceDrag = dragController.moving &&
        (dragController.source == DragSource.Drawer || dragController.source == DragSource.Dock)
    LaunchedEffect(pageCount, dragging, draggingLocal, crossSurfaceDrag, widgetDragController.active) {
        if (dragging != null || draggingLocal != null || crossSurfaceDrag || widgetDragController.active != null) return@LaunchedEffect
        leaveEmptyTrailingPage(grid, env, widgetDragController)
    }

    // Book layout: whatever moved the pager (home button, a drag, a page request), it comes to rest
    // on the left page of a pair.
    LaunchedEffect(isDualPage, pageCount, dragging, draggingLocal, crossSurfaceDrag, widgetDragController.active) {
        if (!isDualPage || dragging != null || draggingLocal != null || crossSurfaceDrag || widgetDragController.active != null) return@LaunchedEffect
        restOnLeftPageOfPair(grid)
    }
    // Publish the grid metrics so a dock→home drop can map the finger's root position to a cell.
    LaunchedEffect(settings.columns, settings.rows, isDualPage) {
        dragController.columns = settings.columns
        dragController.rows = settings.rows
        dragController.isDualPage = isDualPage
    }
    LaunchedEffect(pagerState, pageCount, isDualPage, lastPage) {
        // Allow the always-present trailing page (index == pageCount) so a drawer/dock drop can land on
        // a BRAND-NEW page. Clamping to pageCount-1 made cellAt() report the last existing page, so an
        // app dragged from the drawer onto a new page always saved to the front page instead.
        snapshotFlow { pagerState.currentPage }
            .collect { dragController.currentPage = it.coerceIn(0, lastPage) }
    }
    // The preview of the cell a drag would land in.
    val ghost = rememberDropGhost(grid, dragController, cellW, cellH)

    // Turning the page while something is dragged: hold it in the edge strip for a moment and the
    // page turns once; keep holding and it turns again after a pause.
    LaunchedEffect(pagerState, pageCount, isDualPage, lastPage, edgePx) {
        turnPagesForItemDrag(grid, dragController, haptics) { direction -> currentStepPage(direction) }
    }

    // Keyed on the setting too: the bounce follows Settings the moment it is changed.
    val overscrollPx by remember(settings.pageBounceEnabled) {
        derivedStateOf { pageOverscrollPx(pagerState, settings.pageBounceEnabled) }
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
            openMinusOneScreen(context, settings.minusOneScreen)
        }
    }

    val cellDeps = WorkspaceCellDeps(
        widgetDragController = widgetDragController,
        widgetLooks = widgetLooks,
        widgetViews = widgetViews,
        widgetScrollableById = widgetScrollableById,
        liftWidget = { item, root, fraction, owner ->
            startWidgetLift(context, haptics, widgetDragController, widgetViews, onGrid, item, root, fraction, owner)
        },
        showLabels = settings.showLabels,
        labelColor = settings.labelColor,
        cellIconSize = cellIconSize,
        badges = badges,
        badgeShowCount = settings.badgeShowCount,
        badgeScale = settings.badgeScale,
        glassBlurRadius = settings.glassBlurRadius,
        glassDarkTint = settings.glassDarkTint,
        // Read at call time: the cells' gestures outlive compositions.
        currentFolderTap = { currentFolderTap(it) },
        currentOpenFolder = { currentOpenFolder(it) },
        currentFolderAppTap = { currentFolderAppTap(it) },
        currentLaunchShortcut = { currentLaunchShortcut(it) },
        currentRemoveShortcut = { currentRemoveShortcut(it) },
        onRestorePendingWidget = actions.onRestorePendingWidget,
        onRemovePendingWidget = actions.onRemovePendingWidget,
        onRemoveWidget = actions.onRemoveWidget,
        onAddToWidgetStack = actions.onAddToWidgetStack,
        onRemoveFromWidgetStack = actions.onRemoveFromWidgetStack,
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
                        stiffness = Spring.StiffnessMediumLow * settings.animationSpeed * settings.animationSpeed,
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
                        if (isDualPage) {
                            watchBookSwipe(grid, env, widgetDragController, { editingNow }) { currentStepPage(it) }
                        }
                    },
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Just unfolded: the pages open out from the hinge, or a single page grows
                            // into place.
                            val unfold = unfoldProgress()
                            if (unfold < 1f) openOutFromHinge(unfold, isDualPage, page, pagerState)
                            // Two pages are on screen at once in the book layout, so no per-page effect there.
                            if (!isDualPage && settings.pageScrollEffect != LauncherSettings.PAGE_EFFECT_SLIDE) {
                                pageScrollTransform(
                                    settings.pageScrollEffect,
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                                )
                            }
                        }
                        // Drop placeholder, drawn (not composed) so moving it across cells during a
                        // drag never triggers recomposition — keeps the drag smooth.
                        .drawBehind {
                            // The drop preview: a faint outline of the icon in the cell it would
                            // land in, gliding from cell to cell (see [rememberDropGhost]).
                            if (ghost.page == page && cellW > 1f) {
                                drawDropGhost(ghost.pos.value, cellW, cellH, ws.iconPx, ws.iconDy)
                            }
                        }
                        // NOTE: the drawer/notifications swipe-up detector used to live here, on the
                        // page background, so it only caught swipes from empty space. It was hoisted to
                        // HomeScreen's root Box (see Modifier.pixelHomeSwipe) and runs in the Initial
                        // pointer pass, so a flick now wins the gesture even over icons, folders,
                        // shortcuts and the dock — matching Pixel Launcher. Only the still-long-press
                        // settings detector remains a page-background gesture.
                        .pointerInput(isDualPage) {
                            watchEmptyArea(
                                page = page,
                                grid = grid,
                                env = env,
                                isWidgetDragActive = { currentWidgetDrag.active != null },
                                onMenu = { anchor, above -> currentEmptyAreaMenu(anchor, above) },
                                onDoubleTap = { currentEmptyAreaDoubleTap() },
                            )
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
                                badgeShowCount = settings.badgeShowCount,
                                badgeScale = settings.badgeScale,
                                showLabels = settings.showLabels,
                                labelColor = settings.labelColor,
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
                            rectFreeOnGrid = { excludeRowId, onPage, x, y, spanX, spanY ->
                                isRectFree(effectiveEntries, settings.columns, settings.rows, excludeRowId, onPage, x, y, spanX, spanY)
                            },
                            onSetWidgetBounds = actions.onSetWidgetBounds,
                            onReconfigureWidget = actions.onReconfigureWidget,
                        )
                    }
                }
            }

            val indicatorCount = if (isDualPage) (pageCount + 1) / 2 else pageCount
            if (settings.showPageIndicator && indicatorCount > 1) {
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
        WidgetDragLayer(widgetDragController, dragController, cellW, cellH, pageLeft = grid::pageLeft)
        // The floating dragged *app icon* is drawn by LauncherShell (above the workspace, dock and the
        // drawer) so it can travel across surfaces. A dragged folder/shortcut never leaves the grid, so
        // its floating preview is drawn here locally, following the finger.
        val dl = draggingLocal
        if (dl != null && localMoving) LocalDragPreview(dl, ws, cellW, cellH, cellDeps)
    }
}
