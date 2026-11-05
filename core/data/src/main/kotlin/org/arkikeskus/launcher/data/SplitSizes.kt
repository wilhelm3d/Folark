package org.arkikeskus.launcher.data

import android.content.Context

/**
 * S1: the sizes each set of apps last had in split screen, so they open the same way next time.
 * Kept per apps in screen order (left to right / top to bottom), as divider places in thousandths
 * of the screen: "pkgA|pkgB" → "667", "pkgA|pkgB|pkgC" → "250,500".
 */
object SplitSizes {
    private const val PREFS = "folark_split_sizes"
    /** Old sets are forgotten beyond this many (the oldest first). */
    private const val MAX = 200

    private fun key(packages: List<String>) = packages.joinToString("|")

    /** System UI reported a rest: [packages] left to right at divider places [fractions]. */
    fun remember(context: Context, packages: List<String>, fractions: List<Int>) {
        if (packages.size !in 2..3 || fractions.size != packages.size - 1) return
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val edit = p.edit()
            if (p.all.size > MAX) edit.clear()
            edit.putString(key(packages), fractions.joinToString(",")).apply()
        }
    }

    /** The divider places [packages] (left to right) had last time, or null. */
    fun of(context: Context, packages: List<String>): List<Int>? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(packages), null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == packages.size - 1 }
    }.getOrNull()

    /** True when [fractions] is just half and half (nothing to restore for two apps). */
    fun isEven(fractions: List<Int>) = fractions.size == 1 && fractions[0] in 480..520
}
