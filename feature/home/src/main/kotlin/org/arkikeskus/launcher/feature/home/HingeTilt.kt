package org.arkikeskus.launcher.feature.home

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * How far the phone is folded from flat, 0 (flat) to 1 ([FULL_TILT_ANGLE] or more), from Android's
 * hinge sensor, eased with a spring. 0 while [enabled] is off, home is not on screen, or on a phone
 * without a hinge sensor. Read in a graphics layer so a hinge move redraws only that layer.
 */
@Composable
internal fun rememberHingeFold(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    // Where the hinge is; the sensor reports only on change, so the spring below settles on it.
    val target = remember { mutableFloatStateOf(0f) }
    val fold = remember { Animatable(0f) }
    LaunchedEffect(fold) {
        snapshotFlow { target.floatValue }.collectLatest {
            fold.animateTo(it, spring(stiffness = Spring.StiffnessLow))
        }
    }
    // Only while home is started: the hinge sensor wakes the phone, so it is not kept on in the dark.
    LifecycleStartEffect(enabled, context) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = if (enabled) runCatching { sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) }.getOrNull() else null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val angle = event.values.firstOrNull() ?: return
                val next = ((180f - angle) / (180f - FULL_TILT_ANGLE)).coerceIn(0f, 1f)
                if (abs(next - target.floatValue) > MIN_STEP) target.floatValue = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) runCatching { sensors?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        else target.floatValue = 0f
        onStopOrDispose {
            if (sensor != null) runCatching { sensors?.unregisterListener(listener) }
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
