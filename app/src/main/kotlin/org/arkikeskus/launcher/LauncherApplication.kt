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
                RootGrants.restoreIfNeeded(this@LauncherApplication)
            }
        }
    }
}
