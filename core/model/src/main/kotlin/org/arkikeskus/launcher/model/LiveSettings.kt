package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Live activities and notification pop-ups. */
@Immutable
data class LiveSettings(
    /** Ongoing things (a timer, navigation, a call, a download, Android 16 live updates) as a
     *  live pill at the top of home, and on the taskbar; [activityEffect] is one of LIVE_EFFECT_*. */
    val activities: Boolean = true,
    val activitiesTaskbar: Boolean = true,
    val activityEffect: String = LauncherSettings.LIVE_EFFECT_GLOW,
    /** A long press opens the pill up (full progress, its buttons); off: it shows the next one. */
    val expandOnLongPress: Boolean = true,
    /** Leaves out what Android already shows as a chip in its status bar (calls, Live
     *  updates) while that status bar is showing, so it isn't there twice. */
    val skipSystemChips: Boolean = true,
    /** An app's long-press menu lists its current notifications (both displays). */
    val popupNotifications: Boolean = true,
)
