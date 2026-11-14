package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType

// The setters for the look: status bar, motion and appearance.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setAquamorphicTouchEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.aquamorphicTouchEnabled(screenType)] = value }
suspend fun SettingsRepository.setAppLaunchZoomEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.appLaunchZoomEnabled(screenType)] = value }
suspend fun SettingsRepository.setPopupTheme(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.popupTheme(screenType)] = value }
suspend fun SettingsRepository.setAnimationSpeed(value: Float) = edit {
    it[Keys.ANIMATION_SPEED] = value.coerceIn(LauncherSettings.MIN_ANIMATION_SPEED, LauncherSettings.MAX_ANIMATION_SPEED)
}
suspend fun SettingsRepository.setAppOpenAnimation(value: String) = edit { it[Keys.APP_OPEN_ANIMATION] = value }
suspend fun SettingsRepository.setBatterySaverMode(value: String) = edit { it[Keys.BATTERY_SAVER_MODE] = value }
suspend fun SettingsRepository.setBatterySaverLowBattery(value: Boolean) = edit { it[Keys.BATTERY_SAVER_LOW_BATTERY] = value }
suspend fun SettingsRepository.setBatterySaverWhenHot(value: Boolean) = edit { it[Keys.BATTERY_SAVER_WHEN_HOT] = value }
suspend fun SettingsRepository.setAppFontFamily(value: String, st: ScreenType) = edit { it[Keys.appFontFamily(st)] = value }
suspend fun SettingsRepository.setAppHeadingFont(value: String, st: ScreenType) = edit { it[Keys.appHeadingFont(st)] = value }
suspend fun SettingsRepository.setAppFontBold(value: Boolean, st: ScreenType) = edit { it[Keys.appFontBold(st)] = value }
suspend fun SettingsRepository.setAppFontSizeScale(value: Float, st: ScreenType) = edit { it[Keys.appFontSizeScale(st)] = value.coerceIn(0.7f, 1.4f) }
suspend fun SettingsRepository.setSettingsStyle(value: String) = edit { it[Keys.SETTINGS_STYLE] = value }
suspend fun SettingsRepository.setHapticsStrength(value: String) = edit { it[Keys.HAPTICS_STRENGTH] = value }
suspend fun SettingsRepository.setHapticsDrag(value: Boolean) = edit { it[Keys.HAPTICS_DRAG] = value }
suspend fun SettingsRepository.setHapticsRecents(value: Boolean) = edit { it[Keys.HAPTICS_RECENTS] = value }
suspend fun SettingsRepository.setHapticsClose(value: Boolean) = edit { it[Keys.HAPTICS_CLOSE] = value }
suspend fun SettingsRepository.setHapticsSplit(value: Boolean) = edit { it[Keys.HAPTICS_SPLIT] = value }
suspend fun SettingsRepository.setHapticsLaunch(value: Boolean) = edit { it[Keys.HAPTICS_LAUNCH] = value }
suspend fun SettingsRepository.setHapticsEnabled(value: Boolean) = edit { it[Keys.HAPTICS_ENABLED] = value }
suspend fun SettingsRepository.setLiquidGlassEverywhere(value: Boolean) = edit { it[Keys.LIQUID_GLASS_EVERYWHERE] = value }
suspend fun SettingsRepository.setLiquidGlassLevel(value: Float) = edit { it[Keys.LIQUID_GLASS_LEVEL] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setSystemBarPadding(value: Boolean) = edit { it[Keys.SYSTEM_BAR_PADDING] = value }
suspend fun SettingsRepository.setSystemBarPaddingInner(value: Int) = edit {
    it[Keys.SYSTEM_BAR_PADDING_INNER] = value.coerceIn(LauncherSettings.MIN_SYSTEM_PADDING, LauncherSettings.MAX_SYSTEM_PADDING)
}
suspend fun SettingsRepository.setSystemBarPaddingCover(value: Int) = edit {
    it[Keys.SYSTEM_BAR_PADDING_COVER] = value.coerceIn(LauncherSettings.MIN_SYSTEM_PADDING, LauncherSettings.MAX_SYSTEM_PADDING)
}
suspend fun SettingsRepository.setUnlockTransition(value: Boolean) = edit { it[Keys.UNLOCK_TRANSITION] = value }
suspend fun SettingsRepository.setUnlockSystemHook(value: Boolean) = edit { it[Keys.UNLOCK_SYSTEM_HOOK] = value }
suspend fun SettingsRepository.setUnlockStyle(value: String) = edit { it[Keys.UNLOCK_STYLE] = value }
suspend fun SettingsRepository.setDpiWorkspace(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiWorkspace(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
suspend fun SettingsRepository.setDpiAppDrawer(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiAppDrawer(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
suspend fun SettingsRepository.setDpiSettings(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiSettings(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
suspend fun SettingsRepository.setAmoledDark(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.amoledDark(screenType)] = value }
/** Shows/hides the home status bar (clock + battery + signal). */
suspend fun SettingsRepository.setShowStatusBar(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showStatusBar(screenType)] = value }
/** Hides/shows the system status bar while the launcher is foreground (immersive home). */
suspend fun SettingsRepository.setHideSystemStatusBar(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.hideSystemStatusBar(screenType)] = value }
/** Darkness of the themed status bar's scrim (0..1). */
suspend fun SettingsRepository.setStatusBarScrimOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) =
    edit { it[Keys.statusBarScrimOpacity(screenType)] = value.coerceIn(0f, 1f) }
