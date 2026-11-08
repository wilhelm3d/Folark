package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** How apps open: split screen and floating windows. */
@Immutable
data class LaunchSettings(
    /** Multi-window (both displays): the "Split" drop target shown while an app icon is dragged, and
     *  the "Open in split screen" menu entry. */
    val splitEnabled: Boolean = true,
    /** Multi-window (both displays): "Open in floating window" and floating shortcuts in the app menus. */
    val floatingEnabled: Boolean = true,
    /** Size of a newly opened floating window (both displays; one of FLOATING_SIZE_*). */
    val floatingWindowSize: String = LauncherSettings.FLOATING_SIZE_MEDIUM,
    /** With Folark's Vector module in SystemUI: a third app joins a split screen as a real third
     *  pane (Recents, saved app pairs) instead of floating over it. */
    val threeAppSplit: Boolean = true,
    /** Actually tile that third pane through SystemUI's module. Off by default: the third app
     *  floats instead, because asking SystemUI to start a task into a third pane can crash System
     *  UI on some ROMs. On: the real tiled third pane, where the ROM supports it. */
    val threeAppNative: Boolean = false,
    /** "Aspect ratio…" in the app menus: Android's own per-app aspect ratio page, where the
     *  phone has it. */
    val aspectRatioMenu: Boolean = true,
)
