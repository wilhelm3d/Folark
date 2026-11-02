package org.arkikeskus.launcher.feature.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.backup.BackupRepository
import org.arkikeskus.launcher.data.backup.RestoreResult
import javax.inject.Inject

/** Restoring a backup from the first-run intro: idle, working, or how it ended. */
sealed interface IntroRestoreState {
    data object Idle : IntroRestoreState
    data object Working : IntroRestoreState
    data class Done(val result: RestoreResult) : IntroRestoreState
}

@HiltViewModel
class IntroRestoreViewModel @Inject constructor(
    private val backup: BackupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<IntroRestoreState>(IntroRestoreState.Idle)
    val state: StateFlow<IntroRestoreState> = _state.asStateFlow()

    fun restore(uri: Uri) {
        if (_state.value == IntroRestoreState.Working) return
        _state.value = IntroRestoreState.Working
        viewModelScope.launch { _state.value = IntroRestoreState.Done(backup.restoreFrom(uri)) }
    }
}

/**
 * "Restore from a backup" on the intro's first page, for a reinstall: picks a backup file (one
 * made in Settings ▸ Backup & restore, or the automatic one) and puts everything back.
 * [onRestored] then closes the intro and reloads the home screen.
 */
@Composable
internal fun IntroRestoreButton(
    onRestored: () -> Unit,
    viewModel: IntroRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri)
    }
    LaunchedEffect(state) {
        if ((state as? IntroRestoreState.Done)?.result == RestoreResult.RESTORED) onRestored()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state == IntroRestoreState.Working) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.onboarding_restoring), color = Color.White)
            }
        } else {
            OutlinedButton(
                onClick = {
                    runCatching {
                        pick.launch(arrayOf(BackupRepository.MIME_TYPE, "application/octet-stream", "*/*"))
                    }
                },
            ) {
                Text(stringResource(R.string.onboarding_restore), color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            val failure = (state as? IntroRestoreState.Done)?.result?.takeIf { it != RestoreResult.RESTORED }
            Text(
                text = stringResource(
                    when (failure) {
                        null -> R.string.onboarding_restore_hint
                        RestoreResult.TOO_NEW -> R.string.onboarding_restore_too_new
                        RestoreResult.NOT_A_BACKUP -> R.string.onboarding_restore_not_a_backup
                        else -> R.string.onboarding_restore_failed
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (failure == null) Color.White.copy(alpha = 0.7f) else Color(0xFFFFB4A9),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The activity behind a (possibly wrapped) Compose context. */
internal tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
