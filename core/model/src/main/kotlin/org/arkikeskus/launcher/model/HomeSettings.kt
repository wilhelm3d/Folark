package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** The home screen: grid, pages, labels, wallpaper and gestures. */
@Immutable
data class HomeSettings(
    val columns: Int = 4,
    /** Home-grid row count — more rows = tighter icon rows + more free cells for widgets. */
    val rows: Int = 6,
    val showLabels: Boolean = true,
    /** Let home-screen labels (apps, folders, shortcuts) wrap onto a second line. Off keeps the
     *  single line the home grid has always had; the dock stays single-line either way. */
    val twoLineLabels: Boolean = false,
    val showPageIndicator: Boolean = true,
    /** The page the launcher opens on and the HOME button returns to (0 = the leftmost). */
    val page: Int = 0,
    /** Pages the user added explicitly (0 = none): the home keeps at least this many pages even
     *  when some are empty. Pages that hold an icon or widget exist regardless. */
    val pageCount: Int = 0,
    /** Lock the desktop layout: when true, home + dock items can't be moved, removed, or added. */
    val desktopLocked: Boolean = false,
    /** Allow home screen landscape rotation on this screen profile. */
    val allowLandscape: Boolean = false,
    /** Swipe-down gesture action: [LauncherSettings.GESTURE_NOTIFICATIONS], [LauncherSettings.GESTURE_DRAWER], [LauncherSettings.GESTURE_SEARCH], [LauncherSettings.GESTURE_LOCK], [LauncherSettings.GESTURE_NONE]. */
    val swipeDownAction: String = LauncherSettings.GESTURE_NOTIFICATIONS,
    /** Double-tap gesture action on empty space: [LauncherSettings.GESTURE_NOTIFICATIONS], [LauncherSettings.GESTURE_DRAWER], [LauncherSettings.GESTURE_SEARCH], [LauncherSettings.GESTURE_LOCK], [LauncherSettings.GESTURE_NONE]. */
    val doubleTapAction: String = LauncherSettings.GESTURE_NONE,
    /** Swipe-up gesture action: [LauncherSettings.GESTURE_DRAWER], [LauncherSettings.GESTURE_SEARCH], [LauncherSettings.GESTURE_NONE]. */
    val swipeUpAction: String = LauncherSettings.GESTURE_DRAWER,
    /** App key launched by the left-edge home swipe; blank = gesture disabled. */
    val leftSwipeAppKey: String = "",
    /** App key launched by the right-edge home swipe; blank = gesture disabled. */
    val rightSwipeAppKey: String = "",
    /** Home screen backdrop: darkening (0..0.8) and blur (dp) of the wallpaper behind the icons. */
    val wallpaperDim: Float = 0f,
    val wallpaperBlur: Float = 0f,
    /** Path of this display's own home background picture ("" = show the system wallpaper). */
    val backgroundImage: String = "",
    /**
     * A video background (both displays): how long it plays after home comes back before it rests
     * on its last frame, in seconds; 0 = it keeps looping as long as home shows.
     */
    val videoAwakeSeconds: Int = 30,
    /** A newly installed app is put on home by itself (both displays, first free place). */
    val addNewApps: Boolean = false,
    /** What a swipe up on a home app icon does (one of ICON_SWIPE_*). */
    val iconSwipeUpAction: String = LauncherSettings.ICON_SWIPE_NONE,
    /** Remove a home page as soon as its last item leaves it. */
    val autoRemoveEmptyPages: Boolean = false,
    /** Dragging an item past the last page opens a new, empty page for it. */
    val newPageOnDrag: Boolean = false,
    val pageBounceEnabled: Boolean = true,
    /** What the home pages do while being swiped (one of PAGE_EFFECT_*). */
    val pageScrollEffect: String = LauncherSettings.PAGE_EFFECT_SLIDE,
    /** Unfolding the phone: the home pages open out from the hinge. */
    val unfoldAnimation: Boolean = true,
    val innerDualPage: Boolean = false,
    val parallaxWallpaper: Boolean = false,
    /** What to show when swiping right from the first home page. */
    val minusOneScreen: String = LauncherSettings.MINUS_ONE_NONE,
)
