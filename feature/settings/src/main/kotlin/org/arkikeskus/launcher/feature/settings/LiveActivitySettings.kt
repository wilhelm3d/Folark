package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.ui.component.LivePill
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import androidx.compose.ui.graphics.toArgb

/**
 * Notifications card: the live activity pill (on home, and on the taskbar), its effect, and a
 * preview of a running kitchen timer with that effect.
 */
@Composable
internal fun LiveActivityRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_live_activities),
        subtitle = stringResource(R.string.settings_live_activities_desc),
        checked = s.live.activities,
        onCheckedChange = viewModel::setLiveActivities,
        shared = true,
    )
    if (!s.live.activities) return
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_live_activities_taskbar),
        checked = s.live.activitiesTaskbar,
        onCheckedChange = viewModel::setLiveActivitiesTaskbar,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_live_expand),
        subtitle = stringResource(R.string.settings_live_expand_desc),
        checked = s.live.expandOnLongPress,
        onCheckedChange = viewModel::setLiveExpand,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_live_skip_chips),
        subtitle = stringResource(R.string.settings_live_skip_chips_desc),
        checked = s.live.skipSystemChips,
        onCheckedChange = viewModel::setLiveSkipSystemChips,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_live_effect),
        options = LIVE_EFFECT_LABELS.map { (key, label) -> key to stringResource(label) },
        selected = s.live.activityEffect,
        onSelect = viewModel::setLiveActivityEffect,
    )
    ExpressiveRowDivider()
    LiveActivityPreview(s.live.activityEffect)
}

@Composable
private fun LiveActivityPreview(effect: String) {
    val accent = LocalAccent.current
    // A kitchen timer counting down, a little over half way: shows every effect, progress included.
    val sample = remember(accent) {
        LiveActivity(
            key = "preview",
            packageName = "",
            userSerial = 0L,
            icon = null,
            color = accent.toArgb(),
            title = "Pasta",
            text = "",
            critical = null,
            chronometerBase = System.currentTimeMillis() + 7 * 60_000L + 30_000L,
            countDown = true,
            progress = 0.6f,
            indeterminate = false,
            category = null,
            postTime = 0L,
            contentIntent = null,
        )
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B3550), Color(0xFF4A3B5C), Color(0xFF1F4A52)))),
        ) {
            LivePill(activity = sample, effect = effect, animate = true, light = false, onClick = {})
        }
        Text(
            text = stringResource(R.string.settings_live_preview),
            color = LocalExpressivePalette.current.dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

private val LIVE_EFFECT_LABELS = listOf(
    LauncherSettings.LIVE_EFFECT_GLOW to R.string.settings_live_effect_glow,
    LauncherSettings.LIVE_EFFECT_SHIMMER to R.string.settings_live_effect_shimmer,
    LauncherSettings.LIVE_EFFECT_PROGRESS to R.string.settings_live_effect_progress,
    LauncherSettings.LIVE_EFFECT_ORBIT to R.string.settings_live_effect_orbit,
    LauncherSettings.LIVE_EFFECT_BREATHE to R.string.settings_live_effect_breathe,
    LauncherSettings.LIVE_EFFECT_AURORA to R.string.settings_live_effect_aurora,
    LauncherSettings.LIVE_EFFECT_STILL to R.string.settings_live_effect_still,
)
