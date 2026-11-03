package org.arkikeskus.launcher.feature.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MODE_BAR
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MODE_BAR_FIXED
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.RECENT_WINDOW_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.USAGE_OVERLAP_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.hasUsageAccess
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.homeVisible

// The taskbar's recent and running apps: Android's task list or usage history (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/**
 * Reads the recent apps, then shows the bar. [force]: rebuild its windows even if the recent apps
 * are unchanged (what the bar looks like changed); otherwise they are rebuilt only when the recent
 * apps it shows changed. A change of form (bar, handles, nothing) always rebuilds, in [refresh].
 */
internal fun AppTaskbar.loadRecents(rebuild: Boolean, force: Boolean = true) {
    // A forced rebuild still waiting (its read cancelled by this newer call) is carried over.
    pendingForce = pendingForce || force
    val s = current() ?: run { refresh(rebuild); return }
    recentsJob?.cancel()
    // Nothing on screen would show recent apps: no usage query.
    if (!s.enabled || !screenOn || (homeVisible && !s.onHome)) {
        pendingForce = false
        refresh(rebuild)
        return
    }
    recentsJob = scope.launch {
        val next = withContext(Dispatchers.IO) { recentApps(s) }
        val running = runningPackages
        val counts = runningCounts
        val changed = next != recents || (s.runningDots && (running != shownRunning || counts != shownCounts))
        recents = next
        shownRunning = running
        shownCounts = counts
        val barUp = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED
        val forced = pendingForce
        pendingForce = false
        refresh(rebuild = rebuild && (forced || (changed && barUp)))
    }
}

internal fun AppTaskbar.recentApps(s: TaskbarSetup): List<AppItem> {
    if (s.recentCount <= 0) return emptyList()
    val pinnedKeys = s.pinned.map { it.key }.toSet()
    val own = context.packageName
    val myUser = Process.myUserHandle()
    // Open apps from Android's task list (one-time DUMP grant), for the dots and open-apps mode.
    val open = if (s.runningApps || s.runningDots) OpenTasks.list(context) else null
    runningPackages = open?.map { it.packageName }?.toSet().orEmpty()
    // Count concurrently-open windows per package: freeform pop-ups and split tasks each count,
    // while any number of plain full-screen recents of one app counts once (not several stale ones).
    runningCounts = open?.groupBy { it.packageName }?.mapValues { (_, tasks) ->
        val live = tasks.count { it.windowingMode == "freeform" || it.windowingMode == "multi-window" }
        val fullScreen = if (tasks.any { it.windowingMode == null || it.windowingMode == "fullscreen" }) 1 else 0
        (live + fullScreen).coerceAtLeast(1)
    }.orEmpty()
    val packages = if (s.runningApps && open != null) open.map { it.packageName }.distinct()
        else if (hasUsageAccess(context)) usageHistory() else null
    val ordered: List<AppItem> = if (packages != null) {
        packages.mapNotNull { pkg ->
            allApps.firstOrNull { it.packageName == pkg && it.user == myUser }
                ?: allApps.firstOrNull { it.packageName == pkg }
        }
    } else {
        val byKey = allApps.associateBy { it.key }
        s.launchHistory.mapNotNull { byKey[it] }
    }
    return ordered
        .filter { it.packageName != own && it.key !in pinnedKeys }
        .distinctBy { it.key }
        .take(s.recentCount)
}

/**
 * Packages brought to the front in the last hours, newest first (needs Usage access). The whole
 * window is read once; after that only the events since the last read (with a little overlap for
 * events recorded late), so each call is a small system call.
 */
@Synchronized
internal fun AppTaskbar.usageHistory(): List<String>? = runCatching {
    val now = System.currentTimeMillis()
    val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
    val windowStart = now - RECENT_WINDOW_MS
    val from = if (usageReadUntil in windowStart..now) {
        usageReadUntil - USAGE_OVERLAP_MS
    } else {
        lastResumed.clear()
        windowStart
    }
    val events = manager.queryEvents(from, now) ?: return null
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
            event.timeStamp > (lastResumed[event.packageName] ?: 0L)
        ) {
            lastResumed[event.packageName] = event.timeStamp
        }
    }
    usageReadUntil = now
    lastResumed.entries.removeIf { it.value < windowStart }
    lastResumed.entries.sortedByDescending { it.value }.map { it.key }
}.getOrNull()
