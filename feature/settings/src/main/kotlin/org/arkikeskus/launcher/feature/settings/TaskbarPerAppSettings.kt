package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/**
 * Settings ▸ Taskbar ▸ Per app: apps the taskbar stays out of (games, video), and apps where it is
 * always shown, even full screen or when it isn't kept open.
 */
@Composable
internal fun TaskbarPerAppRows(s: LauncherSettings, viewModel: SettingsViewModel, apps: List<AppItem>) {
    var picking by remember { mutableStateOf<String?>(null) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_taskbar_hidden_apps),
        description = countLabel(s.taskbar.hiddenApps.size, R.string.settings_taskbar_hidden_apps_desc),
        onClick = { picking = HIDE },
    )
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_taskbar_always_apps),
        description = countLabel(s.taskbar.alwaysApps.size, R.string.settings_taskbar_always_apps_desc),
        onClick = { picking = ALWAYS },
    )
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_taskbar_autohide_apps),
        description = countLabel(s.taskbar.autoHideApps.size, R.string.settings_taskbar_autohide_apps_desc),
        onClick = { picking = AUTOHIDE },
    )
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_taskbar_mini_apps),
        description = countLabel(s.taskbar.miniApps.size, R.string.settings_taskbar_mini_apps_desc),
        onClick = { picking = MINI },
    )
    when (picking) {
        HIDE -> PackagePicker(
            title = stringResource(R.string.settings_taskbar_hidden_apps),
            apps = apps,
            initial = s.taskbar.hiddenApps,
            onDone = { viewModel.setTaskbarHiddenApps(it); picking = null },
            onDismiss = { picking = null },
        )
        ALWAYS -> PackagePicker(
            title = stringResource(R.string.settings_taskbar_always_apps),
            apps = apps,
            initial = s.taskbar.alwaysApps,
            onDone = { viewModel.setTaskbarAlwaysApps(it); picking = null },
            onDismiss = { picking = null },
        )
        AUTOHIDE -> PackagePicker(
            title = stringResource(R.string.settings_taskbar_autohide_apps),
            apps = apps,
            initial = s.taskbar.autoHideApps,
            onDone = { viewModel.setTaskbarAutoHideApps(it); picking = null },
            onDismiss = { picking = null },
        )
        MINI -> PackagePicker(
            title = stringResource(R.string.settings_taskbar_mini_apps),
            apps = apps,
            initial = s.taskbar.miniApps,
            onDone = { viewModel.setTaskbarMiniApps(it); picking = null },
            onDismiss = { picking = null },
        )
    }
}

private const val HIDE = "hide"
private const val ALWAYS = "always"
private const val AUTOHIDE = "autohide"
private const val MINI = "mini"

@Composable
private fun countLabel(count: Int, desc: Int): String =
    if (count == 0) stringResource(desc) else stringResource(R.string.settings_taskbar_apps_count, count)

/** Ticks apps by package (one row per app, both profiles together). */
@Composable
private fun PackagePicker(
    title: String,
    apps: List<AppItem>,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var query by remember { mutableStateOf("") }
    val sorted = remember(apps) { apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
    val shown = sorted.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(title, color = p.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_taskbar_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.height(360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        val ticked = app.packageName in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (ticked) chosen.remove(app.packageName) else chosen.add(app.packageName) }
                                .padding(vertical = 6.dp),
                        ) {
                            AppIcon(appItem = app, labelColor = p.text, showLabel = false, iconSize = 36.dp)
                            Text(app.label, color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
                            Checkbox(
                                checked = ticked,
                                onCheckedChange = { if (it) chosen.add(app.packageName) else chosen.remove(app.packageName) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.toSet()) }) { Text(stringResource(R.string.settings_taskbar_done)) } },
        dismissButton = { TextButton(onClick = { chosen.clear() }) { Text(stringResource(R.string.settings_taskbar_clear)) } },
    )
}
