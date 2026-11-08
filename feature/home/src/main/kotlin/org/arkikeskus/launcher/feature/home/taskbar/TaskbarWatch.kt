package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

/**
 * The app-watch polling pace ([TaskbarHost] ticks on this): one small policy of its own so it can
 * be tested on the JVM instead of living inside the handler loop.
 */
internal object TaskbarWatch {
    /** After a switch (or checkNow()): the fast pace, so the bar is current at once. */
    const val FAST_MS = 1_000L
    /** Cap while the bar itself is shown (the running dots are visible and must keep up). */
    const val SLOW_MS = 3_000L
    /** Cap while the bar is not shown (handles only, hidden here): nobody is watching, and
     *  checkNow() (screen on, unfold, a handle touch) returns to the fast pace before anything
     *  could be late. Saves a usage-history query every few seconds while the user is in an app. */
    const val HIDDEN_MS = 30_000L
    /** Cap while the accessibility service hears switches itself: this only catches a missed event
     *  (a service killed mid-session), and the dots are current without any polling at all. */
    const val SAFETY_MS = 20_000L

    /**
     * The delay until the next tick: back to [FAST_MS] after a change, otherwise easing ×1.5 from
     * the current delay towards [cap].
     */
    fun nextDelay(changed: Boolean, current: Long, cap: Long): Long =
        if (changed) FAST_MS else minOf(cap, current * 3 / 2)

    /**
     * The cap for this tick: the short one while the bar shows (its running dots must keep up), the
     * long one while it does not, and the safety-net pace when [heard] — the accessibility service
     * subscribes to TYPE_WINDOW_STATE_CHANGED and reports every switch faster than a poll could.
     */
    fun cap(barShown: Boolean, heard: Boolean): Long = when {
        heard -> SAFETY_MS
        barShown -> SLOW_MS
        else -> HIDDEN_MS
    }
}
