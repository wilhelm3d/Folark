package org.arkikeskus.launcher.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.CrashLog
import org.arkikeskus.launcher.data.ModuleTools
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.model.LauncherSettings
import java.text.DateFormat
import java.util.Date
import org.arkikeskus.launcher.model.catching

/**
 * Phone & system ▸ Support: everything Folark knows about its own health, in one page —
 *
 *  - **Module status**: whether the Vector (LSPosed) module is live in the system launcher and in
 *    System UI (last seen, which gesture hooks that launcher build allowed), and whether the
 *    native three-app split is currently running.
 *  - **Crash log**: the on-device log of Folark's own uncaught exceptions ([CrashLog]).
 *  - **Module log**: what the module wrote inside System UI and the system launcher (root).
 *  - **Check screenshots**: every step of getting a task screenshot, and what each answered ([RootTasks.diagnose]).
 *
 * The four lived on three different pages; a bug report wants them together.
 */
@Composable
internal fun SupportPage(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_support),
        icon = Icons.Rounded.BugReport,
        subtitle = stringResource(R.string.settings_support_desc),
        sharedByBothDisplays = true,
    ) {
        ModuleStatusRow()
        ExpressiveRowDivider()
        CrashLogRow()
        ExpressiveRowDivider()
        SupportModuleLogRow()
        ExpressiveRowDivider()
        ScreenshotCheckRow()
    }
}

// ---------------------------------------------------------------------- module status

/** The Vector module's own prefs file in Folark (the module notes when it last asked; RecentsHookState). */
private const val HOOK_PREFS = "folark_recents_hook"
private const val HOOK_SEEN_AT = "seen_at"
private const val HOOK_COMPAT = "compat"
private const val HOOK_UNLOCK_SEEN_AT = "unlock_seen_at"

/** RecentsHook.HOOK_* bits, reported by the module in the system launcher. */
private const val HOOK_MAIN = 1
private const val HOOK_SEARCH = 2
private const val HOOK_SWIPE = 4

@Composable
private fun ModuleStatusRow() {
    val context = LocalContext.current
    // Re-read on every tap of the row (the module writes when it next asks).
    var refreshAt by remember { mutableStateOf(0) }
    val status = remember(refreshAt) {
        val prefs = context.getSharedPreferences(HOOK_PREFS, Context.MODE_PRIVATE)
        Status(
            seenAt = prefs.getLong(HOOK_SEEN_AT, 0L),
            unlockSeenAt = prefs.getLong(HOOK_UNLOCK_SEEN_AT, 0L),
            compat = prefs.getInt(HOOK_COMPAT, 0),
            splitLive = SplitThree.running(context),
        )
    }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_support_status),
        description = stringResource(R.string.settings_support_status_desc),
        onClick = { refreshAt++ },
    )
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val gestures = listOfNotNull(
        stringResource(R.string.settings_support_status_gesture_recents).takeIf { status.compat and HOOK_MAIN != 0 },
        stringResource(R.string.settings_support_status_gesture_search).takeIf { status.compat and HOOK_SEARCH != 0 },
        stringResource(R.string.settings_support_status_gesture_swipe).takeIf { status.compat and HOOK_SWIPE != 0 },
    )
    Text(
        text = buildString {
            appendLine(
                if (status.seenAt > 0L) stringResource(R.string.settings_support_status_seen, dateFormat.format(Date(status.seenAt)))
                else stringResource(R.string.settings_support_status_not_seen)
            )
            if (status.seenAt > 0L) {
                appendLine(
                    if (gestures.isEmpty()) stringResource(R.string.settings_support_status_gestures_none)
                    else stringResource(R.string.settings_support_status_gestures, gestures.joinToString(", "))
                )
            }
            appendLine(
                if (status.unlockSeenAt > 0L) stringResource(R.string.settings_support_status_unlock, dateFormat.format(Date(status.unlockSeenAt)))
                else stringResource(R.string.settings_support_status_unlock_gone)
            )
            append(
                if (status.splitLive) stringResource(R.string.settings_support_status_split_live)
                else stringResource(R.string.settings_support_status_split_off)
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** One read of the module's status prefs. */
private data class Status(val seenAt: Long, val unlockSeenAt: Long, val compat: Int, val splitLive: Boolean)

// ---------------------------------------------------------------------- crash log

@Composable
private fun CrashLogRow() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_crash_log),
        description = stringResource(R.string.settings_crash_log_desc),
        onClick = { show = true },
    )
    if (show) {
        val log = remember { CrashLog.read(context) }
        fun copy(text: String) {
            context.getSystemService(android.content.ClipboardManager::class.java)
                ?.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
        }
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.settings_crash_log)) },
            text = {
                SelectionContainer {
                    Text(
                        text = log.ifBlank { stringResource(R.string.settings_crash_log_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { copy(log) }, enabled = log.isNotBlank()) {
                    Text(stringResource(R.string.settings_recents_check_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.settings_taskbar_done)) }
            },
        )
    }
}

// ---------------------------------------------------------------------- module log

@Composable
private fun SupportModuleLogRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val noRoot = stringResource(R.string.settings_module_log_no_root)
    val empty = stringResource(R.string.settings_module_log_empty)
    ExpressiveActionItem(
        title = stringResource(if (running) R.string.settings_root_working else R.string.settings_module_log),
        description = stringResource(R.string.settings_module_log_desc),
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    val log = withContext(Dispatchers.IO) { catching { ModuleTools.moduleLog() }.getOrNull() }
                    text = when {
                        log == null -> noRoot
                        log.isEmpty() -> empty
                        else -> log
                    }
                    running = false
                }
            }
        },
    )
    val shown = text ?: return
    AlertDialog(
        onDismissRequest = { text = null },
        title = { Text(stringResource(R.string.settings_module_log)) },
        text = {
            SelectionContainer {
                Text(
                    shown,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("Folark module log", shown))
            }) { Text(stringResource(R.string.settings_recents_check_copy)) }
        },
        dismissButton = {
            TextButton(onClick = { text = null }) { Text(stringResource(R.string.settings_taskbar_done)) }
        },
    )
}

// ---------------------------------------------------------------------- screenshot check

@Composable
private fun ScreenshotCheckRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(if (running) R.string.settings_root_working else R.string.settings_recents_check),
        description = stringResource(R.string.settings_recents_check_desc),
        onClick = {
            if (!running) {
                running = true
                scope.launch {
                    report = withContext(Dispatchers.IO) {
                        catching { RootTasks.diagnose(context) }
                            .getOrElse { "Check failed: ${it.javaClass.simpleName}: ${it.message}" }
                    }
                    running = false
                }
            }
        },
    )
    val text = report ?: return
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text(stringResource(R.string.settings_recents_check)) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { copyText(context, text) }) {
                Text(stringResource(R.string.settings_recents_check_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = { report = null }) {
                Text(stringResource(R.string.settings_taskbar_done))
            }
        },
    )
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("Folark", text))
}
