package org.arkikeskus.launcher.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

private val ACTIONS = listOf(
    LauncherSettings.RECENTS_ACTION_NONE to R.string.settings_recents_action_none,
    LauncherSettings.RECENTS_ACTION_MENU to R.string.settings_recents_action_menu,
    LauncherSettings.RECENTS_ACTION_CLOSE to R.string.settings_recents_action_close,
    LauncherSettings.RECENTS_ACTION_SPLIT to R.string.settings_recents_action_split,
    LauncherSettings.RECENTS_ACTION_POPUP to R.string.settings_recents_action_popup,
    LauncherSettings.RECENTS_ACTION_UNSPLIT to R.string.settings_recents_action_unsplit,
    LauncherSettings.RECENTS_ACTION_PIN to R.string.settings_recents_action_pin,
    LauncherSettings.RECENTS_ACTION_INFO to R.string.settings_recents_action_info,
    LauncherSettings.RECENTS_ACTION_OPEN to R.string.settings_recents_action_open,
)

/**
 * Settings ▸ Recents ▸ Buttons and gestures: every button on and off on its own, and what a
 * swipe, a long press or a double tap on a card does instead.
 */
@Composable
internal fun RecentsButtonRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    val palette = LocalExpressivePalette.current
    Text(
        stringResource(R.string.settings_recents_buttons),
        color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    ButtonSwitch(R.string.settings_recents_btn_close, R.string.settings_recents_btn_close_desc, s.recentsBtnClose, viewModel::setRecentsBtnClose)
    ButtonSwitch(R.string.settings_recents_btn_split, null, s.recentsBtnSplit, viewModel::setRecentsBtnSplit)
    ButtonSwitch(R.string.settings_recents_btn_popup, null, s.recentsBtnPopup, viewModel::setRecentsBtnPopup)
    ButtonSwitch(R.string.settings_recents_btn_unsplit, R.string.settings_recents_btn_unsplit_desc, s.recentsBtnUnsplit, viewModel::setRecentsBtnUnsplit)
    ButtonSwitch(R.string.settings_recents_btn_pin, R.string.settings_recents_btn_pin_desc, s.recentsBtnPin, viewModel::setRecentsBtnPin)
    ButtonSwitch(R.string.settings_recents_btn_split_view, null, s.recentsBtnSplitView, viewModel::setRecentsBtnSplitView)
    ButtonSwitch(R.string.settings_recents_btn_close_all, null, s.recentsBtnCloseAll, viewModel::setRecentsBtnCloseAll)
    ExpressiveRowDivider()

    Text(
        stringResource(R.string.settings_recents_gestures),
        color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        stringResource(R.string.settings_recents_gestures_desc),
        color = palette.faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
    )
    GesturePicker(R.string.settings_recents_gesture_swipe_up, s.recentsSwipeUpAction, viewModel::setRecentsSwipeUpAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_swipe_down, s.recentsSwipeDownAction, viewModel::setRecentsSwipeDownAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_long_press, s.recentsLongPressAction, viewModel::setRecentsLongPressAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_double_tap, s.recentsDoubleTapAction, viewModel::setRecentsDoubleTapAction)
}

@Composable
private fun ButtonSwitch(title: Int, subtitle: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ExpressiveSwitchRow(
        title = stringResource(title),
        subtitle = subtitle?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = onChange,
    )
}

@Composable
private fun GesturePicker(title: Int, selected: String, onSelect: (String) -> Unit) {
    ExpressiveSegmentedControl(
        title = stringResource(title),
        options = ACTIONS.map { (key, label) -> key to stringResource(label) },
        selected = selected,
        onSelect = onSelect,
    )
}
