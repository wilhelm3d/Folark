package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark Recents. */
@Immutable
data class RecentsSettings(
    /** Folark's own Recents screen (all shared by both displays): opened by the taskbar's Recents
     *  button ([LauncherSettings.TASKBAR_RECENTS_FOLARK]) or a home gesture ([LauncherSettings.GESTURE_RECENTS]). */
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
    /** Newest first ([LauncherSettings.RECENTS_SORT_RECENT]) or by name ([LauncherSettings.RECENTS_SORT_NAME]). */
    val sort: String = LauncherSettings.RECENTS_SORT_RECENT,
    /** When each app was last used, under its name. */
    val showTime: Boolean = true,
    /** Card look: [LauncherSettings.RECENTS_CARD_HEADER] (app row above the picture) or [LauncherSettings.RECENTS_CARD_OVERLAY]
     *  (the picture fills the card, the app icon floats on top — a cleaner, borderless look). */
    val cardLayout: String = LauncherSettings.RECENTS_CARD_OVERLAY,
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
    /** Recents animations: how the cards come in (RECENTS_ENTER_*), how a card picked for split
     *  view answers (RECENTS_SELECT_*), and how fast both play (1 = normal, higher = faster). */
    val enterAnimation: String = LauncherSettings.RECENTS_ENTER_RISE,
    val selectAnimation: String = LauncherSettings.RECENTS_SELECT_POP,
    val animationSpeed: Float = 1f,
    /** How Recents' buttons (and its menu) answer a press: one of PRESS_*. */
    val pressAnimation: String = LauncherSettings.PRESS_SHRINK,
    /** Recents look: one of RECENTS_THEME_*; Liquid Glass on top of any of them (how much the
     *  backdrop is blurred, dp, and how milky the glass is). */
    val theme: String = LauncherSettings.RECENTS_THEME_FOLARK,
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
    /** What a gesture on a Recents card does: one of RECENTS_ACTION_*. Swipes left / right
     *  (fling the card off the side of the screen) in the looks that scroll up and down
     *  (Folark, Compact, Stack, Bento, Switcher); swipes up / down in every look but Compact
     *  and Stack (where up and down scroll). */
    val swipeUpAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val swipeDownAction: String = LauncherSettings.RECENTS_ACTION_NONE,
    val swipeLeftAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val swipeRightAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val longPressAction: String = LauncherSettings.RECENTS_ACTION_MENU,
    val doubleTapAction: String = LauncherSettings.RECENTS_ACTION_SPLIT,
    /** The outer screen has its own gestures (below); off: the ones above work on both screens.
     *  Made for one hand on the small screen: pull a card down to open it, double tap for a
     *  pop-up window (split screen rarely fits there). */
    val outerGestures: Boolean = true,
    val outerSwipeUpAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val outerSwipeDownAction: String = LauncherSettings.RECENTS_ACTION_OPEN,
    val outerSwipeLeftAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val outerSwipeRightAction: String = LauncherSettings.RECENTS_ACTION_CLOSE,
    val outerLongPressAction: String = LauncherSettings.RECENTS_ACTION_MENU,
    val outerDoubleTapAction: String = LauncherSettings.RECENTS_ACTION_POPUP,
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
    val attachedSide: String = LauncherSettings.RECENTS_ATTACHED_LEFT,
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
    /** Divider lock: one of DIVIDER_LOCK_* (which splits' dividers can't be dragged). */
    val dividerLock: String = LauncherSettings.DIVIDER_LOCK_OFF,
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
