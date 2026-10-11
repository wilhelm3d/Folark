package org.arkikeskus.launcher

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.ui.theme.LauncherFontFamily
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
        val foldStates = foldStateMonitor.foldStateFlow(this, lifecycleScope)
        setContent {
            val foldState by foldStates.collectAsStateWithLifecycle()
            val screenType = foldState.screenType
            val settingsFlow = remember(screenType) { settingsRepository.settings(screenType) }
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = LauncherSettings())
            // The Studio look is always dark: dark theme and light system-bar icons with it.
            val studio = settings.settingsStyle == LauncherSettings.SETTINGS_STYLE_STUDIO
            LaunchedEffect(studio) {
                if (studio) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    )
                } else {
                    enableEdgeToEdge()
                }
            }
            LauncherTheme(
                darkTheme = studio || isSystemInDarkTheme(),
                amoledDark = settings.amoledDark,
                fontFamily = settings.appFontFamily
                    .takeIf { it != LauncherSettings.FONT_FAMILY_DEFAULT }
                    ?.let { LauncherFontFamily.resolve(it) },
                boldText = settings.appFontBold,
                fontScale = settings.appFontSizeScale,
            ) {
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
