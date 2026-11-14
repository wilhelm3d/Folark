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
    /** Swipe-down gesture action: [HomeGesture.NOTIFICATIONS], [HomeGesture.DRAWER], [HomeGesture.SEARCH], [HomeGesture.LOCK], [HomeGesture.NONE]. */
    val swipeDownAction: HomeGesture = HomeGesture.NOTIFICATIONS,
    /** Double-tap gesture action on empty space: [HomeGesture.NOTIFICATIONS], [HomeGesture.DRAWER], [HomeGesture.SEARCH], [HomeGesture.LOCK], [HomeGesture.NONE]. */
    val doubleTapAction: HomeGesture = HomeGesture.NONE,
    /** Swipe-up gesture action: [HomeGesture.DRAWER], [HomeGesture.SEARCH], [HomeGesture.NONE]. */
    val swipeUpAction: HomeGesture = HomeGesture.DRAWER,
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
    /** What a swipe up on a home app icon does (one of [IconSwipe]). */
    val iconSwipeUpAction: IconSwipe = IconSwipe.NONE,
    /** Remove a home page as soon as its last item leaves it. */
    val autoRemoveEmptyPages: Boolean = false,
    /** Dragging an item past the last page opens a new, empty page for it. */
    val newPageOnDrag: Boolean = false,
    val pageBounceEnabled: Boolean = true,
    /** What the home pages do while being swiped (one of [PageEffect]). */
    val pageScrollEffect: PageEffect = PageEffect.SLIDE,
    /** Unfolding the phone: the home pages open out from the hinge. */
    val unfoldAnimation: Boolean = true,
    val innerDualPage: Boolean = false,
    val parallaxWallpaper: Boolean = false,
    /** What to show when swiping right from the first home page. */
    val minusOneScreen: MinusOne = MinusOne.NONE,
)

/**
 * What a gesture on empty home space does. The Motorola screens are offered only where the phone
 * has them (see MotoLinks).
 */
enum class HomeGesture(override val stored: String) : StoredChoice {
    NOTIFICATIONS("notifications"),
    DRAWER("drawer"),
    SEARCH("search"),
    LOCK("lock"),
    NONE("none"),

    /** Folark's own Recents screen. */
    RECENTS("recents"),

    MOTO_NOTE("moto_note"),
    MOTO_VAULT("moto_vault"),
    MOTO_READY_FOR("moto_ready_for"),
    MOTO_GAMES("moto_games"),
    MOTO_WEATHER("moto_weather"),
    MOTO_STYLES("moto_styles"),
    ;

    companion object {
        /** The Motorola screens, offered as home gestures only where the phone has them (see MotoLinks). */
        val MOTO: List<HomeGesture> = listOf(MOTO_NOTE, MOTO_VAULT, MOTO_READY_FOR, MOTO_GAMES, MOTO_WEATHER, MOTO_STYLES)

        /** What swiping up on home can do (the other gestures can do any of these). */
        val SWIPE_UP: List<HomeGesture> = listOf(DRAWER, SEARCH, NONE)
    }
}

/** What swiping up on a home icon does. */
enum class IconSwipe(override val stored: String) : StoredChoice {
    NONE("none"),
    MENU("menu"),
    FLOATING("floating"),
    SPLIT("split"),
}

/** What pulling the first home page to the right opens. */
enum class MinusOne(override val stored: String) : StoredChoice {
    NONE("none"),
    GOOGLE("google"),
    SMART_FEED("smart_feed"),
}

/** How home pages move when swiped. */
enum class PageEffect(override val stored: String) : StoredChoice {
    SLIDE("slide"),
    FADE("fade"),
    ZOOM("zoom"),
    CUBE("cube"),
    STACK("stack"),
}
