package org.arkikeskus.launcher

import android.app.Application
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class LauncherApplication : Application(), SingletonImageLoader.Factory {

    /** Hilt-provided ImageLoader with the app-icon fetcher/keyer (see DataModule). */
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // The local crash log (Settings ▸ System ▸ Crash log): every uncaught exception lands in a
        // small on-device file, so a crash never needs adb to be read.
        org.arkikeskus.launcher.data.CrashLog.install(this)
        // Settings ▸ Root as plain values for the taskbar, gestures and accessibility service, and
        // the grants it keeps given back when Android took one away.
        appScope.launch {
            settingsRepository.settings(ScreenType.INNER).collect { s ->
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
            settingsRepository.settings(ScreenType.INNER)
                .map { (it.recents.enabled && it.recents.replaceSystem) to it.recents.navHandleOpensSearch }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.state, null) }
                }
        }
        // The module in System UI keeps the status bar margins (Settings ▸ Motorola): tell it when they change.
        appScope.launch {
            settingsRepository.settings(ScreenType.INNER)
                .map { Triple(it.statusBar.systemPadding, it.statusBar.systemPaddingInner, it.statusBar.systemPaddingCover) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.statusBarPadding, null) }
                }
        }
        // The module's divider stops (Settings ▸ Recents & split screen): tell it when they change.
        appScope.launch {
            settingsRepository.settings(ScreenType.INNER)
                .map { r -> with(r.recents) { listOf(dividerStops, freeDivider, dividerLock, dividerMarks, dividerHaptics, keepSplitSize, peekFlingClose) } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.HookLinks.dividerStops, null) }
                }
        }
        // Folark's taskbar in its own overlay windows, when "Display over other apps" is allowed
        // (otherwise the accessibility service hosts it, if enabled). Deferred a moment: nothing
        // can sit over the home screen until it is up anyway, LauncherActivity.onResume refreshes
        // the host again, and this keeps a handler thread out of the cold start.
        appScope.launch {
            kotlinx.coroutines.delay(1_000)
            runCatching { org.arkikeskus.launcher.feature.home.taskbar.TaskbarHost.refresh(this@LauncherApplication) }
        }
        // Split screen: marks while a divider is dragged, the drag hint between panes (S5, S14).
        org.arkikeskus.launcher.feature.home.taskbar.SplitOverlays.start(this, appScope, settingsRepository.settings(ScreenType.INNER))
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
    }
}
