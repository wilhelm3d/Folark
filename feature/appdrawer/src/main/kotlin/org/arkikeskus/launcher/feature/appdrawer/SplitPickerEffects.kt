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

// The split picker's touches of motion and its bottom room: the glow on a picked app, the zoom
// into the drawer, the room kept free above the taskbar (split out of SplitPickerDrawer.kt).

internal const val GLOW_MS = 260

/** Bottom room for the picker's app grids (see [rememberPickerBottomRoom]). */
internal val LocalPickerBottomRoom = compositionLocalOf { 0.dp }

/**
 * How much of this window's bottom the apps should keep free: the navigation bar, or Folark's
 * taskbar where it overlaps the window (whichever is more). The taskbar's place comes from
 * [org.arkikeskus.launcher.ui.TaskbarDropTarget], published whenever the bar is up.
 */
@Composable
internal fun rememberPickerBottomRoom(): Dp {
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val nav = WindowInsets.navigationBars.getBottom(density)
    var barPx by remember { mutableStateOf(0) }
    // Read once the window is laid out, and again a moment later (the bar may come up after us).
    LaunchedEffect(Unit) {
        repeat(3) {
            barPx = taskbarOverlap(view)
            kotlinx.coroutines.delay(400)
        }
    }
    return with(density) { maxOf(nav, barPx).toDp() }
}

/** How far Folark's taskbar reaches up into [view]'s window from its bottom, in px (0: not over it). */
internal fun taskbarOverlap(view: android.view.View): Int {
    val bar = org.arkikeskus.launcher.ui.TaskbarDropTarget.bounds ?: return 0
    val root = view.rootView
    val at = IntArray(2)
    root.getLocationOnScreen(at)
    val left = at[0]
    val right = at[0] + root.width
    val bottom = at[1] + root.height
    if (bar.right <= left || bar.left >= right || bar.top >= bottom) return 0
    return (bottom - bar.top).coerceAtLeast(0)
}

/**
 * A soft accent glow blooming out of the picked icon at [from] and fading, with a thin ring
 * spreading past it ([p] 0..1) — the taskbar's launch glow, in the picker.
 */
@Composable
internal fun PickGlow(from: Rect, p: Float) {
    val accent = ACCENT
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val centre = from.center
        val base = maxOf(from.width, from.height) / 2f
        val fade = (1f - p) * (1f - p)
        val bloom = if (p < 0.2f) p / 0.2f else 1f
        val halo = base * (1f + 0.8f * p)
        if (halo > 0f) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to accent.copy(alpha = 0.85f * bloom * fade),
                    0.55f to accent.copy(alpha = 0.4f * bloom * fade),
                    1f to Color.Transparent,
                    center = centre,
                    radius = halo,
                ),
                radius = halo,
                center = centre,
            )
        }
        drawCircle(
            color = accent.copy(alpha = 0.75f * fade),
            radius = base * (1f + 1.1f * p),
            center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * 0.1f * (1f - p) + 1f),
        )
    }
}

internal class Opening(val app: AppItem, val from: Rect)

internal const val ZOOM_MS = 280
/** How big the icon ends up in the middle (about Android's splash screen icon). */
internal val ZOOM_ICON_END = 88.dp
/** The panel's colour once it fills the drawer (dark, like most app start screens at night). */
internal val ZOOM_PANEL = Color(0xFF1B2127)

/**
 * The tapped app growing out of its icon: a panel opens from the icon's place to the whole drawer,
 * its corners straightening, with the icon gliding to the middle and growing — so the app's own
 * start screen (its icon in the middle) carries straight on from it.
 */
@Composable
internal fun ZoomOpen(opening: Opening, p: Float, iconSize: Dp) {
    val density = LocalDensity.current
    var full by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { c -> full = Rect(Offset.Zero, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())) }
            // Nothing below can be tapped or scrolled while the app opens.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        val end = full ?: return@Box
        val r = lerp(opening.from, end, p)
        val corner = lerpDp(iconSize * 0.3f, 0.dp, p)
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(r.width.toDp(), r.height.toDp())
                    .clip(RoundedCornerShape(corner))
                    .background(lerpColor(Color.White.copy(alpha = 0.16f), ZOOM_PANEL, (p * 1.6f).coerceAtMost(1f))),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(opening.app, labelColor = Color.White, iconSize = lerpDp(iconSize, ZOOM_ICON_END, p), showLabel = false)
            }
        }
    }
}

@Composable
internal fun AppGrid(columns: Int, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        // The last row can scroll clear of the navigation bar and the taskbar.
        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 10.dp + LocalPickerBottomRoom.current),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

