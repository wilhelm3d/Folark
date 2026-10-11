package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem

/**
 * Picks one icon for one app out of the installed icon packs ([packs]: package to label). The grid
 * lists the icons the chosen pack ships ([loadNames]), each drawn on demand ([loadIcon]); a search
 * field narrows them by name. [onReset] returns the app to its normal icon.
 */
@Composable
internal fun IconPickerDialog(
    app: AppItem,
    packs: List<Pair<String, String>>,
    hasCustomIcon: Boolean,
    loadNames: suspend (pack: String) -> List<String>,
    loadIcon: suspend (pack: String, drawable: String) -> ImageBitmap?,
    onPick: (pack: String, drawable: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenPack by remember { mutableStateOf<String?>(null) }
    val pack = chosenPack ?: packs.firstOrNull()?.first
    var query by remember { mutableStateOf("") }
    // null = still loading the pack's icon list.
    val names by produceState<List<String>?>(initialValue = null, pack) {
        value = null
        value = if (pack == null) emptyList() else loadNames(pack)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icon_picker_title, app.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pack == null) {
                    Text(
                        text = stringResource(R.string.icon_picker_no_packs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (packs.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            packs.forEach { (packageName, label) ->
                                FilterChip(
                                    selected = packageName == pack,
                                    onClick = { chosenPack = packageName },
                                    label = { Text(label, maxLines = 1) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.icon_picker_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val all = names
                    val shown = remember(all, query) {
                        val q = query.trim()
                        if (all == null || q.isEmpty()) all.orEmpty() else all.filter { it.contains(q, ignoreCase = true) }
                    }
                    when {
                        all == null -> Text(stringResource(R.string.icon_picker_loading))
                        shown.isEmpty() -> Text(stringResource(R.string.icon_picker_empty))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(56.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                        ) {
                            items(items = shown, key = { it }) { name ->
                                val bitmap by produceState<ImageBitmap?>(initialValue = null, pack, name) {
                                    value = loadIcon(pack, name)
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPick(pack, name) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    bitmap?.let {
                                        Image(bitmap = it, contentDescription = name, modifier = Modifier.size(42.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_pick_cancel)) }
        },
        dismissButton = {
            if (hasCustomIcon) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.icon_picker_reset)) }
            }
        },
    )
}
