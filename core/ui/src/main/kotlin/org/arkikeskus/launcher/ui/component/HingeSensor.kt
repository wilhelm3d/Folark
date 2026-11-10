package org.arkikeskus.launcher.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.os.SystemClock
import org.arkikeskus.launcher.model.catching

/**
 * The phone's hinge angle (Android's public hinge sensor; the Razr Fold reports it as "Hinge Angle",
 * no permission needed), in degrees: 0 closed, 180 flat. One listener for all of Folark: the live
 * hinge effect, the hinge tilt of home and the wallpaper following the hinge each [acquire] it, and
 * the sensor is listened to (at the fastest rate any of them asks for) while at least one holds it.
 *
 * Call it on the main thread; readings arrive there too.
 */
object HingeSensor {
    private var manager: SensorManager? = null
    private var sensor: Sensor? = null
    private var looked = false

    /** The last reading, NaN while nobody listens (or before the first reading). */
    @Volatile var angle: Float = Float.NaN
        private set

    /** When the angle last changed (uptime ms). */
    @Volatile var lastChange: Long = 0L
        private set

    private class Client(val samplingUs: Int, val onAngle: ((Float) -> Unit)?)

    private val clients = LinkedHashMap<Any, Client>()
    private var registeredUs = 0

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull() ?: return
            if (value == angle) return
            angle = value
            lastChange = SystemClock.uptimeMillis()
            // A copy: a client may let go from inside its callback.
            clients.values.toList().forEach { it.onAngle?.invoke(value) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun lookUp(context: Context): Sensor? {
        if (!looked) {
            looked = true
            manager = context.applicationContext.getSystemService(SensorManager::class.java)
            // The non-wake-up hinge sensor where the phone has one: Android's default for the
            // hinge is the wake-up kind, whose every reading may wake the processor (and hold a
            // short wake lock). Folark only reads the hinge while home is on screen, so readings
            // that wait for an awake phone lose nothing.
            sensor = catching {
                manager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, false)
                    ?: manager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
            }.getOrNull()
        }
        return sensor
    }

    /** Whether this phone has a hinge sensor. */
    fun available(context: Context): Boolean = lookUp(context) != null

    /**
     * Starts (or keeps) listening for [token], at least every [samplingUs] microseconds; [onAngle]
     * gets each new reading, and the last known one at once. Acquiring the same token again
     * replaces its rate and callback.
     */
    fun acquire(context: Context, token: Any, samplingUs: Int, onAngle: ((Float) -> Unit)? = null) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "HingeSensor on the main thread only" }
        lookUp(context) ?: return
        clients[token] = Client(samplingUs, onAngle)
        update()
        if (!angle.isNaN()) onAngle?.invoke(angle)
    }

    /** [token] no longer needs the hinge; the sensor stops when nobody does. */
    fun release(token: Any) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "HingeSensor on the main thread only" }
        if (clients.remove(token) == null) return
        update()
    }

    /** Listens at the fastest rate asked for, or not at all. */
    private fun update() {
        val s = sensor ?: return
        val wanted = clients.values.minOfOrNull { it.samplingUs } ?: 0
        if (wanted == registeredUs) return
        if (registeredUs != 0) catching { manager?.unregisterListener(listener) }
        registeredUs = 0
        if (wanted == 0) {
            angle = Float.NaN
            return
        }
        val ok = catching { manager?.registerListener(listener, s, wanted) == true }.getOrDefault(false)
        if (ok) registeredUs = wanted
    }

    /** Rates for [acquire]: the live hinge effect follows the hinge closely, the others at UI pace. */
    const val SAMPLING_FAST_US = 10_000
    const val SAMPLING_UI_US = 66_667
}
