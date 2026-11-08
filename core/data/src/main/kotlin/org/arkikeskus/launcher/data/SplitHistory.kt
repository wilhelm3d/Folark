package org.arkikeskus.launcher.data

import android.content.Context
import org.arkikeskus.launcher.model.catching

/**
 * The last few app pairs put side by side (packages only), newest first, for the split picker's
 * "Paired before" row: drag Chrome to an edge and Maps is offered again if they shared a split
 * lately. For "Open with …" it also keeps when each pair was last made (the last few times) and how
 * often its suggestion was passed over. Kept in Folark's private storage on the device (not in
 * backups), never sent anywhere.
 */
object SplitHistory {
    private const val PREFS = "folark_split_history"
    private const val PAIRS = "pairs"
    private const val TIMES = "times_"
    private const val IGNORED = "ignored_"
    private const val MAX_PAIRS = 24
    /** Times kept per pair: enough to tell "often lately". */
    private const val MAX_TIMES = 6
    private val SAFE = Regex("""^[\w.]+$""")

    /** "Open with …" offers a partner paired at least this often within [SUGGEST_WINDOW_MS]. */
    const val SUGGEST_MIN_TIMES = 3
    const val SUGGEST_WINDOW_MS = 30L * 24 * 3600 * 1000
    /** Passed over this often, a pair's suggestion stops. */
    const val SUGGEST_MAX_IGNORED = 3

    private fun entry(a: String, b: String) = listOf(a, b).sorted().joinToString("|")

    /** [a] and [b] were just put side by side. */
    fun record(context: Context, a: String, b: String, now: Long = System.currentTimeMillis()) {
        if (a == b || !SAFE.matches(a) || !SAFE.matches(b)) return
        catching {
            val p = context.getSharedPreferences(PREFS, 0)
            val entry = entry(a, b)
            val kept = read(p.getString(PAIRS, null)).filterNot { it == entry }
            val next = (listOf(entry) + kept).take(MAX_PAIRS)
            val times = (times(p.getString(TIMES + entry, null)) + now).takeLast(MAX_TIMES)
            val edit = p.edit()
                .putString(PAIRS, next.joinToString("\n"))
                .putString(TIMES + entry, times.joinToString(","))
            // A pair that fell off the list takes its times along.
            (listOf(entry) + kept).drop(MAX_PAIRS).forEach { edit.remove(TIMES + it).remove(IGNORED + it) }
            edit.apply()
        }
    }

    /** The packages [packageName] was last paired with, newest first. */
    fun partners(context: Context, packageName: String): List<String> = catching {
        read(context.getSharedPreferences(PREFS, 0).getString(PAIRS, null))
            .mapNotNull { e -> other(e, packageName) }
            .distinct()
    }.getOrDefault(emptyList())

    /**
     * The partner "Open with …" offers for [packageName] now, or null: paired at least
     * [SUGGEST_MIN_TIMES] times in the last [SUGGEST_WINDOW_MS], and not passed over
     * [SUGGEST_MAX_IGNORED] times since.
     */
    fun suggestion(context: Context, packageName: String, now: Long = System.currentTimeMillis()): String? = catching {
        val p = context.getSharedPreferences(PREFS, 0)
        val pairs = read(p.getString(PAIRS, null))
        pick(
            packageName = packageName,
            times = pairs.associateWith { times(p.getString(TIMES + it, null)) },
            ignored = pairs.associateWith { p.getInt(IGNORED + it, 0) },
            now = now,
        )
    }.getOrNull()

    /** The suggestion of [a] and [b] went by without a tap. */
    fun ignored(context: Context, a: String, b: String) {
        catching {
            val p = context.getSharedPreferences(PREFS, 0)
            val key = IGNORED + entry(a, b)
            p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
        }
    }

    /** The suggestion of [a] and [b] was taken: it may be offered again from now on. */
    fun accepted(context: Context, a: String, b: String) {
        catching { context.getSharedPreferences(PREFS, 0).edit().remove(IGNORED + entry(a, b)).apply() }
    }

    /** Forgets every pair (Settings ▸ Recents ▸ Split picker ▸ Forget paired apps). */
    fun clear(context: Context) {
        catching { context.getSharedPreferences(PREFS, 0).edit().clear().apply() }
    }

    /**
     * The choice behind [suggestion], on plain data: of the pairs with [packageName] (keys "a|b"),
     * the one made most often within the window, newest first on a tie.
     */
    internal fun pick(packageName: String, times: Map<String, List<Long>>, ignored: Map<String, Int>, now: Long): String? =
        times.entries
            .mapNotNull { (entry, at) ->
                val partner = other(entry, packageName) ?: return@mapNotNull null
                if ((ignored[entry] ?: 0) >= SUGGEST_MAX_IGNORED) return@mapNotNull null
                val recent = at.filter { now - it in 0..SUGGEST_WINDOW_MS }
                if (recent.size < SUGGEST_MIN_TIMES) return@mapNotNull null
                Triple(partner, recent.size, recent.max())
            }
            .maxWithOrNull(compareBy<Triple<String, Int, Long>> { it.second }.thenBy { it.third })
            ?.first

    private fun other(entry: String, packageName: String): String? {
        val (x, y) = entry.split("|").takeIf { it.size == 2 } ?: return null
        return when (packageName) {
            x -> y
            y -> x
            else -> null
        }
    }

    private fun read(raw: String?): List<String> = raw?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun times(raw: String?): List<Long> = raw?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
}
