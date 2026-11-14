package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_HOME_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_HOME_ROWS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_ROWS
import org.arkikeskus.launcher.model.DockAlignment
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.IconSwipe
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.MinusOne
import org.arkikeskus.launcher.model.PageEffect
import org.arkikeskus.launcher.model.ScreenType

// The setters for the home screen and the dock.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setPageBounceEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.pageBounceEnabled(screenType)] = value }
suspend fun SettingsRepository.setHomeWallpaperDim(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeWallpaperDim(screenType)] = value.coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_DIM) }
suspend fun SettingsRepository.setHomeWallpaperBlur(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeWallpaperBlur(screenType)] = value.coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_BLUR) }
suspend fun SettingsRepository.setHomeBackgroundImage(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeBackgroundImage(screenType)] = value }
suspend fun SettingsRepository.setIconSwipeUpAction(value: IconSwipe, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.iconSwipeUpAction(screenType)] = value.stored }
suspend fun SettingsRepository.setNewPageOnDrag(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.newPageOnDrag(screenType)] = value }
suspend fun SettingsRepository.setAutoRemoveEmptyPages(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.autoRemoveEmptyPages(screenType)] = value }
suspend fun SettingsRepository.setPageScrollEffect(value: PageEffect) = edit { it[Keys.PAGE_SCROLL_EFFECT] = value.stored }
suspend fun SettingsRepository.setUnfoldAnimation(value: Boolean) = edit { it[Keys.UNFOLD_ANIMATION] = value }
suspend fun SettingsRepository.setAddNewApps(value: Boolean) = edit { it[Keys.ADD_NEW_APPS] = value }
suspend fun SettingsRepository.setVideoAwakeSeconds(value: Int) = edit { it[Keys.VIDEO_AWAKE_SECONDS] = value.coerceIn(0, 600) }
suspend fun SettingsRepository.setInnerDualPageWorkspace(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerDualPageWorkspace(screenType)] = value }
suspend fun SettingsRepository.setInnerDockAlignment(value: DockAlignment, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerDockAlignment(screenType)] = value.stored }
/** The page HOME returns to and the explicit page count, in one settings snapshot (the page
 *  menu's operations own both; see WorkspacePageOperations). */
suspend fun SettingsRepository.setHomePages(home: Int, count: Int, screenType: ScreenType) = edit {
    it[Keys.homePage(screenType)] = home.coerceIn(0, HomeLayoutRepository.MAX_PAGES - 1)
    it[Keys.homePageCount(screenType)] = count.coerceIn(0, HomeLayoutRepository.MAX_PAGES)
}
suspend fun SettingsRepository.setDockEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockEnabled(screenType)] = value }
suspend fun SettingsRepository.setDockColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
suspend fun SettingsRepository.setHomeColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_HOME_COLUMNS) }
suspend fun SettingsRepository.setHomeRows(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeRows(screenType)] = value.coerceIn(MIN_ROWS, MAX_HOME_ROWS) }
suspend fun SettingsRepository.setAllowLandscape(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.allowLandscape(screenType)] = value }
suspend fun SettingsRepository.setSwipeDownAction(value: HomeGesture, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeDownAction(screenType)] = value.stored }
suspend fun SettingsRepository.setDoubleTapAction(value: HomeGesture, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.doubleTapAction(screenType)] = value.stored }
suspend fun SettingsRepository.setSwipeUpAction(value: HomeGesture, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeUpAction(screenType)] = value.stored }
suspend fun SettingsRepository.setShowDockLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDockLabels(screenType)] = value }
suspend fun SettingsRepository.setShowHomeLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showHomeLabels(screenType)] = value }
suspend fun SettingsRepository.setParallaxWallpaper(enabled: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.parallaxWallpaper(screenType)] = enabled }
suspend fun SettingsRepository.setMinusOneScreen(value: MinusOne) = edit { it[Keys.MINUS_ONE_SCREEN] = value.stored }
suspend fun SettingsRepository.setDockBackgroundOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockBackgroundOpacity(screenType)] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setShowPageIndicator(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showPageIndicator(screenType)] = value }
/** Sets (or, for a blank/null [key], clears) the app launched by the left-edge home swipe. */
suspend fun SettingsRepository.setLeftSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.leftSwipeAppKey(screenType)] = key?.trim().orEmpty() }
/** Sets (or, for a blank/null [key], clears) the app launched by the right-edge home swipe. */
suspend fun SettingsRepository.setRightSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.rightSwipeAppKey(screenType)] = key?.trim().orEmpty() }
/** Locks/unlocks the desktop layout (blocks moving/removing/adding home + dock items). */
suspend fun SettingsRepository.setDesktopLocked(value: Boolean) = edit { it[Keys.DESKTOP_LOCKED] = value }
/** Lets home-screen labels wrap onto a second line (apps, folders, shortcuts; not the dock). */
suspend fun SettingsRepository.setTwoLineHomeLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.twoLineHomeLabels(screenType)] = value }
