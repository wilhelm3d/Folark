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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.withLiquidGlass
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicReferenceArray
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

    /** The one-off store repair below has run in this process (it used to run on every collection). */
    @Volatile
    private var storeRepaired = false
    private val repairLock = Mutex()

    fun settings(screenType: ScreenType = ScreenType.OUTER): Flow<LauncherSettings> = dataStore.data.onStart {
        // 0.7 imports unknown numeric backup keys as Long. Repair them before the first typed
        // read when a newer backup was restored in 0.7 and the installation then upgraded.
        if (!storeRepaired) repairLock.withLock {
            if (!storeRepaired) {
                repairStore()
                storeRepaired = true
            }
        }
    }.map { p -> settingsFor(p, screenType) }
        // Unrelated writes to the store (app-usage counts, held notifications) must not make every
        // collector rebuild its state.
        .distinctUntilChanged()

    /** The settings last built per screen, with the store snapshot they came from. */
    private class SettingsMemo(val prefs: Preferences, val settings: LauncherSettings)
    private val settingsMemo = AtomicReferenceArray<SettingsMemo?>(ScreenType.entries.size)

    /**
     * Every collector of [settings] gets the same store snapshot after a write; the settings are
     * built from it once and shared (building reads a few hundred keys).
     */
    private fun settingsFor(p: Preferences, screenType: ScreenType): LauncherSettings {
        val hit = settingsMemo.get(screenType.ordinal)
        if (hit != null && hit.prefs === p) return hit.settings
        return readSettings(p, screenType).withLiquidGlass().also { settingsMemo.set(screenType.ordinal, SettingsMemo(p, it)) }
    }

    /** One stored value as a flow: emits only when that value changes, then parses it. */
    private fun <K, T> keyFlow(key: Preferences.Key<K>, parse: (K?) -> T): Flow<T> =
        dataStore.data.map { it[key] }.distinctUntilChanged().map { parse(it) }

    private suspend fun repairStore() {
        dataStore.edit { p ->
            for (key in listOf(Keys.homePage(ScreenType.OUTER), Keys.homePageCount(ScreenType.OUTER), Keys.homePage(ScreenType.INNER), Keys.homePageCount(ScreenType.INNER))) {
                val raw = p.asMap()[key] ?: continue
                if (raw !is Int) {
                    p[key] = (raw as? Number)?.toLong()
                        ?.coerceIn(0L, HomeLayoutRepository.MAX_PAGES.toLong())?.toInt() ?: 0
                }
            }
            splitProfilesOnce(p)
        }
    }

    private fun readSettings(p: Preferences, screenType: ScreenType): LauncherSettings = p.let {
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
            showDrawerSearch = p[Keys.showDrawerSearch(screenType)] ?: true,
            showDockLabels = p[Keys.showDockLabels(screenType)] ?: false,
            showHomeLabels = p[Keys.showHomeLabels(screenType)] ?: true,
            showDrawerLabels = p[Keys.showDrawerLabels(screenType)] ?: true,
            showFrequentApps = p[Keys.showFrequentApps(screenType)] ?: false,
            drawerSearchPosition = (p[Keys.drawerSearchPosition(screenType)] ?: LauncherSettings.SEARCH_TOP)
                .let { if (it == LauncherSettings.SEARCH_BOTTOM) it else LauncherSettings.SEARCH_TOP },
            drawerIndexBarEnabled = p[Keys.drawerIndexBarEnabled(screenType)] ?: true,
            azIndexScale = (p[Keys.azIndexScale(screenType)] ?: 1f).coerceIn(LauncherSettings.MIN_AZ_INDEX_SCALE, LauncherSettings.MAX_AZ_INDEX_SCALE),
            drawerHandleStyle = if (p[Keys.drawerHandleStyle(screenType)] == LauncherSettings.DRAWER_HANDLE_ARROW) {
                LauncherSettings.DRAWER_HANDLE_ARROW
            } else LauncherSettings.DRAWER_HANDLE_BAR,
            drawerBottomSpace = (p[Keys.drawerBottomSpace(screenType)] ?: 0).coerceIn(0, LauncherSettings.MAX_DRAWER_BOTTOM_SPACE),
            drawerLetterHeaders = p[Keys.drawerLetterHeaders(screenType)] ?: false,
            drawerLargeLetterPopup = p[Keys.drawerLargeLetterPopup(screenType)] ?: true,
            drawerSwipeTabs = p[Keys.drawerSwipeTabs(screenType)] ?: true,
            drawerFlickClose = p[Keys.drawerFlickClose(screenType)] ?: false,
            forceThemedIcons = p[Keys.FORCE_THEMED_ICONS] ?: false,
            themedIconStyle = (p[Keys.THEMED_ICON_STYLE] ?: LauncherSettings.THEMED_STYLE_CLASSIC)
                .let { if (it in LauncherSettings.THEMED_STYLES) it else LauncherSettings.THEMED_STYLE_CLASSIC },
            drawerCornerRadius = (p[Keys.drawerCornerRadius(screenType)] ?: LauncherSettings.DRAWER_CORNER_AUTO)
                .coerceIn(LauncherSettings.DRAWER_CORNER_AUTO, LauncherSettings.MAX_DRAWER_CORNER_RADIUS),
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
            drawerThemeTint = (p[Keys.drawerThemeTint(screenType)] ?: LauncherSettings.DRAWER_THEME_SYSTEM)
                .let { if (it in LauncherSettings.DRAWER_THEMES) it else LauncherSettings.DRAWER_THEME_SYSTEM },
            drawerAccentColor = p[Keys.drawerAccentColor(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_ACCENT,
            drawerCustomColor = p[Keys.drawerCustomColor(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_CUSTOM_COLOR,
            drawerGlassEnabled = p[Keys.drawerGlassEnabled(screenType)] ?: false,
            drawerPanelOpacity = (p[Keys.drawerPanelOpacity(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_PANEL_OPACITY)
                .coerceIn(LauncherSettings.MIN_DRAWER_PANEL_OPACITY, LauncherSettings.MAX_DRAWER_PANEL_OPACITY),
            drawerHeightFraction = (p[Keys.drawerHeightFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            drawerWidthFraction = (p[Keys.drawerWidthFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            drawerAlignment = (p[Keys.drawerAlignment(screenType)] ?: LauncherSettings.DRAWER_ALIGNMENT_CENTER).let {
                if (it == LauncherSettings.DRAWER_ALIGNMENT_LEFT || it == LauncherSettings.DRAWER_ALIGNMENT_RIGHT) it else LauncherSettings.DRAWER_ALIGNMENT_CENTER
            },
            parallaxWallpaper = p[Keys.parallaxWallpaper(screenType)] ?: false,
            drawerLayoutMode = (p[Keys.drawerLayoutMode(screenType)] ?: LauncherSettings.DRAWER_LAYOUT_GRID).let {
                if (it == LauncherSettings.DRAWER_LAYOUT_LIST) it else LauncherSettings.DRAWER_LAYOUT_GRID
            },
            dockBackgroundOpacity = (p[Keys.dockBackgroundOpacity(screenType)] ?: 0.35f).coerceIn(0f, 1f),
            showPageIndicator = p[Keys.showPageIndicator(screenType)] ?: true,
            showNotificationDots = p[Keys.showNotificationDots(screenType)] ?: true,
            notificationDotCount = p[Keys.notificationDotCount(screenType)] ?: true,
            notificationDotScale = (p[Keys.notificationDotScale(screenType)] ?: 1.0f).coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE),
            useThemedIcons = p[Keys.useThemedIcons(screenType)] ?: false,
            iconPackPackage = p[Keys.iconPackPackage(screenType)] ?: "",
            searchContacts = p[Keys.SEARCH_CONTACTS] ?: false,
            leftSwipeAppKey = p[Keys.leftSwipeAppKey(screenType)] ?: "",
            rightSwipeAppKey = p[Keys.rightSwipeAppKey(screenType)] ?: "",
            desktopLocked = p[Keys.DESKTOP_LOCKED] ?: false,
            drawerOpensAtTop = p[Keys.drawerOpensAtTop(screenType)] ?: true,
            appLabelTextScale = (p[Keys.appLabelTextScale(screenType)] ?: 1.0f).coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE),
            twoLineHomeLabels = p[Keys.twoLineHomeLabels(screenType)] ?: false,
            twoLineDrawerLabels = p[Keys.twoLineDrawerLabels(screenType)] ?: true,
            appLabelColor = p[Keys.appLabelColor(screenType)] ?: 0xFFFFFFFF.toInt(),
            showStatusBar = p[Keys.showStatusBar(screenType)] ?: false,
            showWeather = p[Keys.showWeather(screenType)] ?: true,
            hideSystemStatusBar = p[Keys.hideSystemStatusBar(screenType)] ?: false,
            statusBarScrimOpacity = (p[Keys.statusBarScrimOpacity(screenType)] ?: 0.6f).coerceIn(0f, 1f),
            widgetTonalBackground = p[Keys.widgetTonalBackground(screenType)] ?: false,
            notificationWidgetCountStyle = (p[Keys.notificationWidgetCountStyle(screenType)] ?: LauncherSettings.COUNT_NUMBER)
                .let { if (it == LauncherSettings.COUNT_DOT || it == LauncherSettings.COUNT_NONE) it else LauncherSettings.COUNT_NUMBER },
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
            allowLandscape = p[Keys.allowLandscape(screenType)] ?: false,
            swipeDownAction = (p[Keys.swipeDownAction(screenType)] ?: LauncherSettings.GESTURE_NOTIFICATIONS).let {
                if (it == LauncherSettings.GESTURE_DRAWER || it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_LOCK || it == LauncherSettings.GESTURE_RECENTS || it == LauncherSettings.GESTURE_NONE || it in LauncherSettings.MOTO_GESTURES) it else LauncherSettings.GESTURE_NOTIFICATIONS
            },
            doubleTapAction = (p[Keys.doubleTapAction(screenType)] ?: LauncherSettings.GESTURE_NONE).let {
                if (it == LauncherSettings.GESTURE_NOTIFICATIONS || it == LauncherSettings.GESTURE_DRAWER || it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_LOCK || it == LauncherSettings.GESTURE_RECENTS || it in LauncherSettings.MOTO_GESTURES) it else LauncherSettings.GESTURE_NONE
            },
            swipeUpAction = (p[Keys.swipeUpAction(screenType)] ?: LauncherSettings.GESTURE_DRAWER).let {
                if (it == LauncherSettings.GESTURE_SEARCH || it == LauncherSettings.GESTURE_NONE) it else LauncherSettings.GESTURE_DRAWER
            },
            halfOpenedModeEnabled = p[Keys.HALF_OPENED_ENABLED] ?: false,
            hingeTilt = p[Keys.HINGE_TILT] ?: true,
            splitLaunchEnabled = p[Keys.SPLIT_LAUNCH_ENABLED] ?: true,
            floatingLaunchEnabled = p[Keys.FLOATING_LAUNCH_ENABLED] ?: true,
            minusOneScreen = p[Keys.MINUS_ONE_SCREEN] ?: LauncherSettings.MINUS_ONE_NONE,
            amoledDark = p[Keys.amoledDark(screenType)] ?: false,
            foldAction = (p[Keys.foldAction(screenType)] ?: LauncherSettings.ACTION_STAY).let {
                if (it == LauncherSettings.ACTION_PAGE_0 || it == LauncherSettings.ACTION_LOCK) it else LauncherSettings.ACTION_STAY
            },
            unfoldAction = (p[Keys.unfoldAction(screenType)] ?: LauncherSettings.ACTION_STAY).let {
                if (it == LauncherSettings.ACTION_SEARCH || it == LauncherSettings.ACTION_DRAWER) it else LauncherSettings.ACTION_STAY
            },
            foldTransitionSpeed = (p[Keys.foldTransitionSpeed(screenType)] ?: 1f)
                .coerceIn(LauncherSettings.MIN_FOLD_TRANSITION_SPEED, LauncherSettings.MAX_FOLD_TRANSITION_SPEED),
            foldTransitionStyle = (p[Keys.foldTransitionStyle(screenType)] ?: LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP).let {
                if (it == LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP ||
                    it == LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE ||
                    it == LauncherSettings.TRANSITION_CROSSFADE ||
                    it == LauncherSettings.TRANSITION_SCALE ||
                    it == LauncherSettings.TRANSITION_SLIDE ||
                    it == LauncherSettings.TRANSITION_LIVE_HINGE ||
                    it == LauncherSettings.TRANSITION_MORPH_SCALE_FADE) it else LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP
            },
            aquamorphicTouchEnabled = p[Keys.aquamorphicTouchEnabled(screenType)] ?: true,
            appLaunchZoomEnabled = p[Keys.appLaunchZoomEnabled(screenType)] ?: true,
            pageBounceEnabled = p[Keys.pageBounceEnabled(screenType)] ?: true,
            iconSizeScale = (p[Keys.iconSizeScale(screenType)] ?: 1.0f)
                .coerceIn(LauncherSettings.MIN_ICON_SIZE_SCALE, LauncherSettings.MAX_ICON_SIZE_SCALE),
            folderShape = (p[Keys.folderShape(screenType)] ?: LauncherSettings.FOLDER_SHAPE_SQUIRCLE)
                .let { if (it in LauncherSettings.FOLDER_SHAPES) it else LauncherSettings.FOLDER_SHAPE_SQUIRCLE },
            folderTheme = (p[Keys.folderTheme(screenType)] ?: LauncherSettings.FOLDER_THEME_GLASS)
                .let { if (it in LauncherSettings.FOLDER_THEMES) it else LauncherSettings.FOLDER_THEME_GLASS },
            folderPreview = (p[Keys.folderPreview(screenType)] ?: LauncherSettings.FOLDER_PREVIEW_GRID_2)
                .let { if (it in LauncherSettings.FOLDER_PREVIEWS) it else LauncherSettings.FOLDER_PREVIEW_GRID_2 },
            folderOpenColumns = (p[Keys.folderOpenColumns(screenType)] ?: 4)
                .coerceIn(LauncherSettings.MIN_FOLDER_OPEN_COLUMNS, LauncherSettings.MAX_FOLDER_OPEN_COLUMNS),
            folderOpenSize = (p[Keys.folderOpenSize(screenType)] ?: LauncherSettings.FOLDER_OPEN_MEDIUM)
                .let { if (it in LauncherSettings.FOLDER_OPEN_SIZES) it else LauncherSettings.FOLDER_OPEN_MEDIUM },
            folderTapFirstApp = p[Keys.folderTapFirstApp(screenType)] ?: false,
            popupTheme = (p[Keys.popupTheme(screenType)] ?: LauncherSettings.POPUP_THEME_CLASSIC).let { if (it in LauncherSettings.POPUP_THEMES) it else LauncherSettings.POPUP_THEME_CLASSIC },
            stackAutoRotateSeconds = (p[Keys.stackAutoRotateSeconds(screenType)] ?: 0).coerceIn(0, LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS),
            stackCornerRadius = (p[Keys.stackCornerRadius(screenType)] ?: 24).coerceIn(0, LauncherSettings.MAX_STACK_CORNER_RADIUS),
            stackIndicator = (p[Keys.stackIndicator(screenType)] ?: LauncherSettings.STACK_INDICATOR_DOTS).let { if (it in LauncherSettings.STACK_INDICATORS) it else LauncherSettings.STACK_INDICATOR_DOTS },
            stackShowPill = p[Keys.stackShowPill(screenType)] ?: false,
            dotShape = (p[Keys.dotShape(screenType)] ?: LauncherSettings.DOT_SHAPE_CIRCLE).let { if (it in LauncherSettings.DOT_SHAPES) it else LauncherSettings.DOT_SHAPE_CIRCLE },
            dotColor = (p[Keys.dotColor(screenType)] ?: LauncherSettings.DOT_COLOR_RED).let { if (it in LauncherSettings.DOT_COLORS) it else LauncherSettings.DOT_COLOR_RED },
            dotPosition = (p[Keys.dotPosition(screenType)] ?: LauncherSettings.DOT_POSITION_TOP_END).let { if (it in LauncherSettings.DOT_POSITIONS) it else LauncherSettings.DOT_POSITION_TOP_END },
            homeWallpaperDim = (p[Keys.homeWallpaperDim(screenType)] ?: 0f).coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_DIM),
            homeWallpaperBlur = (p[Keys.homeWallpaperBlur(screenType)] ?: 0f).coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_BLUR),
            homeBackgroundImage = p[Keys.homeBackgroundImage(screenType)] ?: "",
            iconSwipeUpAction = (p[Keys.iconSwipeUpAction(screenType)] ?: LauncherSettings.ICON_SWIPE_NONE).let { if (it in LauncherSettings.ICON_SWIPES) it else LauncherSettings.ICON_SWIPE_NONE },
            autoRemoveEmptyPages = p[Keys.autoRemoveEmptyPages(screenType)] ?: false,
            newPageOnDrag = p[Keys.newPageOnDrag(screenType)] ?: false,
            drawerFolderTabs = p[Keys.drawerFolderTabs(screenType)] ?: false,
            floatingWindowSize = (p[Keys.FLOATING_WINDOW_SIZE] ?: LauncherSettings.FLOATING_SIZE_MEDIUM)
                .let { if (it in LauncherSettings.FLOATING_SIZES) it else LauncherSettings.FLOATING_SIZE_MEDIUM },
            mediaWidgetTheme = (p[Keys.MEDIA_WIDGET_THEME] ?: LauncherSettings.WIDGET_THEME_DEFAULT)
                .let { if (it in LauncherSettings.WIDGET_THEMES) it else LauncherSettings.WIDGET_THEME_DEFAULT },
            appsWidgetTheme = (p[Keys.APPS_WIDGET_THEME] ?: LauncherSettings.WIDGET_THEME_DEFAULT)
                .let { if (it in LauncherSettings.WIDGET_THEMES) it else LauncherSettings.WIDGET_THEME_DEFAULT },
            statsWidgetTheme = (p[Keys.STATS_WIDGET_THEME] ?: LauncherSettings.WIDGET_THEME_DEFAULT)
                .let { if (it in LauncherSettings.WIDGET_THEMES) it else LauncherSettings.WIDGET_THEME_DEFAULT },
            noteWidgetTheme = (p[Keys.NOTE_WIDGET_THEME] ?: LauncherSettings.WIDGET_THEME_DEFAULT)
                .let { if (it in LauncherSettings.WIDGET_THEMES) it else LauncherSettings.WIDGET_THEME_DEFAULT },
            mediaWidgetArtwork = p[Keys.MEDIA_WIDGET_ARTWORK] ?: true,
            appsWidgetLabels = p[Keys.APPS_WIDGET_LABELS] ?: false,
            statsShowBattery = p[Keys.STATS_SHOW_BATTERY] ?: true,
            statsShowStorage = p[Keys.STATS_SHOW_STORAGE] ?: true,
            statsShowMemory = p[Keys.STATS_SHOW_MEMORY] ?: true,
            statsShowBluetooth = p[Keys.STATS_SHOW_BLUETOOTH] ?: true,
            noteWidgetTextSize = (p[Keys.NOTE_WIDGET_TEXT_SIZE] ?: LauncherSettings.FOLDER_OPEN_MEDIUM)
                .let { if (it in LauncherSettings.FOLDER_OPEN_SIZES) it else LauncherSettings.FOLDER_OPEN_MEDIUM },
            noteWidgetText = p[Keys.NOTE_WIDGET_TEXT] ?: "",
            searchShortcuts = p[Keys.SEARCH_SHORTCUTS] ?: true,
            searchWeb = p[Keys.SEARCH_WEB] ?: true,
            animationSpeed = (p[Keys.ANIMATION_SPEED] ?: 1f).coerceIn(LauncherSettings.MIN_ANIMATION_SPEED, LauncherSettings.MAX_ANIMATION_SPEED),
            appOpenAnimation = (p[Keys.APP_OPEN_ANIMATION] ?: LauncherSettings.APP_OPEN_ICON)
                .let { if (it in LauncherSettings.APP_OPENS) it else LauncherSettings.APP_OPEN_ICON },
            folderOpenAnimation = (p[Keys.FOLDER_OPEN_ANIMATION] ?: LauncherSettings.FOLDER_ANIM_SHEET)
                .let { if (it in LauncherSettings.FOLDER_ANIMS) it else LauncherSettings.FOLDER_ANIM_SHEET },
            pageScrollEffect = (p[Keys.PAGE_SCROLL_EFFECT] ?: LauncherSettings.PAGE_EFFECT_SLIDE)
                .let { if (it in LauncherSettings.PAGE_EFFECTS) it else LauncherSettings.PAGE_EFFECT_SLIDE },
            taskbarEnabled = p[Keys.TASKBAR_ENABLED] ?: false,
            taskbarPinnedApps = p[Keys.TASKBAR_PINNED]?.split("\n")?.filter { it.isNotEmpty() },
            taskbarRecentCount = (p[Keys.TASKBAR_RECENTS] ?: 4).coerceIn(0, 6),
            taskbarIconSize = (p[Keys.TASKBAR_ICON_SIZE] ?: 48).coerceIn(32, 72),
            taskbarIconSpacing = (p[Keys.TASKBAR_SPACING] ?: 8).coerceIn(0, 24),
            taskbarBottomOffset = (p[Keys.TASKBAR_BOTTOM] ?: 12).coerceIn(0, 64),
            taskbarTheme = (p[Keys.TASKBAR_THEME] ?: LauncherSettings.TASKBAR_THEME_CLEAR).let {
                if (it in LauncherSettings.TASKBAR_THEMES) it else LauncherSettings.TASKBAR_THEME_CLEAR
            },
            taskbarOpacity = (p[Keys.TASKBAR_OPACITY] ?: 0.8f).coerceIn(0.1f, 1f),
            taskbarHandles = p[Keys.TASKBAR_HANDLES] ?: LauncherSettings.TASKBAR_HANDLES_BOTH,
            taskbarOnHome = p[Keys.TASKBAR_ON_HOME] ?: true,
            taskbarKeepOpen = p[Keys.TASKBAR_KEEP_OPEN] ?: false,
            taskbarMediaEnabled = p[Keys.TASKBAR_MEDIA] ?: true,
            taskbarMediaShowPaused = p[Keys.TASKBAR_MEDIA_PAUSED] ?: true,
            taskbarMediaPosition = p[Keys.TASKBAR_MEDIA_POSITION] ?: LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE,
            taskbarMediaTap = p[Keys.TASKBAR_MEDIA_TAP] ?: LauncherSettings.TASKBAR_MEDIA_TAP_OPEN,
            taskbarMediaControls = p[Keys.TASKBAR_MEDIA_CONTROLS] ?: true,
            taskbarMediaShape = p[Keys.TASKBAR_MEDIA_SHAPE] ?: LauncherSettings.TASKBAR_MEDIA_SHAPE_CIRCLE,
            taskbarMediaBadge = p[Keys.TASKBAR_MEDIA_BADGE] ?: true,
            taskbarSystemDrag = p[Keys.TASKBAR_SYSTEM_DRAG] ?: true,
            taskbarRecentsButton = p[Keys.TASKBAR_RECENTS_BUTTON] ?: LauncherSettings.TASKBAR_RECENTS_SYSTEM,
            taskbarQuickSwitch = p[Keys.TASKBAR_QUICK_SWITCH] ?: true,
            taskbarSplitControls = p[Keys.TASKBAR_SPLIT_CONTROLS] ?: true,
            taskbarSplitSide = p[Keys.TASKBAR_SPLIT_SIDE] ?: LauncherSettings.TASKBAR_SPLIT_SIDE_RIGHT,
            taskbarThreeApp = p[Keys.TASKBAR_THREE_APP] ?: true,
            taskbarHideFullScreen = p[Keys.TASKBAR_HIDE_FULL_SCREEN] ?: true,
            settingsStyle = p[Keys.SETTINGS_STYLE] ?: LauncherSettings.SETTINGS_STYLE_STUDIO,
            iconShape = (p[Keys.ICON_SHAPE] ?: LauncherSettings.ICON_SHAPE_SYSTEM)
                .let { if (it in LauncherSettings.ICON_SHAPES) it else LauncherSettings.ICON_SHAPE_SYSTEM },
            motoWeatherTap = p[Keys.MOTO_WEATHER_TAP] ?: true,
            showPrivateSpace = p[Keys.SHOW_PRIVATE_SPACE] ?: true,
            popupNotifications = p[Keys.POPUP_NOTIFICATIONS] ?: true,
            dotAvatars = p[Keys.DOT_AVATARS] ?: true,
            liveActivities = p[Keys.LIVE_ACTIVITIES] ?: true,
            liveActivitiesTaskbar = p[Keys.LIVE_ACTIVITIES_TASKBAR] ?: true,
            liveActivityEffect = (p[Keys.LIVE_ACTIVITY_EFFECT] ?: LauncherSettings.LIVE_EFFECT_GLOW)
                .let { if (it in LauncherSettings.LIVE_EFFECTS) it else LauncherSettings.LIVE_EFFECT_GLOW },
            searchActions = p[Keys.SEARCH_ACTIONS] ?: true,
            recentsEnabled = p[Keys.RECENTS_ENABLED] ?: true,
            recentsReplaceSystem = p[Keys.RECENTS_REPLACE_SYSTEM] ?: false,
            navHandleOpensSearch = p[Keys.NAV_HANDLE_SEARCH] ?: false,
            recentsColumnsInner = (p[Keys.RECENTS_COLUMNS_INNER] ?: 4)
                .coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_INNER),
            recentsColumnsOuter = (p[Keys.RECENTS_COLUMNS_OUTER] ?: 1)
                .coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_OUTER),
            recentsMaxApps = (p[Keys.RECENTS_MAX_APPS] ?: 12).coerceIn(LauncherSettings.MIN_RECENTS_APPS, LauncherSettings.MAX_RECENTS_APPS),
            recentsCardHeight = (p[Keys.RECENTS_CARD_HEIGHT] ?: 300)
                .coerceIn(LauncherSettings.MIN_RECENTS_CARD_HEIGHT, LauncherSettings.MAX_RECENTS_CARD_HEIGHT),
            recentsCornerDp = (p[Keys.RECENTS_CORNER] ?: 26).coerceIn(0, LauncherSettings.MAX_RECENTS_CORNER_DP),
            recentsBackdropDim = (p[Keys.RECENTS_BACKDROP] ?: 0.85f).coerceIn(0f, 1f),
            recentsSort = (p[Keys.RECENTS_SORT] ?: LauncherSettings.RECENTS_SORT_RECENT)
                .let { if (it == LauncherSettings.RECENTS_SORT_NAME) it else LauncherSettings.RECENTS_SORT_RECENT },
            recentsShowTime = p[Keys.RECENTS_SHOW_TIME] ?: true,
            recentsShowActions = p[Keys.RECENTS_SHOW_ACTIONS] ?: true,
            recentsConfirmCloseAll = p[Keys.RECENTS_CONFIRM_CLOSE_ALL] ?: false,
            recentsThirdInSplit = p[Keys.RECENTS_THIRD_IN_SPLIT] ?: true,
            recentsThumbnails = p[Keys.RECENTS_THUMBNAILS] ?: true,
            recentsRootClose = p[Keys.RECENTS_ROOT_CLOSE] ?: true,
            taskbarRunningApps = p[Keys.TASKBAR_RUNNING_APPS] ?: false,
            taskbarRunningDots = p[Keys.TASKBAR_RUNNING_DOTS] ?: true,
            recentsEnterAnimation = (p[Keys.RECENTS_ENTER] ?: LauncherSettings.RECENTS_ENTER_RISE)
                .let { if (it in LauncherSettings.RECENTS_ENTERS) it else LauncherSettings.RECENTS_ENTER_RISE },
            recentsSelectAnimation = (p[Keys.RECENTS_SELECT] ?: LauncherSettings.RECENTS_SELECT_POP)
                .let { if (it in LauncherSettings.RECENTS_SELECTS) it else LauncherSettings.RECENTS_SELECT_POP },
            recentsAnimationSpeed = (p[Keys.RECENTS_ANIM_SPEED] ?: 1f)
                .coerceIn(LauncherSettings.MIN_RECENTS_ANIM_SPEED, LauncherSettings.MAX_RECENTS_ANIM_SPEED),
            recentsPressAnimation = (p[Keys.RECENTS_PRESS] ?: LauncherSettings.PRESS_SHRINK)
                .let { if (it in LauncherSettings.PRESS_EFFECTS) it else LauncherSettings.PRESS_SHRINK },
            recentsTheme = (p[Keys.RECENTS_THEME] ?: LauncherSettings.RECENTS_THEME_FOLARK)
                .let { if (it in LauncherSettings.RECENTS_THEMES) it else LauncherSettings.RECENTS_THEME_FOLARK },
            recentsGlass = p[Keys.RECENTS_GLASS] ?: false,
            recentsGlassBlur = (p[Keys.RECENTS_GLASS_BLUR] ?: 28).coerceIn(0, LauncherSettings.MAX_RECENTS_GLASS_BLUR),
            recentsGlassTint = (p[Keys.RECENTS_GLASS_TINT] ?: 0.16f)
                .coerceIn(LauncherSettings.MIN_RECENTS_GLASS_TINT, LauncherSettings.MAX_RECENTS_GLASS_TINT),
            recentsDragDrop = p[Keys.RECENTS_DRAG_DROP] ?: true,
            recentsBtnClose = p[Keys.RECENTS_BTN_CLOSE] ?: true,
            recentsBtnSplit = p[Keys.RECENTS_BTN_SPLIT] ?: true,
            recentsBtnPopup = p[Keys.RECENTS_BTN_POPUP] ?: true,
            recentsBtnUnsplit = p[Keys.RECENTS_BTN_UNSPLIT] ?: true,
            recentsBtnSplitView = p[Keys.RECENTS_BTN_SPLIT_VIEW] ?: true,
            recentsBtnCloseAll = p[Keys.RECENTS_BTN_CLOSE_ALL] ?: true,
            recentsSwipeUpAction = recentsAction(p[Keys.RECENTS_SWIPE_UP], LauncherSettings.RECENTS_ACTION_CLOSE),
            recentsSwipeDownAction = recentsAction(p[Keys.RECENTS_SWIPE_DOWN], LauncherSettings.RECENTS_ACTION_NONE),
            recentsLongPressAction = recentsAction(p[Keys.RECENTS_LONG_PRESS], LauncherSettings.RECENTS_ACTION_MENU),
            recentsDoubleTapAction = recentsAction(p[Keys.RECENTS_DOUBLE_TAP], LauncherSettings.RECENTS_ACTION_SPLIT),
            recentsPinned = lines(p[Keys.RECENTS_PINNED]),
            recentsBtnPin = p[Keys.RECENTS_BTN_PIN] ?: true,
            recentsPinKeepAlive = p[Keys.RECENTS_PIN_KEEP_ALIVE] ?: true,
            taskbarHiddenApps = lines(p[Keys.TASKBAR_HIDDEN_APPS]),
            taskbarAlwaysApps = lines(p[Keys.TASKBAR_ALWAYS_APPS]),
            liquidGlassEverywhere = p[Keys.LIQUID_GLASS_EVERYWHERE] ?: false,
            liquidGlassLevel = (p[Keys.LIQUID_GLASS_LEVEL] ?: 0.5f).coerceIn(0f, 1f),
            recentsAttachedSide = if (p[Keys.RECENTS_ATTACHED_SIDE] == LauncherSettings.RECENTS_ATTACHED_RIGHT)
                LauncherSettings.RECENTS_ATTACHED_RIGHT else LauncherSettings.RECENTS_ATTACHED_LEFT,
            recentsAttachedPeek = (p[Keys.RECENTS_ATTACHED_PEEK] ?: 0.12f)
                .coerceIn(LauncherSettings.MIN_RECENTS_ATTACHED_PEEK, LauncherSettings.MAX_RECENTS_ATTACHED_PEEK),
            taskbarHandleHint = p[Keys.TASKBAR_HANDLE_HINT] ?: true,
            taskbarSource = if (p[Keys.TASKBAR_SOURCE] == LauncherSettings.TASKBAR_SOURCE_SYSTEM)
                LauncherSettings.TASKBAR_SOURCE_SYSTEM else LauncherSettings.TASKBAR_SOURCE_FOLARK,
            systemTaskbarMode = (p[Keys.SYSTEM_TASKBAR_MODE] ?: 1).let { if (it in listOf(1, 2, 4)) it else 1 },
            rootEnabled = p[Keys.ROOT_ENABLED] ?: true,
            rootLockMode = rootMode(p[Keys.ROOT_LOCK_MODE]),
            rootRecentsMode = rootMode(p[Keys.ROOT_RECENTS_MODE]),
            rootShadeMode = rootMode(p[Keys.ROOT_SHADE_MODE]),
            rootKeepDump = p[Keys.ROOT_KEEP_DUMP] ?: false,
            rootKeepUsage = p[Keys.ROOT_KEEP_USAGE] ?: false,
            rootKeepSecureSettings = p[Keys.ROOT_KEEP_SECURE] ?: false,
            rootKeepNotifications = p[Keys.ROOT_KEEP_NOTIFICATIONS] ?: false,
            rootKeepAccessibility = p[Keys.ROOT_KEEP_ACCESSIBILITY] ?: false,
            rootKeepWidgets = p[Keys.ROOT_KEEP_WIDGETS] ?: false,
            iconNudge = p[Keys.ICON_NUDGE] ?: true,
            unlockTransition = p[Keys.UNLOCK_TRANSITION] ?: true,
            iconNudgeTaskbar = p[Keys.ICON_NUDGE_TASKBAR] ?: true,
            iconNudgeEffect = (p[Keys.ICON_NUDGE_EFFECT] ?: LauncherSettings.NUDGE_BOUNCE)
                .let { if (it in LauncherSettings.NUDGE_EFFECTS) it else LauncherSettings.NUDGE_BOUNCE },
            iconNudgeSoftness = (p[Keys.ICON_NUDGE_SOFTNESS] ?: 0.5f).coerceIn(0f, 1f),
            liveHingeEndAngle = (p[Keys.LIVE_HINGE_END_ANGLE] ?: 165).coerceIn(LauncherSettings.MIN_LIVE_HINGE_END_ANGLE, 180),
            liveHingeSmoothingMs = (p[Keys.LIVE_HINGE_SMOOTHING] ?: 50).coerceIn(0, LauncherSettings.MAX_LIVE_HINGE_SMOOTHING_MS),
            liveHingeStyle = (p[Keys.LIVE_HINGE_STYLE] ?: LauncherSettings.LIVE_HINGE_STYLE_PAGES)
                .let { if (it in LauncherSettings.LIVE_HINGE_STYLES) it else LauncherSettings.LIVE_HINGE_STYLE_PAGES },
            liveHingeOuterStyle = (p[Keys.LIVE_HINGE_OUTER_STYLE] ?: LauncherSettings.LIVE_HINGE_STYLE_WATER)
                .let { if (it in LauncherSettings.LIVE_HINGE_STYLES) it else LauncherSettings.LIVE_HINGE_STYLE_WATER },
            liveHingeBlurDp = (p[Keys.LIVE_HINGE_BLUR] ?: 24).coerceIn(0, LauncherSettings.MAX_LIVE_HINGE_BLUR_DP),
            liveHingeFrost = (p[Keys.LIVE_HINGE_FROST] ?: 0.2f).coerceIn(0f, LauncherSettings.MAX_LIVE_HINGE_FROST),
            weatherWidgetAnimated = p[Keys.WEATHER_WIDGET_ANIMATED] ?: true,
            clockWidgetDarkText = p[Keys.CLOCK_WIDGET_DARK_TEXT] ?: false,
            clockWidgetGlassEffect = p[Keys.CLOCK_WIDGET_GLASS_EFFECT] ?: true,
            clockMinuteColor = (p[Keys.CLOCK_MINUTE_COLOR] ?: LauncherSettings.CLOCK_MINUTE_STYLE).let {
                if (it in LauncherSettings.CLOCK_MINUTE_COLORS) it else LauncherSettings.CLOCK_MINUTE_STYLE
            },
            clockMinuteOpacity = (p[Keys.CLOCK_MINUTE_OPACITY] ?: 1f).coerceIn(0.1f, 1f),
            batterySaverMode = when (p[Keys.BATTERY_SAVER_MODE]) {
                LauncherSettings.BATTERY_SAVER_OFF -> LauncherSettings.BATTERY_SAVER_OFF
                LauncherSettings.BATTERY_SAVER_ON -> LauncherSettings.BATTERY_SAVER_ON
                else -> LauncherSettings.BATTERY_SAVER_AUTO
            },
            clockWidgetStyle = (p[Keys.CLOCK_WIDGET_STYLE] ?: LauncherSettings.CLOCK_STYLE_CLASSIC)
                .let { if (it in LauncherSettings.CLOCK_STYLES) it else LauncherSettings.CLOCK_STYLE_CLASSIC },
            appFontFamily = (p[Keys.appFontFamily(screenType)] ?: LauncherSettings.FONT_FAMILY_DEFAULT).let {
                if (it in LauncherSettings.FONT_FAMILIES) it else LauncherSettings.FONT_FAMILY_DEFAULT
            },
            appFontBold = p[Keys.appFontBold(screenType)] ?: false,
            appFontSizeScale = (p[Keys.appFontSizeScale(screenType)] ?: 1.0f).coerceIn(0.7f, 1.4f),
            widgetBackgroundOpacity = (p[Keys.WIDGET_BACKGROUND_OPACITY] ?: 0f).coerceIn(0f, 1f),
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

    val appPairs: Flow<List<AppPair>> = keyFlow(Keys.APP_PAIRS) { parseAppPairs(it) }

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
                // Optional third (floating) app — absent in pairs saved by older versions.
                app3Key = parts.getOrNull(10).orEmpty(),
                app3Package = parts.getOrNull(11).orEmpty(),
                app3ClassName = parts.getOrNull(12).orEmpty(),
                app3UserSerial = parts.getOrNull(13)?.toLongOrNull() ?: 0L,
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
                p.app3Key,
                p.app3Package,
                p.app3ClassName,
                p.app3UserSerial.toString(),
            ).joinToString("\t")
        }

    suspend fun setFoldAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.foldAction(screenType)] = value }
    suspend fun setUnfoldAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.unfoldAction(screenType)] = value }
    suspend fun setFoldTransitionStyle(value: String, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.foldTransitionStyle(screenType)] = value
    }
    suspend fun setFoldTransitionSpeed(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.foldTransitionSpeed(screenType)] = value
    }
    suspend fun setAquamorphicTouchEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.aquamorphicTouchEnabled(screenType)] = value }
    suspend fun setAppLaunchZoomEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.appLaunchZoomEnabled(screenType)] = value }
    suspend fun setPageBounceEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.pageBounceEnabled(screenType)] = value }
    suspend fun setIconSizeScale(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.iconSizeScale(screenType)] =
            value.coerceIn(LauncherSettings.MIN_ICON_SIZE_SCALE, LauncherSettings.MAX_ICON_SIZE_SCALE)
    }
    suspend fun setFolderShape(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderShape(screenType)] = value }
    suspend fun setFolderTheme(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTheme(screenType)] = value }
    suspend fun setFolderPreview(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderPreview(screenType)] = value }
    suspend fun setFolderOpenColumns(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.folderOpenColumns(screenType)] =
            value.coerceIn(LauncherSettings.MIN_FOLDER_OPEN_COLUMNS, LauncherSettings.MAX_FOLDER_OPEN_COLUMNS)
    }
    suspend fun setFolderOpenSize(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderOpenSize(screenType)] = value }
    suspend fun setFolderTapFirstApp(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.folderTapFirstApp(screenType)] = value }
    suspend fun setPopupTheme(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.popupTheme(screenType)] = value }
    suspend fun setStackAutoRotateSeconds(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.stackAutoRotateSeconds(screenType)] = value.coerceIn(0, LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS)
    }
    suspend fun setStackCornerRadius(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackCornerRadius(screenType)] = value.coerceIn(0, LauncherSettings.MAX_STACK_CORNER_RADIUS) }
    suspend fun setStackIndicator(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackIndicator(screenType)] = value }
    suspend fun setStackShowPill(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackShowPill(screenType)] = value }
    suspend fun setDotShape(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotShape(screenType)] = value }
    suspend fun setDotColor(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotColor(screenType)] = value }
    suspend fun setDotPosition(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dotPosition(screenType)] = value }
    suspend fun setHomeWallpaperDim(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeWallpaperDim(screenType)] = value.coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_DIM) }
    suspend fun setHomeWallpaperBlur(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeWallpaperBlur(screenType)] = value.coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_BLUR) }
    suspend fun setHomeBackgroundImage(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.homeBackgroundImage(screenType)] = value }
    suspend fun setIconSwipeUpAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.iconSwipeUpAction(screenType)] = value }
    suspend fun setNewPageOnDrag(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.newPageOnDrag(screenType)] = value }
    suspend fun setAutoRemoveEmptyPages(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.autoRemoveEmptyPages(screenType)] = value }
    suspend fun setDrawerFolderTabs(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerFolderTabs(screenType)] = value }
    suspend fun setFloatingWindowSize(value: String) = edit { it[Keys.FLOATING_WINDOW_SIZE] = value }
    suspend fun setSearchShortcuts(value: Boolean) = edit { it[Keys.SEARCH_SHORTCUTS] = value }
    suspend fun setSearchWeb(value: Boolean) = edit { it[Keys.SEARCH_WEB] = value }
    suspend fun setAnimationSpeed(value: Float) = edit {
        it[Keys.ANIMATION_SPEED] = value.coerceIn(LauncherSettings.MIN_ANIMATION_SPEED, LauncherSettings.MAX_ANIMATION_SPEED)
    }
    suspend fun setAppOpenAnimation(value: String) = edit { it[Keys.APP_OPEN_ANIMATION] = value }
    suspend fun setFolderOpenAnimation(value: String) = edit { it[Keys.FOLDER_OPEN_ANIMATION] = value }
    suspend fun setPageScrollEffect(value: String) = edit { it[Keys.PAGE_SCROLL_EFFECT] = value }
    suspend fun setWidgetBackgroundOpacity(value: Float) = edit { it[Keys.WIDGET_BACKGROUND_OPACITY] = value.coerceIn(0f, 1f) }
    /** The place typed in for the weather ("name<TAB>latitude<TAB>longitude"); "" = use the device's location. */
    val weatherLocation: Flow<String> = keyFlow(Keys.WEATHER_LOCATION) { it ?: "" }
    suspend fun setWeatherLocation(value: String) = edit { it[Keys.WEATHER_LOCATION] = value }
    suspend fun setBatterySaverMode(value: String) = edit { it[Keys.BATTERY_SAVER_MODE] = value }
    suspend fun setClockWidgetStyle(value: String) = edit { it[Keys.CLOCK_WIDGET_STYLE] = value }
    suspend fun setClockWidgetDarkText(value: Boolean) = edit { it[Keys.CLOCK_WIDGET_DARK_TEXT] = value }
    suspend fun setClockWidgetGlassEffect(value: Boolean) = edit { it[Keys.CLOCK_WIDGET_GLASS_EFFECT] = value }
    suspend fun setClockMinuteColor(value: String) = edit { it[Keys.CLOCK_MINUTE_COLOR] = value }
    suspend fun setClockMinuteOpacity(value: Float) = edit { it[Keys.CLOCK_MINUTE_OPACITY] = value.coerceIn(0.1f, 1f) }
    suspend fun setAppFontFamily(value: String, st: ScreenType) = edit { it[Keys.appFontFamily(st)] = value }
    suspend fun setAppFontBold(value: Boolean, st: ScreenType) = edit { it[Keys.appFontBold(st)] = value }
    suspend fun setAppFontSizeScale(value: Float, st: ScreenType) = edit { it[Keys.appFontSizeScale(st)] = value.coerceIn(0.7f, 1.4f) }
    suspend fun setWeatherWidgetAnimated(value: Boolean) = edit { it[Keys.WEATHER_WIDGET_ANIMATED] = value }
    suspend fun setTaskbarEnabled(value: Boolean) = edit { it[Keys.TASKBAR_ENABLED] = value }
    /** Null goes back to the inner dock's apps. */
    suspend fun setTaskbarPinned(keys: List<String>?) = edit { p ->
        p.remove(Keys.TASKBAR_PINNED)
        if (keys != null) p[Keys.TASKBAR_PINNED] = keys.distinct().joinToString("\n")
    }
    suspend fun setTaskbarRecentCount(value: Int) = edit { it[Keys.TASKBAR_RECENTS] = value.coerceIn(0, 6) }
    suspend fun setTaskbarIconSize(value: Int) = edit { it[Keys.TASKBAR_ICON_SIZE] = value.coerceIn(32, 72) }
    suspend fun setTaskbarIconSpacing(value: Int) = edit { it[Keys.TASKBAR_SPACING] = value.coerceIn(0, 24) }
    suspend fun setTaskbarBottomOffset(value: Int) = edit { it[Keys.TASKBAR_BOTTOM] = value.coerceIn(0, 64) }
    suspend fun setTaskbarTheme(value: String) = edit { it[Keys.TASKBAR_THEME] = value }
    suspend fun setTaskbarOpacity(value: Float) = edit { it[Keys.TASKBAR_OPACITY] = value.coerceIn(0.1f, 1f) }
    suspend fun setTaskbarHandles(value: String) = edit { it[Keys.TASKBAR_HANDLES] = value }
    suspend fun setTaskbarOnHome(value: Boolean) = edit { it[Keys.TASKBAR_ON_HOME] = value }
    suspend fun setTaskbarKeepOpen(value: Boolean) = edit { it[Keys.TASKBAR_KEEP_OPEN] = value }
    suspend fun setTaskbarMediaEnabled(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA] = value }
    suspend fun setTaskbarMediaShowPaused(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_PAUSED] = value }
    suspend fun setTaskbarMediaPosition(value: String) = edit { it[Keys.TASKBAR_MEDIA_POSITION] = value }
    suspend fun setTaskbarMediaTap(value: String) = edit { it[Keys.TASKBAR_MEDIA_TAP] = value }
    suspend fun setTaskbarMediaControls(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_CONTROLS] = value }
    suspend fun setTaskbarMediaShape(value: String) = edit { it[Keys.TASKBAR_MEDIA_SHAPE] = value }
    suspend fun setTaskbarMediaBadge(value: Boolean) = edit { it[Keys.TASKBAR_MEDIA_BADGE] = value }
    suspend fun setTaskbarSystemDrag(value: Boolean) = edit { it[Keys.TASKBAR_SYSTEM_DRAG] = value }
    suspend fun setTaskbarRecentsButton(value: String) = edit { it[Keys.TASKBAR_RECENTS_BUTTON] = value }
    suspend fun setTaskbarQuickSwitch(value: Boolean) = edit { it[Keys.TASKBAR_QUICK_SWITCH] = value }
    suspend fun setTaskbarSplitControls(value: Boolean) = edit { it[Keys.TASKBAR_SPLIT_CONTROLS] = value }
    suspend fun setTaskbarSplitSide(value: String) = edit { it[Keys.TASKBAR_SPLIT_SIDE] = value }
    suspend fun setTaskbarThreeApp(value: Boolean) = edit { it[Keys.TASKBAR_THREE_APP] = value }
    suspend fun setSettingsStyle(value: String) = edit { it[Keys.SETTINGS_STYLE] = value }
    suspend fun setIconShape(value: String) = edit { it[Keys.ICON_SHAPE] = value }
    suspend fun setMotoWeatherTap(value: Boolean) = edit { it[Keys.MOTO_WEATHER_TAP] = value }
    suspend fun setShowPrivateSpace(value: Boolean) = edit { it[Keys.SHOW_PRIVATE_SPACE] = value }
    suspend fun setPopupNotifications(value: Boolean) = edit { it[Keys.POPUP_NOTIFICATIONS] = value }
    suspend fun setDotAvatars(value: Boolean) = edit { it[Keys.DOT_AVATARS] = value }
    suspend fun setLiveActivities(value: Boolean) = edit { it[Keys.LIVE_ACTIVITIES] = value }
    suspend fun setLiveActivitiesTaskbar(value: Boolean) = edit { it[Keys.LIVE_ACTIVITIES_TASKBAR] = value }
    suspend fun setLiveActivityEffect(value: String) = edit { it[Keys.LIVE_ACTIVITY_EFFECT] = value }
    suspend fun setSearchActions(value: Boolean) = edit { it[Keys.SEARCH_ACTIONS] = value }
    suspend fun setRecentsEnabled(value: Boolean) = edit { it[Keys.RECENTS_ENABLED] = value }
    suspend fun setRecentsReplaceSystem(value: Boolean) = edit { it[Keys.RECENTS_REPLACE_SYSTEM] = value }
    suspend fun setNavHandleOpensSearch(value: Boolean) = edit { it[Keys.NAV_HANDLE_SEARCH] = value }
    suspend fun setRecentsColumnsInner(value: Int) = edit {
        it[Keys.RECENTS_COLUMNS_INNER] = value.coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_INNER)
    }
    suspend fun setRecentsColumnsOuter(value: Int) = edit {
        it[Keys.RECENTS_COLUMNS_OUTER] = value.coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_OUTER)
    }
    suspend fun setRecentsMaxApps(value: Int) = edit {
        it[Keys.RECENTS_MAX_APPS] = value.coerceIn(LauncherSettings.MIN_RECENTS_APPS, LauncherSettings.MAX_RECENTS_APPS)
    }
    suspend fun setRecentsCardHeight(value: Int) = edit {
        it[Keys.RECENTS_CARD_HEIGHT] = value.coerceIn(LauncherSettings.MIN_RECENTS_CARD_HEIGHT, LauncherSettings.MAX_RECENTS_CARD_HEIGHT)
    }
    suspend fun setRecentsCornerDp(value: Int) = edit { it[Keys.RECENTS_CORNER] = value.coerceIn(0, LauncherSettings.MAX_RECENTS_CORNER_DP) }
    suspend fun setRecentsBackdropDim(value: Float) = edit { it[Keys.RECENTS_BACKDROP] = value.coerceIn(0f, 1f) }
    suspend fun setRecentsSort(value: String) = edit { it[Keys.RECENTS_SORT] = value }
    suspend fun setRecentsShowTime(value: Boolean) = edit { it[Keys.RECENTS_SHOW_TIME] = value }
    suspend fun setRecentsShowActions(value: Boolean) = edit { it[Keys.RECENTS_SHOW_ACTIONS] = value }
    suspend fun setRecentsConfirmCloseAll(value: Boolean) = edit { it[Keys.RECENTS_CONFIRM_CLOSE_ALL] = value }
    suspend fun setRecentsThirdInSplit(value: Boolean) = edit { it[Keys.RECENTS_THIRD_IN_SPLIT] = value }
    suspend fun setRecentsThumbnails(value: Boolean) = edit { it[Keys.RECENTS_THUMBNAILS] = value }
    suspend fun setRecentsRootClose(value: Boolean) = edit { it[Keys.RECENTS_ROOT_CLOSE] = value }
    suspend fun setTaskbarRunningApps(value: Boolean) = edit { it[Keys.TASKBAR_RUNNING_APPS] = value }
    suspend fun setTaskbarRunningDots(value: Boolean) = edit { it[Keys.TASKBAR_RUNNING_DOTS] = value }
    suspend fun setRecentsEnterAnimation(value: String) = edit { it[Keys.RECENTS_ENTER] = value }
    suspend fun setRecentsSelectAnimation(value: String) = edit { it[Keys.RECENTS_SELECT] = value }
    suspend fun setRecentsAnimationSpeed(value: Float) = edit {
        it[Keys.RECENTS_ANIM_SPEED] = value.coerceIn(LauncherSettings.MIN_RECENTS_ANIM_SPEED, LauncherSettings.MAX_RECENTS_ANIM_SPEED)
    }
    suspend fun setRootEnabled(value: Boolean) = edit { it[Keys.ROOT_ENABLED] = value }
    suspend fun setRecentsTheme(value: String) = edit { it[Keys.RECENTS_THEME] = value }
    suspend fun setRecentsPressAnimation(value: String) = edit { it[Keys.RECENTS_PRESS] = value }
    suspend fun setRecentsGlass(value: Boolean) = edit { it[Keys.RECENTS_GLASS] = value }
    suspend fun setRecentsGlassBlur(value: Int) = edit { it[Keys.RECENTS_GLASS_BLUR] = value.coerceIn(0, LauncherSettings.MAX_RECENTS_GLASS_BLUR) }
    suspend fun setRecentsGlassTint(value: Float) = edit {
        it[Keys.RECENTS_GLASS_TINT] = value.coerceIn(LauncherSettings.MIN_RECENTS_GLASS_TINT, LauncherSettings.MAX_RECENTS_GLASS_TINT)
    }
    suspend fun setRecentsDragDrop(value: Boolean) = edit { it[Keys.RECENTS_DRAG_DROP] = value }
    suspend fun setRecentsBtnClose(value: Boolean) = edit { it[Keys.RECENTS_BTN_CLOSE] = value }
    suspend fun setRecentsBtnSplit(value: Boolean) = edit { it[Keys.RECENTS_BTN_SPLIT] = value }
    suspend fun setRecentsBtnPopup(value: Boolean) = edit { it[Keys.RECENTS_BTN_POPUP] = value }
    suspend fun setRecentsBtnUnsplit(value: Boolean) = edit { it[Keys.RECENTS_BTN_UNSPLIT] = value }
    suspend fun setRecentsBtnSplitView(value: Boolean) = edit { it[Keys.RECENTS_BTN_SPLIT_VIEW] = value }
    suspend fun setRecentsBtnCloseAll(value: Boolean) = edit { it[Keys.RECENTS_BTN_CLOSE_ALL] = value }
    suspend fun setRecentsSwipeUpAction(value: String) = edit { it[Keys.RECENTS_SWIPE_UP] = value }
    suspend fun setRecentsSwipeDownAction(value: String) = edit { it[Keys.RECENTS_SWIPE_DOWN] = value }
    suspend fun setRecentsLongPressAction(value: String) = edit { it[Keys.RECENTS_LONG_PRESS] = value }
    suspend fun setRecentsDoubleTapAction(value: String) = edit { it[Keys.RECENTS_DOUBLE_TAP] = value }

    suspend fun setRecentsPinned(value: Set<String>) = edit { it[Keys.RECENTS_PINNED] = value.joinToString("\n") }
    suspend fun setRecentsBtnPin(value: Boolean) = edit { it[Keys.RECENTS_BTN_PIN] = value }
    suspend fun setRecentsPinKeepAlive(value: Boolean) = edit { it[Keys.RECENTS_PIN_KEEP_ALIVE] = value }
    /** Packages whose battery-optimisation exemption Folark added for a pin (and may take back). */
    suspend fun pinExemptions(): Set<String> = lines(dataStore.data.first()[Keys.RECENTS_PIN_EXEMPT])
    suspend fun setPinExemptions(value: Set<String>) = edit { it[Keys.RECENTS_PIN_EXEMPT] = value.joinToString("\n") }
    suspend fun setTaskbarHiddenApps(value: Set<String>) = edit { it[Keys.TASKBAR_HIDDEN_APPS] = value.joinToString("\n") }
    suspend fun setTaskbarAlwaysApps(value: Set<String>) = edit { it[Keys.TASKBAR_ALWAYS_APPS] = value.joinToString("\n") }
    suspend fun setLiquidGlassEverywhere(value: Boolean) = edit { it[Keys.LIQUID_GLASS_EVERYWHERE] = value }
    suspend fun setLiquidGlassLevel(value: Float) = edit { it[Keys.LIQUID_GLASS_LEVEL] = value.coerceIn(0f, 1f) }

    private fun lines(raw: String?): Set<String> = raw?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun recentsAction(value: String?, fallback: String): String =
        if (value in LauncherSettings.RECENTS_ACTIONS) value!! else fallback
    suspend fun setRecentsAttachedSide(value: String) = edit { it[Keys.RECENTS_ATTACHED_SIDE] = value }
    suspend fun setRecentsAttachedPeek(value: Float) = edit {
        it[Keys.RECENTS_ATTACHED_PEEK] = value.coerceIn(LauncherSettings.MIN_RECENTS_ATTACHED_PEEK, LauncherSettings.MAX_RECENTS_ATTACHED_PEEK)
    }
    suspend fun setTaskbarHandleHint(value: Boolean) = edit { it[Keys.TASKBAR_HANDLE_HINT] = value }
    suspend fun setTaskbarSource(value: String) = edit { it[Keys.TASKBAR_SOURCE] = value }
    suspend fun setSystemTaskbarMode(value: Int) = edit { it[Keys.SYSTEM_TASKBAR_MODE] = value }
    suspend fun setRootLockMode(value: String) = edit { it[Keys.ROOT_LOCK_MODE] = rootMode(value) }
    suspend fun setRootRecentsMode(value: String) = edit { it[Keys.ROOT_RECENTS_MODE] = rootMode(value) }
    suspend fun setRootShadeMode(value: String) = edit { it[Keys.ROOT_SHADE_MODE] = rootMode(value) }
    suspend fun setRootKeepDump(value: Boolean) = edit { it[Keys.ROOT_KEEP_DUMP] = value }
    suspend fun setRootKeepUsage(value: Boolean) = edit { it[Keys.ROOT_KEEP_USAGE] = value }
    suspend fun setRootKeepSecureSettings(value: Boolean) = edit { it[Keys.ROOT_KEEP_SECURE] = value }
    suspend fun setRootKeepNotifications(value: Boolean) = edit { it[Keys.ROOT_KEEP_NOTIFICATIONS] = value }
    suspend fun setRootKeepAccessibility(value: Boolean) = edit { it[Keys.ROOT_KEEP_ACCESSIBILITY] = value }
    suspend fun setRootKeepWidgets(value: Boolean) = edit { it[Keys.ROOT_KEEP_WIDGETS] = value }

    private fun rootMode(value: String?): String =
        if (value in LauncherSettings.ROOT_MODES) value!! else LauncherSettings.ROOT_MODE_FALLBACK
    suspend fun setIconNudge(value: Boolean) = edit { it[Keys.ICON_NUDGE] = value }
    suspend fun setUnlockTransition(value: Boolean) = edit { it[Keys.UNLOCK_TRANSITION] = value }
    suspend fun setIconNudgeTaskbar(value: Boolean) = edit { it[Keys.ICON_NUDGE_TASKBAR] = value }
    suspend fun setIconNudgeEffect(value: String) = edit { it[Keys.ICON_NUDGE_EFFECT] = value }
    suspend fun setIconNudgeSoftness(value: Float) = edit { it[Keys.ICON_NUDGE_SOFTNESS] = value.coerceIn(0f, 1f) }
    suspend fun setLiveHingeEndAngle(value: Int) = edit { it[Keys.LIVE_HINGE_END_ANGLE] = value }
    suspend fun setLiveHingeSmoothing(value: Int) = edit { it[Keys.LIVE_HINGE_SMOOTHING] = value }
    suspend fun setLiveHingeStyle(value: String) = edit { it[Keys.LIVE_HINGE_STYLE] = value }
    suspend fun setLiveHingeOuterStyle(value: String) = edit { it[Keys.LIVE_HINGE_OUTER_STYLE] = value }
    suspend fun setLiveHingeBlur(value: Int) = edit { it[Keys.LIVE_HINGE_BLUR] = value }
    suspend fun setLiveHingeFrost(value: Float) = edit { it[Keys.LIVE_HINGE_FROST] = value }

    /** Per-widget look, home row id → "cornerRadius,padding,opacity" (see the home screen's WidgetLook). */
    val widgetLooks: Flow<Map<String, String>> = keyFlow(Keys.WIDGET_LOOKS) { parseLabels(it) }

    /** Sets (or, for a null/blank [look], clears) the look of the widget in home row [rowId]. */
    suspend fun setWidgetLook(rowId: Long, look: String?) = edit { p ->
        val current = parseLabels(p[Keys.WIDGET_LOOKS]).toMutableMap()
        if (look.isNullOrBlank()) current.remove(rowId.toString()) else current[rowId.toString()] = look
        p[Keys.WIDGET_LOOKS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }
    suspend fun setMediaWidgetTheme(value: String) = edit { it[Keys.MEDIA_WIDGET_THEME] = value }
    suspend fun setAppsWidgetTheme(value: String) = edit { it[Keys.APPS_WIDGET_THEME] = value }
    suspend fun setStatsWidgetTheme(value: String) = edit { it[Keys.STATS_WIDGET_THEME] = value }
    suspend fun setNoteWidgetTheme(value: String) = edit { it[Keys.NOTE_WIDGET_THEME] = value }
    suspend fun setMediaWidgetArtwork(value: Boolean) = edit { it[Keys.MEDIA_WIDGET_ARTWORK] = value }
    suspend fun setAppsWidgetLabels(value: Boolean) = edit { it[Keys.APPS_WIDGET_LABELS] = value }
    suspend fun setStatsShowBattery(value: Boolean) = edit { it[Keys.STATS_SHOW_BATTERY] = value }
    suspend fun setStatsShowStorage(value: Boolean) = edit { it[Keys.STATS_SHOW_STORAGE] = value }
    suspend fun setStatsShowMemory(value: Boolean) = edit { it[Keys.STATS_SHOW_MEMORY] = value }
    suspend fun setStatsShowBluetooth(value: Boolean) = edit { it[Keys.STATS_SHOW_BLUETOOTH] = value }
    suspend fun setNoteWidgetTextSize(value: String) = edit { it[Keys.NOTE_WIDGET_TEXT_SIZE] = value }
    /** The quick-note widget's text; capped so a pasted wall of text cannot bloat every settings read. */
    suspend fun setNoteWidgetText(value: String) = edit { it[Keys.NOTE_WIDGET_TEXT] = value.take(4000) }

    /**
     * Applies a launcher-wide theme preset to [screenType]: one tap sets the drawer, folders,
     * long-press menus, notification dots, widget-stack indicator and dock together. Every value
     * stays an ordinary setting afterwards and can be changed on its own.
     */
    suspend fun applyThemePreset(preset: String, screenType: ScreenType) = edit { p ->
        fun put(drawer: String, glass: Boolean, folder: String, popup: String, dot: String, indicator: String, dock: Float, amoled: Boolean) {
            p[Keys.drawerThemeTint(screenType)] = drawer
            p[Keys.drawerGlassEnabled(screenType)] = glass
            p[Keys.folderTheme(screenType)] = folder
            p[Keys.popupTheme(screenType)] = popup
            p[Keys.dotColor(screenType)] = dot
            p[Keys.stackIndicator(screenType)] = indicator
            p[Keys.dockBackgroundOpacity(screenType)] = dock
            p[Keys.amoledDark(screenType)] = amoled
        }
        when (preset) {
            LauncherSettings.THEME_PRESET_GLASS -> put(
                LauncherSettings.DRAWER_THEME_SYSTEM, true, LauncherSettings.FOLDER_THEME_GLASS,
                LauncherSettings.POPUP_THEME_MATERIAL, LauncherSettings.DOT_COLOR_ACCENT,
                LauncherSettings.STACK_INDICATOR_DOTS, 0.35f, false,
            )
            LauncherSettings.THEME_PRESET_DARK -> put(
                LauncherSettings.DRAWER_THEME_OBSIDIAN_BLACK, false, LauncherSettings.FOLDER_THEME_DARK,
                LauncherSettings.POPUP_THEME_DARK, LauncherSettings.DOT_COLOR_RED,
                LauncherSettings.STACK_INDICATOR_DOTS, 0.6f, true,
            )
            LauncherSettings.THEME_PRESET_LIGHT -> put(
                LauncherSettings.DRAWER_THEME_LIGHT, false, LauncherSettings.FOLDER_THEME_LIGHT,
                LauncherSettings.POPUP_THEME_LIGHT, LauncherSettings.DOT_COLOR_RED,
                LauncherSettings.STACK_INDICATOR_DOTS, 0.5f, false,
            )
            LauncherSettings.THEME_PRESET_MONO -> put(
                LauncherSettings.DRAWER_THEME_MONOCHROME, false, LauncherSettings.FOLDER_THEME_DARK,
                LauncherSettings.POPUP_THEME_DARK, LauncherSettings.DOT_COLOR_WHITE,
                LauncherSettings.STACK_INDICATOR_BAR, 0.4f, false,
            )
        }
    }

    /** Hand-picked icons, app key → "packPackage|drawableName" (both displays). */
    val customIcons: Flow<Map<String, String>> = keyFlow(Keys.CUSTOM_ICONS) { parseLabels(it) }

    /** Sets (or, for a null/blank [icon], clears) the hand-picked icon of the app [key]. */
    suspend fun setCustomIcon(key: String, icon: String?) = edit { p ->
        val current = parseLabels(p[Keys.CUSTOM_ICONS]).toMutableMap()
        if (icon.isNullOrBlank()) current.remove(key) else current[key] = icon
        p[Keys.CUSTOM_ICONS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }
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
    val heldNotifications: Flow<Map<String, Long>> = keyFlow(Keys.PEOPLE_HELD) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
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
    val peopleAliases: Flow<Map<String, String>> = keyFlow(Keys.PEOPLE_ALIASES) { parseAliases(it) }

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
    val pinnedPeople: Flow<List<PinnedPerson>> = keyFlow(Keys.PINNED_PEOPLE) { parsePinned(it) }

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
    fun dockFavorites(screenType: ScreenType = ScreenType.OUTER): Flow<List<String>> = keyFlow(Keys.dockFavorites(screenType)) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** App keys hidden from the app drawer (see AppItem.key). */
    val hiddenApps: Flow<Set<String>> = keyFlow(Keys.HIDDEN_APPS) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) = edit { p ->
        val current = (p[Keys.HIDDEN_APPS]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
        if (hidden) current.add(key) else current.remove(key)
        p[Keys.HIDDEN_APPS] = current.joinToString("\n")
    }

    /** User-chosen custom app labels (AppItem.key / componentName -> label), applied over the system label. */
    val customLabels: Flow<Map<String, String>> = keyFlow(Keys.CUSTOM_LABELS) { parseLabels(it) }

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

    val drawerFolders: Flow<List<DrawerFolder>> = keyFlow(Keys.DRAWER_FOLDERS) { parseFolders(it) }

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
    suspend fun setAllowLandscape(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.allowLandscape(screenType)] = value }
    suspend fun setSwipeDownAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeDownAction(screenType)] = value }
    suspend fun setDoubleTapAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.doubleTapAction(screenType)] = value }
    suspend fun setSwipeUpAction(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.swipeUpAction(screenType)] = value }
    suspend fun setHalfOpenedModeEnabled(value: Boolean) = edit { it[Keys.HALF_OPENED_ENABLED] = value }
    suspend fun setHingeTilt(value: Boolean) = edit { it[Keys.HINGE_TILT] = value }
    suspend fun setTaskbarHideFullScreen(value: Boolean) = edit { it[Keys.TASKBAR_HIDE_FULL_SCREEN] = value }
    suspend fun setSplitLaunchEnabled(value: Boolean) = edit { it[Keys.SPLIT_LAUNCH_ENABLED] = value }
    suspend fun setFloatingLaunchEnabled(value: Boolean) = edit { it[Keys.FLOATING_LAUNCH_ENABLED] = value }
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
    suspend fun setAzIndexScale(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.azIndexScale(screenType)] = value.coerceIn(LauncherSettings.MIN_AZ_INDEX_SCALE, LauncherSettings.MAX_AZ_INDEX_SCALE)
    }
    suspend fun setDrawerBottomSpace(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.drawerBottomSpace(screenType)] = value.coerceIn(0, LauncherSettings.MAX_DRAWER_BOTTOM_SPACE)
    }
    suspend fun setDrawerLetterHeaders(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLetterHeaders(screenType)] = value }
    suspend fun setDrawerLargeLetterPopup(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLargeLetterPopup(screenType)] = value }
    suspend fun setDrawerSwipeTabs(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerSwipeTabs(screenType)] = value }
    suspend fun setDrawerFlickClose(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerFlickClose(screenType)] = value }
    suspend fun setThemedIconStyle(value: String) = edit { it[Keys.THEMED_ICON_STYLE] = value }
    suspend fun setForceThemedIcons(value: Boolean) = edit { it[Keys.FORCE_THEMED_ICONS] = value }
    suspend fun setDrawerHandleStyle(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerHandleStyle(screenType)] = value }
    suspend fun setDrawerCornerRadius(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.drawerCornerRadius(screenType)] = value.coerceIn(LauncherSettings.DRAWER_CORNER_AUTO, LauncherSettings.MAX_DRAWER_CORNER_RADIUS)
    }
    suspend fun setAzIndexPosition(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.azIndexPosition(screenType)] = value }
    suspend fun setDrawerBackgroundBlur(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerBackgroundBlur(screenType)] = value.coerceIn(0f, 25f) }
    suspend fun setDrawerScrimOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerScrimOpacity(screenType)] = value.coerceIn(0f, 1f) }
    suspend fun setGlassBlurRadius(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassBlurRadius(screenType)] = value.coerceIn(0f, 50f) }
    suspend fun setGlassDarkTint(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.glassDarkTint(screenType)] = value.coerceIn(0f, 0.9f) }
    suspend fun flushDataStore() = edit { }
    /** Switches the drawer style. The accent follows the new style's default only while the
     *  current accent is still a style default — a colour the user picked themselves is kept. */
    suspend fun setDrawerStyle(value: String, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.drawerStyle(screenType)] = value
        val current = it[Keys.drawerAccentColor(screenType)]
        if (current == null || current in LauncherSettings.STYLE_DEFAULT_ACCENTS) {
            it[Keys.drawerAccentColor(screenType)] = LauncherSettings.defaultAccentForStyle(value)
        }
    }
    suspend fun setDrawerThemeTint(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerThemeTint(screenType)] = value }
    /** Colours are stored opaque: a pasted hex with zero alpha would make the accent/background invisible. */
    suspend fun setDrawerAccentColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAccentColor(screenType)] = argb or OPAQUE }
    suspend fun setDrawerCustomColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerCustomColor(screenType)] = argb or OPAQUE }
    suspend fun setDrawerGlassEnabled(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerGlassEnabled(screenType)] = value }
    suspend fun setDrawerPanelOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit {
        it[Keys.drawerPanelOpacity(screenType)] =
            value.coerceIn(LauncherSettings.MIN_DRAWER_PANEL_OPACITY, LauncherSettings.MAX_DRAWER_PANEL_OPACITY)
    }

    /** Copies every per-display setting of [from] onto [to] in one write — the explicit way to make
     *  both displays match, now that the profiles never inherit from each other. Layout data (dock
     *  favourites, home pages) is deliberately not copied: icons live per display. */
    @Suppress("UNCHECKED_CAST")
    suspend fun copyProfile(from: ScreenType, to: ScreenType) = edit { p ->
        if (from == to) return@edit
        val snapshot = p.asMap()
        for (name in PROFILE_KEY_NAMES) {
            val sourceName = profileKeyName(name, from)
            val targetName = profileKeyName(name, to)
            when (val value = snapshot.entries.firstOrNull { it.key.name == sourceName }?.value) {
                is Boolean -> p[booleanPreferencesKey(targetName)] = value
                is Int -> p[intPreferencesKey(targetName)] = value
                is Float -> p[floatPreferencesKey(targetName)] = value
                is String -> p[stringPreferencesKey(targetName)] = value
                // Unset on the source means "default" there, so the target returns to its default too.
                else -> {
                    val stale = snapshot.keys.firstOrNull { it.name == targetName }
                    if (stale != null) p.remove(stale as Preferences.Key<Any>)
                }
            }
        }
    }

    suspend fun copyDockFavorites(from: ScreenType, to: ScreenType) = edit { p ->
        if (from == to) return@edit
        p[Keys.dockFavorites(to)] = p[Keys.dockFavorites(from)] ?: ""
    }

    /**
     * One-time split of the two display profiles. Older builds let the inner profile read the outer
     * display's value whenever it had none of its own, so changing a cover-screen setting silently
     * changed the inner screen as well. This freezes what the inner profile inherited at that
     * moment into its own keys; from then on each display only ever reads and writes its own.
     */
    private fun splitProfilesOnce(p: MutablePreferences) {
        if (p[Keys.PROFILES_SPLIT] == true) return
        val all = p.asMap()
        for (name in INHERITED_KEY_NAMES) {
            val innerName = name + INNER_SUFFIX
            if (all.keys.any { it.name == innerName }) continue
            when (val value = all.entries.firstOrNull { it.key.name == name }?.value) {
                is Boolean -> p[booleanPreferencesKey(innerName)] = value
                is Int -> p[intPreferencesKey(innerName)] = value
                is Float -> p[floatPreferencesKey(innerName)] = value
                is String -> p[stringPreferencesKey(innerName)] = value
                else -> Unit
            }
        }
        // Two legacy keys used a different name than today's outer key.
        p[Keys.FOLD_TRANSITION_STYLE]?.let { legacy ->
            for (type in ScreenType.entries) {
                if (p[Keys.foldTransitionStyle(type)] == null) p[Keys.foldTransitionStyle(type)] = legacy
            }
        }
        p[Keys.DOUBLE_TAP_LOCK]?.let { legacy ->
            for (type in ScreenType.entries) {
                if (p[Keys.doubleTapToLock(type)] == null) p[Keys.doubleTapToLock(type)] = legacy
            }
        }
        for (type in ScreenType.entries) {
            if (p[Keys.doubleTapToLock(type)] == true) {
                if (p[Keys.doubleTapAction(type)] == null || p[Keys.doubleTapAction(type)] == LauncherSettings.GESTURE_NONE) {
                    p[Keys.doubleTapAction(type)] = LauncherSettings.GESTURE_LOCK
                }
            }
        }
        p[Keys.PROFILES_SPLIT] = true
    }
    suspend fun setDrawerHeightFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerHeightFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
    suspend fun setDrawerWidthFraction(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerWidthFraction(screenType)] = value.coerceIn(0.4f, 1.0f) }
    suspend fun setDrawerAlignment(value: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAlignment(screenType)] = value }
    suspend fun setParallaxWallpaper(enabled: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.parallaxWallpaper(screenType)] = enabled }
    suspend fun setDrawerLayoutMode(mode: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerLayoutMode(screenType)] = mode }
    suspend fun setMinusOneScreen(value: String) = edit { it[Keys.MINUS_ONE_SCREEN] = value }
    suspend fun setDockBackgroundOpacity(value: Float, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockBackgroundOpacity(screenType)] = value.coerceIn(0f, 1f) }
    suspend fun setShowPageIndicator(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showPageIndicator(screenType)] = value }
    suspend fun setShowNotificationDots(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showNotificationDots(screenType)] = value }
    suspend fun setNotificationDotCount(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.notificationDotCount(screenType)] = value }
    suspend fun setNotificationDotScale(value: Float, screenType: ScreenType = ScreenType.OUTER) =
        edit { it[Keys.notificationDotScale(screenType)] = value.coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE) }
    suspend fun setUseThemedIcons(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.useThemedIcons(screenType)] = value }

    /** Sets (or clears, for blank) the selected third-party icon pack package. */
    suspend fun setIconPackPackage(pkg: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.iconPackPackage(screenType)] = pkg.trim() }

    /** Count indicator style of the built-in notifications widget (see LauncherSettings.COUNT_*). */
    suspend fun setWidgetTonalBackground(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.widgetTonalBackground(screenType)] = value }
    suspend fun setNotificationWidgetCountStyle(style: String, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.notificationWidgetCountStyle(screenType)] = style }
    suspend fun setSearchContacts(value: Boolean) = edit { it[Keys.SEARCH_CONTACTS] = value }

    /** Sets (or, for a blank/null [key], clears) the app launched by the left-edge home swipe. */
    suspend fun setLeftSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.leftSwipeAppKey(screenType)] = key?.trim().orEmpty() }

    /** Sets (or, for a blank/null [key], clears) the app launched by the right-edge home swipe. */
    suspend fun setRightSwipeAppKey(key: String?, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.rightSwipeAppKey(screenType)] = key?.trim().orEmpty() }

    /** Locks/unlocks the desktop layout (blocks moving/removing/adding home + dock items). */
    suspend fun setDesktopLocked(value: Boolean) = edit { it[Keys.DESKTOP_LOCKED] = value }


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

    suspend fun setShowWeather(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showWeather(screenType)] = value }

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
        fun amoledDark(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "amoled_dark" else "amoled_dark_inner")
        fun allowLandscape(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "allow_landscape" else "allow_landscape_inner")
        fun swipeDownAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "swipe_down_action" else "swipe_down_action_inner")
        fun doubleTapAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "double_tap_action" else "double_tap_action_inner")
        fun swipeUpAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "swipe_up_action" else "swipe_up_action_inner")
        fun foldAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "fold_action" else "fold_action_inner")
        fun unfoldAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "unfold_action" else "unfold_action_inner")
        fun drawerSearchPosition(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_search_position" else "drawer_search_position_inner")
        fun drawerIndexBarEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_index_bar_enabled" else "drawer_index_bar_enabled_inner")
        fun azIndexScale(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "az_index_scale" else "az_index_scale_inner")
        fun drawerBottomSpace(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_bottom_space" else "drawer_bottom_space_inner")
        fun drawerLetterHeaders(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_letter_headers" else "drawer_letter_headers_inner")
        fun drawerLargeLetterPopup(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_large_letter_popup" else "drawer_large_letter_popup_inner")
        fun drawerSwipeTabs(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_swipe_tabs" else "drawer_swipe_tabs_inner")
        fun drawerFlickClose(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_flick_close" else "drawer_flick_close_inner")
        val THEMED_ICON_STYLE = stringPreferencesKey("themed_icon_style")
        val FORCE_THEMED_ICONS = booleanPreferencesKey("force_themed_icons")
        fun drawerHandleStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_handle_style" else "drawer_handle_style_inner")
        fun drawerCornerRadius(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_corner_radius" else "drawer_corner_radius_inner")
        fun azIndexPosition(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "az_index_position" else "az_index_position_inner")
        fun drawerBackgroundBlur(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_background_blur" else "drawer_background_blur_inner")
        fun drawerScrimOpacity(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_scrim_opacity" else "drawer_scrim_opacity_inner")
        fun glassBlurRadius(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "glass_blur_radius" else "glass_blur_radius_inner")
        fun glassDarkTint(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "glass_dark_tint" else "glass_dark_tint_inner")
        fun drawerStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_style" else "drawer_style_inner")
        fun drawerThemeTint(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "drawer_theme_tint" else "drawer_theme_tint_inner")
        fun drawerAccentColor(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_accent_color" else "drawer_accent_color_inner")
        fun drawerCustomColor(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "drawer_custom_color" else "drawer_custom_color_inner")
        fun drawerGlassEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_glass_enabled" else "drawer_glass_enabled_inner")
        fun drawerPanelOpacity(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "drawer_panel_opacity" else "drawer_panel_opacity_inner")
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

        fun showNotificationDots(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_notif_dots" else "show_notif_dots_inner")
        fun notificationDotCount(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "notif_dot_count" else "notif_dot_count_inner")
        fun notificationDotScale(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "notif_dot_scale" else "notif_dot_scale_inner")
        fun useThemedIcons(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "use_themed_icons" else "use_themed_icons_inner")
        fun iconPackPackage(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "icon_pack_package" else "icon_pack_package_inner")
        fun widgetTonalBackground(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "widget_tonal_background" else "widget_tonal_background_inner")
        fun notificationWidgetCountStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "notif_widget_count_style" else "notif_widget_count_style_inner")
        fun showWeather(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "show_weather" else "show_weather_inner")

        fun aquamorphicTouchEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "aquamorphic_touch_enabled" else "aquamorphic_touch_enabled_inner")
        fun foldTransitionStyle(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "fold_transition_style_outer" else "fold_transition_style_inner")
        fun foldTransitionSpeed(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "fold_transition_speed_outer" else "fold_transition_speed_inner")
        fun appLaunchZoomEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "app_launch_zoom_enabled" else "app_launch_zoom_enabled_inner")
        fun pageBounceEnabled(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "page_bounce_enabled" else "page_bounce_enabled_inner")
        fun iconSizeScale(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "icon_size_scale" else "icon_size_scale_inner")
        fun folderShape(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "folder_shape" else "folder_shape_inner")
        fun folderTheme(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "folder_theme" else "folder_theme_inner")
        fun folderPreview(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "folder_preview" else "folder_preview_inner")
        fun folderOpenColumns(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "folder_open_columns" else "folder_open_columns_inner")
        fun folderOpenSize(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "folder_open_size" else "folder_open_size_inner")
        fun folderTapFirstApp(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "folder_tap_first_app" else "folder_tap_first_app_inner")
        val CUSTOM_ICONS = stringPreferencesKey("custom_icons")
        fun popupTheme(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "popup_theme" else "popup_theme_inner")
        fun stackAutoRotateSeconds(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "stack_auto_rotate_seconds" else "stack_auto_rotate_seconds_inner")
        fun stackCornerRadius(type: ScreenType) = intPreferencesKey(if (type == ScreenType.OUTER) "stack_corner_radius" else "stack_corner_radius_inner")
        fun stackIndicator(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "stack_indicator" else "stack_indicator_inner")
        fun stackShowPill(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "stack_show_pill" else "stack_show_pill_inner")
        fun dotShape(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "dot_shape" else "dot_shape_inner")
        fun dotColor(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "dot_color" else "dot_color_inner")
        fun dotPosition(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "dot_position" else "dot_position_inner")
        fun homeWallpaperDim(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "home_wallpaper_dim" else "home_wallpaper_dim_inner")
        fun homeWallpaperBlur(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "home_wallpaper_blur" else "home_wallpaper_blur_inner")
        fun homeBackgroundImage(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "home_background_image" else "home_background_image_inner")
        fun iconSwipeUpAction(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "icon_swipe_up_action" else "icon_swipe_up_action_inner")
        fun newPageOnDrag(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "new_page_on_drag" else "new_page_on_drag_inner")
        fun autoRemoveEmptyPages(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "auto_remove_empty_pages" else "auto_remove_empty_pages_inner")
        fun drawerFolderTabs(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "drawer_folder_tabs" else "drawer_folder_tabs_inner")
        fun appFontFamily(type: ScreenType) = stringPreferencesKey(if (type == ScreenType.OUTER) "app_font_family" else "app_font_family_inner")
        fun appFontBold(type: ScreenType) = booleanPreferencesKey(if (type == ScreenType.OUTER) "app_font_bold" else "app_font_bold_inner")
        fun appFontSizeScale(type: ScreenType) = floatPreferencesKey(if (type == ScreenType.OUTER) "app_font_size_scale" else "app_font_size_scale_inner")
        val FLOATING_WINDOW_SIZE = stringPreferencesKey("floating_window_size")
        val SEARCH_SHORTCUTS = booleanPreferencesKey("search_shortcuts")
        val SEARCH_WEB = booleanPreferencesKey("search_web")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val APP_OPEN_ANIMATION = stringPreferencesKey("app_open_animation")
        val FOLDER_OPEN_ANIMATION = stringPreferencesKey("folder_open_animation")
        val PAGE_SCROLL_EFFECT = stringPreferencesKey("page_scroll_effect")
        val WIDGET_BACKGROUND_OPACITY = floatPreferencesKey("widget_background_opacity")
        val WEATHER_LOCATION = stringPreferencesKey("weather_location")
        val BATTERY_SAVER_MODE = stringPreferencesKey("battery_saver_mode")
        val CLOCK_WIDGET_STYLE = stringPreferencesKey("clock_widget_style")
        val CLOCK_WIDGET_DARK_TEXT = booleanPreferencesKey("clock_widget_dark_text")
        val CLOCK_WIDGET_GLASS_EFFECT = booleanPreferencesKey("clock_widget_glass_effect")
        val CLOCK_MINUTE_COLOR = stringPreferencesKey("clock_minute_color")
        val CLOCK_MINUTE_OPACITY = floatPreferencesKey("clock_minute_opacity")
        val WEATHER_WIDGET_ANIMATED = booleanPreferencesKey("weather_widget_animated")
        val TASKBAR_ENABLED = booleanPreferencesKey("taskbar_enabled")
        val TASKBAR_PINNED = stringPreferencesKey("taskbar_pinned")
        val TASKBAR_RECENTS = intPreferencesKey("taskbar_recents")
        val TASKBAR_ICON_SIZE = intPreferencesKey("taskbar_icon_size")
        val TASKBAR_SPACING = intPreferencesKey("taskbar_spacing")
        val TASKBAR_BOTTOM = intPreferencesKey("taskbar_bottom")
        val TASKBAR_THEME = stringPreferencesKey("taskbar_theme")
        val TASKBAR_OPACITY = floatPreferencesKey("taskbar_opacity")
        val TASKBAR_HANDLES = stringPreferencesKey("taskbar_handles")
        val TASKBAR_ON_HOME = booleanPreferencesKey("taskbar_on_home")
        val TASKBAR_KEEP_OPEN = booleanPreferencesKey("taskbar_keep_open")
        val TASKBAR_MEDIA = booleanPreferencesKey("taskbar_media")
        val TASKBAR_MEDIA_PAUSED = booleanPreferencesKey("taskbar_media_paused")
        val TASKBAR_MEDIA_POSITION = stringPreferencesKey("taskbar_media_position")
        val TASKBAR_MEDIA_TAP = stringPreferencesKey("taskbar_media_tap")
        val TASKBAR_MEDIA_CONTROLS = booleanPreferencesKey("taskbar_media_controls")
        val TASKBAR_MEDIA_SHAPE = stringPreferencesKey("taskbar_media_shape")
        val TASKBAR_MEDIA_BADGE = booleanPreferencesKey("taskbar_media_badge")
        val TASKBAR_SYSTEM_DRAG = booleanPreferencesKey("taskbar_system_drag")
        val TASKBAR_RECENTS_BUTTON = stringPreferencesKey("taskbar_recents_button")
        val TASKBAR_QUICK_SWITCH = booleanPreferencesKey("taskbar_quick_switch")
        val TASKBAR_SPLIT_CONTROLS = booleanPreferencesKey("taskbar_split_controls")
        val TASKBAR_SPLIT_SIDE = stringPreferencesKey("taskbar_split_side")
        val TASKBAR_THREE_APP = booleanPreferencesKey("taskbar_three_app")
        val SETTINGS_STYLE = stringPreferencesKey("settings_style")
        val MINUS_ONE_SCREEN = stringPreferencesKey("minus_one_screen")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val MOTO_WEATHER_TAP = booleanPreferencesKey("moto_weather_tap")
        val SHOW_PRIVATE_SPACE = booleanPreferencesKey("show_private_space")
        val POPUP_NOTIFICATIONS = booleanPreferencesKey("popup_notifications")
        val DOT_AVATARS = booleanPreferencesKey("dot_avatars")
        val LIVE_ACTIVITIES = booleanPreferencesKey("live_activities")
        val LIVE_ACTIVITIES_TASKBAR = booleanPreferencesKey("live_activities_taskbar")
        val LIVE_ACTIVITY_EFFECT = stringPreferencesKey("live_activity_effect")
        val SEARCH_ACTIONS = booleanPreferencesKey("search_actions")
        val RECENTS_ENABLED = booleanPreferencesKey("recents_enabled")
        val RECENTS_REPLACE_SYSTEM = booleanPreferencesKey("recents_replace_system")
        val NAV_HANDLE_SEARCH = booleanPreferencesKey("nav_handle_search")
        val RECENTS_COLUMNS_INNER = intPreferencesKey("recents_columns_inner")
        val RECENTS_COLUMNS_OUTER = intPreferencesKey("recents_columns_outer")
        val RECENTS_MAX_APPS = intPreferencesKey("recents_max_apps")
        val RECENTS_CARD_HEIGHT = intPreferencesKey("recents_card_height")
        val RECENTS_CORNER = intPreferencesKey("recents_corner")
        val RECENTS_BACKDROP = floatPreferencesKey("recents_backdrop")
        val RECENTS_SORT = stringPreferencesKey("recents_sort")
        val RECENTS_SHOW_TIME = booleanPreferencesKey("recents_show_time")
        val RECENTS_SHOW_ACTIONS = booleanPreferencesKey("recents_show_actions")
        val RECENTS_CONFIRM_CLOSE_ALL = booleanPreferencesKey("recents_confirm_close_all")
        val RECENTS_THIRD_IN_SPLIT = booleanPreferencesKey("recents_third_in_split")
        val RECENTS_THUMBNAILS = booleanPreferencesKey("recents_thumbnails")
        val RECENTS_ROOT_CLOSE = booleanPreferencesKey("recents_root_close")
        val TASKBAR_RUNNING_APPS = booleanPreferencesKey("taskbar_running_apps")
        val TASKBAR_RUNNING_DOTS = booleanPreferencesKey("taskbar_running_dots")
        val RECENTS_ENTER = stringPreferencesKey("recents_enter")
        val RECENTS_SELECT = stringPreferencesKey("recents_select")
        val RECENTS_ANIM_SPEED = floatPreferencesKey("recents_anim_speed")
        val ROOT_ENABLED = booleanPreferencesKey("root_enabled")
        val RECENTS_THEME = stringPreferencesKey("recents_theme")
        val RECENTS_PRESS = stringPreferencesKey("recents_press")
        val RECENTS_GLASS = booleanPreferencesKey("recents_glass")
        val RECENTS_GLASS_BLUR = intPreferencesKey("recents_glass_blur")
        val RECENTS_GLASS_TINT = floatPreferencesKey("recents_glass_tint")
        val RECENTS_DRAG_DROP = booleanPreferencesKey("recents_drag_drop")
        val RECENTS_BTN_CLOSE = booleanPreferencesKey("recents_btn_close")
        val RECENTS_BTN_SPLIT = booleanPreferencesKey("recents_btn_split")
        val RECENTS_BTN_POPUP = booleanPreferencesKey("recents_btn_popup")
        val RECENTS_BTN_UNSPLIT = booleanPreferencesKey("recents_btn_unsplit")
        val RECENTS_BTN_SPLIT_VIEW = booleanPreferencesKey("recents_btn_split_view")
        val RECENTS_BTN_CLOSE_ALL = booleanPreferencesKey("recents_btn_close_all")
        val RECENTS_SWIPE_UP = stringPreferencesKey("recents_swipe_up")
        val RECENTS_SWIPE_DOWN = stringPreferencesKey("recents_swipe_down")
        val RECENTS_LONG_PRESS = stringPreferencesKey("recents_long_press")
        val RECENTS_DOUBLE_TAP = stringPreferencesKey("recents_double_tap")
        val RECENTS_PINNED = stringPreferencesKey("recents_pinned")
        val RECENTS_BTN_PIN = booleanPreferencesKey("recents_btn_pin")
        val RECENTS_PIN_KEEP_ALIVE = booleanPreferencesKey("recents_pin_keep_alive")
        val RECENTS_PIN_EXEMPT = stringPreferencesKey("recents_pin_exempt")
        val TASKBAR_HIDDEN_APPS = stringPreferencesKey("taskbar_hidden_apps")
        val TASKBAR_ALWAYS_APPS = stringPreferencesKey("taskbar_always_apps")
        val LIQUID_GLASS_EVERYWHERE = booleanPreferencesKey("liquid_glass_everywhere")
        val LIQUID_GLASS_LEVEL = floatPreferencesKey("liquid_glass_level")
        val RECENTS_ATTACHED_SIDE = stringPreferencesKey("recents_attached_side")
        val RECENTS_ATTACHED_PEEK = floatPreferencesKey("recents_attached_peek")
        val TASKBAR_HANDLE_HINT = booleanPreferencesKey("taskbar_handle_hint")
        val TASKBAR_SOURCE = stringPreferencesKey("taskbar_source")
        val SYSTEM_TASKBAR_MODE = intPreferencesKey("system_taskbar_mode")
        val ROOT_LOCK_MODE = stringPreferencesKey("root_lock_mode")
        val ROOT_RECENTS_MODE = stringPreferencesKey("root_recents_mode")
        val ROOT_SHADE_MODE = stringPreferencesKey("root_shade_mode")
        val ROOT_KEEP_DUMP = booleanPreferencesKey("root_keep_dump")
        val ROOT_KEEP_USAGE = booleanPreferencesKey("root_keep_usage")
        val ROOT_KEEP_SECURE = booleanPreferencesKey("root_keep_secure")
        val ROOT_KEEP_NOTIFICATIONS = booleanPreferencesKey("root_keep_notifications")
        val ROOT_KEEP_ACCESSIBILITY = booleanPreferencesKey("root_keep_accessibility")
        val ROOT_KEEP_WIDGETS = booleanPreferencesKey("root_keep_widgets")
        val ICON_NUDGE = booleanPreferencesKey("icon_nudge")
        val UNLOCK_TRANSITION = booleanPreferencesKey("unlock_transition")
        val ICON_NUDGE_TASKBAR = booleanPreferencesKey("icon_nudge_taskbar")
        val ICON_NUDGE_EFFECT = stringPreferencesKey("icon_nudge_effect")
        val ICON_NUDGE_SOFTNESS = floatPreferencesKey("icon_nudge_softness")
        val LIVE_HINGE_END_ANGLE = intPreferencesKey("live_hinge_end_angle")
        val LIVE_HINGE_SMOOTHING = intPreferencesKey("live_hinge_smoothing")
        val LIVE_HINGE_STYLE = stringPreferencesKey("live_hinge_style")
        val LIVE_HINGE_OUTER_STYLE = stringPreferencesKey("live_hinge_outer_style")
        val LIVE_HINGE_BLUR = intPreferencesKey("live_hinge_blur")
        val LIVE_HINGE_FROST = floatPreferencesKey("live_hinge_frost")
        val WIDGET_LOOKS = stringPreferencesKey("widget_looks")
        val MEDIA_WIDGET_THEME = stringPreferencesKey("media_widget_theme")
        val APPS_WIDGET_THEME = stringPreferencesKey("apps_widget_theme")
        val STATS_WIDGET_THEME = stringPreferencesKey("stats_widget_theme")
        val NOTE_WIDGET_THEME = stringPreferencesKey("note_widget_theme")
        val MEDIA_WIDGET_ARTWORK = booleanPreferencesKey("media_widget_artwork")
        val APPS_WIDGET_LABELS = booleanPreferencesKey("apps_widget_labels")
        val STATS_SHOW_BATTERY = booleanPreferencesKey("stats_show_battery")
        val STATS_SHOW_STORAGE = booleanPreferencesKey("stats_show_storage")
        val STATS_SHOW_MEMORY = booleanPreferencesKey("stats_show_memory")
        val STATS_SHOW_BLUETOOTH = booleanPreferencesKey("stats_show_bluetooth")
        val NOTE_WIDGET_TEXT_SIZE = stringPreferencesKey("note_widget_text_size")
        val NOTE_WIDGET_TEXT = stringPreferencesKey("note_widget_text")

        /** Set once the inner profile stopped inheriting from the outer one (see splitProfilesOnce). */
        val PROFILES_SPLIT = booleanPreferencesKey("profiles_split_done")
        val HALF_OPENED_ENABLED = booleanPreferencesKey("half_opened_mode_enabled")
        val HINGE_TILT = booleanPreferencesKey("hinge_tilt")
        val TASKBAR_HIDE_FULL_SCREEN = booleanPreferencesKey("taskbar_hide_full_screen")
        val SPLIT_LAUNCH_ENABLED = booleanPreferencesKey("split_launch_enabled")
        val FLOATING_LAUNCH_ENABLED = booleanPreferencesKey("floating_launch_enabled")
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
        private const val OPAQUE = 0xFF000000.toInt()
        private const val INNER_SUFFIX = "_inner"

        /** Outer-profile key names the inner profile used to fall back to (see splitProfilesOnce). */
        internal val INHERITED_KEY_NAMES = listOf(
            "show_drawer_search", "show_dock_labels", "show_home_labels", "show_drawer_labels",
            "show_frequent_apps", "parallax_wallpaper", "dock_opacity", "show_page_indicator",
            "left_swipe_app_key", "drawer_opens_at_top", "app_label_scale", "two_line_home_labels",
            "two_line_drawer_labels", "app_label_color", "show_status_bar", "hide_system_status_bar",
            "status_bar_scrim", "show_notif_dots", "notif_dot_count", "notif_dot_scale",
            "use_themed_icons", "icon_pack_package", "widget_tonal_background",
            "notif_widget_count_style", "show_weather",
        )

        /** Base names of every per-display setting [copyProfile] transfers (outer name; the inner
         *  key appends "_inner"). Layout data — dock favourites and home pages — is not listed. */
        internal val PROFILE_KEY_NAMES = INHERITED_KEY_NAMES + listOf(
            // The home grid size is left out on purpose: shrinking it needs a reflow of the placed
            // icons, which only the settings stepper performs.
            "dock_enabled", "dock_columns", "drawer_columns", "drawer_rows",
            "workspace_density", "amoled_dark", "allow_landscape", "swipe_down_action", "double_tap_action",
            "swipe_up_action", "drawer_search_position", "drawer_index_bar_enabled", "az_index_position", "az_index_scale", "drawer_corner_radius", "drawer_handle_style", "drawer_bottom_space", "drawer_letter_headers", "drawer_large_letter_popup", "drawer_swipe_tabs", "drawer_flick_close",
            "drawer_background_blur", "drawer_scrim_opacity", "glass_blur_radius", "glass_dark_tint",
            "drawer_style", "drawer_theme_tint", "drawer_accent_color", "drawer_custom_color",
            "drawer_glass_enabled", "drawer_panel_opacity", "drawer_height_fraction", "drawer_width_fraction",
            "drawer_alignment", "drawer_layout_mode", "dpi_workspace", "dpi_app_drawer", "dpi_settings",
            "right_swipe_app_key", "double_tap_to_lock", "aquamorphic_touch_enabled",
            "app_launch_zoom_enabled", "page_bounce_enabled", "fold_transition_style",
            "icon_size_scale", "folder_shape", "folder_theme", "folder_preview", "folder_open_columns",
            "folder_open_size", "folder_tap_first_app",
            "popup_theme", "new_page_on_drag", "stack_auto_rotate_seconds", "stack_corner_radius", "stack_indicator", "stack_show_pill", "dot_shape", "dot_color", "dot_position", "home_wallpaper_dim", "home_wallpaper_blur", "icon_swipe_up_action", "auto_remove_empty_pages", "drawer_folder_tabs",
        )

        /** The stored key name of [base] for [type]. The fold transition is the one key whose outer
         *  name carries a suffix too. */
        internal fun profileKeyName(base: String, type: ScreenType): String = when {
            base == "fold_transition_style" -> if (type == ScreenType.OUTER) "fold_transition_style_outer" else "fold_transition_style_inner"
            type == ScreenType.OUTER -> base
            else -> base + INNER_SUFFIX
        }

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
    }
}
