package org.arkikeskus.launcher.feature.settings

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Settings ▸ each display ▸ Also when unlocking ▸ Sync with the lock screen: with Folark's Vector
 * module running in SystemUI, the unlock animation starts as the lock screen begins to leave. The
 * subtitle says whether SystemUI has reported an unlock to Folark (the module is active there).
 */
@Composable
internal fun UnlockSyncRow(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val seenAt = remember(s.motion.unlockSystemHook) {
        context.getSharedPreferences(HOOK_PREFS, 0).getLong(UNLOCK_SEEN_AT, 0L)
    }
    val active = seenAt > 0L && System.currentTimeMillis() - seenAt < 7L * 24 * 3600 * 1000
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_unlock_sync),
        subtitle = stringResource(
            when {
                !s.motion.unlockSystemHook -> R.string.settings_unlock_sync_desc
                active -> R.string.settings_unlock_sync_active
                else -> R.string.settings_unlock_sync_missing
            },
        ),
        checked = s.motion.unlockSystemHook,
        onCheckedChange = viewModel::setUnlockSystemHook,
        shared = true,
    )
}

private val UNLOCK_STYLE_LABELS = listOf(
    LauncherSettings.UNLOCK_STYLE_DISPLAY to R.string.settings_unlock_style_display,
    LauncherSettings.UNLOCK_STYLE_ZOOM to R.string.settings_unlock_style_zoom,
    LauncherSettings.UNLOCK_STYLE_RISE to R.string.settings_unlock_style_rise,
    LauncherSettings.UNLOCK_STYLE_BLOOM to R.string.settings_unlock_style_bloom,
    LauncherSettings.UNLOCK_STYLE_SWEEP to R.string.settings_unlock_style_sweep,
    LauncherSettings.UNLOCK_STYLE_FOCUS to R.string.settings_unlock_style_focus,
)

/**
 * Settings ▸ each display ▸ Also when unlocking ▸ Unlock style: how home appears when the phone is
 * unlocked to it — the display's own fold transition, or one made for unlocking.
 */
@Composable
internal fun UnlockStyleRow(s: LauncherSettings, viewModel: SettingsViewModel) {
    val palette = org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette.current
    val accent = org.arkikeskus.launcher.ui.expressive.LocalAccent.current
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.padding(vertical = 6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(stringResource(R.string.settings_unlock_style), color = palette.text, fontSize = 15.sp)
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            UNLOCK_STYLE_LABELS.forEach { (key, label) ->
                val chosen = s.motion.unlockStyle == key
                androidx.compose.material3.Text(
                    stringResource(label),
                    color = if (chosen) accent else palette.text,
                    fontSize = 14.sp,
                    fontWeight = if (chosen) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                    modifier = androidx.compose.ui.Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(if (chosen) accent.copy(alpha = 0.16f) else palette.text.copy(alpha = 0.06f))
                        .clickable { viewModel.setUnlockStyle(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// Written by the app's RecentsHookState provider (same keys).
private const val HOOK_PREFS = "folark_recents_hook"
private const val UNLOCK_SEEN_AT = "unlock_seen_at"
