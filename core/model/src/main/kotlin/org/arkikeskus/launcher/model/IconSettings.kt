package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** App icons and their labels. */
@Immutable
data class IconSettings(
    /** With themed icons on: also theme the apps that ship no themed icon (both displays; best effort). */
    val forceThemedIcons: Boolean = false,
    /** How themed icons are drawn (both displays; one of [ThemedStyle]). */
    val themedIconStyle: ThemedStyle = ThemedStyle.CLASSIC,
    /** Render app icons as Material You themed (monochrome) icons where the app provides one. */
    val useThemedIcons: Boolean = false,
    /** Package name of the selected third-party icon pack (empty = system default). Overrides
     *  [useThemedIcons] when set: mapped apps use the pack's icon, unmapped apps are masked to its style. */
    val iconPack: String = "",
    /** Size multiplier for app, folder and shortcut icons on home, the dock and the drawer. A grid
     *  cell still caps it, so a dense grid may not be able to grow its icons. */
    val sizeScale: Float = 1.0f,
    /** Shape of app icons (both displays), one of [IconShape]; [IconShape.SYSTEM] leaves them as drawn. */
    val shape: IconShape = IconShape.SYSTEM,
    /** Icons that can't take a shape by themselves (older apps) sit on a plate in a pale tone of their own colour, cut to the same shape. */
    val shapeAllIcons: Boolean = true,
    /** Size multiplier for app icon labels across home/dock/drawer/folders (1.0 = the default 11sp). */
    val labelTextScale: Float = 1.0f,
    /** ARGB color for app icon labels on the home surfaces (home/dock/folders); default white. The
     *  app drawer keeps its theme color for readability over its solid background. */
    val labelColor: Int = 0xFFFFFFFF.toInt(),
    /** An app's icon nudges when a new notification arrives while home is in view, and on the
     *  taskbar; [nudgeEffect] is one of [NudgeEffect]. */
    val nudge: Boolean = true,
    val nudgeTaskbar: Boolean = true,
    val nudgeEffect: NudgeEffect = NudgeEffect.BOUNCE,
    /** How soft Glow, Heartbeat and Colour are (0 = strong and sharp, 1 = gentle and wide). */
    val nudgeSoftness: Float = 0.5f,
    /** A ring around the icon: progress of a download or upload, a running timer (RingMode.PROGRESS),
     *  and also unread notifications (RingMode.ALL); one of [RingMode]. */
    val ring: RingMode = RingMode.PROGRESS,
)

/** The shape app icons are cut to. */
enum class IconShape(override val stored: String) : StoredChoice {
    SYSTEM("system"),
    CIRCLE("circle"),
    SQUIRCLE("squircle"),
    ROUNDED("rounded"),
    TEARDROP("teardrop"),
    PEBBLE("pebble"),
    SQUARE("square"),
    ARCH("arch"),
    HEXAGON("hexagon"),
    DIAMOND("diamond"),
    COOKIE("cookie"),
}

/**
 * Icon nudge effects: a hop, a shake, a heartbeat, a halo, rings spreading, squash and stretch, a
 * tint.
 */
enum class NudgeEffect(override val stored: String) : StoredChoice {
    BOUNCE("bounce"),
    WIGGLE("wiggle"),
    PULSE("pulse"),
    GLOW("glow"),
    RIPPLE("ripple"),
    JELLY("jelly"),

    /** The icon fills with the app's colour. */
    TINT("tint"),
}

/**
 * Rings round app icons: none, only progress (a download, a timer), or progress and unread
 * notifications.
 */
enum class RingMode(override val stored: String) : StoredChoice {
    OFF("off"),
    PROGRESS("progress"),
    ALL("all"),
}

/** How themed (monochrome) icons are coloured. */
enum class ThemedStyle(override val stored: String) : StoredChoice {
    CLASSIC("classic"),
    INVERTED("inverted"),
    GLASS("glass"),
    DUOTONE("duotone"),

    /** In each app's own colour: a bold plate of it, the glyph almost white. */
    OWN("own"),

    /** In each app's own colour, quietly: a pale (or deep, when dark) plate, the glyph in the colour. */
    SOFT("soft"),
}
