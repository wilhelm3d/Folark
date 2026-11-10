@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arkikeskus.launcher.feature.appdrawer

import org.arkikeskus.launcher.ui.AppEdits
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
import org.arkikeskus.launcher.data.drawerFolders
import org.arkikeskus.launcher.data.createDrawerFolder
import org.arkikeskus.launcher.data.renameDrawerFolder
import org.arkikeskus.launcher.data.deleteDrawerFolder
import org.arkikeskus.launcher.data.addAppsToDrawerFolder
import org.arkikeskus.launcher.data.removeAppFromDrawerFolder
import org.arkikeskus.launcher.data.moveAppsIntoDrawerFolder
import org.arkikeskus.launcher.data.moveDrawerFolder

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
    /** "Aspect ratio…" in the app menu (where the phone has Android's page). */
    val aspectRatioMenu: Boolean = true,
    val splitLaunchEnabled: Boolean = true,
    /** The display's icon-size multiplier (Settings ▸ Grid). */
    val iconSizeScale: Float = 1f,
    /** Drawer folders shown as tabs (Settings ▸ App drawer): id to name, plus the open tab (null = All). */
    val folderTabsEnabled: Boolean = false,
    /** An app in a tab is shown only there, not in "All" as well. */
    val tabsExclusive: Boolean = true,
    /** Settings ▸ App drawer ▸ Lock the tab order: chips can no longer be dragged. */
    val tabsLocked: Boolean = false,
    val tabs: List<Pair<Long, String>> = emptyList(),
    val selectedTab: Long? = null,
    /** Search extras: matching app shortcuts, and whether to offer the "search the web" row. */
    val shortcutResults: List<SearchResult.Shortcut> = emptyList(),
    val actionResults: List<SearchResult.Action> = emptyList(),
    val searchWeb: Boolean = true,
    /** Which engine the web row opens (Settings ▸ Search ▸ Web search engine). */
    val searchWebEngine: String = LauncherSettings.SEARCH_ENGINE_SYSTEM,
    /** Apps hidden from the list (shown under "Hidden apps" at its end), and whether that needs the fingerprint. */
    val hiddenApps: List<AppItem> = emptyList(),
    val hiddenLock: Boolean = false,
    /** Apps installed lately and not opened yet: marked "new" in the list. */
    val newApps: Set<String> = emptySet(),
    /** The top app found shows its shortcuts right under it (Settings ▸ Search). */
    val searchInlineShortcuts: Boolean = true,
    /** Unfolded: tabs in a list on the left, their apps on the right (Settings ▸ Drawer). */
    val twoPane: Boolean = false,
    /** A-Z bar: hold a letter, then slide to fine-scroll (Settings ▸ Drawer). */
    val azHoldScroll: Boolean = true,
    val showFrequentApps: Boolean = false,
    /** Settings ▸ App drawer ▸ Clipboard row. */
    val clipboardRow: Boolean = false,
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
    private val splitCoordinator: org.arkikeskus.launcher.data.SplitCoordinator,
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
            aspectRatioMenu = settings.launch.aspectRatioMenu,
            splitLaunchEnabled = settings.launch.splitEnabled,
            iconSizeScale = settings.icons.sizeScale,
            folderTabsEnabled = settings.drawer.folderTabs,
            tabsExclusive = settings.drawer.tabsExclusive,
            tabsLocked = settings.drawer.lockTabOrder,
            searchWeb = settings.search.web,
            searchWebEngine = settings.search.webEngine,
            hiddenLock = settings.drawer.hiddenLock,
            searchInlineShortcuts = settings.search.inlineShortcuts,
            twoPane = settings.drawer.twoPane,
            azHoldScroll = settings.drawer.azHoldScroll,
            showFrequentApps = settings.drawer.showFrequentApps,
            clipboardRow = settings.drawer.clipboardRow,
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
        // Hide selected apps from the drawer (also excludes them from search results); they are
        // kept apart for "Hidden apps" at the end of the list.
        if (hidden.isEmpty()) {
            state.copy(hiddenApps = emptyList())
        } else {
            state.copy(
                apps = state.apps.filterNot { it.key in hidden },
                hiddenApps = state.apps.filter { it.key in hidden },
            )
        }
    }.combine(settingsRepository.newApps) { state, fresh ->
        state.copy(newApps = fresh)
    }.combine(settingsRepository.drawerFolders.combine(selectedTab) { folders, tab -> folders to tab }) { state, foldersAndTab ->
        val (folders, tab) = foldersAndTab
        if (folders.isEmpty()) {
            state
        } else if (state.query.isNotBlank()) {
            // Searching keeps a flat list so folder members stay findable, but the folders stay
            // known: an open folder card survives a search, and a folder made from a search result
            // can be opened instead of vanishing into an empty list.
            val byKey = state.apps.associateBy { it.key }
            state.copy(
                folders = folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) },
            )
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
            // The folder list is kept in tab mode too: the tab menu and the folder popover look
            // folders up here, even though no tiles are drawn for them.
            val byKey = state.apps.associateBy { it.key }
            val folderUis = folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
            state.copy(
                apps = shown,
                tabs = folders.map { it.id to it.name },
                selectedTab = open?.id,
                folders = folderUis,
            )
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
            state.copy(apps = org.arkikeskus.launcher.data.search.AppMatcher.rank(state.apps, typed) { it.label })
        } else {
            state.copy(
                apps = results.apps,          // hidden-aware app matches from AppSearchProvider
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

    /** Hides [appItem] from the app list, with "… hidden · Undo". */
    fun hideApp(appItem: AppItem) = viewModelScope.launch {
        settingsRepository.setAppHidden(appItem.key, true)
        AppEdits.hidden(appItem.label) {
            viewModelScope.launch { settingsRepository.setAppHidden(appItem.key, false) }
        }
    }

    /** Shows a hidden app in the list again (Hidden apps at the end of the list). */
    fun unhideApp(appItem: AppItem) = viewModelScope.launch { settingsRepository.setAppHidden(appItem.key, false) }

    /** Launches [appItem]; returns whether it started, so the drawer only closes on success. */
    fun onAppClick(appItem: AppItem): Boolean =
        appRepository.launch(appItem)
            .onSuccess { splitCoordinator.opened(appItem) } // "Open with …", when switched on
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

    /** Takes [appItem] off the dock, with "… removed from the dock · Undo". */
    fun removeFromDock(appItem: AppItem) = viewModelScope.launch {
        val screen = _screenType.value
        val index = settingsRepository.dockKeys(screen).indexOf(appItem.key)
        settingsRepository.removeFromDock(appItem.key, screen)
        if (index < 0) return@launch
        AppEdits.removedFromDock(appItem.label) {
            viewModelScope.launch { settingsRepository.addToDockAt(appItem.key, index, screen) }
        }
    }

    /** Puts [appItem] on home; home then jumps to its page and pops it in (the drawer closes). */
    fun addToHome(appItem: AppItem) = viewModelScope.launch {
        val screen = _screenType.value
        val s = settingsRepository.settings(screen).first()
        val page = homeLayoutRepository.addToHome(appItem, s.home.columns, s.home.rows, screen)
        AppEdits.reveal(appItem.key, page, screen.name)
    }

    /** Drag-and-drop from the drawer onto a specific home cell (free cell, or first free if taken). */
    fun addToHomeAt(appItem: AppItem, page: Int, cellX: Int, cellY: Int) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.placeAt(appItem, page, cellX, cellY, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Takes [appItem] off home, with "… removed from home · Undo". */
    fun removeFromHome(appItem: AppItem) = viewModelScope.launch {
        val row = homeLayoutRepository.removeFromHome(appItem, _screenType.value) ?: return@launch
        AppEdits.removedFromHome(appItem.label) {
            viewModelScope.launch {
                val s = settingsRepository.settings(ScreenType.valueOf(row.screenType)).first()
                homeLayoutRepository.restore(row, s.home.columns, s.home.rows)
            }
        }
    }

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

    /** Adds [keys] to the folder; like every folder move they leave any other folder behind. */
    fun addAppsToDrawerFolder(id: Long, keys: List<String>) = viewModelScope.launch {
        settingsRepository.moveAppsIntoDrawerFolder(id, keys)
    }

    /**
     * Takes [key] out of the folder. A folder that loses its last app disappears, the way an iOS
     * folder does when everything is dragged out of it, so no empty folder is left behind.
     */
    fun removeAppFromDrawerFolder(id: Long, key: String) = viewModelScope.launch {
        if (settingsRepository.removeAppFromDrawerFolder(id, key) == 0) {
            settingsRepository.deleteDrawerFolder(id)
        }
    }

    /** Creates a folder and hands its new id back, so the drawer can open it at once. */
    fun createDrawerFolder(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(settingsRepository.createDrawerFolder(name))
    }

    /**
     * "Add to folder ▸ New folder": a folder made for [app] alone, handed back so the drawer can
     * open it for renaming. The drag gesture makes a folder from two apps at once; this is the path
     * for a folder that starts with one.
     */
    fun createFolderWithApp(app: AppItem, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = settingsRepository.createDrawerFolder(context.getString(R.string.folder_default_name))
        settingsRepository.moveAppsIntoDrawerFolder(id, listOf(app.key))
        onCreated(id)
    }

    /**
     * Moves a drawer tab to a new place. The tab order is the stored folder order, so one write keeps
     * the chips, the settings list and the folder tiles in step.
     */
    fun moveDrawerFolder(from: Int, to: Int) = viewModelScope.launch {
        settingsRepository.moveDrawerFolder(from, to)
    }

    /**
     * A folder made by dragging [app] onto [other] — the Pixel/iOS gesture. It is born holding both
     * apps and the drawer opens it straight away, so the name can be typed over in place. Two apps
     * that are already filed together just open that folder, instead of gaining a second one.
     */
    fun createFolderByDrop(app: AppItem, other: AppItem, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            val shared = settingsRepository.drawerFolders.first()
                .firstOrNull { app.key in it.appKeys && other.key in it.appKeys }
            if (shared != null) {
                onCreated(shared.id)
                return@launch
            }
            val id = settingsRepository.createDrawerFolder(context.getString(R.string.folder_default_name))
            settingsRepository.moveAppsIntoDrawerFolder(id, listOf(app.key, other.key))
            onCreated(id)
        }

    /**
     * Files [app] into the folder [id] — a drag onto its tile, or onto its tab in the tab row. Like
     * the iOS gesture this moves the app: one write takes it out of whatever folder held it.
     */
    fun addAppToDrawerFolder(app: AppItem, id: Long) = viewModelScope.launch {
        settingsRepository.moveAppsIntoDrawerFolder(id, listOf(app.key))
    }

    /**
     * The full app list for the folder editor's add-pane — not the tab-narrowed [AppDrawerUiState.apps]:
     * with a folder tab open, that holds only the folder's own apps, leaving nothing to add.
     * Hidden apps stay hidden.
     */
    val editorApps: StateFlow<List<AppItem>> = combine(
        appRepository.apps, settingsRepository.hiddenApps,
    ) { apps, hidden -> apps.filterNot { it.key in hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Drag-and-drop between folder tabs: the app came from the open tab ([target] null = the "All"
     * tab, so it is pulled out of its folder; a folder id = it moves into that folder). Leaving the
     * open folder first keeps the membership exact whichever way the drop went.
     */
    fun moveAppBetweenTabs(app: AppItem, target: Long?) {
        val source = selectedTab.value
        if (source == target) return
        viewModelScope.launch {
            if (target != null) {
                // One write: into the tab, out of the one it came from (a tab left empty goes too).
                settingsRepository.moveAppsIntoDrawerFolder(target, listOf(app.key))
            } else if (source != null) {
                // Out of its folder and into "All": a tab dragged empty disappears.
                if (settingsRepository.removeAppFromDrawerFolder(source, app.key) == 0) {
                    settingsRepository.deleteDrawerFolder(source)
                }
            }
        }
    }
}
