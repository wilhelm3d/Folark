package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Animations and effects. */
@Immutable
data class MotionSettings(
    /** The same transition plays when the phone is unlocked straight to home (both displays). */
    val unlockTransition: Boolean = true,
    /** With Folark's Vector module running in SystemUI: the unlock animation starts the moment the
     *  lock screen begins to leave (told by SystemUI) instead of being guessed from timing. */
    val unlockSystemHook: Boolean = true,
    /** How home appears when unlocking: one of UNLOCK_STYLE_* (display = the display's own fold transition). */
    val unlockStyle: String = LauncherSettings.UNLOCK_STYLE_DISPLAY,
    val aquamorphicTouchEnabled: Boolean = true,
    val appLaunchZoomEnabled: Boolean = true,
    // --- Animations (both displays) ---------------------------------------------------------------
    /** How fast Folark's own animations run: 1 = normal, 2 = twice as fast, 0.5 = half speed. */
    val animationSpeed: Float = 1f,
    /** How an app opens from the launcher (one of APP_OPEN_*). */
    val appOpenAnimation: String = LauncherSettings.APP_OPEN_ICON,
    /** Haptic feedback for Folark's own actions (opening apps, split screen, closing apps, Recents, drag and drop). */
    val hapticsEnabled: Boolean = true,
    /** A light click when an app opens. */
    val hapticsLaunch: Boolean = true,
    /** A rising double tap when two apps come together in split screen. */
    val hapticsSplit: Boolean = true,
    /** A soft thud when an app is closed (a firmer one for Close all). */
    val hapticsClose: Boolean = true,
    /** A gentle swell when Recents opens. */
    val hapticsRecents: Boolean = true,
    /** Ticks when an icon or card is picked up, crosses into a drop zone and lands. */
    val hapticsDrag: Boolean = true,
    /** How strong the haptics are (HAPTICS_*), on top of the phone's own touch-feedback strength. */
    val hapticsStrength: String = LauncherSettings.HAPTICS_MEDIUM,
    /** Folark's battery saver (both displays): off, on, or following the system's battery saver. */
    val batterySaverMode: String = LauncherSettings.BATTERY_SAVER_AUTO,
    /** With "With system": also switch the saver on by itself when the battery is low (below
     *  [LauncherSettings.BATTERY_SAVER_LOW_PERCENT] and not charging) or the phone is running hot. */
    val batterySaverLowBattery: Boolean = true,
    val batterySaverWhenHot: Boolean = true,
)
