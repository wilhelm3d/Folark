package org.arkikeskus.launcher.feature.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.SystemUpdates

/**
 * Settings ▸ Backup & restore ▸ System updates: on lets Motorola's updates run as usual; off blocks
 * them (with root) by disabling Motorola's OTA app and its helpers, to hold a kernel your root
 * method still works on. Only shown on a phone that has Motorola's OTA app. Needs root to switch.
 */
@Composable
internal fun SystemUpdatesRow() {
    val context = LocalContext.current
    if (!remember { SystemUpdates.available(context) }) return
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(SystemUpdates.enabled(context)) }
    var working by remember { mutableStateOf(false) }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_system_updates),
        subtitle = stringResource(
            if (enabled) R.string.settings_system_updates_on_desc else R.string.settings_system_updates_off_desc,
        ),
        checked = enabled,
        shared = true,
        onCheckedChange = { want ->
            if (!working) {
                working = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { SystemUpdates.setEnabled(context, want) }
                    // Show the real state afterwards: if root refused, it stays as it was.
                    enabled = withContext(Dispatchers.IO) { SystemUpdates.enabled(context) }
                    working = false
                    if (!ok) {
                        Toast.makeText(context, R.string.settings_system_updates_need_root, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
    )
}
