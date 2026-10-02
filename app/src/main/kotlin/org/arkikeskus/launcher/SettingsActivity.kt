package org.arkikeskus.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.settings.SettingsScreen
import org.arkikeskus.launcher.launcher.system.FoldStateMonitor
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.LocalFoldState
import org.arkikeskus.launcher.ui.component.LocalScreenType

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject lateinit var foldStateMonitor: FoldStateMonitor
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching {
            val lp = window.attributes
            runCatching {
                val fieldMax = lp.javaClass.getField("preferredMaxDisplayRefreshRate")
                fieldMax.setFloat(lp, 165f)
            }
            runCatching {
                val fieldMin = lp.javaClass.getField("preferredMinDisplayRefreshRate")
                fieldMin.setFloat(lp, 120f)
            }
            window.attributes = lp
        }
        runCatching {
            window.setSustainedPerformanceMode(true)
        }
        setContent {
            val foldState by foldStateMonitor.foldStateFlow(this, lifecycleScope).collectAsStateWithLifecycle()
            val screenType = foldState.screenType
            val settings by settingsRepository.settings(screenType).collectAsStateWithLifecycle(initialValue = LauncherSettings())
            LauncherTheme(amoledDark = settings.amoledDark) {
                CompositionLocalProvider(
                    LocalScreenType provides screenType,
                    LocalFoldState provides foldState,
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}
