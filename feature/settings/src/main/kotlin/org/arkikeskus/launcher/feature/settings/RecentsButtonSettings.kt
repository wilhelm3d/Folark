package org.arkikeskus.launcher.feature.settings

import androidx.compose.ui.text.font.FontWeight
import org.arkikeskus.launcher.ui.expressive.LocalAccent
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
    ButtonSwitch(R.string.settings_recents_btn_close, R.string.settings_recents_btn_close_desc, s.recents.btnClose, viewModel::setRecentsBtnClose)
    ButtonSwitch(R.string.settings_recents_btn_split, null, s.recents.btnSplit, viewModel::setRecentsBtnSplit)
    ButtonSwitch(R.string.settings_recents_btn_popup, null, s.recents.btnPopup, viewModel::setRecentsBtnPopup)
    ButtonSwitch(R.string.settings_recents_btn_unsplit, R.string.settings_recents_btn_unsplit_desc, s.recents.btnUnsplit, viewModel::setRecentsBtnUnsplit)
    ButtonSwitch(R.string.settings_recents_btn_pin, R.string.settings_recents_btn_pin_desc, s.recents.btnPin, viewModel::setRecentsBtnPin)
    ButtonSwitch(R.string.settings_recents_btn_split_view, null, s.recents.btnSplitView, viewModel::setRecentsBtnSplitView)
    ButtonSwitch(R.string.settings_recents_btn_close_all, null, s.recents.btnCloseAll, viewModel::setRecentsBtnCloseAll)
    ExpressiveRowDivider()

    Text(
        stringResource(R.string.settings_recents_gestures),
        color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        stringResource(R.string.settings_recents_gestures_desc),
        color = palette.faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
    )
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_recents_outer_gestures),
        subtitle = stringResource(R.string.settings_recents_outer_gestures_desc),
        checked = s.recents.outerGestures,
        onCheckedChange = viewModel::setRecentsOuterGestures,
    )
    ExpressiveRowDivider()
    // With the cover screen's own set, this first set is the unfolded screen's.
    if (s.recents.outerGestures) ScreenHeading(R.string.settings_recents_gestures_inner)
    GesturePicker(R.string.settings_recents_gesture_swipe_up, s.recents.swipeUpAction, viewModel::setRecentsSwipeUpAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_swipe_down, s.recents.swipeDownAction, viewModel::setRecentsSwipeDownAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_long_press, s.recents.longPressAction, viewModel::setRecentsLongPressAction)
    ExpressiveRowDivider()
    GesturePicker(R.string.settings_recents_gesture_double_tap, s.recents.doubleTapAction, viewModel::setRecentsDoubleTapAction)
    if (s.recents.outerGestures) {
        ExpressiveRowDivider()
        ScreenHeading(R.string.settings_recents_gestures_outer)
        GesturePicker(R.string.settings_recents_gesture_swipe_up, s.recents.outerSwipeUpAction, viewModel::setRecentsOuterSwipeUpAction)
        ExpressiveRowDivider()
        GesturePicker(R.string.settings_recents_gesture_swipe_down, s.recents.outerSwipeDownAction, viewModel::setRecentsOuterSwipeDownAction)
        ExpressiveRowDivider()
        GesturePicker(R.string.settings_recents_gesture_long_press, s.recents.outerLongPressAction, viewModel::setRecentsOuterLongPressAction)
        ExpressiveRowDivider()
        GesturePicker(R.string.settings_recents_gesture_double_tap, s.recents.outerDoubleTapAction, viewModel::setRecentsOuterDoubleTapAction)
    }
}

/** "Unfolded screen" / "Cover screen" above a set of gestures. */
@Composable
private fun ScreenHeading(label: Int) {
    Text(
        stringResource(label),
        color = LocalAccent.current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
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
