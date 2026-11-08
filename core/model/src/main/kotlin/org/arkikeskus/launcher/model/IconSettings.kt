package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** App icons and their labels. */
@Immutable
data class IconSettings(
    /** With themed icons on: also theme the apps that ship no themed icon (both displays; best effort). */
    val forceThemedIcons: Boolean = false,
    /** How themed icons are drawn (both displays; one of THEMED_STYLE_*). */
    val themedIconStyle: String = LauncherSettings.THEMED_STYLE_CLASSIC,
    /** Render app icons as Material You themed (monochrome) icons where the app provides one. */
    val useThemedIcons: Boolean = false,
    /** Package name of the selected third-party icon pack (empty = system default). Overrides
     *  [useThemedIcons] when set: mapped apps use the pack's icon, unmapped apps are masked to its style. */
    val iconPack: String = "",
    /** Size multiplier for app, folder and shortcut icons on home, the dock and the drawer. A grid
     *  cell still caps it, so a dense grid may not be able to grow its icons. */
    val sizeScale: Float = 1.0f,
    /** Shape of app icons (both displays), one of ICON_SHAPE_*; "system" leaves them as drawn. */
    val shape: String = LauncherSettings.ICON_SHAPE_SYSTEM,
    /** Icons that can't take a shape by themselves (older apps) sit on a plate in a pale tone of their own colour, cut to the same shape. */
    val shapeAllIcons: Boolean = true,
    /** Size multiplier for app icon labels across home/dock/drawer/folders (1.0 = the default 11sp). */
    val labelTextScale: Float = 1.0f,
    /** ARGB color for app icon labels on the home surfaces (home/dock/folders); default white. The
     *  app drawer keeps its theme color for readability over its solid background. */
    val labelColor: Int = 0xFFFFFFFF.toInt(),
    /** An app's icon nudges when a new notification arrives while home is in view, and on the
     *  taskbar; [nudgeEffect] is one of NUDGE_*. */
    val nudge: Boolean = true,
    val nudgeTaskbar: Boolean = true,
    val nudgeEffect: String = LauncherSettings.NUDGE_BOUNCE,
    /** How soft Glow, Heartbeat and Colour are (0 = strong and sharp, 1 = gentle and wide). */
    val nudgeSoftness: Float = 0.5f,
    /** A ring around the icon: progress of a download or upload, a running timer (LauncherSettings.RING_PROGRESS),
     *  and also unread notifications (LauncherSettings.RING_ALL); one of RING_*. */
    val ring: String = LauncherSettings.RING_PROGRESS,
)
