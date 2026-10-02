package org.arkikeskus.launcher.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val now = System.currentTimeMillis()
                if (now - lastUpdate < 16) return // ~60fps rate-limit
                lastUpdate = now

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation[1] is pitch, orientation[2] is roll
                    val pitch = orientation[1].coerceIn(-0.8f, 0.8f)
                    val roll = orientation[2].coerceIn(-0.8f, 0.8f)
                    onOffsetChanged(pitch, roll)
                } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val x = event.values[0]
                    val y = event.values[1]
                    currentPitch = (currentPitch + x * 0.1f).coerceIn(-1f, 1f) * 0.95f
                    currentRoll = (currentRoll + y * 0.1f).coerceIn(-1f, 1f) * 0.95f
                    onOffsetChanged(currentPitch, currentRoll)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)
        return listener
    }

    fun unregister(listener: SensorEventListener) {
        sensorManager?.unregisterListener(listener)
    }
}

/**
 * Compose state hook that calculates translation offsets based on device pitch and roll.
 */
@Composable
fun rememberParallaxOffset(enabled: Boolean, maxOffsetPx: Float = 50f): Offset {
    val context = LocalContext.current
    var offset by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, context, maxOffsetPx) {
        if (!enabled) {
            offset = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val manager = ParallaxSensorManager(context)
        val listener = manager.register { pitch, roll ->
            offset = Offset(
                x = (roll / 0.8f) * maxOffsetPx,
                y = -(pitch / 0.8f) * maxOffsetPx, // Inverted Y for screen coordinates
            )
        }

        onDispose {
            listener?.let { manager.unregister(it) }
        }
    }

    return offset
}
