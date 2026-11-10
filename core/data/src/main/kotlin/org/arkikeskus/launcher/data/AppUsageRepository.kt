package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.catching
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/** A per-app launch statistic: a time-decayed [score] and the [lastUsed] epoch-millis timestamp. */
data class UsageStat(val score: Float, val lastUsed: Long)

/**
 * Tracks how often each app is launched, as a time-decayed "frecency" score. Each launch decays the
 * existing score to now and adds 1; ranking decays every score to now for a fair comparison, so a
 * one-off launch fades below apps used regularly. Stored in the same [DataStore] as the other
 * preferences (serialized one app per line, tab-separated: `key\tscore\tlastUsed`).
 *
 * Launches are folded in memory and written once after [FLUSH_DELAY_MS] — one flash write for a
 * burst (opening a pair, trying a few apps) instead of one per icon tap. [usage] shows them at once,
 * and [flush] writes them when the launcher goes to the background, so nothing is lost.
 */
@Singleton
class AppUsageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** The map last read from the store, kept current by one collector for the app's lifetime. */
    private val stored = MutableStateFlow<Map<String, UsageStat>>(emptyMap())

    /**
     * Launches recorded since the last write, as their times, per app. Kept raw (not as a finished
     * score) so the write applies them on top of what the store holds then: a launch recorded
     * before the store was first read (a cold start) can never replace an app's history with 1.
     */
    private val waiting = MutableStateFlow<Map<String, List<Long>>>(emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushing = Mutex()
    private var flushJob: Job? = null

    init {
        scope.launch {
            dataStore.data
                .map { it[Keys.APP_USAGE] }
                .distinctUntilChanged()
                .collect { raw -> stored.value = parse(raw) }
        }
    }

    // Only when the usage counts themselves change (not on every write to the shared store).
    val usage: Flow<Map<String, UsageStat>> = combine(stored, waiting) { saved, pending ->
        if (pending.isEmpty()) saved else saved + withLaunches(saved, pending)
    }.distinctUntilChanged()

    /** Records a launch of [key] now: decays its stored score to now, then adds 1. */
    suspend fun recordLaunch(key: String) = recordLaunches(listOf(key))

    /**
     * Records launches of [keys] (an app pair or trio) in one write instead of one per app. Apps
     * whose score has faded to almost nothing (unused for weeks, or uninstalled) are dropped, so
     * the stored list doesn't grow forever.
     */
    suspend fun recordLaunches(keys: List<String>) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        waiting.update { pending ->
            val next = pending.toMutableMap()
            for (key in keys) next[key] = next[key].orEmpty() + now
            next
        }
        // A burst of launches becomes one store write, and one fan-out to every settings collector.
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flushNow()
        }
    }

    /** Writes the launches waiting in memory now (called when the launcher goes to the background). */
    fun flush() {
        flushJob?.cancel()
        flushJob = null
        scope.launch { flushNow() }
    }

    private suspend fun flushNow() = flushing.withLock {
        val pending = waiting.value
        if (pending.isEmpty()) return@withLock
        val written = catching {
            dataStore.edit { p ->
                val now = System.currentTimeMillis()
                val stats = parse(p[Keys.APP_USAGE]).toMutableMap()
                // Applied to what the store holds now, launch by launch, in order.
                stats.putAll(withLaunches(stats, pending))
                stats.entries.removeIf { (k, stat) -> k !in pending && currentScore(stat, now) < FORGET_BELOW }
                p[Keys.APP_USAGE] = serialize(stats)
            }
        }.isSuccess
        if (written) {
            // Launches recorded while the write was in flight came after these: keep them for the
            // next flush (only ever appended, so dropping the written count leaves exactly those).
            waiting.update { cur ->
                cur.mapValues { (k, times) -> times.drop(pending[k]?.size ?: 0) }.filterValues { it.isNotEmpty() }
            }
        }
    }

    /** [pending] launches applied on top of [base]: each app's stat after its launches, in order. */
    private fun withLaunches(base: Map<String, UsageStat>, pending: Map<String, List<Long>>): Map<String, UsageStat> =
        pending.filterValues { it.isNotEmpty() }
            .mapValues { (key, times) -> times.fold(base[key]) { stat, at -> applyLaunch(stat, at) }!! }

    private fun parse(raw: String?): Map<String, UsageStat> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 3) return@mapNotNull null
            val score = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val lastUsed = parts[2].toLongOrNull() ?: return@mapNotNull null
            parts[0] to UsageStat(score, lastUsed)
        }?.toMap() ?: emptyMap()

    private fun serialize(stats: Map<String, UsageStat>): String =
        stats.entries.joinToString("\n") { (k, s) -> "$k\t${s.score}\t${s.lastUsed}" }

    private object Keys {
        val APP_USAGE = stringPreferencesKey(USAGE_KEY)
    }

    companion object {
        /** DataStore key name for the serialized usage map. Exposed so [SettingsRepository] can
         *  exclude this volatile, device-local data from a backup (it otherwise changed the Drive
         *  dedup hash on every launch → a redundant upload each run). */
        const val USAGE_KEY = "app_usage"

        /** How long a burst of launches is gathered before the one write (F7). */
        const val FLUSH_DELAY_MS = 2_000L

        /** A score this small (one launch about six weeks ago) is forgotten at the next write. */
        const val FORGET_BELOW = 0.015f

        /** Half-life of the launch score; recent launches dominate within ~1–2 weeks. */
        const val HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000

        /** Decayed score of [stat] at [atMillis]; a backwards clock clamps elapsed to 0 (no inflation). */
        fun currentScore(stat: UsageStat, atMillis: Long): Float {
            val elapsed = (atMillis - stat.lastUsed).coerceAtLeast(0L)
            return (stat.score * 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS)).toFloat()
        }

        /** The stat after a launch at [atMillis]: decayed previous score + 1, timestamped at [atMillis]. */
        fun applyLaunch(prev: UsageStat?, atMillis: Long): UsageStat {
            val decayed = prev?.let { currentScore(it, atMillis) } ?: 0f
            return UsageStat(decayed + 1f, atMillis)
        }
    }
}
