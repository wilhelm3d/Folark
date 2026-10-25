package org.arkikeskus.launcher.feature.settings

import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import java.util.UUID

// Settings ▸ App pairs: the list, a row, and the dialogs to make one (split out of SettingsScreen.kt).

@Composable
internal fun AppPairsManager(
    apps: List<AppItem>,
    appPairs: List<AppPair>,
    onSaveAppPair: (AppPair) -> Unit,
    onDeleteAppPair: (String) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onAddAppPairToHome: (AppPair) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    taskbarPairs: List<String> = emptyList(),
    onToggleTaskbar: (AppPair) -> Unit = {},
) {
    // System Back returns to Settings instead of closing it.
    BackHandler(onBack = onBack)
    val palette = LocalExpressivePalette.current
    var showCreator by remember { mutableStateOf(false) }

    Surface(modifier = modifier, color = palette.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_app_pairs),
                    color = palette.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_close), color = Accent)
                }
            }

            ExpressiveActionItem(
                title = stringResource(R.string.settings_create_app_pair),
                description = stringResource(R.string.settings_app_pairs_desc),
                trailingIcon = Icons.Rounded.Add,
                onClick = { showCreator = true },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(appPairs, key = { it.id }) { pair ->
                    AppPairRow(
                        pair = pair,
                        apps = apps,
                        onLaunch = { onLaunchAppPair(pair) },
                        onAddToHome = { onAddAppPairToHome(pair) },
                        onDelete = { onDeleteAppPair(pair.id) },
                        onTaskbar = { onToggleTaskbar(pair) },
                        onTaskbarPinned = pair.id in taskbarPairs,
                    )
                }
            }
        }
    }

    if (showCreator) {
        AppPairCreatorDialog(
            apps = apps,
            onDismiss = { showCreator = false },
            onConfirm = { name, app1, app2, app3, peek ->
                val pair = AppPair(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    app1Key = app1.key,
                    app1Package = app1.packageName,
                    app1ClassName = app1.className,
                    app1UserSerial = app1.userSerial,
                    app2Key = app2.key,
                    app2Package = app2.packageName,
                    app2ClassName = app2.className,
                    app2UserSerial = app2.userSerial,
                    app3Key = app3?.key.orEmpty(),
                    app3Package = app3?.packageName.orEmpty(),
                    app3ClassName = app3?.className.orEmpty(),
                    app3UserSerial = app3?.userSerial ?: 0L,
                    peek = peek,
                )
                onSaveAppPair(pair)
                showCreator = false
            },
        )
    }
}

@Composable
private fun AppPairRow(
    pair: AppPair,
    apps: List<AppItem>,
    onLaunch: () -> Unit,
    onAddToHome: () -> Unit,
    onDelete: () -> Unit,
    onTaskbar: () -> Unit = {},
    onTaskbarPinned: Boolean = false,
) {
    val palette = LocalExpressivePalette.current
    val context = LocalContext.current
    val addedMessage = stringResource(R.string.settings_app_pair_added_home)
    val app1 = apps.firstOrNull { it.key == pair.app1Key }
    val app2 = apps.firstOrNull { it.key == pair.app2Key }
    val app3 = if (pair.hasThirdApp) apps.firstOrNull { it.key == pair.app3Key } else null

    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (app1 != null) AppIcon(appItem = app1, labelColor = Color.Transparent, showLabel = false, iconSize = 36.dp)
                if (app2 != null) AppIcon(appItem = app2, labelColor = Color.Transparent, showLabel = false, iconSize = 36.dp)
                Column {
                    Text(pair.name, fontWeight = FontWeight.Bold, color = palette.text)
                    Text(
                        "${app1?.label ?: ""} & ${app2?.label ?: ""}" + (app3?.let { " + ${it.label}" } ?: "") +
                            (if (pair.peek) " · " + stringResource(R.string.settings_app_pair_peek_short) else ""),
                        fontSize = 12.sp,
                        color = palette.faint,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onAddToHome()
                    android.widget.Toast.makeText(context, addedMessage, android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.settings_app_pair_add_home), color = Accent)
                }
                TextButton(onClick = onTaskbar) {
                    Text(
                        stringResource(if (onTaskbarPinned) R.string.settings_app_pair_unpin_taskbar else R.string.settings_app_pair_pin_taskbar),
                        color = Accent,
                    )
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.settings_delete), color = Color.Red)
                }
            }
        }
    }
}

@Composable
private fun AppPairCreatorDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, AppItem, AppItem, AppItem?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedApp1 by remember { mutableStateOf<AppItem?>(null) }
    var selectedApp2 by remember { mutableStateOf<AppItem?>(null) }
    // Optional: a third app that opens in a floating window over the split pair.
    var selectedApp3 by remember { mutableStateOf<AppItem?>(null) }
    var showApp1Picker by remember { mutableStateOf(false) }
    var showApp2Picker by remember { mutableStateOf(false) }
    var showApp3Picker by remember { mutableStateOf(false) }
    // Open as Android's peek view (the second app peeking in from the edge) instead of half and half.
    var peek by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_create_app_pair)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_app_pair_name)) },
                    singleLine = true,
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_1),
                    description = selectedApp1?.label ?: "Tap to choose",
                    onClick = { showApp1Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_2),
                    description = selectedApp2?.label ?: "Tap to choose",
                    onClick = { showApp2Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_3),
                    description = selectedApp3?.label ?: stringResource(R.string.settings_select_app_3_none),
                    // Tapping again with an app chosen clears it: the third app is optional.
                    onClick = { if (selectedApp3 != null) selectedApp3 = null else showApp3Picker = true },
                )
                if (selectedApp3 == null) {
                    ExpressiveSwitchRow(
                        title = stringResource(R.string.settings_app_pair_peek),
                        subtitle = stringResource(R.string.settings_app_pair_peek_desc),
                        checked = peek,
                        onCheckedChange = { peek = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedApp1 != null && selectedApp2 != null) {
                        onConfirm(name, selectedApp1!!, selectedApp2!!, selectedApp3, peek && selectedApp3 == null)
                    }
                },
                enabled = name.isNotBlank() && selectedApp1 != null && selectedApp2 != null,
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )

    if (showApp1Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp1Picker = false }) {
            selectedApp1 = it
            showApp1Picker = false
        }
    }
    if (showApp2Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp2Picker = false }) {
            selectedApp2 = it
            showApp2Picker = false
        }
    }
    if (showApp3Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp3Picker = false }) {
            selectedApp3 = it
            showApp3Picker = false
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onSelect: (AppItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_choose_app)) },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(apps, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(app) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(appItem = app, labelColor = Color.Transparent, showLabel = false, iconSize = 40.dp)
                        Text(app.label, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
