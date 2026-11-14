package org.arkikeskus.launcher.data

import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.model.LaunchGlow
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.TaskbarAnim
import org.arkikeskus.launcher.model.TaskbarDivider
import org.arkikeskus.launcher.model.TaskbarHandleStyle
import org.arkikeskus.launcher.model.TaskbarHandles
import org.arkikeskus.launcher.model.TaskbarMediaPosition
import org.arkikeskus.launcher.model.TaskbarMediaShape
import org.arkikeskus.launcher.model.TaskbarMediaTap
import org.arkikeskus.launcher.model.TaskbarRecentsButton
import org.arkikeskus.launcher.model.TaskbarSource
import org.arkikeskus.launcher.model.TaskbarSplitSide
import org.arkikeskus.launcher.model.TaskbarTheme

// The setters for the taskbar.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setTaskbarPairs(ids: List<String>) = edit { it[Keys.TASKBAR_PAIRS] = ids.distinct().joinToString("\n") }
suspend fun SettingsRepository.setTaskbarSceneSlots(value: Int) = edit { it[Keys.TASKBAR_SCENE_SLOTS] = value.coerceIn(0, 4) }
suspend fun SettingsRepository.setTaskbarEnabled(value: Boolean) = edit { it[Keys.TASKBAR_ENABLED] = value }
/** Null goes back to the inner dock's apps. */
suspend fun SettingsRepository.setTaskbarPinned(keys: List<String>?) = edit { p ->
    p.remove(Keys.TASKBAR_PINNED)
    if (keys != null) p[Keys.TASKBAR_PINNED] = keys.distinct().joinToString("\n")
}
suspend fun SettingsRepository.setTaskbarRecentCount(value: Int) = edit { it[Keys.TASKBAR_RECENTS] = value.coerceIn(0, 6) }
suspend fun SettingsRepository.setTaskbarIconSize(value: Int) = edit { it[Keys.TASKBAR_ICON_SIZE] = value.coerceIn(32, 72) }
suspend fun SettingsRepository.setTaskbarIconSpacing(value: Int) = edit { it[Keys.TASKBAR_SPACING] = value.coerceIn(0, 24) }
suspend fun SettingsRepository.setTaskbarBottomOffset(value: Int) = edit { it[Keys.TASKBAR_BOTTOM] = value.coerceIn(0, 64) }
suspend fun SettingsRepository.setTaskbarTheme(value: TaskbarTheme) = edit { it[Keys.TASKBAR_THEME] = value.stored }
suspend fun SettingsRepository.setTaskbarOpacity(value: Float) = edit { it[Keys.TASKBAR_OPACITY] = value.coerceIn(0.1f, 1f) }
suspend fun SettingsRepository.setTaskbarGlassBlur(value: Int) = edit { it[Keys.TASKBAR_GLASS_BLUR] = value.coerceIn(0, 80) }
suspend fun SettingsRepository.setTaskbarHandles(value: TaskbarHandles) = edit { it[Keys.TASKBAR_HANDLES] = value.stored }
suspend fun SettingsRepository.setTaskbarOnHome(value: Boolean) = edit { it[Keys.TASKBAR_ON_HOME] = value }
suspend fun SettingsRepository.setTaskbarKeepOpen(value: Boolean) = edit { it[Keys.TASKBAR_KEEP_OPEN] = value }
suspend fun SettingsRepository.setTaskbarMediaEnabled(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA] = value }
suspend fun SettingsRepository.setTaskbarMediaShowPaused(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_PAUSED] = value }
suspend fun SettingsRepository.setTaskbarMediaPosition(value: TaskbarMediaPosition) = edit { it[Keys.TASKBAR_MEDIA_POSITION] = value.stored }
suspend fun SettingsRepository.setTaskbarDividerStyle(value: TaskbarDivider) = edit { it[Keys.TASKBAR_DIVIDER_STYLE] = value.stored }
suspend fun SettingsRepository.setTaskbarMediaTap(value: TaskbarMediaTap) = edit { it[Keys.TASKBAR_MEDIA_TAP] = value.stored }
suspend fun SettingsRepository.setTaskbarMediaControls(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_CONTROLS] = value }
suspend fun SettingsRepository.setTaskbarMediaShape(value: TaskbarMediaShape) = edit { it[Keys.TASKBAR_MEDIA_SHAPE] = value.stored }
suspend fun SettingsRepository.setTaskbarMediaBadge(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_BADGE] = value }
suspend fun SettingsRepository.setTaskbarSystemDrag(value: Boolean) = edit { it[Keys.TASKBAR_SYSTEM_DRAG] = value }
suspend fun SettingsRepository.setTaskbarEdgeSnap(value: Boolean) = edit { it[Keys.TASKBAR_EDGE_SNAP] = value }
suspend fun SettingsRepository.setTaskbarRecentsButton(value: TaskbarRecentsButton) = edit { it[Keys.TASKBAR_RECENTS_BUTTON] = value.stored }
suspend fun SettingsRepository.setTaskbarQuickSwitch(value: Boolean) = edit { it[Keys.TASKBAR_QUICK_SWITCH] = value }
suspend fun SettingsRepository.setTaskbarSplitControls(value: Boolean) = edit { it[Keys.TASKBAR_SPLIT_CONTROLS] = value }
suspend fun SettingsRepository.setTaskbarSplitSide(value: TaskbarSplitSide) = edit { it[Keys.TASKBAR_SPLIT_SIDE] = value.stored }
suspend fun SettingsRepository.setTaskbarThreeApp(value: Boolean) = edit { it[Keys.TASKBAR_THREE_APP] = value }
suspend fun SettingsRepository.setTaskbarLaunchGlow(value: LaunchGlow) = edit { it[Keys.TASKBAR_LAUNCH_GLOW] = value.stored }
suspend fun SettingsRepository.setTaskbarRunningApps(value: Boolean) = edit { it[Keys.TASKBAR_RUNNING_APPS] = value }
suspend fun SettingsRepository.setTaskbarRunningDots(value: Boolean) = edit { it[Keys.TASKBAR_RUNNING_DOTS] = value }
suspend fun SettingsRepository.setTaskbarMultiDots(value: Boolean) = edit { it[Keys.TASKBAR_MULTI_DOTS] = value }
suspend fun SettingsRepository.setTaskbarHiddenApps(value: Set<String>) = edit { it[Keys.TASKBAR_HIDDEN_APPS] = value.joinToString("\n") }
suspend fun SettingsRepository.setTaskbarAlwaysApps(value: Set<String>) = edit { it[Keys.TASKBAR_ALWAYS_APPS] = value.joinToString("\n") }
suspend fun SettingsRepository.setTaskbarAutoHideApps(value: Set<String>) = edit { it[Keys.TASKBAR_AUTOHIDE_APPS] = value.joinToString("\n") }
suspend fun SettingsRepository.setTaskbarMiniApps(value: Set<String>) = edit { it[Keys.TASKBAR_MINI_APPS] = value.joinToString("\n") }
suspend fun SettingsRepository.setTaskbarCoverBar(value: Boolean) = edit { it[Keys.TASKBAR_COVER_BAR] = value }
suspend fun SettingsRepository.setTaskbarCoverCount(value: Int) = edit {
    it[Keys.TASKBAR_COVER_COUNT] = value.coerceIn(LauncherSettings.MIN_COVER_BAR, LauncherSettings.MAX_COVER_BAR)
}
suspend fun SettingsRepository.setTaskbarHandleHint(value: Boolean) = edit { it[Keys.TASKBAR_HANDLE_HINT] = value }
suspend fun SettingsRepository.setTaskbarHandleStyle(value: TaskbarHandleStyle) = edit { it[Keys.TASKBAR_HANDLE_STYLE] = value.stored }
suspend fun SettingsRepository.setTaskbarHideButton(value: Boolean) = edit { it[Keys.TASKBAR_HIDE_BUTTON] = value }
suspend fun SettingsRepository.setTaskbarPinsLocked(value: Boolean) {
    // Pinned apps that follow the dock would still change with it: locked, they are kept as they are now.
    if (value && settings(ScreenType.INNER).first().taskbar.pinnedApps == null) {
        setTaskbarPinned(dockFavorites(ScreenType.INNER).first())
    }
    edit { it[Keys.TASKBAR_PINS_LOCKED] = value }
}
suspend fun SettingsRepository.setTaskbarSwipeDownHide(value: Boolean) = edit { it[Keys.TASKBAR_SWIPE_DOWN_HIDE] = value }
suspend fun SettingsRepository.setTaskbarOpenPreviews(value: Boolean) = edit { it[Keys.TASKBAR_OPEN_PREVIEWS] = value }
suspend fun SettingsRepository.setTaskbarAnim(value: TaskbarAnim) = edit { it[Keys.TASKBAR_ANIM] = value.stored }
suspend fun SettingsRepository.setTaskbarSource(value: TaskbarSource) = edit { it[Keys.TASKBAR_SOURCE] = value.stored }
suspend fun SettingsRepository.setSystemTaskbarMode(value: Int) = edit { it[Keys.SYSTEM_TASKBAR_MODE] = value }
suspend fun SettingsRepository.setTaskbarHideFullScreen(value: Boolean) = edit { it[Keys.TASKBAR_HIDE_FULL_SCREEN] = value }
suspend fun SettingsRepository.setTaskbarClearSystemInset(value: Boolean) = edit { it[Keys.TASKBAR_CLEAR_SYSTEM_INSET] = value }
