package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.ui.LauncherIcons
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.roundToInt

// The widget edit overlay: resize handles and its edit bar (split out of Workspace.kt).

/** Launcher3-style widget edit frame: a touch-consuming scrim (so the resize gesture can't leak into
 *  the drawer swipe-up or page scroll), a body-drag layer (move within the page, or drop on the top
 *  Remove pill to delete), a lightly tinted frame with bar handles on the resizable edges and dots on
 *  the corners, and one compact action bar (size, widget settings, remove) beside the widget. The
 *  widget's own content stays visible throughout. */
@Composable
internal fun WidgetEditOverlay(
    widget: EditingItem,
    widgetDragController: WidgetDragController,
    onLift: (EditingItem, Offset, Offset, Any) -> Unit,
    commitScope: kotlinx.coroutines.CoroutineScope,
    onCancelPreview: () -> Unit,
    range: WidgetResizeRange?,
    reconfigurable: Boolean,
    cellW: Float,
    cellH: Float,
    columns: Int,
    rows: Int,
    density: androidx.compose.ui.unit.Density,
    canFit: (x: Int, y: Int, spanX: Int, spanY: Int) -> Boolean,
    onSetBounds: suspend (x: Int, y: Int, spanX: Int, spanY: Int) -> Boolean,
    onPreviewBounds: (x: Int, y: Int, spanX: Int, spanY: Int) -> Unit,
    onReconfigure: () -> Unit,
    onStyle: () -> Unit,
    onDeleteWidget: () -> Unit,
    onExit: () -> Unit,
    canMovePrev: Boolean,
    canMoveNext: Boolean,
    onMoveToPage: (targetPage: Int, spanX: Int, spanY: Int) -> Unit,
) {
    var cx by remember(widget.rowId) { mutableIntStateOf(widget.cellX) }
    var cy by remember(widget.rowId) { mutableIntStateOf(widget.cellY) }
    var sx by remember(widget.rowId) { mutableIntStateOf(widget.spanX) }
    var sy by remember(widget.rowId) { mutableIntStateOf(widget.spanY) }
    var okX by remember(widget.rowId) { mutableIntStateOf(widget.cellX) }
    var okY by remember(widget.rowId) { mutableIntStateOf(widget.cellY) }
    var okSx by remember(widget.rowId) { mutableIntStateOf(widget.spanX) }
    var okSy by remember(widget.rowId) { mutableIntStateOf(widget.spanY) }

    val currentCanFit by rememberUpdatedState(canFit)
    var previewing by remember(widget.rowId) { mutableStateOf(false) }
    val cancelPreview by rememberUpdatedState(onCancelPreview)
    DisposableEffect(widget.rowId) {
        onDispose { if (previewing) cancelPreview() }
    }
    var committing by remember(widget.rowId) { mutableStateOf(false) }
    val currentSetBounds by rememberUpdatedState(onSetBounds)
    val currentPreview by rememberUpdatedState(onPreviewBounds)
    fun showPreview(x: Int, y: Int, w: Int, h: Int) {
        previewing = true
        currentPreview(x, y, w, h)
    }
    var blocked by remember(widget.rowId) { mutableStateOf(false) }
    fun preview(x: Int, y: Int, w: Int, h: Int): Boolean {
        val accepted = currentCanFit(x, y, w, h)
        blocked = !accepted
        return accepted
    }
    fun commitBounds() {
        if (committing) return
        val x = cx; val y = cy; val w = sx; val h = sy
        committing = true
        previewing = false
        commitScope.launch {
            try {
                if (currentSetBounds(x, y, w, h)) {
                    okX = x; okY = y; okSx = w; okSy = h
                } else {
                    cx = okX; cy = okY; sx = okSx; sy = okSy
                    currentPreview(cx, cy, sx, sy)
                }
            } finally { committing = false }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = if (blocked) MaterialTheme.colorScheme.error else primary
    val handlePx = with(density) { 48.dp.toPx() }
    val widthLabel = stringResource(R.string.widget_resize_width)
    val heightLabel = stringResource(R.string.widget_resize_height)
    val sizeLabel = stringResource(R.string.widget_size, sx, sy)
    fun resizeBy(dx: Int, dy: Int): Boolean {
        val limits = range ?: return false
        if (committing || (dx != 0 && !limits.horizontal) || (dy != 0 && !limits.vertical)) return false
        val w = if (dx == 0) sx else (sx + dx).coerceIn(limits.minX, limits.maxX)
        val h = if (dy == 0) sy else (sy + dy).coerceIn(limits.minY, limits.maxY)
        if ((w == sx && h == sy) || !preview(cx, cy, w, h)) return false
        sx = w; sy = h
        showPreview(cx, cy, sx, sy)
        commitBounds()
        return true
    }

    // Scrim: consumes a tap (exit)
    Box(Modifier.fillMaxSize().pointerInput(widget.rowId) { detectTapGestures { onExit() } })

    val left = cx * cellW
    val top = cy * cellH
    val right = (cx + sx) * cellW
    val bottom = (cy + sy) * cellH

    // Bounding Box Frame with glowing 1.5.dp accent stroke and rounded corners
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { (sx * cellW).toDp() }, with(density) { (sy * cellH).toDp() })
            .drawBehind {
                drawRoundRect(
                    color = borderColor.copy(alpha = 0.25f),
                    size = size,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            .background(borderColor.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            .semantics {
                stateDescription = sizeLabel
                customActions = buildList {
                    if (range?.horizontal == true) {
                        add(CustomAccessibilityAction(widthLabel + " +") { resizeBy(1, 0) })
                        add(CustomAccessibilityAction(widthLabel + " −") { resizeBy(-1, 0) })
                    }
                    if (range?.vertical == true) {
                        add(CustomAccessibilityAction(heightLabel + " +") { resizeBy(0, 1) })
                        add(CustomAccessibilityAction(heightLabel + " −") { resizeBy(0, -1) })
                    }
                }
            }
            .widgetDragGesture(widget.rowId, !committing, widgetDragController, immediate = true) { root, fraction, owner ->
                onLift(widget.copy(cellX = cx, cellY = cy, spanX = sx, spanY = sy), root, fraction, owner)
            },
    )

    // Action bar: the size, widget settings and remove in one compact pill below the widget (above
    // it when there is no room underneath). Resizing itself is done with the handles on the frame,
    // or with the accessibility actions on it — the widget is never covered while you size it.
    var barSize by remember(widget.rowId) { mutableStateOf(IntSize.Zero) }
    val gridWidth = columns * cellW
    val gridHeight = rows * cellH
    val barGap = with(density) { 12.dp.toPx() }
    val barMaxX = (gridWidth - barSize.width - barGap).coerceAtLeast(0f)
    val barX = ((left + right) / 2f - barSize.width / 2f).coerceIn(barGap.coerceAtMost(barMaxX), barMaxX)
    val barY = when {
        bottom + barGap + barSize.height <= gridHeight -> bottom + barGap
        top - barGap - barSize.height >= 0f -> top - barGap - barSize.height
        // No room on either side (a widget as tall as the page): sit inside its bottom edge.
        else -> (bottom - barGap - barSize.height).coerceAtLeast(0f)
    }
    Surface(
        modifier = Modifier
            .offset { IntOffset(barX.roundToInt(), barY.roundToInt()) }
            .onSizeChanged { barSize = it }
            // Hidden for the one frame before its size is known, so it never jumps into place.
            .graphicsLayer { alpha = if (barSize == IntSize.Zero) 0f else 1f }
            .shadow(8.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$sx × $sy",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (reconfigurable) {
                EditBarButton(
                    icon = R.drawable.ic_home_settings,
                    label = stringResource(R.string.widget_reconfigure),
                    tint = MaterialTheme.colorScheme.onSurface,
                    enabled = !committing,
                    onClick = onReconfigure,
                )
            }
            EditBarButton(
                icon = R.drawable.ic_widget_style,
                label = stringResource(R.string.widget_look),
                tint = MaterialTheme.colorScheme.onSurface,
                enabled = !committing,
                onClick = onStyle,
            )
            EditBarButton(
                icon = LauncherIcons.Delete,
                label = stringResource(R.string.widget_remove),
                tint = MaterialTheme.colorScheme.error,
                enabled = !committing,
                onClick = onDeleteWidget,
            )
        }
    }

    if (range != null) {
        var accX by remember(widget.rowId) { mutableFloatStateOf(0f) }
        var accY by remember(widget.rowId) { mutableFloatStateOf(0f) }

        fun step(acc: Float, cell: Float): Int {
            val f = acc / cell
            return if (abs(f) > 0.66f) f.roundToInt() else 0
        }

        @Composable
        fun ResizeHandlePill(
            centerX: Float,
            centerY: Float,
            label: String,
            // 0 = corner dot, 1 = horizontal bar (top / bottom edge), 2 = vertical bar (left / right edge)
            bar: Int = 0,
            onDrag: (Offset) -> Unit,
        ) {
            var active by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (active) 1.35f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "handlePillScale",
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset((centerX - handlePx / 2).roundToInt(), (centerY - handlePx / 2).roundToInt()) }
                    .size(with(density) { handlePx.toDp() })
                    .semantics { contentDescription = label }
                    .pointerInput(widget.rowId, cellW, cellH, range, committing) {
                        if (committing) return@pointerInput
                        detectDragGestures(
                            onDragStart = { active = true },
                            onDragEnd = {
                                active = false
                                accX = 0f; accY = 0f; blocked = false
                                commitBounds()
                            },
                            onDragCancel = {
                                active = false
                                accX = 0f; accY = 0f; blocked = false
                                cx = okX; cy = okY; sx = okSx; sy = okSy
                                previewing = false
                                cancelPreview()
                            },
                        ) { ch, d -> ch.consume(); onDrag(d) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .size(
                            width = (when (bar) { 1 -> 30.dp; 2 -> 6.dp; else -> 12.dp }) * scale,
                            height = (when (bar) { 1 -> 6.dp; 2 -> 30.dp; else -> 12.dp }) * scale,
                        )
                        .shadow(4.dp, CircleShape),
                    shape = CircleShape,
                    color = borderColor,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                ) {}
            }
        }

        // Edge Handles
        if (range.horizontal) {
            ResizeHandlePill(right, (top + bottom) / 2f, widthLabel, bar = 2) { d ->
                accX += d.x
                val s = step(accX, cellW)
                if (s != 0) {
                    val n = (sx + s).coerceIn(range.minX, range.maxX)
                    if (n != sx && preview(cx, cy, n, sy)) { sx = n; showPreview(cx, cy, sx, sy) }
                    accX = 0f
                }
            }
            ResizeHandlePill(left, (top + bottom) / 2f, widthLabel, bar = 2) { d ->
                accX += d.x
                val s = step(accX, cellW)
                if (s != 0) {
                    resizeWidgetStartEdge(cx, sx, s, range.minX, range.maxX)?.let { (x, width) ->
                        if ((x != cx || width != sx) && preview(x, cy, width, sy)) {
                            cx = x; sx = width
                            showPreview(cx, cy, sx, sy)
                        }
                    }
                    accX = 0f
                }
            }
        }
        if (range.vertical) {
            ResizeHandlePill((left + right) / 2f, bottom, heightLabel, bar = 1) { d ->
                accY += d.y
                val s = step(accY, cellH)
                if (s != 0) {
                    val n = (sy + s).coerceIn(range.minY, range.maxY)
                    if (n != sy && preview(cx, cy, sx, n)) { sy = n; showPreview(cx, cy, sx, sy) }
                    accY = 0f
                }
            }
            ResizeHandlePill((left + right) / 2f, top, heightLabel, bar = 1) { d ->
                accY += d.y
                val s = step(accY, cellH)
                if (s != 0) {
                    resizeWidgetStartEdge(cy, sy, s, range.minY, range.maxY)?.let { (y, height) ->
                        if ((y != cy || height != sy) && preview(cx, y, sx, height)) {
                            cy = y; sy = height
                            showPreview(cx, cy, sx, sy)
                        }
                    }
                    accY = 0f
                }
            }
        }

        // Corner Handles
        if (range.horizontal && range.vertical) {
            ResizeHandlePill(left, top, "$widthLabel $heightLabel") { d ->
                accX += d.x; accY += d.y
                val sxStep = step(accX, cellW)
                val syStep = step(accY, cellH)
                var changed = false
                var newX = cx; var newW = sx; var newY = cy; var newH = sy
                if (sxStep != 0) {
                    resizeWidgetStartEdge(cx, sx, sxStep, range.minX, range.maxX)?.let { (x, width) ->
                        newX = x; newW = width; changed = true
                    }
                    accX = 0f
                }
                if (syStep != 0) {
                    resizeWidgetStartEdge(cy, sy, syStep, range.minY, range.maxY)?.let { (y, height) ->
                        newY = y; newH = height; changed = true
                    }
                    accY = 0f
                }
                if (changed && preview(newX, newY, newW, newH)) {
                    cx = newX; sx = newW; cy = newY; sy = newH
                    showPreview(cx, cy, sx, sy)
                }
            }
            ResizeHandlePill(right, top, "$widthLabel $heightLabel") { d ->
                accX += d.x; accY += d.y
                val sxStep = step(accX, cellW)
                val syStep = step(accY, cellH)
                var changed = false
                var newW = sx; var newY = cy; var newH = sy
                if (sxStep != 0) {
                    val n = (sx + sxStep).coerceIn(range.minX, range.maxX)
                    if (n != sx) { newW = n; changed = true }
                    accX = 0f
                }
                if (syStep != 0) {
                    resizeWidgetStartEdge(cy, sy, syStep, range.minY, range.maxY)?.let { (y, height) ->
                        newY = y; newH = height; changed = true
                    }
                    accY = 0f
                }
                if (changed && preview(cx, newY, newW, newH)) {
                    sx = newW; cy = newY; sy = newH
                    showPreview(cx, cy, sx, sy)
                }
            }
            ResizeHandlePill(left, bottom, "$widthLabel $heightLabel") { d ->
                accX += d.x; accY += d.y
                val sxStep = step(accX, cellW)
                val syStep = step(accY, cellH)
                var changed = false
                var newX = cx; var newW = sx; var newH = sy
                if (sxStep != 0) {
                    resizeWidgetStartEdge(cx, sx, sxStep, range.minX, range.maxX)?.let { (x, width) ->
                        newX = x; newW = width; changed = true
                    }
                    accX = 0f
                }
                if (syStep != 0) {
                    val n = (sy + syStep).coerceIn(range.minY, range.maxY)
                    if (n != sy) { newH = n; changed = true }
                    accY = 0f
                }
                if (changed && preview(newX, cy, newW, newH)) {
                    cx = newX; sx = newW; sy = newH
                    showPreview(cx, cy, sx, sy)
                }
            }
            ResizeHandlePill(right, bottom, "$widthLabel $heightLabel") { d ->
                accX += d.x; accY += d.y
                val sxStep = step(accX, cellW)
                val syStep = step(accY, cellH)
                var changed = false
                var newW = sx; var newH = sy
                if (sxStep != 0) {
                    val n = (sx + sxStep).coerceIn(range.minX, range.maxX)
                    if (n != sx) { newW = n; changed = true }
                    accX = 0f
                }
                if (syStep != 0) {
                    val n = (sy + syStep).coerceIn(range.minY, range.maxY)
                    if (n != sy) { newH = n; changed = true }
                    accY = 0f
                }
                if (changed && preview(cx, cy, newW, newH)) {
                    sx = newW; sy = newH
                    showPreview(cx, cy, sx, sy)
                }
            }
        }
    }

    if (canMovePrev || canMoveNext) {
        val arrowY = (rows * cellH - handlePx - with(density) { 16.dp.toPx() })
        val centerX = columns * cellW / 2f
        if (canMovePrev) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((centerX - handlePx - with(density) { 8.dp.toPx() }).roundToInt(), arrowY.roundToInt()) }
                    .size(with(density) { handlePx.toDp() })
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .clickable(enabled = !committing) { onMoveToPage(widget.page - 1, sx, sy) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LauncherIcons.ChevronRight),
                    contentDescription = stringResource(R.string.widget_move_prev_page),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(with(density) { (handlePx * 0.62f).toDp() }).graphicsLayer { scaleX = -1f },
                )
            }
        }
        if (canMoveNext) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((centerX + with(density) { 8.dp.toPx() }).roundToInt(), arrowY.roundToInt()) }
                    .size(with(density) { handlePx.toDp() })
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .clickable(enabled = !committing) { onMoveToPage(widget.page + 1, sx, sy) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LauncherIcons.ChevronRight),
                    contentDescription = stringResource(R.string.widget_move_next_page),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(with(density) { (handlePx * 0.62f).toDp() }),
                )
            }
        }
    }
}

/** Book layout: every home page takes half of the pager, so two sit side by side. */
internal object HalfPageSize : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
        ((availableSpace - pageSpacing) / 2).coerceAtLeast(0)
}

/** One round icon button of the widget edit bar. */
@Composable
private fun EditBarButton(icon: Int, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}
