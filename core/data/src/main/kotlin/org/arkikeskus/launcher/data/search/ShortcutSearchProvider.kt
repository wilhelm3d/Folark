package org.arkikeskus.launcher.data.search

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import javax.inject.Inject

/**
 * Searches the app shortcuts every installed app publishes ("New tab", "Compose", "Scan code") by
 * label. The system only answers shortcut queries for the default launcher, so this returns nothing
 * until Folark holds the home role. Gated by the search-shortcuts setting; a query needs at least
 * two characters, since one letter matches a large part of every app's shortcut list.
 */
class ShortcutSearchProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : SearchProvider {

    override suspend fun isEnabled(): Boolean =
        settingsRepository.settings(ScreenType.OUTER).first().search.shortcuts

    override suspend fun query(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return@withContext emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) {
            return@withContext emptyList()
        }
        val packageManager = context.packageManager
        val snapshot = shortcutSnapshot(launcherApps)
        val results = ArrayList<SearchResult.Shortcut>()
        for (entry in snapshot.entries) {
            if (!entry.lowerLabel.contains(q)) continue
            val appLabel = snapshot.appLabels.getOrPut(entry.packageName) {
                runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(entry.packageName, 0)).toString()
                }.getOrDefault(entry.packageName)
            }
            results += SearchResult.Shortcut(entry.packageName, entry.id, entry.serial, entry.label, appLabel)
        }
        // Labels that START with the query first, then alphabetically; a handful is plenty.
        results
            .distinctBy { it.id }
            .sortedWith(compareBy({ !it.label.lowercase().startsWith(q) }, { it.label.lowercase() }))
            .take(MAX_RESULTS)
    }

    private class ShortcutEntry(val packageName: String, val id: String, val serial: Long, val label: String, val lowerLabel: String)
    private class ShortcutSnapshot(
        val at: Long,
        val entries: List<ShortcutEntry>,
        val appLabels: java.util.concurrent.ConcurrentHashMap<String, String>,
    )

    @Volatile
    private var snapshot: ShortcutSnapshot? = null

    /**
     * Every enabled shortcut of every profile, read from the system at most once per [CACHE_MS]:
     * a search runs on each typing pause, and the full list is a large system call.
     */
    private fun shortcutSnapshot(launcherApps: LauncherApps): ShortcutSnapshot {
        val now = SystemClock.elapsedRealtime()
        snapshot?.let { if (now - it.at < CACHE_MS) return it }
        val userManager = context.getSystemService(UserManager::class.java)
        val profiles = runCatching { userManager?.userProfiles }.getOrNull() ?: listOf(Process.myUserHandle())
        val entries = ArrayList<ShortcutEntry>()
        for (user in profiles) {
            val serial = runCatching { userManager?.getSerialNumberForUser(user) }.getOrNull() ?: 0L
            val shortcuts = runCatching {
                launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(QUERY_FLAGS), user)
            }.getOrNull().orEmpty()
            for (info in shortcuts) {
                if (!info.isEnabled) continue
                val label = (info.longLabel ?: info.shortLabel)?.toString().orEmpty()
                if (label.isBlank()) continue
                entries += ShortcutEntry(info.`package`, info.id, serial, label, label.lowercase())
            }
        }
        return ShortcutSnapshot(now, entries, java.util.concurrent.ConcurrentHashMap()).also { snapshot = it }
    }

    private companion object {
        const val CACHE_MS = 10_000L
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RESULTS = 8
        const val QUERY_FLAGS = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
    }
}
