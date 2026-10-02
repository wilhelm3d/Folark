package org.arkikeskus.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import javax.inject.Singleton

/** A person the user pinned to the people widget: a quiet tile that stays put between messages.
 *  [key] is [PeopleGrouping.personKey] of [name]; the contact fields are what a lookup found when
 *  the pin was made (blank when the person isn't in the contacts, or contacts aren't allowed). */
data class PinnedPerson(
    val key: String,
    val name: String,
    val lookupUri: String = "",
    val number: String = "",
    val photoUri: String = "",
)

/** A folder shown in the app drawer: a stable [id], a [name], and the keys of its member apps. */
data class DrawerFolder(val id: Long, val name: String, val appKeys: List<String>)

/**
 * Reads/writes launcher preferences. The [DataStore] is injected (provided from a Context-backed
 * `preferencesDataStore` in DataModule) so the repository's merge logic can be unit-tested on the
 * JVM with a temp-file store.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<LauncherSettings> get() = settings(ScreenType.OUTER)

    fun settings(screenType: ScreenType = ScreenType.OUTER): Flow<LauncherSettings> = dataStore.data.onStart {
        // 0.7 imports unknown numeric backup keys as Long. Repair them before the first typed
        // read when a newer backup was restored in 0.7 and the installation then upgraded.
        dataStore.edit { p ->
            for (key in listOf(Keys.homePage(ScreenType.OUTER), Keys.homePageCount(ScreenType.OUTER), Keys.homePage(ScreenType.INNER), Keys.homePageCount(ScreenType.INNER))) {
                val raw = p.asMap()[key] ?: continue
                if (raw !is Int) {
                    p[key] = (raw as? Number)?.toLong()
                        ?.coerceIn(0L, HomeLayoutRepository.MAX_PAGES.toLong())?.toInt() ?: 0
                }
            }
        }
    }.map { p ->
        // Clamp the numeric values on read as well as on write: a stale/garbage value left in the
        // store during development (e.g. homeColumns = 0) must never reach the layout math, where a
        // zero column count would spin firstFreeCell() in an infinite loop.
        LauncherSettings(
            dockEnabled = p[Keys.dockEnabled(screenType)] ?: true,
            dockColumns = (p[Keys.dockColumns(screenType)] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            homeColumns = (p[Keys.homeColumns(screenType)] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            homeRows = (p[Keys.homeRows(screenType)] ?: HomeLayoutRepository.ROWS).coerceIn(MIN_ROWS, MAX_ROWS),
            drawerColumns = (p[Keys.drawerColumns(screenType)] ?: if (screenType == ScreenType.INNER) 6 else 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            drawerRows = (p[Keys.drawerRows(screenType)] ?: 6).coerceIn(MIN_DRAWER_ROWS, MAX_DRAWER_ROWS),
            showDrawerSearch = p[Keys.showDrawerSearch(screenType)] ?: p[Keys.SHOW_DRAWER_SEARCH] ?: true,
            showDockLabels = p[Keys.showDockLabels(screenType)] ?: p[Keys.SHOW_DOCK_LABELS] ?: false,
            showHomeLabels = p[Keys.showHomeLabels(screenType)] ?: p[Keys.SHOW_HOME_LABELS] ?: true,
            showDrawerLabels = p[Keys.showDrawerLabels(screenType)] ?: p[Keys.SHOW_DRAWER_LABELS] ?: true,
            showFrequentApps = p[Keys.showFrequentApps(screenType)] ?: p[Keys.SHOW_FREQUENT_APPS] ?: false,
            drawerSearchPosition = (p[Keys.drawerSearchPosition(screenType)] ?: LauncherSettings.SEARCH_TOP)
                .let { if (it == LauncherSettings.SEARCH_BOTTOM) it else LauncherSettings.SEARCH_TOP },
            drawerIndexBarEnabled = p[Keys.drawerIndexBarEnabled(screenType)] ?: true,
            azIndexPosition = (p[Keys.azIndexPosition(screenType)] ?: LauncherSettings.AZ_POSITION_VERTICAL_RIGHT).let {
                if (it == LauncherSettings.AZ_POSITION_VERTICAL_LEFT ||
                    it == LauncherSettings.AZ_POSITION_HORIZONTAL_TOP ||
                    it == LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM) it
                else LauncherSettings.AZ_POSITION_VERTICAL_RIGHT
            },
            drawerBackgroundBlur = (p[Keys.drawerBackgroundBlur(screenType)] ?: 0f).coerceIn(0f, 25f),
            drawerScrimOpacity = (p[Keys.drawerScrimOpacity(screenType)] ?: 0.5f).coerceIn(0f, 1f),
            glassBlurRadius = (p[Keys.glassBlurRadius(screenType)] ?: 25.0f).coerceIn(0f, 50f),
            glassDarkTint = (p[Keys.glassDarkTint(screenType)] ?: 0.45f).coerceIn(0f, 0.9f),
            drawerStyle = (p[Keys.drawerStyle(screenType)] ?: LauncherSettings.DRAWER_STYLE_STANDARD_GRID)
                .let {
                    if (it == LauncherSettings.DRAWER_STYLE_HORIZONTAL ||
                        it == LauncherSettings.DRAWER_STYLE_ONE_UI ||
                        it == LauncherSettings.DRAWER_STYLE_NOTHING_OS ||
                        it == LauncherSettings.DRAWER_STYLE_VERTICAL ||
                        it == LauncherSettings.DRAWER_STYLE_MOTO ||
                        it == LauncherSettings.DRAWER_STYLE_STANDARD_GRID) it
                    else LauncherSettings.DRAWER_STYLE_STANDARD_GRID
                },
            drawerThemeTint = (p[Keys.drawerThemeTint(screenType)] ?: LauncherSettings.DRAWER_THEME_DEFAULT_DARK)
                .let {
                    if (it == LauncherSettings.DRAWER_THEME_OBSIDIAN_BLACK ||
                        it == LauncherSettings.DRAWER_THEME_ACCENT_TINT ||
                        it == LauncherSettings.DRAWER_THEME_MONOCHROME ||
                        it == LauncherSettings.DRAWER_THEME_DEFAULT_DARK) it
                    else LauncherSettings.DRAWER_THEME_DEFAULT_DARK
                },
            drawerAccentColor = p[Keys.drawerAccentColor(screenType)] ?: 0xFF00B0FF.toInt(),
            drawerHeightFraction = (p[Keys.drawerHeightFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            drawerWidthFraction = (p[Keys.drawerWidthFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            drawerAlignment = (p[Keys.drawerAlignment(screenType)] ?: LauncherSettings.DRAWER_ALIGNMENT_CENTER).let {
                if (it == LauncherSettings.DRAWER_ALIGNMENT_LEFT || it == LauncherSettings.DRAWER_ALIGNMENT_RIGHT) it else LauncherSettings.DRAWER_ALIGNMENT_CENTER
            },
            parallaxWallpaper = p[Keys.parallaxWallpaper(screenType)] ?: p[Keys.PARALLAX_WALLPAPER] ?: false,
            drawerLayoutMode = (p[Keys.drawerLayoutMode(screenType)] ?: LauncherSettings.DRAWER_LAYOUT_GRID).let {
                if (it == LauncherSettings.DRAWER_LAYOUT_LIST) it else LauncherSettings.DRAWER_LAYOUT_GRID
            },
            dockBackgroundOpacity = (p[Keys.dockBackgroundOpacity(screenType)] ?: p[Keys.DOCK_OPACITY] ?: 0.35f).coerceIn(0f, 1f),
            showPageIndicator = p[Keys.showPageIndicator(screenType)] ?: p[Keys.SHOW_PAGE_INDICATOR] ?: true,
            showNotificationDots = p[Keys.SHOW_NOTIF_DOTS] ?: true,
            notificationDotCount = p[Keys.NOTIF_DOT_COUNT] ?: true,
            notificationDotScale = (p[Keys.NOTIF_DOT_SCALE] ?: 1.0f).coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE),
            useThemedIcons = p[Keys.USE_THEMED_ICONS] ?: false,
            iconPackPackage = p[Keys.ICON_PACK] ?: "",
            searchContacts = p[Keys.SEARCH_CONTACTS] ?: false,
            leftSwipeAppKey = p[Keys.leftSwipeAppKey(screenType)] ?: p[Keys.LEFT_SWIPE_APP_KEY] ?: "",
            rightSwipeAppKey = p[Keys.rightSwipeAppKey(screenType)] ?: "",
            desktopLocked = p[Keys.DESKTOP_LOCKED] ?: false,
            drawerOpensAtTop = p[Keys.drawerOpensAtTop(screenType)] ?: p[Keys.DRAWER_OPENS_AT_TOP] ?: true,
            appLabelTextScale = (p[Keys.appLabelTextScale(screenType)] ?: p[Keys.APP_LABEL_SCALE] ?: 1.0f).coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE),
            twoLineHomeLabels = p[Keys.twoLineHomeLabels(screenType)] ?: p[Keys.TWO_LINE_HOME_LABELS] ?: false,
            twoLineDrawerLabels = p[Keys.twoLineDrawerLabels(screenType)] ?: p[Keys.TWO_LINE_DRAWER_LABELS] ?: true,
            appLabelColor = p[Keys.appLabelColor(screenType)] ?: p[Keys.APP_LABEL_COLOR] ?: 0xFFFFFFFF.toInt(),
            showStatusBar = p[Keys.showStatusBar(screenType)] ?: p[Keys.SHOW_STATUS_BAR] ?: false,
            showWeather = p[Keys.SHOW_WEATHER] ?: true,
            hideSystemStatusBar = p[Keys.hideSystemStatusBar(screenType)] ?: p[Keys.HIDE_SYSTEM_STATUS_BAR] ?: false,
            statusBarScrimOpacity = (p[Keys.statusBarScrimOpacity(screenType)] ?: p[Keys.STATUS_BAR_SCRIM] ?: 0.6f).coerceIn(0f, 1f),
            widgetTonalBackground = p[Keys.WIDGET_TONAL_BACKGROUND] ?: false,
            notificationWidgetCountStyle = (p[Keys.NOTIF_WIDGET_COUNT_STYLE] ?: LauncherSettings.COUNT_NUMBER)
                .let { if (it == LauncherSettings.COUNT_DOT || it == LauncherSettings.COUNT_NONE) it else LauncherSettings.COUNT_NUMBER },
            doubleTapToLock = p[Keys.doubleTapToLock(screenType)] ?: p[Keys.DOUBLE_TAP_LOCK] ?: false,
            peoplePrivacy = (p[Keys.PEOPLE_PRIVACY] ?: LauncherSettings.PRIVACY_ALL).let {
                if (it == LauncherSettings.PRIVACY_SENDER || it == LauncherSettings.PRIVACY_COUNT) it
                else LauncherSettings.PRIVACY_ALL
            },
            peopleBatchEnabled = p[Keys.PEOPLE_BATCH_ENABLED] ?: false,
            peopleShowApps = p[Keys.PEOPLE_SHOW_APPS] ?: true,
            homePage = (p[Keys.homePage(screenType)] ?: 0).coerceIn(0, HomeLayoutRepository.MAX_PAGES),
            homePageCount = (p[Keys.homePageCount(screenType)] ?: 0).coerceIn(0, HomeLayoutRepository.MAX_PAGES),
            peopleBatchTimes = (p[Keys.PEOPLE_BATCH_TIMES] ?: LauncherSettings.DEFAULT_BATCH_TIMES)
                .let { BatchSchedule.parse(it) }
                .let { if (it.isEmpty()) LauncherSettings.DEFAULT_BATCH_TIMES else BatchSchedule.format(it) },
            workspaceDensity = (p[Keys.workspaceDensity(screenType)] ?: LauncherSettings.DENSITY_NORMAL).let {
                if (it == LauncherSettings.DENSITY_COMPACT || it == LauncherSettings.DENSITY_SPACIOUS) it else LauncherSettings.DENSITY_NORMAL
            },
            allowLandscape = p[Keys.allowLandscape(screenType)] ?: false,
            swipeDownAction = (p[Keys.swipeDownAction(screenType)] ?: LauncherSettings.GESTURE_NOTIFICATIONS).let {
                if (it == LauncherSettings.GESTURE_DRAWER || it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_LOCK || it == LauncherSettings.GESTURE_NONE) it else LauncherSettings.GESTURE_NOTIFICATIONS
            },
            doubleTapAction = (p[Keys.doubleTapAction(screenType)] ?: LauncherSettings.GESTURE_NONE).let {
                if (it == LauncherSettings.GESTURE_NOTIFICATIONS || it == LauncherSettings.GESTURE_DRAWER || it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_LOCK) it else LauncherSettings.GESTURE_NONE
            },
            swipeUpAction = (p[Keys.swipeUpAction(screenType)] ?: LauncherSettings.GESTURE_DRAWER).let {
                if (it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_NONE) it else LauncherSettings.GESTURE_DRAWER
            },
            halfOpenedModeEnabled = p[Keys.HALF_OPENED_ENABLED] ?: false,
            amoledDark = p[Keys.amoledDark(screenType)] ?: false,
            foldAction = (p[Keys.foldAction(screenType)] ?: LauncherSettings.ACTION_STAY).let {
                if (it == LauncherSettings.ACTION_PAGE_0 || it == LauncherSettings.ACTION_LOCK) it else LauncherSettings.ACTION_STAY
            },
            unfoldAction = (p[Keys.unfoldAction(screenType)] ?: LauncherSettings.ACTION_STAY).let {
                if (it == LauncherSettings.ACTION_SEARCH || it == LauncherSettings.ACTION_DRAWER) it else LauncherSettings.ACTION_STAY
            },
            innerTaskbarEnabled = p[Keys.innerTaskbarEnabled(screenType)] ?: true,
            outerWallpaperScale = (p[Keys.OUTER_WALLPAPER_SCALE] ?: 1.0f).coerceIn(1.0f, 2.5f),
            innerWallpaperScale = (p[Keys.INNER_WALLPAPER_SCALE] ?: 1.0f).coerceIn(1.0f, 2.5f),
            foldTransitionStyle = (p[Keys.foldTransitionStyle(screenType)] ?: p[Keys.FOLD_TRANSITION_STYLE] ?: LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP).let {
                if (it == LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP ||
                    it == LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE ||
                    it == LauncherSettings.TRANSITION_CROSSFADE ||
                    it == LauncherSettings.TRANSITION_SCALE ||
                    it == LauncherSettings.TRANSITION_SLIDE ||
                    it == LauncherSettings.TRANSITION_MORPH_SCALE_FADE) it else LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP
            },
            aquamorphicTouchEnabled = p[Keys.aquamorphicTouchEnabled(screenType)] ?: true,
            appLaunchZoomEnabled = p[Keys.appLaunchZoomEnabled(screenType)] ?: true,
            pageBounceEnabled = p[Keys.pageBounceEnabled(screenType)] ?: true,
            dpiWorkspace = (p[Keys.dpiWorkspace(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
            dpiAppDrawer = (p[Keys.dpiAppDrawer(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
            dpiSettings = (p[Keys.dpiSettings(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
            innerDualPageWorkspace = p[Keys.innerDualPageWorkspace(screenType)] ?: false,
            innerSidebarAppDrawer = p[Keys.innerSidebarAppDrawer(screenType)] ?: false,
            innerDockAlignment = (p[Keys.innerDockAlignment(screenType)] ?: LauncherSettings.DOCK_ALIGNMENT_CENTER).let {
                if (it == LauncherSettings.DOCK_ALIGNMENT_LEFT || it == LauncherSettings.DOCK_ALIGNMENT_RIGHT || it == LauncherSettings.DOCK_ALIGNMENT_FLOATING) it else LauncherSettings.DOCK_ALIGNMENT_CENTER
            },
        )
    }

    val appPairs: Flow<List<AppPair>> = dataStore.data.map { p -> parseAppPairs(p[Keys.APP_PAIRS]) }

    suspend fun saveAppPair(pair: AppPair) = edit { p ->
        val current = parseAppPairs(p[Keys.APP_PAIRS]).toMutableList()
        val index = current.indexOfFirst { it.id == pair.id }
        if (index >= 0) current[index] = pair else current.add(pair)
        p[Keys.APP_PAIRS] = serializeAppPairs(current)
    }

    suspend fun deleteAppPair(id: String) = edit { p ->
        val current = parseAppPairs(p[Keys.APP_PAIRS])
        p[Keys.APP_PAIRS] = serializeAppPairs(current.filterNot { it.id == id })
    }

    private fun parseAppPairs(raw: String?): List<AppPair> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 10) return@mapNotNull null
            AppPair(
                id = parts[0],
                name = parts[1],
                app1Key = parts[2],
                app1Package = parts[3],
                app1ClassName = parts[4],
                app1UserSerial = parts[5].toLongOrNull() ?: 0L,
                app2Key = parts[6],
                app2Package = parts[7],
                app2ClassName = parts[8],
                app2UserSerial = parts[9].toLongOrNull() ?: 0L,
            )
        } ?: emptyList()

    private fun serializeAppPairs(pairs: List<AppPair>): String =
        pairs.joinToString("\n") { p ->
            listOf(
                p.id,
                p.name.replace(SEPARATORS, " "),
                p.app1Key,
                p.app1Package,
                p.app1ClassName,
                p.app1UserSerial.toString(),
                p.app2Key,
                p.app2Package,
                p.app2ClassName,
                p.app2UserSerial.toString(),
            ).joinToString("\t")
        }

    suspend fun setFoldAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.foldAction(screenType)] = value }
    suspend fun setUnfoldAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.unfoldAction(screenType)] = value }
    suspend fun setInnerTaskbarEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerTaskbarEnabled(screenType)] = value }
    suspend fun setOuterWallpaperScale(value: Float) = edit { it[Keys.OUTER_WALLPAPER_SCALE] = value.coerceIn(1.0f, 2.5f) }
    suspend fun setInnerWallpaperScale(value: Float) = edit { it[Keys.INNER_WALLPAPER_SCALE] = value.coerceIn(1.0f, 2.5f) }
    suspend fun setFoldTransitionStyle(value: String, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.foldTransitionStyle(screenType)] = value
        if (screenType == ScreenType.OUTER) {
            it[Keys.FOLD_TRANSITION_STYLE] = value
        }
    }
    suspend fun setAquamorphicTouchEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.aquamorphicTouchEnabled(screenType)] = value }
    suspend fun setAppLaunchZoomEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.appLaunchZoomEnabled(screenType)] = value }
    suspend fun setPageBounceEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.pageBounceEnabled(screenType)] = value }
    suspend fun setDpiWorkspace(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiWorkspace(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
    suspend fun setDpiAppDrawer(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiAppDrawer(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
    suspend fun setDpiSettings(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dpiSettings(screenType)] = value.coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE) }
    suspend fun setInnerDualPageWorkspace(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerDualPageWorkspace(screenType)] = value }
    suspend fun setInnerSidebarAppDrawer(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerSidebarAppDrawer(screenType)] = value }
    suspend fun setInnerDockAlignment(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.innerDockAlignment(screenType)] = value }

    suspend fun setPeopleBatchEnabled(value: Boolean) = edit { it[Keys.PEOPLE_BATCH_ENABLED] = value }
    /** The page HOME returns to and the explicit page count, in one settings snapshot (the page
     *  menu's operations own both; see WorkspacePageOperations). */
    suspend fun setHomePages(home: Int, count: Int, screenType: ScreenType) = edit {
        it[Keys.homePage(screenType)] = home.coerceIn(0, HomeLayoutRepository.MAX_PAGES - 1)
        it[Keys.homePageCount(screenType)] = count.coerceIn(0, HomeLayoutRepository.MAX_PAGES)
    }

    suspend fun setPeopleShowApps(value: Boolean) = edit { it[Keys.PEOPLE_SHOW_APPS] = value }

    /** Batch delivery times; normalized through [BatchSchedule], falling back to the default when
     *  nothing parses so the batch can never silently hold messages forever. */
    suspend fun setPeopleBatchTimes(raw: String) = edit { p ->
        val parsed = BatchSchedule.parse(raw)
        p[Keys.PEOPLE_BATCH_TIMES] = if (parsed.isEmpty()) LauncherSettings.DEFAULT_BATCH_TIMES else BatchSchedule.format(parsed)
    }

    // --- People widget: local batch deadlines (also reads legacy Android snoozes) -------------
    // "key\tdeliverAtEpochMs" per line. Device-local: a notification key means nothing elsewhere.

    /** Notification key → local delivery time. Notification text is never persisted. */
    val heldNotifications: Flow<Map<String, Long>> = dataStore.data.map { p ->
        p[Keys.PEOPLE_HELD]?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            val until = if (i > 0) line.substring(i + 1).toLongOrNull() else null
            if (until == null) null else line.substring(0, i) to until
        }?.toMap() ?: emptyMap()
    }

    suspend fun setHeldNotifications(held: Map<String, Long>) = edit { p ->
        p[Keys.PEOPLE_HELD] = held.entries.joinToString("\n") { (k, v) -> k.replace(SEPARATORS, " ") + "\t" + v }
    }

    // --- People widget aliases ("this notification name is the same person") --------------------
    // One link per line: "aliasKey\ttargetKey" (both PeopleGrouping.personKey values).

    /** alias key → target key. Single-hop: a target is never itself an alias (see [linkPerson]). */
    val peopleAliases: Flow<Map<String, String>> = dataStore.data.map { p -> parseAliases(p[Keys.PEOPLE_ALIASES]) }

    /** Links [alias] to [target]: an existing link on [alias] is replaced, links that pointed at
     *  [alias] follow it to the new target, and a target that is itself an alias resolves first. */
    suspend fun linkPerson(alias: String, target: String) = edit { p ->
        val current = parseAliases(p[Keys.PEOPLE_ALIASES])
        val resolved = current[target] ?: target
        if (resolved == alias) return@edit
        val next = current
            .mapValues { (_, t) -> if (t == alias) resolved else t }
            .filterKeys { it != alias && it != resolved } + (alias to resolved)
        p[Keys.PEOPLE_ALIASES] = serializeAliases(next.filter { (a, t) -> a != t })
    }

    /** Removes every link that merges into [target]. */
    suspend fun unlinkPerson(target: String) = edit { p ->
        p[Keys.PEOPLE_ALIASES] = serializeAliases(parseAliases(p[Keys.PEOPLE_ALIASES]).filterValues { it != target })
    }

    private fun parseAliases(raw: String?): Map<String, String> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0 || i == line.length - 1) null else line.substring(0, i) to line.substring(i + 1)
        }?.toMap() ?: emptyMap()

    private fun serializeAliases(aliases: Map<String, String>): String =
        aliases.entries.joinToString("\n") { (a, t) -> a.replace(SEPARATORS, " ") + "\t" + t.replace(SEPARATORS, " ") }

    /** What the people widget shows of a notification (see LauncherSettings.PRIVACY_*). */
    suspend fun setPeoplePrivacy(value: String) = edit { it[Keys.PEOPLE_PRIVACY] = value }

    // --- People widget pins ---------------------------------------------------------------------
    // One person per line, tab-separated: "key\tname\tlookupUri\tnumber\tphotoUri". Names are
    // normalized at the write point like folder names, so a plain split round-trips.

    /** People pinned to the people widget, in pin order. */
    val pinnedPeople: Flow<List<PinnedPerson>> = dataStore.data.map { p -> parsePinned(p[Keys.PINNED_PEOPLE]) }

    /** Pins [person] (replacing an earlier pin with the same key, keeping its position). */
    suspend fun pinPerson(person: PinnedPerson) = edit { p ->
        val current = parsePinned(p[Keys.PINNED_PEOPLE])
        val next = if (current.any { it.key == person.key }) current.map { if (it.key == person.key) person else it }
            else current + person
        p[Keys.PINNED_PEOPLE] = serializePinned(next)
    }

    suspend fun unpinPerson(key: String) = edit { p ->
        p[Keys.PINNED_PEOPLE] = serializePinned(parsePinned(p[Keys.PINNED_PEOPLE]).filterNot { it.key == key })
    }

    private fun parsePinned(raw: String?): List<PinnedPerson> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            val key = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PinnedPerson(
                key = key,
                name = parts.getOrNull(1).orEmpty().ifBlank { key },
                lookupUri = parts.getOrNull(2).orEmpty(),
                number = parts.getOrNull(3).orEmpty(),
                photoUri = parts.getOrNull(4).orEmpty(),
            )
        } ?: emptyList()

    private fun serializePinned(people: List<PinnedPerson>): String =
        people.joinToString("\n") { person ->
            listOf(person.key, person.name, person.lookupUri, person.number, person.photoUri)
                .joinToString("\t") { it.replace(SEPARATORS, " ") }
        }

    /** Ordered list of dock favorite app keys (see AppItem.key). */
    val dockFavorites: Flow<List<String>> get() = dockFavorites(ScreenType.OUTER)

    /** Ordered list of dock favorite app keys (see AppItem.key). */
    fun dockFavorites(screenType: ScreenType = ScreenType.OUTER): Flow<List<String>> = dataStore.data.map { p ->
        p[Keys.dockFavorites(screenType)]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** App keys hidden from the app drawer (see AppItem.key). */
    val hiddenApps: Flow<Set<String>> = dataStore.data.map { p ->
        p[Keys.HIDDEN_APPS]?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) = edit { p ->
        val current = (p[Keys.HIDDEN_APPS]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
        if (hidden) current.add(key) else current.remove(key)
        p[Keys.HIDDEN_APPS] = current.joinToString("\n")
    }

    /** User-chosen custom app labels (AppItem.key / componentName -> label), applied over the system label. */
    val customLabels: Flow<Map<String, String>> = dataStore.data.map { p -> parseLabels(p[Keys.CUSTOM_LABELS]) }

    /** Sets (or, for a blank [label], clears) the custom label for an app by its [componentName] or key. */
    suspend fun setCustomAppLabel(componentName: String, label: String) = edit { p ->
        val current = parseLabels(p[Keys.CUSTOM_LABELS]).toMutableMap()
        val trimmed = label.trim()
        if (trimmed.isEmpty()) current.remove(componentName) else current[componentName] = trimmed
        p[Keys.CUSTOM_LABELS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    /** Sets (or, for a blank [label], clears) the custom label for [key]. */
    suspend fun setCustomLabel(key: String, label: String?) = setCustomAppLabel(key, label.orEmpty())

    private fun parseLabels(raw: String?): Map<String, String> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }?.toMap() ?: emptyMap()

    // --- App-drawer folders ----------------------------------------------------------------------
    // Serialized one folder per line, tab-separated fields: "id\tname\tkey1\tkey2...". App keys
    // never contain a tab; folder names are normalized in serializeFolders, so a plain split
    // round-trips cleanly.

    val drawerFolders: Flow<List<DrawerFolder>> = dataStore.data.map { p -> parseFolders(p[Keys.DRAWER_FOLDERS]) }

    /** Creates an empty drawer folder, returns its new id. */
    suspend fun createDrawerFolder(name: String): Long {
        var newId = 1L
        edit { p ->
            val folders = parseFolders(p[Keys.DRAWER_FOLDERS])
            newId = (folders.maxOfOrNull { it.id } ?: 0L) + 1L
            p[Keys.DRAWER_FOLDERS] = serializeFolders(folders + DrawerFolder(newId, name, emptyList()))
        }
        return newId
    }

    suspend fun renameDrawerFolder(id: Long, name: String) = editFolders { folders ->
        folders.map { if (it.id == id) it.copy(name = name) else it }
    }

    suspend fun deleteDrawerFolder(id: Long) = editFolders { folders -> folders.filterNot { it.id == id } }

    suspend fun addAppsToDrawerFolder(id: Long, keys: List<String>) = editFolders { folders ->
        folders.map { f ->
            if (f.id == id) f.copy(appKeys = (f.appKeys + keys).distinct()) else f
        }
    }

    suspend fun removeAppFromDrawerFolder(id: Long, key: String) = editFolders { folders ->
        folders.map { f -> if (f.id == id) f.copy(appKeys = f.appKeys - key) else f }
    }

    private suspend fun editFolders(block: (List<DrawerFolder>) -> List<DrawerFolder>) = edit { p ->
        p[Keys.DRAWER_FOLDERS] = serializeFolders(block(parseFolders(p[Keys.DRAWER_FOLDERS])))
    }

    private fun parseFolders(raw: String?): List<DrawerFolder> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            DrawerFolder(id, parts.getOrNull(1).orEmpty(), parts.drop(2).filter { it.isNotEmpty() })
        } ?: emptyList()

    private fun serializeFolders(folders: List<DrawerFolder>): String =
        folders.joinToString("\n") { f ->
            // The record format is tab/newline-separated; a pasted separator in a folder name would
            // truncate the record and corrupt the member list — normalize at the one write point.
            val safeName = f.name.replace(SEPARATORS, " ")
            (listOf(f.id.toString(), safeName) + f.appKeys).joinToString("\t")
        }

    private val SEPARATORS = Regex("[\\t\\n\\r]+")

    suspend fun setDockEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockEnabled(screenType)] = value }
    suspend fun setDockColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
    suspend fun setHomeColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
    suspend fun setHomeRows(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeRows(screenType)] = value.coerceIn(MIN_ROWS, MAX_ROWS) }
    suspend fun setDrawerColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerColumns(screenType)] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
    suspend fun setDrawerRows(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerRows(screenType)] = value.coerceIn(MIN_DRAWER_ROWS, MAX_DRAWER_ROWS) }
    suspend fun setWorkspaceDensity(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.workspaceDensity(screenType)] = value }
    suspend fun setAllowLandscape(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.allowLandscape(screenType)] = value }
    suspend fun setSwipeDownAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeDownAction(screenType)] = value }
    suspend fun setDoubleTapAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.doubleTapAction(screenType)] = value }
    suspend fun setSwipeUpAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeUpAction(screenType)] = value }
    suspend fun setHalfOpenedModeEnabled(value: Boolean) = edit { it[Keys.HALF_OPENED_ENABLED] = value }
    suspend fun setAmoledDark(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.amoledDark(screenType)] = value }
    suspend fun setShowDrawerSearch(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDrawerSearch(screenType)] = value }
    suspend fun setSwipeUpForDrawer(value: Boolean) = edit { it[Keys.SWIPE_UP_DRAWER] = value }
    suspend fun setSwipeDownForNotifications(value: Boolean) = edit { it[Keys.SWIPE_DOWN_NOTIF] = value }
    suspend fun setShowDockLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDockLabels(screenType)] = value }
    suspend fun setShowHomeLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showHomeLabels(screenType)] = value }
    suspend fun setShowDrawerLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showDrawerLabels(screenType)] = value }
    suspend fun setShowFrequentApps(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showFrequentApps(screenType)] = value }
    suspend fun setDrawerSearchPosition(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerSearchPosition(screenType)] = value }
    suspend fun setDrawerIndexBarEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerIndexBarEnabled(screenType)] = value }
    suspend fun setAzIndexPosition(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.azIndexPosition(screenType)] = value }
    suspend fun setDrawerBackgroundBlur(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerBackgroundBlur(screenType)] = value.coerceIn(0f, 25f) }
    suspend fun setDrawerScrimOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerScrimOpacity(screenType)] = value.coerceIn(0f, 1f) }
    suspend fun setGlassBlurRadius(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassBlurRadius(screenType)] = value.coerceIn(0f, 50f) }
    suspend fun setGlassDarkTint(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassDarkTint(screenType)] = value.coerceIn(0f, 0.9f) }
    suspend fun flushDataStore() = edit { }
    suspend fun setDrawerStyle(value: String, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.drawerStyle(screenType)] = value
        val defaultAccent = when (value) {
            LauncherSettings.DRAWER_STYLE_ONE_UI -> 0xFF0A84FF.toInt()
            LauncherSettings.DRAWER_STYLE_MOTO -> 0xFF00BFA5.toInt()
            LauncherSettings.DRAWER_STYLE_NOTHING_OS -> 0xFFFF3B30.toInt()
            else -> 0xFF00B0FF.toInt()
        }
        it[Keys.drawerAccentColor(screenType)] = defaultAccent
    }
    suspend fun setDrawerThemeTint(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerThemeTint(screenType)] = value }
    suspend fun setDrawerAccentColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAccentColor(screenType)] = argb }
    suspend fun setDrawerHeightFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerHeightFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
    suspend fun setDrawerWidthFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerWidthFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
    suspend fun setDrawerAlignment(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAlignment(screenType)] = value }
    suspend fun setParallaxWallpaper(enabled: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.parallaxWallpaper(screenType)] = enabled }
    suspend fun setDrawerLayoutMode(mode: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLayoutMode(screenType)] = mode }
    suspend fun setDockBackgroundOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockBackgroundOpacity(screenType)] = value.coerceIn(0f, 1f) }
    suspend fun setShowPageIndicator(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showPageIndicator(screenType)] = value }
    suspend fun setShowNotificationDots(value: Boolean) = edit { it[Keys.SHOW_NOTIF_DOTS] = value }
    suspend fun setNotificationDotCount(value: Boolean) = edit { it[Keys.NOTIF_DOT_COUNT] = value }
    suspend fun setNotificationDotScale(value: Float) =
        edit { it[Keys.NOTIF_DOT_SCALE] = value.coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE) }
    suspend fun setUseThemedIcons(value: Boolean) = edit { it[Keys.USE_THEMED_ICONS] = value }

    /** Sets (or clears, for blank) the selected third-party icon pack package. */
    suspend fun setIconPackPackage(pkg: String) = edit { it[Keys.ICON_PACK] = pkg.trim() }

    /** Count indicator style of the built-in notifications widget (see LauncherSettings.COUNT_*). */
    suspend fun setWidgetTonalBackground(value: Boolean) = edit { it[Keys.WIDGET_TONAL_BACKGROUND] = value }
    suspend fun setNotificationWidgetCountStyle(style: String) = edit { it[Keys.NOTIF_WIDGET_COUNT_STYLE] = style }
    suspend fun setSearchContacts(value: Boolean) = edit { it[Keys.SEARCH_CONTACTS] = value }

    /** Sets (or, for a blank/null [key], clears) the app launched by the left-edge home swipe. */
    suspend fun setLeftSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.leftSwipeAppKey(screenType)] = key?.trim().orEmpty() }

    /** Sets (or, for a blank/null [key], clears) the app launched by the right-edge home swipe. */
    suspend fun setRightSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.rightSwipeAppKey(screenType)] = key?.trim().orEmpty() }

    /** Locks/unlocks the desktop layout (blocks moving/removing/adding home + dock items). */
    suspend fun setDesktopLocked(value: Boolean) = edit { it[Keys.DESKTOP_LOCKED] = value }

    /** Toggles double-tap to lock screen. */
    suspend fun setDoubleTapToLock(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.doubleTapToLock(screenType)] = value }

    /** Whether the app drawer reopens at the top vs. remembers its last scroll position. */
    suspend fun setDrawerOpensAtTop(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerOpensAtTop(screenType)] = value }

    /** Size multiplier for app icon labels (clamped to the slider's range). */
    suspend fun setAppLabelTextScale(value: Float, screenType: ScreenType = ScreenType.OUTER) =
        edit { it[Keys.appLabelTextScale(screenType)] = value.coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE) }

    /** ARGB color for the home-surface app icon labels. */
    suspend fun setAppLabelColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.appLabelColor(screenType)] = argb }

    /** Lets home-screen labels wrap onto a second line (apps, folders, shortcuts; not the dock). */
    suspend fun setTwoLineHomeLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.twoLineHomeLabels(screenType)] = value }

    /** Lets app-drawer labels wrap onto a second line. */
    suspend fun setTwoLineDrawerLabels(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.twoLineDrawerLabels(screenType)] = value }

    /** Shows/hides the home status bar (clock + battery + signal). */
    suspend fun setShowStatusBar(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showStatusBar(screenType)] = value }

    suspend fun setShowWeather(value: Boolean) = edit { it[Keys.SHOW_WEATHER] = value }

    /** Hides/shows the system status bar while the launcher is foreground (immersive home). */
    suspend fun setHideSystemStatusBar(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.hideSystemStatusBar(screenType)] = value }

    /** Darkness of the themed status bar's scrim (0..1). */
    suspend fun setStatusBarScrimOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) =
        edit { it[Keys.statusBarScrimOpacity(screenType)] = value.coerceIn(0f, 1f) }

    suspend fun addToDock(key: String, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        if (key !in current) current.add(key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun removeFromDock(key: String, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        current.remove(key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun addToDockAt(key: String, index: Int, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        current.remove(key)
        current.add(index.coerceIn(0, current.size), key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun reorderVisibleDock(visibleKeys: List<String>, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val normalizedVisible = visibleKeys.distinct()
        val hiddenTail = currentFavorites(p, screenType).filter { it !in normalizedVisible }
        p[Keys.dockFavorites(screenType)] = (normalizedVisible + hiddenTail).joinToString("\n")
    }

    private fun currentFavorites(p: MutablePreferences, screenType: ScreenType = ScreenType.OUTER): List<String> =
        p[Keys.dockFavorites(screenType)]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    // --- First-run default layout + onboarding (device-local) ---
    suspend fun defaultLayoutSeededOnce(): Boolean = dataStore.data.first()[Keys.DEFAULT_LAYOUT_SEEDED] ?: false
    suspend fun setDefaultLayoutSeeded() = edit { it[Keys.DEFAULT_LAYOUT_SEEDED] = true }
    suspend fun onboardingDoneOnce(): Boolean = dataStore.data.first()[Keys.ONBOARDING_DONE] ?: false
    suspend fun setOnboardingDone() = edit { it[Keys.ONBOARDING_DONE] = true }

    /** One-time first-run freshness decision, persisted BEFORE any seeding writes so a mid-seed
     *  death can't flip a fresh install into an "updating user". Null = not decided yet. */
    suspend fun firstRunFreshOnce(): Boolean? =
        when (dataStore.data.first()[Keys.FIRST_RUN_FRESH]) {
            "yes" -> true
            "no" -> false
            else -> null
        }
    suspend fun setFirstRunFresh(fresh: Boolean) = edit { it[Keys.FIRST_RUN_FRESH] = if (fresh) "yes" else "no" }

    /** Replaces the (empty) dock with the first-run default apps — seeding only, not a user API. */
    suspend fun seedDock(keys: List<String>, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockFavorites(screenType)] = keys.joinToString("\n") }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        fun dockEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "dock_enabled" else "dock_enabled_inner")
        fun dockColumns(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "dock_columns" else "dock_columns_inner")
        fun homeColumns(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "home_columns" else "home_columns_inner")
        fun homeRows(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "home_rows" else "home_rows_inner")
        fun drawerColumns(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_columns" else "drawer_columns_inner")
        fun drawerRows(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_rows" else "drawer_rows_inner")
        fun dockFavorites(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "dock_favorites" else "dock_favorites_inner")
        fun homePage(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "home_page" else "home_page_inner")
        fun homePageCount(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "home_page_count" else "home_page_count_inner")
        fun workspaceDensity(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "workspace_density" else "workspace_density_inner")
        fun amoledDark(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "amoled_dark" else "amoled_dark_inner")
        fun allowLandscape(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "allow_landscape" else "allow_landscape_inner")
        fun swipeDownAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "swipe_down_action" else "swipe_down_action_inner")
        fun doubleTapAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "double_tap_action" else "double_tap_action_inner")
        fun swipeUpAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "swipe_up_action" else "swipe_up_action_inner")
        fun foldAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "fold_action" else "fold_action_inner")
        fun unfoldAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "unfold_action" else "unfold_action_inner")
        fun innerTaskbarEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "inner_taskbar_enabled" else "inner_taskbar_enabled_inner")
        fun drawerSearchPosition(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_search_position" else "drawer_search_position_inner")
        fun drawerIndexBarEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_index_bar_enabled" else "drawer_index_bar_enabled_inner")
        fun azIndexPosition(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "az_index_position" else "az_index_position_inner")
        fun drawerBackgroundBlur(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_background_blur" else "drawer_background_blur_inner")
        fun drawerScrimOpacity(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_scrim_opacity" else "drawer_scrim_opacity_inner")
        fun glassBlurRadius(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "glass_blur_radius" else "glass_blur_radius_inner")
        fun glassDarkTint(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "glass_dark_tint" else "glass_dark_tint_inner")
        fun drawerStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_style" else "drawer_style_inner")
        fun drawerThemeTint(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_theme_tint" else "drawer_theme_tint_inner")
        fun drawerAccentColor(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_accent_color" else "drawer_accent_color_inner")
        fun drawerHeightFraction(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_height_fraction" else "drawer_height_fraction_inner")
        fun drawerWidthFraction(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_width_fraction" else "drawer_width_fraction_inner")
        fun drawerAlignment(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_alignment" else "drawer_alignment_inner")
        fun drawerLayoutMode(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_layout_mode" else "drawer_layout_mode_inner")
        fun showDrawerLabels(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_drawer_labels" else "show_drawer_labels_inner")
        fun showFrequentApps(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_frequent_apps" else "show_frequent_apps_inner")
        fun dpiWorkspace(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "dpi_workspace" else "dpi_workspace_inner")
        fun dpiAppDrawer(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "dpi_app_drawer" else "dpi_app_drawer_inner")
        fun dpiSettings(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "dpi_settings" else "dpi_settings_inner")
        fun innerDualPageWorkspace(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "inner_dual_page_workspace" else "inner_dual_page_workspace_inner")
        fun innerSidebarAppDrawer(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "inner_sidebar_app_drawer" else "inner_sidebar_app_drawer_inner")
        fun innerDockAlignment(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "inner_dock_alignment" else "inner_dock_alignment_inner")
        fun leftSwipeAppKey(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "left_swipe_app_key" else "left_swipe_app_key_inner")
        fun rightSwipeAppKey(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "right_swipe_app_key" else "right_swipe_app_key_inner")
        fun doubleTapToLock(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "double_tap_to_lock" else "double_tap_to_lock_inner")
        fun dockBackgroundOpacity(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "dock_opacity" else "dock_opacity_inner")
        fun showDockLabels(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_dock_labels" else "show_dock_labels_inner")
        fun showHomeLabels(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_home_labels" else "show_home_labels_inner")
        fun twoLineHomeLabels(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "two_line_home_labels" else "two_line_home_labels_inner")
        fun twoLineDrawerLabels(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "two_line_drawer_labels" else "two_line_drawer_labels_inner")
        fun appLabelTextScale(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "app_label_scale" else "app_label_scale_inner")
        fun appLabelColor(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "app_label_color" else "app_label_color_inner")
        fun parallaxWallpaper(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "parallax_wallpaper" else "parallax_wallpaper_inner")
        fun showDrawerSearch(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_drawer_search" else "show_drawer_search_inner")
        fun drawerOpensAtTop(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_opens_at_top" else "drawer_opens_at_top_inner")
        fun showStatusBar(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_status_bar" else "show_status_bar_inner")
        fun hideSystemStatusBar(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "hide_system_status_bar" else "hide_system_status_bar_inner")
        fun statusBarScrimOpacity(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "status_bar_scrim" else "status_bar_scrim_inner")
        fun showPageIndicator(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_page_indicator" else "show_page_indicator_inner")

        fun aquamorphicTouchEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "aquamorphic_touch_enabled" else "aquamorphic_touch_enabled_inner")
        fun foldTransitionStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "fold_transition_style_outer" else "fold_transition_style_inner")
        fun appLaunchZoomEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "app_launch_zoom_enabled" else "app_launch_zoom_enabled_inner")
        fun pageBounceEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "page_bounce_enabled" else "page_bounce_enabled_inner")

        val HALF_OPENED_ENABLED = booleanPreferencesKey("half_opened_mode_enabled")
        val OUTER_WALLPAPER_SCALE = floatPreferencesKey("outer_wallpaper_scale")
        val INNER_WALLPAPER_SCALE = floatPreferencesKey("inner_wallpaper_scale")
        val FOLD_TRANSITION_STYLE = stringPreferencesKey("fold_transition_style")
        val APP_PAIRS = stringPreferencesKey("app_pairs")

        val SHOW_DRAWER_SEARCH = booleanPreferencesKey("show_drawer_search")
        val SWIPE_UP_DRAWER = booleanPreferencesKey("swipe_up_drawer")
        val SWIPE_DOWN_NOTIF = booleanPreferencesKey("swipe_down_notif")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val CUSTOM_LABELS = stringPreferencesKey("custom_labels")
        val DRAWER_FOLDERS = stringPreferencesKey("drawer_folders")
        val SHOW_DOCK_LABELS = booleanPreferencesKey("show_dock_labels")
        val SHOW_HOME_LABELS = booleanPreferencesKey("show_home_labels")
        val SHOW_DRAWER_LABELS = booleanPreferencesKey("show_drawer_labels")
        val DOCK_OPACITY = floatPreferencesKey("dock_opacity")
        val SHOW_PAGE_INDICATOR = booleanPreferencesKey("show_page_indicator")
        val SHOW_NOTIF_DOTS = booleanPreferencesKey("show_notif_dots")
        val NOTIF_DOT_COUNT = booleanPreferencesKey("notif_dot_count")
        val NOTIF_DOT_SCALE = floatPreferencesKey("notif_dot_scale")
        val WIDGET_TONAL_BACKGROUND = booleanPreferencesKey("widget_tonal_background")
        val NOTIF_WIDGET_COUNT_STYLE = stringPreferencesKey("notif_widget_count_style")
        val USE_THEMED_ICONS = booleanPreferencesKey("use_themed_icons")
        val ICON_PACK = stringPreferencesKey("icon_pack_package")
        val SEARCH_CONTACTS = booleanPreferencesKey("search_contacts")
        val LEFT_SWIPE_APP_KEY = stringPreferencesKey("left_swipe_app_key")
        val DESKTOP_LOCKED = booleanPreferencesKey("desktop_locked")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val SHOW_FREQUENT_APPS = booleanPreferencesKey("show_frequent_apps")
        val DRAWER_OPENS_AT_TOP = booleanPreferencesKey("drawer_opens_at_top")
        val PARALLAX_WALLPAPER = booleanPreferencesKey("parallax_wallpaper")
        val APP_LABEL_SCALE = floatPreferencesKey("app_label_scale")
        val APP_LABEL_COLOR = intPreferencesKey("app_label_color")
        val TWO_LINE_HOME_LABELS = booleanPreferencesKey("two_line_home_labels")
        val TWO_LINE_DRAWER_LABELS = booleanPreferencesKey("two_line_drawer_labels")
        val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val HIDE_SYSTEM_STATUS_BAR = booleanPreferencesKey("hide_system_status_bar")
        val STATUS_BAR_SCRIM = floatPreferencesKey("status_bar_scrim")
        val DEFAULT_LAYOUT_SEEDED = booleanPreferencesKey("default_layout_seeded")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val FIRST_RUN_FRESH = stringPreferencesKey("first_run_fresh")
        val PEOPLE_PRIVACY = stringPreferencesKey("people_privacy")
        val PINNED_PEOPLE = stringPreferencesKey("pinned_people")
        val PEOPLE_ALIASES = stringPreferencesKey("people_aliases")
        val PEOPLE_BATCH_ENABLED = booleanPreferencesKey("people_batch_enabled")
        val PEOPLE_BATCH_TIMES = stringPreferencesKey("people_batch_times")
        val PEOPLE_SHOW_APPS = booleanPreferencesKey("people_show_apps")
        val PEOPLE_HELD = stringPreferencesKey("people_held_notifications")
    }

    companion object {
        /** Valid range for the home/dock/drawer column counts (mirrors the settings steppers). */
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 8

        /** Valid range for the home-grid row count (mirrors the settings stepper). */
        const val MIN_ROWS = 4
        const val MAX_ROWS = 8
        const val MIN_DRAWER_ROWS = 3
        const val MAX_DRAWER_ROWS = 8

        /** Valid range for the notification-dot scale slider. */
        const val MIN_DOT_SCALE = 0.6f
        const val MAX_DOT_SCALE = 1.8f

        /** Valid range for the app-label text-size slider. */
        const val MIN_LABEL_SCALE = 0.8f
        const val MAX_LABEL_SCALE = 1.6f

        /** Preference keys whose value must be restored as Float (JSON loses the Int/Float distinction). */
        val FLOAT_KEYS = setOf("dock_opacity", "notif_dot_scale", "app_label_scale", "status_bar_scrim", "outer_wallpaper_scale", "inner_wallpaper_scale", "drawer_background_blur", "drawer_scrim_opacity", "drawer_background_blur_inner", "drawer_scrim_opacity_inner", "glass_blur_radius", "glass_blur_radius_inner", "glass_dark_tint", "glass_dark_tint_inner", "dpi_workspace", "dpi_app_drawer", "dpi_settings", "dpi_workspace_inner", "dpi_app_drawer_inner", "dpi_settings_inner", "drawer_height_fraction", "drawer_width_fraction", "drawer_height_fraction_inner", "drawer_width_fraction_inner")
        val INT_KEYS = setOf(
            "dock_columns", "home_columns", "home_rows", "drawer_columns", "drawer_rows", "app_label_color",
            "home_page", "home_page_count",
            "dock_columns_inner", "home_columns_inner", "home_rows_inner", "drawer_columns_inner", "drawer_rows_inner",
            "home_page_inner", "home_page_count_inner"
        )

        /** Known boolean/string preference keys. importRaw writes a known key ONLY with its
         *  registered type — a wrong-typed value in an edited/corrupted backup would otherwise be
         *  stored under the same key name and crash every settings read with a ClassCastException. */
        val BOOLEAN_KEYS = setOf(
            "dock_enabled", "show_drawer_search", "swipe_up_drawer", "swipe_down_notif",
            "show_dock_labels", "show_home_labels", "show_drawer_labels", "show_page_indicator",
            "show_notif_dots", "notif_dot_count", "use_themed_icons", "search_contacts",
            "desktop_locked", "show_frequent_apps", "drawer_opens_at_top", "show_status_bar",
            "show_weather", "hide_system_status_bar", "double_tap_lock", "widget_tonal_background",
            "two_line_home_labels", "two_line_drawer_labels", "people_batch_enabled", "people_show_apps",
            "dock_enabled_inner", "amoled_dark", "amoled_dark_inner", "inner_taskbar_enabled", "inner_taskbar_enabled_inner",
            "aquamorphic_touch_enabled", "aquamorphic_touch_enabled_inner",
            "app_launch_zoom_enabled", "app_launch_zoom_enabled_inner",
            "page_bounce_enabled", "page_bounce_enabled_inner"
        )
        val STRING_KEYS = setOf(
            "dock_favorites", "hidden_apps", "custom_labels", "drawer_folders",
            "notif_widget_count_style", "icon_pack_package", "left_swipe_app_key",
            "people_privacy", "pinned_people", "people_aliases", "people_batch_times",
            "dock_favorites_inner", "app_pairs", "fold_action", "fold_action_inner", "unfold_action", "unfold_action_inner",
            "fold_transition_style", "fold_transition_style_outer", "fold_transition_style_inner",
            "drawer_alignment", "drawer_alignment_inner", "drawer_layout_mode", "drawer_layout_mode_inner", "az_index_position", "az_index_position_inner"
        )

        /** Device-local bookkeeping keys excluded from an exported backup and never imported.
         *  The `drive_*`/`update_*` names are ≤0.7.11 leftovers (the Drive backup and in-app
         *  updater were removed in 0.7.12): upgraded devices still carry them in the DataStore and
         *  old backup files still contain them, so the names must stay blocked here. */
        val DEVICE_LOCAL_KEYS = setOf(
            "drive_backup_enabled", "drive_last_backup_time", "drive_last_backup_hash",
            "drive_failure_count", "local_last_backup_time",
            "drive_interval_days", "drive_wifi_only", "drive_charging_only",
            "auto_update_enabled", "update_last_check", "update_last_notified_version",
            "default_layout_seeded", "onboarding_done", "first_run_fresh", "people_held_notifications",
        )
    }
}
