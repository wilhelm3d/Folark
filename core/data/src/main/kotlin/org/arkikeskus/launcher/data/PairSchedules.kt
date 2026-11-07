package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.AppPair
import java.util.Calendar

/**
 * App-pair schedules (Settings ▸ Split screen & peek ▸ App pairs): a scene can be set to come up
 * at a time of day, on chosen weekdays. Stored per pair id as "HH:MM|days" (days = digits 1..7,
 * Monday..Sunday; empty = every day) so it round-trips as plain text, next to the pair itself.
 *
 * Firing is the app module's job (PairScheduler): this file only models, parses and computes the
 * next fire time; the flows live in [SettingsRepository] (they need its DataStore).
 */
data class PairSchedule(val hour: Int, val minute: Int, val days: Set<Int>) {

    /** Locale-independent "HH:MM|days", as the store keeps it. */
    fun serialize(): String = "%02d:%02d|%s".format(hour, minute, days.sorted().joinToString(""))

    companion object {
        fun parse(raw: String?): PairSchedule? {
            raw ?: return null
            val (time, daysRaw) = raw.split("|").let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val daySet = daysRaw.filter { it in '1'..'7' }.map { it - '0' }.toSet()
            return PairSchedule(hour, minute, daySet)
        }
    }
}

/** One pair with its schedule (null = none set), for the scheduler and the pairs manager. */
data class ScheduledPair(val pair: AppPair, val schedule: PairSchedule?)

/** The DataStore key prefix of a pair's schedule ("pairsched:<pairId>"). */
internal const val PAIR_SCHEDULE_PREFIX = "pairsched:"

/** The DataStore key of a pair's schedule ("pairsched:<pairId>"). */
fun pairScheduleKey(pairId: String) = "$PAIR_SCHEDULE_PREFIX$pairId"

/**
 * The next epoch-millisecond at which [schedule] fires, strictly after [now]. Days empty = daily;
 * day digits 1..7 are Monday..Sunday (Calendar's week starts on Sunday, hence the mapping).
 */
fun PairSchedule.nextFireAfter(now: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
    if (days.isNotEmpty()) {
        val wanted = days.map { if (it == 7) Calendar.SUNDAY else it + 1 }
        while (cal.get(Calendar.DAY_OF_WEEK) !in wanted) cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}
