package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.FoldAction
import org.arkikeskus.launcher.model.FoldTransition
import org.arkikeskus.launcher.model.LiveHingeStyle
import org.arkikeskus.launcher.model.ScreenType

// The setters for folding and unfolding.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setFoldAction(value: FoldAction, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.foldAction(screenType)] = value.stored }
suspend fun SettingsRepository.setUnfoldAction(value: FoldAction, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.unfoldAction(screenType)] = value.stored }
suspend fun SettingsRepository.setFoldTransitionStyle(value: FoldTransition, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.foldTransitionStyle(screenType)] = value.stored
}
suspend fun SettingsRepository.setFoldTransitionSpeed(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.foldTransitionSpeed(screenType)] = value
}
suspend fun SettingsRepository.setLiveHingeEndAngle(value: Int) = edit { it[Keys.LIVE_HINGE_END_ANGLE] = value }
suspend fun SettingsRepository.setLiveHingeSmoothing(value: Int) = edit { it[Keys.LIVE_HINGE_SMOOTHING] = value }
suspend fun SettingsRepository.setLiveHingeStyle(value: LiveHingeStyle) = edit { it[Keys.LIVE_HINGE_STYLE] = value.stored }
suspend fun SettingsRepository.setLiveHingeOuterStyle(value: LiveHingeStyle) = edit { it[Keys.LIVE_HINGE_OUTER_STYLE] = value.stored }
suspend fun SettingsRepository.setLiveHingeBlur(value: Int) = edit { it[Keys.LIVE_HINGE_BLUR] = value }
suspend fun SettingsRepository.setLiveHingeFrost(value: Float) = edit { it[Keys.LIVE_HINGE_FROST] = value }
suspend fun SettingsRepository.setHalfOpenedModeEnabled(value: Boolean) = edit { it[Keys.HALF_OPENED_ENABLED] = value }
suspend fun SettingsRepository.setHingeTilt(value: Boolean) = edit { it[Keys.HINGE_TILT] = value }
suspend fun SettingsRepository.setWallpaperHinge(value: Boolean) = edit { it[Keys.WALLPAPER_HINGE] = value }
