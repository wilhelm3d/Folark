package org.arkikeskus.launcher.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Sensor manager utility that reads [Sensor.TYPE_ROTATION_VECTOR] or [Sensor.TYPE_GYROSCOPE]
 * to calculate device pitch and roll for 3D depth and parallax effects.
 */
class ParallaxSensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    fun register(onOffsetChanged: (pitch: Float, roll: Float) -> Unit): SensorEventListener? {
        val s = sensor ?: return null
        val listener = object : SensorEventListener {
            private var lastUpdate = System.currentTimeMillis()
            private var currentPitch = 0f
            private var currentRoll = 0f
            private var lastEmittedPitch = 0f
            private var lastEmittedRoll = 0f
            private var stationaryStartMs = System.currentTimeMillis()
            private var isStationary = false

            // Reused for every event: at sensor rate two fresh arrays per sample is steady garbage.
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val now = System.currentTimeMillis()
                if (now - lastUpdate < 33) return // ~30fps UI rate-limit for battery efficiency
                lastUpdate = now

                var pitch = 0f
                var roll = 0f

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    pitch = orientation[1].coerceIn(-0.8f, 0.8f)
                    roll = orientation[2].coerceIn(-0.8f, 0.8f)
                } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val x = event.values[0]
                    val y = event.values[1]
                    currentPitch = (currentPitch + x * 0.1f).coerceIn(-1f, 1f) * 0.95f
                    currentRoll = (currentRoll + y * 0.1f).coerceIn(-1f, 1f) * 0.95f
                    pitch = currentPitch
                    roll = currentRoll
                }

                // Stationary auto-pause: if device is resting flat/still for >1.5s, pause updates
                val delta = kotlin.math.abs(pitch - lastEmittedPitch) + kotlin.math.abs(roll - lastEmittedRoll)
                if (delta < 0.015f) {
                    if (!isStationary && now - stationaryStartMs > 1500L) {
                        isStationary = true
                    }
                } else {
                    stationaryStartMs = now
                    isStationary = false
                }

                if (!isStationary) {
                    lastEmittedPitch = pitch
                    lastEmittedRoll = roll
                    onOffsetChanged(pitch, roll)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        return listener
    }

    fun unregister(listener: SensorEventListener) {
        sensorManager?.unregisterListener(listener)
    }
}

/**
 * Compose state hook that calculates translation offsets based on device pitch and roll.
 *
 * Returns the [State] itself, not its value: read it inside a `graphicsLayer { }` block and the
 * sensor only moves a layer — reading the value in composition recomposed the whole caller at
 * sensor rate. The sensor is registered only while [enabled]; the caller folds "the launcher is
 * actually on screen" into that flag, so nothing listens while another app is in front.
 */
@Composable
fun rememberParallaxOffset(enabled: Boolean, maxOffsetPx: Float = 50f): State<Offset> {
    val context = LocalContext.current
    val offset = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, context, maxOffsetPx) {
        if (!enabled) {
            offset.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val manager = ParallaxSensorManager(context)
        // Smoothed, and only written when it moved by half a pixel or more: the sensor never holds
        // perfectly still, and every write draws a frame, so a phone lying on a table would
        // otherwise redraw the home screen about 50 times a second.
        var x = offset.value.x
        var y = offset.value.y
        val listener = manager.register { pitch, roll ->
            x += ((roll / 0.8f) * maxOffsetPx - x) * PARALLAX_SMOOTHING
            y += (-(pitch / 0.8f) * maxOffsetPx - y) * PARALLAX_SMOOTHING // Inverted Y for screen coordinates
            val shown = offset.value
            if (kotlin.math.abs(x - shown.x) >= PARALLAX_MIN_STEP_PX || kotlin.math.abs(y - shown.y) >= PARALLAX_MIN_STEP_PX) {
                offset.value = Offset(x, y)
            }
        }

        onDispose {
            listener?.let { manager.unregister(it) }
        }
    }

    return offset
}

/** How far each sensor sample moves the shown offset towards the new reading (a low-pass filter). */
private const val PARALLAX_SMOOTHING = 0.3f

/** Smaller moves than this are not drawn. */
private const val PARALLAX_MIN_STEP_PX = 0.5f
