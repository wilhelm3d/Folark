package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Root features (all off unless root is enabled). */
@Immutable
data class RootSettings(
    /** Settings ▸ Root. [enabled] off: Folark never asks for root. The modes (ROOT_MODE_*)
     *  pick Android's way or root for locking, Android's Recents key and the notification shade;
     *  the "keep" switches give (and give back) grants with root instead of ADB or settings pages. */
    val enabled: Boolean = true,
    val lockMode: String = LauncherSettings.ROOT_MODE_FALLBACK,
    val recentsMode: String = LauncherSettings.ROOT_MODE_FALLBACK,
    val shadeMode: String = LauncherSettings.ROOT_MODE_FALLBACK,
    val keepDump: Boolean = false,
    val keepUsage: Boolean = false,
    val keepSecureSettings: Boolean = false,
    val keepNotifications: Boolean = false,
    val keepAccessibility: Boolean = false,
    val keepWidgets: Boolean = false,
)
