package org.arkikeskus.launcher.feature.settings

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ModuleTools
import org.arkikeskus.launcher.data.SplitDiagnostics
import org.arkikeskus.launcher.data.SplitHistory
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.SplitPickerLook
import kotlin.math.roundToInt

/**
 * Settings ▸ Recents ▸ Split screen: how two apps are put side by side (the engine), and how the
 * split picker looks, with a live preview.
 */
@Composable
internal fun SplitScreenSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val look = s.recents.picker
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_split_screen),
        icon = Icons.Rounded.HorizontalSplit,
        subtitle = stringResource(R.string.settings_split_screen_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_engine),
            options = listOf(
                LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING to stringResource(R.string.settings_split_engine_intent),
                LauncherSettings.SPLIT_ENGINE_CLASSIC to stringResource(R.string.settings_split_engine_classic),
                LauncherSettings.SPLIT_ENGINE_LSPOSED to stringResource(R.string.settings_split_engine_lsposed),
                LauncherSettings.SPLIT_ENGINE_ROOT to stringResource(R.string.settings_split_engine_root),
            ),
            selected = s.recents.splitEngine,
            onSelect = viewModel::setRecentsSplitEngine,
        )
        Hint(
            stringResource(
                when (s.recents.splitEngine) {
                    LauncherSettings.SPLIT_ENGINE_CLASSIC -> R.string.settings_split_engine_classic_desc
                    LauncherSettings.SPLIT_ENGINE_LSPOSED -> R.string.settings_split_engine_lsposed_desc
                    LauncherSettings.SPLIT_ENGINE_ROOT -> R.string.settings_split_engine_root_desc
                    else -> R.string.settings_split_engine_auto_desc
                },
            ),
        )
        SplitGuardRow()
        ExpressiveRowDivider()
        SplitCheckRow(s.recents.splitEngine)
        ExpressiveRowDivider()
        DropZonesRow(s.recents.dropZones, onToggle = { zone, on -> viewModel.setDropZone(zone, on) })
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drop_zone_haptics),
            subtitle = stringResource(R.string.settings_drop_zone_haptics_desc),
            checked = s.recents.dropZones.haptics,
            onCheckedChange = viewModel::setDropZoneHaptics,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_undo),
            subtitle = stringResource(R.string.settings_split_undo_desc),
            checked = s.recents.splitUndo,
            onCheckedChange = viewModel::setSplitUndo,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_divider_stops),
            subtitle = stringResource(R.string.settings_divider_stops_desc),
            checked = s.recents.dividerStops,
            onCheckedChange = viewModel::setDividerStops,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_free_divider),
            subtitle = stringResource(R.string.settings_split_free_divider_desc),
            checked = s.recents.freeDivider,
            onCheckedChange = viewModel::setSplitFreeDivider,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_three_even),
            subtitle = stringResource(R.string.settings_split_three_even_desc),
            checked = s.recents.threeEven,
            onCheckedChange = viewModel::setSplitThreeEven,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_ratio_menu),
            subtitle = stringResource(R.string.settings_split_ratio_menu_desc),
            checked = s.recents.splitRatioMenu,
            onCheckedChange = viewModel::setSplitRatioMenu,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notification_split),
            subtitle = stringResource(R.string.settings_notification_split_desc),
            checked = s.recents.notificationSplit,
            onCheckedChange = viewModel::setNotificationSplit,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_open_with),
            subtitle = stringResource(R.string.settings_split_open_with_desc),
            checked = s.recents.openWith,
            onCheckedChange = viewModel::setSplitOpenWith,
        )
        ExpressiveRowDivider()
        RestartSystemUiRow()
        ExpressiveRowDivider()
        ModuleLogRow()
    }

    // While a slider is dragged the preview follows the finger (the saved value catches up after).
    var dragIcon by remember { mutableStateOf<Float?>(null) }
    var dragOpacity by remember { mutableStateOf<Float?>(null) }
    val shown = look.copy(
        iconSize = dragIcon?.roundToInt() ?: look.iconSize,
        opacity = dragOpacity ?: look.opacity,
    )

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_split_picker),
        icon = Icons.Rounded.Apps,
        subtitle = stringResource(R.string.settings_split_picker_desc),
        sharedByBothDisplays = true,
    ) {
        SplitPickerPreview(shown)
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_split_picker_columns),
            value = look.columns,
            min = SplitPickerLook.MIN_COLUMNS,
            max = SplitPickerLook.MAX_COLUMNS,
            onValueChange = viewModel::setSplitPickerColumns,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_split_picker_icon),
            value = look.iconSize.toFloat(),
            onValueChange = { viewModel.setSplitPickerIconSize(it.roundToInt()) },
            valueRange = SplitPickerLook.MIN_ICON.toFloat()..SplitPickerLook.MAX_ICON.toFloat(),
            valueDisplay = "${shown.iconSize} dp",
            onDragValue = { dragIcon = it },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_labels),
            checked = look.labels,
            onCheckedChange = viewModel::setSplitPickerLabels,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_folders),
            subtitle = stringResource(R.string.settings_split_picker_folders_desc),
            checked = look.folders,
            onCheckedChange = viewModel::setSplitPickerFolders,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_search),
            checked = look.search,
            onCheckedChange = viewModel::setSplitPickerSearch,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_recents),
            options = listOf(
                SplitPickerLook.RECENTS_OFF to stringResource(R.string.settings_split_picker_recents_off),
                SplitPickerLook.RECENTS_ICONS to stringResource(R.string.settings_split_picker_recents_icons),
                SplitPickerLook.RECENTS_PREVIEWS to stringResource(R.string.settings_split_picker_recents_previews),
            ),
            selected = look.recents,
            onSelect = viewModel::setSplitPickerRecents,
        )
        if (look.recents == SplitPickerLook.RECENTS_PREVIEWS) {
            Hint(stringResource(R.string.settings_split_picker_recents_previews_desc))
        }
        if (look.recents != SplitPickerLook.RECENTS_OFF) {
            ExpressiveRowDivider()
            ExpressiveStepperRow(
                title = stringResource(R.string.settings_split_picker_recents_count),
                value = look.recentsCount,
                min = SplitPickerLook.MIN_RECENTS,
                max = SplitPickerLook.MAX_RECENTS,
                onValueChange = viewModel::setSplitPickerRecentsCount,
            )
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_favorites),
            subtitle = stringResource(R.string.settings_split_picker_favorites_desc),
            checked = look.favorites,
            onCheckedChange = viewModel::setSplitPickerFavorites,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_pairs),
            subtitle = stringResource(R.string.settings_split_picker_pairs_desc),
            checked = look.recentPairs,
            onCheckedChange = viewModel::setSplitPickerRecentPairs,
        )
        if (look.recentPairs) {
            val context = LocalContext.current
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_split_picker_forget),
                description = stringResource(R.string.settings_split_picker_forget_desc),
                onClick = {
                    SplitHistory.clear(context)
                    android.widget.Toast.makeText(context, R.string.settings_split_picker_forgotten, android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_peek_toggle),
            subtitle = stringResource(R.string.settings_split_picker_peek_toggle_desc),
            checked = look.peekToggle,
            onCheckedChange = viewModel::setSplitPickerPeekToggle,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_long_press),
            subtitle = stringResource(R.string.settings_split_picker_long_press_desc),
            checked = look.longPress,
            onCheckedChange = viewModel::setSplitPickerLongPress,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_sort),
            options = listOf(
                SplitPickerLook.SORT_NAME to stringResource(R.string.settings_split_picker_sort_name),
                SplitPickerLook.SORT_USAGE to stringResource(R.string.settings_split_picker_sort_usage),
            ),
            selected = look.sort,
            onSelect = viewModel::setSplitPickerSort,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_split_picker_opacity),
            value = look.opacity,
            onValueChange = viewModel::setSplitPickerOpacity,
            valueRange = SplitPickerLook.MIN_OPACITY..SplitPickerLook.MAX_OPACITY,
            valueDisplay = "${(shown.opacity * 100).roundToInt()}%",
            onDragValue = { dragOpacity = it },
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_open_anim),
            options = listOf(
                SplitPickerLook.OPEN_ZOOM to stringResource(R.string.settings_split_picker_open_zoom),
                SplitPickerLook.OPEN_FADE to stringResource(R.string.settings_split_picker_open_fade),
                SplitPickerLook.OPEN_NONE to stringResource(R.string.settings_split_picker_open_none),
            ),
            selected = look.openAnim,
            onSelect = viewModel::setSplitPickerOpenAnim,
        )
        Hint(
            stringResource(
                when (look.openAnim) {
                    SplitPickerLook.OPEN_FADE -> R.string.settings_split_picker_open_fade_desc
                    SplitPickerLook.OPEN_NONE -> R.string.settings_split_picker_open_none_desc
                    else -> R.string.settings_split_picker_open_zoom_desc
                },
            ),
        )
    }
}

/**
 * Shown while the crash guard has paused the module's split requests (System UI restarted right
 * after one): what happened, and a way to allow them again.
 */
@Composable
private fun SplitGuardRow() {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(SplitThree.pausedInfo(context)) }
    LifecycleResumeEffect(Unit) {
        paused = SplitThree.pausedInfo(context)
        onPauseOrDispose { }
    }
    val info = paused ?: return
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_split_native_paused),
        description = stringResource(
            R.string.settings_split_native_paused_desc,
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(info.first)),
        ),
        onClick = {
            SplitThree.resume(context)
            paused = null
        },
    )
}

/** Restart System UI (root), after the module was installed or updated: asks first. */
@Composable
private fun RestartSystemUiRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_restart_systemui),
        description = stringResource(R.string.settings_restart_systemui_desc),
        onClick = { asking = true },
    )
    if (!asking) return
    AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.settings_restart_systemui)) },
        text = { Text(stringResource(R.string.settings_restart_systemui_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ModuleTools.restartSystemUi() }
                    if (!ok) android.widget.Toast.makeText(context, R.string.settings_restart_systemui_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.settings_restart_systemui_go)) }
        },
        dismissButton = {
            TextButton(onClick = { asking = false }) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** The module's own log lines (root), to see what it did or why a split didn't form. */
@Composable
private fun ModuleLogRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val noRoot = stringResource(R.string.settings_module_log_no_root)
    val empty = stringResource(R.string.settings_module_log_empty)
    ExpressiveActionItem(
        title = stringResource(if (running) R.string.settings_root_working else R.string.settings_module_log),
        description = stringResource(R.string.settings_module_log_desc),
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    val log = withContext(Dispatchers.IO) { runCatching { ModuleTools.moduleLog() }.getOrNull() }
                    text = when {
                        log == null -> noRoot
                        log.isEmpty() -> empty
                        else -> log
                    }
                    running = false
                }
            }
        },
    )
    val shown = text ?: return
    AlertDialog(
        onDismissRequest = { text = null },
        title = { Text(stringResource(R.string.settings_module_log)) },
        text = {
            SelectionContainer {
                Text(
                    shown,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("Folark module log", shown))
            }) { Text(stringResource(R.string.settings_recents_check_copy)) }
        },
        dismissButton = {
            TextButton(onClick = { text = null }) { Text(stringResource(R.string.settings_taskbar_done)) }
        },
    )
}

/** Check split screen: runs [SplitDiagnostics] and shows what each part answered. */
@Composable
private fun SplitCheckRow(engine: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(if (running) R.string.settings_root_working else R.string.settings_split_check),
        description = stringResource(R.string.settings_split_check_desc),
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    report = withContext(Dispatchers.IO) {
                        runCatching { SplitDiagnostics.report(context, engine) }
                            .getOrElse { "Check failed: ${it.javaClass.simpleName}: ${it.message}" }
                    }
                    running = false
                }
            }
        },
    )
    val text = report ?: return
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text(stringResource(R.string.settings_split_check)) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("split check", text))
            }) { Text(stringResource(R.string.settings_recents_check_copy)) }
        },
        dismissButton = {
            TextButton(onClick = { report = null }) { Text(stringResource(R.string.settings_taskbar_done)) }
        },
    )
}

/**
 * Drop zones: a small screen showing the six zones where they are (the ones on lit, tap one to
 * switch it), and the same as labelled chips.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DropZonesRow(zones: DropZones, onToggle: (String, Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.settings_drop_zones), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.settings_drop_zones_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF11161B), Color(0xFF05080A)))),
        ) {
            val w = maxWidth
            val h = maxHeight
            // Where each zone sits, as fractions of the screen (as Recents and the taskbar test them).
            @Composable
            fun zone(id: String, x0: Float, y0: Float, x1: Float, y1: Float, label: Int) {
                val on = zones.isOn(id)
                Box(
                    Modifier
                        .offset(w * x0, h * y0)
                        .size(w * (x1 - x0), h * (y1 - y0))
                        .padding(2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) accent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.04f))
                        .border(1.dp, if (on) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable { onToggle(id, !on) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label),
                        color = Color.White.copy(alpha = if (on) 0.95f else 0.4f),
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
            zone(DropZones.LEFT, 0f, 0.16f, 0.32f, 0.84f, R.string.settings_drop_zone_left)
            zone(DropZones.RIGHT, 0.68f, 0.16f, 1f, 0.84f, R.string.settings_drop_zone_right)
            zone(DropZones.TOP, 0f, 0f, 1f, 0.16f, R.string.settings_drop_zone_top)
            zone(DropZones.BOTTOM, 0f, 0.84f, 1f, 1f, R.string.settings_drop_zone_bottom)
            zone(DropZones.PEEK, 0.38f, 0f, 0.62f, 0.22f, R.string.settings_drop_zone_peek)
            zone(DropZones.THREE, 0.38f, 0.78f, 0.62f, 1f, R.string.settings_drop_zone_three)
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                DropZones.LEFT to R.string.settings_drop_zone_left,
                DropZones.RIGHT to R.string.settings_drop_zone_right,
                DropZones.TOP to R.string.settings_drop_zone_top,
                DropZones.BOTTOM to R.string.settings_drop_zone_bottom,
                DropZones.PEEK to R.string.settings_drop_zone_peek,
                DropZones.THREE to R.string.settings_drop_zone_three,
            ).forEach { (id, label) ->
                val on = zones.isOn(id)
                androidx.compose.material3.FilterChip(
                    selected = on,
                    onClick = { onToggle(id, !on) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

// --------------------------------------------------------------------- live preview

/** A small split screen: an app on the left, the picker (as set) on the right. */
@Composable
private fun SplitPickerPreview(look: SplitPickerLook) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.settings_split_picker_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(22.dp))
                // A colourful wallpaper, so the picker's see-through background shows.
                .background(Brush.linearGradient(listOf(Color(0xFF3A2E8C), Color(0xFF0E6E73), Color(0xFF8C3A5A))))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The first app: a plain app window.
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color(0xFFECEFF3)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.85f)))
                repeat(6) { i ->
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.9f else 0.65f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFC9CFD6)))
                }
            }
            PickerMock(look, accent, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * The picker in the preview. With an opening animation set, it plays on a loop: the highlighted
 * app is "tapped" and opens the way it will on the phone (Zoom grows it out of its icon, Fade fades
 * it in); Off shows no animation, as Android's own is used then.
 */
@Composable
private fun PickerMock(look: SplitPickerLook, accent: Color, modifier: Modifier) {
    val scale = 0.42f
    val icon = (look.iconSize * scale).dp
    val gap = 4.dp
    val density = LocalDensity.current
    var box by remember { mutableStateOf(Rect.Zero) }
    var tile by remember { mutableStateOf<Rect?>(null) }
    val p = remember { Animatable(0f) }
    val anim = if (ValueAnimator.areAnimatorsEnabled()) look.openAnim else SplitPickerLook.OPEN_NONE
    LaunchedEffect(anim) {
        p.snapTo(0f)
        if (anim == SplitPickerLook.OPEN_NONE) return@LaunchedEffect
        while (true) {
            delay(PREVIEW_IDLE_MS)
            p.animateTo(1f, tween(PREVIEW_OPEN_MS, easing = FastOutSlowInEasing))
            delay(PREVIEW_HOLD_MS)
            p.animateTo(0f, tween(PREVIEW_RESET_MS))
        }
    }
    val zoom = anim == SplitPickerLook.OPEN_ZOOM
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0E12).copy(alpha = look.opacity))
            .onGloballyPositioned { box = it.boundsInRoot() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .graphicsLayer {
                    val q = if (zoom) p.value else 0f
                    scaleX = 1f - 0.06f * q
                    scaleY = 1f - 0.06f * q
                    alpha = 1f - 0.7f * q
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (look.search) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)))
            }
            // Favorites (your marked apps) and Paired before, as small rows of icons.
            listOf(look.favorites to 0, look.recentPairs to 4).filter { it.first }.forEach { (_, from) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(3) { i -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(mockColor(from + i))) }
                }
            }
            if (look.recents != SplitPickerLook.RECENTS_OFF) {
                val previews = look.recents == SplitPickerLook.RECENTS_PREVIEWS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(look.recentsCount.coerceAtMost(if (previews) 4 else 5)) { i ->
                        if (previews) {
                            Box(Modifier.width(16.dp).height(24.dp).clip(RoundedCornerShape(3.dp)).background(mockColor(i + 3).copy(alpha = 0.8f)))
                        } else {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(mockColor(i + 3)))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            }
            Box(Modifier.fillMaxSize()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val rows = 6
                    var index = 0
                    repeat(rows) { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(look.columns) { c ->
                                val folder = look.folders && r == 0 && c < 2
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (folder) {
                                        Box(
                                            Modifier.size(icon).clip(RoundedCornerShape(icon * 0.3f)).background(Color.White.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                repeat(2) { y ->
                                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                        repeat(2) { x -> Box(Modifier.size(icon * 0.3f).clip(CircleShape).background(mockColor(c * 4 + y * 2 + x))) }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (!(look.folders && r == 0)) {
                                        val first = index == 0
                                        Box(
                                            Modifier
                                                .size(icon)
                                                .then(if (first) Modifier.onGloballyPositioned { tile = it.boundsInRoot() } else Modifier)
                                                .clip(CircleShape)
                                                .background(if (first) accent else mockColor(index)),
                                        )
                                        index++
                                    } else {
                                        Spacer(Modifier.size(icon))
                                    }
                                    if (look.labels) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(Modifier.width(icon * 0.9f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.45f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // The app opening (Zoom: out of its icon; Fade: over the whole picker).
        val from = tile
        if (p.value > 0f && from != null && box.width > 0f) {
            val end = Rect(Offset.Zero, box.size)
            val start = from.translate(-box.topLeft)
            val r = if (zoom) lerp(start, end, p.value) else end
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                        .size(r.width.toDp(), r.height.toDp())
                        .graphicsLayer { alpha = if (zoom) 1f else p.value }
                        .clip(RoundedCornerShape(if (zoom) icon * 0.5f * (1f - p.value) else 0.dp))
                        .background(if (zoom) lerpColor(accent, Color(0xFF1B2127), (p.value * 1.6f).coerceAtMost(1f)) else Color(0xFF1B2127)),
                    contentAlignment = Alignment.Center,
                ) {
                    val size = if (zoom) icon + (34.dp - icon) * p.value else 34.dp
                    Box(Modifier.size(size).clip(CircleShape).background(accent))
                }
            }
        }
    }
}

private const val PREVIEW_IDLE_MS = 1300L
private const val PREVIEW_OPEN_MS = 420
private const val PREVIEW_HOLD_MS = 900L
private const val PREVIEW_RESET_MS = 260

private val MOCK = listOf(
    Color(0xFFEF6C6C), Color(0xFFF2B35A), Color(0xFF6CC28A), Color(0xFF5A9BF2),
    Color(0xFFB07CF2), Color(0xFF4FC3C7), Color(0xFFF27CB4), Color(0xFF9AA5B1),
)

private fun mockColor(i: Int): Color = MOCK[i.mod(MOCK.size)]
