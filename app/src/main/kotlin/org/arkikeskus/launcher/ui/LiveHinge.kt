package org.arkikeskus.launcher.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
import kotlin.math.max

/**
 * The phone's hinge angle (Android's public hinge sensor; the Razr Fold reports it as "Hinge Angle",
 * no permission needed), in degrees: 0 closed, 180 flat. Read only while [start]ed.
 */
class HingeAngleSource(context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = runCatching { sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) }.getOrNull()

    /** Whether this phone has a hinge sensor. */
    val available: Boolean get() = sensor != null

    /** The last reading, NaN until the first one after [start]. */
    @Volatile var angle: Float = Float.NaN
        private set

    /** When the angle last changed (uptime ms). */
    @Volatile var lastChange: Long = 0L
        private set

    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull() ?: return
            if (value != angle) {
                angle = value
                lastChange = SystemClock.uptimeMillis()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val s = sensor ?: return
        if (listening) return
        // The hinge sensor reports on change; 100 Hz is the most it is asked for.
        listening = runCatching { sensors?.registerListener(listener, s, SAMPLING_US) == true }.getOrDefault(false)
    }

    fun stop() {
        if (!listening) return
        runCatching { sensors?.unregisterListener(listener) }
        listening = false
        angle = Float.NaN
    }

    private companion object {
        const val SAMPLING_US = 10_000
    }
}

/**
 * Drives [progress] (0..1) from the hinge while the phone is being opened: 0 at the angle the inner
 * screen came on at, 1 at [endAngle]. [smoothingMs] eases the steps between readings (0 = raw).
 * Returns when fully open, when the hinge has rested for a moment (a half-open laptop pose), or
 * after a time limit; the caller then finishes the transition on a short timer.
 */
internal suspend fun followHinge(
    hinge: HingeAngleSource,
    progress: Animatable<Float, AnimationVector1D>,
    endAngle: Float,
    smoothingMs: Int,
    opening: Boolean = true,
) {
    val started = SystemClock.uptimeMillis()
    // The first reading after the screen came on (the listener may have just started).
    withTimeoutOrNull(FIRST_READING_WAIT_MS) {
        while (hinge.angle.isNaN()) withFrameNanos { }
    }
    val first = hinge.angle
    if (first.isNaN()) return
    
    val startAngle: Float
    val end: Float
    if (opening) {
        end = endAngle.coerceIn(MIN_SPAN, 180f)
        startAngle = first.coerceIn(0f, end - MIN_SPAN)
    } else {
        end = endAngle.coerceIn(0f, 180f - MIN_SPAN)
        startAngle = first.coerceIn(end + MIN_SPAN, 180f)
    }
    
    var shown = progress.value
    var lastFrame = 0L
    while (true) {
        val frame = withFrameNanos { it }
        val dtMs = if (lastFrame == 0L) 0f else (frame - lastFrame) / 1_000_000f
        lastFrame = frame
        val angle = hinge.angle
        val target = if (angle.isNaN()) shown else {
            if (opening) {
                ((angle - startAngle) / (end - startAngle)).coerceIn(0f, 1f)
            } else {
                ((startAngle - angle) / (startAngle - end)).coerceIn(0f, 1f)
            }
        }
        shown = if (smoothingMs <= 0) target else shown + (target - shown) * (1f - exp(-dtMs / smoothingMs))
        progress.snapTo(shown)
        if (shown >= DONE) return
        val now = SystemClock.uptimeMillis()
        if (now - max(hinge.lastChange, started) > REST_MS || now - started > LIMIT_MS) return
    }
}

private const val FIRST_READING_WAIT_MS = 150L
private const val MIN_SPAN = 30f
private const val DONE = 0.995f
/** The hinge stopped moving (a half-open pose): finish on a timer. */
private const val REST_MS = 450L
private const val LIMIT_MS = 2_500L
