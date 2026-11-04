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

// The local drag of a folder or pinned shortcut on the home grid (split out of Workspace.kt).

internal fun Modifier.workspaceEntryDrag(
    ws: WorkspaceDragState,
    grid: WorkspaceGrid,
    env: WorkspaceEnv,
    entry: HomeEntry,
    rowId: Long,
    removable: Boolean,
    onTap: () -> Unit,
    onStillPress: () -> Unit,
    onRemove: () -> Unit,
): Modifier = with(ws) { with(grid) { with(env) {
this@workspaceEntryDrag.pointerInput(entry, rowId, cellW, cellH, columns, rows, pageCount, lastPage, isDualPage, locked) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val slop = viewConfiguration.touchSlop
        var tapped = false
        var swiped = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null) { swiped = true; return@withTimeoutOrNull }
                c.consume()
                if (!c.pressed) { tapped = true; return@withTimeoutOrNull }
                if ((c.position - down.position).getDistance() > slop) { swiped = true; return@withTimeoutOrNull }
            }
        }
        if (tapped) { onTap(); return@awaitEachGesture }
        if (swiped) return@awaitEachGesture
        // Desktop locked: long-press does nothing (no move/remove); tap (above) still opens.
        if (locked) return@awaitEachGesture
        // PICK UP
        draggingLocal = entry
        localMoving = false
        // Claim the gesture for this local entry so the root swipe-up detector (which runs in the
        // Initial pass, before us) won't steal the first movement after this long-press.
        dragController.localGestureActive = true
        localDragPos = Offset(pageLeft(entry.page) + entry.cellX * cellW + down.position.x, entry.cellY * cellH + down.position.y)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            val completed = drag(down.id) { change ->
                val delta = change.positionChange()
                change.consume()
                localDragPos += delta
                if (!localMoving) {
                    localMoving = true
                    if (removable) dragController.localDragging = true
                }
                // Publish root coords so the remove zone can highlight + hit-test this local drag.
                if (removable) dragController.update(dragController.gridBounds.topLeft + localDragPos)
                if (!pagerState.isScrollInProgress) {
                    if (localDragPos.x > gridSize.width - edgePx && stepPage(1) != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(stepPage(1)) }
                    } else if (localDragPos.x < edgePx && pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(stepPage(-1)) }
                    }
                }
                val cx = (xInPage(localDragPos.x) / cellW).toInt().coerceIn(0, columns - 1)
                val cy = (localDragPos.y / cellH).toInt().coerceIn(0, rows - 1)
                val nc = IntOffset(cx, cy)
                if (nc != targetCell) targetCell = nc
                targetCellPage = pageAtX(localDragPos.x)
            }
            targetCell = null
            val rootPos = dragController.gridBounds.topLeft + localDragPos
            if (completed && localMoving && removable && dragController.isOverRemove(rootPos)) {
                onRemove()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } else if (completed && localMoving) {
                val tx = (xInPage(localDragPos.x) / cellW).toInt().coerceIn(0, columns - 1)
                val ty = (localDragPos.y / cellH).toInt().coerceIn(0, rows - 1)
                val targetPage = pageAtX(localDragPos.x)
                if (targetPage != entry.page || tx != entry.cellX || ty != entry.cellY) {
                    localOptimistic = rowId to Triple(targetPage, tx, ty)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        if (!onMoveFolder(rowId, targetPage, tx, ty)) localOptimistic = null
                    }
                }
            } else if (completed) {
                onStillPress()
            }
        } finally {
            // Reset even on cancellation (node disposed / pointerInput restarted), so a stuck flag
            // can never leave the root swipe detector permanently disabled.
            dragController.localDragging = false
            dragController.localGestureActive = false
            draggingLocal = null
            localMoving = false
        }
    }
}
} } }
