package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_LABEL_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_LABEL_SCALE
import org.arkikeskus.launcher.model.DotColor
import org.arkikeskus.launcher.model.DotPosition
import org.arkikeskus.launcher.model.DotShape
import org.arkikeskus.launcher.model.FolderAnimation
import org.arkikeskus.launcher.model.FolderPreview
import org.arkikeskus.launcher.model.FolderShape
import org.arkikeskus.launcher.model.FolderTheme
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.NudgeEffect
import org.arkikeskus.launcher.model.RingMode
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SizeChoice
import org.arkikeskus.launcher.model.ThemedStyle

// The setters for icons, notification dots and home folders.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setIconSizeScale(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.iconSizeScale(screenType)] =
        value.coerceIn(LauncherSettings.MIN_ICON_SIZE_SCALE, LauncherSettings.MAX_ICON_SIZE_SCALE)
}
suspend fun SettingsRepository.setFolderShape(value: FolderShape, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderShape(screenType)] = value.stored }
suspend fun SettingsRepository.setFolderTheme(value: FolderTheme, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTheme(screenType)] = value.stored }
suspend fun SettingsRepository.setFolderPreview(value: FolderPreview, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderPreview(screenType)] = value.stored }
suspend fun SettingsRepository.setFolderOpenColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.folderOpenColumns(screenType)] =
        value.coerceIn(LauncherSettings.MIN_FOLDER_OPEN_COLUMNS, LauncherSettings.MAX_FOLDER_OPEN_COLUMNS)
}
suspend fun SettingsRepository.setFolderOpenSize(value: SizeChoice, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderOpenSize(screenType)] = value.stored }
suspend fun SettingsRepository.setFolderTapFirstApp(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTapFirstApp(screenType)] = value }
suspend fun SettingsRepository.setDotShape(value: DotShape, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotShape(screenType)] = value.stored }
suspend fun SettingsRepository.setDotColor(value: DotColor, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotColor(screenType)] = value.stored }
suspend fun SettingsRepository.setDotPosition(value: DotPosition, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotPosition(screenType)] = value.stored }
suspend fun SettingsRepository.setFolderOpenAnimation(value: FolderAnimation) = edit { it[Keys.FOLDER_OPEN_ANIMATION] = value.stored }
suspend fun SettingsRepository.setDotAvatars(value: Boolean) = edit { it[Keys.DOT_AVATARS] = value }
suspend fun SettingsRepository.setShapeAllIcons(value: Boolean) = edit { it[Keys.ICON_SHAPE_ALL] = value }
suspend fun SettingsRepository.setIconNudge(value: Boolean) = edit { it[Keys.ICON_NUDGE] = value }
suspend fun SettingsRepository.setIconNudgeTaskbar(value: Boolean) = edit { it[Keys.ICON_NUDGE_TASKBAR] = value }
suspend fun SettingsRepository.setIconNudgeEffect(value: NudgeEffect) = edit { it[Keys.ICON_NUDGE_EFFECT] = value.stored }
suspend fun SettingsRepository.setIconNudgeSoftness(value: Float) = edit { it[Keys.ICON_NUDGE_SOFTNESS] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setIconRing(value: RingMode) = edit { it[Keys.ICON_RING] = value.stored }
suspend fun SettingsRepository.setFolderDirectApps(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderDirectApps(screenType)] = value }
suspend fun SettingsRepository.setThemedIconStyle(value: ThemedStyle) = edit { it[Keys.THEMED_ICON_STYLE] = value.stored }
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
