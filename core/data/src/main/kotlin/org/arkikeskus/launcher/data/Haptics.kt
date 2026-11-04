package org.arkikeskus.launcher.data

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Folark's haptic feedback, one place for every action so each kind of moment always feels the
 * same (Settings ▸ Appearance ▸ Haptics). Each [Event] is a small composition of the phone's own
 * haptic primitives where the vibration motor supports them: a crisp click to open, a rising
 * double tap as two apps meet in split screen, a soft thud as an app is closed, a gentle swell as
 * Recents opens, light ticks while dragging. Without primitive support it falls back to Android's
 * standard haptic constants. It follows the phone's touch-feedback setting (Android 13+), and
 * plays nothing for an event that is switched off.
 */
object Haptics {

    enum class Event {
        /** An app opens (from home, the drawer, the taskbar, Recents). */
        LAUNCH,
        /** Two (or three) apps come together in split screen. */
        SPLIT,
        /** One app is closed (a Recents card swiped away, a close button). */
        CLOSE,
        /** Every app is closed at once. */
        CLOSE_ALL,
        /** Recents opens. */
        RECENTS,
        /** An icon or card is picked up to drag. */
        PICK_UP,
        /** A drag crosses into a drop zone (or the next slot). */
        ZONE,
        /** A drag is dropped on its target. */
        DROP,
    }

    /** The current settings, kept up to date by the app (LauncherApplication). */
    @Volatile
    var settings: LauncherSettings.Motion = LauncherSettings.Motion()

    /** Whether [event] plays with the current settings. */
    fun enabled(event: Event): Boolean {
        val s = settings
        if (!s.hapticsEnabled) return false
        return when (event) {
            Event.LAUNCH -> s.hapticsLaunch
            Event.SPLIT -> s.hapticsSplit
            Event.CLOSE, Event.CLOSE_ALL -> s.hapticsClose
            Event.RECENTS -> s.hapticsRecents
            Event.PICK_UP, Event.ZONE, Event.DROP -> s.hapticsDrag
        }
    }

    /** Plays [event] (if switched on). Safe from any thread. */
    fun play(context: Context, event: Event) {
        if (!enabled(event)) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val scale = when (settings.hapticsStrength) {
            LauncherSettings.HAPTICS_LIGHT -> 0.45f
            LauncherSettings.HAPTICS_STRONG -> 1f
            else -> 0.72f
        }
        val effect = composition(vibrator, event, scale) ?: fallback(event)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // As touch feedback: the phone's own touch-feedback switch and strength apply.
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    /** Plays [event] through [view] (its window's haptic settings), falling back to [play]. */
    fun play(view: View, event: Event) = play(view.context, event)

    /** A sample of [event] at the chosen strength, for the settings page (ignores the per-event switches). */
    fun preview(context: Context, event: Event, strength: String) {
        val before = settings
        settings = before.copy(hapticsEnabled = true, hapticsLaunch = true, hapticsSplit = true, hapticsClose = true, hapticsRecents = true, hapticsDrag = true, hapticsStrength = strength)
        try {
            play(context, event)
        } finally {
            settings = before
        }
    }

    private fun composition(vibrator: Vibrator, event: Event, scale: Float): VibrationEffect? {
        fun supported(vararg ids: Int) = runCatching { vibrator.areAllPrimitivesSupported(*ids) }.getOrDefault(false)
        val parts: List<Triple<Int, Float, Int>> = when (event) {
            Event.LAUNCH -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 0))
            Event.SPLIT -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.55f, 0), Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f, 40))
            Event.CLOSE -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.55f, 0))
            Event.CLOSE_ALL -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 0), Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, 70))
            Event.RECENTS -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.35f, 0), Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0))
            Event.PICK_UP -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f, 0))
            Event.ZONE -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.8f, 0))
            Event.DROP -> listOf(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 0))
        }
        if (!supported(*parts.map { it.first }.toIntArray())) return null
        return runCatching {
            val composition = VibrationEffect.startComposition()
            parts.forEach { (id, strength, delay) -> composition.addPrimitive(id, (strength * scale).coerceIn(0f, 1f), delay) }
            composition.compose()
        }.getOrNull()
    }

    /** Android's predefined effects, for motors without the primitives. */
    private fun fallback(event: Event): VibrationEffect = VibrationEffect.createPredefined(
        when (event) {
            Event.LAUNCH, Event.DROP -> VibrationEffect.EFFECT_CLICK
            Event.SPLIT, Event.CLOSE_ALL -> VibrationEffect.EFFECT_DOUBLE_CLICK
            Event.CLOSE -> VibrationEffect.EFFECT_HEAVY_CLICK
            Event.RECENTS, Event.PICK_UP, Event.ZONE -> VibrationEffect.EFFECT_TICK
        },
    )

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
