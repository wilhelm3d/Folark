package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs

// Widget stacks: several widgets in one cell, swiped through (split out of Workspace.kt).

/** Nothing OS 3 Style Stackable Widget Container. */
@Composable
internal fun WidgetStackContainer(
    stack: PlacedWidgetStack,
    /** The stack's footprint in dp — the size every hosted widget in it is told to render at. */
    widthDp: Int,
    heightDp: Int,
    widgetViews: Map<Int, android.appwidget.AppWidgetHostView>,
    widgetDragController: WidgetDragController,
    onRestorePendingWidget: (PendingWidget) -> Unit,
    onAddToStack: (stackId: Long) -> Unit = {},
    onRemoveFromStack: (stackId: Long, itemRowId: Long) -> Unit = { _, _ -> },
    onDeleteStack: (stackId: Long) -> Unit = {},
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = 0) { stack.items.size }
    // Settings ▸ Appearance ▸ Widget stacks: corner radius, page indicator and the options pill.
    val stackStyle = LocalStackStyle.current
    val stackShape = RoundedCornerShape(stackStyle.cornerRadius.dp)
    var menuExpanded by remember { mutableStateOf(false) }
    val stackScope = rememberCoroutineScope()
    val itemCount = stack.items.size

    // Settings ▸ Widget stacks ▸ switch automatically: moves on (and wraps round) after the chosen
    // time. The timer starts again whenever the shown widget changes, and only runs while the
    // home screen is in front and nobody is touching the stack or its menu.
    var resumed by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    // A stack that holds the Now playing widget brings it to the front when something starts
    // playing (and stops switching by itself until playback ends). You can still swipe away.
    val mediaIndex = stack.items.indexOfFirst { (it as? PlacedBuiltin)?.type == HomeItemEntity.BUILTIN_MEDIA }
    val stackContext = LocalContext.current
    val mediaWatcher = remember(stackContext, mediaIndex >= 0) {
        if (mediaIndex >= 0) MediaSessionWatcher(stackContext) else null
    }
    if (mediaWatcher != null) {
        LifecycleResumeEffect(mediaWatcher) {
            mediaWatcher.start()
            onPauseOrDispose { mediaWatcher.stop() }
        }
    }
    val mediaPlaying = mediaWatcher?.nowPlaying?.playing == true
    LaunchedEffect(mediaPlaying, mediaIndex) {
        if (mediaPlaying && mediaIndex >= 0 && pagerState.currentPage != mediaIndex) {
            pagerState.animateScrollToPage(mediaIndex)
        }
    }
    val autoRotate = if (LocalBatterySaver.current || LocalHomeCovered.current) 0 else stackStyle.autoRotateSeconds
    LaunchedEffect(autoRotate, itemCount, resumed, menuExpanded, touching, mediaPlaying, pagerState.settledPage) {
        if (autoRotate <= 0 || itemCount < 2 || !resumed || menuExpanded || touching || mediaPlaying) return@LaunchedEffect
        while (true) {
            delay(autoRotate * 1000L)
            if (widgetDragController.active == null && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % itemCount)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(stackShape)
            // A swipe up or down flips to the next or previous widget (wrapping round). It is
            // watched on the way down to the widgets, because a hosted app widget takes the touches
            // for itself and the pager underneath would never see the swipe.
            .pointerInput(itemCount, locked) {
                if (locked || itemCount < 2) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    var total = Offset.Zero
                    var claimed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            total += change.positionChange()
                            if (!claimed && abs(total.y) > slop && abs(total.y) > abs(total.x)) claimed = true
                            if (claimed) change.consume()
                        }
                    } finally {
                        touching = false
                    }
                    if (claimed && abs(total.y) > slop * 2f) {
                        val step = if (total.y < 0f) 1 else -1
                        val next = ((pagerState.currentPage + step) % itemCount + itemCount) % itemCount
                        stackScope.launch { pagerState.animateScrollToPage(next) }
                    }
                }
            },
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Swipes are handled by the stack itself (above), the same way for every kind of widget.
            userScrollEnabled = false,
            // Keyed by row, not position: removing a widget must not hand its page (and the host
            // view parked in it) to the next one.
            key = { index -> stack.items.getOrNull(index)?.stackChildRowId() ?: index },
        ) { pageIndex ->
            val child = stack.items.getOrNull(pageIndex) ?: return@VerticalPager

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Read here, in the draw phase, so a flip redraws the page instead of recomposing it.
                        val pageOffset = kotlin.math.abs((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                        val scale = (1f - pageOffset * 0.08f).coerceIn(0.88f, 1f)
                        this.alpha = (1f - pageOffset * 0.35f).coerceIn(0.2f, 1f)
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                    .clip(stackShape)
            ) {
                when (child) {
                    is PlacedWidget -> {
                        val view = widgetViews[child.appWidgetId]
                        if (view != null) {
                            AndroidView(
                                factory = { c -> android.widget.FrameLayout(c) },
                                update = { container ->
                                    // Same contract as a widget on the grid: without its size options
                                    // a collection widget (a RemoteViewsService list) renders empty.
                                    pushWidgetSize(view, widthDp, heightDp)
                                    if (view.parent !== container) {
                                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                                        container.removeAllViews()
                                        container.addView(
                                            view,
                                            android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, stackShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = child.provider.shortClassName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is PlacedBuiltin -> {
                        CompositionLocalProvider(LocalWidgetDragController provides widgetDragController) {
                            when (child.type) {
                                HomeItemEntity.BUILTIN_NOTIFICATIONS -> NotificationsWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET -> SamsungNotificationWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_BATTERY -> BatteryWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_PEOPLE -> PeopleWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_NOTHING_CLOCK -> NothingClockWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_SAMSUNG_WEATHER -> SamsungWeatherWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_INTERACTIVE_NOTIFICATIONS -> InteractiveNotificationWidget(Modifier.fillMaxSize())
                                HomeItemEntity.BUILTIN_MEDIA, HomeItemEntity.BUILTIN_TOP_APPS,
                                HomeItemEntity.BUILTIN_STATS, HomeItemEntity.BUILTIN_NOTE, HomeItemEntity.BUILTIN_CLIPBOARD, HomeItemEntity.BUILTIN_MIXER,
                                HomeItemEntity.BUILTIN_WEATHER_DAY, HomeItemEntity.BUILTIN_WEATHER_BIG ->
                                    BuiltinWidget(child.type, Modifier.fillMaxSize())
                                else -> SmartspaceWidget(Modifier.fillMaxSize())
                            }
                        }
                    }
                    is PendingWidget -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer, stackShape)
                                .clickable { onRestorePendingWidget(child) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.widget_restore_tap),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        // Stack Options Pill (Top Right)
        if (!locked && stackStyle.showPill) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    onClick = { menuExpanded = !menuExpanded },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.50f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_widgets),
                            contentDescription = stringResource(R.string.stack_menu),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "${stack.items.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stack_add), style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            menuExpanded = false
                            onAddToStack(stack.rowId)
                        },
                    )
                    if (stack.items.size > 1) {
                        val childRowId = stack.items.getOrNull(pagerState.currentPage)?.stackChildRowId()
                        if (childRowId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stack_remove_active), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveFromStack(stack.rowId, childRowId)
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stack_delete), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDeleteStack(stack.rowId)
                        },
                    )
                }
            }
        }

        // Animated bottom page indicator: dots, a segmented bar, or nothing.
        val barIndicator = stackStyle.indicator == LauncherSettings.STACK_INDICATOR_BAR
        if (stack.items.size > 1 && stackStyle.indicator != LauncherSettings.STACK_INDICATOR_NONE) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .background(Color.Black.copy(alpha = 0.32f), CircleShape)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(if (barIndicator) 2.dp else 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(stack.items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (barIndicator) 7.dp else if (isSelected) 8.dp else 4.dp,
                        label = "stackDotWidth",
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = if (barIndicator) 2.dp else 4.dp)
                            .background(
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/** How widget stacks are drawn (Settings ▸ Appearance): corner radius in dp, the page indicator
 *  (`LauncherSettings.STACK_INDICATOR_*`) and whether the options pill shows. */
data class StackStyle(
    val cornerRadius: Int = 24,
    val indicator: String = LauncherSettings.STACK_INDICATOR_DOTS,
    val showPill: Boolean = false,
    /** Seconds until a stack shows its next widget by itself; 0 = never. */
    val autoRotateSeconds: Int = 0,
)

val LocalStackStyle = androidx.compose.runtime.compositionLocalOf { StackStyle() }

private fun HomeEntry.stackChildRowId(): Long? = when (this) {
    is PlacedWidget -> rowId
    is PlacedBuiltin -> rowId
    is PendingWidget -> rowId
    else -> null
}
