package org.arkikeskus.launcher.feature.settings

import org.arkikeskus.launcher.ui.component.onScreen
import android.content.Context
import android.widget.Toast
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.model.LaunchGlow
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.TaskbarAnim
import org.arkikeskus.launcher.model.TaskbarDivider
import org.arkikeskus.launcher.model.TaskbarHandleStyle
import org.arkikeskus.launcher.model.TaskbarHandles
import org.arkikeskus.launcher.model.TaskbarMediaPosition
import org.arkikeskus.launcher.model.TaskbarMediaShape
import org.arkikeskus.launcher.model.TaskbarRecentsButton
import org.arkikeskus.launcher.model.TaskbarSource
import org.arkikeskus.launcher.model.TaskbarSplitSide
import org.arkikeskus.launcher.model.TaskbarTheme
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt
import org.arkikeskus.launcher.model.catching

/** Usage access, the same check the taskbar itself makes (an app-op, or the permission by default). */
private fun usageAccessGranted(context: Context): Boolean = ShellSplit.hasUsageAccess(context)

private fun splitPermissionGranted(context: Context): Boolean = ShellSplit.available(context)

private fun openUsageAccess(context: Context) {
    // Straight to Folark's own switch where the system supports it, else the list of apps.
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (catching { context.startActivity(direct) }.isFailure) {
        catching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Settings ▸ Taskbar: Folark's own taskbar on the unfolded screen. Shared by both displays. */
@Composable
internal fun TaskbarSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var usageAccess by remember { mutableStateOf(usageAccessGranted(context)) }
    var splitReady by remember { mutableStateOf(splitPermissionGranted(context)) }
    var picking by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        usageAccess = usageAccessGranted(context)
        splitReady = splitPermissionGranted(context)
        onPauseOrDispose { }
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_taskbar_category),
        icon = Icons.Rounded.CallToAction,
        subtitle = stringResource(R.string.settings_category_taskbar_desc),
        sharedByBothDisplays = true,
    ) {
        // Folark's taskbar, or Android's (with the system's gestures and quick switch).
        SystemTaskbarRows(s, viewModel)
        if (s.taskbar.source == TaskbarSource.SYSTEM) return@ExpressiveCategoryContainer
        ExpressiveRowDivider()
        TaskbarToggle(enabled = s.taskbar.enabled, onSetEnabled = viewModel::setTaskbarEnabled)
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_on_home),
            subtitle = stringResource(R.string.settings_taskbar_on_home_desc),
            checked = s.taskbar.onHome,
            onCheckedChange = viewModel::setTaskbarOnHome,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_keep_open),
            subtitle = stringResource(R.string.settings_taskbar_keep_open_desc),
            checked = s.taskbar.keepOpen,
            onCheckedChange = viewModel::setTaskbarKeepOpen,
        )
        if (s.taskbar.keepOpen) {
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_hide_full_screen),
                subtitle = stringResource(R.string.settings_taskbar_hide_full_screen_desc),
                checked = s.taskbar.hideFullScreen,
                onCheckedChange = viewModel::setTaskbarHideFullScreen,
            )
        }
    }
    // Android's own taskbar chosen: none of Folark's taskbar settings apply.
    if (s.taskbar.source == TaskbarSource.SYSTEM) return

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_tb_group_apps),
        icon = Icons.Rounded.Apps,
        sharedByBothDisplays = true,
    ) {
        val pinnedCount = s.taskbar.pinnedApps?.size
        ExpressiveActionItem(
            title = stringResource(R.string.settings_taskbar_pinned),
            description = if (pinnedCount == null) {
                stringResource(R.string.settings_taskbar_pinned_dock)
            } else {
                stringResource(R.string.settings_taskbar_pinned_count, pinnedCount)
            },
            onClick = { picking = true },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_pins_locked),
            subtitle = stringResource(R.string.settings_taskbar_pins_locked_desc),
            checked = s.taskbar.pinsLocked,
            onCheckedChange = viewModel::setTaskbarPinsLocked,
        )
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_taskbar_scene_slots),
            value = s.taskbar.sceneSlots,
            min = 0,
            max = 4,
            onValueChange = viewModel::setTaskbarSceneSlots,
        )
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_taskbar_recents),
            value = s.taskbar.recentCount,
            min = 0,
            max = 6,
            onValueChange = viewModel::setTaskbarRecentCount,
        )
        if (s.taskbar.recentCount > 0 && !usageAccess) {
            ExpressiveActionItem(
                title = stringResource(R.string.settings_taskbar_usage_access),
                description = stringResource(R.string.settings_taskbar_usage_access_desc),
                onClick = { openUsageAccess(context) },
            )
        }
        if (s.taskbar.recentCount > 0) {
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_open_previews),
                subtitle = stringResource(R.string.settings_taskbar_open_previews_desc),
                checked = s.taskbar.openPreviews,
                onCheckedChange = viewModel::setTaskbarOpenPreviews,
            )
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_media),
            subtitle = stringResource(R.string.settings_taskbar_media_desc),
            checked = s.taskbar.mediaEnabled,
            onCheckedChange = viewModel::setTaskbarMediaEnabled,
        )
        if (s.taskbar.mediaEnabled) {
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_media_paused),
                subtitle = stringResource(R.string.settings_taskbar_media_paused_desc),
                checked = s.taskbar.mediaShowPaused,
                onCheckedChange = viewModel::setTaskbarMediaShowPaused,
            )
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_taskbar_media_position),
                options = listOf(
                    TaskbarMediaPosition.START to stringResource(R.string.settings_taskbar_media_position_start),
                    TaskbarMediaPosition.MIDDLE to stringResource(R.string.settings_taskbar_media_position_middle),
                    TaskbarMediaPosition.END to stringResource(R.string.settings_taskbar_media_position_end),
                ),
                selected = s.taskbar.mediaPosition,
                onSelect = viewModel::setTaskbarMediaPosition,
                icon = null,
            )
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_taskbar_divider_style),
                options = listOf(
                    TaskbarDivider.LINE to stringResource(R.string.settings_taskbar_divider_line),
                    TaskbarDivider.BOLD to stringResource(R.string.settings_taskbar_divider_bold),
                    TaskbarDivider.DOT to stringResource(R.string.settings_taskbar_divider_dot),
                    TaskbarDivider.GAP to stringResource(R.string.settings_taskbar_divider_gap),
                    TaskbarDivider.NONE to stringResource(R.string.settings_taskbar_divider_none),
                ),
                selected = s.taskbar.dividerStyle,
                onSelect = viewModel::setTaskbarDividerStyle,
                icon = null,
            )
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_taskbar_media_shape),
                options = listOf(
                    TaskbarMediaShape.CIRCLE to stringResource(R.string.settings_taskbar_media_shape_circle),
                    TaskbarMediaShape.ROUNDED to stringResource(R.string.settings_taskbar_media_shape_rounded),
                ),
                selected = s.taskbar.mediaShape,
                onSelect = viewModel::setTaskbarMediaShape,
                icon = null,
            )
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_media_badge),
                subtitle = stringResource(R.string.settings_taskbar_media_badge_desc),
                checked = s.taskbar.mediaBadge,
                onCheckedChange = viewModel::setTaskbarMediaBadge,
            )
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_cover_bar),
            subtitle = stringResource(R.string.settings_taskbar_cover_bar_desc),
            checked = s.taskbar.coverBar,
            onCheckedChange = viewModel::setTaskbarCoverBar,
        )
        if (s.taskbar.coverBar) {
            ExpressiveStepperRow(
                title = stringResource(R.string.settings_taskbar_cover_count),
                value = s.taskbar.coverCount,
                min = LauncherSettings.MIN_COVER_BAR,
                max = LauncherSettings.MAX_COVER_BAR,
                onValueChange = viewModel::setTaskbarCoverCount,
            )
        }
        SettingsAdvanced {
            TaskbarPerAppRows(s, viewModel, apps)
        }
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_tb_group_look),
        icon = Icons.Rounded.Palette,
        sharedByBothDisplays = true,
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_theme),
            options = listOf(
                TaskbarTheme.CLEAR to stringResource(R.string.settings_taskbar_theme_clear),
                TaskbarTheme.FROSTED to stringResource(R.string.settings_taskbar_theme_frosted),
                TaskbarTheme.DARK to stringResource(R.string.settings_taskbar_theme_dark),
                TaskbarTheme.LIGHT to stringResource(R.string.settings_taskbar_theme_light),
                TaskbarTheme.ACCENT to stringResource(R.string.settings_taskbar_theme_accent),
                TaskbarTheme.GLASS to stringResource(R.string.settings_taskbar_theme_glass),
                TaskbarTheme.AMOLED to stringResource(R.string.settings_taskbar_theme_amoled),
                TaskbarTheme.NEON to stringResource(R.string.settings_taskbar_theme_neon),
            ),
            selected = s.taskbar.theme,
            onSelect = viewModel::setTaskbarTheme,
            icon = null,
        )
        if (s.taskbar.theme != TaskbarTheme.CLEAR) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_taskbar_opacity),
                value = s.taskbar.opacity,
                onValueChange = viewModel::setTaskbarOpacity,
                valueRange = 0.1f..1f,
                valueDisplay = "${(s.taskbar.opacity * 100).roundToInt()}%",
            )
        }
        if (s.taskbar.theme == TaskbarTheme.GLASS) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_taskbar_glass_blur),
                value = s.taskbar.glassBlur.toFloat(),
                onValueChange = { viewModel.setTaskbarGlassBlur(it.roundToInt()) },
                valueRange = 0f..80f,
                valueDisplay = "${s.taskbar.glassBlur} dp",
            )
        }
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_taskbar_icon_size),
            value = s.taskbar.iconSize.toFloat(),
            onValueChange = { viewModel.setTaskbarIconSize(it.roundToInt()) },
            valueRange = 32f..72f,
            valueDisplay = "${s.taskbar.iconSize} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_taskbar_spacing),
            value = s.taskbar.iconSpacing.toFloat(),
            onValueChange = { viewModel.setTaskbarIconSpacing(it.roundToInt()) },
            valueRange = 0f..24f,
            valueDisplay = "${s.taskbar.iconSpacing} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_anim),
            options = listOf(
                TaskbarAnim.SLIDE to stringResource(R.string.settings_taskbar_anim_slide),
                TaskbarAnim.FADE to stringResource(R.string.settings_taskbar_anim_fade),
                TaskbarAnim.SCALE to stringResource(R.string.settings_taskbar_anim_scale),
                TaskbarAnim.RISE to stringResource(R.string.settings_taskbar_anim_rise),
                TaskbarAnim.BOUNCE to stringResource(R.string.settings_taskbar_anim_bounce),
                TaskbarAnim.FLIP to stringResource(R.string.settings_taskbar_anim_flip),
                TaskbarAnim.UNFOLD to stringResource(R.string.settings_taskbar_anim_unfold),
                TaskbarAnim.POP to stringResource(R.string.settings_taskbar_anim_pop),
            ),
            selected = s.taskbar.taskbarAnim,
            onSelect = viewModel::setTaskbarAnim,
            icon = null,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_launch_glow),
            options = listOf(
                LaunchGlow.APP to stringResource(R.string.settings_taskbar_launch_glow_app),
                LaunchGlow.ACCENT to stringResource(R.string.settings_taskbar_launch_glow_accent),
                LaunchGlow.WHITE to stringResource(R.string.settings_taskbar_launch_glow_white),
                LaunchGlow.OFF to stringResource(R.string.settings_taskbar_launch_glow_off),
            ),
            selected = s.taskbar.launchGlow,
            onSelect = viewModel::setTaskbarLaunchGlow,
            icon = null,
        )
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_tb_group_handles),
        icon = Icons.Rounded.Gesture,
        sharedByBothDisplays = true,
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_handles),
            options = listOf(
                TaskbarHandles.BOTH to stringResource(R.string.settings_taskbar_handles_both),
                TaskbarHandles.LEFT to stringResource(R.string.settings_taskbar_handles_left),
                TaskbarHandles.RIGHT to stringResource(R.string.settings_taskbar_handles_right),
                TaskbarHandles.CENTER to stringResource(R.string.settings_taskbar_handles_center),
            ),
            selected = s.taskbar.handles,
            onSelect = viewModel::setTaskbarHandles,
            icon = null,
        )
        if (!s.taskbar.keepOpen) {
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_handle_hint),
                subtitle = stringResource(R.string.settings_taskbar_handle_hint_desc),
                checked = s.taskbar.handleHint,
                onCheckedChange = viewModel::setTaskbarHandleHint,
            )
            if (s.taskbar.handleHint) {
                ExpressiveRowDivider()
                // Five looks for the pull hint: a line, one or two arrows, dots, or a text label.
                ExpressiveSegmentedControl(
                    title = stringResource(R.string.settings_taskbar_handle_style),
                    options = listOf(
                        TaskbarHandleStyle.LINE to stringResource(R.string.settings_taskbar_handle_line),
                        TaskbarHandleStyle.ARROW to stringResource(R.string.settings_taskbar_handle_arrow),
                        TaskbarHandleStyle.DOUBLE to stringResource(R.string.settings_taskbar_handle_double),
                        TaskbarHandleStyle.DOTS to stringResource(R.string.settings_taskbar_handle_dots),
                        TaskbarHandleStyle.TEXT to stringResource(R.string.settings_taskbar_handle_text),
                    ),
                    selected = s.taskbar.taskbarHandleStyle,
                    onSelect = viewModel::setTaskbarHandleStyle,
                    icon = null,
                )
            }
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_hide_button),
            subtitle = stringResource(R.string.settings_taskbar_hide_button_desc),
            checked = s.taskbar.hideButton,
            onCheckedChange = viewModel::setTaskbarHideButton,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_swipe_down_hide),
            subtitle = stringResource(R.string.settings_taskbar_swipe_down_hide_desc),
            checked = s.taskbar.swipeDownHide,
            onCheckedChange = viewModel::setTaskbarSwipeDownHide,
        )
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_tb_group_place),
        icon = Icons.Rounded.VerticalAlignBottom,
        sharedByBothDisplays = true,
    ) {
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_taskbar_bottom),
            value = s.taskbar.bottomOffset.toFloat(),
            onValueChange = { viewModel.setTaskbarBottomOffset(it.roundToInt()) },
            valueRange = 0f..64f,
            valueDisplay = "${s.taskbar.bottomOffset} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_clear_inset),
            subtitle = stringResource(R.string.settings_taskbar_clear_inset_desc),
            checked = s.taskbar.clearSystemInset,
            onCheckedChange = viewModel::setTaskbarClearSystemInset,
        )
        Text(
            text = stringResource(R.string.settings_taskbar_gestures_hint),
            color = LocalExpressivePalette.current.dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    if (picking) {
        TaskbarPinnedPicker(
            apps = apps,
            initial = s.taskbar.pinnedApps,
            onDone = { keys ->
                viewModel.setTaskbarPinned(keys)
                picking = false
            },
            onUseDock = {
                viewModel.setTaskbarPinned(null)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Picks the apps always shown in the taskbar, in the order they are ticked. */
@Composable
private fun TaskbarPinnedPicker(
    apps: List<AppItem>,
    initial: List<String>?,
    onDone: (List<String>) -> Unit,
    onUseDock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial.orEmpty()) } }
    var query by remember { mutableStateOf("") }
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val shown = sorted.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_taskbar_pinned), color = p.text) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_taskbar_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.height(360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        val ticked = app.key in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (ticked) chosen.remove(app.key) else chosen.add(app.key) }
                                .padding(vertical = 6.dp),
                        ) {
                            AppIcon(appItem = app, labelColor = p.text, showLabel = false, iconSize = 36.dp)
                            Text(
                                text = app.label,
                                color = p.text,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f).padding(start = 14.dp),
                            )
                            Checkbox(
                                checked = ticked,
                                onCheckedChange = { if (it) chosen.add(app.key) else chosen.remove(app.key) },
                                colors = CheckboxDefaults.colors(checkedColor = Accent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(chosen.toList()) }) { Text(stringResource(R.string.settings_taskbar_done)) }
        },
        dismissButton = {
            TextButton(onClick = onUseDock) { Text(stringResource(R.string.settings_taskbar_use_dock)) }
        },
    )
}

// --------------------------------------------------------------------- live preview

/** A looping demo of the taskbar: it shows, holds, hides to the chosen pull handle, and repeats. */
@Composable
internal fun TaskbarPreview(handleStyle: TaskbarHandleStyle, handles: TaskbarHandles, anim: TaskbarAnim) {
    val accent = MaterialTheme.colorScheme.primary
    var shown by remember { mutableStateOf(true) }
    val visible = org.arkikeskus.launcher.ui.component.rememberOnScreen()
    LaunchedEffect(anim, handleStyle, handles) {
        shown = true
        while (true) {
            kotlinx.coroutines.delay(1500)
            // Only while it can be seen (E28).
            org.arkikeskus.launcher.ui.component.awaitOnScreen(visible)
            shown = !shown
        }
    }
    val spec: AnimationSpec<Float> =
        if (anim == TaskbarAnim.BOUNCE || anim == TaskbarAnim.POP) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
        else tween(300)
    val p by animateFloatAsState(if (shown) 1f else 0f, spec, label = "tbprev")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onScreen(visible)) {
        Text(
            stringResource(R.string.settings_taskbar_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF11161B), Color(0xFF05080A)))),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val handleAligns = when (handles) {
                TaskbarHandles.LEFT -> listOf(Alignment.BottomStart)
                TaskbarHandles.RIGHT -> listOf(Alignment.BottomEnd)
                TaskbarHandles.CENTER -> listOf(Alignment.BottomCenter)
                else -> listOf(Alignment.BottomStart, Alignment.BottomEnd)
            }
            handleAligns.forEach { a ->
                Box(
                    Modifier.align(a).padding(horizontal = 22.dp, vertical = 4.dp).graphicsLayer { alpha = 1f - p },
                ) { PreviewHandle(handleStyle, accent) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .graphicsLayer {
                        val dist = size.height * 1.6f + 60f
                        when (anim) {
                            TaskbarAnim.FADE -> alpha = p
                            TaskbarAnim.SCALE -> { scaleX = 0.82f + 0.18f * p; scaleY = 0.82f + 0.18f * p; alpha = p }
                            TaskbarAnim.RISE -> { translationY = (1f - p) * dist; alpha = p }
                            TaskbarAnim.FLIP -> {
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                rotationX = (1f - p) * 90f
                                alpha = p
                            }
                            TaskbarAnim.UNFOLD -> { scaleX = 0.08f + 0.92f * p; alpha = p }
                            TaskbarAnim.POP -> { scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p; alpha = p }
                            else -> translationY = (1f - p) * dist // SLIDE, BOUNCE
                        }
                    },
            ) { PreviewBar(accent) }
        }
    }
}

@Composable
private fun PreviewBar(accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC12171C))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i ->
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (i == 4) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun PreviewHandle(style: TaskbarHandleStyle, accent: Color) {
    val c = Color.White.copy(alpha = 0.82f)
    when (style) {
        TaskbarHandleStyle.ARROW -> Canvas(Modifier.size(28.dp, 12.dp)) { previewChevrons(1, c) }
        TaskbarHandleStyle.DOUBLE -> Canvas(Modifier.size(28.dp, 18.dp)) { previewChevrons(2, c) }
        TaskbarHandleStyle.DOTS -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { Box(Modifier.size(5.dp).clip(CircleShape).background(c)) }
        }
        TaskbarHandleStyle.TEXT -> Text(
            stringResource(R.string.settings_taskbar_preview_apps),
            color = c,
            fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x9912171C)).padding(horizontal = 12.dp, vertical = 3.dp),
        )
        else -> Box(Modifier.size(28.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c))
    }
}

private fun DrawScope.previewChevrons(count: Int, color: Color) {
    val w = size.width
    val h = size.height
    val sw = (h / count) * 0.18f
    val armW = w * 0.42f
    val cx = w / 2f
    for (i in 0 until count) {
        val slotTop = i * (h / count)
        val top = slotTop + (h / count) * 0.28f
        val bottom = slotTop + (h / count) * 0.72f
        drawLine(color, Offset(cx - armW, bottom), Offset(cx, top), sw, StrokeCap.Round)
        drawLine(color, Offset(cx, top), Offset(cx + armW, bottom), sw, StrokeCap.Round)
    }
}

/** Split screen from the taskbar: the one-time grant and how a drag from the bar splits. */
@Composable
internal fun TaskbarSplitCard(s: LauncherSettings, viewModel: SettingsViewModel) {
    // Android's own taskbar chosen: none of Folark's taskbar settings apply.
    if (s.taskbar.source == TaskbarSource.SYSTEM) return
    val context = LocalContext.current
    var splitReady by remember { mutableStateOf(splitPermissionGranted(context)) }
    LifecycleResumeEffect(Unit) {
        splitReady = splitPermissionGranted(context)
        onPauseOrDispose { }
    }
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_tb_group_split),
        icon = Icons.Rounded.VerticalSplit,
        sharedByBothDisplays = true,
    ) {
        val grantCommand = ShellSplit.grantCommand(context.packageName)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_taskbar_split_setup),
            description = if (splitReady) {
                stringResource(R.string.settings_taskbar_split_ready)
            } else {
                stringResource(R.string.settings_taskbar_split_needed, grantCommand)
            },
            onClick = {
                if (!splitReady) {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    catching { clipboard?.setPrimaryClip(ClipData.newPlainText("adb", grantCommand)) }
                    Toast.makeText(context, R.string.settings_taskbar_split_copied, Toast.LENGTH_SHORT).show()
                }
            },
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_split_side),
            options = listOf(
                TaskbarSplitSide.LEFT to stringResource(R.string.settings_taskbar_split_side_left),
                TaskbarSplitSide.RIGHT to stringResource(R.string.settings_taskbar_split_side_right),
            ),
            selected = s.taskbar.splitSide,
            onSelect = viewModel::setTaskbarSplitSide,
            icon = null,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_three_app),
            subtitle = stringResource(R.string.settings_taskbar_three_app_desc),
            checked = s.taskbar.threeApp,
            onCheckedChange = viewModel::setTaskbarThreeApp,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_split_controls),
            subtitle = stringResource(R.string.settings_taskbar_split_controls_desc),
            checked = s.taskbar.splitControls,
            onCheckedChange = viewModel::setTaskbarSplitControls,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_recents_button),
            options = listOf(
                TaskbarRecentsButton.OFF to stringResource(R.string.settings_taskbar_recents_button_off),
                TaskbarRecentsButton.SYSTEM to stringResource(R.string.settings_taskbar_recents_button_system),
                TaskbarRecentsButton.DESKTOP to stringResource(R.string.settings_taskbar_recents_button_desktop),
                TaskbarRecentsButton.FOLARK to stringResource(R.string.settings_taskbar_recents_button_folark),
            ),
            selected = s.taskbar.recentsButton,
            onSelect = viewModel::setTaskbarRecentsButton,
            icon = null,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_quick_switch),
            subtitle = stringResource(R.string.settings_taskbar_quick_switch_desc),
            checked = s.taskbar.quickSwitch,
            onCheckedChange = viewModel::setTaskbarQuickSwitch,
        )
        SettingsAdvanced {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_edge_snap),
                subtitle = stringResource(R.string.settings_taskbar_edge_snap_desc),
                checked = s.taskbar.edgeSnap,
                onCheckedChange = viewModel::setTaskbarEdgeSnap,
            )
                ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_system_drag),
                subtitle = stringResource(R.string.settings_taskbar_system_drag_desc),
                checked = s.taskbar.systemDrag,
                onCheckedChange = viewModel::setTaskbarSystemDrag,
            )
        }
    }
}
