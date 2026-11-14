package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.ui.AppEdits
import org.arkikeskus.launcher.feature.home.taskbar.pin
import org.arkikeskus.launcher.feature.home.workspace.Workspace
import org.arkikeskus.launcher.feature.home.workspace.WorkspacePageOperations
import org.arkikeskus.launcher.feature.home.workspace.permanentPageCount
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.AppShortcuts
import javax.inject.Inject
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.data.setHomePages
import org.arkikeskus.launcher.data.setSearchContacts

/** How long the themed status bar stays blanked after a heads-up post; SystemUI auto-dismisses a
 *  heads-up notification after ~5 s (its default decay) plus a hide animation, and we have no
 *  un-pin signal, so we time it out past that. 6 s left the system bar's dismissal animation
 *  overlapping our re-shown bar for ~1 s on a real WhatsApp heads-up (Pixel 8a, Android 17). */
private const val HEADS_UP_SUPPRESS_MS = 8_000L

/** A page-menu operation's result when storage refused it (the layout is left as it was). */
internal const val PAGE_FAILED = -2

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    notificationBadgeRepository: NotificationBadgeRepository,
    private val signalMonitor: org.arkikeskus.launcher.launcher.system.SignalMonitor,
    private val splitCoordinator: org.arkikeskus.launcher.data.SplitCoordinator,
) : ViewModel() {

    /** The current screen layout profile. */
    private val _screenType = MutableStateFlow(ScreenType.OUTER)
    fun setScreenType(type: ScreenType) {
        _screenType.value = type
        ensureScreenInitialized(type)
    }

    private fun ensureScreenInitialized(type: ScreenType) {
        if (type == ScreenType.INNER) {
            viewModelScope.launch {
                if (homeLayoutRepository.isHomeEmpty(ScreenType.INNER)) {
                    if (!homeLayoutRepository.isHomeEmpty(ScreenType.OUTER)) {
                        homeLayoutRepository.copyHomeLayout(from = ScreenType.OUTER, to = ScreenType.INNER)
                        settingsRepository.copyDockFavorites(from = ScreenType.OUTER, to = ScreenType.INNER)
                    } else {
                        val settings = settingsRepository.settings(ScreenType.INNER).first()
                        homeLayoutRepository.addBuiltin(
                            HomeItemEntity.BUILTIN_SMARTSPACE,
                            settings.home.columns, SMARTSPACE_DEFAULT_SPAN_Y, settings.home.columns, settings.home.rows, ScreenType.INNER
                        )
                        val apps = appRepository.apps.firstOrNull { it.isNotEmpty() } ?: emptyList()
                        val dockKeys = resolveDefaultDockKeys(context, apps)
                        if (dockKeys.isNotEmpty()) {
                            settingsRepository.seedDock(dockKeys, ScreenType.INNER)
                        }
                    }
                }
            }
        }
    }

    /** True while the first-run intro should cover the home screen (fresh installs only). */
    private val _showOnboarding = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding

    /** First-run seeding and package-event hygiene, split out (see HomeBootstrap.kt). Declared
     *  before the init block below, which starts both. */
    private val seeder = HomeSeeder(context, settingsRepository, homeLayoutRepository, appRepository)
    private val hygiene = HomeHygiene(appRepository, homeLayoutRepository, settingsRepository)

    init {
        // First-run experience (see [HomeSeeder]) and the quiet package-event hygiene
        // (see [HomeHygiene]), each split into its own collaborator.
        viewModelScope.launch {
            seeder.seedIfNeeded { _showOnboarding.value = true }
        }
        hygiene.start(viewModelScope) { pkg ->
            shortcutCache.keys.removeIf { it.startsWith("$pkg/") }
        }
    }

    /** Closes the first-run intro (finished or skipped) and never shows it again. */
    fun finishOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    /** Contacts granted in the intro → also enable the drawer's contact search (its whole point). */
    fun onContactsPermissionGranted() =
        viewModelScope.launch { settingsRepository.setSearchContacts(true) }

    /** Phone-state granted in the intro → restart the telephony callbacks so the network
     *  generation label appears without waiting for a process restart. */
    fun onPhonePermissionGranted() = signalMonitor.onPermissionsChanged()

    /** Called on every home resume: catches a READ_PHONE_STATE grant made outside onboarding
     *  (Settings ▸ Luvat, system app info), which has no callback into the monitor. Cheap no-op
     *  when the grant state hasn't changed. */
    fun refreshPhonePermission() = signalMonitor.refreshPermission()

    /** Resolved (label + icon) cache for pinned shortcuts, keyed by package/id/userSerial.
     *  Concurrent: filled from the uiState combine, invalidated from the package-event collector. */
    private val shortcutCache = java.util.concurrent.ConcurrentHashMap<String, AppShortcuts.Resolved>()

    /**
     * True for a short window after a heads-up-worthy notification is posted, while the system
     * transiently reveals its own status bar over the launcher. HomeScreen blanks the themed status bar
     * during it so the two don't overlap (the reveal is not dispatched as a WindowInsets change, so this
     * NotificationListener-fed signal is the only way to detect it — see the Fable-5 audit note).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val headsUpActive: StateFlow<Boolean> = notificationBadgeRepository.headsUp
        .flatMapLatest { postedAt ->
            // Compute the window from the timestamp so a value replayed on re-subscription (returning
            // home) is already expired and never re-blanks the bar; only a still-fresh post keeps it on.
            val remaining = postedAt + HEADS_UP_SUPPRESS_MS - SystemClock.elapsedRealtime()
            if (postedAt == 0L || remaining <= 0L) flowOf(false)
            else flow { emit(true); delay(remaining); emit(false) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    /** Notification badge counts by app badge key — their own state, apart from the layout. */
    val badges: StateFlow<Map<String, Int>> = notificationBadgeRepository.badges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = _screenType.flatMapLatest { screenType ->
        combine(
            settingsRepository.settings(screenType),
            settingsRepository.dockFavorites(screenType),
            appRepository.apps,
            // Paired with the saved app pairs (combine takes five typed flows): a pair shortcut on
            // home is drawn from, and follows edits to, its pair.
            homeLayoutRepository.homeItems(screenType).combine(appRepository.appPairs) { items, pairs -> items to pairs },
            // Notification badges are deliberately NOT part of this state (see [badges]): they change
            // with every notification, and the layout below must not be rebuilt for that.
        ) { settings, favoriteKeys, apps, homeItemsAndPairs ->
            val (homeItems, appPairs) = homeItemsAndPairs
            val byKey = apps.associateBy { it.key }
            val dockApps = favoriteKeys.mapNotNull { byKey[it] }.take(settings.dock.columns)
            // Resolve any not-yet-cached pinned shortcuts (label + icon) off the main thread.
            for (row in homeItems) {
                if (row.containerId == HomeItemEntity.HOME && row.isShortcut &&
                    // A floating or app-pair shortcut is the launcher's own: the system has nothing to resolve.
                    !HomeItemEntity.isLauncherShortcutId(row.shortcutId)
                ) {
                    val shortcutId = row.shortcutId ?: continue
                    val k = shortcutKey(row)
                    if (!shortcutCache.containsKey(k)) {
                        withContext(Dispatchers.IO) {
                            AppShortcuts.resolve(context, row.packageName, shortcutId, row.userSerial)
                        }?.let { shortcutCache[k] = it }
                    }
                }
            }
            // What each row shows (HomeEntryMapping.kt).
            val entries = homeEntries(homeItems, byKey, appPairs) { shortcutCache[it] }
            val occupied = homeItems.filter { it.containerId == HomeItemEntity.HOME }.map { it.page }.toSet()
            // A new trailing page is offered transiently by the workspace while dragging, and becomes
            // permanent once an icon lands.
            val pageCount = permanentPageCount(occupied, settings.home.pageCount)
            HomeUiState(
                settings = settings,
                dockApps = dockApps,
                entries = entries,
                pageCount = pageCount,
                homePage = settings.home.page.coerceIn(0, pageCount - 1),
                occupiedPages = occupied,
                loaded = true,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun launch(appItem: AppItem) {
        appRepository.launch(appItem)
            .onSuccess { splitCoordinator.opened(appItem) } // "Open with …", when switched on
            .onFailure { Log.w("HomeViewModel", "Failed to launch ${appItem.key}", it) }
    }

    /** Launches the app bound to the home left-edge swipe (see [launchEdgeApp]). */
    fun onLeftSwipe() = launchEdgeApp { it.home.leftSwipeAppKey }

    /** Launches the app bound to the home right-edge swipe (see [launchEdgeApp]). */
    fun onRightSwipe() = launchEdgeApp { it.home.rightSwipeAppKey }

    /** Launches the app whose key [keyOf] reads from the settings; nothing when none is set (blank)
     *  or the app no longer resolves (e.g. uninstalled). */
    private fun launchEdgeApp(keyOf: (LauncherSettings) -> String) = viewModelScope.launch {
        val key = keyOf(settingsRepository.settings(_screenType.value).first())
        if (key.isBlank()) return@launch
        appRepository.apps.first().firstOrNull { it.key == key }?.let { launch(it) }
    }

    /** Launches a shortcut placed on the home screen: a pinned deep shortcut, or a floating shortcut
     *  (which opens its app in a floating window — or normally, while that feature is switched off). */
    fun launchShortcut(shortcut: PlacedShortcut) {
        val floatingApp = shortcut.floatingApp
        val appPair = shortcut.appPair
        when {
            appPair != null -> appRepository.launchAppPair(appPair).onFailure {
                Log.w("HomeViewModel", "Failed to launch app pair ${appPair.name}", it)
            }
            floatingApp == null ->
                AppShortcuts.startById(context, shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
            uiState.value.settings.launch.floatingEnabled -> launchFloating(floatingApp)
            else -> launch(floatingApp)
        }
    }

    // --- Widget looks (Edit home ▸ Widget style) ---------------------------------------------------

    /** Per-widget looks (corner radius, padding, opacity) by home row id. */
    val widgetLooks: StateFlow<Map<Long, WidgetLook>> = settingsRepository.widgetLooks
        .map { raw ->
            buildMap {
                raw.forEach { (k, v) ->
                    val id = k.toLongOrNull()
                    val look = WidgetLook.parse(v)
                    if (id != null && look != null) put(id, look)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Saves the look of the widget in row [rowId]; null returns it to the default. */
    fun setWidgetLook(rowId: Long, look: WidgetLook?) = viewModelScope.launch {
        settingsRepository.setWidgetLook(rowId, look?.serialize())
    }

    /** Every launchable app, for the split-screen partner picker. */
    val allApps: StateFlow<List<AppItem>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Opens [appItem] in a floating window where the device supports one (else full screen). */
    fun launchFloating(appItem: AppItem) {
        appRepository.launchFloating(appItem).onFailure {
            Log.w("HomeViewModel", "Failed to launch ${appItem.key} in a floating window", it)
        }
    }

    /**
     * Dropped on a split zone pill: [app] on that side and Folark's split picker in the other half
     * ([zone] THREE: the picker chooses two more apps). False when no split can be made here.
     */
    suspend fun openSplitPicker(app: AppItem, zone: String): Boolean {
        if (!splitCoordinator.canSplit()) return false
        viewModelScope.launch {
            splitCoordinator.openPicker(
                org.arkikeskus.launcher.data.SplitApp(app),
                firstOnRight = zone == org.arkikeskus.launcher.model.DropZones.RIGHT,
                three = zone == org.arkikeskus.launcher.model.DropZones.THREE,
            )
        }
        return true
    }

    /** Opens [first] and [second] side by side as a split screen. */
    fun launchSplit(first: AppItem, second: AppItem) {
        appRepository.launchSplit(first, second).onFailure {
            Log.w("HomeViewModel", "Failed to launch ${first.key} for split screen", it)
        }
    }

    /**
     * Puts a shortcut on home that opens [first] and [second] as a split screen. The pair is saved
     * as an app pair (Settings ▸ App Pairs) unless these two apps are one already, in this order.
     */
    fun addAppPairShortcut(first: AppItem, second: AppItem) = viewModelScope.launch {
        val pair = appRepository.appPairs.first().firstOrNull { it.app1Key == first.key && it.app2Key == second.key }
            ?: org.arkikeskus.launcher.model.AppPair(
                id = java.util.UUID.randomUUID().toString(),
                name = "${first.label} + ${second.label}",
                app1Key = first.key,
                app1Package = first.packageName,
                app1ClassName = first.className,
                app1UserSerial = first.userSerial,
                app2Key = second.key,
                app2Package = second.packageName,
                app2ClassName = second.className,
                app2UserSerial = second.userSerial,
            ).also { appRepository.saveAppPair(it) }
        addPinnedShortcut(pair.app1Package, HomeItemEntity.appPairShortcutId(pair.id), pair.app1UserSerial)
    }

    /** Puts a shortcut on home that opens [appItem] straight in a floating window. Idempotent. */
    fun addFloatingShortcut(appItem: AppItem) = addPinnedShortcut(
        appItem.packageName, HomeItemEntity.floatingShortcutId(appItem.className), appItem.userSerial,
    )

    /** Stores a pinned shortcut on home (the system-level pin is done by the caller, which has a
     *  Context). Idempotent — the repository skips one already present. */
    fun addPinnedShortcut(packageName: String, shortcutId: String, userSerial: Long) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addShortcut(packageName, shortcutId, userSerial, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Removes a pinned shortcut from home and re-pins the remaining set for its package in the system. */
    fun removeShortcut(rowId: Long) = viewModelScope.launch {
        homeLayoutRepository.removeShortcut(rowId)?.let { remaining ->
            AppShortcuts.setPinned(context, remaining.packageName, remaining.userSerial, remaining.shortcutIds)
        }
    }

    /** Pins [item] in the system (IO — the Binder round-trips must not run on the main thread) and
     *  places it on the home grid only if the system pin succeeded (else it would be a dead cell). */
    fun pinShortcut(item: AppShortcuts.Item) = viewModelScope.launch {
        if (AppShortcuts.pin(context, item)) {
            addPinnedShortcut(item.packageName, item.id, item.userSerial)
        }
    }

    /** Places a freshly-bound widget on the home grid (spans are the picker's default). */
    fun addWidget(appWidgetId: Int, provider: String, spanX: Int, spanY: Int) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addWidget(appWidgetId, provider, spanX, spanY, s.home.columns, s.home.rows, _screenType.value)
    }

    suspend fun addWidgetAt(
        appWidgetId: Int?, provider: String?, builtinType: String?,
        placement: org.arkikeskus.launcher.model.WidgetPlacement,
    ): Boolean {
        val s = settingsRepository.settings(_screenType.value).first()
        return homeLayoutRepository.addWidgetAt(
            appWidgetId, provider, builtinType, placement, s.home.columns, s.home.rows, _screenType.value
        )
    }

    // --- Pages (the empty-area menu) -----------------------------------------------------------

    private val pageOperations = WorkspacePageOperations(
        settings = { settingsRepository.settings(_screenType.value).first() },
        occupiedPages = {
            homeLayoutRepository.homeItems(_screenType.value).first().filter { it.containerId == HomeItemEntity.HOME }
                .map { it.page }.toSet()
        },
        insertRows = { at -> homeLayoutRepository.insertPage(at, _screenType.value) },
        removeRows = { page, force -> homeLayoutRepository.removeEmptyPage(page, force, _screenType.value) },
        save = { home, count -> settingsRepository.setHomePages(home, count, _screenType.value) },
    )

    /** HOME returns to [page] from now on. */
    fun setHomePage(page: Int) = viewModelScope.launch { pageOperations.setHome(page) }

    /** True when the home screen shows nothing on [page]. A row can still be stored there (a
     *  disabled app, a shortcut its app dropped, a widget without a provider); such leftovers are
     *  purged when the user removes the page, or the page could never be removed. */
    fun isPageEmpty(page: Int): Boolean = uiState.value.entries.none { it.page == page }

    /** Removes an empty page, returning its navigation destination, null when rejected (not empty,
     *  the only page), or [PAGE_FAILED] when storage refused. */
    suspend fun removeEmptyPage(page: Int): Int? =
        pageOperation { pageOperations.remove(page, purge = isPageEmpty(page)) ?: return null } ?: PAGE_FAILED

    private suspend inline fun pageOperation(block: () -> Int): Int? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("HomeViewModel", "page operation failed", e)
        null
    }

    /** Removes a placed widget row — or a whole widget stack — and hands [onFreed] the AppWidgetHost
     *  ids the removed rows were bound to, so the caller can free them. */
    fun removeWidget(rowId: Long, onFreed: (List<Int>) -> Unit = {}) = viewModelScope.launch {
        onFreed(homeLayoutRepository.removeWidget(rowId))
        // Row ids can be reused, so a removed widget must not leave its look behind.
        settingsRepository.setWidgetLook(rowId, null)
    }

    /** Adds the built-in smartspace widget at the first free full-width rectangle (widget picker). */
    fun addSmartspace() = addBuiltin(HomeItemEntity.BUILTIN_SMARTSPACE, spanX = null, SMARTSPACE_DEFAULT_SPAN_Y)

    /** Adds the built-in notifications widget at the first free full-width rectangle (widget picker).
     *  Full width like the smartspace clock: the icon group centers inside its own footprint, so any
     *  narrower default could never sit symmetric on an odd column count (e.g. 4 wide in 5 columns). */
    fun addNotificationsWidget() = addBuiltin(HomeItemEntity.BUILTIN_NOTIFICATIONS, spanX = null, NOTIFICATIONS_DEFAULT_SPAN_Y)

    /** Adds the Category Card Interface briefing card notification widget at the first free full-width rectangle. */
    fun addSamsungNotificationWidget() = addBuiltin(HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET, spanX = null, spanY = 2)

    /** Adds the built-in battery widget (an app-icon-sized ring) at the first free cell (widget picker). */
    fun addBatteryWidget() = addBuiltin(HomeItemEntity.BUILTIN_BATTERY, BATTERY_SPAN, BATTERY_SPAN)

    /** Adds the built-in widget [type] at the first free [spanX]×[spanY] rectangle; [spanX] null is
     *  the grid's full width. */
    private fun addBuiltin(type: String, spanX: Int?, spanY: Int) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addBuiltin(type, spanX ?: s.home.columns, spanY, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Turns bound widget rows whose id this device's host never allocated back into placeholders
     *  (the Google Auto Backup / device-transfer restore path — see HomeScreen's startup sweep). */
    suspend fun unbindStaleWidgets(validIds: Set<Int>) = homeLayoutRepository.unbindStaleWidgets(validIds, _screenType.value)

    fun createWidgetStack(targetRowId: Long, droppedRowId: Long) = viewModelScope.launch {
        homeLayoutRepository.createWidgetStack(targetRowId, droppedRowId, _screenType.value)
    }

    fun addToWidgetStack(stackId: Long, widgetRowId: Long) = viewModelScope.launch {
        homeLayoutRepository.addToWidgetStack(stackId, widgetRowId, _screenType.value)
    }

    /** Adds a freshly bound app widget to a stack; false when the stack is gone (the caller then
     *  frees [appWidgetId]). */
    suspend fun addBoundWidgetToStack(stackId: Long, appWidgetId: Int, providerString: String): Boolean =
        homeLayoutRepository.addBoundWidgetToStack(stackId, appWidgetId, providerString, _screenType.value)

    fun addBuiltinToStack(stackId: Long, builtinType: String) = viewModelScope.launch {
        homeLayoutRepository.addBuiltinToStack(stackId, builtinType, _screenType.value)
    }

    /** Takes one widget out of a stack; [onFreed] receives its AppWidgetHost id, if it had one. */
    fun removeFromWidgetStack(stackId: Long, itemRowId: Long, onFreed: (Int) -> Unit = {}) = viewModelScope.launch {
        homeLayoutRepository.removeFromWidgetStack(stackId, itemRowId, _screenType.value)?.let(onFreed)
    }

    /** Binds a restored placeholder widget to its freshly allocated [appWidgetId] (the caller did the
     *  allocate + system bind/configure); the row turns back into a live widget. */
    suspend fun bindRestoredWidget(rowId: Long, appWidgetId: Int): Boolean =
        homeLayoutRepository.bindRestoredWidget(rowId, appWidgetId)

    /** Device-local ids of all bound widgets (for the startup AppWidgetHost id reconcile). */
    suspend fun boundWidgetIds(): Set<Int> = homeLayoutRepository.boundWidgetIds()

    fun reorderDock(newOrder: List<AppItem>) =
        viewModelScope.launch { settingsRepository.reorderVisibleDock(newOrder.map { it.key }, _screenType.value) }

    /** Takes [appItem] off home, with "… removed from home · Undo" (it goes back to its cell). */
    fun removeFromHome(appItem: AppItem) = viewModelScope.launch {
        val row = homeLayoutRepository.removeFromHome(appItem, _screenType.value) ?: return@launch
        AppEdits.removedFromHome(appItem.label) {
            viewModelScope.launch {
                val s = settingsRepository.settings(ScreenType.valueOf(row.screenType)).first()
                homeLayoutRepository.restore(row, s.home.columns, s.home.rows)
            }
        }
    }

    // ---- Edit home (several apps at once) ------------------------------------------------------

    /** Takes [apps] off home together, with one "N apps removed from home · Undo". */
    fun removeAll(apps: List<AppItem>) = viewModelScope.launch {
        if (apps.isEmpty()) return@launch
        val screen = _screenType.value
        val rows = homeLayoutRepository.removeAllFromHome(apps, screen)
        if (rows.isEmpty()) return@launch
        val undo: () -> Unit = {
            viewModelScope.launch {
                val s = settingsRepository.settings(screen).first()
                homeLayoutRepository.restoreAll(rows, s.home.columns, s.home.rows)
            }
        }
        if (rows.size == 1) AppEdits.removedFromHome(apps.first().label, undo) else AppEdits.removedMany(rows.size, undo)
    }

    /** Puts [apps] (two or more, all on home) in a new folder at the first one's place. */
    fun folderOf(apps: List<AppItem>, name: String) = viewModelScope.launch {
        if (apps.size < 2) return@launch
        val screen = _screenType.value
        val id = homeLayoutRepository.createFolder(apps[0], apps[1], name, screen)
        if (id < 0) return@launch
        apps.drop(2).forEach { homeLayoutRepository.addToFolder(it, id, screen) }
    }

    /** Moves [apps] to [page] (a new page when it is one past the last) and shows them there. */
    fun moveAllToPage(apps: List<AppItem>, page: Int) = viewModelScope.launch {
        if (apps.isEmpty()) return@launch
        val screen = _screenType.value
        val s = settingsRepository.settings(screen).first()
        val landed = homeLayoutRepository.moveAllToPage(apps, page, s.home.columns, s.home.rows, screen)
        AppEdits.reveal(apps.last().key, landed, screen.name)
    }

    /** Adds [apps] to home (edit mode's "Add apps") and shows where the last one went. */
    fun addAll(apps: List<AppItem>) = viewModelScope.launch {
        if (apps.isEmpty()) return@launch
        val screen = _screenType.value
        val s = settingsRepository.settings(screen).first()
        var page = 0
        apps.forEach { page = homeLayoutRepository.addToHome(it, s.home.columns, s.home.rows, screen) }
        AppEdits.reveal(apps.last().key, page, screen.name)
    }

    /** Hides [appItem] from the app list, with "… hidden · Undo". */
    fun hideApp(appItem: AppItem) = viewModelScope.launch {
        settingsRepository.setAppHidden(appItem.key, true)
        AppEdits.hidden(appItem.label) {
            viewModelScope.launch { settingsRepository.setAppHidden(appItem.key, false) }
        }
    }

    fun addToDock(appItem: AppItem) =
        viewModelScope.launch { settingsRepository.addToDock(appItem.key, _screenType.value) }

    /** Takes [appItem] off the dock, with "… removed from the dock · Undo" (back in its place). */
    fun removeFromDock(appItem: AppItem) = viewModelScope.launch {
        val screen = _screenType.value
        val index = settingsRepository.dockKeys(screen).indexOf(appItem.key)
        settingsRepository.removeFromDock(appItem.key, screen)
        if (index < 0) return@launch
        AppEdits.removedFromDock(appItem.label) {
            viewModelScope.launch { settingsRepository.addToDockAt(appItem.key, index, screen) }
        }
    }

    /** Puts [appItem] on home and shows it there (home jumps to its page, the icon pops in). */
    fun addToHome(appItem: AppItem) = viewModelScope.launch {
        val screen = _screenType.value
        val s = settingsRepository.settings(screen).first()
        val page = homeLayoutRepository.addToHome(appItem, s.home.columns, s.home.rows, screen)
        AppEdits.reveal(appItem.key, page, screen.name)
    }

    /** Moves/swaps a home shortcut; returns whether the repository accepted it (see [Workspace]). */
    suspend fun moveItem(appItem: AppItem, page: Int, cellX: Int, cellY: Int): Boolean =
        homeLayoutRepository.moveItem(appItem, page, cellX, cellY, _screenType.value)

    /** Moves/swaps a folder to a home cell (folder relocation on the grid). */
    suspend fun moveFolder(folderId: Long, page: Int, cellX: Int, cellY: Int): Boolean =
        homeLayoutRepository.moveFolder(folderId, page, cellX, cellY, _screenType.value)

    /** Moves or resizes a widget to the given bounds; returns whether the repository accepted it
     *  (the Workspace clears its optimistic override on false). */
    suspend fun setWidgetBounds(rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int): Boolean {
        val s = settingsRepository.settings(_screenType.value).first()
        return homeLayoutRepository.setWidgetBounds(rowId, page, cellX, cellY, spanX, spanY, s.home.columns, s.home.rows, _screenType.value)
    }

    /** Cross-surface: an icon dragged from the dock onto a home cell — place it and leave the dock. */
    fun moveToHome(appItem: AppItem, page: Int, cellX: Int, cellY: Int) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        withContext(NonCancellable) {
            if (homeLayoutRepository.placeAt(appItem, page, cellX, cellY, s.home.columns, s.home.rows, _screenType.value)) {
                settingsRepository.removeFromDock(appItem.key, _screenType.value)
            }
        }
    }

    /** Cross-surface: a home icon dragged into the dock at [index] — add to dock and leave home. */
    fun moveToDock(appItem: AppItem, index: Int) = viewModelScope.launch {
        withContext(NonCancellable) {
            settingsRepository.addToDockAt(appItem.key, index, _screenType.value)
            homeLayoutRepository.removeFromHome(appItem, _screenType.value)
        }
    }

    /** Drop an app onto another home app → make a folder of the two. */
    fun createFolder(target: AppItem, dropped: AppItem, name: String) = viewModelScope.launch {
        homeLayoutRepository.createFolder(target, dropped, name, _screenType.value)
    }

    /** Drop an app onto an existing folder → add it to that folder. */
    fun addToFolder(appItem: AppItem, folderId: Long) = viewModelScope.launch {
        homeLayoutRepository.addToFolder(appItem, folderId, _screenType.value)
    }

    /** Take an app out of a folder back onto the home screen (dissolves the folder if one is left). */
    fun removeFromFolder(appItem: AppItem, folderId: Long) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.removeFromFolder(appItem, folderId, s.home.columns, s.home.rows, _screenType.value)
    }

    fun renameFolder(folderId: Long, name: String) =
        viewModelScope.launch { homeLayoutRepository.renameFolder(folderId, name) }

    /** Sets a custom display name for an app (blank/null clears it back to the system label). */
    fun setCustomLabel(key: String, label: String?) =
        viewModelScope.launch { settingsRepository.setCustomLabel(key, label) }

    fun setCustomAppLabel(componentName: String, label: String) =
        viewModelScope.launch { settingsRepository.setCustomAppLabel(componentName, label) }

    // Declared last so [uiState] exists by the time this runs.
    init {
        // Settings ▸ Home ▸ "Remove empty pages": a page goes as soon as its last item leaves it.
        // Only a page that just BECAME empty is removed — one the user added on purpose and has not
        // filled yet is left alone.
        viewModelScope.launch {
            var previous: Pair<ScreenType, Set<Int>>? = null
            var skipNext = false
            uiState.collect { state ->
                if (!state.loaded) return@collect
                val type = _screenType.value
                val shown = state.entries.mapTo(HashSet()) { it.page }
                val before = previous
                previous = type to shown
                if (skipNext) {
                    // The emission caused by our own removal: its page numbers have shifted.
                    skipNext = false
                    return@collect
                }
                if (!state.settings.home.autoRemoveEmptyPages || before == null || before.first != type) return@collect
                val emptied = (before.second - shown).filter { it in 0 until state.pageCount }.maxOrNull()
                if (emptied != null && state.pageCount > 1) {
                    val removed = catching { removeEmptyPage(emptied) }.getOrNull()
                    if (removed != null && removed != PAGE_FAILED) skipNext = true
                }
            }
        }
    }
}
