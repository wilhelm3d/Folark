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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import org.arkikeskus.launcher.data.PairSchedule
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
    schedules: Map<String, PairSchedule>,
    onSetSchedule: (String, PairSchedule?) -> Unit,
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
    var editing by remember { mutableStateOf<AppPair?>(null) }
    var scheduling by remember { mutableStateOf<AppPair?>(null) }

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
                        schedule = schedules[pair.id],
                        onLaunch = { onLaunchAppPair(pair) },
                        onAddToHome = { onAddAppPairToHome(pair) },
                        onDelete = { onDeleteAppPair(pair.id) },
                        onEdit = { editing = pair },
                        onSchedule = { scheduling = pair },
                        onTaskbar = { onToggleTaskbar(pair) },
                        onTaskbarPinned = pair.id in taskbarPairs,
                    )
                }
            }
        }
    }

    if (showCreator || editing != null) {
        val initial = editing
        AppPairCreatorDialog(
            apps = apps,
            initial = initial,
            onDismiss = {
                showCreator = false
                editing = null
            },
            onConfirm = { scene ->
                val app1 = scene.app1
                val app2 = scene.app2
                val app3 = scene.app3
                val pair = AppPair(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    name = scene.name,
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
                    peek = scene.layout == AppPair.LAYOUT_PEEK,
                    layout = scene.layout,
                    thirdMode = scene.thirdMode,
                    coverMode = scene.coverMode,
                )
                onSaveAppPair(pair)
                showCreator = false
                editing = null
            },
        )
    }

    scheduling?.let { pair ->
        PairScheduleDialog(
            pairName = pair.name,
            initial = schedules[pair.id],
            onDismiss = { scheduling = null },
            onSave = { schedule ->
                onSetSchedule(pair.id, schedule)
                scheduling = null
            },
        )
    }
}

/**
 * The schedule dialog for one pair: on/off, a time (Material 3's [androidx.compose.material3.TimePicker])
 * and the weekdays (none chosen = every day). Saving re-lays the alarms via the schedules flow.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PairScheduleDialog(
    pairName: String,
    initial: PairSchedule?,
    onDismiss: () -> Unit,
    onSave: (PairSchedule?) -> Unit,
) {
    val palette = LocalExpressivePalette.current
    var on by remember { mutableStateOf(initial != null) }
    // The picker state owns the time; the dialog reads it on save.
    val pickerState = remember {
        androidx.compose.material3.TimePickerState(
            initialHour = initial?.hour ?: 8,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true,
        )
    }
    var days by remember { mutableStateOf(initial?.days?.toSet() ?: emptySet()) }
    val dayLabels = listOf(
        1 to stringResource(R.string.pair_schedule_mon), 2 to stringResource(R.string.pair_schedule_tue),
        3 to stringResource(R.string.pair_schedule_wed), 4 to stringResource(R.string.pair_schedule_thu),
        5 to stringResource(R.string.pair_schedule_fri), 6 to stringResource(R.string.pair_schedule_sat),
        7 to stringResource(R.string.pair_schedule_sun),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_schedule_title, pairName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.pair_schedule_on),
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = { on = it })
                }
                if (on) {
                    androidx.compose.material3.TimePicker(state = pickerState)
                    // The chips are the weekday choice; none chosen means every day.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        dayLabels.forEach { (day, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.pair_schedule_days_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.dim,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!on) onSave(null)
                    else onSave(PairSchedule(pickerState.hour, pickerState.minute, days))
                },
            ) { Text(stringResource(R.string.pair_schedule_save), color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pair_schedule_cancel)) }
        },
    )
}

/** What the scene dialog hands back. */
private class SceneDraft(
    val name: String,
    val app1: AppItem,
    val app2: AppItem,
    val app3: AppItem?,
    val layout: String,
    val thirdMode: String,
    val coverMode: String,
)

@Composable
private fun AppPairRow(
    pair: AppPair,
    apps: List<AppItem>,
    schedule: PairSchedule? = null,
    onLaunch: () -> Unit,
    onAddToHome: () -> Unit,
    onDelete: () -> Unit,
    onTaskbar: () -> Unit = {},
    onTaskbarPinned: Boolean = false,
    onEdit: () -> Unit = {},
    onSchedule: () -> Unit = {},
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
                            " · " + stringResource(layoutLabel(pair.effectiveLayout)) +
                            (if (pair.coverMode == AppPair.COVER_FIRST) " · " + stringResource(R.string.settings_scene_cover_first_short) else ""),
                        fontSize = 12.sp,
                        color = palette.faint,
                    )
                    // The schedule as a second line, so it is visible without opening anything.
                    schedule?.let {
                        Text(
                            text = stringResource(R.string.pair_schedule_row) + " " +
                                "%02d:%02d".format(it.hour, it.minute) + " · " + scheduleDaysLabel(it.days),
                            fontSize = 12.sp,
                            color = Accent,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onAddToHome()
                    android.widget.Toast.makeText(context, addedMessage, android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.settings_app_pair_add_home), color = Accent)
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.settings_scene_edit), color = Accent)
                }
                TextButton(onClick = onSchedule) {
                    Text(
                        stringResource(if (schedule != null) R.string.pair_schedule_edit else R.string.pair_schedule_add),
                        color = Accent,
                    )
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

private fun layoutLabel(layout: String): Int = when (layout) {
    AppPair.LAYOUT_FIRST_THIRD -> R.string.settings_scene_layout_third
    AppPair.LAYOUT_FIRST_TWO_THIRDS -> R.string.settings_scene_layout_two_thirds
    AppPair.LAYOUT_PEEK -> R.string.settings_app_pair_peek_short
    else -> R.string.settings_scene_layout_even
}

/** "Mon, Wed, Fri" or "Every day", for the row's schedule line. */
@Composable
private fun scheduleDaysLabel(days: Set<Int>): String {
    if (days.isEmpty()) return stringResource(R.string.pair_schedule_every_day)
    val names = mapOf(
        1 to stringResource(R.string.pair_schedule_mon), 2 to stringResource(R.string.pair_schedule_tue),
        3 to stringResource(R.string.pair_schedule_wed), 4 to stringResource(R.string.pair_schedule_thu),
        5 to stringResource(R.string.pair_schedule_fri), 6 to stringResource(R.string.pair_schedule_sat),
        7 to stringResource(R.string.pair_schedule_sun),
    )
    return days.sorted().mapNotNull { names[it] }.joinToString(", ")
}

/** One choice of several, as a row of chips under a title. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SceneChoice(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val palette = LocalExpressivePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, label) ->
                androidx.compose.material3.FilterChip(
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }
    }
}

@Composable
private fun AppPairCreatorDialog(
    apps: List<AppItem>,
    initial: AppPair?,
    onDismiss: () -> Unit,
    onConfirm: (SceneDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var selectedApp1 by remember { mutableStateOf(initial?.let { p -> apps.firstOrNull { it.key == p.app1Key } }) }
    var selectedApp2 by remember { mutableStateOf(initial?.let { p -> apps.firstOrNull { it.key == p.app2Key } }) }
    // Optional: a third app, as a third pane or floating over the two.
    var selectedApp3 by remember { mutableStateOf(initial?.takeIf { it.hasThirdApp }?.let { p -> apps.firstOrNull { it.key == p.app3Key } }) }
    var showApp1Picker by remember { mutableStateOf(false) }
    var showApp2Picker by remember { mutableStateOf(false) }
    var showApp3Picker by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(initial?.effectiveLayout ?: AppPair.LAYOUT_EVEN) }
    var thirdMode by remember { mutableStateOf(initial?.thirdMode ?: AppPair.THIRD_AUTO) }
    // New scenes open just their first app on the cover screen; old pairs keep their split there.
    var coverMode by remember { mutableStateOf(initial?.coverMode ?: AppPair.COVER_FIRST) }
    val tapToChoose = stringResource(R.string.settings_scene_tap_to_choose)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.settings_create_app_pair else R.string.settings_scene_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_app_pair_name)) },
                    singleLine = true,
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_1),
                    description = selectedApp1?.label ?: tapToChoose,
                    onClick = { showApp1Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_2),
                    description = selectedApp2?.label ?: tapToChoose,
                    onClick = { showApp2Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_3),
                    description = selectedApp3?.label ?: stringResource(R.string.settings_select_app_3_none),
                    // Tapping again with an app chosen clears it: the third app is optional.
                    onClick = { if (selectedApp3 != null) selectedApp3 = null else showApp3Picker = true },
                )
                val app1Label = selectedApp1?.label ?: stringResource(R.string.settings_select_app_1)
                if (selectedApp3 == null || thirdMode == AppPair.THIRD_FLOAT) {
                    SceneChoice(
                        title = stringResource(R.string.settings_scene_layout),
                        options = listOf(
                            AppPair.LAYOUT_EVEN to stringResource(R.string.settings_scene_layout_even),
                            AppPair.LAYOUT_FIRST_THIRD to stringResource(R.string.settings_scene_layout_app_third, app1Label),
                            AppPair.LAYOUT_FIRST_TWO_THIRDS to stringResource(R.string.settings_scene_layout_app_two_thirds, app1Label),
                            AppPair.LAYOUT_PEEK to stringResource(R.string.settings_app_pair_peek_short),
                        ),
                        selected = layout,
                        onSelect = { layout = it },
                    )
                    if (layout != AppPair.LAYOUT_EVEN) {
                        Text(
                            stringResource(R.string.settings_scene_layout_needs_module),
                            color = LocalExpressivePalette.current.faint,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (selectedApp3 != null) {
                    SceneChoice(
                        title = stringResource(R.string.settings_scene_third),
                        options = listOf(
                            AppPair.THIRD_AUTO to stringResource(R.string.settings_scene_third_auto),
                            AppPair.THIRD_PANE to stringResource(R.string.settings_scene_third_pane),
                            AppPair.THIRD_FLOAT to stringResource(R.string.settings_scene_third_float),
                        ),
                        selected = thirdMode,
                        onSelect = { thirdMode = it },
                    )
                }
                SceneChoice(
                    title = stringResource(R.string.settings_scene_cover),
                    options = listOf(
                        AppPair.COVER_SPLIT to stringResource(R.string.settings_scene_cover_split),
                        AppPair.COVER_FIRST to stringResource(R.string.settings_scene_cover_first, app1Label),
                    ),
                    selected = coverMode,
                    onSelect = { coverMode = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = selectedApp1
                    val b = selectedApp2
                    if (name.isNotBlank() && a != null && b != null) {
                        val c = selectedApp3
                        onConfirm(
                            SceneDraft(
                                name = name,
                                app1 = a,
                                app2 = b,
                                app3 = c,
                                // Three panes have Android's own layout.
                                layout = if (c != null && thirdMode != AppPair.THIRD_FLOAT) AppPair.LAYOUT_EVEN else layout,
                                thirdMode = thirdMode,
                                coverMode = coverMode,
                            ),
                        )
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
