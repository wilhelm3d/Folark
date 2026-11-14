package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_LABEL_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_LABEL_SCALE
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType

// The setters for icons, notification dots and home folders.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setIconSizeScale(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.iconSizeScale(screenType)] =
        value.coerceIn(LauncherSettings.MIN_ICON_SIZE_SCALE, LauncherSettings.MAX_ICON_SIZE_SCALE)
}
suspend fun SettingsRepository.setFolderShape(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderShape(screenType)] = value }
suspend fun SettingsRepository.setFolderTheme(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTheme(screenType)] = value }
suspend fun SettingsRepository.setFolderPreview(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderPreview(screenType)] = value }
suspend fun SettingsRepository.setFolderOpenColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.folderOpenColumns(screenType)] =
        value.coerceIn(LauncherSettings.MIN_FOLDER_OPEN_COLUMNS, LauncherSettings.MAX_FOLDER_OPEN_COLUMNS)
}
suspend fun SettingsRepository.setFolderOpenSize(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderOpenSize(screenType)] = value }
suspend fun SettingsRepository.setFolderTapFirstApp(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTapFirstApp(screenType)] = value }
suspend fun SettingsRepository.setDotShape(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotShape(screenType)] = value }
suspend fun SettingsRepository.setDotColor(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotColor(screenType)] = value }
suspend fun SettingsRepository.setDotPosition(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotPosition(screenType)] = value }
suspend fun SettingsRepository.setFolderOpenAnimation(value: String) = edit { it[Keys.FOLDER_OPEN_ANIMATION] = value }
suspend fun SettingsRepository.setDotAvatars(value: Boolean) = edit { it[Keys.DOT_AVATARS] = value }
suspend fun SettingsRepository.setShapeAllIcons(value: Boolean) = edit { it[Keys.ICON_SHAPE_ALL] = value }
suspend fun SettingsRepository.setIconNudge(value: Boolean) = edit { it[Keys.ICON_NUDGE] = value }
suspend fun SettingsRepository.setIconNudgeTaskbar(value: Boolean) = edit { it[Keys.ICON_NUDGE_TASKBAR] = value }
suspend fun SettingsRepository.setIconNudgeEffect(value: String) = edit { it[Keys.ICON_NUDGE_EFFECT] = value }
suspend fun SettingsRepository.setIconNudgeSoftness(value: Float) = edit { it[Keys.ICON_NUDGE_SOFTNESS] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setIconRing(value: String) = edit { it[Keys.ICON_RING] = value }
suspend fun SettingsRepository.setFolderDirectApps(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderDirectApps(screenType)] = value }
suspend fun SettingsRepository.setThemedIconStyle(value: String) = edit { it[Keys.THEMED_ICON_STYLE] = value }
suspend fun SettingsRepository.setForceThemedIcons(value: Boolean) = edit { it[Keys.FORCE_THEMED_ICONS] = value }
suspend fun SettingsRepository.setShowNotificationDots(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showNotificationDots(screenType)] = value }
suspend fun SettingsRepository.setNotificationDotCount(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.notificationDotCount(screenType)] = value }
suspend fun SettingsRepository.setNotificationDotScale(value: Float, screenType: ScreenType = ScreenType.OUTER) =
    edit { it[Keys.notificationDotScale(screenType)] = value.coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE) }
suspend fun SettingsRepository.setUseThemedIcons(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.useThemedIcons(screenType)] = value }
/** Sets (or clears, for blank) the selected third-party icon pack package. */
suspend fun SettingsRepository.setIconPackPackage(pkg: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.iconPackPackage(screenType)] = pkg.trim() }
/** Size multiplier for app icon labels (clamped to the slider's range). */
suspend fun SettingsRepository.setAppLabelTextScale(value: Float, screenType: ScreenType = ScreenType.OUTER) =
    edit { it[Keys.appLabelTextScale(screenType)] = value.coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE) }
/** ARGB color for the home-surface app icon labels. */
suspend fun SettingsRepository.setAppLabelColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.appLabelColor(screenType)] = argb }
