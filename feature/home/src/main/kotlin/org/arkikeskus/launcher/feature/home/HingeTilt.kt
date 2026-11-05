package org.arkikeskus.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import org.arkikeskus.launcher.ui.component.HingeSensor

/**
 * How far the phone is folded from flat, 0 (flat) to 1 ([FULL_TILT_ANGLE] or more), from Android's
 * hinge sensor, eased with a spring. 0 while [enabled] is off, home is not on screen, or on a phone
 * without a hinge sensor. Read in a graphics layer so a hinge move redraws only that layer.
 */
@Composable
fun rememberHingeFold(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    // Where the hinge is; the sensor reports only on change, so the spring below settles on it.
    val target = remember { mutableFloatStateOf(0f) }
    val fold = remember { Animatable(0f) }
    LaunchedEffect(fold) {
        snapshotFlow { target.floatValue }.collectLatest {
            fold.animateTo(it, spring(stiffness = Spring.StiffnessLow))
        }
    }
    // Only while home is resumed (in front, nothing over it): the hinge sensor wakes the phone, so it
    // is not kept on in the dark, nor behind Folark Recents or the split picker (see-through, so home
    // stays started under them).
    LifecycleResumeEffect(enabled, context) {
        // One shared hinge listener for all of Folark (HingeSensor); this asks it at UI pace.
        val token = Any()
        if (enabled) {
            HingeSensor.acquire(context, token, HingeSensor.SAMPLING_UI_US) { angle ->
                val next = ((180f - angle) / (180f - FULL_TILT_ANGLE)).coerceIn(0f, 1f)
                if (abs(next - target.floatValue) > MIN_STEP) target.floatValue = next
            }
        } else {
            target.floatValue = 0f
        }
        onPauseOrDispose {
            if (enabled) HingeSensor.release(token)
            target.floatValue = 0f
        }
    }
    return fold.asState()
}

/** The fold at which the tilt is at its largest (a laptop pose). */
private const val FULL_TILT_ANGLE = 90f
private const val MIN_STEP = 0.004f

/** Largest tilt of home (degrees) and how far it slides towards the hinge (fraction of its size). */
internal const val HINGE_TILT_DEG = 7f
internal const val HINGE_SHIFT = 0.025f
