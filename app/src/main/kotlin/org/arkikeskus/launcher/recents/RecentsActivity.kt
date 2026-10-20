package org.arkikeskus.launcher.recents

import android.graphics.Color
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.theme.LauncherFontFamily

/**
 * Folark's own Recents (Settings ▸ Recents), over the wallpaper: what is open, with split view for
 * up to three apps side by side plus a pop-up, and with root the apps' screenshots and closing one.
 * Opened from the taskbar's Recents button or a home gesture.
 */
@AndroidEntryPoint
class RecentsActivity : ComponentActivity() {

    private val viewModel: RecentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
            // Liquid Glass: Android blurs whatever is behind Recents (home, or the app).
            val blurPx = if (settings.recentsGlass) (settings.recentsGlassBlur * resources.displayMetrics.density).toInt() else 0
            LaunchedEffect(blurPx) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { window.setBackgroundBlurRadius(blurPx) }
            }
            LauncherTheme(
                darkTheme = true,
                amoledDark = settings.amoledDark,
                fontFamily = settings.appFontFamily
                    .takeIf { it != LauncherSettings.FONT_FAMILY_DEFAULT }
                    ?.let { LauncherFontFamily.resolve(it) },
                boldText = settings.appFontBold,
                fontScale = settings.appFontSizeScale,
            ) {
                RecentsScreen(
                    state = state,
                    thumbnails = thumbnails,
                    settings = settings,
                    onOpen = { card -> viewModel.open(card); leave() },
                    onClose = viewModel::close,
                    onCloseAll = { viewModel.closeAll(); leave() },
                    onPopUp = { card -> viewModel.popUp(card); leave() },
                    onUnsplit = { card -> viewModel.unsplit(card); viewModel.open(card); leave() },
                    onOpenTogether = { cards -> viewModel.openTogether(cards, settings.recentsThirdInSplit); leave() },
                    onCombine = { dragged, target, how ->
                        if (how == COMBINE_ATTACHED) {
                            viewModel.openAttached(dragged, target, settings.recentsAttachedSide, settings.recentsAttachedPeek, windowManager.maximumWindowMetrics.bounds)
                        } else {
                            viewModel.openTogether(listOf(target, dragged), settings.recentsThirdInSplit)
                        }
                        leave()
                    },
                    onAppInfo = { card -> viewModel.appInfo(card); leave() },
                    onTogglePin = viewModel::togglePin,
                    onDismiss = ::leave,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Recents is a passing screen: leaving it ends it, so it never waits in the background. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun leave() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
