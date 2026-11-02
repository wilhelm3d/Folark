package org.arkikeskus.launcher.feature.settings

import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.backup.BackupRepository
import org.arkikeskus.launcher.data.backup.RestoreResult
import java.util.Date
import javax.inject.Inject

/** What the backup card says under its rows after the last action. */
enum class BackupMessage { WORKING, SAVED, SAVE_FAILED, AUTO_ON, AUTO_FAILED, RESTORED, NOT_A_BACKUP, TOO_NEW, RESTORE_FAILED }

/** Settings ▸ Backup & restore. */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backup: BackupRepository,
) : ViewModel() {
    val autoFolder: StateFlow<String> = backup.autoBackupFolder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), "")
    val lastAuto: StateFlow<Long> = backup.lastAutoBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0L)

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun backupTo(uri: Uri) = viewModelScope.launch {
        _message.value = BackupMessage.WORKING
        _message.value = if (backup.backupTo(uri)) BackupMessage.SAVED else BackupMessage.SAVE_FAILED
    }

    fun setAutoFolder(uri: Uri) = viewModelScope.launch {
        _message.value = BackupMessage.WORKING
        _message.value = if (backup.setAutoBackupFolder(uri)) BackupMessage.AUTO_ON else BackupMessage.AUTO_FAILED
    }

    fun turnOffAuto() = viewModelScope.launch {
        backup.clearAutoBackupFolder()
        _message.value = null
    }

    fun restoreFrom(uri: Uri) = viewModelScope.launch {
        _message.value = BackupMessage.WORKING
        _message.value = when (backup.restoreFrom(uri)) {
            RestoreResult.RESTORED -> BackupMessage.RESTORED
            RestoreResult.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
            RestoreResult.TOO_NEW -> BackupMessage.TOO_NEW
            RestoreResult.FAILED -> BackupMessage.RESTORE_FAILED
        }
    }
}

/** "primary:Documents/Folark" → "Documents/Folark", for showing where automatic backups go. */
private fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    id.substringAfter(':').ifEmpty { id }
}.getOrDefault(uri)

@Composable
internal fun BackupSection(viewModel: BackupSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val autoFolder by viewModel.autoFolder.collectAsStateWithLifecycle()
    val lastAuto by viewModel.lastAuto.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE),
    ) { uri -> if (uri != null) viewModel.backupTo(uri) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setAutoFolder(uri)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_backup),
        icon = Icons.Rounded.SettingsBackupRestore,
        subtitle = stringResource(R.string.settings_category_backup_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_backup_now),
            description = stringResource(R.string.settings_backup_now_desc),
            onClick = {
                val day = DateFormat.format("yyyy-MM-dd", Date()).toString()
                runCatching { createFile.launch("Folark-backup-$day.zip") }
            },
        )
        ExpressiveRowDivider()

        val autoDescription = if (autoFolder.isEmpty()) {
            stringResource(R.string.settings_backup_auto_off)
        } else {
            val last = if (lastAuto > 0L) {
                DateFormat.getMediumDateFormat(context).format(Date(lastAuto)) + " " +
                    DateFormat.getTimeFormat(context).format(Date(lastAuto))
            } else {
                stringResource(R.string.settings_backup_never)
            }
            stringResource(R.string.settings_backup_auto_on, folderLabel(autoFolder), last)
        }
        ExpressiveActionItem(
            title = stringResource(R.string.settings_backup_auto),
            description = autoDescription,
            onClick = { runCatching { pickFolder.launch(null) } },
        )
        if (autoFolder.isNotEmpty()) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_backup_auto_turn_off),
                description = stringResource(R.string.settings_backup_auto_turn_off_desc),
                onClick = { viewModel.turnOffAuto() },
            )
        }
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_restore),
            description = stringResource(R.string.settings_restore_desc),
            onClick = { runCatching { pickBackup.launch(arrayOf(BackupRepository.MIME_TYPE, "application/octet-stream", "*/*")) } },
        )
        ExpressiveRowDivider()

        SystemUpdatesRow()

        message?.let { current ->
            Text(
                text = stringResource(
                    when (current) {
                        BackupMessage.WORKING -> R.string.settings_backup_working
                        BackupMessage.SAVED -> R.string.settings_backup_saved
                        BackupMessage.SAVE_FAILED -> R.string.settings_backup_save_failed
                        BackupMessage.AUTO_ON -> R.string.settings_backup_auto_started
                        BackupMessage.AUTO_FAILED -> R.string.settings_backup_auto_failed
                        BackupMessage.RESTORED -> R.string.settings_restore_done
                        BackupMessage.NOT_A_BACKUP -> R.string.backup_not_a_backup
                        BackupMessage.TOO_NEW -> R.string.backup_too_new
                        BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    viewModel.restoreFrom(uri)
                }) { Text(stringResource(R.string.settings_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
