package org.arkikeskus.launcher.data

import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType
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
    private val allApps: Flow<List<AppItem>> = combine(source.appsFlow(), settingsRepository.customLabels.distinctUntilChanged()) { apps, labels ->
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

    /** Every app outside Private Space: what home, the drawer, search, widgets and the taskbar show. */
    val apps: Flow<List<AppItem>> = allApps
        .map { list -> if (list.none { it.privateProfile }) list else list.filterNot { it.privateProfile } }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Private Space apps (none while it is locked), for the drawer's Private section only. */
    val privateApps: Flow<List<AppItem>> = allApps.map { list -> list.filter { it.privateProfile } }

    /** See [LauncherAppsSource.privateSpace]. */
    val privateSpace: StateFlow<PrivateSpaceState?> get() = source.privateSpace

    fun setPrivateSpaceLocked(locked: Boolean): Boolean = source.setPrivateSpaceLocked(locked)

    fun privateSpaceInstallSender() = source.privateSpaceInstallSender()

    /** Opens a Private Space app; unlike [launch] it is not counted for "most used". */
    fun launchPrivate(appItem: AppItem): Result<Unit> = source.launch(appItem, cachedOpenAnimation, cachedAnimationSpeed)

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
        val result = source.launch(appItem, cachedOpenAnimation, cachedAnimationSpeed)
        if (result.isSuccess) scope.launch { appUsageRepository.recordLaunch(appItem.key) }
        return result
    }

    val appPairs: Flow<List<AppPair>> = settingsRepository.appPairs

    fun saveAppPair(pair: AppPair) = scope.launch { settingsRepository.saveAppPair(pair) }

    fun deleteAppPair(id: String) = scope.launch { settingsRepository.deleteAppPair(id) }

    /** Launches both apps of [pair] as a split screen: the first one normally, the second beside it. */
    fun launchAppPair(pair: AppPair): Result<Unit> {
        val result = source.launchAppPair(pair)
        if (result.isSuccess) {
            scope.launch {
                // Every app that opened is counted, in one write at the end.
                val launched = mutableListOf(pair.app1Key)
                delay(SPLIT_SECOND_APP_DELAY_MS)
                if (source.launchAppPairSecond(pair).isSuccess) {
                    launched += pair.app2Key
                    settleIntoSplit(pair.app2ComponentName.packageName)
                }
                if (pair.hasThirdApp) {
                    // The third app floats over the pair once the split has settled.
                    delay(SPLIT_THIRD_APP_DELAY_MS)
                    if (source.launchAppPairThird(pair, floatingWindowSize()).isSuccess) {
                        launched += pair.app3Key
                    }
                }
                appUsageRepository.recordLaunches(launched)
            }
        }
        return result
    }

    /** Opens [first] and, once it is in front, [second] beside it as a split screen. */
    fun launchSplit(first: AppItem, second: AppItem): Result<Unit> {
        val result = source.launch(first)
        if (result.isSuccess) {
            scope.launch {
                delay(SPLIT_SECOND_APP_DELAY_MS)
                if (source.launchAdjacent(second).isSuccess) {
                    appUsageRepository.recordLaunches(listOf(first.key, second.key))
                    settleIntoSplit(second.packageName)
                } else {
                    appUsageRepository.recordLaunch(first.key)
                }
            }
        }
        return result
    }

    /**
     * After an app was opened over the one in front, puts the two in split screen with Android's
     * own split-screen command. The plain "open next to the current app" request is ignored on many
     * phones when it doesn't come from the app in front; the command works from anywhere once the
     * one-time grant was given (see [ShellSplit]). Without it, nothing happens here.
     */
    private suspend fun settleIntoSplit(packageName: String) {
        if (!source.canSplitByCommand()) return
        delay(SPLIT_SETTLE_DELAY_MS)
        withContext(Dispatchers.IO) { source.moveIntoSplit(packageName) }
    }

    /** Opens [appItem] as a split half beside whatever app is in front (the taskbar's drag-to-split). */
    fun launchBeside(appItem: AppItem): Result<Unit> {
        val result = source.launchAdjacent(appItem)
        if (result.isSuccess) {
            scope.launch {
                appUsageRepository.recordLaunch(appItem.key)
                settleIntoSplit(appItem.packageName)
            }
        }
        return result
    }

    /** Opens [appItem] in a floating window (see [LauncherAppsSource.launchFloating]). */
    fun launchFloating(appItem: AppItem): Result<Unit> {
        val result = source.launchFloating(appItem, cachedFloatingSize)
        if (result.isSuccess) scope.launch { appUsageRepository.recordLaunch(appItem.key) }
        return result
    }

    /** The floating-window size setting, kept current so a launch from a tap never waits on storage. */
    @Volatile
    private var cachedFloatingSize: String = org.arkikeskus.launcher.model.LauncherSettings.FLOATING_SIZE_MEDIUM

    /** The app-open animation and animation speed settings, kept current the same way. */
    @Volatile
    private var cachedOpenAnimation: String = org.arkikeskus.launcher.model.LauncherSettings.APP_OPEN_ICON

    @Volatile
    private var cachedAnimationSpeed: Float = 1f

    private fun floatingWindowSize(): String = cachedFloatingSize

    init {
        scope.launch {
            runCatching {
                settingsRepository.settings(ScreenType.OUTER).collect {
                    cachedFloatingSize = it.floatingWindowSize
                    cachedOpenAnimation = it.appOpenAnimation
                    cachedAnimationSpeed = it.animationSpeed
                    // "Theme all icons" changes how icons are drawn: re-render them when it flips.
                    if (source.forceThemedIcons != it.forceThemedIcons || source.themedIconStyle != it.themedIconStyle) {
                        source.forceThemedIcons = it.forceThemedIcons
                        source.themedIconStyle = it.themedIconStyle
                        source.invalidateCachesAndReload()
                    }
                }
            }
        }
    }

    private companion object {
        /** The first app must be the task in front before the "adjacent" launch can pair with it. */
        const val SPLIT_SECOND_APP_DELAY_MS = 300L

        /** How long an opened app gets to come to the front before it is moved into split screen. */
        const val SPLIT_SETTLE_DELAY_MS = 150L

        /** The split needs longer to settle before a floating window is put over it. */
        const val SPLIT_THIRD_APP_DELAY_MS = 800L
    }
}
