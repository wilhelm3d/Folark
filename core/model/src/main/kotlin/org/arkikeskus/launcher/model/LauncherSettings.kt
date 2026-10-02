package org.arkikeskus.launcher.model

/** User-configurable launcher settings (persisted in DataStore). */
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
    val drawerBackgroundBlur: Float = 0f,
    val drawerScrimOpacity: Float = 0.5f,
    val glassBlurRadius: Float = 25.0f,
    val glassDarkTint: Float = 0.45f,
    val drawerStyle: String = DRAWER_STYLE_STANDARD_GRID,
    val drawerThemeTint: String = DRAWER_THEME_DEFAULT_DARK,
    val drawerAccentColor: Int = 0xFF00B0FF.toInt(),
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
    /** Double-tap on empty home-screen space locks the screen (needs the lock accessibility service). */
    val doubleTapToLock: Boolean = false,
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
    /** Workspace density / cell padding scale: [DENSITY_COMPACT], [DENSITY_NORMAL], [DENSITY_SPACIOUS]. */
    val workspaceDensity: String = DENSITY_NORMAL,
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
    /** AMOLED dark mode (pure black backgrounds and surfaces). */
    val amoledDark: Boolean = false,
    val foldAction: String = ACTION_STAY,
    val unfoldAction: String = ACTION_STAY,
    val innerTaskbarEnabled: Boolean = true,
    val outerWallpaperScale: Float = 1.0f,
    val innerWallpaperScale: Float = 1.0f,
    val foldTransitionStyle: String = TRANSITION_BOOK_UNFOLD_SWEEP,
    val aquamorphicTouchEnabled: Boolean = true,
    val appLaunchZoomEnabled: Boolean = true,
    val pageBounceEnabled: Boolean = true,
    val dpiWorkspace: Float = 1.0f,
    val dpiAppDrawer: Float = 1.0f,
    val dpiSettings: Float = 1.0f,
    val innerDualPageWorkspace: Boolean = false,
    val innerSidebarAppDrawer: Boolean = false,
    val innerDockAlignment: String = DOCK_ALIGNMENT_CENTER,
    val parallaxWallpaper: Boolean = false,
    val drawerLayoutMode: String = DRAWER_LAYOUT_GRID,
) {
    companion object {
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

        const val DRAWER_THEME_DEFAULT_DARK = "default_dark"
        const val DRAWER_THEME_OBSIDIAN_BLACK = "obsidian_black"
        const val DRAWER_THEME_ACCENT_TINT = "accent_tint"
        const val DRAWER_THEME_MONOCHROME = "monochrome"

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

        const val DEFAULT_BATCH_TIMES = "08:00,12:00,17:00"
    }
}
