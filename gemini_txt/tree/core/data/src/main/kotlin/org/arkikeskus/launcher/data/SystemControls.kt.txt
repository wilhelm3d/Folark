package org.arkikeskus.launcher.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display

/**
 * Android settings Folark may change after the one-time adb grant of WRITE_SECURE_SETTINGS
 * ([grantCommand]): keeping its accessibility service on, the top refresh rate and the system
 * animation speed. Every write is guarded: without the grant, or if Android refuses one, nothing
 * changes and the call says so.
 */
object SystemControls {
    private const val PREFS = "system_controls"
    private const val KEY_KEEP_ACCESSIBILITY = "keep_accessibility"
    private const val PEAK_REFRESH_RATE = "peak_refresh_rate"

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun grantCommand(packageName: String): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

    // ------------------------------------------------------------------ accessibility service

    fun keepAccessibility(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_ACCESSIBILITY, true)

    fun setKeepAccessibility(context: Context, keep: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_KEEP_ACCESSIBILITY, keep).apply()
    }

    fun isServiceEnabled(context: Context, service: ComponentName): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == service }
    }

    /** Turns [service] back on if it is off and the user wants it kept on; true if it did. */
    fun keepServiceOn(context: Context, service: ComponentName): Boolean = runCatching {
        if (!canWrite(context) || !keepAccessibility(context) || isServiceEnabled(context, service)) return false
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val next = if (current.isBlank()) service.flattenToString() else current + ":" + service.flattenToString()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ refresh rate

    /** The highest refresh rate the screen offers at its current size, in Hz. */
    fun maxRefreshRate(context: Context): Float = runCatching {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return 60f
        val mode = display.mode
        display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxOfOrNull { it.refreshRate } ?: mode.refreshRate
    }.getOrDefault(60f)

    /** Android's top refresh rate setting; null if it can't be read. */
    fun peakRefreshRate(context: Context): Float? = runCatching {
        Settings.System.getFloat(context.contentResolver, PEAK_REFRESH_RATE)
    }.getOrNull()

    /** Sets Android's top refresh rate; false if Android refused. */
    fun setPeakRefreshRate(context: Context, hz: Float): Boolean = runCatching {
        canWrite(context) && Settings.System.putFloat(context.contentResolver, PEAK_REFRESH_RATE, hz)
    }.getOrDefault(false)

    // ------------------------------------------------------------------ animation speed

    /** Android's animation length scale (1 = normal, 0 = off). */
    fun animationScale(context: Context): Float = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    /** Sets all three of Android's animation scales; false if Android refused. */
    fun setAnimationScale(context: Context, scale: Float): Boolean = runCatching {
        if (!canWrite(context)) return false
        val resolver = context.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, scale) &&
            Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, scale) &&
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }.getOrDefault(false)
}
