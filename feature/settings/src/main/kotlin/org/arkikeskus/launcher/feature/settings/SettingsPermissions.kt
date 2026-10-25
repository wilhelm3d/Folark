package org.arkikeskus.launcher.feature.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

// Permissions: the rows that ask for them, the toggles that need them, and the Permissions section (split out of SettingsScreen.kt).

@Composable
private fun PermissionRow(label: String, permission: String) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    LifecycleResumeEffect(permission) {
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        val activity = context.findActivity()
        if (!result && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        ) {
            openAppDetailsSettings(context)
        }
    }
    ExpressiveActionItem(
        title = label,
        description = stringResource(
            if (granted) R.string.settings_permission_granted else R.string.settings_permission_not_granted,
        ),
        onClick = {
            if (!granted) launcher.launch(permission)
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun lockServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val target = ComponentName(context.packageName, LOCK_SERVICE_CLASS)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
}

private fun openAppDetailsSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

internal fun isNotificationAccessGranted(context: Context): Boolean =
    runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

internal fun openNotificationAccess(context: Context) {
    val component = ComponentName(
        context.packageName,
        "org.arkikeskus.launcher.notifications.NotificationDotListenerService",
    )
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(detail) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
internal fun WeatherToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_show_weather),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
    )
}

@Composable
internal fun StatusBarToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_status_bar),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        },
    )
}

/** The taskbar over other apps: it is drawn by the accessibility service, so that must be allowed. */
@Composable
internal fun TaskbarToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
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
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_taskbar),
        subtitle = stringResource(R.string.settings_taskbar_desc),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn && !lockServiceEnabled(context)) openAccessibilitySettings()
        },
    )
    if (enabled && !serviceEnabled) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_double_tap_lock_grant),
            description = stringResource(R.string.settings_taskbar_grant_desc),
            onClick = { openAccessibilitySettings() },
        )
    }
}

@Composable
internal fun PermissionsSection(
    isDefaultLauncher: Boolean,
    notifAccessGranted: Boolean,
    onSetDefault: () -> Unit,
    onNotifAccess: () -> Unit,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_permissions),
        icon = Icons.Rounded.Shield,
        subtitle = stringResource(R.string.settings_category_permissions_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_set_default),
            description = stringResource(
                if (isDefaultLauncher) R.string.settings_default_current else R.string.settings_default_tap,
            ),
            onClick = onSetDefault,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_notif_access),
            description = stringResource(
                if (notifAccessGranted) R.string.settings_notif_access_granted
                else R.string.settings_notif_access_not_granted,
            ),
            onClick = onNotifAccess,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_calendar),
            permission = Manifest.permission.READ_CALENDAR,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_contacts),
            permission = Manifest.permission.READ_CONTACTS,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_phone),
            permission = Manifest.permission.READ_PHONE_STATE,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_location),
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    SystemControlsSection()
}
