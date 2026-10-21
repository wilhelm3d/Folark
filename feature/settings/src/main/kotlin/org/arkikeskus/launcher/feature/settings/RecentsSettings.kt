package org.arkikeskus.launcher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.roundToInt

/**
 * Settings ▸ Recents: Folark's own Recents screen, everything about it switchable and adjustable.
 * Shared by both displays (the columns are set for each).
 */
@Composable
internal fun RecentsSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var canList by remember { mutableStateOf(ShellSplit.hasDump(context)) }
    LifecycleResumeEffect(Unit) {
        canList = ShellSplit.hasDump(context)
        onPauseOrDispose { }
    }
    // Root: asked once when this page opens, and again on "Check again".
    var rootCheck by remember { mutableIntStateOf(0) }
    var root by remember { mutableStateOf(RootShell.lastKnown) }
    LaunchedEffect(rootCheck, s.recentsThumbnails, s.recentsRootClose) {
        if (rootCheck == 0 && !s.recentsThumbnails && !s.recentsRootClose) return@LaunchedEffect
        root = withContext(Dispatchers.IO) { RootShell.available(recheck = rootCheck > 0) }
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_recents),
        icon = Icons.Rounded.GridView,
        subtitle = stringResource(R.string.settings_category_recents_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_enabled),
            subtitle = stringResource(R.string.settings_recents_enabled_desc),
            checked = s.recentsEnabled,
            onCheckedChange = viewModel::setRecentsEnabled,
        )
        if (!s.recentsEnabled) return@ExpressiveCategoryContainer
        ExpressiveRowDivider()

        if (!canList) {
            ExpressiveActionItem(
                title = stringResource(R.string.settings_recents_grant),
                description = ShellSplit.grantCommand(context.packageName),
                onClick = { copyToClipboard(context, ShellSplit.grantCommand(context.packageName)) },
            )
            ExpressiveRowDivider()
        }

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_replace_system),
            subtitle = stringResource(R.string.settings_recents_replace_system_desc),
            checked = s.recentsReplaceSystem,
            onCheckedChange = viewModel::setRecentsReplaceSystem,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_nav_handle_search),
            subtitle = stringResource(R.string.settings_nav_handle_search_desc),
            checked = s.navHandleOpensSearch,
            onCheckedChange = viewModel::setNavHandleOpensSearch,
        )
        ExpressiveRowDivider()
        if (s.recentsReplaceSystem || s.navHandleOpensSearch) {
            // The Vector (LSPosed) module asks Folark each time it acts: seen lately = active.
            val seenAt = remember(s.recentsReplaceSystem) {
                context.getSharedPreferences("folark_recents_hook", 0).getLong("seen_at", 0L)
            }
            val active = seenAt > 0L && System.currentTimeMillis() - seenAt < 7L * 24 * 3600 * 1000
            ExpressiveActionItem(
                title = stringResource(if (active) R.string.settings_recents_hook_active else R.string.settings_recents_hook_missing),
                description = stringResource(if (active) R.string.settings_recents_hook_active_desc else R.string.settings_recents_hook_missing_desc),
                onClick = { },
            )
            ExpressiveRowDivider()
        }

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_taskbar_button),
            subtitle = stringResource(R.string.settings_recents_taskbar_button_desc),
            checked = s.taskbarRecentsButton == LauncherSettings.TASKBAR_RECENTS_FOLARK,
            onCheckedChange = {
                viewModel.setTaskbarRecentsButton(if (it) LauncherSettings.TASKBAR_RECENTS_FOLARK else LauncherSettings.TASKBAR_RECENTS_SYSTEM)
            },
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- look
        RecentsLookRows(s, viewModel)
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- layout
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_recents_columns_inner),
            value = s.recentsColumnsInner,
            min = LauncherSettings.MIN_RECENTS_COLUMNS, max = LauncherSettings.MAX_RECENTS_COLUMNS_INNER,
            onValueChange = viewModel::setRecentsColumnsInner,
        )
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_recents_columns_outer),
            value = s.recentsColumnsOuter,
            min = LauncherSettings.MIN_RECENTS_COLUMNS, max = LauncherSettings.MAX_RECENTS_COLUMNS_OUTER,
            onValueChange = viewModel::setRecentsColumnsOuter,
        )
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_recents_max_apps),
            value = s.recentsMaxApps,
            min = LauncherSettings.MIN_RECENTS_APPS, max = LauncherSettings.MAX_RECENTS_APPS,
            onValueChange = viewModel::setRecentsMaxApps,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_card_height),
            value = s.recentsCardHeight.toFloat(),
            onValueChange = { viewModel.setRecentsCardHeight(it.roundToInt()) },
            valueRange = LauncherSettings.MIN_RECENTS_CARD_HEIGHT.toFloat()..LauncherSettings.MAX_RECENTS_CARD_HEIGHT.toFloat(),
            valueDisplay = "${s.recentsCardHeight} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_corners),
            value = s.recentsCornerDp.toFloat(),
            onValueChange = { viewModel.setRecentsCornerDp(it.roundToInt()) },
            valueRange = 0f..LauncherSettings.MAX_RECENTS_CORNER_DP.toFloat(),
            valueDisplay = "${s.recentsCornerDp} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_backdrop),
            value = s.recentsBackdropDim,
            onValueChange = viewModel::setRecentsBackdropDim,
            valueRange = 0f..1f,
            valueDisplay = "${(s.recentsBackdropDim * 100).roundToInt()} %",
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_recents_sort),
            options = listOf(
                LauncherSettings.RECENTS_SORT_RECENT to stringResource(R.string.settings_recents_sort_recent),
                LauncherSettings.RECENTS_SORT_NAME to stringResource(R.string.settings_recents_sort_name),
            ),
            selected = s.recentsSort,
            onSelect = viewModel::setRecentsSort,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_show_time),
            checked = s.recentsShowTime,
            onCheckedChange = viewModel::setRecentsShowTime,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_recents_card_layout),
            options = listOf(
                LauncherSettings.RECENTS_CARD_OVERLAY to stringResource(R.string.settings_recents_card_overlay),
                LauncherSettings.RECENTS_CARD_HEADER to stringResource(R.string.settings_recents_card_header),
            ),
            selected = s.recentsCardLayout,
            onSelect = viewModel::setRecentsCardLayout,
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- buttons and gestures
        RecentsButtonRows(s, viewModel)
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_confirm_close_all),
            checked = s.recentsConfirmCloseAll,
            onCheckedChange = viewModel::setRecentsConfirmCloseAll,
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- animations
        RecentsMotionRows(s, viewModel)
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- split view
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_third_in_split),
            subtitle = stringResource(R.string.settings_recents_third_in_split_desc),
            checked = s.recentsThirdInSplit,
            onCheckedChange = viewModel::setRecentsThirdInSplit,
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- root
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_thumbnails),
            subtitle = stringResource(R.string.settings_recents_thumbnails_desc),
            checked = s.recentsThumbnails,
            onCheckedChange = viewModel::setRecentsThumbnails,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_pin_keep_alive),
            subtitle = stringResource(R.string.settings_recents_pin_keep_alive_desc),
            checked = s.recentsPinKeepAlive,
            onCheckedChange = viewModel::setRecentsPinKeepAlive,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_root_close),
            subtitle = stringResource(R.string.settings_recents_root_close_desc),
            checked = s.recentsRootClose,
            onCheckedChange = viewModel::setRecentsRootClose,
        )
        ExpressiveRowDivider()
        RecentsCheckRow()
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(
                when (root) {
                    true -> R.string.settings_recents_root_yes
                    false -> R.string.settings_recents_root_no
                    null -> R.string.settings_recents_root_unknown
                },
            ),
            description = stringResource(R.string.settings_recents_root_check),
            onClick = { rootCheck++ },
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- taskbar
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_running_apps),
            subtitle = stringResource(R.string.settings_taskbar_running_apps_desc),
            checked = s.taskbarRunningApps,
            onCheckedChange = viewModel::setTaskbarRunningApps,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_taskbar_running_dots),
            subtitle = stringResource(R.string.settings_taskbar_running_dots_desc),
            checked = s.taskbarRunningDots,
            onCheckedChange = viewModel::setTaskbarRunningDots,
        )
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("adb", text))
    android.widget.Toast.makeText(context, R.string.settings_recents_copied, android.widget.Toast.LENGTH_SHORT).show()
}

/** Settings ▸ Recents ▸ Check screenshots: runs each step and shows what it answered. */
@androidx.compose.runtime.Composable
private fun RecentsCheckRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(if (running) R.string.settings_root_working else R.string.settings_recents_check),
        description = stringResource(R.string.settings_recents_check_desc),
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    report = withContext(Dispatchers.IO) {
                        runCatching { org.arkikeskus.launcher.data.root.RootTasks.diagnose(context) }
                            .getOrElse { "Check failed: ${it.javaClass.simpleName}: ${it.message}" }
                    }
                    running = false
                }
            }
        },
    )
    val text = report ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { report = null },
        title = { androidx.compose.material3.Text(stringResource(R.string.settings_recents_check)) },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    text,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { copyToClipboard(context, text) }) {
                androidx.compose.material3.Text(stringResource(R.string.settings_recents_check_copy))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { report = null }) {
                androidx.compose.material3.Text(stringResource(R.string.settings_taskbar_done))
            }
        },
    )
}
