package org.arkikeskus.launcher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.BadgeStyle
import org.arkikeskus.launcher.ui.component.LocalBadgeStyle
import org.arkikeskus.launcher.ui.component.onScreen
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

/**
 * The live preview pinned above a category's settings (it does not scroll with them). A category
 * can have a few views (the home screen, the glass, a menu…): whichever one a change touches comes
 * to the front and the frame lights up, so every change shows right where the eye already is.
 * Values being dragged on a slider show at once ([LocalPreviewOverrides]).
 */
internal class PreviewPart(
    @StringRes val label: Int,
    /** What this view shows; when it changes, this view comes to the front. */
    val watch: (LauncherSettings) -> Any?,
    val content: @Composable (LauncherSettings) -> Unit,
)

/** The views of [category]'s pinned preview; empty for pages with nothing to show (root, backup…). */
@Composable
internal fun previewParts(category: SettingsCategory, previewIcon: ImageBitmap?, allApps: List<AppItem>): List<PreviewPart> =
    when (category) {
        SettingsCategory.THEME -> listOf(
            PreviewPart(R.string.settings_pv_home, { listOf(it.appearance, it.icons.labelColor, it.home.showLabels) }) { HomeThemePreview(it) },
            PreviewPart(R.string.settings_pv_glass, { it.drawer.glassBlurRadius to it.drawer.glassDarkTint }) { GlassBlurPreview(it) },
            PreviewPart(R.string.settings_pv_menu, { it.appearance.popupTheme }) { PopupThemePreview(it.appearance.popupTheme) },
            PreviewPart(R.string.settings_pv_stacks, { listOf(it.widgets.stackCornerRadius, it.widgets.stackIndicator, it.widgets.stackShowPill) }) { CornerPreview(it) },
            PreviewPart(R.string.settings_pv_backdrop, { listOf(it.home.wallpaperDim, it.home.wallpaperBlur, it.home.backgroundImage) }) { BackdropPreview(it) },
        )
        SettingsCategory.ICONS -> listOf(
            PreviewPart(R.string.settings_pv_icons, { it.icons }) { IconsPreview(it) },
            PreviewPart(R.string.settings_pv_badges, { it.dots }) { s ->
                CompositionLocalProvider(LocalBadgeStyle provides BadgeStyle(s.dots.shape, s.dots.color, s.dots.position)) {
                    PreviewFrame(height = 96.dp) {
                        Backdrop(path = "", blur = 0f, dim = 0.1f)
                        Box(Modifier.align(Alignment.Center)) {
                            BadgePreview(icon = previewIcon, showDots = s.dots.enabled, showCount = s.dots.showCount, scale = s.dots.scale)
                        }
                    }
                }
            },
        )
        SettingsCategory.MOTION -> listOf(
            PreviewPart(R.string.settings_pv_motion, { listOf(it.motion, it.home.pageBounceEnabled) }) { MotionPreview(it) },
        )
        SettingsCategory.HOME_SCREEN -> listOf(
            PreviewPart(R.string.settings_pv_home, { it }) { HomeThemePreview(it) },
        )
        SettingsCategory.DOCK_FOLDERS -> listOf(
            PreviewPart(R.string.settings_pv_dock, { it.dock }) { s ->
                PreviewFrame(height = 84.dp) {
                    Backdrop(path = s.home.backgroundImage, blur = s.home.wallpaperBlur, dim = s.home.wallpaperDim)
                    Box(Modifier.align(Alignment.Center).padding(horizontal = 12.dp)) {
                        DockPreview(opacity = s.dock.backgroundOpacity, icon = previewIcon)
                    }
                }
            },
            PreviewPart(R.string.settings_pv_folders, { it.folders }) { FolderStylePreview(it) },
        )
        SettingsCategory.GESTURES -> listOf(
            PreviewPart(R.string.settings_pv_gestures, { listOf(it.home.swipeUpAction, it.home.swipeDownAction, it.home.leftSwipeAppKey, it.home.rightSwipeAppKey) }) {
                GesturePreview(it, allApps)
            },
        )
        SettingsCategory.APP_DRAWER -> listOf(
            PreviewPart(R.string.settings_pv_drawer, { it.drawer }) { DrawerPreview(s = it) },
        )
        SettingsCategory.TASKBAR -> listOf(
            PreviewPart(R.string.settings_pv_taskbar, { it.taskbar }) { TaskbarPreview(it.taskbar.taskbarHandleStyle, it.taskbar.handles, it.taskbar.taskbarAnim) },
        )
        SettingsCategory.RECENTS -> listOf(
            PreviewPart(R.string.settings_pv_recents_motion, { listOf(it.recents.enterAnimation, it.recents.selectAnimation, it.recents.animationSpeed) }) {
                RecentsMotionPreview(it.recents.enterAnimation, it.recents.selectAnimation, it.recents.animationSpeed)
            },
            PreviewPart(R.string.settings_pv_recents_press, { it.recents.pressAnimation }) {
                PressPreview(it.recents.pressAnimation, it.recents.animationSpeed)
            },
        )
        SettingsCategory.SPLIT -> listOf(
            PreviewPart(R.string.settings_pv_split, { it.recents.picker }) { SplitPickerPreview(it.recents.picker) },
        )
        else -> emptyList()
    }

/**
 * The pinned preview: a small title row (which view, the other views as chips, fold away) over the
 * view. [compact] shrinks it for narrow screens; [collapsed] is shared by all pages.
 */
@Composable
internal fun PinnedCategoryPreview(
    category: SettingsCategory,
    settings: LauncherSettings,
    previewIcon: ImageBitmap?,
    allApps: List<AppItem>,
    compact: Boolean,
    collapsed: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    val parts = previewParts(category, previewIcon, allApps)
    if (parts.isEmpty()) return
    val palette = LocalExpressivePalette.current
    val live = settings.withPreview(LocalPreviewOverrides.current)

    var shown by rememberSaveable(category) { mutableIntStateOf(0) }
    if (shown >= parts.size) shown = 0
    val keys = parts.map { it.watch(live) }
    var last by remember(category) { mutableStateOf(keys) }
    var changes by remember(category) { mutableIntStateOf(0) }
    LaunchedEffect(keys) {
        val changed = keys.indices.filter { keys[it] != last.getOrNull(it) }
        last = keys
        if (changed.isEmpty()) return@LaunchedEffect
        // One view touched: that one. Several (a preset, the other display's profile): the overview.
        shown = changed.filter { it != 0 }.singleOrNull() ?: 0
        changes++
    }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(changes) {
        if (changes == 0) return@LaunchedEffect
        glow.snapTo(1f)
        glow.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }

    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceHi)
            .border(
                width = (1f + glow.value * 1.5f).dp,
                color = lerp(palette.faint.copy(alpha = 0.25f), Accent, glow.value),
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { collapsed.value = !collapsed.value },
        ) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = lerp(palette.dim, Accent, glow.value), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_pv_live),
                color = palette.dim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                parts.forEachIndexed { i, part ->
                    val selected = i == shown
                    Text(
                        text = stringResource(part.label),
                        color = if (selected) Color.White else palette.dim,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Accent else palette.btn)
                            .clickable {
                                shown = i
                                collapsed.value = false
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = if (collapsed.value) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(if (collapsed.value) R.string.settings_pv_show else R.string.settings_pv_hide),
                tint = palette.dim,
                modifier = Modifier.padding(start = 6.dp).size(20.dp),
            )
        }
        AnimatedVisibility(visible = !collapsed.value, enter = expandVertically(), exit = shrinkVertically()) {
            Crossfade(targetState = shown.coerceIn(0, parts.lastIndex), label = "preview", modifier = Modifier.padding(top = 8.dp)) { index ->
                Box(Modifier.fillMaxWidth().scaledDown(if (compact) COMPACT_SCALE else 1f)) {
                    parts[index].content(live)
                }
            }
        }
    }
}

private const val COMPACT_SCALE = 0.72f

/** Lays the content out at full width and draws it [f] times as big, taking only that much room. */
private fun Modifier.scaledDown(f: Float): Modifier = if (f >= 1f) this else layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, (placeable.height * f).roundToInt()) {
        placeable.placeRelativeWithLayer((width - placeable.width) / 2, 0) {
            scaleX = f
            scaleY = f
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

// ------------------------------------------------------------------ views the pages didn't have yet

/** A row of app icons with the chosen pack, shape, themed style and labels. */
@Composable
private fun IconsPreview(s: LauncherSettings) {
    val apps = LocalPreviewApps.current
    PreviewFrame(height = 108.dp) {
        Backdrop(path = s.home.backgroundImage, blur = s.home.wallpaperBlur, dim = s.home.wallpaperDim)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        ) {
            apps.take(5).forEach { app ->
                AppIcon(
                    appItem = app,
                    labelColor = Color(s.icons.labelColor),
                    iconSize = 46.dp,
                    showLabel = s.home.showLabels,
                    maxLabelLines = 1,
                    aquamorphicTouchEnabled = false,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

/**
 * Folark's motion on a loop: a tap on an icon (the fluid ripple when on), the app opening from it
 * (zoom when on) and a page swipe that bounces at the edge (when on), at the chosen speed.
 */
@Composable
private fun MotionPreview(s: LauncherSettings) {
    val speed = s.motion.animationSpeed.coerceIn(0.25f, 4f)
    val saver = s.motion.batterySaverMode == LauncherSettings.BATTERY_SAVER_ON
    val cycle = (2600 / speed).roundToInt().coerceAtLeast(600)
    val visible = org.arkikeskus.launcher.ui.component.rememberOnScreen()
    val transition = rememberInfiniteTransition(label = "motion")
    // 0..1 over one loop: tap (0-0.15), open (0.15-0.5), hold, close (0.6-0.8), page bounce (0.8-1).
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(cycle, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val time = if (saver || !visible.value) 0.55f else t
    PreviewFrame(height = 150.dp, modifier = Modifier.onScreen(visible)) {
        Backdrop(path = s.home.backgroundImage, blur = s.home.wallpaperBlur, dim = s.home.wallpaperDim)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            // The page swipe: everything slides left and (with bounce) springs back past its place.
            val swipe = when {
                time < 0.8f -> 0f
                s.home.pageBounceEnabled -> kotlin.math.sin(((time - 0.8f) / 0.2f) * Math.PI.toFloat()) * -26f *
                    (1f - (time - 0.8f) / 0.2f)
                else -> 0f
            }
            Box(Modifier.fillMaxSize().offset(x = swipe.dp)) {
                // The icon.
                val iconX = w * 0.22f
                val iconY = h * 0.5f
                val tap = if (time in 0f..0.15f) time / 0.15f else 0f
                if (s.motion.aquamorphicTouchEnabled && tap > 0f) {
                    Box(
                        Modifier
                            .offset(x = iconX - 34.dp, y = iconY - 34.dp)
                            .size(68.dp)
                            .graphicsLayer { scaleX = 0.6f + tap * 0.7f; scaleY = scaleX; alpha = 1f - tap }
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
                Box(
                    Modifier
                        .offset(x = iconX - 22.dp, y = iconY - 22.dp)
                        .size(44.dp)
                        .graphicsLayer {
                            val squash = if (s.motion.aquamorphicTouchEnabled) 1f - kotlin.math.sin(tap * Math.PI.toFloat()) * 0.12f else 1f
                            scaleX = squash
                            scaleY = squash
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent),
                )
                // The app window, grown from the icon (zoom) or simply faded in.
                val open = when {
                    time < 0.15f -> 0f
                    time < 0.5f -> FastOutSlowInEasing.transform((time - 0.15f) / 0.35f)
                    time < 0.6f -> 1f
                    time < 0.8f -> 1f - FastOutSlowInEasing.transform((time - 0.6f) / 0.2f)
                    else -> 0f
                }
                if (open > 0f) {
                    val zoom = s.motion.appLaunchZoomEnabled
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .graphicsLayer {
                                if (zoom) {
                                    val sc = 0.12f + 0.88f * open
                                    scaleX = sc
                                    scaleY = sc
                                    transformOrigin = TransformOrigin(0.22f, 0.5f)
                                    alpha = (open * 3f).coerceAtMost(1f)
                                } else {
                                    alpha = open
                                }
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF2F4F8)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(CircleShape).background(Accent.copy(alpha = 0.8f)))
                            Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CircleShape).background(Color(0xFFCDD3DD)))
                            Box(Modifier.fillMaxWidth(0.65f).height(8.dp).clip(CircleShape).background(Color(0xFFCDD3DD)))
                        }
                    }
                }
            }
            Text(
                text = stringResource(
                    when {
                        saver -> R.string.settings_pv_motion_saver
                        else -> R.string.settings_pv_motion_speed
                    },
                    "%.1f×".format(speed),
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

/** Where each swipe on home goes: up, down, and the apps at the left and right edges. */
@Composable
private fun GesturePreview(s: LauncherSettings, allApps: List<AppItem>) {
    val left = allApps.firstOrNull { it.key == s.home.leftSwipeAppKey }?.label ?: stringResource(R.string.settings_left_edge_none)
    val right = allApps.firstOrNull { it.key == s.home.rightSwipeAppKey }?.label ?: stringResource(R.string.settings_right_edge_none)
    PreviewFrame(height = 150.dp) {
        Backdrop(path = s.home.backgroundImage, blur = s.home.wallpaperBlur, dim = s.home.wallpaperDim + 0.25f)
        GestureTag("↓  " + gestureLabel(s.home.swipeDownAction), Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        GestureTag("↑  " + gestureLabel(s.home.swipeUpAction), Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        GestureTag("→  $left", Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
        GestureTag("$right  ←", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
    }
}

@Composable
private fun GestureTag(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
