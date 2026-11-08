package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folding: half-open mode, fold/unfold actions and the live hinge. */
@Immutable
data class FoldSettings(
    /** Enable tabletop / half-opened layout mode when the device is half-folded. */
    val halfOpenedModeEnabled: Boolean = false,
    /** Half folded: home leans with the hinge (needs the half-folded layout and a hinge sensor). */
    val hingeTilt: Boolean = true,
    /** The home picture (and Android's wallpaper, where it lets a launcher) zooms with the hinge
     *  as the phone opens and closes. */
    val wallpaperHinge: Boolean = false,
    val foldAction: String = LauncherSettings.ACTION_STAY,
    val unfoldAction: String = LauncherSettings.ACTION_STAY,
    val transitionStyle: String = LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP,
    /** How fast this display's switch animation plays (1 = normal; higher is faster). */
    val transitionSpeed: Float = 1f,
    /** Live hinge transition (both displays' settings): the angle the effect ends at (degrees),
     *  and how much the readings are smoothed (ms, 0 = raw). */
    val liveHingeEndAngle: Int = 165,
    val liveHingeSmoothingMs: Int = 50,
    /** Live hinge: the effect (one of LIVE_HINGE_STYLE_*), and its blur (dp) and frost (0..1). */
    val liveHingeStyle: String = LauncherSettings.LIVE_HINGE_STYLE_PAGES,
    /** The effect the outer display plays (on a timer) when the phone is closed. */
    val liveHingeOuterStyle: String = LauncherSettings.LIVE_HINGE_STYLE_WATER,
    val liveHingeBlurDp: Int = 24,
    val liveHingeFrost: Float = 0.2f,
)
