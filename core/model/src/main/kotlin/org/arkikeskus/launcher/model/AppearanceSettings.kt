package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Theme, fonts, glass and per-surface density. */
@Immutable
data class AppearanceSettings(
    /** AMOLED dark mode (pure black backgrounds and surfaces). */
    val amoledDark: Boolean = false,
    /** Per-display font family: "default", "ndot", "ntype", "space_grotesk", "serif", "monospace". */
    val fontFamily: String = LauncherSettings.FONT_FAMILY_DEFAULT,
    /** Per-display font for headings and titles only ([LauncherSettings.FONT_FAMILY_SAME]: the same as [fontFamily]). */
    val headingFont: String = LauncherSettings.FONT_FAMILY_SAME,
    /** Per-display font bold style. */
    val fontBold: Boolean = false,
    /** Per-display font size scale (0.7f..1.4f). */
    val fontSizeScale: Float = 1.0f,
    /** Look of the settings screens (both displays), one of SETTINGS_STYLE_*. */
    val settingsStyle: String = LauncherSettings.SETTINGS_STYLE_STUDIO,
    /** Look of the long-press menus (one of POPUP_THEME_*). */
    val popupTheme: String = LauncherSettings.POPUP_THEME_CLASSIC,
    /** Liquid Glass everywhere (see withLiquidGlass): one switch and one level (0 milky .. 1 clear). */
    val liquidGlassEverywhere: Boolean = false,
    val liquidGlassLevel: Float = 0.5f,
    val dpiWorkspace: Float = 1.0f,
    val dpiAppDrawer: Float = 1.0f,
    val dpiSettings: Float = 1.0f,
)
