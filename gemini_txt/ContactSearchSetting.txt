package org.arkikeskus.launcher.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Contacts in search (both displays): people show up in the drawer's search, with call and message
 * buttons. Turning it on asks for the contacts permission; without it the switch stays off.
 */
@Composable
internal fun ContactSearchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasContacts(context)) }
    // The permission can be taken away in Android's settings meanwhile.
    LifecycleResumeEffect(Unit) {
        granted = hasContacts(context)
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            onCheckedChange(true)
        } else {
            // "Don't ask again": only Android's app settings can give it now.
            val activity = context.findHostActivity()
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_contacts),
        subtitle = stringResource(
            if (checked && !granted) R.string.settings_search_contacts_no_permission else R.string.settings_search_contacts_desc,
        ),
        checked = checked && granted,
        onCheckedChange = { on ->
            if (on && !granted) ask.launch(Manifest.permission.READ_CONTACTS) else onCheckedChange(on)
        },
        shared = true,
    )
}

private fun hasContacts(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
