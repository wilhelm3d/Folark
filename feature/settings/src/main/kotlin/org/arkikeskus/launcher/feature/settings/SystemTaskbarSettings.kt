package org.arkikeskus.launcher.feature.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.SystemTaskbar
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Settings ▸ Taskbar ▸ Which taskbar: Folark's own, or Android's (the system launcher's, which
 * also runs gesture navigation and quick switch). For Android's: its mode (always, by a swipe up,
 * off), set with root, or on Motorola's own settings page without root.
 */
@Composable
internal fun SystemTaskbarRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_taskbar_source),
        options = listOf(
            LauncherSettings.TASKBAR_SOURCE_FOLARK to stringResource(R.string.settings_taskbar_source_folark),
            LauncherSettings.TASKBAR_SOURCE_SYSTEM to stringResource(R.string.settings_taskbar_source_system),
        ),
        selected = s.taskbarSource,
        onSelect = viewModel::setTaskbarSource,
    )
    if (s.taskbarSource != LauncherSettings.TASKBAR_SOURCE_SYSTEM) return
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_system_taskbar_mode),
        options = listOf(
            SystemTaskbar.MODE_ALWAYS to stringResource(R.string.settings_system_taskbar_always),
            SystemTaskbar.MODE_GESTURE to stringResource(R.string.settings_system_taskbar_gesture),
            SystemTaskbar.MODE_OFF to stringResource(R.string.settings_system_taskbar_off),
        ),
        selected = s.systemTaskbarMode,
        onSelect = { mode ->
            viewModel.setSystemTaskbarMode(mode)
            scope.launch {
                val done = withContext(Dispatchers.IO) { SystemTaskbar.apply(mode) }
                if (done) {
                    Toast.makeText(context, R.string.settings_system_taskbar_applied, Toast.LENGTH_SHORT).show()
                } else {
                    // No root: Motorola's own page, where the same choice is one tap.
                    Toast.makeText(context, R.string.settings_system_taskbar_no_root, Toast.LENGTH_LONG).show()
                    SystemTaskbar.openSettings(context)
                }
            }
        },
    )
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_system_taskbar_open),
        description = stringResource(R.string.settings_system_taskbar_open_desc),
        onClick = { SystemTaskbar.openSettings(context) },
    )
}
