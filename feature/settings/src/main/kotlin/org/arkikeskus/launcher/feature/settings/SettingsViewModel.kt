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
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Keys currently hidden from the drawer. */
    val hiddenKeys: StateFlow<Set<String>> = repository.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val appPairs: StateFlow<List<AppPair>> = appRepository.appPairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAppPair(pair: AppPair) = appRepository.saveAppPair(pair)
    /** Deletes the pair and its shortcuts on both home screens (they would be dead cells). */
    fun deleteAppPair(id: String) {
        appRepository.deleteAppPair(id)
        update { homeLayoutRepository.removeAppPairShortcuts(id) }
    }

    /** Puts a shortcut for [pair] on the home screen of the display profile being edited. */
    fun addAppPairToHome(pair: AppPair) = update {
        val type = _screenType.value
        val s = repository.settings(type).first()
        homeLayoutRepository.addShortcut(
            pair.app1Package,
            org.arkikeskus.launcher.data.local.HomeItemEntity.appPairShortcutId(pair.id),
            pair.app1UserSerial,
            s.homeColumns, s.homeRows, type,
        )
    }
    fun launchAppPair(pair: AppPair) = appRepository.launchAppPair(pair)

    fun setFoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldAction(value, screenType) }
    fun setUnfoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setUnfoldAction(value, screenType) }
    fun setOuterWallpaperScale(value: Float) = update { repository.setOuterWallpaperScale(value) }
    fun setInnerWallpaperScale(value: Float) = update { repository.setInnerWallpaperScale(value) }
    fun setFoldTransitionStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldTransitionStyle(value, screenType) }
    fun setAquamorphicTouchEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAquamorphicTouchEnabled(value, screenType) }
    fun setAppLaunchZoomEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAppLaunchZoomEnabled(value, screenType) }
    fun setPageBounceEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setPageBounceEnabled(value, screenType) }
    fun setIconSizeScale(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setIconSizeScale(value, screenType) }
    fun setFolderShape(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFolderShape(value, screenType) }
    fun setFolderTheme(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFolderTheme(value, screenType) }
    fun setFolderPreview(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFolderPreview(value, screenType) }
    fun setFolderOpenColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setFolderOpenColumns(value, screenType) }
    fun setFolderOpenSize(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFolderOpenSize(value, screenType) }
    fun setFolderTapFirstApp(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setFolderTapFirstApp(value, screenType) }
    fun setPopupTheme(value: String, screenType: ScreenType = _screenType.value) = update { repository.setPopupTheme(value, screenType) }
    fun setStackAutoRotateSeconds(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setStackAutoRotateSeconds(value, screenType) }
    fun setStackCornerRadius(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setStackCornerRadius(value, screenType) }
    fun setStackIndicator(value: String, screenType: ScreenType = _screenType.value) = update { repository.setStackIndicator(value, screenType) }
    fun setStackShowPill(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setStackShowPill(value, screenType) }
    fun setDotShape(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDotShape(value, screenType) }
    fun setDotColor(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDotColor(value, screenType) }
    fun setDotPosition(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDotPosition(value, screenType) }
    fun setHomeWallpaperDim(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setHomeWallpaperDim(value, screenType) }
    fun setHomeWallpaperBlur(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setHomeWallpaperBlur(value, screenType) }
    fun setIconSwipeUpAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setIconSwipeUpAction(value, screenType) }
    fun setNewPageOnDrag(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setNewPageOnDrag(value, screenType) }
    fun setAutoRemoveEmptyPages(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAutoRemoveEmptyPages(value, screenType) }
    fun setDrawerFolderTabs(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerFolderTabs(value, screenType) }
    fun setHomeBackgroundImage(value: String, screenType: ScreenType = _screenType.value) = update { repository.setHomeBackgroundImage(value, screenType) }
    fun setFloatingWindowSize(value: String) = update { repository.setFloatingWindowSize(value) }
    fun setSearchShortcuts(value: Boolean) = update { repository.setSearchShortcuts(value) }
    fun setSearchWeb(value: Boolean) = update { repository.setSearchWeb(value) }
    fun setAnimationSpeed(value: Float) = update { repository.setAnimationSpeed(value) }
    fun setAppOpenAnimation(value: String) = update { repository.setAppOpenAnimation(value) }
    fun setFolderOpenAnimation(value: String) = update { repository.setFolderOpenAnimation(value) }
    fun setPageScrollEffect(value: String) = update { repository.setPageScrollEffect(value) }
    fun setWidgetBackgroundOpacity(value: Float) = update { repository.setWidgetBackgroundOpacity(value) }
    fun setBatterySaverMode(value: String) = update { repository.setBatterySaverMode(value) }
    fun setClockWidgetStyle(value: String) = update { repository.setClockWidgetStyle(value) }
    fun setClockWidgetDarkText(value: Boolean) = update { repository.setClockWidgetDarkText(value) }
    fun setClockMinuteColor(value: String) = update { repository.setClockMinuteColor(value) }
    fun setClockMinuteOpacity(value: Float) = update { repository.setClockMinuteOpacity(value) }
    fun setClockWidgetGlassEffect(value: Boolean) = update { repository.setClockWidgetGlassEffect(value) }
    fun setWeatherWidgetAnimated(value: Boolean) = update { repository.setWeatherWidgetAnimated(value) }
    fun setTaskbarEnabled(value: Boolean) = update { repository.setTaskbarEnabled(value) }
    fun setTaskbarPinned(keys: List<String>?) = update { repository.setTaskbarPinned(keys) }
    fun setTaskbarRecentCount(value: Int) = update { repository.setTaskbarRecentCount(value) }
    fun setTaskbarIconSize(value: Int) = update { repository.setTaskbarIconSize(value) }
    fun setTaskbarIconSpacing(value: Int) = update { repository.setTaskbarIconSpacing(value) }
    fun setTaskbarBottomOffset(value: Int) = update { repository.setTaskbarBottomOffset(value) }
    fun setTaskbarTheme(value: String) = update { repository.setTaskbarTheme(value) }
    fun setTaskbarOpacity(value: Float) = update { repository.setTaskbarOpacity(value) }
    fun setTaskbarHandles(value: String) = update { repository.setTaskbarHandles(value) }
    fun setTaskbarOnHome(value: Boolean) = update { repository.setTaskbarOnHome(value) }
    fun setTaskbarKeepOpen(value: Boolean) = update { repository.setTaskbarKeepOpen(value) }
    fun setTaskbarMediaEnabled(value: Boolean) = update { repository.setTaskbarMediaEnabled(value) }
    fun setTaskbarMediaShowPaused(value: Boolean) = update { repository.setTaskbarMediaShowPaused(value) }
    fun setTaskbarMediaPosition(value: String) = update { repository.setTaskbarMediaPosition(value) }
    fun setTaskbarMediaTap(value: String) = update { repository.setTaskbarMediaTap(value) }
    fun setTaskbarMediaControls(value: Boolean) = update { repository.setTaskbarMediaControls(value) }
    fun setTaskbarMediaShape(value: String) = update { repository.setTaskbarMediaShape(value) }
    fun setTaskbarMediaBadge(value: Boolean) = update { repository.setTaskbarMediaBadge(value) }
    fun setTaskbarSystemDrag(value: Boolean) = update { repository.setTaskbarSystemDrag(value) }
    fun setTaskbarRecentsButton(value: String) = update { repository.setTaskbarRecentsButton(value) }
    fun setTaskbarQuickSwitch(value: Boolean) = update { repository.setTaskbarQuickSwitch(value) }
    fun setTaskbarSplitControls(value: Boolean) = update { repository.setTaskbarSplitControls(value) }
    fun setTaskbarSplitSide(value: String) = update { repository.setTaskbarSplitSide(value) }
    fun setTaskbarAppPrediction(value: Boolean) = update { repository.setTaskbarAppPrediction(value) }
    fun setTaskbarThreeApp(value: Boolean) = update { repository.setTaskbarThreeApp(value) }
    fun setSettingsStyle(value: String) = update { repository.setSettingsStyle(value) }
    fun setIconShape(value: String) = update { repository.setIconShape(value) }
    fun setMotoWeatherTap(value: Boolean) = update { repository.setMotoWeatherTap(value) }
    fun setShowPrivateSpace(value: Boolean) = update { repository.setShowPrivateSpace(value) }
    fun setLiveHingeEndAngle(value: Int) = update { repository.setLiveHingeEndAngle(value) }
    fun setLiveHingeSmoothing(value: Int) = update { repository.setLiveHingeSmoothing(value) }
    fun setLiveHingeGlass(value: Boolean) = update { repository.setLiveHingeGlass(value) }
    fun setLiveHingeStyle(value: String) = update { repository.setLiveHingeStyle(value) }
    fun setLiveHingeSpeed(value: Float) = update { repository.setLiveHingeSpeed(value) }
    fun setLiveHingeBlur(value: Int) = update { repository.setLiveHingeBlur(value) }
    fun setLiveHingeFrost(value: Float) = update { repository.setLiveHingeFrost(value) }
    fun setMediaWidgetTheme(value: String) = update { repository.setMediaWidgetTheme(value) }
    fun setAppsWidgetTheme(value: String) = update { repository.setAppsWidgetTheme(value) }
    fun setStatsWidgetTheme(value: String) = update { repository.setStatsWidgetTheme(value) }
    fun setNoteWidgetTheme(value: String) = update { repository.setNoteWidgetTheme(value) }
    fun setMediaWidgetArtwork(value: Boolean) = update { repository.setMediaWidgetArtwork(value) }
    fun setAppsWidgetLabels(value: Boolean) = update { repository.setAppsWidgetLabels(value) }
    fun setStatsShowBattery(value: Boolean) = update { repository.setStatsShowBattery(value) }
    fun setStatsShowStorage(value: Boolean) = update { repository.setStatsShowStorage(value) }
    fun setStatsShowMemory(value: Boolean) = update { repository.setStatsShowMemory(value) }
    fun setStatsShowBluetooth(value: Boolean) = update { repository.setStatsShowBluetooth(value) }
    fun setNoteWidgetTextSize(value: String) = update { repository.setNoteWidgetTextSize(value) }
    fun applyThemePreset(preset: String, screenType: ScreenType = _screenType.value) = update { repository.applyThemePreset(preset, screenType) }

    fun setAppHidden(key: String, hidden: Boolean) = update { repository.setAppHidden(key, hidden) }

    /** Creates a new empty folder in the app drawer (named [name]); it's filled from the drawer. */
    fun createDrawerFolder(name: String) = update { repository.createDrawerFolder(name) }

    fun setDpiWorkspace(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiWorkspace(value, screenType) }
    fun setDpiAppDrawer(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiAppDrawer(value, screenType) }
    fun setDpiSettings(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDpiSettings(value, screenType) }
    fun setAppFontFamily(value: String, screenType: ScreenType = _screenType.value) = update { repository.setAppFontFamily(value, screenType) }
    fun setAppFontBold(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAppFontBold(value, screenType) }
    fun setAppFontSizeScale(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setAppFontSizeScale(value, screenType) }
    fun setInnerDualPageWorkspace(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setInnerDualPageWorkspace(value, screenType) }
    fun setInnerSidebarAppDrawer(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setInnerSidebarAppDrawer(value, screenType) }
    fun setInnerDockAlignment(value: String, screenType: ScreenType = _screenType.value) = update { repository.setInnerDockAlignment(value, screenType) }
    fun setAllowLandscape(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAllowLandscape(value, screenType) }
    fun setSwipeDownAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setSwipeDownAction(value, screenType) }
    fun setDoubleTapAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDoubleTapAction(value, screenType) }
    fun setSwipeUpAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setSwipeUpAction(value, screenType) }
    fun setHalfOpenedModeEnabled(value: Boolean) = update { repository.setHalfOpenedModeEnabled(value) }
    fun setSplitLaunchEnabled(value: Boolean) = update { repository.setSplitLaunchEnabled(value) }
    fun setFloatingLaunchEnabled(value: Boolean) = update { repository.setFloatingLaunchEnabled(value) }
    fun setAmoledDark(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAmoledDark(value, screenType) }
    fun setDockEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDockEnabled(value, screenType) }
    fun setDockColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDockColumns(value, screenType) }
    fun setDrawerColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerColumns(value, screenType) }
    fun setDrawerRows(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerRows(value, screenType) }
    fun setShowDrawerSearch(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowDrawerSearch(value, screenType) }
    fun setShowFrequentApps(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowFrequentApps(value, screenType) }
    fun setDrawerSearchPosition(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerSearchPosition(value, screenType) }
    fun setDrawerIndexBarEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerIndexBarEnabled(value, screenType) }
    fun setAzIndexScale(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setAzIndexScale(value, screenType) }
    fun setDrawerBottomSpace(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerBottomSpace(value, screenType) }
    fun setDrawerLetterHeaders(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerLetterHeaders(value, screenType) }
    fun setDrawerLargeLetterPopup(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerLargeLetterPopup(value, screenType) }
    fun setDrawerSwipeTabs(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerSwipeTabs(value, screenType) }
    fun setDrawerFlickClose(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerFlickClose(value, screenType) }
    fun setThemedIconStyle(value: String) = update { repository.setThemedIconStyle(value) }
    fun setForceThemedIcons(value: Boolean) = update { repository.setForceThemedIcons(value) }
    fun setDrawerHandleStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerHandleStyle(value, screenType) }
    fun setDrawerCornerRadius(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerCornerRadius(value, screenType) }
    fun setAzIndexPosition(value: String, screenType: ScreenType = _screenType.value) = update { repository.setAzIndexPosition(value, screenType) }
    fun setDrawerBackgroundBlur(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerBackgroundBlur(value, screenType) }
    fun setDrawerScrimOpacity(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerScrimOpacity(value, screenType) }
    fun setGlassBlurRadius(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setGlassBlurRadius(value, screenType) }
    fun setGlassDarkTint(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setGlassDarkTint(value, screenType) }
    fun setDrawerStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerStyle(value, screenType) }
    fun setDrawerThemeTint(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerThemeTint(value, screenType) }
    fun setDrawerAccentColor(argb: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerAccentColor(argb, screenType) }
    fun setDrawerCustomColor(argb: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerCustomColor(argb, screenType) }
    fun setDrawerGlassEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update {
        repository.setDrawerGlassEnabled(value, screenType)
        // Glass without a blurred backdrop is hard to read, so switching it on from a profile that
        // has no blur yet starts with a sensible one. An existing blur value is left alone.
        if (value && repository.settings(screenType).first().drawerBackgroundBlur <= 0f) {
            repository.setDrawerBackgroundBlur(DEFAULT_GLASS_BLUR, screenType)
        }
    }
    fun setDrawerPanelOpacity(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerPanelOpacity(value, screenType) }

    /** Copies the per-display settings and home layout of [from] onto [to] (see SettingsRepository.copyProfile). */
    fun copyProfile(from: ScreenType, to: ScreenType, onComplete: () -> Unit = {}) = viewModelScope.launch {
        repository.copyProfile(from, to)
        homeLayoutRepository.copyHomeLayout(from, to)
        repository.copyDockFavorites(from, to)
        onComplete()
    }

    fun setDrawerHeightFraction(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerHeightFraction(value, screenType) }
    fun setDrawerWidthFraction(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setDrawerWidthFraction(value, screenType) }
    fun setDrawerAlignment(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerAlignment(value, screenType) }
    fun setDrawerLayoutMode(value: String, screenType: ScreenType = _screenType.value) = update { repository.setDrawerLayoutMode(value, screenType) }
    fun setMinusOneScreen(value: String) = update { repository.setMinusOneScreen(value) }
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
    fun setShowNotificationDots(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowNotificationDots(value, screenType) }
    fun setNotificationDotCount(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setNotificationDotCount(value, screenType) }
    fun setNotificationDotScale(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setNotificationDotScale(value, screenType) }
    fun setUseThemedIcons(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setUseThemedIcons(value, screenType) }
    fun setIconPack(pkg: String, screenType: ScreenType = _screenType.value) = update { repository.setIconPackPackage(pkg, screenType) }
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
    fun setShowWeather(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowWeather(value, screenType) }
    fun setWidgetTonalBackground(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setWidgetTonalBackground(value, screenType) }
    fun setNotificationWidgetCountStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setNotificationWidgetCountStyle(value, screenType) }
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

    private companion object {
        const val DEFAULT_GLASS_BLUR = 18f
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
