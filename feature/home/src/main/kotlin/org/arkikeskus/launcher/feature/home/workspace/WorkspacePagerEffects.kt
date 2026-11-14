package org.arkikeskus.launcher.feature.home.workspace

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.arkikeskus.launcher.feature.home.FolderIcon
import org.arkikeskus.launcher.feature.home.HomeEntry
import org.arkikeskus.launcher.feature.home.PlacedFolder
import org.arkikeskus.launcher.feature.home.PlacedShortcut
import org.arkikeskus.launcher.feature.home.ShortcutIconContent
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.catching

// What the pager does on its own, apart from the cells: pages returning from the spare one, resting
// on a pair's left page, the bounce at the ends, and the folder or shortcut floating under the
// finger (split out of Workspace.kt).

/**
 * Only retreats from the temporary trailing page. Pages explicitly added from the menu are permanent
 * even when empty, including pages beyond the last icon or widget. A drawer/dock→home drag flips to
 * an empty page ON PURPOSE so the app can be dropped there; the effect that calls this yields to it
 * (otherwise the page snaps back to the front under the finger — the "it won't stay on the other
 * page" bug). The in-home icon/local drags are already covered by the drag state. The settled page
 * is followed in here (not read by the caller), so a page swipe doesn't recompose the whole workspace.
 */
internal suspend fun leaveEmptyTrailingPage(
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    widgetDragController: WidgetDragController,
) {
    val ws = grid.ws
    snapshotFlow { grid.pagerState.settledPage }.collectLatest { settledPage ->
        if (settledPage < grid.pageCount) return@collectLatest
        // After a cross-surface drag ends, the dropped app reaches this page via the DB flow a beat
        // later — wait briefly before deciding the page is empty, so a valid drop isn't undone.
        // A page-count change cancels this wait: the trailing page may have become permanent.
        if (env.latestEntries.none { it.page == settledPage }) delay(180)
        if (ws.dragging == null && ws.draggingLocal == null && widgetDragController.active == null) {
            val target = emptyPageReturnTarget(
                settledPage = settledPage,
                permanentPageCount = grid.pageCount,
                hasContent = env.latestEntries.any { it.page == settledPage },
            )
            if (target != null) grid.pagerState.animateScrollToPage(target)
        }
    }
}

/**
 * Book layout: whatever moved the pager (home button, a drag, a page request), it comes to rest on
 * the left page of a pair (1|2, 3|4 …): never on 2|3.
 */
internal suspend fun restOnLeftPageOfPair(grid: WorkspaceGrid) {
    snapshotFlow { grid.pagerState.settledPage }.collectLatest { settledPage ->
        val rest = (settledPage - settledPage % 2).coerceIn(0, grid.lastLeftPage)
        if (settledPage != rest) grid.pagerState.animateScrollToPage(rest)
    }
}

/**
 * How far the first or last page is pulled past its edge, in px: pulled right past the first page
 * is positive, left past the last negative; 0 in between, or when the bounce is [enabled] off.
 */
internal fun pageOverscrollPx(pagerState: PagerState, enabled: Boolean): Float {
    if (!enabled || pagerState.pageCount <= 0) return 0f
    val currentPage = pagerState.currentPage
    val offset = pagerState.currentPageOffsetFraction
    val totalPages = pagerState.pageCount
    return when {
        currentPage == 0 && offset < 0f -> (-offset * 160f).coerceAtMost(60f)
        currentPage == totalPages - 1 && offset > 0f -> (-offset * 160f).coerceAtLeast(-60f)
        else -> 0f
    }
}

/** Pulling the first page far enough opens the screen to the left of home (Settings ▸ Home ▸ minus-one screen). */
internal fun openMinusOneScreen(context: Context, minusOneScreen: String) {
    when (minusOneScreen) {
        LauncherSettings.MINUS_ONE_GOOGLE -> {
            catching {
                context.startActivity(
                    Intent("com.google.android.googlequicksearchbox.TEXT_ASSIST")
                        .setPackage("com.google.android.googlequicksearchbox")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        LauncherSettings.MINUS_ONE_SMART_FEED -> {
            catching {
                context.startActivity(
                    Intent("com.motorola.smartfeed.action.START")
                        .setPackage("com.motorola.smartfeed")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

/**
 * The folder or pinned shortcut [dragged] floating under the finger. It never leaves the grid, so
 * unlike a dragged app icon (drawn by LauncherShell, above the workspace, dock and drawer, so it
 * can travel across surfaces) it is drawn here, following the finger (grid coordinates).
 */
@Composable
internal fun LocalDragPreview(dragged: HomeEntry, ws: WorkspaceDragState, cellW: Float, cellH: Float, deps: WorkspaceCellDeps) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (ws.localDragPos.x - cellW / 2f).roundToInt(),
                    (ws.localDragPos.y - cellH / 2f).roundToInt(),
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
        when (dragged) {
            is PlacedFolder -> FolderIcon(
                name = dragged.name,
                apps = dragged.apps,
                showLabel = deps.showLabels,
                badgeCount = dragged.apps.sumOf { deps.badges[it.badgeKey] ?: 0 },
                badgeShowCount = deps.badgeShowCount,
                badgeScale = deps.badgeScale,
                labelColor = deps.labelColor,
                size = deps.cellIconSize,
                glassBlurRadius = deps.glassBlurRadius,
                glassDarkTint = deps.glassDarkTint,
            )
            is PlacedShortcut -> ShortcutIconContent(dragged, deps.showLabels, deps.labelColor, deps.cellIconSize)
            else -> Unit
        }
    }
}
