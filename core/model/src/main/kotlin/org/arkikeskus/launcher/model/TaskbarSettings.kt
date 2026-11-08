package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark's taskbar (and which taskbar is used). */
@Immutable
data class TaskbarSettings(
    /** A pull-up taskbar over other apps on the inner display (needs the accessibility service). */
    val enabled: Boolean = false,
    /** Taskbar: the apps always in it, in order; null = the inner dock's apps. */
    val pinnedApps: List<String>? = null,
    /** Taskbar: how many recently used apps follow the pinned ones (0..6). */
    val recentCount: Int = 4,
    /** Taskbar: icon size in dp. */
    val iconSize: Int = 48,
    /** Taskbar: room between icons in dp. */
    val iconSpacing: Int = 8,
    /** Taskbar: distance from the bottom edge in dp. */
    val bottomOffset: Int = 12,
    /** Taskbar: lift it (and the corner handles and Mini pill) clear of the space the system
     *  keeps at the bottom for its gesture handle or navigation bar, so it never sits on top of
     *  Android's home swipe area. Measured per display; off keeps it flush to the edge. */
    val clearSystemInset: Boolean = true,
    /** Taskbar: its look, one of TASKBAR_THEME_*. */
    val theme: String = LauncherSettings.TASKBAR_THEME_CLEAR,
    /** Taskbar: how solid the backdrop of the non-clear themes is. */
    val opacity: Float = 0.8f,
    /** Taskbar: with the Glass theme, how far the wallpaper/apps behind the bar are blurred (dp,
     *  real backdrop blur; 0 = off). Needs Android 12+ and the system's cross-window blur. */
    val glassBlur: Int = 24,
    /** Taskbar: which bottom corners open it over other apps, one of TASKBAR_HANDLES_*. */
    val handles: String = LauncherSettings.TASKBAR_HANDLES_BOTH,
    /** Taskbar: always shown on Folark's home screen and app drawer. */
    val onHome: Boolean = true,
    /** Taskbar: stays open over other apps instead of opening from the corners. */
    val keepOpen: Boolean = false,
    /** Taskbar: an icon with the cover of what is playing. */
    val mediaEnabled: Boolean = true,
    /** Taskbar media icon: also shown while paused (not only while playing). */
    val mediaShowPaused: Boolean = true,
    /** Taskbar media icon: where it sits, one of TASKBAR_MEDIA_POS_*. */
    val mediaPosition: String = LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE,
    /** The mark between pinned apps (and pairs) and recent apps: one of TASKBAR_DIVIDER_*. */
    val dividerStyle: String = LauncherSettings.TASKBAR_DIVIDER_LINE,
    /** Taskbar media icon: what a tap does, one of TASKBAR_MEDIA_TAP_*. */
    val mediaTap: String = LauncherSettings.TASKBAR_MEDIA_TAP_OPEN,
    /** Taskbar media icon: pulling it up shows back, play/pause and forward. */
    val mediaControls: Boolean = true,
    /** Taskbar media icon: the cover's shape, one of TASKBAR_MEDIA_SHAPE_*. */
    val mediaShape: String = LauncherSettings.TASKBAR_MEDIA_SHAPE_CIRCLE,
    /** Taskbar media icon: the player app's icon as a small badge on the cover. */
    val mediaBadge: Boolean = true,
    /** Taskbar over an app: a drag to the right (an icon or a shortcut) uses Android's own split drop zones. */
    val systemDrag: Boolean = true,
    /** Taskbar: drag an icon to a screen edge to snap it there — left/right half (split), top
     *  (full screen, or a third app over a split), bottom (floating window), with glass drop
     *  zones, Windows-snap style. Off keeps the up-left-float / up-right-split drag. */
    val edgeSnap: Boolean = true,
    /** Taskbar: the recents button, one of TASKBAR_RECENTS_*. */
    val recentsButton: String = LauncherSettings.TASKBAR_RECENTS_SYSTEM,
    /** Taskbar: holding the recents button goes back to the last app. */
    val quickSwitch: Boolean = true,
    /** Taskbar: a button for the split screen (swap sides, one app full screen). */
    val splitControls: Boolean = true,
    /** Taskbar: the side a new split-screen app opens on, one of TASKBAR_SPLIT_SIDE_*. */
    val splitSide: String = LauncherSettings.TASKBAR_SPLIT_SIDE_RIGHT,
    /** Taskbar: a drag over two apps side by side can add a third (Android's drop zones). */
    val threeApp: Boolean = true,
    /** Taskbar: kept-open bar steps aside while an app is full screen (video, games). */
    val hideFullScreen: Boolean = true,
    /** Taskbar: the apps that are open now instead of the recently used ones, and a dot under the
     *  icons of open apps (both need the one-time DUMP grant). */
    val runningApps: Boolean = false,
    val runningDots: Boolean = true,
    /** Taskbar: the running dot widens into a pill for an app with two or more open windows
     *  (split pairs, pop-ups), Windows-style. Needs [runningDots]. */
    val multiInstanceDots: Boolean = true,
    /** Taskbar per app (package names): never shown in [hiddenApps]; always shown in
     *  [alwaysApps], even full screen or when it isn't kept open; hidden but revealed by a
     *  corner swipe in [autoHideApps]; a small tap-to-open pill in [miniApps]. */
    val hiddenApps: Set<String> = emptySet(),
    val alwaysApps: Set<String> = emptySet(),
    val autoHideApps: Set<String> = emptySet(),
    val miniApps: Set<String> = emptySet(),
    /** Taskbar: a small visible line at the corners where a swipe up opens it (when it isn't kept open). */
    val handleHint: Boolean = true,
    /** The look of that pull hint: one of TASKBAR_HANDLE_* — a line, one or two arrows, dots or text. */
    val taskbarHandleStyle: String = LauncherSettings.TASKBAR_HANDLE_LINE,
    /** A button on the bar (next to Recents) that hides the bar and leaves the pull handles. */
    val hideButton: Boolean = true,
    /** The pinned apps (and scenes) stay as they are: nothing is added, removed or moved from
     *  the bar, the drawer, home or Recents (only Settings ▸ Taskbar can change them). */
    val pinsLocked: Boolean = false,
    /** A swipe down on the opened bar hides it to the pull handles. */
    val swipeDownHide: Boolean = true,
    /** Open apps on the bar as small pictures of the app (as Recents shows them, root). */
    val openPreviews: Boolean = false,
    /** How the bar shows and hides: one of TASKBAR_ANIM_*. */
    val taskbarAnim: String = LauncherSettings.TASKBAR_ANIM_SLIDE,
    /** The glow when an app is started from the bar: in the app's colour, the accent colour, white, or none (LAUNCH_GLOW_*). */
    val launchGlow: String = LauncherSettings.LAUNCH_GLOW_APP,
    /** Which taskbar: Folark's own, or Android's (the system launcher's, with its gestures and
     *  quick switch); for Android's, its mode (1 always, 2 by a swipe up, 4 off). */
    val source: String = LauncherSettings.TASKBAR_SOURCE_FOLARK,
    val systemMode: Int = 1,
    /** Saved app pairs (and trios) pinned on Folark's taskbar, by pair id. */
    val pinnedPairs: List<String> = emptyList(),
    /** A slim bar on the cover screen too: opened from a bottom corner, [coverCount] apps. */
    val coverBar: Boolean = false,
    val coverCount: Int = 5,
)
