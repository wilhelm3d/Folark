package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.data.HiddenChannel
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/**
 * Settings ▸ Notifications ▸ Muted channels: the channels muted from the notifications widget's
 * long-press menu, each with Unmute (and Unmute all). Shown only while something is muted.
 */
@Composable
internal fun MutedChannelsRow(viewModel: SettingsViewModel) {
    val muted by viewModel.hiddenChannels.collectAsStateWithLifecycle()
    if (muted.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_muted_channels),
        description = pluralStringResource(R.plurals.settings_muted_channels_count, muted.size, muted.size),
        onClick = { open = true },
        shared = true,
    )
    if (open) {
        MutedChannelsDialog(
            muted = muted,
            appLabel = viewModel::appLabel,
            onUnmute = { viewModel.unmuteChannel(it) },
            onUnmuteAll = {
                viewModel.unmuteAllChannels()
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun MutedChannelsDialog(
    muted: List<HiddenChannel>,
    appLabel: (String) -> String,
    onUnmute: (HiddenChannel) -> Unit,
    onUnmuteAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    // Read up front: the dialog's texts are not settings of their own (and stay out of the search index).
    val desc = stringResource(R.string.settings_muted_channels_desc)
    val unmute = stringResource(R.string.settings_muted_channels_unmute)
    val unmuteAll = stringResource(R.string.settings_muted_channels_unmute_all)
    val done = stringResource(R.string.settings_taskbar_done)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_muted_channels), color = p.text) },
        text = {
            Column {
                Text(
                    desc,
                    color = p.dim,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(muted, key = { it.packageName + "\t" + it.channelId }) { channel ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(appLabel(channel.packageName), color = p.text, fontSize = 15.sp)
                                Text(
                                    channel.name ?: channel.channelId,
                                    color = p.dim,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                            TextButton(onClick = { onUnmute(channel) }) {
                                Text(unmute)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(done) }
        },
        dismissButton = {
            TextButton(onClick = onUnmuteAll) { Text(unmuteAll) }
        },
    )
}
