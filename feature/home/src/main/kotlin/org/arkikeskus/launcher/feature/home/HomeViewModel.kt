package org.arkikeskus.launcher.feature.home

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

/** How long the themed status bar stays blanked after a heads-up post; SystemUI auto-dismisses a
 *  heads-up notification after ~5 s (its default decay) plus a hide animation, and we have no
 *  un-pin signal, so we time it out past that. 6 s left the system bar's dismissal animation
 *  overlapping our re-shown bar for ~1 s on a real WhatsApp heads-up (Pixel 8a, Android 17). */
private const val HEADS_UP_SUPPRESS_MS = 8_000L

/** Default grid footprint of the built-in smartspace widget (clamped to the column count). */
const val SMARTSPACE_DEFAULT_SPAN_X = 4
const val SMARTSPACE_DEFAULT_SPAN_Y = 2

/** Smallest allowed smartspace size — below 3 columns the clock + event row no longer fit. */
const val SMARTSPACE_MIN_SPAN_X = 3

/** Edit-frame default width of the built-in notifications widget (the picker ADDS it full-width —
 *  a narrower footprint can't center its icon group on an odd column count). */
const val NOTIFICATIONS_DEFAULT_SPAN_X = 4
const val NOTIFICATIONS_DEFAULT_SPAN_Y = 1

/** Smallest allowed notifications-widget width — one icon + the overflow chip still fit. */
const val NOTIFICATIONS_MIN_SPAN_X = 2

/** Default AND minimum footprint of the built-in battery widget — app-icon sized. */
const val BATTERY_SPAN = 1

/** The people widget defaults to a few rows of tiles; the user stretches it to the whole grid. */
const val PEOPLE_DEFAULT_SPAN_Y = 3

/** Narrowest people widget: two quiet tiles, or one tile with content. */
const val PEOPLE_MIN_SPAN_X = 2

/** A page-menu operation's result when storage refused it (the layout is left as it was). */
internal const val PAGE_FAILED = -2

/** Something placed at a free cell on a home page — an app shortcut or a folder. */
@Immutable
sealed interface HomeEntry {
    val page: Int
    val cellX: Int
    val cellY: Int
}

/** An app shortcut placed at a free cell on a home page. */
@Immutable
data class PlacedApp(
    val app: AppItem,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
) : HomeEntry

/** A folder placed at a free cell, holding [apps] (resolved, in order). */
@Immutable
data class PlacedFolder(
    val id: Long,
    val name: String,
    val apps: List<AppItem>,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
) : HomeEntry

/** A pinned deep shortcut placed at a free cell ([rowId] is the home_items row, used to move/remove). */
@Immutable
data class PlacedShortcut(
    val rowId: Long,
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    val label: String,
    val icon: ImageBitmap?,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    /** Non-null → a launcher-made shortcut that opens this app in a floating window (drawn with the
     *  app's own icon), not a system deep shortcut. */
    val floatingApp: AppItem? = null,
    /** Non-null → a shortcut for this saved app pair; [pairApps] are its two apps, for the icon. */
    val appPair: org.arkikeskus.launcher.model.AppPair? = null,
    val pairApps: Pair<AppItem, AppItem>? = null,
    /** The pair's optional third (floating) app, drawn small between the other two. */
    val pairThirdApp: AppItem? = null,
) : HomeEntry

/** A bound app widget placed at a home cell, occupying [spanX]×[spanY] cells. */
@Immutable
data class PlacedWidget(
    val rowId: Long,
    val appWidgetId: Int,
    val provider: ComponentName,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : HomeEntry

/**
 * A restored widget whose device-local `appWidgetId` isn't bound on this device yet. Rendered as a
 * tap-to-set-up placeholder occupying [spanX]×[spanY] cells until the user re-binds it (see the restore
 * flow in HomeScreen). Its row already carries the [provider] and spans from the backup.
 */
@Immutable
data class PendingWidget(
    val rowId: Long,
    val provider: ComponentName,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : HomeEntry

/** A built-in launcher widget ([type] — smartspace or notifications), occupying [spanX]×[spanY]. */
@Immutable
data class PlacedBuiltin(
    val rowId: Long,
    val type: String,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : HomeEntry

/** A Nothing OS 3 style stack of widgets occupying [spanX]×[spanY] cells. Swipable vertically. */
@Immutable
data class PlacedWidgetStack(
    val rowId: Long,
    val items: List<HomeEntry>,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : HomeEntry

@Immutable
data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val dockApps: List<AppItem> = emptyList(),
    val entries: List<HomeEntry> = emptyList(),
    val pageCount: Int = 1,
    /** The page HOME returns to, clamped to the pages that exist. */
    val homePage: Int = 0,
    /** Pages with any stored row on them — including rows that resolved to nothing (an uninstalled
     *  shortcut's), which [entries] leaves out but the page menu must still treat as occupied. */
    val occupiedPages: Set<Int> = emptySet(),
    /** False for the placeholder shown before settings and the layout have been read. */
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    notificationBadgeRepository: NotificationBadgeRepository,
    private val signalMonitor: org.arkikeskus.launcher.launcher.system.SignalMonitor,
    private val iconPackRepository: org.arkikeskus.launcher.data.IconPackRepository,
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

    init {
        // First-run experience, decided ONCE per install: on an empty home surface, seed the
        // smartspace widget + the default dock (phone/messages/Settings/browser/camera) and show
        // the intro. Device-local flags so removing items never re-seeds, and updating users
        // (non-empty home) get the flags silently without any of it.
        viewModelScope.launch {
            val seeded = settingsRepository.defaultLayoutSeededOnce()
            val onboarded = settingsRepository.onboardingDoneOnce()
            if (seeded && onboarded) return@launch
            // Decide freshness ONCE and persist the decision BEFORE any seeding writes: a death
            // mid-seed leaves the home non-empty, which must not flip a fresh install into an
            // "updating user" on the retry (dock never seeded, intro never shown).
            val fresh = settingsRepository.firstRunFreshOnce() ?: run {
                val decided = homeLayoutRepository.isHomeEmpty(_screenType.value)
                settingsRepository.setFirstRunFresh(decided)
                decided
            }
            if (!seeded) {
                if (fresh) {
                    // The app list needs a beat on a cold start; a fresh device always has apps.
                    val apps = appRepository.apps.first { it.isNotEmpty() }
                    val dockKeys = resolveDefaultDockKeys(context, apps)
                    // Seed BOTH display profiles. The view model starts before it knows which
                    // display it is on, so seeding only "the current one" always seeded the outer
                    // profile and left the inner display with an empty home and dock forever
                    // (the seed runs once per install).
                    for (type in ScreenType.entries) {
                        val settings = settingsRepository.settings(type).first()
                        // Idempotent on a retry: the widget only while home is still empty, the dock
                        // only while the favorites are still empty.
                        if (homeLayoutRepository.isHomeEmpty(type)) {
                            // Full width so the centered clock sits in the middle of the screen.
                            homeLayoutRepository.addBuiltin(
                                HomeItemEntity.BUILTIN_SMARTSPACE,
                                settings.home.columns, SMARTSPACE_DEFAULT_SPAN_Y, settings.home.columns, settings.home.rows, type
                            )
                        }
                        if (dockKeys.isNotEmpty() && settingsRepository.dockFavorites(type).first().isEmpty()) {
                            settingsRepository.seedDock(dockKeys, type)
                            // The seed is 5 apps but the dock defaults to 4 columns — widen to fit.
                            if (dockKeys.size > settings.dock.columns) {
                                settingsRepository.setDockColumns(dockKeys.size, type)
                            }
                        }
                    }
                }
                settingsRepository.setDefaultLayoutSeeded()
            }
            if (!onboarded) {
                if (fresh) _showOnboarding.value = true else settingsRepository.setOnboardingDone()
            }
        }
        // A package update can change a pinned shortcut's label/icon; drop its cached resolutions
        // so the uiState combine re-resolves them (cache keys are "package/id/serial").
        viewModelScope.launch {
            appRepository.packageEvents.collect { pkg ->
                shortcutCache.keys.removeIf { it.startsWith("$pkg/") }
            }
        }
        // Ghost-row hygiene: an uninstalled app's home rows never render but still occupy their
        // cells, silently blocking placement/resizes/moves with no visible reason. Delete them on
        // the uninstall event, and sweep once per launch for uninstalls the launcher slept through.
        viewModelScope.launch {
            appRepository.packageRemovals.collect { (pkg, serial) ->
                homeLayoutRepository.removeAppRowsForPackage(pkg, serial)
            }
        }
        viewModelScope.launch {
            appRepository.apps.first { it.isNotEmpty() } // let the app providers settle on a cold start
            homeLayoutRepository.removeStaleAppRows(appRepository::isAppInstalled)
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
            // Children grouped by their folder id, kept in stored order.
            val childrenByFolder = homeItems
                .filter { it.containerId != HomeItemEntity.HOME }
                .groupBy { it.containerId }
            // Resolve any not-yet-cached pinned shortcuts (label + icon) off the main thread.
            for (row in homeItems) {
                if (row.containerId == HomeItemEntity.HOME && row.isShortcut &&
                    // A floating or app-pair shortcut is the launcher's own: the system has nothing to resolve.
                    !HomeItemEntity.isLauncherShortcutId(row.shortcutId)
                ) {
                    val k = "${row.packageName}/${row.shortcutId}/${row.userSerial}"
                    if (!shortcutCache.containsKey(k)) {
                        withContext(Dispatchers.IO) {
                            AppShortcuts.resolve(context, row.packageName, row.shortcutId!!, row.userSerial)
                        }?.let { shortcutCache[k] = it }
                    }
                }
            }
            val entries = homeItems
                .filter { it.containerId == HomeItemEntity.HOME }
                .mapNotNull { row ->
                    when {
                        row.builtinType == HomeItemEntity.BUILTIN_WIDGET_STACK -> {
                            val childEntries = childrenByFolder[row.id].orEmpty().mapNotNull { child ->
                                when {
                                    child.isBuiltin && child.builtinType != HomeItemEntity.BUILTIN_WIDGET_STACK -> PlacedBuiltin(
                                        rowId = child.id,
                                        type = child.builtinType!!,
                                        page = child.page, cellX = child.cellX, cellY = child.cellY,
                                        spanX = child.spanX, spanY = child.spanY,
                                    )
                                    child.isWidget -> {
                                        ComponentName.unflattenFromString(child.widgetProvider.orEmpty())?.let { provider ->
                                            PlacedWidget(
                                                rowId = child.id,
                                                appWidgetId = child.appWidgetId!!,
                                                provider = provider,
                                                page = child.page, cellX = child.cellX, cellY = child.cellY,
                                                spanX = child.spanX, spanY = child.spanY,
                                            )
                                        }
                                    }
                                    else -> {
                                        val cwp = child.widgetProvider
                                        if (cwp != null) {
                                            ComponentName.unflattenFromString(cwp)?.let { provider ->
                                                PendingWidget(
                                                    rowId = child.id,
                                                    provider = provider,
                                                    page = child.page, cellX = child.cellX, cellY = child.cellY,
                                                    spanX = child.spanX, spanY = child.spanY,
                                                )
                                            }
                                        } else null
                                    }
                                }
                            }
                            if (childEntries.isNotEmpty()) {
                                PlacedWidgetStack(
                                    rowId = row.id,
                                    items = childEntries,
                                    page = row.page, cellX = row.cellX, cellY = row.cellY,
                                    spanX = row.spanX, spanY = row.spanY,
                                )
                            } else null
                        }
                        row.isBuiltin -> PlacedBuiltin(
                            rowId = row.id,
                            type = row.builtinType!!,
                            page = row.page, cellX = row.cellX, cellY = row.cellY,
                            spanX = row.spanX, spanY = row.spanY,
                        )
                        row.isFolder -> {
                            // distinctBy: a duplicate child row (old data from before the merge fix, or
                            // an edited backup) must never reach the folder grid's app-key lazy keys.
                            val folderApps = childrenByFolder[row.id].orEmpty()
                                .mapNotNull { byKey[it.key] }
                                .distinctBy { it.key }
                            PlacedFolder(row.id, row.folderName.orEmpty(), folderApps, row.page, row.cellX, row.cellY)
                        }
                        row.isShortcut -> {
                            val pairId = HomeItemEntity.appPairShortcutPairId(row.shortcutId)
                            val floatingClass = HomeItemEntity.floatingShortcutClass(row.shortcutId)
                            if (pairId != null) {
                                // A saved app pair: shown only while the pair and both its apps exist.
                                val pair = appPairs.firstOrNull { it.id == pairId }
                                val firstApp = pair?.let { byKey[it.app1Key] }
                                val secondApp = pair?.let { byKey[it.app2Key] }
                                if (pair != null && firstApp != null && secondApp != null) {
                                    PlacedShortcut(
                                        rowId = row.id,
                                        packageName = row.packageName,
                                        shortcutId = row.shortcutId!!,
                                        userSerial = row.userSerial,
                                        label = pair.name,
                                        icon = null,
                                        page = row.page,
                                        cellX = row.cellX,
                                        cellY = row.cellY,
                                        appPair = pair,
                                        pairApps = firstApp to secondApp,
                                        // Missing (uninstalled) third app: the pair still shows and works.
                                        pairThirdApp = if (pair.hasThirdApp) byKey[pair.app3Key] else null,
                                    )
                                } else {
                                    null
                                }
                            } else if (floatingClass != null) {
                                // Launcher-made "open in a floating window" shortcut: it is its app.
                                byKey["${row.packageName}/$floatingClass/${row.userSerial}"]?.let { app ->
                                    PlacedShortcut(
                                        rowId = row.id,
                                        packageName = row.packageName,
                                        shortcutId = row.shortcutId!!,
                                        userSerial = row.userSerial,
                                        label = app.label,
                                        icon = null,
                                        page = row.page,
                                        cellX = row.cellX,
                                        cellY = row.cellY,
                                        floatingApp = app,
                                    )
                                }
                            } else {
                                val k = "${row.packageName}/${row.shortcutId}/${row.userSerial}"
                                shortcutCache[k]?.let { r ->
                                    PlacedShortcut(
                                        rowId = row.id,
                                        packageName = row.packageName,
                                        shortcutId = row.shortcutId!!,
                                        userSerial = row.userSerial,
                                        label = r.label,
                                        icon = r.icon,
                                        page = row.page,
                                        cellX = row.cellX,
                                        cellY = row.cellY,
                                    )
                                }
                            }
                        }
                        row.isWidget -> {
                            ComponentName.unflattenFromString(row.widgetProvider.orEmpty())?.let { provider ->
                                PlacedWidget(
                                    rowId = row.id,
                                    appWidgetId = row.appWidgetId!!,
                                    provider = provider,
                                    page = row.page, cellX = row.cellX, cellY = row.cellY,
                                    spanX = row.spanX, spanY = row.spanY,
                                )
                            }
                        }
                        else -> {
                            // A restored widget row carries a provider but no bound id → placeholder until
                            // re-bound; anything else is a plain app. (Local val so the null-check smart-casts
                            // — widgetProvider is a cross-module property that can't be smart-cast directly.)
                            val wp = row.widgetProvider
                            if (wp != null) {
                                ComponentName.unflattenFromString(wp)?.let { provider ->
                                    PendingWidget(
                                        rowId = row.id,
                                        provider = provider,
                                        page = row.page, cellX = row.cellX, cellY = row.cellY,
                                        spanX = row.spanX, spanY = row.spanY,
                                    )
                                }
                            } else {
                                byKey[row.key]?.let { PlacedApp(it, row.page, row.cellX, row.cellY) }
                            }
                        }
                    }
                }
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
        appRepository.launch(appItem).onFailure {
            Log.w("HomeViewModel", "Failed to launch ${appItem.key}", it)
        }
    }

    /**
     * Launches the app bound to the home left-edge swipe. No-op when
     * none is configured (blank key) or the app no longer resolves (e.g. uninstalled).
     */
    fun onLeftSwipe() = viewModelScope.launch {
        val key = settingsRepository.settings(_screenType.value).first().home.leftSwipeAppKey
        if (key.isBlank()) return@launch
        appRepository.apps.first().firstOrNull { it.key == key }?.let { launch(it) }
    }

    /**
     * Launches the app bound to the home right-edge swipe. No-op when
     * none is configured (blank key) or the app no longer resolves (e.g. uninstalled).
     */
    fun onRightSwipe() = viewModelScope.launch {
        val key = settingsRepository.settings(_screenType.value).first().home.rightSwipeAppKey
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

    // --- Per-app icons (the "Change icon" picker) ---------------------------------------------------

    /** The installed icon packs the picker can choose from (queried off the main thread). */
    val iconPacks: StateFlow<List<org.arkikeskus.launcher.data.IconPackInfo>> =
        flow { emit(withContext(Dispatchers.Default) { iconPackRepository.installedPacks() }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Hand-picked icons, app key → "pack|drawable" — the picker shows "use default" for these. */
    val customIcons: StateFlow<Map<String, String>> = settingsRepository.customIcons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** The names of the icons [pack] offers (parsing the pack is slow the first time). */
    suspend fun iconPackDrawableNames(pack: String): List<String> = withContext(Dispatchers.Default) {
        iconPackRepository.get(pack)?.drawableNames().orEmpty()
    }

    /** One of [pack]'s icons as a small bitmap for the picker grid; null if it cannot be drawn. */
    suspend fun iconPackBitmap(pack: String, drawable: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val cacheKey = "$pack|$drawable"
        // Scrolling the grid back and forth re-asks for the same icons; decode each one once.
        iconPickerCache.get(cacheKey)?.let { return@withContext it }
        runCatching {
            iconPackRepository.get(pack)?.getDrawable(drawable)?.let { icon ->
                val bitmap = android.graphics.Bitmap.createBitmap(
                    ICON_PICKER_PX, ICON_PICKER_PX, android.graphics.Bitmap.Config.ARGB_8888,
                )
                icon.setBounds(0, 0, ICON_PICKER_PX, ICON_PICKER_PX)
                icon.draw(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap().also { iconPickerCache.put(cacheKey, it) }
            }
        }.getOrNull()
    }

    /** Decoded picker icons, "pack|drawable" → bitmap (about 50 KB each at [ICON_PICKER_PX]). */
    private val iconPickerCache = android.util.LruCache<String, ImageBitmap>(ICON_PICKER_CACHE_SIZE)

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

    /** Gives [appItem] the icon [drawable] of [pack], everywhere it is shown. */
    fun setCustomIcon(appItem: AppItem, pack: String, drawable: String) = viewModelScope.launch {
        settingsRepository.setCustomIcon(appItem.key, "$pack|$drawable")
    }

    /** Returns [appItem] to its normal icon. */
    fun clearCustomIcon(appItem: AppItem) = viewModelScope.launch {
        settingsRepository.setCustomIcon(appItem.key, null)
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

    /** Opens an empty page at [at] (0..pageCount); what was there and after moves right. Returns the
     *  new index, -1 at the page limit, or [PAGE_FAILED] when storage refused. */

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
    fun addSmartspace() = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addBuiltin(
            HomeItemEntity.BUILTIN_SMARTSPACE,
            s.home.columns, SMARTSPACE_DEFAULT_SPAN_Y, s.home.columns, s.home.rows, _screenType.value
        )
    }

    /** Adds the built-in notifications widget at the first free full-width rectangle (widget picker).
     *  Full width like the smartspace clock: the icon group centers inside its own footprint, so any
     *  narrower default could never sit symmetric on an odd column count (e.g. 4 wide in 5 columns). */
    fun addNotificationsWidget() = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addBuiltin(
            HomeItemEntity.BUILTIN_NOTIFICATIONS,
            s.home.columns, NOTIFICATIONS_DEFAULT_SPAN_Y, s.home.columns, s.home.rows, _screenType.value
        )
    }

    /** Adds the Category Card Interface briefing card notification widget at the first free full-width rectangle. */
    fun addSamsungNotificationWidget() = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addBuiltin(
            HomeItemEntity.BUILTIN_NOTIFICATION_WIDGET,
            s.home.columns, 2, s.home.columns, s.home.rows, _screenType.value
        )
    }

    /** Adds the built-in battery widget (an app-icon-sized ring) at the first free cell (widget picker). */
    fun addBatteryWidget() = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addBuiltin(
            HomeItemEntity.BUILTIN_BATTERY,
            BATTERY_SPAN, BATTERY_SPAN, s.home.columns, s.home.rows, _screenType.value
        )
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

    fun removeFromHome(appItem: AppItem) =
        viewModelScope.launch { homeLayoutRepository.removeFromHome(appItem, _screenType.value) }

    fun addToDock(appItem: AppItem) =
        viewModelScope.launch { settingsRepository.addToDock(appItem.key, _screenType.value) }

    fun removeFromDock(appItem: AppItem) =
        viewModelScope.launch { settingsRepository.removeFromDock(appItem.key, _screenType.value) }

    fun addToHome(appItem: AppItem) = viewModelScope.launch {
        val s = settingsRepository.settings(_screenType.value).first()
        homeLayoutRepository.addToHome(appItem, s.home.columns, s.home.rows, _screenType.value)
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
                    val removed = runCatching { removeEmptyPage(emptied) }.getOrNull()
                    if (removed != null && removed != PAGE_FAILED) skipNext = true
                }
            }
        }
    }

    private companion object {
        /** Rasterization size (px) of an icon in the picker grid. */
        const val ICON_PICKER_PX = 112

        /** How many decoded picker icons are kept (a few screens of the grid). */
        const val ICON_PICKER_CACHE_SIZE = 240
    }
}
