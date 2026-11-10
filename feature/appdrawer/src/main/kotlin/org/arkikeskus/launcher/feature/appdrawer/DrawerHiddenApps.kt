package org.arkikeskus.launcher.feature.appdrawer

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.component.AppIcon

/**
 * "Hidden apps (3)" at the end of the app list: the apps hidden from it are one tap away (after
 * the fingerprint, with Settings ▸ App drawer ▸ Lock hidden apps), to open or to show again,
 * without a trip to Settings.
 */
@Composable
internal fun HiddenAppsRow(count: Int, locked: Boolean, onClick: () -> Unit) {
    val text = MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            painterResource(if (locked) LauncherIcons.Lock else LauncherIcons.VisibilityOff),
            contentDescription = null,
            tint = text.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            pluralStringResource(R.plurals.drawer_hidden_apps, count, count),
            color = text.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** The hidden apps: tap one to open it, the eye to show it in the list again. */
@Composable
internal fun HiddenAppsDialog(
    apps: List<AppItem>,
    onOpen: (AppItem) -> Unit,
    onUnhide: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_hidden_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.drawer_hidden_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(apps, key = { it.key }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(app) }
                                .padding(vertical = 6.dp),
                        ) {
                            AppIcon(appItem = app, labelColor = Color.Unspecified, showLabel = false, iconSize = 36.dp)
                            Text(app.label, modifier = Modifier.weight(1f).padding(start = 14.dp))
                            IconButton(onClick = { onUnhide(app) }) {
                                Icon(
                                    painterResource(LauncherIcons.Visibility),
                                    contentDescription = stringResource(R.string.drawer_hidden_unhide),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.drawer_hidden_close)) } },
    )
}

/**
 * Asks for the fingerprint (or face, or the screen lock) before [onPassed]; straight through
 * where the phone has no lock at all, since there is nothing to ask for then.
 */
internal fun confirmOwner(context: Context, title: String, onPassed: () -> Unit) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val manager = context.getSystemService(BiometricManager::class.java)
    if (manager == null || manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onPassed()
        return
    }
    val started = catching {
        BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()
            .authenticate(
                CancellationSignal(),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onPassed()
                },
            )
    }.isSuccess
    // A phone that cannot show the prompt here keeps them closed rather than open.
    if (!started) return
}
