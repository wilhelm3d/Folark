@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arkikeskus.launcher.feature.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
import org.arkikeskus.launcher.data.PairSchedule
import org.arkikeskus.launcher.data.IconPackRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.data.DrawerFolder
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import org.arkikeskus.launcher.data.drawerFolders
import org.arkikeskus.launcher.data.createDrawerFolder
import org.arkikeskus.launcher.data.renameDrawerFolder
import org.arkikeskus.launcher.data.moveDrawerFolder

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

    /** All launchable apps, for the hidden-apps manager list (already sorted by AppRepository). */
    val apps: StateFlow<List<AppItem>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notification channels muted from the notifications widget (Settings ▸ Notifications). */
    val hiddenChannels: StateFlow<List<org.arkikeskus.launcher.data.HiddenChannel>> = repository.hiddenChannelList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unmuteChannel(channel: org.arkikeskus.launcher.data.HiddenChannel) =
        update { repository.setChannelHidden(channel.packageName, channel.channelId, hidden = false) }

    fun unmuteAllChannels() = update { repository.clearHiddenChannels() }

    /** An app's name for a package (any profile), or the package itself when it is not installed. */
    fun appLabel(packageName: String): String =
        apps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName

    /** Keys currently hidden from the drawer. */
    val hiddenKeys: StateFlow<Set<String>> = repository.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Drawer folders, in the order they appear. They are also the drawer's tabs, so this list is the
     * tab order shown on the App drawer page.
     */
    val drawerFolders: StateFlow<List<DrawerFolder>> = repository.drawerFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Moves the folder at [from] to [to]. The stored folder order is what the drawer reads, so the
     * tab row follows on its next emission — no separate tab list to keep in step.
     */
    fun moveDrawerFolder(from: Int, to: Int) = viewModelScope.launch {
        repository.moveDrawerFolder(from, to)
    }

    fun renameDrawerFolder(id: Long, name: String) = viewModelScope.launch {
        repository.renameDrawerFolder(id, name)
    }

    val appPairs: StateFlow<List<AppPair>> = appRepository.appPairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAppPair(pair: AppPair) = appRepository.saveAppPair(pair)

    /** Schedules per pair id (PairSchedules.kt), for the pairs manager's schedule dialog. */
    val pairSchedules: StateFlow<Map<String, PairSchedule>> =
        combine(appRepository.appPairs, repository.pairSchedules) { pairs, raw ->
            raw.mapNotNull { (id, value) ->
                val schedule = PairSchedule.parse(value) ?: return@mapNotNull null
                if (pairs.none { it.id == id }) return@mapNotNull null
                id to schedule
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPairSchedule(pairId: String, schedule: PairSchedule?) =
        viewModelScope.launch { repository.setPairSchedule(pairId, schedule) }

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
            s.home.columns, s.home.rows, type,
        )
    }
    fun launchAppPair(pair: AppPair) = appRepository.launchAppPair(pair)
    fun setThreeAppSplit(value: Boolean) = update { repository.setThreeAppSplit(value) }
    fun setThreeAppNative(value: Boolean) = update { repository.setThreeAppNative(value) }

    /** Pins [pair] on Folark's taskbar, or takes it off again. */
    fun toggleTaskbarPair(pair: AppPair) = update {
        val current = settings.value.taskbar.pinnedPairs
        repository.setTaskbarPairs(if (pair.id in current) current - pair.id else current + pair.id)
    }

    fun setFoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldAction(value, screenType) }
    fun setUnfoldAction(value: String, screenType: ScreenType = _screenType.value) = update { repository.setUnfoldAction(value, screenType) }
    fun setFoldTransitionStyle(value: String, screenType: ScreenType = _screenType.value) = update { repository.setFoldTransitionStyle(value, screenType) }
    fun setFoldTransitionSpeed(value: Float, screenType: ScreenType = _screenType.value) = update { repository.setFoldTransitionSpeed(value, screenType) }
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
    fun setDrawerTabsExclusive(value: Boolean) = update { repository.setDrawerTabsExclusive(value) }
    fun setDrawerTabsLocked(value: Boolean) = update { repository.setDrawerTabsLocked(value) }
    fun setHomeBackgroundImage(value: String, screenType: ScreenType = _screenType.value) = update { repository.setHomeBackgroundImage(value, screenType) }
    fun setFloatingWindowSize(value: String) = update { repository.setFloatingWindowSize(value) }
    fun setSearchShortcuts(value: Boolean) = update { repository.setSearchShortcuts(value) }
    fun setSearchWeb(value: Boolean) = update { repository.setSearchWeb(value) }
    fun setSearchWebEngine(value: String) = update { repository.setSearchWebEngine(value) }
    fun setSearchFiles(value: Boolean) = update { repository.setSearchFiles(value) }
    fun setAnimationSpeed(value: Float) = update { repository.setAnimationSpeed(value) }
    fun setAppOpenAnimation(value: String) = update { repository.setAppOpenAnimation(value) }
    fun setFolderOpenAnimation(value: String) = update { repository.setFolderOpenAnimation(value) }
    fun setPageScrollEffect(value: String) = update { repository.setPageScrollEffect(value) }
    fun setUnfoldAnimation(value: Boolean) = update { repository.setUnfoldAnimation(value) }
    fun setWidgetBackgroundOpacity(value: Float) = update { repository.setWidgetBackgroundOpacity(value) }
    fun setBatterySaverMode(value: String) = update { repository.setBatterySaverMode(value) }
    fun setBatterySaverLowBattery(value: Boolean) = update { repository.setBatterySaverLowBattery(value) }
    fun setBatterySaverWhenHot(value: Boolean) = update { repository.setBatterySaverWhenHot(value) }
    fun setClockWidgetStyle(value: String) = update { repository.setClockWidgetStyle(value) }
    fun setClockWidgetDarkText(value: Boolean) = update { repository.setClockWidgetDarkText(value) }
    fun setClockMinuteColor(value: String) = update { repository.setClockMinuteColor(value) }
    fun setClockMinuteOpacity(value: Float) = update { repository.setClockMinuteOpacity(value) }
    fun setClockWidgetGlassEffect(value: Boolean) = update { repository.setClockWidgetGlassEffect(value) }
    fun setWeatherWidgetAnimated(value: Boolean) = update { repository.setWeatherWidgetAnimated(value) }
    fun setWeatherAwakeSeconds(value: Int) = update { repository.setWeatherAwakeSeconds(value) }
    fun setVideoAwakeSeconds(value: Int) = update { repository.setVideoAwakeSeconds(value) }
    fun setTaskbarEnabled(value: Boolean) = update { repository.setTaskbarEnabled(value) }
    fun setTaskbarPinned(keys: List<String>?) = update { repository.setTaskbarPinned(keys) }
    fun setTaskbarRecentCount(value: Int) = update { repository.setTaskbarRecentCount(value) }
    fun setTaskbarSceneSlots(value: Int) = update { repository.setTaskbarSceneSlots(value) }
    fun setTaskbarIconSize(value: Int) = update { repository.setTaskbarIconSize(value) }
    fun setTaskbarIconSpacing(value: Int) = update { repository.setTaskbarIconSpacing(value) }
    fun setTaskbarBottomOffset(value: Int) = update { repository.setTaskbarBottomOffset(value) }
    fun setTaskbarTheme(value: String) = update { repository.setTaskbarTheme(value) }
    fun setTaskbarOpacity(value: Float) = update { repository.setTaskbarOpacity(value) }
    fun setTaskbarGlassBlur(value: Int) = update { repository.setTaskbarGlassBlur(value) }
    fun setTaskbarHandles(value: String) = update { repository.setTaskbarHandles(value) }
    fun setTaskbarOnHome(value: Boolean) = update { repository.setTaskbarOnHome(value) }
    fun setTaskbarKeepOpen(value: Boolean) = update { repository.setTaskbarKeepOpen(value) }
    fun setTaskbarMediaEnabled(value: Boolean) = update { repository.setTaskbarMediaEnabled(value) }
    fun setTaskbarMediaShowPaused(value: Boolean) = update { repository.setTaskbarMediaShowPaused(value) }
    fun setTaskbarMediaPosition(value: String) = update { repository.setTaskbarMediaPosition(value) }
    fun setTaskbarDividerStyle(value: String) = update { repository.setTaskbarDividerStyle(value) }
    fun setTaskbarMediaTap(value: String) = update { repository.setTaskbarMediaTap(value) }
    fun setTaskbarMediaControls(value: Boolean) = update { repository.setTaskbarMediaControls(value) }
    fun setTaskbarMediaShape(value: String) = update { repository.setTaskbarMediaShape(value) }
    fun setTaskbarMediaBadge(value: Boolean) = update { repository.setTaskbarMediaBadge(value) }
    fun setTaskbarSystemDrag(value: Boolean) = update { repository.setTaskbarSystemDrag(value) }
    fun setTaskbarEdgeSnap(value: Boolean) = update { repository.setTaskbarEdgeSnap(value) }
    fun setTaskbarRecentsButton(value: String) = update { repository.setTaskbarRecentsButton(value) }
    fun setTaskbarQuickSwitch(value: Boolean) = update { repository.setTaskbarQuickSwitch(value) }
    fun setTaskbarSplitControls(value: Boolean) = update { repository.setTaskbarSplitControls(value) }
    fun setTaskbarSplitSide(value: String) = update { repository.setTaskbarSplitSide(value) }
    fun setTaskbarThreeApp(value: Boolean) = update { repository.setTaskbarThreeApp(value) }
    fun setSettingsStyle(value: String) = update { repository.setSettingsStyle(value) }
    fun setIconShape(value: String) = update { repository.setIconShape(value) }
    fun setMotoWeatherTap(value: Boolean) = update { repository.setMotoWeatherTap(value) }
    fun setShowPrivateSpace(value: Boolean) = update { repository.setShowPrivateSpace(value) }
    fun setPopupNotifications(value: Boolean) = update { repository.setPopupNotifications(value) }
    fun setDotAvatars(value: Boolean) = update { repository.setDotAvatars(value) }
    fun setLiveActivities(value: Boolean) = update { repository.setLiveActivities(value) }
    fun setLiveActivitiesTaskbar(value: Boolean) = update { repository.setLiveActivitiesTaskbar(value) }
    fun setLiveExpand(value: Boolean) = update { repository.setLiveExpand(value) }
    fun setLiveSkipSystemChips(value: Boolean) = update { repository.setLiveSkipSystemChips(value) }
    fun setLiveActivityEffect(value: String) = update { repository.setLiveActivityEffect(value) }
    fun setSearchActions(value: Boolean) = update { repository.setSearchActions(value) }
    fun setRecentsEnabled(value: Boolean) = update { repository.setRecentsEnabled(value) }
    fun setRecentsReplaceSystem(value: Boolean) = update { repository.setRecentsReplaceSystem(value) }
    fun setNavHandleOpensSearch(value: Boolean) = update { repository.setNavHandleOpensSearch(value) }
    fun setRecentsColumnsInner(value: Int) = update { repository.setRecentsColumnsInner(value) }
    fun setRecentsColumnsOuter(value: Int) = update { repository.setRecentsColumnsOuter(value) }
    fun setRecentsMaxApps(value: Int) = update { repository.setRecentsMaxApps(value) }
    fun setRecentsCardHeight(value: Int) = editRecentsCover.let { cover -> update { repository.setRecentsCardHeight(value, cover = cover) } }
    fun setRecentsCornerDp(value: Int) = editRecentsCover.let { cover -> update { repository.setRecentsCornerDp(value, cover = cover) } }
    fun setRecentsBackdropDim(value: Float) = editRecentsCover.let { cover -> update { repository.setRecentsBackdropDim(value, cover = cover) } }
    fun setRecentsSort(value: String) = update { repository.setRecentsSort(value) }
    fun setRecentsShowTime(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsShowTime(value, cover = cover) } }
    fun setRecentsCardLayout(value: String) = editRecentsCover.let { cover -> update { repository.setRecentsCardLayout(value, cover = cover) } }
    fun setRecentsCardIcons(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsCardIcons(value, cover = cover) } }
    fun setRecentsCardShadow(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsCardShadow(value, cover = cover) } }
    fun setRecentsLabelScale(value: Float) = editRecentsCover.let { cover -> update { repository.setRecentsLabelScale(value, cover = cover) } }
    fun setRecentsConfirmCloseAll(value: Boolean) = update { repository.setRecentsConfirmCloseAll(value) }
    fun setRecentsThirdInSplit(value: Boolean) = update { repository.setRecentsThirdInSplit(value) }
    fun setRecentsThumbnails(value: Boolean) = update { repository.setRecentsThumbnails(value) }
    fun setRecentsSpeakerBadge(value: Boolean) = update { repository.setRecentsSpeakerBadge(value) }
    fun setHapticsStrength(value: String) = update { repository.setHapticsStrength(value) }
    fun setHapticsDrag(value: Boolean) = update { repository.setHapticsDrag(value) }
    fun setHapticsRecents(value: Boolean) = update { repository.setHapticsRecents(value) }
    fun setHapticsClose(value: Boolean) = update { repository.setHapticsClose(value) }
    fun setHapticsSplit(value: Boolean) = update { repository.setHapticsSplit(value) }
    fun setHapticsLaunch(value: Boolean) = update { repository.setHapticsLaunch(value) }
    fun setHapticsEnabled(value: Boolean) = update { repository.setHapticsEnabled(value) }
    fun setShapeAllIcons(value: Boolean) = update { repository.setShapeAllIcons(value) }
    fun setTaskbarLaunchGlow(value: String) = update { repository.setTaskbarLaunchGlow(value) }
    fun setDrawerTwoPane(value: Boolean) = update { repository.setDrawerTwoPane(value) }
    fun setDrawerAzHoldScroll(value: Boolean) = update { repository.setDrawerAzHoldScroll(value) }
    fun setHiddenAppsLock(value: Boolean) = update { repository.setHiddenAppsLock(value) }
    fun setAddNewApps(value: Boolean) = update { repository.setAddNewApps(value) }
    fun setSearchInlineShortcuts(value: Boolean) = update { repository.setSearchInlineShortcuts(value) }
    fun setSearchSettingsPages(value: Boolean) = update { repository.setSearchSettingsPages(value) }
    fun setRecentsMemoryBadge(value: Boolean) = update { repository.setRecentsMemoryBadge(value) }
    fun setRecentsGroupSplits(value: Boolean) = update { repository.setRecentsGroupSplits(value) }
    fun setRecentsRootClose(value: Boolean) = update { repository.setRecentsRootClose(value) }
    fun setTaskbarRunningApps(value: Boolean) = update { repository.setTaskbarRunningApps(value) }
    fun setTaskbarRunningDots(value: Boolean) = update { repository.setTaskbarRunningDots(value) }
    fun setTaskbarMultiDots(value: Boolean) = update { repository.setTaskbarMultiDots(value) }
    fun setRecentsEnterAnimation(value: String) = editRecentsCover.let { cover -> update { repository.setRecentsEnterAnimation(value, cover = cover) } }
    fun setRecentsSelectAnimation(value: String) = editRecentsCover.let { cover -> update { repository.setRecentsSelectAnimation(value, cover = cover) } }
    fun setRecentsAnimationSpeed(value: Float) = editRecentsCover.let { cover -> update { repository.setRecentsAnimationSpeed(value, cover = cover) } }
    fun setRootEnabled(value: Boolean) = update { repository.setRootEnabled(value) }
    fun setRecentsTheme(value: String) = editRecentsCover.let { cover -> update { repository.setRecentsTheme(value, cover = cover) } }
    fun setRecentsPressAnimation(value: String) = editRecentsCover.let { cover -> update { repository.setRecentsPressAnimation(value, cover = cover) } }
    fun setRecentsGlass(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsGlass(value, cover = cover) } }
    fun setRecentsGlassBlur(value: Int) = editRecentsCover.let { cover -> update { repository.setRecentsGlassBlur(value, cover = cover) } }
    fun setRecentsGlassTint(value: Float) = editRecentsCover.let { cover -> update { repository.setRecentsGlassTint(value, cover = cover) } }
    fun setRecentsDragDrop(value: Boolean) = update { repository.setRecentsDragDrop(value) }
    fun setRecentsOverlayLabels(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsOverlayLabels(value, cover = cover) } }
    fun setRecentsMenuMotion(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsMenuMotion(value, cover = cover) } }
    fun setRecentsScrollTicks(value: Boolean) = update { repository.setRecentsScrollTicks(value) }
    fun setRecentsBtnClose(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnClose(value, cover = cover) } }
    fun setRecentsBtnSplit(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnSplit(value, cover = cover) } }
    fun setRecentsBtnPopup(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnPopup(value, cover = cover) } }
    fun setRecentsBtnUnsplit(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnUnsplit(value, cover = cover) } }
    fun setRecentsBtnSplitView(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnSplitView(value, cover = cover) } }
    fun setRecentsBtnCloseAll(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnCloseAll(value, cover = cover) } }
    fun setRecentsSwipeUpAction(value: String) = update { repository.setRecentsSwipeUpAction(value) }
    fun setRecentsSwipeDownAction(value: String) = update { repository.setRecentsSwipeDownAction(value) }
    fun setRecentsSwipeLeftAction(value: String) = update { repository.setRecentsSwipeLeftAction(value) }
    fun setRecentsSwipeRightAction(value: String) = update { repository.setRecentsSwipeRightAction(value) }
    fun setRecentsLongPressAction(value: String) = update { repository.setRecentsLongPressAction(value) }
    fun setRecentsDoubleTapAction(value: String) = update { repository.setRecentsDoubleTapAction(value) }
    fun setRecentsOuterGestures(value: Boolean) = update { repository.setRecentsOuterGestures(value) }

    /** Settings ▸ Recents: the look being edited is the outer screen's (with its own look on). */
    var editRecentsCover by mutableStateOf(false)
    fun setRecentsOuterLook(value: Boolean) {
        if (!value) editRecentsCover = false
        update { repository.setRecentsOuterLook(value) }
    }
    fun setRecentsOuterSwipeUpAction(value: String) = update { repository.setRecentsOuterSwipeUpAction(value) }
    fun setRecentsOuterSwipeDownAction(value: String) = update { repository.setRecentsOuterSwipeDownAction(value) }
    fun setRecentsOuterSwipeLeftAction(value: String) = update { repository.setRecentsOuterSwipeLeftAction(value) }
    fun setRecentsOuterSwipeRightAction(value: String) = update { repository.setRecentsOuterSwipeRightAction(value) }
    fun setRecentsOuterLongPressAction(value: String) = update { repository.setRecentsOuterLongPressAction(value) }
    fun setRecentsOuterDoubleTapAction(value: String) = update { repository.setRecentsOuterDoubleTapAction(value) }
    fun setRecentsBtnPin(value: Boolean) = editRecentsCover.let { cover -> update { repository.setRecentsBtnPin(value, cover = cover) } }
    fun setRecentsPinKeepAlive(value: Boolean) = update { repository.setRecentsPinKeepAlive(value) }
    fun setTaskbarHiddenApps(value: Set<String>) = update { repository.setTaskbarHiddenApps(value) }
    fun setTaskbarAlwaysApps(value: Set<String>) = update { repository.setTaskbarAlwaysApps(value) }
    fun setTaskbarAutoHideApps(value: Set<String>) = update { repository.setTaskbarAutoHideApps(value) }
    fun setTaskbarMiniApps(value: Set<String>) = update { repository.setTaskbarMiniApps(value) }
    fun setLiquidGlassEverywhere(value: Boolean) = update { repository.setLiquidGlassEverywhere(value) }
    fun setLiquidGlassLevel(value: Float) = update { repository.setLiquidGlassLevel(value) }
    fun setRecentsAttachedSide(value: String) = update { repository.setRecentsAttachedSide(value) }
    fun setRecentsAttachedPeek(value: Float) = update { repository.setRecentsAttachedPeek(value) }
    fun setRecentsAttachedNative(value: Boolean) = update { repository.setRecentsAttachedNative(value) }
    fun setRecentsSplitEngine(value: Int) = update { repository.setRecentsSplitEngine(value) }
    fun setSplitPickerColumns(value: Int) = update { repository.setSplitPickerColumns(value) }
    fun setSplitPickerIconSize(value: Int) = update { repository.setSplitPickerIconSize(value) }
    fun setSplitPickerLabels(value: Boolean) = update { repository.setSplitPickerLabels(value) }
    fun setSplitPickerFolders(value: Boolean) = update { repository.setSplitPickerFolders(value) }
    fun setSplitPickerRecents(value: String) = update { repository.setSplitPickerRecents(value) }
    fun setSplitPickerRecentsCount(value: Int) = update { repository.setSplitPickerRecentsCount(value) }
    fun setSplitPickerSort(value: String) = update { repository.setSplitPickerSort(value) }
    fun setSplitPickerSearch(value: Boolean) = update { repository.setSplitPickerSearch(value) }
    fun setSplitPickerOpacity(value: Float) = update { repository.setSplitPickerOpacity(value) }
    fun setSplitPickerOpenAnim(value: String) = update { repository.setSplitPickerOpenAnim(value) }
    fun setDropZone(zone: String, on: Boolean) = update { repository.setDropZone(zone, on) }
    fun setDropZoneHaptics(value: Boolean) = update { repository.setDropZoneHaptics(value) }
    fun setSplitUndo(value: Boolean) = update { repository.setSplitUndo(value) }
    fun setDividerStops(value: Boolean) = update { repository.setDividerStops(value) }
    fun setSplitThreeEven(value: Boolean) = update { repository.setSplitThreeEven(value) }
    fun setSplitFreeDivider(value: Boolean) = update { repository.setSplitFreeDivider(value) }
    fun setSplitDividerLock(value: String) = update { repository.setSplitDividerLock(value) }
    fun setSplitDividerMarks(value: Boolean) = update { repository.setSplitDividerMarks(value) }
    fun setSplitDividerHaptics(value: Boolean) = update { repository.setSplitDividerHaptics(value) }
    fun setSplitKeepSize(value: Boolean) = update { repository.setSplitKeepSize(value) }
    fun setSplitRememberSize(value: Boolean) = update { repository.setSplitRememberSize(value) }
    fun setSplitDragHint(value: Boolean) = update { repository.setSplitDragHint(value) }
    fun setPeekFlingClose(value: Boolean) = update { repository.setPeekFlingClose(value) }
    fun setSplitRatioMenu(value: Boolean) = update { repository.setSplitRatioMenu(value) }
    fun setSplitOpenWith(value: Boolean) = update { repository.setSplitOpenWith(value) }
    fun setNotificationSplit(value: Boolean) = update { repository.setNotificationSplit(value) }
    fun setSystemBarPadding(value: Boolean) = update { repository.setSystemBarPadding(value) }
    fun setSystemBarPaddingInner(value: Int) = update { repository.setSystemBarPaddingInner(value) }
    fun setSystemBarPaddingCover(value: Int) = update { repository.setSystemBarPaddingCover(value) }
    fun setTaskbarCoverBar(value: Boolean) = update { repository.setTaskbarCoverBar(value) }
    fun setTaskbarCoverCount(value: Int) = update { repository.setTaskbarCoverCount(value) }
    fun setAspectRatioMenu(value: Boolean) = update { repository.setAspectRatioMenu(value) }
    fun setSplitPickerFavorites(value: Boolean) = update { repository.setSplitPickerFavorites(value) }
    fun setSplitPickerRecentPairs(value: Boolean) = update { repository.setSplitPickerRecentPairs(value) }
    fun setSplitPickerPeekToggle(value: Boolean) = update { repository.setSplitPickerPeekToggle(value) }
    fun setSplitPickerLongPress(value: Boolean) = update { repository.setSplitPickerLongPress(value) }
    fun setTaskbarHandleHint(value: Boolean) = update { repository.setTaskbarHandleHint(value) }
    fun setTaskbarHandleStyle(value: String) = update { repository.setTaskbarHandleStyle(value) }
    fun setTaskbarHideButton(value: Boolean) = update { repository.setTaskbarHideButton(value) }
    fun setTaskbarPinsLocked(value: Boolean) = update { repository.setTaskbarPinsLocked(value) }
    fun setTaskbarSwipeDownHide(value: Boolean) = update { repository.setTaskbarSwipeDownHide(value) }
    fun setTaskbarOpenPreviews(value: Boolean) = update { repository.setTaskbarOpenPreviews(value) }
    fun setTaskbarAnim(value: String) = update { repository.setTaskbarAnim(value) }
    fun setTaskbarSource(value: String) = update { repository.setTaskbarSource(value) }
    fun setSystemTaskbarMode(value: Int) = update { repository.setSystemTaskbarMode(value) }
    fun setRootLockMode(value: String) = update { repository.setRootLockMode(value) }
    fun setRootRecentsMode(value: String) = update { repository.setRootRecentsMode(value) }
    fun setRootShadeMode(value: String) = update { repository.setRootShadeMode(value) }
    fun setRootKeepDump(value: Boolean) = update { repository.setRootKeepDump(value) }
    fun setRootKeepUsage(value: Boolean) = update { repository.setRootKeepUsage(value) }
    fun setRootKeepSecureSettings(value: Boolean) = update { repository.setRootKeepSecureSettings(value) }
    fun setRootKeepNotifications(value: Boolean) = update { repository.setRootKeepNotifications(value) }
    fun setRootKeepAccessibility(value: Boolean) = update { repository.setRootKeepAccessibility(value) }
    fun setRootKeepWidgets(value: Boolean) = update { repository.setRootKeepWidgets(value) }
    fun setIconNudge(value: Boolean) = update { repository.setIconNudge(value) }
    fun setUnlockTransition(value: Boolean) = update { repository.setUnlockTransition(value) }
    fun setUnlockSystemHook(value: Boolean) = update { repository.setUnlockSystemHook(value) }
    fun setUnlockStyle(value: String) = update { repository.setUnlockStyle(value) }
    fun setIconNudgeTaskbar(value: Boolean) = update { repository.setIconNudgeTaskbar(value) }
    fun setIconNudgeEffect(value: String) = update { repository.setIconNudgeEffect(value) }
    fun setIconNudgeSoftness(value: Float) = update { repository.setIconNudgeSoftness(value) }
    fun setLiveHingeEndAngle(value: Int) = update { repository.setLiveHingeEndAngle(value) }
    fun setLiveHingeSmoothing(value: Int) = update { repository.setLiveHingeSmoothing(value) }
    fun setLiveHingeStyle(value: String) = update { repository.setLiveHingeStyle(value) }
    fun setLiveHingeOuterStyle(value: String) = update { repository.setLiveHingeOuterStyle(value) }
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
    fun setAppHeadingFont(value: String, screenType: ScreenType = _screenType.value) = update { repository.setAppHeadingFont(value, screenType) }
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
    fun setHingeTilt(value: Boolean) = update { repository.setHingeTilt(value) }
    fun setWallpaperHinge(value: Boolean) = update { repository.setWallpaperHinge(value) }
    fun setIconRing(value: String) = update { repository.setIconRing(value) }
    fun setFolderDirectApps(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setFolderDirectApps(value, screenType) }
    fun setTaskbarHideFullScreen(value: Boolean) = update { repository.setTaskbarHideFullScreen(value) }
    fun setTaskbarClearSystemInset(value: Boolean) = update { repository.setTaskbarClearSystemInset(value) }
    fun setSplitLaunchEnabled(value: Boolean) = update { repository.setSplitLaunchEnabled(value) }
    fun setFloatingLaunchEnabled(value: Boolean) = update { repository.setFloatingLaunchEnabled(value) }
    fun setAmoledDark(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setAmoledDark(value, screenType) }
    fun setDockEnabled(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDockEnabled(value, screenType) }
    fun setDockColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDockColumns(value, screenType) }
    fun setDrawerColumns(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerColumns(value, screenType) }
    fun setDrawerRows(value: Int, screenType: ScreenType = _screenType.value) = update { repository.setDrawerRows(value, screenType) }
    fun setShowDrawerSearch(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowDrawerSearch(value, screenType) }
    fun setShowFrequentApps(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setShowFrequentApps(value, screenType) }
    fun setDrawerClipboard(value: Boolean, screenType: ScreenType = _screenType.value) = update { repository.setDrawerClipboard(value, screenType) }
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
        if (value && repository.settings(screenType).first().drawer.backgroundBlur <= 0f) {
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
        if (value < s.home.columns) homeLayoutRepository.reflow(value, s.home.rows, screenType)
        repository.setHomeColumns(value, screenType)
    }

    fun setHomeRows(value: Int, screenType: ScreenType = _screenType.value) = viewModelScope.launch {
        // Same off-grid guard as the column stepper, on the vertical axis.
        val s = repository.settings(screenType).first()
        if (value < s.home.rows) homeLayoutRepository.reflow(s.home.columns, value, screenType)
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
    private companion object {
        const val DEFAULT_GLASS_BLUR = 18f
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
