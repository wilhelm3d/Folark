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
    val shape: DotShape = DotShape.CIRCLE,
    val color: DotColor = DotColor.RED,
    val position: DotPosition = DotPosition.TOP_END,
    /** Chat apps' notification dots show the sender's photo (both displays). */
    val avatars: Boolean = true,
)

/** A notification dot's colour. */
enum class DotColor(override val stored: String) : StoredChoice {
    RED("red"),
    ACCENT("accent"),
    WHITE("white"),
}

/** Which corner of the icon a notification dot sits in. */
enum class DotPosition(override val stored: String) : StoredChoice {
    TOP_END("top_end"),
    TOP_START("top_start"),
    BOTTOM_END("bottom_end"),
    BOTTOM_START("bottom_start"),
}

/** A notification dot's shape. */
enum class DotShape(override val stored: String) : StoredChoice {
    CIRCLE("circle"),
    SQUIRCLE("squircle"),
    SQUARE("square"),
}
