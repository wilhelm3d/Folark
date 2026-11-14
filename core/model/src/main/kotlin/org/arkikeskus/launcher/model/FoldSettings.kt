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
    val foldAction: FoldAction = FoldAction.STAY,
    val unfoldAction: FoldAction = FoldAction.STAY,
    val transitionStyle: FoldTransition = FoldTransition.BOOK_UNFOLD_SWEEP,
    /** How fast this display's switch animation plays (1 = normal; higher is faster). */
    val transitionSpeed: Float = 1f,
    /** Live hinge transition (both displays' settings): the angle the effect ends at (degrees),
     *  and how much the readings are smoothed (ms, 0 = raw). */
    val liveHingeEndAngle: Int = 165,
    val liveHingeSmoothingMs: Int = 50,
    /** Live hinge: the effect (one of [LiveHingeStyle]), and its blur (dp) and frost (0..1). */
    val liveHingeStyle: LiveHingeStyle = LiveHingeStyle.PAGES,
    /** The effect the outer display plays (on a timer) when the phone is closed. */
    val liveHingeOuterStyle: LiveHingeStyle = LiveHingeStyle.WATER,
    val liveHingeBlurDp: Int = 24,
    val liveHingeFrost: Float = 0.2f,
)

/** What happens to home when the phone is folded or unfolded. */
enum class FoldAction(override val stored: String) : StoredChoice {
    PAGE_0("page_0"),
    LOCK("lock"),
    SEARCH("search"),
    DRAWER("drawer"),
    STAY("stay"),
    ;

    companion object {
        /** What folding the phone can do to home. */
        val ON_FOLD: List<FoldAction> = listOf(STAY, PAGE_0, LOCK)

        /** What unfolding the phone can do to home. */
        val ON_UNFOLD: List<FoldAction> = listOf(STAY, SEARCH, DRAWER)
    }
}

/** The animation when the phone is folded or unfolded. */
enum class FoldTransition(override val stored: String) : StoredChoice {
    MORPH_SCALE_FADE("morph_scale_fade"),
    BOOK_UNFOLD_SWEEP("book_unfold_sweep"),
    AQUAMORPHIC_RIPPLE("aquamorphic_ripple"),
    CROSSFADE("crossfade"),
    SCALE("scale"),
    SLIDE("slide"),

    /** Follows the hinge while the phone opens (needs a hinge sensor; the inner display only). */
    LIVE_HINGE("live_hinge"),
}

/** How home follows the hinge while the phone opens. */
enum class LiveHingeStyle(override val stored: String) : StoredChoice {
    /** Two pages folding flat around the hinge. */
    PAGES("pages"),

    /** Rings spreading from the hinge, like water settling. */
    WATER("water"),

    /** Black and white and dim, colour flowing in. */
    INK("ink"),

    /** Out of black. */
    FADE("fade"),

    /** Falling in from above, as if dropping off the outer screen. */
    DROP("drop"),

    /** Two halves sliding in to meet at the hinge. */
    DOORS("doors"),

    /** A circle opening from the hinge. */
    PORTAL("portal"),

    /** Coming forward out of depth. */
    ZOOM("zoom"),
}
