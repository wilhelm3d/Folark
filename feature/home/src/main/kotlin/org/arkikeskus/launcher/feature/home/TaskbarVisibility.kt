package org.arkikeskus.launcher.feature.home

/**
 * Which form the taskbar takes right now, worked out from plain facts so the rules can be unit
 * tested apart from the windows that draw them ([AppTaskbar.refresh] supplies the facts and draws
 * the answer).
 */
internal object TaskbarVisibility {

    enum class Mode { NONE, HANDLES, BAR, BAR_FIXED, MINI }

    data class Facts(
        /** Settings ▸ Taskbar is on (and Folark's taskbar is the one chosen). */
        val enabled: Boolean,
        val screenOn: Boolean,
        val keyguardLocked: Boolean,
        /** Anything to show: pinned apps, pairs, recent apps, the media icon or a live activity. */
        val hasContent: Boolean,
        /** Folark's home (or its drawer) is in front. */
        val homeVisible: Boolean,
        /** The keyboard is up over home or the drawer. */
        val keyboardOnHome: Boolean,
        /** Folark's app drawer is open over home. */
        val drawerOpen: Boolean = false,
        /** The keyboard is up in the app in front. */
        val keyboardUp: Boolean,
        /** The hide button was tapped on home, or in [foreground] (remembered per app). */
        val manuallyHidden: Boolean,
        /** Just opened from a handle or the mini pill. */
        val expanded: Boolean,
        /** Settings ▸ Taskbar ▸ On home. */
        val onHome: Boolean,
        /** Settings ▸ Taskbar ▸ Keep open over apps. */
        val keepOpen: Boolean,
        /** Settings ▸ Taskbar ▸ Hide over full-screen apps. */
        val hideFullScreen: Boolean,
        /** The app in front hides Android's bars (a video, a game). */
        val fullScreen: Boolean,
        /** The app in front, when known. */
        val foreground: String?,
        val hiddenApps: Set<String> = emptySet(),
        val alwaysApps: Set<String> = emptySet(),
        val autoHideApps: Set<String> = emptySet(),
        val miniApps: Set<String> = emptySet(),
    )

    /** Whether the taskbar takes any form at all. */
    fun active(f: Facts): Boolean = f.enabled && f.screenOn && !f.keyguardLocked && f.hasContent

    fun mode(f: Facts): Mode {
        if (!active(f)) return Mode.NONE
        return when {
            // Typing on home or in the drawer: the bar would cover the keyboard's bottom row.
            f.homeVisible && f.keyboardOnHome -> Mode.NONE
            // The app drawer is open: its own bottom row (search, dock) is where the bar would sit.
            f.homeVisible && f.drawerOpen -> Mode.NONE
            // The hide button was tapped: show only the pull handles until a handle swipe reopens it.
            f.manuallyHidden && !f.expanded -> Mode.HANDLES
            f.homeVisible && f.onHome -> Mode.BAR_FIXED
            f.homeVisible -> Mode.NONE
            // Settings ▸ Taskbar ▸ per app: never in these; always (not over the keyboard) in those.
            f.foreground in f.hiddenApps -> Mode.NONE
            f.foreground in f.alwaysApps && !f.keyboardUp -> Mode.BAR_FIXED
            // Just opened from a handle or the mini pill: the full bar (it collapses again by itself).
            f.expanded -> Mode.BAR
            // Per app: auto-hide (a corner swipe reveals it), or a small tap-to-open mini pill.
            f.foreground in f.autoHideApps -> Mode.HANDLES
            // The mini pill steps aside over a full-screen video too (a corner swipe still opens the bar).
            f.foreground in f.miniApps && !f.keyboardUp && !(f.hideFullScreen && f.fullScreen) -> Mode.MINI
            // Full screen (a video, a game): no bar covering it; a corner swipe still opens it.
            f.keepOpen && !(f.hideFullScreen && f.fullScreen) && !f.keyboardUp -> Mode.BAR_FIXED
            else -> Mode.HANDLES
        }
    }

    /**
     * Whether the bar needs to know the app in front: per-app rules (auto-hide only matters for a
     * kept-open bar, handles being the default otherwise), apps the bar was hidden in, and a
     * kept-open bar whose recent apps or running dots should follow app switches.
     */
    fun needsForeground(
        hiddenApps: Set<String>,
        alwaysApps: Set<String>,
        autoHideApps: Set<String>,
        miniApps: Set<String>,
        hiddenHere: Set<String>,
        keepOpen: Boolean,
        followsOpenApps: Boolean,
    ): Boolean = hiddenApps.isNotEmpty() || alwaysApps.isNotEmpty() || miniApps.isNotEmpty() ||
        hiddenHere.isNotEmpty() || (keepOpen && (autoHideApps.isNotEmpty() || followsOpenApps))
}
