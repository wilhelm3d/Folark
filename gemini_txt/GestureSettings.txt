package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gesture
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// The Gestures section and its edge-swipe app picker (split out of SettingsScreen.kt).

@Composable
internal fun GesturesSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    allApps: List<AppItem>,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLeftSwipe: () -> Unit,
    onRightSwipe: () -> Unit,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_gestures),
        icon = Icons.Rounded.Gesture,
        subtitle = stringResource(R.string.settings_category_gestures_desc),
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_swipe_up_action),
            description = gestureLabel(s.home.swipeUpAction),
            onClick = onSwipeUp,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_swipe_down_action),
            description = gestureLabel(s.home.swipeDownAction),
            onClick = onSwipeDown,
        )
        ExpressiveRowDivider()

        val leftSwipeLabel = allApps.firstOrNull { it.key == s.home.leftSwipeAppKey }?.label
            ?: stringResource(R.string.settings_left_edge_none)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_left_edge),
            description = leftSwipeLabel,
            onClick = onLeftSwipe,
        )
        ExpressiveRowDivider()

        val rightSwipeLabel = allApps.firstOrNull { it.key == s.home.rightSwipeAppKey }?.label
            ?: stringResource(R.string.settings_right_edge_none)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_right_edge),
            description = rightSwipeLabel,
            onClick = onRightSwipe,
        )
        ExpressiveRowDivider()

        DoubleTapActionRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        var pickingMinusOne by remember { mutableStateOf(false) }
        ExpressiveActionItem(
            title = stringResource(R.string.settings_minus_one_title),
            description = when (s.home.minusOneScreen) {
                LauncherSettings.MINUS_ONE_GOOGLE -> stringResource(R.string.settings_minus_one_google)
                LauncherSettings.MINUS_ONE_SMART_FEED -> stringResource(R.string.settings_minus_one_smart_feed)
                else -> stringResource(R.string.settings_minus_one_none)
            },
            onClick = { pickingMinusOne = true },
        )
        if (pickingMinusOne) {
            ChoicePicker(
                title = R.string.settings_minus_one_title,
                options = listOf(
                    LauncherSettings.MINUS_ONE_NONE to R.string.settings_minus_one_none,
                    LauncherSettings.MINUS_ONE_GOOGLE to R.string.settings_minus_one_google,
                    LauncherSettings.MINUS_ONE_SMART_FEED to R.string.settings_minus_one_smart_feed,
                ),
                selected = s.home.minusOneScreen,
                onPick = { viewModel.setMinusOneScreen(it); pickingMinusOne = false },
                onDismiss = { pickingMinusOne = false },
            )
        }
        ExpressiveRowDivider()

        IconSwipeRow(s = s, viewModel = viewModel, screenType = screenType)
    }
}

@Composable
internal fun EdgeSwipeAppPicker(
    titleRes: Int,
    noneRes: Int,
    apps: List<AppItem>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(titleRes), color = p.text) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(noneRes), color = Accent, fontSize = 16.sp)
                }
                apps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.key) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(appItem = app, labelColor = p.text, showLabel = false, iconSize = 32.dp)
                        Text(app.label, modifier = Modifier.padding(start = 12.dp), color = p.text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}
