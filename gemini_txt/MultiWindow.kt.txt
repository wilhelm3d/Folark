package org.arkikeskus.launcher.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon

/**
 * One drop target of the bar shown at the top while an icon is dragged ("Remove", "Split"). It only
 * draws itself: the caller publishes its bounds (through [modifier]) and decides what [active] means.
 */
@Composable
internal fun DragTargetPill(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (active) activeColor else Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * Picks the app that shares the screen with [first]. A launcher cannot put a single app into split
 * screen and leave the other half to the system, so a split always starts as a pair. With the box
 * ticked, [onPick] is also asked to keep the pair as a shortcut on the home screen.
 */
@Composable
internal fun SplitPartnerDialog(
    first: AppItem,
    apps: List<AppItem>,
    onPick: (second: AppItem, addToHome: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var addToHome by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_pick_title, first.label)) },
        text = {
            Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { addToHome = !addToHome },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = addToHome, onCheckedChange = { addToHome = it })
                Text(
                    text = stringResource(R.string.split_pick_add_home),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(apps, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app, addToHome) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIcon(
                            appItem = app,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            iconSize = 40.dp,
                            showLabel = false,
                            aquamorphicTouchEnabled = false,
                        )
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_pick_cancel)) }
        },
    )
}
