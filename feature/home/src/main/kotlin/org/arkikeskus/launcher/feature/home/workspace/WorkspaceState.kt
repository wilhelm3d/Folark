package org.arkikeskus.launcher.feature.home.workspace

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.widgets.*
import org.arkikeskus.launcher.feature.home.taskbar.*

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.HomeDragController

/**
 * The home grid's drag and placement state, shared by [Workspace] and the cells and gestures split
 * out of it. Every field is snapshot state, so a read in a draw or gesture lambda is tracked.
 */
@Stable
internal class WorkspaceDragState {
    /** The grid's measured size (both pages in the book layout). */
    var gridSize by mutableStateOf(IntSize.Zero)

    /** The app being dragged (shared controller) and where the finger is, in grid coordinates. */
    var dragging by mutableStateOf<PlacedApp?>(null)
    var dragPos by mutableStateOf(Offset.Zero)

    /** A folder or pinned shortcut being moved on the grid (it never leaves home). */
    var draggingLocal by mutableStateOf<HomeEntry?>(null)
    var localMoving by mutableStateOf(false)
    var localDragPos by mutableStateOf(Offset.Zero)

    /** That entry, shown at its new cell (row id to page, x, y) until the database catches up. */
    var localOptimistic by mutableStateOf<Pair<Long, Triple<Int, Int, Int>>?>(null)

    /** From the finger to the dragged app icon's centre (px), set at pick-up. */
    var grab by mutableStateOf(Offset.Zero)

    /** An app icon's size and how far its centre sits above its cell's centre (the label under it), px. */
    var iconPx by mutableFloatStateOf(0f)
    var iconDy by mutableFloatStateOf(0f)

    /** A dropped app gliding into its cell (or back to its old one); null when none is. */
    var settle by mutableStateOf<Settle?>(null)

    /** The [Settle] a cell has already started gliding, so it is not drawn at the drop point again. */
    var settleStarted by mutableLongStateOf(0L)

    /** The cell a drag would drop into (the placeholder), and its page. */
    var targetCell by mutableStateOf<IntOffset?>(null)
    var targetCellPage by mutableIntStateOf(0)

    /** Apps shown at a new cell (key to page, x, y) until the database catches up. */
    var optimistic by mutableStateOf(emptyMap<String, Triple<Int, Int, Int>>())

    /** Apps taken off home (to the dock or a folder), hidden until the database drops them. */
    var removedKeys by mutableStateOf(emptySet<String>())

    /** The widget whose edit frame is open (resize, move, style, remove), if any. */
    var editingWidget by mutableStateOf<EditingItem?>(null)

    /** The widget (row id) whose "Widget style" dialog is open. */
    var lookEditing by mutableStateOf<Long?>(null)

    /** A widget shown at new bounds (row id to bounds) until the database catches up. */
    var widgetOptimistic by mutableStateOf<Pair<Long, WidgetBounds>?>(null)

    /** A widget being resized: row id to its frame in grid pixels, following the finger (its
     *  content is laid out at that size as it goes); null when no handle is held. */
    /** Where the last tap on a grid item landed (in the item's own cell), e.g. which app of a folder tile. */
    var lastTapLocal: Offset = Offset.Zero

    var widgetLive by mutableStateOf<Pair<Long, androidx.compose.ui.geometry.Rect>?>(null)
}

/**
 * Where things are on the home grid. Cell sizes and page positions are read live from [ws] and the
 * pager, so a gesture that outlives a composition still measures with the current grid.
 *
 * Book layout (inner display): the pager shows two half-width pages side by side, so a page is half
 * the grid wide and a point in the right half belongs to the page after the current one. "Grid x" is
 * measured from the left edge of the whole pager.
 */
@Stable
internal data class WorkspaceGrid(
    val columns: Int,
    val rows: Int,
    val pageCount: Int,
    /** The highest pager page, and the highest one it may rest on (book layout: a left page). */
    val lastPage: Int,
    val lastLeftPage: Int,
    val isDualPage: Boolean,
    val locked: Boolean,
    /** How close to the edge a dragged icon must come to flip the page, in px. */
    val edgePx: Float,
    val pagerState: PagerState,
    val ws: WorkspaceDragState,
) {
    fun pageWidthPx(): Float = if (isDualPage) ws.gridSize.width / 2f else ws.gridSize.width.toFloat()

    fun pageAtX(gridX: Float): Int =
        if (isDualPage && gridX >= pageWidthPx()) (pagerState.currentPage + 1).coerceAtMost(lastPage) else pagerState.currentPage

    fun xInPage(gridX: Float): Float = if (isDualPage && gridX >= pageWidthPx()) gridX - pageWidthPx() else gridX

    fun pageLeft(page: Int): Float = if (isDualPage) (page - pagerState.currentPage) * pageWidthPx() else 0f

    val cellW: Float get() = if (columns > 0 && ws.gridSize.width > 0) pageWidthPx() / columns else 1f
    val cellH: Float get() = if (rows > 0 && ws.gridSize.height > 0) ws.gridSize.height.toFloat() / rows else 1f

    /** The page a step forward (+1) or back (-1) lands on; the book layout turns two pages at once. */
    fun stepPage(direction: Int): Int =
        (pagerState.currentPage + direction * (if (isDualPage) 2 else 1)).coerceIn(0, lastLeftPage)
}

/** What the home grid's gestures act through: the host's callbacks and the shared drag. */
@Stable
internal data class WorkspaceEnv(
    val scope: CoroutineScope,
    val haptics: HapticFeedback,
    val dragController: HomeDragController,
    val windowHeightPx: Int,
    /** The latest placements: gestures outlive compositions, so they read them through this. */
    val latestEntriesState: State<List<HomeEntry>>,
    val onAppClick: (AppItem) -> Unit,
    val onAppMenu: (AppItem, IntOffset, Boolean) -> Unit,
    val onMove: suspend (AppItem, Int, Int, Int) -> Boolean,
    val onMoveFolder: suspend (Long, Int, Int, Int) -> Boolean,
    val onMoveToDock: (AppItem, Int) -> Unit,
    val onRemoveFromHome: (AppItem) -> Unit,
    val onCreateFolder: (target: AppItem, dropped: AppItem) -> Unit,
    val onAddToFolder: (app: AppItem, folderId: Long) -> Unit,
) {
    val latestEntries: List<HomeEntry> get() = latestEntriesState.value
}

/**
 * A dropped app icon on its way into a cell: [from] is where the floating icon's centre was at the
 * drop (grid px), [page]/[x]/[y] the cell it glides into, [token] (System.nanoTime at the drop)
 * tells one drop from the next.
 */
internal data class Settle(val key: String, val from: Offset, val page: Int, val x: Int, val y: Int, val token: Long)

/** A glide not started within this long (its page was not on screen) is dropped. */
internal const val SETTLE_STALE_NS = 1_000_000_000L

/** How long a dropped icon takes to glide into place. */
internal const val SETTLE_MS = 220

/** The edge strip that turns the page while an item is dragged, and how long to hold it there. */
internal const val EDGE_FLIP_DP = 40
internal const val EDGE_FLIP_HOLD_MS = 400L

/** Holding on at the edge turns again only after this pause (after the page has settled). */
internal const val EDGE_FLIP_REPEAT_MS = 700L

/** How far a finger may drift during the hold and still pick an icon up, in touch slops. */
internal const val HOLD_DRIFT_SLOPS = 2f
