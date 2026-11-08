package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Notification dots on icons. */
@Immutable
data class DotSettings(
    val enabled: Boolean = true,
    /** When notification dots are on: true shows the count (Nova-style), false shows a plain dot. */
    val showCount: Boolean = true,
    /** Size multiplier for the notification dot/badge (1.0 = default). */
    val scale: Float = 1.0f,
    /** Notification dots: shape, colour and corner of the icon. */
    val shape: String = LauncherSettings.DOT_SHAPE_CIRCLE,
    val color: String = LauncherSettings.DOT_COLOR_RED,
    val position: String = LauncherSettings.DOT_POSITION_TOP_END,
    /** Chat apps' notification dots show the sender's photo (both displays). */
    val avatars: Boolean = true,
)
