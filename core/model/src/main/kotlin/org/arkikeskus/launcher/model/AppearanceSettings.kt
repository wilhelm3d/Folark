package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Theme, fonts, glass and per-surface density. */
@Immutable
data class AppearanceSettings(
    /** AMOLED dark mode (pure black backgrounds and surfaces). */
    val amoledDark: Boolean = false,
    /** Per-display font family: "default", "ndot", "ntype", "space_grotesk", "serif", "monospace". */
    val fontFamily: FontChoice = FontChoice.DEFAULT,
    /** Per-display font for headings and titles only ([FontChoice.SAME]: the same as [fontFamily]). */
    val headingFont: FontChoice = FontChoice.SAME,
    /** Per-display font bold style. */
    val fontBold: Boolean = false,
    /** Per-display font size scale (0.7f..1.4f). */
    val fontSizeScale: Float = 1.0f,
    /** Look of the settings screens (both displays), one of [SettingsStyle]. */
    val settingsStyle: SettingsStyle = SettingsStyle.STUDIO,
    /** Look of the long-press menus (one of [PopupTheme]). */
    val popupTheme: PopupTheme = PopupTheme.CLASSIC,
    /** Liquid Glass everywhere (see withLiquidGlass): one switch and one level (0 milky .. 1 clear). */
    val liquidGlassEverywhere: Boolean = false,
    val liquidGlassLevel: Float = 0.5f,
    val dpiWorkspace: Float = 1.0f,
    val dpiAppDrawer: Float = 1.0f,
    val dpiSettings: Float = 1.0f,
)

/**
 * Folark's fonts. The Motorola ones ship on Motorola phones (/product/fonts) and are offered only
 * where they exist; condensed to cursive are families every Android phone carries (no font files
 * of Folark's own).
 */
enum class FontChoice(override val stored: String) : StoredChoice {
    DEFAULT("default"),
    NDOT("ndot"),
    NTYPE("ntype"),
    SPACE_GROTESK("space_grotesk"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    CONDENSED("condensed"),
    LIGHT("light"),
    MEDIUM("medium"),
    CASUAL("casual"),
    SMALLCAPS("smallcaps"),
    CURSIVE("cursive"),
    MOTO_ROOKERY("moto_rookery"),
    MOTO_WORK_SANS("moto_work_sans"),
    MOTO_EXO2("moto_exo2"),
    MOTO_ROBOTO_SLAB("moto_roboto_slab"),

    /** Headings only: the same font as the rest of the text. */
    SAME("same"),
    ;

    companion object {
        /** The fonts for text; [SAME] is for headings only. */
        val BODY: List<FontChoice> = entries - SAME
    }
}

/** The long-press menus' look. */
enum class PopupTheme(override val stored: String) : StoredChoice {
    CLASSIC("classic"),
    MATERIAL("material"),
    DARK("dark"),
    LIGHT("light"),

    /** Liquid Glass: see-through over a soft dark tint, a bright rim and a sheen. */
    GLASS("glass"),
}

/** The Settings screen's look. */
enum class SettingsStyle(override val stored: String) : StoredChoice {
    /** Settings look: follows the system light/dark theme. */
    STANDARD("standard"),

    /** Settings look: always dark charcoal, outlined cards, uppercase titles. */
    STUDIO("studio"),
}

/** One-tap theme presets: each sets the drawer, folders, menus, dots and dock together. */
enum class ThemePreset(override val stored: String) : StoredChoice {
    GLASS("glass"),
    DARK("dark"),
    LIGHT("light"),
    MONO("mono"),
}
