package org.arkikeskus.launcher.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.arkikeskus.launcher.model.catching
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Files and media in search (both displays): pictures, videos and sounds are found by name.
 * Turning it on asks for the read permission; without one the switch stays off. Android 13+
 * splits the permission by media type, so any one of them is enough to start. Documents need
 * "All files access" on Android 11+, offered as a second switch.
 */
@Composable
internal fun FileSearchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasFileAccess(context)) }
    var allFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    // The permissions can be given or taken away in Android's settings meanwhile.
    LifecycleResumeEffect(Unit) {
        granted = hasFileAccess(context)
        allFiles = hasAllFilesAccess()
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = hasFileAccess(context) || result.values.any { it }
        if (granted) onCheckedChange(true)
    }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_files),
        subtitle = stringResource(
            if (checked && !granted) R.string.settings_search_files_no_permission else R.string.settings_search_files_desc,
        ),
        checked = checked && granted,
        onCheckedChange = { on ->
            if (on && !granted) ask.launch(filePermissions()) else onCheckedChange(on)
        },
        shared = true,
    )
    // Documents need more than the media permissions since Android 11: "All files access", granted
    // on Android's own page. Offered once file search is on; checked again on every return.
    if (checked && granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_search_documents),
            subtitle = stringResource(R.string.settings_search_documents_desc),
            checked = allFiles,
            // On or off, it is Android's own page that changes it.
            onCheckedChange = { openAllFilesAccess(context) },
            shared = true,
        )
    }
}

private fun openAllFilesAccess(context: Context) {
    val pkg = Uri.fromParts("package", context.packageName, null)
    val opened = catching {
        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
    }.isSuccess
    if (!opened) catching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}

/** "All files access" is on (always false before Android 11, where it does not exist). */
private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

private fun filePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasFileAccess(context: Context): Boolean =
    hasAllFilesAccess() || filePermissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
