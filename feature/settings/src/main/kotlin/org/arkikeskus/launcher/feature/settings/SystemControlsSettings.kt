package org.arkikeskus.launcher.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.arkikeskus.launcher.data.SystemControls
import kotlin.math.abs
import kotlin.math.roundToInt

private const val REFRESH_STANDARD = 60f
private val ANIMATION_SCALES = listOf(0f, 0.5f, 1f, 1.5f, 2f)

/**
 * Settings ▸ Permissions ▸ System controls: Android settings Folark can change after a one-time
 * adb grant (see [SystemControls]). Values are read again each time Settings comes back.
 */
@Composable
internal fun SystemControlsSection() {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val granted = remember(resumes) { SystemControls.canWrite(context) }
    val maxHz = remember { SystemControls.maxRefreshRate(context) }
    var keep by remember { mutableStateOf(SystemControls.keepAccessibility(context)) }
    var peak by remember(resumes) { mutableStateOf(SystemControls.peakRefreshRate(context)) }
    var scale by remember(resumes) { mutableFloatStateOf(SystemControls.animationScale(context)) }
    val notGranted = stringResource(R.string.settings_system_controls_needed_toast)
    val refused = stringResource(R.string.settings_system_controls_refused)

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_system_controls),
        icon = Icons.Rounded.Tune,
        subtitle = stringResource(R.string.settings_system_controls_desc),
        sharedByBothDisplays = true,
    ) {
        val command = SystemControls.grantCommand(context.packageName)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_system_controls_setup),
            description = if (granted) {
                stringResource(R.string.settings_system_controls_ready)
            } else {
                stringResource(R.string.settings_system_controls_needed, command)
            },
            onClick = {
                if (!granted) {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("adb", command)) }
                    Toast.makeText(context, R.string.settings_taskbar_split_copied, Toast.LENGTH_SHORT).show()
                }
            },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_keep_accessibility),
            subtitle = stringResource(R.string.settings_keep_accessibility_desc),
            checked = keep,
            onCheckedChange = { on ->
                keep = on
                SystemControls.setKeepAccessibility(context, on)
                if (on) {
                    if (!granted) {
                        Toast.makeText(context, notGranted, Toast.LENGTH_SHORT).show()
                    } else {
                        val service = ComponentName(context.packageName, LOCK_SERVICE_CLASS)
                        SystemControls.keepServiceOn(context, service)
                    }
                }
            },
        )

        if (maxHz > REFRESH_STANDARD + 1f) {
            ExpressiveRowDivider()
            val top = maxHz.roundToInt()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_smooth_display),
                options = listOf(
                    false to stringResource(R.string.settings_smooth_display_hz, REFRESH_STANDARD.roundToInt()),
                    true to stringResource(R.string.settings_smooth_display_hz, top),
                ),
                selected = (peak ?: REFRESH_STANDARD) > REFRESH_STANDARD + 1f,
                onSelect = { smooth ->
                    when {
                        !granted -> Toast.makeText(context, notGranted, Toast.LENGTH_SHORT).show()
                        SystemControls.setPeakRefreshRate(context, if (smooth) maxHz else REFRESH_STANDARD) ->
                            peak = SystemControls.peakRefreshRate(context) ?: if (smooth) maxHz else REFRESH_STANDARD
                        else -> Toast.makeText(context, refused, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }

        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_system_animation_speed),
            options = ANIMATION_SCALES.map { value ->
                value to if (value == 0f) {
                    stringResource(R.string.settings_system_animation_off)
                } else {
                    stringResource(R.string.settings_system_animation_value, value.toString().removeSuffix(".0"))
                }
            },
            selected = ANIMATION_SCALES.minBy { abs(it - scale) },
            onSelect = { value ->
                when {
                    !granted -> Toast.makeText(context, notGranted, Toast.LENGTH_SHORT).show()
                    SystemControls.setAnimationScale(context, value) -> scale = value
                    else -> Toast.makeText(context, refused, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

/** Folark's accessibility service (taskbar and screen lock). */
internal const val LOCK_SERVICE_CLASS = "org.arkikeskus.launcher.feature.home.LockAccessibilityService"
