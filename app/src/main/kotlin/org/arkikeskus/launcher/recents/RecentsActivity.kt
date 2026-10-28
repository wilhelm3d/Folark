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
            val mutedApps by viewModel.mutedApps.collectAsStateWithLifecycle()
            // Liquid Glass: Android blurs whatever is behind Recents (home, or the app).
            val blurPx = if (settings.recents.glass) (settings.recents.glassBlur * resources.displayMetrics.density).toInt() else 0
            LaunchedEffect(blurPx) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { window.setBackgroundBlurRadius(blurPx) }
            }
            LauncherTheme(
                darkTheme = true,
                amoledDark = settings.appearance.amoledDark,
                fontFamily = settings.appearance.fontFamily
                    .takeIf { it != LauncherSettings.FONT_FAMILY_DEFAULT }
                    ?.let { LauncherFontFamily.resolve(it) },
                boldText = settings.appearance.fontBold,
                fontScale = settings.appearance.fontSizeScale,
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
                    onOpenTogether = { cards ->
                        viewModel.openTogether(cards, settings.recents.thirdInSplit, settings.launch.threeAppSplit)
                        leave()
                    },
                    // "Split 3 apps" from a card's menu: the third always joins the split.
                    onOpenThree = { cards -> viewModel.openTogether(cards, thirdInSplit = true, threeApp = settings.launch.threeAppSplit); leave() },
                    onPeek = { main, peek ->
                        viewModel.openAttached(main, peek, settings.recents.attachedSide, settings.recents.attachedPeek, windowManager.maximumWindowMetrics.bounds)
                        leave()
                    },
                    onPeekSplit = { card -> viewModel.peekSplit(card, settings.recents.attachedSide, windowManager.maximumWindowMetrics.bounds, settings.recents.attachedPeek); leave() },
                    onCombine = { dragged, target, how ->
                        if (how == COMBINE_THIRD) {
                            viewModel.addThird(target, dragged)
                        } else if (how.startsWith(COMBINE_REPLACE)) {
                            viewModel.replacePane(target, how.substringAfter(':', "").toIntOrNull() ?: 0, dragged)
                        } else if (how == COMBINE_ATTACHED) {
                            viewModel.openAttached(dragged, target, settings.recents.attachedSide, settings.recents.attachedPeek, windowManager.maximumWindowMetrics.bounds)
                        } else {
                            viewModel.openTogether(listOf(target, dragged), settings.recents.thirdInSplit, settings.launch.threeAppSplit)
                        }
                        leave()
                    },
                    onAppInfo = { card -> viewModel.appInfo(card); leave() },
                    onTogglePin = viewModel::togglePin,
                    onPlaceAtEdge = { card, edge -> viewModel.placeAtEdge(card, edge); leave() },
                    isMuted = { card -> card.tasks.any { it.packageName in mutedApps } },
                    onToggleMute = { card -> viewModel.toggleMute(card) },
                    onNativeSplit = { card -> viewModel.nativeSplitSelect(card); leave() },
                    onNativeThird = { card -> viewModel.nativeThreeAppSelect(card); leave() },
                    onSavePair = { card ->
                        val saved = viewModel.saveAsPair(card)
                        android.widget.Toast.makeText(
                            this,
                            if (saved) org.arkikeskus.launcher.R.string.recents_pair_saved else org.arkikeskus.launcher.R.string.recents_pair_not_saved,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onDismiss = ::leave,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Opened again while open: a Recents button or key pressed a second time closes it. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val toggle = intent.getBooleanExtra(org.arkikeskus.launcher.xposed.RecentsHook.EXTRA_TOGGLE, false)
        if (toggle && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) leave()
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
