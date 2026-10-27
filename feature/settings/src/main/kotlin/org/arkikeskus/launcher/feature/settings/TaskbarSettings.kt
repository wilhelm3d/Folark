package org.arkikeskus.launcher.feature.settings

import android.app.AppOpsManager
import android.content.Context
import android.widget.Toast
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallToAction
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
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

private fun usageAccessGranted(context: Context): Boolean = runCatching {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

private fun splitPermissionGranted(context: Context): Boolean = ShellSplit.available(context)

private fun openUsageAccess(context: Context) {
    // Straight to Folark's own switch where the system supports it, else the list of apps.
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching {
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
        if (s.taskbar.source == LauncherSettings.TASKBAR_SOURCE_SYSTEM) return@ExpressiveCategoryContainer
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
        ExpressiveRowDivider()

        if (s.taskbar.keepOpen) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_hide_full_screen),
                subtitle = stringResource(R.string.settings_taskbar_hide_full_screen_desc),
                checked = s.taskbar.hideFullScreen,
                onCheckedChange = viewModel::setTaskbarHideFullScreen,
            )
            ExpressiveRowDivider()
        }

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_handles),
            options = listOf(
                LauncherSettings.TASKBAR_HANDLES_BOTH to stringResource(R.string.settings_taskbar_handles_both),
                LauncherSettings.TASKBAR_HANDLES_LEFT to stringResource(R.string.settings_taskbar_handles_left),
                LauncherSettings.TASKBAR_HANDLES_RIGHT to stringResource(R.string.settings_taskbar_handles_right),
            ),
            selected = s.taskbar.handles,
            onSelect = viewModel::setTaskbarHandles,
            icon = null,
        )
        ExpressiveRowDivider()

        if (!s.taskbar.keepOpen) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_taskbar_handle_hint),
                subtitle = stringResource(R.string.settings_taskbar_handle_hint_desc),
                checked = s.taskbar.handleHint,
                onCheckedChange = viewModel::setTaskbarHandleHint,
            )
            ExpressiveRowDivider()
        }

        TaskbarPerAppRows(s, viewModel, apps)
        ExpressiveRowDivider()

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
                    runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("adb", grantCommand)) }
                    Toast.makeText(context, R.string.settings_taskbar_split_copied, Toast.LENGTH_SHORT).show()
                }
            },
        )
        ExpressiveRowDivider()

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
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_three_app),
            subtitle = stringResource(R.string.settings_taskbar_three_app_desc),
            checked = s.taskbar.threeApp,
            onCheckedChange = viewModel::setTaskbarThreeApp,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_split_side),
            options = listOf(
                LauncherSettings.TASKBAR_SPLIT_SIDE_LEFT to stringResource(R.string.settings_taskbar_split_side_left),
                LauncherSettings.TASKBAR_SPLIT_SIDE_RIGHT to stringResource(R.string.settings_taskbar_split_side_right),
            ),
            selected = s.taskbar.splitSide,
            onSelect = viewModel::setTaskbarSplitSide,
            icon = null,
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
                LauncherSettings.TASKBAR_RECENTS_OFF to stringResource(R.string.settings_taskbar_recents_button_off),
                LauncherSettings.TASKBAR_RECENTS_SYSTEM to stringResource(R.string.settings_taskbar_recents_button_system),
                LauncherSettings.TASKBAR_RECENTS_DESKTOP to stringResource(R.string.settings_taskbar_recents_button_desktop),
                LauncherSettings.TASKBAR_RECENTS_FOLARK to stringResource(R.string.settings_taskbar_recents_button_folark),
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
                    LauncherSettings.TASKBAR_MEDIA_POS_START to stringResource(R.string.settings_taskbar_media_position_start),
                    LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE to stringResource(R.string.settings_taskbar_media_position_middle),
                    LauncherSettings.TASKBAR_MEDIA_POS_END to stringResource(R.string.settings_taskbar_media_position_end),
                ),
                selected = s.taskbar.mediaPosition,
                onSelect = viewModel::setTaskbarMediaPosition,
                icon = null,
            )
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_taskbar_media_shape),
                options = listOf(
                    LauncherSettings.TASKBAR_MEDIA_SHAPE_CIRCLE to stringResource(R.string.settings_taskbar_media_shape_circle),
                    LauncherSettings.TASKBAR_MEDIA_SHAPE_ROUNDED to stringResource(R.string.settings_taskbar_media_shape_rounded),
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

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_taskbar_theme),
            options = listOf(
                LauncherSettings.TASKBAR_THEME_CLEAR to stringResource(R.string.settings_taskbar_theme_clear),
                LauncherSettings.TASKBAR_THEME_FROSTED to stringResource(R.string.settings_taskbar_theme_frosted),
                LauncherSettings.TASKBAR_THEME_DARK to stringResource(R.string.settings_taskbar_theme_dark),
                LauncherSettings.TASKBAR_THEME_LIGHT to stringResource(R.string.settings_taskbar_theme_light),
                LauncherSettings.TASKBAR_THEME_ACCENT to stringResource(R.string.settings_taskbar_theme_accent),
                LauncherSettings.TASKBAR_THEME_GLASS to stringResource(R.string.settings_taskbar_theme_glass),
            ),
            selected = s.taskbar.theme,
            onSelect = viewModel::setTaskbarTheme,
            icon = null,
        )
        if (s.taskbar.theme != LauncherSettings.TASKBAR_THEME_CLEAR) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_taskbar_opacity),
                value = s.taskbar.opacity,
                onValueChange = viewModel::setTaskbarOpacity,
                valueRange = 0.1f..1f,
                valueDisplay = "${(s.taskbar.opacity * 100).roundToInt()}%",
            )
        }
        if (s.taskbar.theme == LauncherSettings.TASKBAR_THEME_GLASS) {
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
        ExpressiveRowDivider()

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
