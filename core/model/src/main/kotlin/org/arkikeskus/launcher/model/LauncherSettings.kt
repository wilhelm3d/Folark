package org.arkikeskus.launcher.model

/** User-configurable launcher settings (persisted in DataStore). */
import androidx.compose.runtime.Immutable

@Immutable
data class LauncherSettings(
    val dockEnabled: Boolean = true,
    val dockColumns: Int = 4,
    val homeColumns: Int = 4,
    /** Home-grid row count — more rows = tighter icon rows + more free cells for widgets. */
    val homeRows: Int = 6,
    val drawerColumns: Int = 4,
    val drawerRows: Int = 6,
    val showDrawerSearch: Boolean = true,
    val showDockLabels: Boolean = false,
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val drawerSearchPosition: String = SEARCH_TOP,
    val drawerIndexBarEnabled: Boolean = true,
    val azIndexPosition: String = AZ_POSITION_VERTICAL_RIGHT,
    /** Size of the A-Z index bar's letters and touch strip: 1 = normal. */
    val azIndexScale: Float = 1f,
    /** Corner radius of the app drawer panel in dp; [DRAWER_CORNER_AUTO] follows the drawer style. */
    val drawerCornerRadius: Int = DRAWER_CORNER_AUTO,
    /** The pull handle at the top of the drawer: a bar, or a small arrow that turns as the drawer moves. */
    val drawerHandleStyle: String = DRAWER_HANDLE_BAR,
    /** Extra room kept free under the drawer, in dp (for a system taskbar that the system does not report). */
    val drawerBottomSpace: Int = 0,
    /** Small "A", "B", "C" headings between the apps of the full list. */
    val drawerLetterHeaders: Boolean = false,
    /** Show the letter under the finger large in the middle of the drawer while using the A-Z bar. */
    val drawerLargeLetterPopup: Boolean = true,
    /** A sideways swipe on the app list moves between the drawer's folder tabs. */
    val drawerSwipeTabs: Boolean = true,
    /** A fast downward flick closes the drawer even when the list is scrolled down. */
    val drawerFlickClose: Boolean = false,
    /** With themed icons on: also theme the apps that ship no themed icon (both displays; best effort). */
    val forceThemedIcons: Boolean = false,
    /** How themed icons are drawn (both displays; one of THEMED_STYLE_*). */
    val themedIconStyle: String = THEMED_STYLE_CLASSIC,
    val drawerBackgroundBlur: Float = 0f,
    val drawerScrimOpacity: Float = 0.5f,
    val glassBlurRadius: Float = 25.0f,
    val glassDarkTint: Float = 0.45f,
    val drawerStyle: String = DRAWER_STYLE_STANDARD_GRID,
    /** Drawer background theme, one of the DRAWER_THEME_* values. [DRAWER_THEME_SYSTEM] follows the
     *  launcher theme (light/dark), which is what the drawer always did before themes were wired up. */
    val drawerThemeTint: String = DRAWER_THEME_SYSTEM,
    val drawerAccentColor: Int = DEFAULT_DRAWER_ACCENT,
    /** ARGB background used by [DRAWER_THEME_CUSTOM]. */
    val drawerCustomColor: Int = DEFAULT_DRAWER_CUSTOM_COLOR,
    /** Liquid Glass: the drawer panel becomes translucent ([drawerPanelOpacity]) with a specular rim
     *  and sheen, over the wallpaper blurred by [drawerBackgroundBlur]. */
    val drawerGlassEnabled: Boolean = false,
    /** Opacity of the drawer panel while [drawerGlassEnabled] is on (lower = more see-through). */
    val drawerPanelOpacity: Float = DEFAULT_DRAWER_PANEL_OPACITY,
    val drawerHeightFraction: Float = 1.0f,
    val drawerWidthFraction: Float = 1.0f,
    val drawerAlignment: String = DRAWER_ALIGNMENT_CENTER,
    val dockBackgroundOpacity: Float = 0.35f,
    val showPageIndicator: Boolean = true,
    val showNotificationDots: Boolean = true,
    /** When notification dots are on: true shows the count (Nova-style), false shows a plain dot. */
    val notificationDotCount: Boolean = true,
    /** Size multiplier for the notification dot/badge (1.0 = default). */
    val notificationDotScale: Float = 1.0f,
    /** Render app icons as Material You themed (monochrome) icons where the app provides one. */
    val useThemedIcons: Boolean = false,
    /** Package name of the selected third-party icon pack (empty = system default). Overrides
     *  [useThemedIcons] when set: mapped apps use the pack's icon, unmapped apps are masked to its style. */
    val iconPackPackage: String = "",
    /** Include contacts in app-drawer search (gated by READ_CONTACTS; requested when enabled). */
    val searchContacts: Boolean = false,
    /** App key launched by the left-edge home swipe; blank = gesture disabled. */
    val leftSwipeAppKey: String = "",
    /** App key launched by the right-edge home swipe; blank = gesture disabled. */
    val rightSwipeAppKey: String = "",
    /** Lock the desktop layout: when true, home + dock items can't be moved, removed, or added. */
    val desktopLocked: Boolean = false,
    /** Show a "most used" row (top apps by decayed launch frequency) above the drawer's app list. */
    val showFrequentApps: Boolean = false,
    /** Reopen the app drawer scrolled back to the top (Pixel-style) rather than remembering the last
     *  scroll position — keeps the "most used" row and A–Z start visible on every open. */
    val drawerOpensAtTop: Boolean = true,
    /** Size multiplier for app icon labels across home/dock/drawer/folders (1.0 = the default 11sp). */
    val appLabelTextScale: Float = 1.0f,
    /** Let home-screen labels (apps, folders, shortcuts) wrap onto a second line. Off keeps the
     *  single line the home grid has always had; the dock stays single-line either way. */
    val twoLineHomeLabels: Boolean = false,
    /** Let app-drawer labels (apps, folders, the "most used" row) wrap onto a second line — the
     *  drawer's long-standing look, so this defaults on. */
    val twoLineDrawerLabels: Boolean = true,
    /** ARGB color for app icon labels on the home surfaces (home/dock/folders); default white. The
     *  app drawer keeps its theme color for readability over its solid background. */
    val appLabelColor: Int = 0xFFFFFFFF.toInt(),
    /** Show a slim status bar (clock + battery + signal, with dynamic battery/signal colors) at the top
     *  of the home screen. */
    val showStatusBar: Boolean = false,
    /** Show the current weather (Open-Meteo; needs a location permission) in the smartspace widget. */
    val showWeather: Boolean = true,
    /** Hide the system status bar while the launcher is in the foreground (immersive/fullscreen home).
     *  Independent of [showStatusBar]; combine the two to replace the system bar with the themed one. */
    val hideSystemStatusBar: Boolean = false,
    /** Darkness of the scrim drawn behind the themed status bar (0 = none, 1 = solid black); keeps the
     *  clock/indicators legible over bright wallpapers. Own setting, like [dockBackgroundOpacity]. */
    val statusBarScrimOpacity: Float = 0.6f,
    /** Count indicator of the built-in notifications widget: [COUNT_NUMBER], [COUNT_DOT] or [COUNT_NONE]. */
    /** Use theme surfaces for built-in widgets; false keeps the translucent wallpaper style. */
    val widgetTonalBackground: Boolean = false,
    val notificationWidgetCountStyle: String = COUNT_NUMBER,
    /** What the built-in people widget shows of a notification on the (shared) home screen:
     *  [PRIVACY_ALL] = sender + message text, [PRIVACY_SENDER] = sender only, [PRIVACY_COUNT] = the
     *  tile colors and counts but shows no text at all (pinned people keep their user-chosen name). */
    val peoplePrivacy: String = PRIVACY_ALL,
    /** Hold messages from people who aren't pinned and deliver them in batches at [peopleBatchTimes]
     *  in launcher surfaces only. Android notifications remain active so new messages from pinned
     *  people and one-time codes can bypass the batch. Missed calls always come through. */
    val peopleBatchEnabled: Boolean = false,
    /** Comma-separated "HH:mm" delivery times for the batch; see BatchSchedule. */
    val peopleBatchTimes: String = DEFAULT_BATCH_TIMES,
    /** Also show notifications that aren't from a person in the people widget, grouped by app. */
    val peopleShowApps: Boolean = true,
    /** The page the launcher opens on and the HOME button returns to (0 = the leftmost). */
    val homePage: Int = 0,
    /** Pages the user added explicitly (0 = none): the home keeps at least this many pages even
     *  when some are empty. Pages that hold an icon or widget exist regardless. */
    val homePageCount: Int = 0,
    /** Allow home screen landscape rotation on this screen profile. */
    val allowLandscape: Boolean = false,
    /** Swipe-down gesture action: [GESTURE_NOTIFICATIONS], [GESTURE_DRAWER], [GESTURE_SEARCH], [GESTURE_LOCK], [GESTURE_NONE]. */
    val swipeDownAction: String = GESTURE_NOTIFICATIONS,
    /** Double-tap gesture action on empty space: [GESTURE_NOTIFICATIONS], [GESTURE_DRAWER], [GESTURE_SEARCH], [GESTURE_LOCK], [GESTURE_NONE]. */
    val doubleTapAction: String = GESTURE_NONE,
    /** Swipe-up gesture action: [GESTURE_DRAWER], [GESTURE_SEARCH], [GESTURE_NONE]. */
    val swipeUpAction: String = GESTURE_DRAWER,
    /** Enable tabletop / half-opened layout mode when the device is half-folded. */
    val halfOpenedModeEnabled: Boolean = false,
    /** Half folded: home leans with the hinge (needs the half-folded layout and a hinge sensor). */
    val hingeTilt: Boolean = true,
    /** AMOLED dark mode (pure black backgrounds and surfaces). */
    val amoledDark: Boolean = false,
    val foldAction: String = ACTION_STAY,
    val unfoldAction: String = ACTION_STAY,
    val foldTransitionStyle: String = TRANSITION_BOOK_UNFOLD_SWEEP,
    /** How fast this display's switch animation plays (1 = normal; higher is faster). */
    val foldTransitionSpeed: Float = 1f,
    /** The same transition plays when the phone is unlocked straight to home (both displays). */
    val unlockTransition: Boolean = true,
    val aquamorphicTouchEnabled: Boolean = true,
    val appLaunchZoomEnabled: Boolean = true,
    val pageBounceEnabled: Boolean = true,
    /** Size multiplier for app, folder and shortcut icons on home, the dock and the drawer. A grid
     *  cell still caps it, so a dense grid may not be able to grow its icons. */
    val iconSizeScale: Float = 1.0f,
    /** Home folders: tile shape, colour theme and what the closed tile previews. */
    val folderShape: String = FOLDER_SHAPE_SQUIRCLE,
    val folderTheme: String = FOLDER_THEME_GLASS,
    val folderPreview: String = FOLDER_PREVIEW_GRID_2,
    /** Home folders, opened: columns of the app grid and the overall size (icons + sheet height). */
    val folderOpenColumns: Int = 4,
    val folderOpenSize: String = FOLDER_OPEN_MEDIUM,
    /** Tap a folder → launch its first app; a long-press (without moving) opens the folder. */
    val folderTapFirstApp: Boolean = false,
    /** Look of the long-press menus (one of POPUP_THEME_*). */
    val popupTheme: String = POPUP_THEME_CLASSIC,
    /** Widget stacks: corner radius (dp), page indicator style and whether the options pill shows. */
    val stackCornerRadius: Int = 24,
    /** Seconds after which a widget stack moves on to its next widget by itself; 0 = never. */
    val stackAutoRotateSeconds: Int = 0,
    val stackIndicator: String = STACK_INDICATOR_DOTS,
    val stackShowPill: Boolean = false,
    /** Notification dots: shape, colour and corner of the icon. */
    val dotShape: String = DOT_SHAPE_CIRCLE,
    val dotColor: String = DOT_COLOR_RED,
    val dotPosition: String = DOT_POSITION_TOP_END,
    /** Home screen backdrop: darkening (0..0.8) and blur (dp) of the wallpaper behind the icons. */
    val homeWallpaperDim: Float = 0f,
    val homeWallpaperBlur: Float = 0f,
    /** Path of this display's own home background picture ("" = show the system wallpaper). */
    val homeBackgroundImage: String = "",
    /** What a swipe up on a home app icon does (one of ICON_SWIPE_*). */
    val iconSwipeUpAction: String = ICON_SWIPE_NONE,
    /** Remove a home page as soon as its last item leaves it. */
    val autoRemoveEmptyPages: Boolean = false,
    /** Dragging an item past the last page opens a new, empty page for it. */
    val newPageOnDrag: Boolean = false,
    /** Show the drawer's folders as tabs above the app list instead of tiles inside it. */
    val drawerFolderTabs: Boolean = false,
    /** Size of a newly opened floating window (both displays; one of FLOATING_SIZE_*). */
    val floatingWindowSize: String = FLOATING_SIZE_MEDIUM,
    // --- Folark's own widgets (both displays): a theme each, plus their options -----------------
    /** Now playing: theme, and whether the cover art fills the widget's background. */
    val mediaWidgetTheme: String = WIDGET_THEME_DEFAULT,
    val mediaWidgetArtwork: Boolean = true,
    /** Most used apps: theme, and whether the icons carry labels. */
    val appsWidgetTheme: String = WIDGET_THEME_DEFAULT,
    val appsWidgetLabels: Boolean = false,
    /** Device status: theme, and which gauges it shows. */
    val statsWidgetTheme: String = WIDGET_THEME_DEFAULT,
    val statsShowBattery: Boolean = true,
    val statsShowStorage: Boolean = true,
    val statsShowMemory: Boolean = true,
    val statsShowBluetooth: Boolean = true,
    /** Quick note: theme, text size (FOLDER_OPEN_SMALL/MEDIUM/LARGE) and the note itself. */
    val noteWidgetTheme: String = WIDGET_THEME_DEFAULT,
    val noteWidgetTextSize: String = FOLDER_OPEN_MEDIUM,
    val noteWidgetText: String = "",
    /** Search (both displays): offer app shortcuts and a "search the web" row. */
    val searchShortcuts: Boolean = true,
    val searchWeb: Boolean = true,
    /** How solid the card behind Folark's own widgets is (both displays): 0 = see-through, 1 = as themed. */
    val widgetBackgroundOpacity: Float = 0f,
    /** Digital clock widget (both displays): dark text instead of white. */
    val clockWidgetDarkText: Boolean = false,
    /** Digital clock widget (both displays): its look, one of CLOCK_STYLE_*. */
    val clockWidgetStyle: String = CLOCK_STYLE_CLASSIC,
    /** Digital clock widget (both displays): frosted liquid glass card backdrop. */
    val clockWidgetGlassEffect: Boolean = true,
    /** Digital clock widget (both displays): the minutes' colour, one of CLOCK_MINUTE_*. */
    val clockMinuteColor: String = CLOCK_MINUTE_STYLE,
    /** Digital clock widget (both displays): how opaque the minutes are, 0.1..1. */
    val clockMinuteOpacity: Float = 1f,
    /** Per-display font family: "default", "ndot", "ntype", "space_grotesk", "serif", "monospace". */
    val appFontFamily: String = FONT_FAMILY_DEFAULT,
    /** Per-display font bold style. */
    val appFontBold: Boolean = false,
    /** Per-display font size scale (0.7f..1.4f). */
    val appFontSizeScale: Float = 1.0f,
    /** Weather day widget (both displays): animated conditions behind it. */
    val weatherWidgetAnimated: Boolean = true,
    /** Folark's battery saver (both displays): off, on, or following the system's battery saver. */
    val batterySaverMode: String = BATTERY_SAVER_AUTO,
    // --- Animations (both displays) ---------------------------------------------------------------
    /** How fast Folark's own animations run: 1 = normal, 2 = twice as fast, 0.5 = half speed. */
    val animationSpeed: Float = 1f,
    /** How an app opens from the launcher (one of APP_OPEN_*). */
    val appOpenAnimation: String = APP_OPEN_ICON,
    /** How a home folder opens (one of FOLDER_ANIM_*). */
    val folderOpenAnimation: String = FOLDER_ANIM_SHEET,
    /** What the home pages do while being swiped (one of PAGE_EFFECT_*). */
    val pageScrollEffect: String = PAGE_EFFECT_SLIDE,
    /** A pull-up taskbar over other apps on the inner display (needs the accessibility service). */
    val taskbarEnabled: Boolean = false,
    /** Taskbar: the apps always in it, in order; null = the inner dock's apps. */
    val taskbarPinnedApps: List<String>? = null,
    /** Taskbar: how many recently used apps follow the pinned ones (0..6). */
    val taskbarRecentCount: Int = 4,
    /** Taskbar: icon size in dp. */
    val taskbarIconSize: Int = 48,
    /** Taskbar: room between icons in dp. */
    val taskbarIconSpacing: Int = 8,
    /** Taskbar: distance from the bottom edge in dp. */
    val taskbarBottomOffset: Int = 12,
    /** Taskbar: its look, one of TASKBAR_THEME_*. */
    val taskbarTheme: String = TASKBAR_THEME_CLEAR,
    /** Taskbar: how solid the backdrop of the non-clear themes is. */
    val taskbarOpacity: Float = 0.8f,
    /** Taskbar: which bottom corners open it over other apps, one of TASKBAR_HANDLES_*. */
    val taskbarHandles: String = TASKBAR_HANDLES_BOTH,
    /** Taskbar: always shown on Folark's home screen and app drawer. */
    val taskbarOnHome: Boolean = true,
    /** Taskbar: stays open over other apps instead of opening from the corners. */
    val taskbarKeepOpen: Boolean = false,
    /** Taskbar: an icon with the cover of what is playing. */
    val taskbarMediaEnabled: Boolean = true,
    /** Taskbar media icon: also shown while paused (not only while playing). */
    val taskbarMediaShowPaused: Boolean = true,
    /** Taskbar media icon: where it sits, one of TASKBAR_MEDIA_POS_*. */
    val taskbarMediaPosition: String = TASKBAR_MEDIA_POS_MIDDLE,
    /** Taskbar media icon: what a tap does, one of TASKBAR_MEDIA_TAP_*. */
    val taskbarMediaTap: String = TASKBAR_MEDIA_TAP_OPEN,
    /** Taskbar media icon: pulling it up shows back, play/pause and forward. */
    val taskbarMediaControls: Boolean = true,
    /** Taskbar media icon: the cover's shape, one of TASKBAR_MEDIA_SHAPE_*. */
    val taskbarMediaShape: String = TASKBAR_MEDIA_SHAPE_CIRCLE,
    /** Taskbar media icon: the player app's icon as a small badge on the cover. */
    val taskbarMediaBadge: Boolean = true,
    /** Taskbar over an app: a drag to the right (an icon or a shortcut) uses Android's own split drop zones. */
    val taskbarSystemDrag: Boolean = true,
    /** Taskbar: the recents button, one of TASKBAR_RECENTS_*. */
    val taskbarRecentsButton: String = TASKBAR_RECENTS_SYSTEM,
    /** Taskbar: holding the recents button goes back to the last app. */
    val taskbarQuickSwitch: Boolean = true,
    /** Taskbar: a button for the split screen (swap sides, one app full screen). */
    val taskbarSplitControls: Boolean = true,
    /** Taskbar: the side a new split-screen app opens on, one of TASKBAR_SPLIT_SIDE_*. */
    val taskbarSplitSide: String = TASKBAR_SPLIT_SIDE_RIGHT,
    /** Taskbar: a drag over two apps side by side can add a third (Android's drop zones). */
    val taskbarThreeApp: Boolean = true,
    /** Taskbar: kept-open bar steps aside while an app is full screen (video, games). */
    val taskbarHideFullScreen: Boolean = true,
    /** Look of the settings screens (both displays), one of SETTINGS_STYLE_*. */
    val settingsStyle: String = SETTINGS_STYLE_STUDIO,
    /** Shape of app icons (both displays), one of ICON_SHAPE_*; "system" leaves them as drawn. */
    val iconShape: String = ICON_SHAPE_SYSTEM,
    /** A tap on Folark's weather opens Motorola Weather (where the phone has it). */
    val motoWeatherTap: Boolean = true,
    /** The drawer's Private Space section (both displays; where the phone has a Private Space). */
    val showPrivateSpace: Boolean = true,
    /** An app's long-press menu lists its current notifications (both displays). */
    val popupNotifications: Boolean = true,
    /** Chat apps' notification dots show the sender's photo (both displays). */
    val dotAvatars: Boolean = true,
    /** Ongoing things (a timer, navigation, a call, a download, Android 16 live updates) as a
     *  live pill at the top of home, and on the taskbar; [liveActivityEffect] is one of LIVE_EFFECT_*. */
    val liveActivities: Boolean = true,
    val liveActivitiesTaskbar: Boolean = true,
    val liveActivityEffect: String = LIVE_EFFECT_GLOW,
    /** An app's icon nudges when a new notification arrives while home is in view, and on the
     *  taskbar; [iconNudgeEffect] is one of NUDGE_*. */
    val iconNudge: Boolean = true,
    val iconNudgeTaskbar: Boolean = true,
    val iconNudgeEffect: String = NUDGE_BOUNCE,
    /** How soft Glow, Heartbeat and Colour are (0 = strong and sharp, 1 = gentle and wide). */
    val iconNudgeSoftness: Float = 0.5f,
    /** Quick actions in search: timers, alarms, events, directions, email, notes, numbers. */
    val searchActions: Boolean = true,
    /** Folark's own Recents screen (all shared by both displays): opened by the taskbar's Recents
     *  button ([TASKBAR_RECENTS_FOLARK]) or a home gesture ([GESTURE_RECENTS]). */
    val recentsEnabled: Boolean = true,
    /** Android's own Recents (swipe up and hold, the stock taskbar, the Recents key) hands over to
     *  Folark Recents as soon as it opens (through Folark's accessibility service). */
    val recentsReplaceSystem: Boolean = false,
    /** Cards per row on the inner screen and on the cover screen. */
    val recentsColumnsInner: Int = 4,
    val recentsColumnsOuter: Int = 1,
    /** The most cards shown. */
    val recentsMaxApps: Int = 12,
    /** Height of a card's picture (dp), its corner radius (dp), and how dark the backdrop is (0..1). */
    val recentsCardHeight: Int = 300,
    val recentsCornerDp: Int = 26,
    val recentsBackdropDim: Float = 0.85f,
    /** Newest first ([RECENTS_SORT_RECENT]) or by name ([RECENTS_SORT_NAME]). */
    val recentsSort: String = RECENTS_SORT_RECENT,
    /** When each app was last used, under its name. */
    val recentsShowTime: Boolean = true,
    /** Split / Pop-up buttons on each card. */
    val recentsShowActions: Boolean = true,
    /** "Close all" asks once more before closing everything. */
    val recentsConfirmCloseAll: Boolean = false,
    /** Split view: a third app joins the split screen (phones that allow three, like the Razr
     *  Fold), otherwise it opens as a pop-up. */
    val recentsThirdInSplit: Boolean = true,
    /** With root: Android's saved screenshots of the apps, and closing a single app. */
    val recentsThumbnails: Boolean = true,
    val recentsRootClose: Boolean = true,
    /** Taskbar: the apps that are open now instead of the recently used ones, and a dot under the
     *  icons of open apps (both need the one-time DUMP grant). */
    val taskbarRunningApps: Boolean = false,
    val taskbarRunningDots: Boolean = true,
    /** Recents animations: how the cards come in (RECENTS_ENTER_*), how a card picked for split
     *  view answers (RECENTS_SELECT_*), and how fast both play (1 = normal, higher = faster). */
    val recentsEnterAnimation: String = RECENTS_ENTER_RISE,
    val recentsSelectAnimation: String = RECENTS_SELECT_POP,
    val recentsAnimationSpeed: Float = 1f,
    /** How Recents' buttons (and its menu) answer a press: one of PRESS_*. */
    val recentsPressAnimation: String = PRESS_SHRINK,
    /** Recents look: one of RECENTS_THEME_*; Liquid Glass on top of any of them (how much the
     *  backdrop is blurred, dp, and how milky the glass is). */
    val recentsTheme: String = RECENTS_THEME_FOLARK,
    val recentsGlass: Boolean = false,
    val recentsGlassBlur: Int = 28,
    val recentsGlassTint: Float = 0.16f,
    /** Hold a Recents card and drop it on another: left half split view, right half attached view
     *  (the other app peeks in at [recentsAttachedSide], taking [recentsAttachedPeek] of the width). */
    val recentsDragDrop: Boolean = true,
    /** Recents buttons, each on or off: ✕ (close, root), Split, Pop-up, Full screen (on a split
     *  card), and at the top Split view and Close all. */
    val recentsBtnClose: Boolean = true,
    val recentsBtnSplit: Boolean = true,
    val recentsBtnPopup: Boolean = true,
    val recentsBtnUnsplit: Boolean = true,
    val recentsBtnSplitView: Boolean = true,
    val recentsBtnCloseAll: Boolean = true,
    /** What a gesture on a Recents card does: one of RECENTS_ACTION_*. Swipes are sideways in the
     *  Compact list (left = up, right = down). */
    val recentsSwipeUpAction: String = RECENTS_ACTION_CLOSE,
    val recentsSwipeDownAction: String = RECENTS_ACTION_NONE,
    val recentsLongPressAction: String = RECENTS_ACTION_MENU,
    val recentsDoubleTapAction: String = RECENTS_ACTION_SPLIT,
    /** Pinned apps ("package/user"): first in Recents, never closed by Close all; with root and
     *  [recentsPinKeepAlive] also kept from being closed in the background. [recentsBtnPin]: the
     *  pin button on cards. */
    val recentsPinned: Set<String> = emptySet(),
    val recentsBtnPin: Boolean = true,
    val recentsPinKeepAlive: Boolean = true,
    /** Taskbar per app (package names): never shown in [taskbarHiddenApps]; always shown in
     *  [taskbarAlwaysApps], even full screen or when it isn't kept open. */
    val taskbarHiddenApps: Set<String> = emptySet(),
    val taskbarAlwaysApps: Set<String> = emptySet(),
    /** Liquid Glass everywhere (see withLiquidGlass): one switch and one level (0 milky .. 1 clear). */
    val liquidGlassEverywhere: Boolean = false,
    val liquidGlassLevel: Float = 0.5f,
    val recentsAttachedSide: String = RECENTS_ATTACHED_LEFT,
    val recentsAttachedPeek: Float = 0.12f,
    /** Taskbar: a small visible line at the corners where a swipe up opens it (when it isn't kept open). */
    val taskbarHandleHint: Boolean = true,
    /** Settings ▸ Root. [rootEnabled] off: Folark never asks for root. The modes (ROOT_MODE_*)
     *  pick Android's way or root for locking, Android's Recents key and the notification shade;
     *  the "keep" switches give (and give back) grants with root instead of ADB or settings pages. */
    val rootEnabled: Boolean = true,
    val rootLockMode: String = ROOT_MODE_FALLBACK,
    val rootRecentsMode: String = ROOT_MODE_FALLBACK,
    val rootShadeMode: String = ROOT_MODE_FALLBACK,
    val rootKeepDump: Boolean = false,
    val rootKeepUsage: Boolean = false,
    val rootKeepSecureSettings: Boolean = false,
    val rootKeepNotifications: Boolean = false,
    val rootKeepAccessibility: Boolean = false,
    val rootKeepWidgets: Boolean = false,
    /** Live hinge transition (both displays' settings): the angle the effect ends at (degrees),
     *  and how much the readings are smoothed (ms, 0 = raw). */
    val liveHingeEndAngle: Int = 165,
    val liveHingeSmoothingMs: Int = 50,
    /** Live hinge: the effect (one of LIVE_HINGE_STYLE_*), and its blur (dp) and frost (0..1). */
    val liveHingeStyle: String = LIVE_HINGE_STYLE_PAGES,
    /** The effect the outer display plays (on a timer) when the phone is closed. */
    val liveHingeOuterStyle: String = LIVE_HINGE_STYLE_WATER,
    val liveHingeBlurDp: Int = 24,
    val liveHingeFrost: Float = 0.2f,
    /** Multi-window (both displays): the "Split" drop target shown while an app icon is dragged, and
     *  the "Open in split screen" menu entry. */
    val splitLaunchEnabled: Boolean = true,
    /** Multi-window (both displays): "Open in floating window" and floating shortcuts in the app menus. */
    val floatingLaunchEnabled: Boolean = true,
    val dpiWorkspace: Float = 1.0f,
    val dpiAppDrawer: Float = 1.0f,
    val dpiSettings: Float = 1.0f,
    val innerDualPageWorkspace: Boolean = false,
    val innerSidebarAppDrawer: Boolean = false,
    val innerDockAlignment: String = DOCK_ALIGNMENT_CENTER,
    val parallaxWallpaper: Boolean = false,
    val drawerLayoutMode: String = DRAWER_LAYOUT_GRID,
    /** What to show when swiping right from the first home page. */
    val minusOneScreen: String = MINUS_ONE_NONE,
) {
    companion object {
        const val MINUS_ONE_NONE = "none"
        const val MINUS_ONE_GOOGLE = "google"
        const val MINUS_ONE_SMART_FEED = "smart_feed"
        val MINUS_ONE_OPTIONS = listOf(MINUS_ONE_NONE, MINUS_ONE_GOOGLE, MINUS_ONE_SMART_FEED)

        const val DRAWER_LAYOUT_GRID = "grid"
        const val DRAWER_LAYOUT_LIST = "list"

        const val AZ_POSITION_VERTICAL_RIGHT = "VERTICAL_RIGHT"
        const val AZ_POSITION_VERTICAL_LEFT = "VERTICAL_LEFT"
        const val AZ_POSITION_HORIZONTAL_TOP = "HORIZONTAL_TOP"
        const val AZ_POSITION_HORIZONTAL_BOTTOM = "HORIZONTAL_BOTTOM"

        const val DOCK_ALIGNMENT_CENTER = "center"
        const val DOCK_ALIGNMENT_LEFT = "left"
        const val DOCK_ALIGNMENT_RIGHT = "right"
        const val DOCK_ALIGNMENT_FLOATING = "floating"

        const val MIN_ICON_SIZE_SCALE = 0.7f
        const val MAX_ICON_SIZE_SCALE = 1.4f

        const val FOLDER_SHAPE_SQUIRCLE = "squircle"
        const val FOLDER_SHAPE_CIRCLE = "circle"
        const val FOLDER_SHAPE_SQUARE = "square"
        val FOLDER_SHAPES = setOf(FOLDER_SHAPE_SQUIRCLE, FOLDER_SHAPE_CIRCLE, FOLDER_SHAPE_SQUARE)

        const val FOLDER_THEME_GLASS = "glass"
        const val FOLDER_THEME_DARK = "dark"
        const val FOLDER_THEME_LIGHT = "light"
        const val FOLDER_THEME_ACCENT = "accent"
        val FOLDER_THEMES = setOf(FOLDER_THEME_GLASS, FOLDER_THEME_DARK, FOLDER_THEME_LIGHT, FOLDER_THEME_ACCENT)

        /** Closed-tile preview: a 2×2 or 3×3 grid of the first apps, or the first app alone as a cover. */
        const val FOLDER_PREVIEW_GRID_2 = "grid2"
        const val FOLDER_PREVIEW_GRID_3 = "grid3"
        const val FOLDER_PREVIEW_COVER = "cover"
        val FOLDER_PREVIEWS = setOf(FOLDER_PREVIEW_GRID_2, FOLDER_PREVIEW_GRID_3, FOLDER_PREVIEW_COVER)

        const val FOLDER_OPEN_SMALL = "small"
        const val FOLDER_OPEN_MEDIUM = "medium"
        const val FOLDER_OPEN_LARGE = "large"
        val FOLDER_OPEN_SIZES = setOf(FOLDER_OPEN_SMALL, FOLDER_OPEN_MEDIUM, FOLDER_OPEN_LARGE)

        const val POPUP_THEME_CLASSIC = "classic"
        const val POPUP_THEME_MATERIAL = "material"
        const val POPUP_THEME_DARK = "dark"
        const val POPUP_THEME_LIGHT = "light"
        /** Liquid Glass: see-through over a soft dark tint, a bright rim and a sheen. */
        const val POPUP_THEME_GLASS = "glass"
        val POPUP_THEMES = setOf(POPUP_THEME_CLASSIC, POPUP_THEME_MATERIAL, POPUP_THEME_DARK, POPUP_THEME_LIGHT, POPUP_THEME_GLASS)

        const val MAX_STACK_CORNER_RADIUS = 32
        const val MAX_STACK_AUTO_ROTATE_SECONDS = 120
        const val STACK_INDICATOR_DOTS = "dots"
        const val STACK_INDICATOR_BAR = "bar"
        const val STACK_INDICATOR_NONE = "none"
        val STACK_INDICATORS = setOf(STACK_INDICATOR_DOTS, STACK_INDICATOR_BAR, STACK_INDICATOR_NONE)

        const val DOT_SHAPE_CIRCLE = "circle"
        const val DOT_SHAPE_SQUIRCLE = "squircle"
        const val DOT_SHAPE_SQUARE = "square"
        val DOT_SHAPES = setOf(DOT_SHAPE_CIRCLE, DOT_SHAPE_SQUIRCLE, DOT_SHAPE_SQUARE)
        const val DOT_COLOR_RED = "red"
        const val DOT_COLOR_ACCENT = "accent"
        const val DOT_COLOR_WHITE = "white"
        val DOT_COLORS = setOf(DOT_COLOR_RED, DOT_COLOR_ACCENT, DOT_COLOR_WHITE)
        const val DOT_POSITION_TOP_END = "top_end"
        const val DOT_POSITION_TOP_START = "top_start"
        const val DOT_POSITION_BOTTOM_END = "bottom_end"
        const val DOT_POSITION_BOTTOM_START = "bottom_start"
        val DOT_POSITIONS = setOf(DOT_POSITION_TOP_END, DOT_POSITION_TOP_START, DOT_POSITION_BOTTOM_END, DOT_POSITION_BOTTOM_START)

        const val MAX_HOME_WALLPAPER_DIM = 0.8f
        const val MAX_HOME_WALLPAPER_BLUR = 40f

        const val ICON_SWIPE_NONE = "none"
        const val ICON_SWIPE_MENU = "menu"
        const val ICON_SWIPE_FLOATING = "floating"
        const val ICON_SWIPE_SPLIT = "split"
        val ICON_SWIPES = setOf(ICON_SWIPE_NONE, ICON_SWIPE_MENU, ICON_SWIPE_FLOATING, ICON_SWIPE_SPLIT)

        const val FLOATING_SIZE_SMALL = "small"
        const val FLOATING_SIZE_MEDIUM = "medium"
        const val FLOATING_SIZE_LARGE = "large"
        val FLOATING_SIZES = setOf(FLOATING_SIZE_SMALL, FLOATING_SIZE_MEDIUM, FLOATING_SIZE_LARGE)
        const val MIN_AZ_INDEX_SCALE = 0.7f
        const val MAX_AZ_INDEX_SCALE = 2f
        const val DRAWER_CORNER_AUTO = -1
        const val MAX_DRAWER_BOTTOM_SPACE = 120
        const val THEMED_STYLE_CLASSIC = "classic"
        const val THEMED_STYLE_INVERTED = "inverted"
        const val THEMED_STYLE_GLASS = "glass"
        const val THEMED_STYLE_DUOTONE = "duotone"
        val THEMED_STYLES = listOf(THEMED_STYLE_CLASSIC, THEMED_STYLE_INVERTED, THEMED_STYLE_GLASS, THEMED_STYLE_DUOTONE)
        const val BATTERY_SAVER_OFF = "off"
        const val BATTERY_SAVER_AUTO = "auto"
        const val BATTERY_SAVER_ON = "on"
        const val FONT_FAMILY_DEFAULT = "default"
        const val FONT_FAMILY_NDOT = "ndot"
        const val FONT_FAMILY_NTYPE = "ntype"
        const val FONT_FAMILY_SPACE_GROTESK = "space_grotesk"
        const val FONT_FAMILY_SERIF = "serif"
        const val FONT_FAMILY_MONOSPACE = "monospace"
        /** Fonts that ship on Motorola phones (/product/fonts); offered only where they exist. */
        const val FONT_FAMILY_MOTO_ROOKERY = "moto_rookery"
        const val FONT_FAMILY_MOTO_WORK_SANS = "moto_work_sans"
        const val FONT_FAMILY_MOTO_EXO2 = "moto_exo2"
        const val FONT_FAMILY_MOTO_ROBOTO_SLAB = "moto_roboto_slab"
        val FONT_FAMILIES = listOf(
            FONT_FAMILY_DEFAULT, FONT_FAMILY_NDOT, FONT_FAMILY_NTYPE,
            FONT_FAMILY_SPACE_GROTESK, FONT_FAMILY_SERIF, FONT_FAMILY_MONOSPACE,
            FONT_FAMILY_MOTO_ROOKERY, FONT_FAMILY_MOTO_WORK_SANS, FONT_FAMILY_MOTO_EXO2,
            FONT_FAMILY_MOTO_ROBOTO_SLAB,
        )

        const val CLOCK_STYLE_CLASSIC = "classic"
        const val CLOCK_STYLE_DUO = "duo"
        const val CLOCK_STYLE_LIGHT = "light"
        const val CLOCK_STYLE_DOT = "dot"
        const val CLOCK_STYLE_STACKED = "stacked"
        const val CLOCK_STYLE_GLASS_PILL = "glass_pill"
        const val CLOCK_STYLE_OUTLINE = "outline"
        const val CLOCK_STYLE_VERTICAL_STACK = "vertical_stack"
        const val CLOCK_STYLE_NEUMORPHIC = "neumorphic"
        const val CLOCK_STYLE_NOTHING_NDOT = "nothing_ndot"

        const val TASKBAR_THEME_CLEAR = "clear"
        const val TASKBAR_THEME_FROSTED = "frosted"
        const val TASKBAR_THEME_DARK = "dark"
        const val TASKBAR_THEME_LIGHT = "light"
        const val TASKBAR_THEME_ACCENT = "accent"
        /** Liquid Glass: see-through with a bright rim and a sheen along the top. */
        const val TASKBAR_THEME_GLASS = "glass"
        val TASKBAR_THEMES = listOf(
            TASKBAR_THEME_CLEAR, TASKBAR_THEME_FROSTED, TASKBAR_THEME_DARK, TASKBAR_THEME_LIGHT, TASKBAR_THEME_ACCENT,
            TASKBAR_THEME_GLASS,
        )
        const val TASKBAR_MEDIA_POS_START = "start"
        const val TASKBAR_MEDIA_POS_MIDDLE = "middle"
        const val TASKBAR_MEDIA_POS_END = "end"
        const val TASKBAR_MEDIA_TAP_OPEN = "open"
        const val TASKBAR_MEDIA_TAP_PLAY_PAUSE = "play_pause"
        const val TASKBAR_MEDIA_SHAPE_CIRCLE = "circle"
        const val TASKBAR_MEDIA_SHAPE_ROUNDED = "rounded"

        const val TASKBAR_RECENTS_OFF = "off"
        const val TASKBAR_RECENTS_SYSTEM = "system"
        const val TASKBAR_RECENTS_DESKTOP = "desktop"
        /** Folark's own Recents screen. */
        const val TASKBAR_RECENTS_FOLARK = "folark"

        const val RECENTS_SORT_RECENT = "recent"
        const val RECENTS_SORT_NAME = "name"
        const val MIN_RECENTS_COLUMNS = 1
        const val MAX_RECENTS_COLUMNS_INNER = 6
        const val MAX_RECENTS_COLUMNS_OUTER = 2
        const val MIN_RECENTS_APPS = 4
        const val MAX_RECENTS_APPS = 30
        const val MIN_RECENTS_CARD_HEIGHT = 160
        const val MAX_RECENTS_CARD_HEIGHT = 440
        const val MAX_RECENTS_CORNER_DP = 40
        const val MIN_RECENTS_ANIM_SPEED = 0.25f
        /** Recents themes: Folark's card grid, iPad (big windows in a row), Carousel (one big card
         *  in the middle), Deck (overlapping cards), Compact (a list), Switcher (big icons). */
        const val RECENTS_THEME_FOLARK = "folark"
        const val RECENTS_THEME_IPAD = "ipad"
        const val RECENTS_THEME_CAROUSEL = "carousel"
        const val RECENTS_THEME_DECK = "deck"
        const val RECENTS_THEME_COMPACT = "compact"
        const val RECENTS_THEME_SWITCHER = "switcher"
        val RECENTS_THEMES = listOf(
            RECENTS_THEME_FOLARK, RECENTS_THEME_IPAD, RECENTS_THEME_CAROUSEL,
            RECENTS_THEME_DECK, RECENTS_THEME_COMPACT, RECENTS_THEME_SWITCHER,
        )
        const val MAX_RECENTS_GLASS_BLUR = 80
        const val MIN_RECENTS_GLASS_TINT = 0.04f
        const val MAX_RECENTS_GLASS_TINT = 0.4f
        const val RECENTS_ACTION_NONE = "none"
        const val RECENTS_ACTION_CLOSE = "close"
        const val RECENTS_ACTION_SPLIT = "split"
        const val RECENTS_ACTION_POPUP = "popup"
        const val RECENTS_ACTION_UNSPLIT = "unsplit"
        const val RECENTS_ACTION_INFO = "info"
        const val RECENTS_ACTION_OPEN = "open"
        const val RECENTS_ACTION_PIN = "pin"
        /** A menu on the card: split screen, freeform, lock, close. */
        const val RECENTS_ACTION_MENU = "menu"
        val RECENTS_ACTIONS = listOf(
            RECENTS_ACTION_NONE, RECENTS_ACTION_MENU, RECENTS_ACTION_CLOSE, RECENTS_ACTION_SPLIT, RECENTS_ACTION_POPUP,
            RECENTS_ACTION_UNSPLIT, RECENTS_ACTION_PIN, RECENTS_ACTION_INFO, RECENTS_ACTION_OPEN,
        )
        const val RECENTS_ATTACHED_LEFT = "left"
        const val RECENTS_ATTACHED_RIGHT = "right"
        const val MIN_RECENTS_ATTACHED_PEEK = 0.05f
        const val MAX_RECENTS_ATTACHED_PEEK = 0.3f
        const val MAX_RECENTS_ANIM_SPEED = 2.5f

        /** Recents entrances: up from below, out of the distance, falling in diagonally, turning
         *  over, fanning out from a stack, coming into focus. */
        const val RECENTS_ENTER_RISE = "rise"
        const val RECENTS_ENTER_ZOOM = "zoom"
        const val RECENTS_ENTER_CASCADE = "cascade"
        const val RECENTS_ENTER_FLIP = "flip"
        const val RECENTS_ENTER_FAN = "fan"
        const val RECENTS_ENTER_FOCUS = "focus"
        const val RECENTS_ENTER_NONE = "none"
        val RECENTS_ENTERS = listOf(
            RECENTS_ENTER_RISE, RECENTS_ENTER_ZOOM, RECENTS_ENTER_CASCADE, RECENTS_ENTER_FLIP,
            RECENTS_ENTER_FAN, RECENTS_ENTER_FOCUS, RECENTS_ENTER_NONE,
        )

        /** A card picked for split view: a hop, a spreading ring, lifted up, leaning to its side,
         *  a breathing halo, a little shake. */
        const val RECENTS_SELECT_POP = "pop"
        const val RECENTS_SELECT_RING = "ring"
        const val RECENTS_SELECT_LIFT = "lift"
        const val RECENTS_SELECT_TILT = "tilt"
        const val RECENTS_SELECT_GLOW = "glow"
        const val RECENTS_SELECT_JIGGLE = "jiggle"
        const val RECENTS_SELECT_NONE = "none"
        val RECENTS_SELECTS = listOf(
            RECENTS_SELECT_POP, RECENTS_SELECT_RING, RECENTS_SELECT_LIFT, RECENTS_SELECT_TILT,
            RECENTS_SELECT_GLOW, RECENTS_SELECT_JIGGLE, RECENTS_SELECT_NONE,
        )

        /** Button press animations: sink in, overshoot, a halo, a spreading ring, lean in, wobble. */
        const val PRESS_SHRINK = "shrink"
        const val PRESS_BOUNCE = "bounce"
        const val PRESS_GLOW = "glow"
        const val PRESS_RIPPLE = "ripple"
        const val PRESS_TILT = "tilt"
        const val PRESS_JELLY = "jelly"
        const val PRESS_NONE = "none"
        val PRESS_EFFECTS = listOf(PRESS_SHRINK, PRESS_BOUNCE, PRESS_GLOW, PRESS_RIPPLE, PRESS_TILT, PRESS_JELLY, PRESS_NONE)

        /** Settings ▸ Root: Android's way only, root when Android's way can't, or root first. */
        const val ROOT_MODE_OFF = "off"
        const val ROOT_MODE_FALLBACK = "fallback"
        const val ROOT_MODE_ALWAYS = "always"
        val ROOT_MODES = listOf(ROOT_MODE_OFF, ROOT_MODE_FALLBACK, ROOT_MODE_ALWAYS)
        const val TASKBAR_SPLIT_SIDE_LEFT = "left"
        const val TASKBAR_SPLIT_SIDE_RIGHT = "right"

        /** Settings look: follows the system light/dark theme. */
        const val SETTINGS_STYLE_STANDARD = "standard"
        /** Settings look: always dark charcoal, outlined cards, uppercase titles. */
        const val SETTINGS_STYLE_STUDIO = "studio"

        const val ICON_SHAPE_SYSTEM = "system"
        const val ICON_SHAPE_CIRCLE = "circle"
        const val ICON_SHAPE_SQUIRCLE = "squircle"
        const val ICON_SHAPE_ROUNDED = "rounded"
        const val ICON_SHAPE_TEARDROP = "teardrop"
        const val ICON_SHAPE_PEBBLE = "pebble"
        val ICON_SHAPES = listOf(
            ICON_SHAPE_SYSTEM, ICON_SHAPE_CIRCLE, ICON_SHAPE_SQUIRCLE,
            ICON_SHAPE_ROUNDED, ICON_SHAPE_TEARDROP, ICON_SHAPE_PEBBLE,
        )
        const val TASKBAR_HANDLES_BOTH = "both"
        const val TASKBAR_HANDLES_LEFT = "left"
        const val TASKBAR_HANDLES_RIGHT = "right"

        /** Minutes as the clock style colours them (accent or like the hours). */
        const val CLOCK_MINUTE_STYLE = "style"
        const val CLOCK_MINUTE_WHITE = "white"
        const val CLOCK_MINUTE_LIGHT_GREY = "light_grey"
        const val CLOCK_MINUTE_GREY = "grey"
        const val CLOCK_MINUTE_DARK_GREY = "dark_grey"
        const val CLOCK_MINUTE_BLACK = "black"
        val CLOCK_MINUTE_COLORS = listOf(
            CLOCK_MINUTE_STYLE, CLOCK_MINUTE_WHITE, CLOCK_MINUTE_LIGHT_GREY,
            CLOCK_MINUTE_GREY, CLOCK_MINUTE_DARK_GREY, CLOCK_MINUTE_BLACK,
        )
        val CLOCK_STYLES = listOf(
            CLOCK_STYLE_CLASSIC, CLOCK_STYLE_DUO, CLOCK_STYLE_LIGHT, CLOCK_STYLE_DOT,
            CLOCK_STYLE_STACKED, CLOCK_STYLE_GLASS_PILL, CLOCK_STYLE_OUTLINE,
            CLOCK_STYLE_VERTICAL_STACK, CLOCK_STYLE_NEUMORPHIC, CLOCK_STYLE_NOTHING_NDOT,
        )
        const val DRAWER_HANDLE_BAR = "bar"
        const val DRAWER_HANDLE_ARROW = "arrow"
        const val MAX_DRAWER_CORNER_RADIUS = 56
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 2f
        const val APP_OPEN_SYSTEM = "system"

        /** The app grows out of the icon that was tapped. */
        const val APP_OPEN_ICON = "icon"
        const val APP_OPEN_ZOOM = "zoom"
        const val APP_OPEN_SLIDE = "slide"
        const val APP_OPEN_FADE = "fade"
        const val APP_OPEN_NONE = "none"
        /** The app's window is revealed from the icon that was tapped (a growing clip). */
        const val APP_OPEN_REVEAL = "reveal"
        val APP_OPENS = listOf(APP_OPEN_SYSTEM, APP_OPEN_ICON, APP_OPEN_REVEAL, APP_OPEN_ZOOM, APP_OPEN_SLIDE, APP_OPEN_FADE, APP_OPEN_NONE)
        const val FOLDER_ANIM_SHEET = "sheet"
        const val FOLDER_ANIM_ZOOM = "zoom"
        const val FOLDER_ANIM_FADE = "fade"
        const val FOLDER_ANIM_NONE = "none"
        val FOLDER_ANIMS = listOf(FOLDER_ANIM_SHEET, FOLDER_ANIM_ZOOM, FOLDER_ANIM_FADE, FOLDER_ANIM_NONE)
        const val PAGE_EFFECT_SLIDE = "slide"
        const val PAGE_EFFECT_FADE = "fade"
        const val PAGE_EFFECT_ZOOM = "zoom"
        const val PAGE_EFFECT_CUBE = "cube"
        const val PAGE_EFFECT_STACK = "stack"
        val PAGE_EFFECTS = listOf(PAGE_EFFECT_SLIDE, PAGE_EFFECT_FADE, PAGE_EFFECT_ZOOM, PAGE_EFFECT_CUBE, PAGE_EFFECT_STACK)

        /** One-tap theme presets: each sets the drawer, folders, menus, dots and dock together. */
        const val THEME_PRESET_GLASS = "glass"
        const val THEME_PRESET_DARK = "dark"
        const val THEME_PRESET_LIGHT = "light"
        const val THEME_PRESET_MONO = "mono"

        /** Themes of Folark's own widgets: the standard translucent card, or a solid one. */
        const val WIDGET_THEME_DEFAULT = "default"
        const val WIDGET_THEME_DARK = "dark"
        const val WIDGET_THEME_LIGHT = "light"
        const val WIDGET_THEME_ACCENT = "accent"
        val WIDGET_THEMES = setOf(WIDGET_THEME_DEFAULT, WIDGET_THEME_DARK, WIDGET_THEME_LIGHT, WIDGET_THEME_ACCENT)

        const val MIN_FOLDER_OPEN_COLUMNS = 3
        const val MAX_FOLDER_OPEN_COLUMNS = 6

        const val MIN_DPI_SCALE = 0.7f
        const val MAX_DPI_SCALE = 1.4f

        const val SEARCH_TOP = "top"
        const val SEARCH_BOTTOM = "bottom"

        const val DRAWER_STYLE_STANDARD_GRID = "standard_grid"
        const val DRAWER_STYLE_ONE_UI = "one_ui"
        const val DRAWER_STYLE_NOTHING_OS = "nothing_os"
        const val DRAWER_STYLE_MOTO = "moto"
        const val DRAWER_STYLE_VERTICAL = "vertical"
        const val DRAWER_STYLE_HORIZONTAL = "horizontal"

        const val DRAWER_THEME_SYSTEM = "system"
        const val DRAWER_THEME_MATERIAL_YOU = "material_you"
        const val DRAWER_THEME_LIGHT = "light"
        const val DRAWER_THEME_CUSTOM = "custom"
        const val DRAWER_THEME_DEFAULT_DARK = "default_dark"
        const val DRAWER_THEME_OBSIDIAN_BLACK = "obsidian_black"
        const val DRAWER_THEME_ACCENT_TINT = "accent_tint"
        const val DRAWER_THEME_MONOCHROME = "monochrome"

        /** Every valid [drawerThemeTint] value, in the order the settings picker shows them. */
        val DRAWER_THEMES = listOf(
            DRAWER_THEME_SYSTEM,
            DRAWER_THEME_MATERIAL_YOU,
            DRAWER_THEME_DEFAULT_DARK,
            DRAWER_THEME_OBSIDIAN_BLACK,
            DRAWER_THEME_LIGHT,
            DRAWER_THEME_ACCENT_TINT,
            DRAWER_THEME_MONOCHROME,
            DRAWER_THEME_CUSTOM,
        )

        const val DEFAULT_DRAWER_ACCENT = 0xFF00B0FF.toInt()
        const val DEFAULT_DRAWER_CUSTOM_COLOR = 0xFF1B2A41.toInt()
        const val DEFAULT_DRAWER_PANEL_OPACITY = 0.55f
        const val MIN_DRAWER_PANEL_OPACITY = 0.15f
        const val MAX_DRAWER_PANEL_OPACITY = 0.95f
        /** Upper bound of [drawerBackgroundBlur] (dp of wallpaper blur behind the open drawer). */
        const val MAX_DRAWER_BLUR = 25f

        /** The accent each drawer style ships with; a style change only replaces the accent while
         *  it still is one of these (i.e. the user never picked their own). */
        fun defaultAccentForStyle(style: String): Int = when (style) {
            DRAWER_STYLE_ONE_UI -> 0xFF0A84FF.toInt()
            DRAWER_STYLE_MOTO -> 0xFF00BFA5.toInt()
            DRAWER_STYLE_NOTHING_OS -> 0xFFFF3B30.toInt()
            else -> DEFAULT_DRAWER_ACCENT
        }

        val STYLE_DEFAULT_ACCENTS: Set<Int> = setOf(
            DRAWER_STYLE_STANDARD_GRID, DRAWER_STYLE_ONE_UI, DRAWER_STYLE_MOTO, DRAWER_STYLE_NOTHING_OS,
        ).mapTo(HashSet()) { defaultAccentForStyle(it) }

        const val DRAWER_ALIGNMENT_LEFT = "left"
        const val DRAWER_ALIGNMENT_CENTER = "center"
        const val DRAWER_ALIGNMENT_RIGHT = "right"

        const val COUNT_NUMBER = "number"
        const val COUNT_DOT = "dot"
        const val COUNT_NONE = "none"

        const val PRIVACY_ALL = "all"
        const val PRIVACY_SENDER = "sender"
        const val PRIVACY_COUNT = "count"

        const val DENSITY_COMPACT = "compact"
        const val DENSITY_NORMAL = "normal"
        const val DENSITY_SPACIOUS = "spacious"

        const val GESTURE_NOTIFICATIONS = "notifications"
        const val GESTURE_DRAWER = "drawer"
        const val GESTURE_SEARCH = "search"
        const val GESTURE_LOCK = "lock"
        const val GESTURE_NONE = "none"
        /** Folark's own Recents screen. */
        const val GESTURE_RECENTS = "recents"
        /** Motorola screens as home gestures (offered only where the phone has them, see MotoLinks). */
        const val GESTURE_MOTO_NOTE = "moto_note"
        const val GESTURE_MOTO_VAULT = "moto_vault"
        const val GESTURE_MOTO_READY_FOR = "moto_ready_for"
        const val GESTURE_MOTO_GAMES = "moto_games"
        const val GESTURE_MOTO_WEATHER = "moto_weather"
        const val GESTURE_MOTO_STYLES = "moto_styles"
        val MOTO_GESTURES = listOf(
            GESTURE_MOTO_NOTE, GESTURE_MOTO_VAULT, GESTURE_MOTO_READY_FOR,
            GESTURE_MOTO_GAMES, GESTURE_MOTO_WEATHER, GESTURE_MOTO_STYLES,
        )

        const val ACTION_PAGE_0 = "page_0"
        const val ACTION_LOCK = "lock"
        const val ACTION_SEARCH = "search"
        const val ACTION_DRAWER = "drawer"
        const val ACTION_STAY = "stay"

        const val TRANSITION_MORPH_SCALE_FADE = "morph_scale_fade"
        const val TRANSITION_BOOK_UNFOLD_SWEEP = "book_unfold_sweep"
        const val TRANSITION_AQUAMORPHIC_RIPPLE = "aquamorphic_ripple"
        const val TRANSITION_CROSSFADE = "crossfade"
        const val TRANSITION_SCALE = "scale"
        const val TRANSITION_SLIDE = "slide"
        /** Follows the hinge while the phone opens (needs a hinge sensor; the inner display only). */
        const val TRANSITION_LIVE_HINGE = "live_hinge"
        const val MIN_FOLD_TRANSITION_SPEED = 0.1f
        const val MAX_FOLD_TRANSITION_SPEED = 1.5f
        const val MIN_LIVE_HINGE_END_ANGLE = 120
        const val MAX_LIVE_HINGE_SMOOTHING_MS = 120
        const val MAX_LIVE_HINGE_BLUR_DP = 48
        const val MAX_LIVE_HINGE_FROST = 0.7f
        /** Two pages folding flat around the hinge. */
        const val LIVE_HINGE_STYLE_PAGES = "pages"
        /** Rings spreading from the hinge, like water settling. */
        const val LIVE_HINGE_STYLE_WATER = "water"
        /** Black and white and dim, colour flowing in. */
        const val LIVE_HINGE_STYLE_INK = "ink"
        /** Out of black. */
        const val LIVE_HINGE_STYLE_FADE = "fade"
        /** Falling in from above, as if dropping off the cover screen. */
        const val LIVE_HINGE_STYLE_DROP = "drop"
        /** Two halves sliding in to meet at the hinge. */
        const val LIVE_HINGE_STYLE_DOORS = "doors"
        /** A circle opening from the hinge. */
        const val LIVE_HINGE_STYLE_PORTAL = "portal"
        /** Coming forward out of depth. */
        const val LIVE_HINGE_STYLE_ZOOM = "zoom"
        val LIVE_HINGE_STYLES = listOf(
            LIVE_HINGE_STYLE_PAGES, LIVE_HINGE_STYLE_WATER, LIVE_HINGE_STYLE_INK, LIVE_HINGE_STYLE_FADE,
            LIVE_HINGE_STYLE_DROP, LIVE_HINGE_STYLE_DOORS, LIVE_HINGE_STYLE_PORTAL, LIVE_HINGE_STYLE_ZOOM,
        )

        /** Live activity pill effects: a breathing halo, a light sweeping across, filling up with
         *  the progress, a light running round the rim, a gentle pulse, drifting colour, or none. */
        /** Icon nudge effects: a hop, a shake, a heartbeat, a halo, rings spreading, squash and stretch. */
        const val NUDGE_BOUNCE = "bounce"
        const val NUDGE_WIGGLE = "wiggle"
        const val NUDGE_PULSE = "pulse"
        const val NUDGE_GLOW = "glow"
        const val NUDGE_RIPPLE = "ripple"
        const val NUDGE_JELLY = "jelly"
        /** The icon fills with the app's colour. */
        const val NUDGE_TINT = "tint"
        val NUDGE_EFFECTS = listOf(NUDGE_BOUNCE, NUDGE_WIGGLE, NUDGE_PULSE, NUDGE_GLOW, NUDGE_RIPPLE, NUDGE_JELLY, NUDGE_TINT)

        const val LIVE_EFFECT_GLOW = "glow"
        const val LIVE_EFFECT_SHIMMER = "shimmer"
        const val LIVE_EFFECT_PROGRESS = "progress"
        const val LIVE_EFFECT_ORBIT = "orbit"
        const val LIVE_EFFECT_BREATHE = "breathe"
        const val LIVE_EFFECT_AURORA = "aurora"
        const val LIVE_EFFECT_STILL = "still"
        val LIVE_EFFECTS = listOf(
            LIVE_EFFECT_GLOW, LIVE_EFFECT_SHIMMER, LIVE_EFFECT_PROGRESS, LIVE_EFFECT_ORBIT,
            LIVE_EFFECT_BREATHE, LIVE_EFFECT_AURORA, LIVE_EFFECT_STILL,
        )

        const val DEFAULT_BATCH_TIMES = "08:00,12:00,17:00"
    }
}
