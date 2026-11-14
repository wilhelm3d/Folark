package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_DRAWER_ROWS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_DRAWER_ROWS
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.model.DrawerHandle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.DrawerStyle
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchEngine
import org.arkikeskus.launcher.model.SearchPosition

// The setters for the app drawer and its search.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setDrawerFolderTabs(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerFolderTabs(screenType)] = value }
suspend fun SettingsRepository.setDrawerTabsExclusive(value: Boolean) = edit { it[Keys.DRAWER_TABS_EXCLUSIVE] = value }
suspend fun SettingsRepository.setDrawerTabsLocked(value: Boolean) = edit { it[Keys.DRAWER_TABS_LOCKED] = value }
suspend fun SettingsRepository.setSearchShortcuts(value: Boolean) = edit { it[Keys.SEARCH_SHORTCUTS] = value }
suspend fun SettingsRepository.setSearchWeb(value: Boolean) = edit { it[Keys.SEARCH_WEB] = value }
suspend fun SettingsRepository.setSearchWebEngine(value: SearchEngine) = edit { it[Keys.SEARCH_WEB_ENGINE] = value.stored }
suspend fun SettingsRepository.setSearchFiles(value: Boolean) = edit { it[Keys.SEARCH_FILES] = value }
suspend fun SettingsRepository.setHiddenAppsLock(value: Boolean) = edit { it[Keys.HIDDEN_APPS_LOCK] = value }
suspend fun SettingsRepository.setShowPrivateSpace(value: Boolean) = edit { it[Keys.SHOW_PRIVATE_SPACE] = value }
suspend fun SettingsRepository.setSearchActions(value: Boolean) = edit { it[Keys.SEARCH_ACTIONS] = value }
suspend fun SettingsRepository.setDrawerTwoPane(value: Boolean) = edit { it[Keys.DRAWER_TWO_PANE] = value }
suspend fun SettingsRepository.setDrawerAzHoldScroll(value: Boolean) = edit { it[Keys.DRAWER_AZ_HOLD_SCROLL] = value }
suspend fun SettingsRepository.setSearchInlineShortcuts(value: Boolean) = edit { it[Keys.SEARCH_INLINE_SHORTCUTS] = value }
suspend fun SettingsRepository.setSearchSettingsPages(value: Boolean) = edit { it[Keys.SEARCH_SETTINGS_PAGES] = value }
suspend fun SettingsRepository.setInnerSidebarAppDrawer(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerSidebarAppDrawer(screenType)] = value }
suspend fun SettingsRepository.setDrawerColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
suspend fun SettingsRepository.setDrawerRows(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerRows(screenType)] = value.coerceIn(MIN_DRAWER_ROWS, MAX_DRAWER_ROWS) }
suspend fun SettingsRepository.setShowDrawerSearch(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDrawerSearch(screenType)] = value }
suspend fun SettingsRepository.setShowDrawerLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDrawerLabels(screenType)] = value }
suspend fun SettingsRepository.setShowFrequentApps(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showFrequentApps(screenType)] = value }
suspend fun SettingsRepository.setDrawerClipboard(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerClipboard(screenType)] = value }
suspend fun SettingsRepository.setDrawerSearchPosition(value: SearchPosition, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerSearchPosition(screenType)] = value.stored }
suspend fun SettingsRepository.setDrawerIndexBarEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerIndexBarEnabled(screenType)] = value }
suspend fun SettingsRepository.setAzIndexScale(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.azIndexScale(screenType)] = value.coerceIn(LauncherSettings.MIN_AZ_INDEX_SCALE, LauncherSettings.MAX_AZ_INDEX_SCALE)
}
suspend fun SettingsRepository.setDrawerBottomSpace(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.drawerBottomSpace(screenType)] = value.coerceIn(0, LauncherSettings.MAX_DRAWER_BOTTOM_SPACE)
}
suspend fun SettingsRepository.setDrawerLetterHeaders(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLetterHeaders(screenType)] = value }
suspend fun SettingsRepository.setDrawerLargeLetterPopup(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLargeLetterPopup(screenType)] = value }
suspend fun SettingsRepository.setDrawerSwipeTabs(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerSwipeTabs(screenType)] = value }
suspend fun SettingsRepository.setDrawerFlickClose(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerFlickClose(screenType)] = value }
suspend fun SettingsRepository.setDrawerHandleStyle(value: DrawerHandle, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerHandleStyle(screenType)] = value.stored }
suspend fun SettingsRepository.setDrawerCornerRadius(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.drawerCornerRadius(screenType)] = value.coerceIn(LauncherSettings.DRAWER_CORNER_AUTO, LauncherSettings.MAX_DRAWER_CORNER_RADIUS)
}
suspend fun SettingsRepository.setAzIndexPosition(value: AzPosition, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.azIndexPosition(screenType)] = value.stored }
suspend fun SettingsRepository.setDrawerBackgroundBlur(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerBackgroundBlur(screenType)] = value.coerceIn(0f, 25f) }
suspend fun SettingsRepository.setDrawerScrimOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerScrimOpacity(screenType)] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setGlassBlurRadius(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassBlurRadius(screenType)] = value.coerceIn(0f, 50f) }
suspend fun SettingsRepository.setGlassDarkTint(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassDarkTint(screenType)] = value.coerceIn(0f, 0.9f) }
/** Switches the drawer style. The accent follows the new style's default only while the
 *  current accent is still a style default — a colour the user picked themselves is kept. */
suspend fun SettingsRepository.setDrawerStyle(value: DrawerStyle, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.drawerStyle(screenType)] = value.stored
    val current = it[Keys.drawerAccentColor(screenType)]
    if (current == null || current in DrawerStyle.DEFAULT_ACCENTS) {
        it[Keys.drawerAccentColor(screenType)] = value.defaultAccent
    }
}
suspend fun SettingsRepository.setDrawerThemeTint(value: DrawerTheme, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerThemeTint(screenType)] = value.stored }
suspend fun SettingsRepository.setDrawerGlassEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerGlassEnabled(screenType)] = value }
suspend fun SettingsRepository.setDrawerPanelOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.drawerPanelOpacity(screenType)] =
        value.coerceIn(LauncherSettings.MIN_DRAWER_PANEL_OPACITY, LauncherSettings.MAX_DRAWER_PANEL_OPACITY)
}
suspend fun SettingsRepository.setDrawerHeightFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerHeightFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
suspend fun SettingsRepository.setDrawerWidthFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerWidthFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
suspend fun SettingsRepository.setDrawerAlignment(value: DrawerAlignment, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAlignment(screenType)] = value.stored }
suspend fun SettingsRepository.setDrawerLayoutMode(mode: DrawerLayout, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLayoutMode(screenType)] = mode.stored }
suspend fun SettingsRepository.setSearchContacts(value: Boolean) = edit { it[Keys.SEARCH_CONTACTS] = value }
/** Whether the app drawer reopens at the top vs. remembers its last scroll position. */
suspend fun SettingsRepository.setDrawerOpensAtTop(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerOpensAtTop(screenType)] = value }
/** Lets app-drawer labels wrap onto a second line. */
suspend fun SettingsRepository.setTwoLineDrawerLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.twoLineDrawerLabels(screenType)] = value }
