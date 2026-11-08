package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark's own status bar and the system one. */
@Immutable
data class StatusBarSettings(
    /** Show a slim status bar (clock + battery + signal, with dynamic battery/signal colors) at the top
     *  of the home screen. */
    val enabled: Boolean = false,
    /** Hide the system status bar while the launcher is in the foreground (immersive/fullscreen home).
     *  Independent of [enabled]; combine the two to replace the system bar with the themed one. */
    val hideSystem: Boolean = false,
    /** Darkness of the scrim drawn behind the themed status bar (0 = none, 1 = solid black); keeps the
     *  clock/indicators legible over bright wallpapers. Own setting, like [Dock.backgroundOpacity]. */
    val scrimOpacity: Float = 0.6f,
    /** Android's own status bar (Folark's Vector module in System UI, both displays): at least
     *  [systemPaddingInner] / [systemPaddingCover] dp of room at its left and right ends. */
    val systemPadding: Boolean = false,
    val systemPaddingInner: Int = 14,
    val systemPaddingCover: Int = 10,
)
