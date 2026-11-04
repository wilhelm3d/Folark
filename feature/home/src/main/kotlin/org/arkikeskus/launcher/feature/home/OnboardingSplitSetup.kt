package org.arkikeskus.launcher.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitThree

/** What split screen and the taskbar have on this phone right now (all cheap checks, no root prompt). */
private data class SplitSetupState(
    val overlay: Boolean = false,
    val usage: Boolean = false,
    val dump: Boolean = false,
    val module: Boolean = false,
)

private fun readSplitSetup(context: Context) = SplitSetupState(
    overlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
    usage = ShellSplit.hasUsageAccess(context),
    dump = ShellSplit.hasDump(context),
    module = SplitThree.running(context),
)

/**
 * The intro's last page: what split screen and the taskbar need, each checked live (every two
 * seconds while the page shows, so a grant given from a computer turns green at once): drawing
 * over other apps (the taskbar), usage access (its recent apps), the one-time ADB grant (split
 * screen: the command, ready to copy), and Folark's module in Vector (native split, optional).
 * The line at the bottom says how a split would be made now.
 */
@Composable
internal fun SplitSetupPage() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SplitSetupState()) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { readSplitSetup(context) }
            delay(CHECK_EVERY_MS)
        }
    }
    val command = remember { ShellSplit.grantCommand(context.packageName) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_split_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_split_text),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))

        SetupCheck(
            title = stringResource(R.string.onboarding_split_overlay),
            description = stringResource(R.string.onboarding_split_overlay_desc),
            done = state.overlay,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
        SetupCheck(
            title = stringResource(R.string.onboarding_split_usage),
            description = stringResource(R.string.onboarding_split_usage_desc),
            done = state.usage,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
        SetupCheck(
            title = stringResource(R.string.onboarding_split_adb),
            description = stringResource(R.string.onboarding_split_adb_desc),
            done = state.dump,
            onClick = null,
        ) {
            if (!state.dump) {
                Spacer(Modifier.height(8.dp))
                Text(
                    command,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                )
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("adb", command)) }
                    copied = true
                }) {
                    Text(
                        stringResource(if (copied) R.string.onboarding_split_copied else R.string.onboarding_split_copy),
                        color = AURORA_BLUE_LIGHT_TEXT,
                    )
                }
            }
        }
        SetupCheck(
            title = stringResource(R.string.onboarding_split_module),
            description = stringResource(R.string.onboarding_split_module_desc),
            done = state.module,
            optional = true,
            onClick = null,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(
                when {
                    state.dump && state.module -> R.string.onboarding_split_ready_native
                    state.dump -> R.string.onboarding_split_ready_commands
                    else -> R.string.onboarding_split_not_yet
                },
            ),
            style = MaterialTheme.typography.titleSmall,
            color = if (state.dump) Color(0xFFB8F5C8) else Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun SetupCheck(
    title: String,
    description: String,
    done: Boolean,
    onClick: (() -> Unit)?,
    optional: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.White.copy(alpha = if (done) 0.05f else 0.08f), RoundedCornerShape(18.dp))
            .then(if (done) Modifier.border(1.dp, Color(0xFFB8F5C8).copy(alpha = 0.45f), RoundedCornerShape(18.dp)) else Modifier)
            .then(if (!done && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(description, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            when {
                done -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_granted),
                    tint = Color(0xFFB8F5C8),
                    modifier = Modifier.size(24.dp),
                )
                optional -> Text(stringResource(R.string.onboarding_split_optional), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
                onClick != null -> Text(stringResource(R.string.onboarding_grant), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
            }
        }
        extra()
    }
}

private val AURORA_BLUE_LIGHT_TEXT = Color(0xFF9CC2FF)
private const val CHECK_EVERY_MS = 2_000L
