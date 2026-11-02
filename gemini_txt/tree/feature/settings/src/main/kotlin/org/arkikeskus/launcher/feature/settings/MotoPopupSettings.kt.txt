package org.arkikeskus.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.MotoPopup

/**
 * Foldable page, under the floating window size: whether Motorola's pop-up gesture is on, read
 * again each time Settings comes back. A tap opens Motorola's pop-up settings. Nothing on other phones.
 */
@Composable
internal fun MotoPopupGestureRow() {
    val context = LocalContext.current
    val available = remember { MotoPopup.isAvailable(context) }
    if (!available) return
    var resumes by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes) {
        enabled = withContext(Dispatchers.IO) { MotoPopup.gestureEnabled(context) }
    }
    ExpressiveRowDivider()
    ExpressiveActionItem(
        title = stringResource(R.string.settings_moto_popup_gesture),
        description = stringResource(
            when (enabled) {
                true -> R.string.settings_moto_popup_gesture_on
                false -> R.string.settings_moto_popup_gesture_off
                null -> R.string.settings_moto_popup_gesture_unknown
            },
        ),
        onClick = { runCatching { context.startActivity(MotoPopup.settingsIntent(context)) } },
    )
}
