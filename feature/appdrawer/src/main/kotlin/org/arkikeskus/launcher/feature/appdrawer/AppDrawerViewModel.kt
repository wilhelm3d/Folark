package org.arkikeskus.launcher.feature.appdrawer

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.search.SearchAggregator
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.model.SearchResults
import javax.inject.Inject

/** A drawer folder resolved to its member [apps] (for rendering). */
data class DrawerFolderUi(val id: Long, val name: String, val apps: List<AppItem>)

data class AppDrawerUiState(
    val apps: List<AppItem> = emptyList(),
    val folders: List<DrawerFolderUi> = emptyList(),
    val query: String = "",
    val columns: Int = 4,
    val rows: Int = 6,
    val dockKeys: Set<String> = emptySet(),
    /** Visible dock capacity — the drawer hides "Add to dock" once this many favorites exist. */
    val dockColumns: Int = 4,
    val homeKeys: Set<String> = emptySet(),
    val showLabels: Boolean = true,
    val showSearch: Boolean = true,
    val badges: Map<String, Int> = emptyMap(),
    val showNotificationDots: Boolean = true,
    val notificationDotCount: Boolean = true,
    val notificationDotScale: Float = 1f,
    val useThemedIcons: Boolean = false,
    val iconPackPackage: String = "",
    val iconShape: String = "",
    val calc: SearchResult.Calculation? = null,
    val settingResults: List<SearchResult.Setting> = emptyList(),
    val contactResults: List<SearchResult.Contact> = emptyList(),
    val desktopLocked: Boolean = false,
    /** Multi-window settings: which of the floating / split entries the long-press menu offers. */
    val floatingLaunchEnabled: Boolean = true,
    val splitLaunchEnabled: Boolean = true,
    /** The display's icon-size multiplier (Settings ▸ Grid). */
    val iconSizeScale: Float = 1f,
    /** Drawer folders shown as tabs (Settings ▸ App drawer): id to name, plus the open tab (null = All). */
    val folderTabsEnabled: Boolean = false,
    /** An app in a tab is shown only there, not in "All" as well. */
    val tabsExclusive: Boolean = true,
    val tabs: List<Pair<Long, String>> = emptyList(),
    val selectedTab: Long? = null,
    /** Search extras: matching app shortcuts, and whether to offer the "search the web" row. */
    val shortcutResults: List<SearchResult.Shortcut> = emptyList(),
    val actionResults: List<SearchResult.Action> = emptyList(),
    val searchWeb: Boolean = true,
    val showFrequentApps: Boolean = false,
    val frequentApps: List<AppItem> = emptyList(),
    val appLabelTextScale: Float = 1f,
    /** Labels may wrap onto a second line (Settings ▸ App drawer ▸ Two-line labels). */
    val twoLineLabels: Boolean = true,
    val drawerOpensAtTop: Boolean = true,
    val drawerSearchPosition: String = LauncherSettings.SEARCH_TOP,
    val drawerIndexBarEnabled: Boolean = true,
    val azIndexPosition: String = LauncherSettings.AZ_POSITION_VERTICAL_RIGHT,
    val azIndexScale: Float = 1f,
    val drawerCornerRadius: Int = LauncherSettings.DRAWER_CORNER_AUTO,
    val drawerHandleStyle: String = LauncherSettings.DRAWER_HANDLE_BAR,
    val drawerBottomSpace: Int = 0,
    val drawerLetterHeaders: Boolean = false,
    val drawerLargeLetterPopup: Boolean = true,
    val drawerSwipeTabs: Boolean = true,
    val drawerFlickClose: Boolean = false,
    val drawerBackgroundBlur: Float = 0f,
    val drawerScrimOpacity: Float = 0.5f,
    val glassBlurRadius: Float = 25f,
    val glassDarkTint: Float = 0.45f,
    val drawerStyle: String = LauncherSettings.DRAWER_STYLE_STANDARD_GRID,
    val drawerThemeTint: String = LauncherSettings.DRAWER_THEME_SYSTEM,
    val drawerAccentColor: Int = LauncherSettings.DEFAULT_DRAWER_ACCENT,
    val drawerCustomColor: Int = LauncherSettings.DEFAULT_DRAWER_CUSTOM_COLOR,
    val drawerGlassEnabled: Boolean = false,
    val drawerPanelOpacity: Float = LauncherSettings.DEFAULT_DRAWER_PANEL_OPACITY,
    val drawerHeightFraction: Float = 1.0f,
    val drawerWidthFraction: Float = 1.0f,
    val drawerAlignment: String = LauncherSettings.DRAWER_ALIGNMENT_CENTER,
    val drawerLayoutMode: String = LauncherSettings.DRAWER_LAYOUT_GRID,
    val dpiAppDrawer: Float = 1f,
    val innerSidebarAppDrawer: Boolean = false,
)

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    notificationBadgeRepository: NotificationBadgeRepository,
    private val searchAggregator: SearchAggregator,
    private val appUsageRepository: org.arkikeskus.launcher.data.AppUsageRepository,
) : ViewModel() {

    private val _screenType = MutableStateFlow(ScreenType.OUTER)
    fun setScreenType(type: ScreenType) { _screenType.value = type }

    private val query = MutableStateFlow("")

    /** The open drawer tab: a drawer folder's id, or null for "All". */
    private val selectedTab = MutableStateFlow<Long?>(null)
    fun selectTab(id: Long?) { selectedTab.value = id }

    /** The drawer's Private Space section (PrivateSpace.kt); null when there is none, or it is off. */
    val privateSpace: StateFlow<PrivateSpaceUi?> = combine(
        appRepository.privateSpace,
        appRepository.privateApps,
        settingsRepository.settings(ScreenType.OUTER).map { it.drawer.showPrivateSpace }.distinctUntilChanged(),
    ) { space, apps, show ->
        if (space == null || !show) null else PrivateSpaceUi(space.locked, if (space.locked) emptyList() else apps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Locks an open Private Space, or asks Android to unlock it (it prompts for the screen lock). */
    fun togglePrivateSpace() {
        val space = privateSpace.value ?: return
        appRepository.setPrivateSpaceLocked(!space.locked)
    }

    fun privateSpaceInstallSender() = appRepository.privateSpaceInstallSender()

    fun launchPrivate(appItem: AppItem): Boolean =
        appRepository.launchPrivate(appItem)
            .onFailure { Log.w("AppDrawerViewModel", "Failed to launch a Private Space app", it) }
            .isSuccess

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults: Flow<Pair<String, SearchResults>> = combine(
        query.debounce { if (it.isBlank()) 0L else 200L }, // contacts hit ContentResolver; avoid per-keystroke
        appRepository.apps,                                 // install/uninstall/rename re-runs a live query
    ) { q, _ -> q }
        // flatMapLatest cancels the previous run; results arrive as each provider answers.
        .flatMapLatest { q -> searchAggregator.results(q).map { q to it } }

    val uiState: StateFlow<AppDrawerUiState> = _screenType.flatMapLatest { screenType -> combine(
        appRepository.apps,
        query,
        settingsRepository.settings(screenType),
        settingsRepository.dockFavorites(screenType),
        homeLayoutRepository.homeItems(screenType),
    ) { apps, q, settings, favorites, homeItems ->
        // Favorites whose app was uninstalled don't render in the dock (HomeViewModel drops them),
        // so they must not count toward its visible capacity either.
        val installed = apps.mapTo(HashSet()) { it.key }
        AppDrawerUiState(
            apps = apps,
            query = q,
            columns = settings.drawer.columns,
            rows = settings.drawer.rows,
            dockKeys = favorites.filterTo(LinkedHashSet()) { it in installed },
            dockColumns = settings.dock.columns,
            // Only apps placed directly on home (not folder rows or apps inside folders).
            homeKeys = homeItems
                .filter { it.containerId == HomeItemEntity.HOME && !it.isFolder }
                .map { it.key }
                .toSet(),
            showLabels = settings.drawer.showLabels,
            showSearch = settings.drawer.showSearch,
            showNotificationDots = settings.dots.enabled,
            notificationDotCount = settings.dots.showCount,
            notificationDotScale = settings.dots.scale,
            useThemedIcons = settings.icons.useThemedIcons,
            iconPackPackage = settings.icons.iconPack,
            iconShape = settings.icons.shape,
            desktopLocked = settings.home.desktopLocked,
            floatingLaunchEnabled = settings.launch.floatingEnabled,
            splitLaunchEnabled = settings.launch.splitEnabled,
            iconSizeScale = settings.icons.sizeScale,
            folderTabsEnabled = settings.drawer.folderTabs,
            tabsExclusive = settings.drawer.tabsExclusive,
            searchWeb = settings.search.web,
            showFrequentApps = settings.drawer.showFrequentApps,
            appLabelTextScale = settings.icons.labelTextScale,
            twoLineLabels = settings.drawer.twoLineLabels,
            drawerOpensAtTop = settings.drawer.opensAtTop,
            drawerSearchPosition = settings.drawer.searchPosition,
            drawerIndexBarEnabled = settings.drawer.indexBarEnabled,
            azIndexPosition = settings.drawer.azIndexPosition,
            azIndexScale = settings.drawer.azIndexScale,
            drawerCornerRadius = settings.drawer.cornerRadius,
            drawerHandleStyle = settings.drawer.handleStyle,
            drawerBottomSpace = settings.drawer.bottomSpace,
            drawerLetterHeaders = settings.drawer.letterHeaders,
            drawerLargeLetterPopup = settings.drawer.largeLetterPopup,
            drawerSwipeTabs = settings.drawer.swipeTabs,
            drawerFlickClose = settings.drawer.flickClose,
            drawerBackgroundBlur = settings.drawer.backgroundBlur,
            drawerScrimOpacity = settings.drawer.scrimOpacity,
            glassBlurRadius = settings.drawer.glassBlurRadius,
            glassDarkTint = settings.drawer.glassDarkTint,
            drawerStyle = settings.drawer.style,
            drawerThemeTint = settings.drawer.themeTint,
            drawerAccentColor = settings.drawer.accentColor,
            drawerCustomColor = settings.drawer.customColor,
            drawerGlassEnabled = settings.drawer.glassEnabled,
            drawerPanelOpacity = settings.drawer.panelOpacity,
            drawerHeightFraction = settings.drawer.heightFraction,
            drawerWidthFraction = settings.drawer.widthFraction,
            drawerAlignment = settings.drawer.alignment,
            drawerLayoutMode = settings.drawer.layoutMode,
            dpiAppDrawer = settings.appearance.dpiAppDrawer,
            innerSidebarAppDrawer = settings.drawer.innerSidebar,
        )
    } }.combine(notificationBadgeRepository.badges) { state, badges ->
        state.copy(badges = badges)
    }.combine(settingsRepository.hiddenApps) { state, hidden ->
        // Hide selected apps from the drawer (also excludes them from search results).
        if (hidden.isEmpty()) state else state.copy(apps = state.apps.filterNot { it.key in hidden })
    }.combine(settingsRepository.drawerFolders.combine(selectedTab) { folders, tab -> folders to tab }) { state, foldersAndTab ->
        val (folders, tab) = foldersAndTab
        // While searching (or with no folders) keep a flat list so folder members stay findable.
        if (state.query.isNotBlank() || folders.isEmpty()) {
            state
        } else if (state.folderTabsEnabled) {
            // Folders as tabs: a tab narrows the list to that folder; "All" shows the rest (or,
            // with Apps in a tab leave All off, every app).
            val open = folders.firstOrNull { it.id == tab }
            val shown = if (open == null) {
                if (state.tabsExclusive) {
                    val inTabs = folders.flatMap { it.appKeys }.toSet()
                    state.apps.filterNot { it.key in inTabs }
                } else {
                    state.apps
                }
            } else {
                val byKey = state.apps.associateBy { it.key }
                open.appKeys.mapNotNull { byKey[it] }
            }
            state.copy(apps = shown, tabs = folders.map { it.id to it.name }, selectedTab = open?.id)
        } else {
            val byKey = state.apps.associateBy { it.key }
            val folderUis = folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
            val inFolder = folders.flatMap { it.appKeys }.toSet()
            state.copy(apps = state.apps.filterNot { it.key in inFolder }, folders = folderUis)
        }
    }.combine(searchResults) { state, (searchedFor, results) ->
        if (state.query.isBlank()) {
            state // idle drawer: apps + folders already prepared above
        } else if (searchedFor.trim() != state.query.trim()) {
            // The real search is still on its way for this text (it waits for typing to pause):
            // show a plain name match meanwhile instead of an empty list.
            val typed = state.query.trim()
            state.copy(apps = state.apps.filter { it.label.contains(typed, ignoreCase = true) }, folders = emptyList())
        } else {
            state.copy(
                apps = results.apps,          // hidden-aware app matches from AppSearchProvider
                folders = emptyList(),         // no folders while searching
                calc = results.calc,
                settingResults = results.settings,
                contactResults = results.contacts,
                shortcutResults = results.shortcuts,
                actionResults = results.actions,
            )
        }
    }.combine(appUsageRepository.usage) { state, usage ->
        // Only the idle drawer gets a "most used" row; search keeps its own relevance order.
        if (!state.showFrequentApps || state.query.isNotBlank()) {
            state
        } else {
            val now = System.currentTimeMillis()
            val ranked = state.apps
                .mapNotNull { app ->
                    usage[app.key]?.let { app to org.arkikeskus.launcher.data.AppUsageRepository.currentScore(it, now) }
                }
                .filter { it.second > 0f }
                .sortedWith(
                    compareByDescending<Pair<AppItem, Float>> { it.second }
                        .thenBy { it.first.label.lowercase() },
                )
                .take(state.columns) // one row = drawerColumns; reacts to the icon-count setting
                .map { it.first }
            state.copy(frequentApps = ranked)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDrawerUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun hideApp(appItem: AppItem) = viewModelScope.launch { settingsRepository.setAppHidden(appItem.key, true) }

    /** Launches [appItem]; returns whether it started, so the drawer only closes on success. */
    fun onAppClick(appItem: AppItem): Boolean =
        appRepository.launch(appItem)
            .onFailure { Log.w("AppDrawerViewModel", "Failed to launch ${appItem.key}", it) }
            .isSuccess

    /** Opens [appItem] in a floating window (full screen where the device has none); returns whether
     *  it started, like [onAppClick]. */
    fun launchFloating(appItem: AppItem): Boolean =
        appRepository.launchFloating(appItem)
            .onFailure { Log.w("AppDrawerViewModel", "Failed to launch ${appItem.key} in a floating window", it) }
            .isSuccess

    /** Puts a shortcut on home that opens [appItem] straight in a floating window. Idempotent. */
    fun addFloatingShortcut(appItem: AppItem) = addPinnedShortcut(
        appItem.packageName, HomeItemEntity.floatingShortcutId(appItem.className), appItem.userSerial,
    )

    fun addToDock(appItem: AppItem) = viewModelScope.launch { settingsRepository.addToDock(appItem.key, _screenType.value) }

    fun removeFromDock(appItem: AppItem) = viewModelScope.launch { settingsRepository.removeFromDock(appItem.key, _screenType.value) }

    fun addToHome(appItem: AppItem) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addToHome(appItem, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Drag-and-drop from the drawer onto a specific home cell (free cell, or first free if taken). */
    fun addToHomeAt(appItem: AppItem, page: Int, cellX: Int, cellY: Int) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.placeAt(appItem, page, cellX, cellY, s.home.columns, s.home.rows, _screenType.value)
    }

    fun removeFromHome(appItem: AppItem) = viewModelScope.launch { homeLayoutRepository.removeFromHome(appItem, _screenType.value) }

    /** Stores a pinned shortcut on home (system-level pin done by the caller, which has a Context). */
    fun addPinnedShortcut(packageName: String, shortcutId: String, userSerial: Long) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addShortcut(packageName, shortcutId, userSerial, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Pins [item] in the system (IO — the Binder round-trips must not run on the main thread) and
     *  places it on the home grid only if the system pin succeeded (else it would be a dead cell). */
    fun pinShortcut(item: org.arkikeskus.launcher.ui.AppShortcuts.Item) = viewModelScope.launch {
        if (org.arkikeskus.launcher.ui.AppShortcuts.pin(context, item)) {
            addPinnedShortcut(item.packageName, item.id, item.userSerial)
        }
    }

    /** Sets a custom display name for an app (blank/null clears it back to the system label). */
    fun setCustomLabel(key: String, label: String?) =
        viewModelScope.launch { settingsRepository.setCustomLabel(key, label) }

    fun setCustomAppLabel(componentName: String, label: String) =
        viewModelScope.launch { settingsRepository.setCustomAppLabel(componentName, label) }

    // --- Drawer folders --------------------------------------------------------------------------
    fun renameDrawerFolder(id: Long, name: String) =
        viewModelScope.launch { settingsRepository.renameDrawerFolder(id, name) }

    fun deleteDrawerFolder(id: Long) =
        viewModelScope.launch { settingsRepository.deleteDrawerFolder(id) }

    fun addAppsToDrawerFolder(id: Long, keys: List<String>) =
        viewModelScope.launch { settingsRepository.addAppsToDrawerFolder(id, keys) }

    fun removeAppFromDrawerFolder(id: Long, key: String) =
        viewModelScope.launch { settingsRepository.removeAppFromDrawerFolder(id, key) }
}
