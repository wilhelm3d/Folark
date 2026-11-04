package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/**
 * Settings ▸ Start: the first page. Whether Folark is set up (default home app, notification
 * access), the switches people change most, and what is new in this version, each with a way to
 * the page that has more.
 */
@Composable
internal fun StartSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    isDefaultLauncher: Boolean,
    notifAccessGranted: Boolean,
    onSetDefault: () -> Unit,
    onNotifAccess: () -> Unit,
    onOpen: (SettingsCategory) -> Unit,
) {
    // ---- Set-up status: only what still needs doing is a button.
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_start_status),
        icon = Icons.Rounded.Home,
    ) {
        StatusRow(
            ok = isDefaultLauncher,
            okText = stringResource(R.string.settings_start_default_ok),
            todoText = stringResource(R.string.settings_start_default_todo),
            onFix = onSetDefault,
        )
        ExpressiveRowDivider()
        StatusRow(
            ok = notifAccessGranted,
            okText = stringResource(R.string.settings_start_notif_ok),
            todoText = stringResource(R.string.settings_start_notif_todo),
            onFix = onNotifAccess,
        )
    }

    // ---- Quick switches (all shared by both displays).
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_start_quick),
        icon = Icons.Rounded.ToggleOn,
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_start_quick_taskbar),
            checked = s.taskbar.enabled,
            onCheckedChange = viewModel::setTaskbarEnabled,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_start_quick_recents),
            checked = s.recents.enabled,
            onCheckedChange = viewModel::setRecentsEnabled,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_start_quick_glass),
            checked = s.appearance.liquidGlassEverywhere,
            onCheckedChange = viewModel::setLiquidGlassEverywhere,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_start_quick_popup_notifications),
            checked = s.live.popupNotifications,
            onCheckedChange = viewModel::setPopupNotifications,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_app_open_animation),
            options = listOf(
                LauncherSettings.APP_OPEN_ICON to stringResource(R.string.settings_anim_icon),
                LauncherSettings.APP_OPEN_ZOOM to stringResource(R.string.settings_anim_zoom),
                LauncherSettings.APP_OPEN_POP to stringResource(R.string.settings_anim_pop),
                LauncherSettings.APP_OPEN_UNFOLD to stringResource(R.string.settings_anim_unfold),
                LauncherSettings.APP_OPEN_SYSTEM to stringResource(R.string.settings_anim_system),
            ),
            selected = s.motion.appOpenAnimation,
            onSelect = viewModel::setAppOpenAnimation,
        )
    }

    // ---- What's new in this version: each line opens its page.
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_start_new),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_start_new_desc),
    ) {
        val news = listOf(
            R.string.settings_start_new_taskbar to SettingsCategory.TASKBAR,
            R.string.settings_start_new_recents to SettingsCategory.RECENTS,
            R.string.settings_start_new_drawer to SettingsCategory.APP_DRAWER,
            R.string.settings_start_new_split to SettingsCategory.RECENTS,
            R.string.settings_start_new_motion to SettingsCategory.APPEARANCE,
        )
        news.forEachIndexed { i, (text, category) ->
            if (i > 0) ExpressiveRowDivider()
            NewsRow(stringResource(text)) { onOpen(category) }
        }
    }
}

/** A done/to-do line: a tick when [ok], else the to-do text as a button that fixes it. */
@Composable
private fun StatusRow(ok: Boolean, okText: String, todoText: String, onFix: () -> Unit) {
    val palette = LocalExpressivePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (ok) Modifier else Modifier.clickable(onClick = onFix))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Accent else palette.text,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (ok) okText else todoText,
            color = if (ok) palette.dim else palette.text,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.dim, modifier = Modifier.size(20.dp))
        }
    }
}

/** One "new in this version" line that opens the page it is on. */
@Composable
private fun NewsRow(text: String, onClick: () -> Unit) {
    val palette = LocalExpressivePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = text, color = palette.text, fontSize = 14.5.sp, lineHeight = 19.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.dim, modifier = Modifier.size(20.dp))
    }
}
