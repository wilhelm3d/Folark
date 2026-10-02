package org.arkikeskus.launcher.data

import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppRepository @Inject constructor(
    private val source: LauncherAppsSource,
    private val settingsRepository: SettingsRepository,
    private val appUsageRepository: AppUsageRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * All launchable apps, sorted by (effective) label, updating on install/remove/change. Any
     * user-chosen custom label (see [SettingsRepository.customLabels]) is applied over the system
     * label so it shows everywhere apps are rendered, and re-sorted to stay alphabetical.
     *
     * Shared (Eagerly, replay 1): the upstream is a cold callbackFlow that registers a
     * LauncherApps.Callback and scans every profile's activities per collector — and this flow has
     * 3–4 permanent collectors (home, the two drawer combines, the notifications widget) plus a
     * `.first()` per search keystroke. Unshared, ONE package update on the device triggered 3–4
     * duplicate Binder scans and every search keystroke re-registered + re-scanned the world.
     * Eagerly (not WhileSubscribed) keeps the single callback registered for the process lifetime —
     * a HOME process — so package events (and their icon-epoch bumps) are never missed while
     * collectors are briefly detached.
     */
    val apps: Flow<List<AppItem>> = combine(source.appsFlow(), settingsRepository.customLabels) { apps, labels ->
        if (labels.isEmpty()) {
            apps
        } else {
            apps
                .map { a ->
                    val custom = labels[a.key]
                        ?: labels[a.componentName.flattenToString()]
                        ?: labels[a.componentName.flattenToShortString()]
                        ?: labels["${a.packageName}/${a.className}"]
                    if (custom != null) a.copy(label = custom) else a
                }
                .sortedBy { it.label.lowercase() }
        }
    }.flowOn(Dispatchers.Default).shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Persists a custom label for [componentName] in DataStore. */
    fun setCustomAppLabel(componentName: String, label: String) = scope.launch {
        settingsRepository.setCustomAppLabel(componentName, label)
    }

    /** See [LauncherAppsSource.packageEvents]. */
    val packageEvents: Flow<String> get() = source.packageEvents

    /** See [LauncherAppsSource.packageRemovals]. */
    val packageRemovals: Flow<Pair<String, Long>> get() = source.packageRemovals

    /** See [LauncherAppsSource.isAppInstalled] (fail-safe: unknown → true). */
    fun isAppInstalled(packageName: String, userSerial: Long): Boolean =
        source.isAppInstalled(packageName, userSerial)

    /** Invalidates icon/layout caches across the launcher. */
    fun invalidateCaches() = source.invalidateCachesAndReload()

    /** Launches [appItem]; on success, records the launch for the "most used" ranking (fire-and-forget). */
    /** See [LauncherAppsSource.launchClassName]. */
    fun launchClassName(packageName: String, user: UserHandle): String? = source.launchClassName(packageName, user)

    fun launch(appItem: AppItem): Result<Unit> {
        val result = source.launch(appItem)
        if (result.isSuccess) scope.launch { appUsageRepository.recordLaunch(appItem.key) }
        return result
    }

    val appPairs: Flow<List<AppPair>> = settingsRepository.appPairs

    fun saveAppPair(pair: AppPair) = scope.launch { settingsRepository.saveAppPair(pair) }

    fun deleteAppPair(id: String) = scope.launch { settingsRepository.deleteAppPair(id) }

    fun launchAppPair(pair: AppPair): Result<Unit> {
        val result = source.launchAppPair(pair)
        if (result.isSuccess) {
            scope.launch {
                appUsageRepository.recordLaunch(pair.app1Key)
                appUsageRepository.recordLaunch(pair.app2Key)
            }
        }
        return result
    }
}
