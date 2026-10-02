package org.arkikeskus.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.data.IconPackRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    private val iconPackRepository: IconPackRepository,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _screenType = MutableStateFlow(ScreenType.OUTER)
    val currentScreenType: StateFlow<ScreenType> = _screenType.asStateFlow()

    fun setScreenType(type: ScreenType) {
        _screenType.value = type
    }

    /** Updates the target screen type to match the active physical screen (`LocalScreenType.current`). */
    fun initScreenType(type: ScreenType) {
        setScreenType(type)
    }

    val settings: StateFlow<LauncherSettings> = _screenType.flatMapLatest { screenType ->
        repository.settings(screenType)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherSettings(),
    )

    /** Returns a flow of [LauncherSettings] for the specified physical screen type. */
    fun settingsForScreen(screenType: ScreenType): Flow<LauncherSettings> =
        repository.settings(screenType)

    /** Installed icon packs (queried off-main once), for the picker. */
    val iconPacks: StateFlow<List<IconPackInfo>> =
        flow { emit(iconPackRepository.installedPacks()) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All launchable apps (sorted), for the hidden-apps manager list. */
    val apps: StateFlow<List<AppItem>> = appRepository.apps
        .map { list -> list.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Keys currently hidden from the drawer. */
    val hiddenKeys: StateFlow<Set<String>> = repository.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val appPairs: StateFlow<List<AppPair>> = appRepository.appPairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAppPair(pair: AppPair) = appRepository.saveAppPair(pair)
    fun deleteAppPair(id: String) = appRepository.deleteAppPair(id)
    fun launchAppPair(pair: AppPair) = appRepository.launchAppPair(pair)

    fun setFoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldAction(value, screenType) }
    fun setUnfoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setUnfoldAction(value, screenType) }
    fun setInnerTaskbarEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setInnerTaskbarEnabled(value, screenType) }
    fun setOuterWallpaperScale(value: Float) = update { repository.setOuterWallpaperScale(value) }
    fun setInnerWallpaperScale(value: Float) = update { repository.setInnerWallpaperScale(value) }
    fun setFoldTransitionStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldTransitionStyle(value, screenType) }
    fun setAquamorphicTouchEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAquamorphicTouchEnabled(value, screenType) }
    fun setAppLaunchZoomEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAppLaunchZoomEnabled(value, screenType) }
    fun setPageBounceEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setPageBounceEnabled(value, screenType) }

    fun setAppHidden(key: String, hidden: Boolean) = update { repository.setAppHidden(key, hidden) }

    /** Creates a new empty folder in the app drawer (named [name]); it's filled from the drawer. */
    fun createDrawerFolder(name: String) = update { repository.createDrawerFolder(name) }

    fun setWorkspaceDensity(value: String, screenType: ScreenType = _screenType.value) = update { repository.setWorkspaceDensity(value, screenType) }
    fun setDpiWorkspace(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiWorkspace(value, screenType) }
    fun setDpiAppDrawer(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiAppDrawer(value, screenType) }
    fun setDpiSettings(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiSettings(value, screenType) }
    fun setInnerDualPageWorkspace(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setInnerDualPageWorkspace(value, screenType) }
    fun setInnerSidebarAppDrawer(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setInnerSidebarAppDrawer(value, screenType) }
    fun setInnerDockAlignment(value: String, screenType: ScreenType = _screenType.value) = update { repository.setInnerDockAlignment(value, screenType) }
    fun setAllowLandscape(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAllowLandscape(value, screenType) }
    fun setSwipeDownAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setSwipeDownAction(value, screenType) }
    fun setDoubleTapAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDoubleTapAction(value, screenType) }
    fun setSwipeUpAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setSwipeUpAction(value, screenType) }
    fun setHalfOpenedModeEnabled(value: Boolean) = update { repository.setHalfOpenedModeEnabled(value) }
    fun setAmoledDark(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAmoledDark(value, screenType) }
    fun setDockEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDockEnabled(value, screenType) }
    fun setDockColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDockColumns(value, screenType) }
    fun setDrawerColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerColumns(value, screenType) }
    fun setDrawerRows(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerRows(value, screenType) }
    fun setShowDrawerSearch(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowDrawerSearch(value, screenType) }
    fun setShowFrequentApps(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowFrequentApps(value, screenType) }
    fun setDrawerSearchPosition(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerSearchPosition(value, screenType) }
    fun setDrawerIndexBarEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerIndexBarEnabled(value, screenType) }
    fun setAzIndexPosition(value: String, screenType: ScreenType = _screenType.value) = update { repository.setAzIndexPosition(value, screenType) }
    fun setDrawerBackgroundBlur(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerBackgroundBlur(value, screenType) }
    fun setDrawerScrimOpacity(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerScrimOpacity(value, screenType) }
    fun setGlassBlurRadius(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setGlassBlurRadius(value, screenType) }
    fun setGlassDarkTint(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setGlassDarkTint(value, screenType) }
    fun setDrawerStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerStyle(value, screenType) }
    fun setDrawerThemeTint(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerThemeTint(value, screenType) }
    fun setDrawerAccentColor(argb: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerAccentColor(argb, screenType) }
    fun setDrawerHeightFraction(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerHeightFraction(value, screenType) }
    fun setDrawerWidthFraction(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerWidthFraction(value, screenType) }
    fun setDrawerAlignment(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerAlignment(value, screenType) }
    fun setDrawerLayoutMode(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerLayoutMode(value, screenType) }
    fun setParallaxWallpaper(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setParallaxWallpaper(value, screenType) }
    fun setDrawerOpensAtTop(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerOpensAtTop(value, screenType) }
    fun setHomeColumns(value: Int, screenType: ScreenType = _screenType.value) = viewModelScope.launch {
        // Shrinking the grid can leave shortcuts at an out-of-range cellX; repack them back on-screen
        // before the new (smaller) column count reaches the home screen, so nothing draws off-grid.
        val s = repository.settings(screenType).first()
        if (value < s.homeColumns) homeLayoutRepository.reflow(value, s.homeRows, screenType)
        repository.setHomeColumns(value, screenType)
    }

    fun setHomeRows(value: Int, screenType: ScreenType = _screenType.value) = viewModelScope.launch {
        // Same off-grid guard as the column stepper, on the vertical axis.
        val s = repository.settings(screenType).first()
        if (value < s.homeRows) homeLayoutRepository.reflow(s.homeColumns, value, screenType)
        repository.setHomeRows(value, screenType)
    }
    fun setShowDockLabels(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowDockLabels(value, screenType) }
    fun setShowHomeLabels(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowHomeLabels(value, screenType) }
    fun setShowDrawerLabels(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowDrawerLabels(value, screenType) }
    fun setDockBackgroundOpacity(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDockBackgroundOpacity(value, screenType) }
    fun setShowPageIndicator(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowPageIndicator(value, screenType) }
    fun setShowNotificationDots(value: Boolean) = update { repository.setShowNotificationDots(value) }
    fun setNotificationDotCount(value: Boolean) = update { repository.setNotificationDotCount(value) }
    fun setNotificationDotScale(value: Float) = update { repository.setNotificationDotScale(value) }
    fun setUseThemedIcons(value: Boolean) = update { repository.setUseThemedIcons(value) }
    fun setIconPack(pkg: String) = update { repository.setIconPackPackage(pkg) }
    fun setSearchContacts(value: Boolean) = update { repository.setSearchContacts(value) }

    /** Sets (or clears, for null) the app launched by the left-edge home swipe. */
    fun setLeftSwipeAppKey(key: String?, screenType: ScreenType = _screenType.value) = update { repository.setLeftSwipeAppKey(key, screenType) }

    /** Sets (or clears, for null) the app launched by the right-edge home swipe. */
    fun setRightSwipeAppKey(key: String?, screenType: ScreenType = _screenType.value) = update { repository.setRightSwipeAppKey(key, screenType) }

    fun setDesktopLocked(value: Boolean) = update { repository.setDesktopLocked(value) }
    fun setDoubleTapToLock(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDoubleTapToLock(value, screenType) }

    fun setAppLabelTextScale(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setAppLabelTextScale(value, screenType) }
    fun setAppLabelColor(argb: Int, screenType: ScreenType = _screenType.value) = update { repository.setAppLabelColor(argb, screenType) }
    fun setTwoLineHomeLabels(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setTwoLineHomeLabels(value, screenType) }
    fun setTwoLineDrawerLabels(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setTwoLineDrawerLabels(value, screenType) }
    fun setShowStatusBar(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowStatusBar(value, screenType) }
    fun setShowWeather(value: Boolean) = update { repository.setShowWeather(value) }
    fun setWidgetTonalBackground(value: Boolean) = update { repository.setWidgetTonalBackground(value) }
    fun setNotificationWidgetCountStyle(value: String) = update { repository.setNotificationWidgetCountStyle(value) }
    fun setPeoplePrivacy(value: String) = update { repository.setPeoplePrivacy(value) }
    fun setPeopleShowApps(value: Boolean) = update { repository.setPeopleShowApps(value) }
    fun setPeopleBatchEnabled(value: Boolean) = update { repository.setPeopleBatchEnabled(value) }
    fun setPeopleBatchTimes(value: String) = update { repository.setPeopleBatchTimes(value) }
    fun setHideSystemStatusBar(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setHideSystemStatusBar(value, screenType) }
    fun setStatusBarScrimOpacity(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setStatusBarScrimOpacity(value, screenType) }

    /** Flushes DataStore changes to disk, invalidates caches, re-emits settings, and notifies [onComplete]. */
    fun applyAllSettings(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.flushDataStore()
            appRepository.invalidateCaches()
            _screenType.value = _screenType.value
            onComplete()
        }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
