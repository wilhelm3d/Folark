package org.arkikeskus.launcher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.LocalBadgeStyle
import org.arkikeskus.launcher.ui.component.BadgeStyle
import org.arkikeskus.launcher.ui.component.LocalScreenType

// The Notifications section (split out of SettingsScreen.kt).

@Composable
internal fun NotificationsSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    previewIcon: ImageBitmap?,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_notifications),
        icon = Icons.Rounded.Notifications,
        subtitle = stringResource(R.string.settings_category_notifications_desc),
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notif_dots),
            checked = s.dots.enabled,
            onCheckedChange = { viewModel.setShowNotificationDots(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notif_count),
            checked = s.dots.showCount,
            onCheckedChange = { viewModel.setNotificationDotCount(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_notif_size),
            value = s.dots.scale,
            onValueChange = { viewModel.setNotificationDotScale(it, screenType) },
            valueRange = 0.6f..1.8f,
            valueDisplay = "%.1fx".format(s.dots.scale),
        )
        ExpressiveRowDivider()

        NotificationDotStyleRows(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        // The preview follows the shape, colour and corner chosen in the rows above.
        CompositionLocalProvider(LocalBadgeStyle provides BadgeStyle(s.dots.shape, s.dots.color, s.dots.position)) {
            BadgePreview(
                icon = previewIcon,
                showDots = s.dots.enabled,
                showCount = s.dots.showCount,
                scale = s.dots.scale,
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_dot_avatars),
            subtitle = stringResource(R.string.settings_dot_avatars_desc),
            checked = s.dots.avatars,
            onCheckedChange = viewModel::setDotAvatars,
            shared = true,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_popup_notifications),
            subtitle = stringResource(R.string.settings_popup_notifications_desc),
            checked = s.live.popupNotifications,
            onCheckedChange = viewModel::setPopupNotifications,
            shared = true,
        )
        ExpressiveRowDivider()

        IconNudgeRows(s, viewModel, previewIcon)
        ExpressiveRowDivider()

        LiveActivityRows(s, viewModel)
    }
}
