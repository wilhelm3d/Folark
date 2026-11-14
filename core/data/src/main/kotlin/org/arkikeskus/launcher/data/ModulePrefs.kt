package org.arkikeskus.launcher.data

/**
 * The small SharedPreferences file where Folark notes what its Vector module (in the system
 * launcher and System UI) last told it. Written by the module's provider calls ([SEEN_AT],
 * [COMPAT], [UNLOCK_SEEN_AT]) and by [SplitThree] (its split3_* entries); read by Settings to show
 * "module active" / "limited". One name for every reader and writer, so none of them can drift.
 */
object ModulePrefs {
    const val FILE = "folark_recents_hook"

    /** When the module in the system launcher last asked for Folark's Recents state (epoch ms). */
    const val SEEN_AT = "seen_at"

    /** Which launcher internals the module could hook, as the bit set it reported. */
    const val COMPAT = "compat"

    /** When the module last answered an unlock (epoch ms). */
    const val UNLOCK_SEEN_AT = "unlock_seen_at"
}
