package org.arkikeskus.launcher.data

import android.content.Context
import android.view.WindowManager

/** Which screen of the fold is in use, from places that have no window of their own. */
object Screens {
    /** Shortest side, in dp, from which a screen counts as the inner (unfolded) one. */
    private const val INNER_MIN_DP = 600f

    /** The inner screen is in use (the phone is open); false on the cover screen or when unknown. */
    fun innerShowing(context: Context): Boolean = runCatching {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val density = context.resources.displayMetrics.density
        density > 0f && minOf(bounds.width(), bounds.height()) / density >= INNER_MIN_DP
    }.getOrDefault(context.resources.configuration.smallestScreenWidthDp >= INNER_MIN_DP)
}
