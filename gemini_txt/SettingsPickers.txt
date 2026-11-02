package org.arkikeskus.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// Small shared pickers and labels used by several settings sections (split out of SettingsScreen.kt).

@Composable
internal fun gestureLabel(action: String): String = stringResource(
    when (action) {
        LauncherSettings.GESTURE_DRAWER -> R.string.settings_gesture_drawer
        LauncherSettings.GESTURE_SEARCH -> R.string.settings_gesture_search
        LauncherSettings.GESTURE_LOCK -> R.string.settings_gesture_lock
        LauncherSettings.GESTURE_RECENTS -> R.string.settings_gesture_recents
        LauncherSettings.GESTURE_NONE -> R.string.settings_gesture_none
        LauncherSettings.GESTURE_MOTO_NOTE -> R.string.settings_gesture_moto_note
        LauncherSettings.GESTURE_MOTO_VAULT -> R.string.settings_gesture_moto_vault
        LauncherSettings.GESTURE_MOTO_READY_FOR -> R.string.settings_gesture_moto_ready_for
        LauncherSettings.GESTURE_MOTO_GAMES -> R.string.settings_gesture_moto_games
        LauncherSettings.GESTURE_MOTO_WEATHER -> R.string.settings_gesture_moto_weather
        LauncherSettings.GESTURE_MOTO_STYLES -> R.string.settings_gesture_moto_styles
        else -> R.string.settings_gesture_notifications
    }
)

/** Home gesture choices: Folark's own, then the Motorola screens this phone has, then none. */
internal fun gestureOptions(context: android.content.Context): List<Pair<String, Int>> = listOf(
    LauncherSettings.GESTURE_NOTIFICATIONS to R.string.settings_gesture_notifications,
    LauncherSettings.GESTURE_DRAWER to R.string.settings_gesture_drawer,
    LauncherSettings.GESTURE_SEARCH to R.string.settings_gesture_search,
    LauncherSettings.GESTURE_LOCK to R.string.settings_gesture_lock,
    LauncherSettings.GESTURE_RECENTS to R.string.settings_gesture_recents,
) + listOf(
    LauncherSettings.GESTURE_MOTO_NOTE to R.string.settings_gesture_moto_note,
    LauncherSettings.GESTURE_MOTO_VAULT to R.string.settings_gesture_moto_vault,
    LauncherSettings.GESTURE_MOTO_READY_FOR to R.string.settings_gesture_moto_ready_for,
    LauncherSettings.GESTURE_MOTO_GAMES to R.string.settings_gesture_moto_games,
    LauncherSettings.GESTURE_MOTO_WEATHER to R.string.settings_gesture_moto_weather,
    LauncherSettings.GESTURE_MOTO_STYLES to R.string.settings_gesture_moto_styles,
).filter { (gesture, _) -> MotoLinks.gestureAvailable(context, gesture) } + listOf(
    LauncherSettings.GESTURE_NONE to R.string.settings_gesture_none,
)

/** Gestures page: what a double tap on empty home space does (any home gesture, Motorola's too). */
@Composable
internal fun DoubleTapActionRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(lockServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = lockServiceEnabled(context)
        onPauseOrDispose { }
    }
    fun openAccessibilitySettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    var picking by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_double_tap_action),
        description = gestureLabel(s.home.doubleTapAction),
        onClick = { picking = true },
    )
    if (s.home.doubleTapAction == LauncherSettings.GESTURE_LOCK && !serviceEnabled) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_double_tap_lock_grant),
            description = stringResource(R.string.settings_double_tap_lock_grant_desc),
            onClick = { openAccessibilitySettings() },
        )
    }
    if (picking) {
        ChoicePicker(
            title = R.string.settings_double_tap_action,
            options = remember { gestureOptions(context) },
            selected = s.home.doubleTapAction,
            onPick = { choice ->
                viewModel.setDoubleTapAction(choice, screenType)
                if (choice == LauncherSettings.GESTURE_LOCK && !lockServiceEnabled(context)) {
                    openAccessibilitySettings()
                }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
internal fun BatchTimesEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalExpressivePalette.current
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_people_batch_times), color = p.text) },
        text = {
            Column {
                Text(stringResource(R.string.settings_people_batch_times_hint), color = p.text, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.settings_people_batch_times_save), color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
internal fun ChoicePicker(
    @StringRes title: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(title), color = p.text) },
        text = {
            Column {
                options.forEach { (value, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(labelRes),
                            color = if (selected == value) Accent else p.text,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
internal fun IconPackPicker(
    packs: List<IconPackInfo>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_icon_pack), color = p.text) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick("") }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_icon_pack_system),
                        color = if (selected.isBlank()) Accent else p.text,
                        fontSize = 16.sp,
                    )
                }
                packs.forEach { pack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(pack.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pack.label,
                            color = if (selected == pack.packageName) Accent else p.text,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
internal fun HiddenAppsManager(
    apps: List<AppItem>,
    hiddenKeys: Set<String>,
    onSetHidden: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalExpressivePalette.current
    BackHandler(onBack = onBack)
    Surface(modifier = modifier, color = p.bg) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_hidden_apps),
                    color = p.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_close), color = Accent, fontSize = 16.sp)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = apps, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            appItem = app,
                            labelColor = p.text,
                            showLabel = false,
                            iconSize = 40.dp,
                        )
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            color = p.text,
                            fontSize = 16.sp,
                        )
                        Switch(
                            checked = app.key in hiddenKeys,
                            onCheckedChange = { onSetHidden(app.key, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Accent,
                                checkedThumbColor = Color.White,
                                checkedBorderColor = Accent,
                                uncheckedTrackColor = p.trackOff,
                                uncheckedThumbColor = p.thumbOff,
                                uncheckedBorderColor = p.trackOff,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun foldActionLabel(action: String): String = when (action) {
    LauncherSettings.ACTION_PAGE_0 -> stringResource(R.string.settings_action_page_0)
    LauncherSettings.ACTION_LOCK -> stringResource(R.string.settings_action_lock)
    else -> stringResource(R.string.settings_action_stay)
}

@Composable
internal fun unfoldActionLabel(action: String): String = when (action) {
    LauncherSettings.ACTION_SEARCH -> stringResource(R.string.settings_action_search)
    LauncherSettings.ACTION_DRAWER -> stringResource(R.string.settings_action_drawer)
    else -> stringResource(R.string.settings_action_stay)
}

@Composable
internal fun transitionLabel(style: String, inner: Boolean = true): String = when (style) {
    LauncherSettings.TRANSITION_MORPH_SCALE_FADE -> stringResource(R.string.settings_transition_morph_scale_fade)
    LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP -> stringResource(R.string.settings_transition_book_unfold_sweep)
    LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE -> stringResource(R.string.settings_transition_aquamorphic_ripple)
    LauncherSettings.TRANSITION_SCALE -> stringResource(R.string.settings_transition_scale)
    LauncherSettings.TRANSITION_SLIDE -> stringResource(R.string.settings_transition_slide)
    LauncherSettings.TRANSITION_LIVE_HINGE ->
        stringResource(if (inner) R.string.settings_transition_live_hinge else R.string.settings_transition_hinge_timed)
    else -> stringResource(R.string.settings_transition_crossfade)
}
