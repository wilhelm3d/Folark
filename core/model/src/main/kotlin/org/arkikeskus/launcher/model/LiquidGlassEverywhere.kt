package org.arkikeskus.launcher.model

/**
 * Settings ▸ Appearance ▸ Liquid Glass everywhere: one switch and one level for the glass look of
 * the drawer, folders, long-press menus, the clock widget, the taskbar and Recents. While it is on
 * it decides those looks; your own choices come back when it is turned off (they are not changed).
 *
 * [LauncherSettings.appearance.liquidGlassLevel]: 0 = milky and soft, 1 = clear and strongly blurred.
 */
fun LauncherSettings.withLiquidGlass(): LauncherSettings {
    if (!appearance.liquidGlassEverywhere) return this
    val l = appearance.liquidGlassLevel.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a + (b - a) * l
    return copy(
        drawer = drawer.copy(
            glassEnabled = true,
            panelOpacity = lerp(0.72f, 0.2f),
            backgroundBlur = lerp(8f, 25f),
            glassBlurRadius = lerp(12f, 50f),
            glassDarkTint = lerp(0.6f, 0.18f),
        ),
        folders = folders.copy(theme = LauncherSettings.FOLDER_THEME_GLASS),
        appearance = appearance.copy(popupTheme = LauncherSettings.POPUP_THEME_GLASS),
        widgets = widgets.copy(clockGlassEffect = true),
        taskbar = taskbar.copy(
            theme = LauncherSettings.TASKBAR_THEME_GLASS,
            opacity = lerp(0.95f, 0.45f),
        ),
        recents = recents.copy(
            glass = true,
            glassBlur = lerp(16f, 66f).toInt(),
            glassTint = lerp(0.28f, 0.07f),
        ),
    )
}
