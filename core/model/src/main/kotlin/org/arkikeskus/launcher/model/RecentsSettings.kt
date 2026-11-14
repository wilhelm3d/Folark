package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark Recents. */
@Immutable
data class RecentsSettings(
    /** Folark's own Recents screen (all shared by both displays): opened by the taskbar's Recents
     *  button ([TaskbarRecentsButton.FOLARK]) or a home gesture ([HomeGesture.RECENTS]). */
    val enabled: Boolean = true,
    /** Android's own Recents (swipe up and hold, the stock taskbar, the Recents key) hands over to
     *  Folark Recents as soon as it opens (through Folark's accessibility service). */
    val replaceSystem: Boolean = false,
    /** Vector module in the system launcher: a long press on the gesture handle opens Folark's
     *  search instead of Android's assistant / Circle to Search. */
    val navHandleOpensSearch: Boolean = false,
    /** Cards per row on the inner screen and on the outer screen. */
    val columnsInner: Int = 4,
    val columnsOuter: Int = 1,
    /** The most cards shown. */
    val maxApps: Int = 12,
    /** Height of a card's picture (dp), its corner radius (dp), and how dark the backdrop is (0..1). */
    val cardHeight: Int = 300,
    val cornerDp: Int = 26,
    val backdropDim: Float = 0.85f,
    /** Newest first ([RecentsSort.RECENT]) or by name ([RecentsSort.NAME]). */
    val sort: RecentsSort = RecentsSort.RECENT,
    /** When each app was last used, under its name. */
    val showTime: Boolean = true,
    /** Card look: [RecentsCardLayout.HEADER] (app row above the picture) or [RecentsCardLayout.OVERLAY]
     *  (the picture fills the card, the app icon floats on top — a cleaner, borderless look). */
    val cardLayout: RecentsCardLayout = RecentsCardLayout.OVERLAY,
    /** Borderless cards: the app's name under each card. */
    val overlayLabels: Boolean = true,
    /** The app's icon floating on each card (overlay card looks). */
    val cardIcons: Boolean = true,
    /** A soft drop shadow under each card. */
    val cardShadow: Boolean = true,
    /** Scales the app-name labels on the cards, [LauncherSettings.MIN_RECENTS_LABEL_SCALE]..[LauncherSettings.MAX_RECENTS_LABEL_SCALE]. */
    val labelScale: Float = 1f,
    /** The long-press menu grows from the finger and its rows come in one by one (off: it just appears). */
    val menuMotion: Boolean = true,
    /** A light haptic tick for every card scrolled past (off by default). */
    val scrollTicks: Boolean = false,
    /** "Close all" asks once more before closing everything. */
    val confirmCloseAll: Boolean = false,
    /** Split view: a third app joins the split screen (phones that allow three, like the Razr
     *  Fold), otherwise it opens as a pop-up. */
    val thirdInSplit: Boolean = true,
    /** With root: Android's saved screenshots of the apps, and closing a single app. */
    val thumbnails: Boolean = true,
    val rootClose: Boolean = true,
    /** A speaker badge on a card whose app has a media session; a tap pauses/resumes just that
     *  app (there is no public per-UID stream mute, so this is the per-app audio control). */
    val speakerBadge: Boolean = false,
    /** Apps that shared a split screen show as one double card (a tap brings the split back); off: a card per app. */
    val groupSplits: Boolean = true,
    /** With root: how much memory each card's app is using, as a small label on the card. */
    val memoryBadge: Boolean = false,
    /** Recents animations: how the cards come in ([RecentsEnter]), how a card picked for split
     *  view answers ([RecentsSelect]), and how fast both play (1 = normal, higher = faster). */
    val enterAnimation: RecentsEnter = RecentsEnter.RISE,
    val selectAnimation: RecentsSelect = RecentsSelect.POP,
    val animationSpeed: Float = 1f,
    /** How Recents' buttons (and its menu) answer a press: one of [PressEffect]. */
    val pressAnimation: PressEffect = PressEffect.SHRINK,
    /** Recents look: one of [RecentsTheme]; Liquid Glass on top of any of them (how much the
     *  backdrop is blurred, dp, and how milky the glass is). */
    val theme: RecentsTheme = RecentsTheme.FOLARK,
    val glass: Boolean = false,
    val glassBlur: Int = 28,
    val glassTint: Float = 0.16f,
    /** Hold a Recents card and drop it on another: left half split view, right half attached view
     *  (the other app peeks in at [attachedSide], taking [attachedPeek] of the width). */
    val dragDrop: Boolean = true,
    /** Recents buttons, each on or off: ✕ (close, root), Split, Pop-up, Full screen (on a split
     *  card), and at the top Split view and Close all. */
    val btnClose: Boolean = true,
    val btnSplit: Boolean = true,
    val btnPopup: Boolean = true,
    val btnUnsplit: Boolean = true,
    val btnSplitView: Boolean = true,
    val btnCloseAll: Boolean = true,
    /** What a gesture on a Recents card does: one of [RecentsAction]. Swipes left / right
     *  (fling the card off the side of the screen) in the looks that scroll up and down
     *  (Folark, Compact, Stack, Bento, Switcher); swipes up / down in every look but Compact
     *  and Stack (where up and down scroll). */
    val swipeUpAction: RecentsAction = RecentsAction.CLOSE,
    val swipeDownAction: RecentsAction = RecentsAction.NONE,
    val swipeLeftAction: RecentsAction = RecentsAction.CLOSE,
    val swipeRightAction: RecentsAction = RecentsAction.CLOSE,
    val longPressAction: RecentsAction = RecentsAction.MENU,
    val doubleTapAction: RecentsAction = RecentsAction.SPLIT,
    /** The outer screen has its own gestures (below); off: the ones above work on both screens.
     *  Made for one hand on the small screen: pull a card down to open it, double tap for a
     *  pop-up window (split screen rarely fits there). */
    val outerGestures: Boolean = true,
    val outerSwipeUpAction: RecentsAction = RecentsAction.CLOSE,
    val outerSwipeDownAction: RecentsAction = RecentsAction.OPEN,
    val outerSwipeLeftAction: RecentsAction = RecentsAction.CLOSE,
    val outerSwipeRightAction: RecentsAction = RecentsAction.CLOSE,
    val outerLongPressAction: RecentsAction = RecentsAction.MENU,
    val outerDoubleTapAction: RecentsAction = RecentsAction.POPUP,
    /** The outer screen has a Recents look of its own: [outerLook] is these settings with the
     *  outer screen's theme, cards, glass, buttons and animations (each one the unfolded
     *  screen's until changed for the outer screen). */
    val outerLookEnabled: Boolean = false,
    val outerLook: RecentsSettings? = null,
    /** Pinned apps ("package/user"): first in Recents, never closed by Close all; with root and
     *  [pinKeepAlive] also kept from being closed in the background. [btnPin]: the
     *  pin button on cards. */
    val pinned: Set<String> = emptySet(),
    val btnPin: Boolean = true,
    val pinKeepAlive: Boolean = true,
    val attachedSide: RecentsAttachedSide = RecentsAttachedSide.LEFT,
    val attachedPeek: Float = 0.12f,
    /** Peek view via SystemUI's own snap (Folark's module). Off by default: Folark instead drags
     *  the divider like a finger, which some ROMs handle far more reliably (the module's cold
     *  snap can crash System UI on certain builds). On: the exact native peek strip, where it works. */
    val attachedNative: Boolean = false,
    /** How a split screen is formed (drag to an edge, the split picker): one of SPLIT_ENGINE_*. */
    val splitEngine: Int = LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING,
    /** How the split picker (the drawer beside a dragged app) looks. */
    val picker: SplitPickerLook = SplitPickerLook(),
    /** Which drop zones a dragged app has (Recents, the taskbar, the home drop bar). */
    val dropZones: DropZones = DropZones(),
    /** An "Undo split" pill on the taskbar for a few seconds after a split forms. */
    val splitUndo: Boolean = true,
    /** The split divider stops at 33 %, 50 % and 67 % (Folark's Vector module in System UI). */
    val dividerStops: Boolean = true,
    /** The split divider stays where it is let go, for two apps and three (needs the module). */
    val freeDivider: Boolean = true,
    /** Divider lock: one of [DividerLock] (which splits' dividers can't be dragged). */
    val dividerLock: DividerLock = DividerLock.OFF,
    /** Faint marks at ⅓, ½, ⅔ while the divider is dragged, and a let-go near one lands on it. */
    val dividerMarks: Boolean = true,
    /** A haptic tick when the dragged divider passes ⅓, ½ or ⅔. */
    val dividerHaptics: Boolean = true,
    /** A split keeps its own sizes when the phone turns or folds (Motorola resets them). */
    val keepSplitSize: Boolean = true,
    /** Each set of apps opens again at the sizes it had last time. */
    val rememberSplitSize: Boolean = true,
    /** While something is dragged out of an app in split screen, the panes show where it can go. */
    val splitDragHint: Boolean = true,
    /** Flinging the divider of peek view towards the strip closes the app peeking in. */
    val peekFlingClose: Boolean = false,
    /** A third app joining a split makes three equal thirds, not Motorola's carousel (needs the module). */
    val threeEven: Boolean = true,
    /** Divider sizes (⅓, ½, ⅔) in the taskbar's split menu (needs Folark's Vector module). */
    val splitRatioMenu: Boolean = true,
    /** "Open with …": opening an app you often pair offers its usual partner for a few seconds. */
    val openWith: Boolean = false,
    /** Long-press a notification (Folark's widgets, the taskbar) to open it beside the last app. */
    val notificationSplit: Boolean = true,
)

/** Which splits keep their divider where it was put. */
enum class DividerLock(override val stored: String) : StoredChoice {
    OFF("off"),
    TWO("two"),
    THREE("three"),
    ALL("all"),
}

/** Button press animations: sink in, overshoot, a halo, a spreading ring, lean in, wobble. */
enum class PressEffect(override val stored: String) : StoredChoice {
    SHRINK("shrink"),
    BOUNCE("bounce"),
    GLOW("glow"),
    RIPPLE("ripple"),
    TILT("tilt"),
    JELLY("jelly"),
    NONE("none"),
}

/** What a gesture on a Recents card does. */
enum class RecentsAction(override val stored: String) : StoredChoice {
    NONE("none"),

    /** A menu on the card: split screen, freeform, lock, close. */
    MENU("menu"),

    CLOSE("close"),
    SPLIT("split"),
    POPUP("popup"),
    UNSPLIT("unsplit"),
    PIN("pin"),
    INFO("info"),
    OPEN("open"),

    /** Peek view: one app big, another peeking in from the edge (on a split card: that split). */
    PEEK("peek"),

    /** Split 3 apps: pick two more (needs the three-app split switch and the SystemUI hook). */
    SPLIT3("split3"),

    /** Closes every other unpinned app (needs root, like Close). */
    CLOSE_OTHERS("close_others"),

    /** Same as the Close all button. */
    CLOSE_ALL("close_all"),
}

/** The side attached Recents opens from. */
enum class RecentsAttachedSide(override val stored: String) : StoredChoice {
    LEFT("left"),
    RIGHT("right"),
}

/** Where a Recents card shows the app's name and icon. */
enum class RecentsCardLayout(override val stored: String) : StoredChoice {
    HEADER("header"),
    OVERLAY("overlay"),
}

/**
 * Recents entrances: up from below, out of the distance, falling in diagonally, turning over,
 * fanning out from a stack, coming into focus, and more.
 */
enum class RecentsEnter(override val stored: String) : StoredChoice {
    RISE("rise"),
    ZOOM("zoom"),
    CASCADE("cascade"),
    FLIP("flip"),
    FAN("fan"),
    FOCUS("focus"),

    /** A ripple running across the cards: each rises with a little overshoot, left to right. */
    WAVE("wave"),

    /** Each card swings down into place from its top edge, like a sign on a hinge. */
    SWING("swing"),

    /** Cards fall in from above and settle with a bounce. */
    DROP("drop"),

    NONE("none"),
}

/**
 * A card picked for split view: a hop, a spreading ring, lifted up, leaning to its side, a
 * breathing halo, a little shake.
 */
enum class RecentsSelect(override val stored: String) : StoredChoice {
    POP("pop"),
    RING("ring"),
    LIFT("lift"),
    TILT("tilt"),
    GLOW("glow"),
    JIGGLE("jiggle"),
    NONE("none"),
}

/** The order of the Recents cards. */
enum class RecentsSort(override val stored: String) : StoredChoice {
    RECENT("recent"),
    NAME("name"),
}

/**
 * Recents themes: Folark's card grid, iPad (big windows in a row), Carousel (one big card in the
 * middle), Deck (overlapping cards), Compact (a list), Switcher (big icons), and more.
 */
enum class RecentsTheme(override val stored: String) : StoredChoice {
    FOLARK("folark"),
    IPAD("ipad"),
    CAROUSEL("carousel"),
    DECK("deck"),
    COMPACT("compact"),
    SWITCHER("switcher"),
    STACK("stack"),
    ARC("arc"),

    /** Tall rounded cards you snap sideways, the app's icon and name above each. */
    PORTRAIT("portrait"),

    /** The middle card large and centred, its icon and name above it; neighbours peek in. */
    SPOTLIGHT("spotlight"),

    /** A row of larger cards, two or three in view, for the unfolded screen. */
    GALLERY("gallery"),
}
