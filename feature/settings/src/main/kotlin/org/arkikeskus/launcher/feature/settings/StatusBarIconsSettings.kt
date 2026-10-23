package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.StatusBarIcons

/**
 * Settings ▸ Appearance ▸ Android's status bar icons: switch off icons you don't want to see (the
 * VPN key, NFC, the alarm...). Changes Android's own hidden-icons list, with the System controls
 * grant or root; "Show all" puts back every icon offered here.
 */
@Composable
internal fun StatusBarIconsRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(StatusBarIcons.hidden(context)) }
    var canChange by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { canChange = withContext(Dispatchers.IO) { StatusBarIcons.canChange(context) } }
    val offered = StatusBarIcons.SLOTS.map { it.first }
    val count = hidden.count { it in offered }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_status_icons),
        description = when {
            canChange == false -> stringResource(R.string.settings_status_icons_needs)
            count == 0 -> stringResource(R.string.settings_status_icons_desc)
            else -> pluralStringResource(R.plurals.settings_status_icons_hidden, count, count)
        },
        onClick = { if (canChange == true) open = true },
    )
    if (!open) return
    fun change(block: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { block() }
        hidden = withContext(Dispatchers.IO) { StatusBarIcons.hidden(context) }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.settings_status_icons)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                StatusBarIcons.SLOTS.forEach { (slot, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = slot !in hidden,
                            onCheckedChange = { show -> change { StatusBarIcons.setHidden(context, slot, !show) } },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_status_icons_done)) } },
        dismissButton = { TextButton(onClick = { change { StatusBarIcons.showAll(context) } }) { Text(stringResource(R.string.settings_status_icons_show_all)) } },
    )
}
