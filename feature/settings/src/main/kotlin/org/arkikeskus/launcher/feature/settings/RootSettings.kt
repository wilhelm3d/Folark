package org.arkikeskus.launcher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.RootGrants
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Settings ▸ Root: what root can do instead of ADB, Android's settings pages, or the accessibility
 * service. Each part has its own switch or choice; with "Use root" off Folark never asks for root.
 */
@Composable
internal fun RootSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootCheck by remember { mutableIntStateOf(0) }
    var root by remember { mutableStateOf(RootShell.lastKnown) }
    var busy by remember { mutableStateOf(false) }
    // What is granted now: read on open, on return to the page, and after each grant.
    var refresh by remember { mutableIntStateOf(0) }
    val granted = remember(refresh) { RootGrants.ALL.associateWith { RootGrants.granted(context, it) } }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(rootCheck, s.root.enabled) {
        if (!s.root.enabled) return@LaunchedEffect
        root = withContext(Dispatchers.IO) { RootShell.available(recheck = rootCheck > 0) }
    }
    fun grant(vararg which: String) {
        if (busy) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { which.forEach { RootGrants.grant(context, it) } }
            root = RootShell.lastKnown
            busy = false
            refresh++
        }
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_root),
        icon = Icons.Rounded.AdminPanelSettings,
        subtitle = stringResource(R.string.settings_category_root_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_root_enabled),
            subtitle = stringResource(R.string.settings_root_enabled_desc),
            checked = s.root.enabled,
            onCheckedChange = viewModel::setRootEnabled,
        )
        if (!s.root.enabled) return@ExpressiveCategoryContainer
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(
                when (root) {
                    true -> R.string.settings_recents_root_yes
                    false -> R.string.settings_recents_root_no
                    null -> R.string.settings_recents_root_unknown
                },
            ),
            description = stringResource(R.string.settings_root_check_desc),
            onClick = { rootCheck++ },
        )
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- instead of the accessibility service
        RootModeRow(R.string.settings_root_lock, R.string.settings_root_lock_desc, s.root.lockMode, viewModel::setRootLockMode)
        ExpressiveRowDivider()
        RootModeRow(R.string.settings_root_recents, R.string.settings_root_recents_desc, s.root.recentsMode, viewModel::setRootRecentsMode)
        ExpressiveRowDivider()
        RootModeRow(R.string.settings_root_shade, R.string.settings_root_shade_desc, s.root.shadeMode, viewModel::setRootShadeMode)
        ExpressiveRowDivider()

        // ---------------------------------------------------------------- instead of ADB and settings pages
        KeepRow(R.string.settings_root_keep_dump, R.string.settings_root_keep_dump_desc, s.root.keepDump, granted[RootGrants.DUMP]) {
            viewModel.setRootKeepDump(it); if (it) grant(RootGrants.DUMP)
        }
        ExpressiveRowDivider()
        KeepRow(R.string.settings_root_keep_usage, R.string.settings_root_keep_usage_desc, s.root.keepUsage, granted[RootGrants.USAGE]) {
            viewModel.setRootKeepUsage(it); if (it) grant(RootGrants.USAGE)
        }
        ExpressiveRowDivider()
        KeepRow(R.string.settings_root_keep_secure, R.string.settings_root_keep_secure_desc, s.root.keepSecureSettings, granted[RootGrants.SECURE_SETTINGS]) {
            viewModel.setRootKeepSecureSettings(it); if (it) grant(RootGrants.SECURE_SETTINGS)
        }
        ExpressiveRowDivider()
        KeepRow(R.string.settings_root_keep_notifications, R.string.settings_root_keep_notifications_desc, s.root.keepNotifications, granted[RootGrants.NOTIFICATIONS]) {
            viewModel.setRootKeepNotifications(it); if (it) grant(RootGrants.NOTIFICATIONS)
        }
        ExpressiveRowDivider()
        KeepRow(R.string.settings_root_keep_accessibility, R.string.settings_root_keep_accessibility_desc, s.root.keepAccessibility, granted[RootGrants.ACCESSIBILITY]) {
            viewModel.setRootKeepAccessibility(it); if (it) grant(RootGrants.ACCESSIBILITY)
        }
        ExpressiveRowDivider()
        KeepRow(R.string.settings_root_keep_widgets, R.string.settings_root_keep_widgets_desc, s.root.keepWidgets, granted[RootGrants.WIDGETS]) {
            viewModel.setRootKeepWidgets(it); if (it) grant(RootGrants.WIDGETS)
        }
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(if (busy) R.string.settings_root_working else R.string.settings_root_grant_all),
            description = stringResource(R.string.settings_root_grant_all_desc),
            onClick = { grant(*RootGrants.ALL.toTypedArray()) },
        )
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(R.string.settings_root_default_home),
            description = stringResource(R.string.settings_root_default_home_desc),
            onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { RootGrants.makeDefaultHome(context) }
                    android.widget.Toast.makeText(
                        context,
                        if (ok) R.string.settings_root_done else R.string.settings_root_failed,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun RootModeRow(title: Int, subtitle: Int, selected: String, onSelect: (String) -> Unit) {
    ExpressiveSegmentedControl(
        title = stringResource(title),
        options = listOf(
            LauncherSettings.ROOT_MODE_OFF to stringResource(R.string.settings_root_mode_off),
            LauncherSettings.ROOT_MODE_FALLBACK to stringResource(R.string.settings_root_mode_fallback),
            LauncherSettings.ROOT_MODE_ALWAYS to stringResource(R.string.settings_root_mode_always),
        ),
        selected = selected,
        onSelect = onSelect,
    )
    androidx.compose.material3.Text(
        stringResource(subtitle),
        color = org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette.current.faint,
        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

@Composable
private fun KeepRow(title: Int, subtitle: Int, checked: Boolean, granted: Boolean?, onChange: (Boolean) -> Unit) {
    val state = stringResource(
        when (granted) {
            true -> R.string.settings_root_state_granted
            false -> R.string.settings_root_state_missing
            null -> R.string.settings_root_state_unknown
        },
    )
    ExpressiveSwitchRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle) + " · " + state,
        checked = checked,
        onCheckedChange = onChange,
    )
}
