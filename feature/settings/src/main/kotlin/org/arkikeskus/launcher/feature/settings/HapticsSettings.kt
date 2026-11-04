package org.arkikeskus.launcher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.arkikeskus.launcher.data.Haptics
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Appearance page ▸ Haptics (both displays): one switch for all of Folark's haptic feedback, how
 * strong it is, and each kind of moment on its own. Choosing a strength, or switching a moment on,
 * plays it once so it can be felt straight away.
 */
@Composable
internal fun HapticsSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val m = s.motion
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_haptics),
        icon = Icons.Rounded.Vibration,
        subtitle = stringResource(R.string.settings_haptics_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_haptics_enabled),
            checked = m.hapticsEnabled,
            onCheckedChange = {
                viewModel.setHapticsEnabled(it)
                if (it) Haptics.preview(context, Haptics.Event.LAUNCH, m.hapticsStrength)
            },
        )
        if (!m.hapticsEnabled) return@ExpressiveCategoryContainer
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_haptics_strength),
            options = listOf(
                LauncherSettings.HAPTICS_LIGHT to stringResource(R.string.settings_haptics_light),
                LauncherSettings.HAPTICS_MEDIUM to stringResource(R.string.settings_haptics_medium),
                LauncherSettings.HAPTICS_STRONG to stringResource(R.string.settings_haptics_strong),
            ),
            selected = m.hapticsStrength,
            onSelect = {
                viewModel.setHapticsStrength(it)
                Haptics.preview(context, Haptics.Event.SPLIT, it)
            },
        )
        val moments = listOf(
            Triple(R.string.settings_haptics_launch, m.hapticsLaunch, Haptics.Event.LAUNCH) to viewModel::setHapticsLaunch,
            Triple(R.string.settings_haptics_split, m.hapticsSplit, Haptics.Event.SPLIT) to viewModel::setHapticsSplit,
            Triple(R.string.settings_haptics_close, m.hapticsClose, Haptics.Event.CLOSE) to viewModel::setHapticsClose,
            Triple(R.string.settings_haptics_recents, m.hapticsRecents, Haptics.Event.RECENTS) to viewModel::setHapticsRecents,
            Triple(R.string.settings_haptics_drag, m.hapticsDrag, Haptics.Event.PICK_UP) to viewModel::setHapticsDrag,
        )
        val descriptions = mapOf(
            R.string.settings_haptics_launch to R.string.settings_haptics_launch_desc,
            R.string.settings_haptics_split to R.string.settings_haptics_split_desc,
            R.string.settings_haptics_close to R.string.settings_haptics_close_desc,
            R.string.settings_haptics_recents to R.string.settings_haptics_recents_desc,
            R.string.settings_haptics_drag to R.string.settings_haptics_drag_desc,
        )
        moments.forEach { (moment, set) ->
            val (title, checked, event) = moment
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(title),
                subtitle = stringResource(descriptions.getValue(title)),
                checked = checked,
                onCheckedChange = {
                    set(it)
                    if (it) Haptics.preview(context, event, m.hapticsStrength)
                },
            )
        }
    }
}
