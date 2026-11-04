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
        // Settings ▸ Root as plain values for the taskbar, gestures and accessibility service, and
        // the grants it keeps given back when Android took one away.
        appScope.launch {
            settingsRepository.settings(ScreenType.INNER).collect { s ->
                RootFeatures.update(s)
                // Settings ▸ Haptics, for every place that plays them (some without a view).
                org.arkikeskus.launcher.data.Haptics.settings = s.motion
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
                    runCatching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.RecentsHookState.STATE_URI, null) }
                }
        }
        // The module in System UI keeps the status bar margins (Settings ▸ Motorola): tell it when they change.
        appScope.launch {
            settingsRepository.settings(ScreenType.INNER)
                .map { Triple(it.statusBar.systemPadding, it.statusBar.systemPaddingInner, it.statusBar.systemPaddingCover) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching { contentResolver.notifyChange(org.arkikeskus.launcher.xposed.StatusBarPaddingHook.CHANGED_URI, null) }
                }
        }
        // Folark's taskbar in its own overlay windows, when "Display over other apps" is allowed
        // (otherwise the accessibility service hosts it, if enabled).
        runCatching { org.arkikeskus.launcher.feature.home.TaskbarHost.refresh(this) }
    }
}
