package org.arkikeskus.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.data.root.RootGrants
import org.arkikeskus.launcher.data.root.RootFeatures
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import org.arkikeskus.launcher.model.catching

@HiltAndroidApp
class LauncherApplication : Application(), SingletonImageLoader.Factory {

    /** Hilt-provided ImageLoader with the app-icon fetcher/keyer (see DataModule). */
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var appUsageRepository: AppUsageRepository

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // The local crash log (Settings ▸ System ▸ Crash log): every uncaught exception lands in a
        // small on-device file, so a crash never needs adb to be read.
        org.arkikeskus.launcher.data.CrashLog.install(this)
        // Which Folark screen is on screen, for scheduled pairs (open now, or notify instead).
        org.arkikeskus.launcher.pairs.FolarkOnScreen.install(this)

        // F7: app launches are written in one batch after a burst; write the rest when the
        // launcher leaves the screen so a launch is never lost with the process.
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = appUsageRepository.flush()
            // UI_HIDDEN when Folark leaves the screen, BACKGROUND (and up) when it may be stopped.
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) appUsageRepository.flush()
            }
        })

        // FIX #4: share one upstream DataStore flow instead of opening four independent subscriptions.
        // Each settingsRepository.settings() call was a separate DataStore collector, meaning every
        // settings write triggered 4× the deserialisation work. One hot, eagerly-started shared flow
        // serves all collectors from a single DataStore read.
        val innerSettings = settingsRepository.settings(ScreenType.INNER)
            .shareIn(appScope, SharingStarted.Eagerly, replay = 1)

        // Settings ▸ Root as plain values for the taskbar, gestures and accessibility service, and
        // the grants it keeps given back when Android took one away.
        appScope.launch {
            innerSettings.collect { s ->
                RootFeatures.update(s)
                // Settings ▸ Haptics, for every place that plays them (some without a view).
                org.arkikeskus.launcher.data.Haptics.settings = s.motion
                // The module's provider answers from memory (no binder-thread DataStore reads).
                org.arkikeskus.launcher.xposed.HookStateCache.update(s)
                RootGrants.restoreIfNeeded(this@LauncherApplication)
            }
        }
        // The Vector module in the system launcher keeps Folark's answer for Replace Android's
        // Recents and the gesture handle's search: tell it when either changes.
        appScope.launch {
            innerSettings
                .map { (it.recents.enabled && it.recents.replaceSystem) to it.recents.navHandleOpensSearch }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    catching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.state, null) }
                }
        }
        // The module in System UI keeps the status bar margins (Settings ▸ Motorola): tell it when they change.
        appScope.launch {
            innerSettings
                .map { Triple(it.statusBar.systemPadding, it.statusBar.systemPaddingInner, it.statusBar.systemPaddingCover) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    catching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.statusBarPadding, null) }
                }
        }
        // The module's divider stops (Settings ▸ Recents & split screen): tell it when they change.
        appScope.launch {
            innerSettings
                .map { r -> with(r.recents) { listOf(dividerStops, freeDivider, dividerLock, dividerMarks, dividerHaptics, keepSplitSize, peekFlingClose) } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    catching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.dividerStops, null) }
                }
        }
        // Folark's taskbar in its own overlay windows, when "Display over other apps" is allowed
        // (otherwise the accessibility service hosts it, if enabled). Deferred a moment: nothing
        // can sit over the home screen until it is up anyway, LauncherActivity.onResume refreshes
        // the host again, and this keeps a handler thread out of the cold start.
        appScope.launch {
            kotlinx.coroutines.delay(1_000)
            catching { org.arkikeskus.launcher.feature.home.taskbar.TaskbarHost.refresh(this@LauncherApplication) }
        }
        // Split screen: marks while a divider is dragged, the drag hint between panes (S5, S14).
        org.arkikeskus.launcher.feature.home.taskbar.SplitOverlays.start(this, appScope, innerSettings)
        // S1: where a split's divider rests is kept per set of apps (left to right).
        appScope.launch {
            org.arkikeskus.launcher.data.SplitEvents.layouts.collect { (tasks, fractions) ->
                val read = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    org.arkikeskus.launcher.data.ShellSplit.recent(this@LauncherApplication)
                }
                val packages = tasks.map { id -> read?.tasks?.firstOrNull { it.id == id }?.packageName ?: return@collect }
                org.arkikeskus.launcher.data.SplitSizes.remember(this@LauncherApplication, packages, fractions.toList())
            }
        }
        // Scheduled app pairs: any schedule or scene change (and boot/time changes, via the
        // receiver) re-lays the alarms.
        appScope.launch {
            settingsRepository.scheduledPairs.collect {
                org.arkikeskus.launcher.pairs.PairScheduleReceiver.rescheduleAll(this@LauncherApplication)
            }
        }
    }
}
