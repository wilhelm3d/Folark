package org.arkikeskus.launcher.data

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_DRAWER_ROWS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_HOME_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_HOME_ROWS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_LABEL_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_DOT_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_DRAWER_ROWS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_LABEL_SCALE
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_ROWS
import org.arkikeskus.launcher.model.AppOpenAnimation
import org.arkikeskus.launcher.model.AppearanceSettings
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.BatterySaverMode
import org.arkikeskus.launcher.model.ClockMinuteColor
import org.arkikeskus.launcher.model.ClockStyle
import org.arkikeskus.launcher.model.CountStyle
import org.arkikeskus.launcher.model.DividerLock
import org.arkikeskus.launcher.model.DockAlignment
import org.arkikeskus.launcher.model.DockSettings
import org.arkikeskus.launcher.model.DotColor
import org.arkikeskus.launcher.model.DotPosition
import org.arkikeskus.launcher.model.DotSettings
import org.arkikeskus.launcher.model.DotShape
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.model.DrawerHandle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.DrawerSettings
import org.arkikeskus.launcher.model.DrawerStyle
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.model.FoldAction
import org.arkikeskus.launcher.model.FoldSettings
import org.arkikeskus.launcher.model.FoldTransition
import org.arkikeskus.launcher.model.FolderAnimation
import org.arkikeskus.launcher.model.FolderPreview
import org.arkikeskus.launcher.model.FolderSettings
import org.arkikeskus.launcher.model.FolderShape
import org.arkikeskus.launcher.model.FolderTheme
import org.arkikeskus.launcher.model.FontChoice
import org.arkikeskus.launcher.model.HapticsStrength
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.HomeSettings
import org.arkikeskus.launcher.model.IconSettings
import org.arkikeskus.launcher.model.IconShape
import org.arkikeskus.launcher.model.IconSwipe
import org.arkikeskus.launcher.model.LaunchGlow
import org.arkikeskus.launcher.model.LaunchSettings
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.LiveEffect
import org.arkikeskus.launcher.model.LiveHingeStyle
import org.arkikeskus.launcher.model.LiveSettings
import org.arkikeskus.launcher.model.MinusOne
import org.arkikeskus.launcher.model.MotionSettings
import org.arkikeskus.launcher.model.NudgeEffect
import org.arkikeskus.launcher.model.PageEffect
import org.arkikeskus.launcher.model.PeoplePrivacy
import org.arkikeskus.launcher.model.PickerOpen
import org.arkikeskus.launcher.model.PickerRecents
import org.arkikeskus.launcher.model.PickerSort
import org.arkikeskus.launcher.model.PopupTheme
import org.arkikeskus.launcher.model.PressEffect
import org.arkikeskus.launcher.model.RecentsAction
import org.arkikeskus.launcher.model.RecentsAttachedSide
import org.arkikeskus.launcher.model.RecentsCardLayout
import org.arkikeskus.launcher.model.RecentsEnter
import org.arkikeskus.launcher.model.RecentsSelect
import org.arkikeskus.launcher.model.RecentsSettings
import org.arkikeskus.launcher.model.RecentsSort
import org.arkikeskus.launcher.model.RecentsTheme
import org.arkikeskus.launcher.model.RingMode
import org.arkikeskus.launcher.model.RootMode
import org.arkikeskus.launcher.model.RootSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchEngine
import org.arkikeskus.launcher.model.SearchPosition
import org.arkikeskus.launcher.model.SearchSettings
import org.arkikeskus.launcher.model.SettingsStyle
import org.arkikeskus.launcher.model.SizeChoice
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.model.StackIndicator
import org.arkikeskus.launcher.model.StatusBarSettings
import org.arkikeskus.launcher.model.TaskbarAnim
import org.arkikeskus.launcher.model.TaskbarDivider
import org.arkikeskus.launcher.model.TaskbarHandleStyle
import org.arkikeskus.launcher.model.TaskbarHandles
import org.arkikeskus.launcher.model.TaskbarMediaPosition
import org.arkikeskus.launcher.model.TaskbarMediaShape
import org.arkikeskus.launcher.model.TaskbarMediaTap
import org.arkikeskus.launcher.model.TaskbarRecentsButton
import org.arkikeskus.launcher.model.TaskbarSettings
import org.arkikeskus.launcher.model.TaskbarSource
import org.arkikeskus.launcher.model.TaskbarSplitSide
import org.arkikeskus.launcher.model.TaskbarTheme
import org.arkikeskus.launcher.model.ThemedStyle
import org.arkikeskus.launcher.model.UnlockStyle
import org.arkikeskus.launcher.model.WidgetSettings
import org.arkikeskus.launcher.model.WidgetTheme
import org.arkikeskus.launcher.model.storedChoice

// Reading the settings (split out of SettingsRepository.kt): from the stored values to
// LauncherSettings for one display, with every number clamped to its range on the way in.

internal fun readSettings(p: Preferences, screenType: ScreenType): LauncherSettings = p.let {
    // Clamp the numeric values on read as well as on write: a stale/garbage value left in the
    // store during development (e.g. homeColumns = 0) must never reach the layout math, where a
    // zero column count would spin firstFreeCell() in an infinite loop.
    LauncherSettings(
        dock = DockSettings(
            enabled = p[Keys.dockEnabled(screenType)] ?: true,
            columns = (p[Keys.dockColumns(screenType)] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            showLabels = p[Keys.showDockLabels(screenType)] ?: false,
            backgroundOpacity = (p[Keys.dockBackgroundOpacity(screenType)] ?: 0.35f).coerceIn(0f, 1f),
            innerAlignment = storedChoice(p[Keys.innerDockAlignment(screenType)], DockAlignment.CENTER),
        ),
        home = HomeSettings(
            columns = (p[Keys.homeColumns(screenType)] ?: 4).coerceIn(MIN_COLUMNS, MAX_HOME_COLUMNS),
            rows = (p[Keys.homeRows(screenType)] ?: HomeLayoutRepository.ROWS).coerceIn(MIN_ROWS, MAX_HOME_ROWS),
            showLabels = p[Keys.showHomeLabels(screenType)] ?: true,
            parallaxWallpaper = p[Keys.parallaxWallpaper(screenType)] ?: false,
            showPageIndicator = p[Keys.showPageIndicator(screenType)] ?: true,
            leftSwipeAppKey = p[Keys.leftSwipeAppKey(screenType)] ?: "",
            rightSwipeAppKey = p[Keys.rightSwipeAppKey(screenType)] ?: "",
            desktopLocked = p[Keys.DESKTOP_LOCKED] ?: false,
            twoLineLabels = p[Keys.twoLineHomeLabels(screenType)] ?: false,
            page = (p[Keys.homePage(screenType)] ?: 0).coerceIn(0, HomeLayoutRepository.MAX_PAGES),
            pageCount = (p[Keys.homePageCount(screenType)] ?: 0).coerceIn(0, HomeLayoutRepository.MAX_PAGES),
            allowLandscape = p[Keys.allowLandscape(screenType)] ?: false,
            swipeDownAction = storedChoice(p[Keys.swipeDownAction(screenType)], HomeGesture.NOTIFICATIONS),
            doubleTapAction = storedChoice(p[Keys.doubleTapAction(screenType)], HomeGesture.NONE),
            swipeUpAction = storedChoice(p[Keys.swipeUpAction(screenType)], HomeGesture.DRAWER, HomeGesture.SWIPE_UP),
            minusOneScreen = storedChoice(p[Keys.MINUS_ONE_SCREEN], MinusOne.NONE),
            pageBounceEnabled = p[Keys.pageBounceEnabled(screenType)] ?: true,
            wallpaperDim = (p[Keys.homeWallpaperDim(screenType)] ?: 0f).coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_DIM),
            wallpaperBlur = (p[Keys.homeWallpaperBlur(screenType)] ?: 0f).coerceIn(0f, LauncherSettings.MAX_HOME_WALLPAPER_BLUR),
            backgroundImage = p[Keys.homeBackgroundImage(screenType)] ?: "",
            videoAwakeSeconds = (p[Keys.VIDEO_AWAKE_SECONDS] ?: 30).coerceIn(0, 600),
            addNewApps = p[Keys.ADD_NEW_APPS] ?: false,
            iconSwipeUpAction = storedChoice(p[Keys.iconSwipeUpAction(screenType)], IconSwipe.NONE),
            autoRemoveEmptyPages = p[Keys.autoRemoveEmptyPages(screenType)] ?: false,
            newPageOnDrag = p[Keys.newPageOnDrag(screenType)] ?: false,
            unfoldAnimation = p[Keys.UNFOLD_ANIMATION] ?: true,
            pageScrollEffect = storedChoice(p[Keys.PAGE_SCROLL_EFFECT], PageEffect.SLIDE),
            innerDualPage = p[Keys.innerDualPageWorkspace(screenType)] ?: false,
        ),
        drawer = DrawerSettings(
            columns = (p[Keys.drawerColumns(screenType)] ?: if (screenType == ScreenType.INNER) 6 else 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            rows = (p[Keys.drawerRows(screenType)] ?: 6).coerceIn(MIN_DRAWER_ROWS, MAX_DRAWER_ROWS),
            showSearch = p[Keys.showDrawerSearch(screenType)] ?: true,
            showLabels = p[Keys.showDrawerLabels(screenType)] ?: true,
            showFrequentApps = p[Keys.showFrequentApps(screenType)] ?: false,
            clipboardRow = p[Keys.drawerClipboard(screenType)] ?: false,
            searchPosition = storedChoice(p[Keys.drawerSearchPosition(screenType)], SearchPosition.TOP),
            indexBarEnabled = p[Keys.drawerIndexBarEnabled(screenType)] ?: true,
            azIndexScale = (p[Keys.azIndexScale(screenType)] ?: 1f).coerceIn(LauncherSettings.MIN_AZ_INDEX_SCALE, LauncherSettings.MAX_AZ_INDEX_SCALE),
            handleStyle = storedChoice(p[Keys.drawerHandleStyle(screenType)], DrawerHandle.BAR),
            bottomSpace = (p[Keys.drawerBottomSpace(screenType)] ?: 0).coerceIn(0, LauncherSettings.MAX_DRAWER_BOTTOM_SPACE),
            letterHeaders = p[Keys.drawerLetterHeaders(screenType)] ?: false,
            largeLetterPopup = p[Keys.drawerLargeLetterPopup(screenType)] ?: true,
            swipeTabs = p[Keys.drawerSwipeTabs(screenType)] ?: true,
            flickClose = p[Keys.drawerFlickClose(screenType)] ?: false,
            cornerRadius = (p[Keys.drawerCornerRadius(screenType)] ?: LauncherSettings.DRAWER_CORNER_AUTO)
                .coerceIn(LauncherSettings.DRAWER_CORNER_AUTO, LauncherSettings.MAX_DRAWER_CORNER_RADIUS),
            azIndexPosition = storedChoice(p[Keys.azIndexPosition(screenType)], AzPosition.VERTICAL_RIGHT),
            backgroundBlur = (p[Keys.drawerBackgroundBlur(screenType)] ?: 0f).coerceIn(0f, 25f),
            scrimOpacity = (p[Keys.drawerScrimOpacity(screenType)] ?: 0.5f).coerceIn(0f, 1f),
            glassBlurRadius = (p[Keys.glassBlurRadius(screenType)] ?: 25.0f).coerceIn(0f, 50f),
            glassDarkTint = (p[Keys.glassDarkTint(screenType)] ?: 0.45f).coerceIn(0f, 0.9f),
            style = storedChoice(p[Keys.drawerStyle(screenType)], DrawerStyle.STANDARD_GRID),
            themeTint = storedChoice(p[Keys.drawerThemeTint(screenType)], DrawerTheme.SYSTEM),
            accentColor = p[Keys.drawerAccentColor(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_ACCENT,
            customColor = p[Keys.drawerCustomColor(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_CUSTOM_COLOR,
            glassEnabled = p[Keys.drawerGlassEnabled(screenType)] ?: false,
            panelOpacity = (p[Keys.drawerPanelOpacity(screenType)] ?: LauncherSettings.DEFAULT_DRAWER_PANEL_OPACITY)
                .coerceIn(LauncherSettings.MIN_DRAWER_PANEL_OPACITY, LauncherSettings.MAX_DRAWER_PANEL_OPACITY),
            heightFraction = (p[Keys.drawerHeightFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            widthFraction = (p[Keys.drawerWidthFraction(screenType)] ?: 1.0f).coerceIn(0.4f, 1.0f),
            alignment = storedChoice(p[Keys.drawerAlignment(screenType)], DrawerAlignment.CENTER),
            layoutMode = storedChoice(p[Keys.drawerLayoutMode(screenType)], DrawerLayout.GRID),
            opensAtTop = p[Keys.drawerOpensAtTop(screenType)] ?: true,
            twoLineLabels = p[Keys.twoLineDrawerLabels(screenType)] ?: true,
            folderTabs = p[Keys.drawerFolderTabs(screenType)] ?: false,
            tabsExclusive = p[Keys.DRAWER_TABS_EXCLUSIVE] ?: true,
            lockTabOrder = p[Keys.DRAWER_TABS_LOCKED] ?: false,
            showPrivateSpace = p[Keys.SHOW_PRIVATE_SPACE] ?: true,
            innerSidebar = p[Keys.innerSidebarAppDrawer(screenType)] ?: false,
            azHoldScroll = p[Keys.DRAWER_AZ_HOLD_SCROLL] ?: true,
            hiddenLock = p[Keys.HIDDEN_APPS_LOCK] ?: false,
            twoPane = p[Keys.DRAWER_TWO_PANE] ?: false,
        ),
        search = SearchSettings(
            contacts = p[Keys.SEARCH_CONTACTS] ?: false,
            shortcuts = p[Keys.SEARCH_SHORTCUTS] ?: true,
            web = p[Keys.SEARCH_WEB] ?: true,
            webEngine = storedChoice(p[Keys.SEARCH_WEB_ENGINE], SearchEngine.SYSTEM),
            files = p[Keys.SEARCH_FILES] ?: false,
            actions = p[Keys.SEARCH_ACTIONS] ?: true,
            settingsPages = p[Keys.SEARCH_SETTINGS_PAGES] ?: true,
            inlineShortcuts = p[Keys.SEARCH_INLINE_SHORTCUTS] ?: true,
        ),
        icons = IconSettings(
            forceThemedIcons = p[Keys.FORCE_THEMED_ICONS] ?: false,
            themedIconStyle = storedChoice(p[Keys.THEMED_ICON_STYLE], ThemedStyle.CLASSIC),
            useThemedIcons = p[Keys.useThemedIcons(screenType)] ?: false,
            iconPack = p[Keys.iconPackPackage(screenType)] ?: "",
            labelTextScale = (p[Keys.appLabelTextScale(screenType)] ?: 1.0f).coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE),
            labelColor = p[Keys.appLabelColor(screenType)] ?: 0xFFFFFFFF.toInt(),
            sizeScale = (p[Keys.iconSizeScale(screenType)] ?: 1.0f)
                .coerceIn(LauncherSettings.MIN_ICON_SIZE_SCALE, LauncherSettings.MAX_ICON_SIZE_SCALE),
            shape = storedChoice(p[Keys.ICON_SHAPE], IconShape.SYSTEM),
            shapeAllIcons = p[Keys.ICON_SHAPE_ALL] ?: true,
            nudge = p[Keys.ICON_NUDGE] ?: true,
            nudgeTaskbar = p[Keys.ICON_NUDGE_TASKBAR] ?: true,
            nudgeEffect = storedChoice(p[Keys.ICON_NUDGE_EFFECT], NudgeEffect.BOUNCE),
            nudgeSoftness = (p[Keys.ICON_NUDGE_SOFTNESS] ?: 0.5f).coerceIn(0f, 1f),
            ring = storedChoice(p[Keys.ICON_RING], RingMode.PROGRESS),
        ),
        dots = DotSettings(
            enabled = p[Keys.showNotificationDots(screenType)] ?: true,
            showCount = p[Keys.notificationDotCount(screenType)] ?: true,
            scale = (p[Keys.notificationDotScale(screenType)] ?: 1.0f).coerceIn(MIN_DOT_SCALE, MAX_DOT_SCALE),
            shape = storedChoice(p[Keys.dotShape(screenType)], DotShape.CIRCLE),
            color = storedChoice(p[Keys.dotColor(screenType)], DotColor.RED),
            position = storedChoice(p[Keys.dotPosition(screenType)], DotPosition.TOP_END),
            avatars = p[Keys.DOT_AVATARS] ?: true,
        ),
        folders = FolderSettings(
            shape = storedChoice(p[Keys.folderShape(screenType)], FolderShape.SQUIRCLE),
            theme = storedChoice(p[Keys.folderTheme(screenType)], FolderTheme.GLASS),
            preview = storedChoice(p[Keys.folderPreview(screenType)], FolderPreview.GRID_2),
            openColumns = (p[Keys.folderOpenColumns(screenType)] ?: 4)
                .coerceIn(LauncherSettings.MIN_FOLDER_OPEN_COLUMNS, LauncherSettings.MAX_FOLDER_OPEN_COLUMNS),
            openSize = storedChoice(p[Keys.folderOpenSize(screenType)], SizeChoice.MEDIUM),
            tapFirstApp = p[Keys.folderTapFirstApp(screenType)] ?: false,
            directApps = p[Keys.folderDirectApps(screenType)] ?: false,
            openAnimation = storedChoice(p[Keys.FOLDER_OPEN_ANIMATION], FolderAnimation.SHEET),
        ),
        widgets = WidgetSettings(
            showWeather = p[Keys.showWeather(screenType)] ?: true,
            tonalBackground = p[Keys.widgetTonalBackground(screenType)] ?: false,
            notificationCountStyle = storedChoice(p[Keys.notificationWidgetCountStyle(screenType)], CountStyle.NUMBER),
            peoplePrivacy = storedChoice(p[Keys.PEOPLE_PRIVACY], PeoplePrivacy.ALL),
            peopleBatchEnabled = p[Keys.PEOPLE_BATCH_ENABLED] ?: false,
            peopleShowApps = p[Keys.PEOPLE_SHOW_APPS] ?: true,
            peopleBatchTimes = (p[Keys.PEOPLE_BATCH_TIMES] ?: LauncherSettings.DEFAULT_BATCH_TIMES)
                .let { BatchSchedule.parse(it) }
                .let { if (it.isEmpty()) LauncherSettings.DEFAULT_BATCH_TIMES else BatchSchedule.format(it) },
            stackAutoRotateSeconds = (p[Keys.stackAutoRotateSeconds(screenType)] ?: 0).coerceIn(0, LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS),
            stackCornerRadius = (p[Keys.stackCornerRadius(screenType)] ?: 24).coerceIn(0, LauncherSettings.MAX_STACK_CORNER_RADIUS),
            stackIndicator = storedChoice(p[Keys.stackIndicator(screenType)], StackIndicator.DOTS),
            stackShowPill = p[Keys.stackShowPill(screenType)] ?: false,
            mediaTheme = storedChoice(p[Keys.MEDIA_WIDGET_THEME], WidgetTheme.DEFAULT),
            appsTheme = storedChoice(p[Keys.APPS_WIDGET_THEME], WidgetTheme.DEFAULT),
            statsTheme = storedChoice(p[Keys.STATS_WIDGET_THEME], WidgetTheme.DEFAULT),
            noteTheme = storedChoice(p[Keys.NOTE_WIDGET_THEME], WidgetTheme.DEFAULT),
            mediaArtwork = p[Keys.MEDIA_WIDGET_ARTWORK] ?: true,
            appsLabels = p[Keys.APPS_WIDGET_LABELS] ?: false,
            statsShowBattery = p[Keys.STATS_SHOW_BATTERY] ?: true,
            statsShowStorage = p[Keys.STATS_SHOW_STORAGE] ?: true,
            statsShowMemory = p[Keys.STATS_SHOW_MEMORY] ?: true,
            statsShowBluetooth = p[Keys.STATS_SHOW_BLUETOOTH] ?: true,
            noteTextSize = storedChoice(p[Keys.NOTE_WIDGET_TEXT_SIZE], SizeChoice.MEDIUM),
            noteText = p[Keys.NOTE_WIDGET_TEXT] ?: "",
            motoWeatherTap = p[Keys.MOTO_WEATHER_TAP] ?: true,
            weatherAnimated = p[Keys.WEATHER_WIDGET_ANIMATED] ?: true,
            weatherAwakeSeconds = (p[Keys.WEATHER_AWAKE_SECONDS] ?: 45).coerceIn(0, 600),
            clockDarkText = p[Keys.CLOCK_WIDGET_DARK_TEXT] ?: false,
            clockGlassEffect = p[Keys.CLOCK_WIDGET_GLASS_EFFECT] ?: true,
            clockMinuteColor = storedChoice(p[Keys.CLOCK_MINUTE_COLOR], ClockMinuteColor.STYLE),
            clockMinuteOpacity = (p[Keys.CLOCK_MINUTE_OPACITY] ?: 1f).coerceIn(0.1f, 1f),
            clockStyle = storedChoice(p[Keys.CLOCK_WIDGET_STYLE], ClockStyle.CLASSIC),
            backgroundOpacity = (p[Keys.WIDGET_BACKGROUND_OPACITY] ?: 0f).coerceIn(0f, 1f),
        ),
        statusBar = StatusBarSettings(
            enabled = p[Keys.showStatusBar(screenType)] ?: false,
            hideSystem = p[Keys.hideSystemStatusBar(screenType)] ?: false,
            scrimOpacity = (p[Keys.statusBarScrimOpacity(screenType)] ?: 0.6f).coerceIn(0f, 1f),
            systemPadding = p[Keys.SYSTEM_BAR_PADDING] ?: false,
            systemPaddingInner = (p[Keys.SYSTEM_BAR_PADDING_INNER] ?: 14)
                .coerceIn(LauncherSettings.MIN_SYSTEM_PADDING, LauncherSettings.MAX_SYSTEM_PADDING),
            systemPaddingCover = (p[Keys.SYSTEM_BAR_PADDING_COVER] ?: 10)
                .coerceIn(LauncherSettings.MIN_SYSTEM_PADDING, LauncherSettings.MAX_SYSTEM_PADDING),
        ),
        fold = FoldSettings(
            halfOpenedModeEnabled = p[Keys.HALF_OPENED_ENABLED] ?: false,
            hingeTilt = p[Keys.HINGE_TILT] ?: true,
            wallpaperHinge = p[Keys.WALLPAPER_HINGE] ?: false,
            foldAction = storedChoice(p[Keys.foldAction(screenType)], FoldAction.STAY, FoldAction.ON_FOLD),
            unfoldAction = storedChoice(p[Keys.unfoldAction(screenType)], FoldAction.STAY, FoldAction.ON_UNFOLD),
            transitionSpeed = (p[Keys.foldTransitionSpeed(screenType)] ?: 1f)
                .coerceIn(LauncherSettings.MIN_FOLD_TRANSITION_SPEED, LauncherSettings.MAX_FOLD_TRANSITION_SPEED),
            transitionStyle = storedChoice(p[Keys.foldTransitionStyle(screenType)], FoldTransition.BOOK_UNFOLD_SWEEP),
            liveHingeEndAngle = (p[Keys.LIVE_HINGE_END_ANGLE] ?: 165).coerceIn(LauncherSettings.MIN_LIVE_HINGE_END_ANGLE, 180),
            liveHingeSmoothingMs = (p[Keys.LIVE_HINGE_SMOOTHING] ?: 50).coerceIn(0, LauncherSettings.MAX_LIVE_HINGE_SMOOTHING_MS),
            liveHingeStyle = storedChoice(p[Keys.LIVE_HINGE_STYLE], LiveHingeStyle.PAGES),
            liveHingeOuterStyle = storedChoice(p[Keys.LIVE_HINGE_OUTER_STYLE], LiveHingeStyle.WATER),
            liveHingeBlurDp = (p[Keys.LIVE_HINGE_BLUR] ?: 24).coerceIn(0, LauncherSettings.MAX_LIVE_HINGE_BLUR_DP),
            liveHingeFrost = (p[Keys.LIVE_HINGE_FROST] ?: 0.2f).coerceIn(0f, LauncherSettings.MAX_LIVE_HINGE_FROST),
        ),
        motion = MotionSettings(
            aquamorphicTouchEnabled = p[Keys.aquamorphicTouchEnabled(screenType)] ?: true,
            appLaunchZoomEnabled = p[Keys.appLaunchZoomEnabled(screenType)] ?: true,
            animationSpeed = (p[Keys.ANIMATION_SPEED] ?: 1f).coerceIn(LauncherSettings.MIN_ANIMATION_SPEED, LauncherSettings.MAX_ANIMATION_SPEED),
            appOpenAnimation = storedChoice(p[Keys.APP_OPEN_ANIMATION], AppOpenAnimation.ICON),
            hapticsEnabled = p[Keys.HAPTICS_ENABLED] ?: true,
            hapticsLaunch = p[Keys.HAPTICS_LAUNCH] ?: true,
            hapticsSplit = p[Keys.HAPTICS_SPLIT] ?: true,
            hapticsClose = p[Keys.HAPTICS_CLOSE] ?: true,
            hapticsRecents = p[Keys.HAPTICS_RECENTS] ?: true,
            hapticsDrag = p[Keys.HAPTICS_DRAG] ?: true,
            hapticsStrength = storedChoice(p[Keys.HAPTICS_STRENGTH], HapticsStrength.MEDIUM),
            unlockTransition = p[Keys.UNLOCK_TRANSITION] ?: true,
            unlockSystemHook = p[Keys.UNLOCK_SYSTEM_HOOK] ?: true,
            unlockStyle = storedChoice(p[Keys.UNLOCK_STYLE], UnlockStyle.DISPLAY),
            batterySaverMode = storedChoice(p[Keys.BATTERY_SAVER_MODE], BatterySaverMode.AUTO),
            batterySaverLowBattery = p[Keys.BATTERY_SAVER_LOW_BATTERY] ?: true,
            batterySaverWhenHot = p[Keys.BATTERY_SAVER_WHEN_HOT] ?: true,
        ),
        appearance = AppearanceSettings(
            amoledDark = p[Keys.amoledDark(screenType)] ?: false,
            popupTheme = storedChoice(p[Keys.popupTheme(screenType)], PopupTheme.CLASSIC),
            settingsStyle = storedChoice(p[Keys.SETTINGS_STYLE], SettingsStyle.STUDIO),
            liquidGlassEverywhere = p[Keys.LIQUID_GLASS_EVERYWHERE] ?: false,
            liquidGlassLevel = (p[Keys.LIQUID_GLASS_LEVEL] ?: 0.5f).coerceIn(0f, 1f),
            fontFamily = storedChoice(p[Keys.appFontFamily(screenType)], FontChoice.DEFAULT, FontChoice.BODY),
            headingFont = storedChoice(p[Keys.appHeadingFont(screenType)], FontChoice.SAME),
            fontBold = p[Keys.appFontBold(screenType)] ?: false,
            fontSizeScale = (p[Keys.appFontSizeScale(screenType)] ?: 1.0f).coerceIn(0.7f, 1.4f),
            dpiWorkspace = (p[Keys.dpiWorkspace(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
            dpiAppDrawer = (p[Keys.dpiAppDrawer(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
            dpiSettings = (p[Keys.dpiSettings(screenType)] ?: 1.0f).coerceIn(LauncherSettings.MIN_DPI_SCALE, LauncherSettings.MAX_DPI_SCALE),
        ),
        taskbar = TaskbarSettings(
            enabled = p[Keys.TASKBAR_ENABLED] ?: false,
            pinnedApps = p[Keys.TASKBAR_PINNED]?.split("\n")?.filter { it.isNotEmpty() },
            recentCount = (p[Keys.TASKBAR_RECENTS] ?: 4).coerceIn(0, 6),
            iconSize = (p[Keys.TASKBAR_ICON_SIZE] ?: 48).coerceIn(32, 72),
            iconSpacing = (p[Keys.TASKBAR_SPACING] ?: 8).coerceIn(0, 24),
            bottomOffset = (p[Keys.TASKBAR_BOTTOM] ?: 12).coerceIn(0, 64),
            clearSystemInset = p[Keys.TASKBAR_CLEAR_SYSTEM_INSET] ?: true,
            theme = storedChoice(p[Keys.TASKBAR_THEME], TaskbarTheme.CLEAR),
            opacity = (p[Keys.TASKBAR_OPACITY] ?: 0.8f).coerceIn(0.1f, 1f),
            glassBlur = (p[Keys.TASKBAR_GLASS_BLUR] ?: 24).coerceIn(0, 80),
            handles = storedChoice(p[Keys.TASKBAR_HANDLES], TaskbarHandles.BOTH),
            onHome = p[Keys.TASKBAR_ON_HOME] ?: true,
            keepOpen = p[Keys.TASKBAR_KEEP_OPEN] ?: false,
            mediaEnabled = p[Keys.TASKBAR_MEDIA] ?: true,
            mediaShowPaused = p[Keys.TASKBAR_MEDIA_PAUSED] ?: true,
            mediaPosition = storedChoice(p[Keys.TASKBAR_MEDIA_POSITION], TaskbarMediaPosition.MIDDLE),
            dividerStyle = storedChoice(p[Keys.TASKBAR_DIVIDER_STYLE], TaskbarDivider.LINE),
            mediaTap = storedChoice(p[Keys.TASKBAR_MEDIA_TAP], TaskbarMediaTap.OPEN),
            mediaControls = p[Keys.TASKBAR_MEDIA_CONTROLS] ?: true,
            mediaShape = storedChoice(p[Keys.TASKBAR_MEDIA_SHAPE], TaskbarMediaShape.CIRCLE),
            mediaBadge = p[Keys.TASKBAR_MEDIA_BADGE] ?: true,
            systemDrag = p[Keys.TASKBAR_SYSTEM_DRAG] ?: true,
            edgeSnap = p[Keys.TASKBAR_EDGE_SNAP] ?: true,
            recentsButton = storedChoice(p[Keys.TASKBAR_RECENTS_BUTTON], TaskbarRecentsButton.SYSTEM),
            quickSwitch = p[Keys.TASKBAR_QUICK_SWITCH] ?: true,
            splitControls = p[Keys.TASKBAR_SPLIT_CONTROLS] ?: true,
            splitSide = storedChoice(p[Keys.TASKBAR_SPLIT_SIDE], TaskbarSplitSide.RIGHT),
            threeApp = p[Keys.TASKBAR_THREE_APP] ?: true,
            hideFullScreen = p[Keys.TASKBAR_HIDE_FULL_SCREEN] ?: true,
            runningApps = p[Keys.TASKBAR_RUNNING_APPS] ?: false,
            runningDots = p[Keys.TASKBAR_RUNNING_DOTS] ?: true,
            multiInstanceDots = p[Keys.TASKBAR_MULTI_DOTS] ?: true,
            hiddenApps = lines(p[Keys.TASKBAR_HIDDEN_APPS]),
            alwaysApps = lines(p[Keys.TASKBAR_ALWAYS_APPS]),
            autoHideApps = lines(p[Keys.TASKBAR_AUTOHIDE_APPS]),
            miniApps = lines(p[Keys.TASKBAR_MINI_APPS]),
            handleHint = p[Keys.TASKBAR_HANDLE_HINT] ?: true,
            taskbarHandleStyle = storedChoice(p[Keys.TASKBAR_HANDLE_STYLE], TaskbarHandleStyle.LINE),
            hideButton = p[Keys.TASKBAR_HIDE_BUTTON] ?: true,
            pinsLocked = p[Keys.TASKBAR_PINS_LOCKED] ?: false,
            swipeDownHide = p[Keys.TASKBAR_SWIPE_DOWN_HIDE] ?: true,
            openPreviews = p[Keys.TASKBAR_OPEN_PREVIEWS] ?: false,
            taskbarAnim = storedChoice(p[Keys.TASKBAR_ANIM], TaskbarAnim.SLIDE),
            launchGlow = storedChoice(p[Keys.TASKBAR_LAUNCH_GLOW], LaunchGlow.APP),
            source = storedChoice(p[Keys.TASKBAR_SOURCE], TaskbarSource.FOLARK),
            systemMode = (p[Keys.SYSTEM_TASKBAR_MODE] ?: 1).let { if (it in listOf(1, 2, 4)) it else 1 },
            pinnedPairs = p[Keys.TASKBAR_PAIRS]?.split("\n")?.filter { it.isNotEmpty() }.orEmpty(),
            sceneSlots = (p[Keys.TASKBAR_SCENE_SLOTS] ?: 0).coerceIn(0, 4),
            coverBar = p[Keys.TASKBAR_COVER_BAR] ?: false,
            coverCount = (p[Keys.TASKBAR_COVER_COUNT] ?: 5).coerceIn(LauncherSettings.MIN_COVER_BAR, LauncherSettings.MAX_COVER_BAR),
        ),
        live = LiveSettings(
            popupNotifications = p[Keys.POPUP_NOTIFICATIONS] ?: true,
            activities = p[Keys.LIVE_ACTIVITIES] ?: true,
            activitiesTaskbar = p[Keys.LIVE_ACTIVITIES_TASKBAR] ?: true,
            expandOnLongPress = p[Keys.LIVE_EXPAND] ?: true,
            skipSystemChips = p[Keys.LIVE_SKIP_SYSTEM_CHIPS] ?: true,
            activityEffect = storedChoice(p[Keys.LIVE_ACTIVITY_EFFECT], LiveEffect.GLOW),
        ),
        recents = readRecents(p).let { r ->
            if (p[Keys.RECENTS_OUTER_LOOK] == true) r.copy(outerLookEnabled = true, outerLook = readRecents(coverLook(p))) else r
        },
        root = RootSettings(
            enabled = p[Keys.ROOT_ENABLED] ?: true,
            lockMode = storedChoice(p[Keys.ROOT_LOCK_MODE], RootMode.FALLBACK),
            recentsMode = storedChoice(p[Keys.ROOT_RECENTS_MODE], RootMode.FALLBACK),
            shadeMode = storedChoice(p[Keys.ROOT_SHADE_MODE], RootMode.FALLBACK),
            keepDump = p[Keys.ROOT_KEEP_DUMP] ?: false,
            keepUsage = p[Keys.ROOT_KEEP_USAGE] ?: false,
            keepSecureSettings = p[Keys.ROOT_KEEP_SECURE] ?: false,
            keepNotifications = p[Keys.ROOT_KEEP_NOTIFICATIONS] ?: false,
            keepAccessibility = p[Keys.ROOT_KEEP_ACCESSIBILITY] ?: false,
            keepWidgets = p[Keys.ROOT_KEEP_WIDGETS] ?: false,
        ),
        launch = LaunchSettings(
            splitEnabled = p[Keys.SPLIT_LAUNCH_ENABLED] ?: true,
            floatingEnabled = p[Keys.FLOATING_LAUNCH_ENABLED] ?: true,
            floatingWindowSize = storedChoice(p[Keys.FLOATING_WINDOW_SIZE], SizeChoice.MEDIUM),
            threeAppSplit = p[Keys.THREE_APP_SPLIT] ?: true,
            threeAppNative = p[Keys.THREE_APP_NATIVE] ?: false,
            aspectRatioMenu = p[Keys.ASPECT_RATIO_MENU] ?: true,
        ),
    )
}

internal fun lines(raw: String?): Set<String> = raw?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

/** Settings ▸ Recents, read from [p]. */
private fun readRecents(p: Preferences): RecentsSettings =
RecentsSettings(
    enabled = p[Keys.RECENTS_ENABLED] ?: true,
    replaceSystem = p[Keys.RECENTS_REPLACE_SYSTEM] ?: false,
    navHandleOpensSearch = p[Keys.NAV_HANDLE_SEARCH] ?: false,
    columnsInner = (p[Keys.RECENTS_COLUMNS_INNER] ?: 4)
        .coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_INNER),
    columnsOuter = (p[Keys.RECENTS_COLUMNS_OUTER] ?: 1)
        .coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_OUTER),
    maxApps = (p[Keys.RECENTS_MAX_APPS] ?: 12).coerceIn(LauncherSettings.MIN_RECENTS_APPS, LauncherSettings.MAX_RECENTS_APPS),
    cardHeight = (p[Keys.RECENTS_CARD_HEIGHT] ?: 300)
        .coerceIn(LauncherSettings.MIN_RECENTS_CARD_HEIGHT, LauncherSettings.MAX_RECENTS_CARD_HEIGHT),
    cornerDp = (p[Keys.RECENTS_CORNER] ?: 26).coerceIn(0, LauncherSettings.MAX_RECENTS_CORNER_DP),
    backdropDim = (p[Keys.RECENTS_BACKDROP] ?: 0.85f).coerceIn(0f, 1f),
    sort = storedChoice(p[Keys.RECENTS_SORT], RecentsSort.RECENT),
    showTime = p[Keys.RECENTS_SHOW_TIME] ?: true,
    cardLayout = storedChoice(p[Keys.RECENTS_CARD_LAYOUT], RecentsCardLayout.OVERLAY),
    confirmCloseAll = p[Keys.RECENTS_CONFIRM_CLOSE_ALL] ?: false,
    thirdInSplit = p[Keys.RECENTS_THIRD_IN_SPLIT] ?: true,
    thumbnails = p[Keys.RECENTS_THUMBNAILS] ?: true,
    speakerBadge = p[Keys.RECENTS_SPEAKER_BADGE] ?: false,
    groupSplits = p[Keys.RECENTS_GROUP_SPLITS] ?: true,
    memoryBadge = p[Keys.RECENTS_MEMORY_BADGE] ?: false,
    rootClose = p[Keys.RECENTS_ROOT_CLOSE] ?: true,
    enterAnimation = storedChoice(p[Keys.RECENTS_ENTER], RecentsEnter.RISE),
    selectAnimation = storedChoice(p[Keys.RECENTS_SELECT], RecentsSelect.POP),
    animationSpeed = (p[Keys.RECENTS_ANIM_SPEED] ?: 1f)
        .coerceIn(LauncherSettings.MIN_RECENTS_ANIM_SPEED, LauncherSettings.MAX_RECENTS_ANIM_SPEED),
    pressAnimation = storedChoice(p[Keys.RECENTS_PRESS], PressEffect.SHRINK),
    theme = storedChoice(p[Keys.RECENTS_THEME], RecentsTheme.FOLARK),
    glass = p[Keys.RECENTS_GLASS] ?: false,
    glassBlur = (p[Keys.RECENTS_GLASS_BLUR] ?: 28).coerceIn(0, LauncherSettings.MAX_RECENTS_GLASS_BLUR),
    glassTint = (p[Keys.RECENTS_GLASS_TINT] ?: 0.16f)
        .coerceIn(LauncherSettings.MIN_RECENTS_GLASS_TINT, LauncherSettings.MAX_RECENTS_GLASS_TINT),
    dragDrop = p[Keys.RECENTS_DRAG_DROP] ?: true,
    overlayLabels = p[Keys.RECENTS_OVERLAY_LABELS] ?: true,
    cardIcons = p[Keys.RECENTS_CARD_ICONS] ?: true,
    cardShadow = p[Keys.RECENTS_CARD_SHADOW] ?: true,
    labelScale = (p[Keys.RECENTS_LABEL_SCALE] ?: 1f)
        .coerceIn(LauncherSettings.MIN_RECENTS_LABEL_SCALE, LauncherSettings.MAX_RECENTS_LABEL_SCALE),
    menuMotion = p[Keys.RECENTS_MENU_MOTION] ?: true,
    scrollTicks = p[Keys.RECENTS_SCROLL_TICKS] ?: false,
    btnClose = p[Keys.RECENTS_BTN_CLOSE] ?: true,
    btnSplit = p[Keys.RECENTS_BTN_SPLIT] ?: true,
    btnPopup = p[Keys.RECENTS_BTN_POPUP] ?: true,
    btnUnsplit = p[Keys.RECENTS_BTN_UNSPLIT] ?: true,
    btnSplitView = p[Keys.RECENTS_BTN_SPLIT_VIEW] ?: true,
    btnCloseAll = p[Keys.RECENTS_BTN_CLOSE_ALL] ?: true,
    swipeUpAction = storedChoice(p[Keys.RECENTS_SWIPE_UP], RecentsAction.CLOSE),
    swipeDownAction = storedChoice(p[Keys.RECENTS_SWIPE_DOWN], RecentsAction.NONE),
    swipeLeftAction = storedChoice(p[Keys.RECENTS_SWIPE_LEFT], RecentsAction.CLOSE),
    swipeRightAction = storedChoice(p[Keys.RECENTS_SWIPE_RIGHT], RecentsAction.CLOSE),
    longPressAction = storedChoice(p[Keys.RECENTS_LONG_PRESS], RecentsAction.MENU),
    doubleTapAction = storedChoice(p[Keys.RECENTS_DOUBLE_TAP], RecentsAction.SPLIT),
    outerGestures = p[Keys.RECENTS_OUTER_GESTURES] ?: true,
    outerSwipeUpAction = storedChoice(p[Keys.RECENTS_OUTER_SWIPE_UP], RecentsAction.CLOSE),
    outerSwipeDownAction = storedChoice(p[Keys.RECENTS_OUTER_SWIPE_DOWN], RecentsAction.OPEN),
    outerSwipeLeftAction = storedChoice(p[Keys.RECENTS_OUTER_SWIPE_LEFT], RecentsAction.CLOSE),
    outerSwipeRightAction = storedChoice(p[Keys.RECENTS_OUTER_SWIPE_RIGHT], RecentsAction.CLOSE),
    outerLongPressAction = storedChoice(p[Keys.RECENTS_OUTER_LONG_PRESS], RecentsAction.MENU),
    outerDoubleTapAction = storedChoice(p[Keys.RECENTS_OUTER_DOUBLE_TAP], RecentsAction.POPUP),
    pinned = lines(p[Keys.RECENTS_PINNED]),
    btnPin = p[Keys.RECENTS_BTN_PIN] ?: true,
    pinKeepAlive = p[Keys.RECENTS_PIN_KEEP_ALIVE] ?: true,
    attachedSide = storedChoice(p[Keys.RECENTS_ATTACHED_SIDE], RecentsAttachedSide.LEFT),
    attachedPeek = (p[Keys.RECENTS_ATTACHED_PEEK] ?: 0.12f)
        .coerceIn(LauncherSettings.MIN_RECENTS_ATTACHED_PEEK, LauncherSettings.MAX_RECENTS_ATTACHED_PEEK),
    attachedNative = p[Keys.RECENTS_ATTACHED_NATIVE] ?: false,
    splitEngine = p[Keys.RECENTS_SPLIT_ENGINE]?.takeIf { it in LauncherSettings.SPLIT_ENGINES }
        ?: LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING,
    picker = splitPickerLook(p),
    dropZones = DropZones.ALL.fold(DropZones(haptics = p[Keys.DROP_ZONE_HAPTICS] ?: true)) { z, id ->
        z.with(id, p[Keys.dropZone(id)] ?: true)
    },
    splitUndo = p[Keys.SPLIT_UNDO] ?: true,
    dividerStops = p[Keys.DIVIDER_STOPS] ?: true,
    threeEven = p[Keys.SPLIT_THREE_EVEN] ?: true,
    freeDivider = p[Keys.SPLIT_FREE_DIVIDER] ?: true,
    dividerLock = storedChoice(p[Keys.SPLIT_DIVIDER_LOCK], DividerLock.OFF),
    dividerMarks = p[Keys.SPLIT_DIVIDER_MARKS] ?: true,
    dividerHaptics = p[Keys.SPLIT_DIVIDER_HAPTICS] ?: true,
    keepSplitSize = p[Keys.SPLIT_KEEP_SIZE] ?: true,
    rememberSplitSize = p[Keys.SPLIT_REMEMBER_SIZE] ?: true,
    splitDragHint = p[Keys.SPLIT_DRAG_HINT] ?: true,
    peekFlingClose = p[Keys.PEEK_FLING_CLOSE] ?: false,
    splitRatioMenu = p[Keys.SPLIT_RATIO_MENU] ?: true,
    openWith = p[Keys.SPLIT_OPEN_WITH] ?: false,
    notificationSplit = p[Keys.NOTIFICATION_SPLIT] ?: true,
)

private fun splitPickerLook(p: Preferences): SplitPickerLook = SplitPickerLook(
    columns = (p[Keys.SPLIT_PICKER_COLUMNS] ?: 5).coerceIn(SplitPickerLook.MIN_COLUMNS, SplitPickerLook.MAX_COLUMNS),
    iconSize = (p[Keys.SPLIT_PICKER_ICON] ?: 46).coerceIn(SplitPickerLook.MIN_ICON, SplitPickerLook.MAX_ICON),
    labels = p[Keys.SPLIT_PICKER_LABELS] ?: true,
    folders = p[Keys.SPLIT_PICKER_FOLDERS] ?: true,
    recents = storedChoice(p[Keys.SPLIT_PICKER_RECENTS], PickerRecents.ICONS),
    recentsCount = (p[Keys.SPLIT_PICKER_RECENTS_COUNT] ?: 5).coerceIn(SplitPickerLook.MIN_RECENTS, SplitPickerLook.MAX_RECENTS),
    sort = storedChoice(p[Keys.SPLIT_PICKER_SORT], PickerSort.NAME),
    search = p[Keys.SPLIT_PICKER_SEARCH] ?: false,
    opacity = (p[Keys.SPLIT_PICKER_OPACITY] ?: 0.95f).coerceIn(SplitPickerLook.MIN_OPACITY, SplitPickerLook.MAX_OPACITY),
    openAnim = storedChoice(p[Keys.SPLIT_PICKER_OPEN_ANIM], PickerOpen.ZOOM),
    favorites = p[Keys.SPLIT_PICKER_FAVORITES] ?: true,
    favoriteKeys = orderedLines(p[Keys.SPLIT_PICKER_FAVORITE_KEYS]),
    recentPairs = p[Keys.SPLIT_PICKER_RECENT_PAIRS] ?: true,
    peekToggle = p[Keys.SPLIT_PICKER_PEEK_TOGGLE] ?: true,
    longPress = p[Keys.SPLIT_PICKER_LONG_PRESS] ?: true,
)

/** Like [lines], keeping the order (first added first). */
internal fun orderedLines(raw: String?): List<String> =
    raw?.split("\n")?.filter { it.isNotBlank() }?.distinct() ?: emptyList()

/** [p] with every Recents look setting the outer screen has its own value for replaced by it. */
@Suppress("UNCHECKED_CAST")
private fun coverLook(p: Preferences): Preferences = p.toMutablePreferences().apply {
    Keys.RECENTS_LOOK_KEYS.forEach { (unfolded, cover) ->
        p[cover as Preferences.Key<Any>]?.let { this[unfolded as Preferences.Key<Any>] = it }
    }
}
