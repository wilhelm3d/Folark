package org.arkikeskus.launcher

import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.feature.home.APPWIDGET_HOST_ID
import org.arkikeskus.launcher.feature.home.LocalAppWidgetHost
import org.arkikeskus.launcher.feature.home.LocalOrphanWidgetConfigResult
import org.arkikeskus.launcher.feature.home.LocalWidgetConfigLauncher
import org.arkikeskus.launcher.feature.home.LockAccessibilityService
import org.arkikeskus.launcher.launcher.system.FoldStateMonitor
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.LauncherShell
import org.arkikeskus.launcher.ui.LauncherShellViewModel
import org.arkikeskus.launcher.ui.component.LocalFoldState
import org.arkikeskus.launcher.ui.component.LocalIconEpochs
import org.arkikeskus.launcher.ui.component.LocalScreenType
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    /** Emits on every HOME intent (onNewIntent). The payload is Launcher3's "alreadyOnHome": true
     *  when HOME was pressed while the launcher was already the foreground app (→ snap the workspace
     *  back to the first page), false when the user is coming home from another app (→ keep the page
     *  they launched from; popups/drawer still get dismissed by the collectors). */
    private val homeSignals = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** True between onResume and onStop. Device-verified (Pixel 8a, Android 17): the lifecycle STATE
     *  cannot tell the two HOME cases apart — the system STARTs the launcher during the app-close
     *  transition before delivering the intent, so it reads STARTED both ways. This flag can, because
     *  onResume is guaranteed to run only after onNewIntent, while a foreground HOME press only runs
     *  onPause (the flag stays true until onStop). */
    private var wasForeground = false

    @Inject lateinit var foldStateMonitor: FoldStateMonitor
    @Inject lateinit var settingsRepository: SettingsRepository

    // Use the Activity context (not applicationContext) for the host — Launcher3 does the same; a
    // collection widget's RemoteViewsAdapter and the host's listener callbacks register against this.
    private val appWidgetHost by lazy { AppWidgetHost(this, APPWIDGET_HOST_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initBounds = windowManager.currentWindowMetrics.bounds
        val initMaxDim = maxOf(initBounds.width(), initBounds.height())
        val initIsInner = foldStateMonitor.isInnerDisplay(this)
        Log.d(
            "FolarkFoldDiagnostics",
            "LauncherActivity onCreate: bounds=${initBounds.width()}x${initBounds.height()}, maxDimension=$initMaxDim, isInnerDisplay=$initIsInner"
        )
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

            val bounds = windowManager.currentWindowMetrics.bounds
            val maxDimension = maxOf(bounds.width(), bounds.height())
            val isInner = foldStateMonitor.isInnerDisplay(this)
            Log.d(
                "FolarkFoldDiagnostics",
                "LauncherActivity setContent: bounds=${bounds.width()}x${bounds.height()}, maxDimension=$maxDimension, isInnerDisplay=$isInner, foldState=$foldState, screenType=$screenType"
            )

            var previousFoldState by remember { mutableStateOf(foldState) }
            LaunchedEffect(foldState) {
                val currentBounds = windowManager.currentWindowMetrics.bounds
                val maxDim = maxOf(currentBounds.width(), currentBounds.height())
                val isInnerDisp = foldStateMonitor.isInnerDisplay(this@LauncherActivity)
                Log.d(
                    "FolarkFoldDiagnostics",
                    "LauncherActivity LaunchedEffect(foldState): bounds=${currentBounds.width()}x${currentBounds.height()}, maxDimension=$maxDim, isInnerDisplay=$isInnerDisp, foldState=$foldState, screenType=${foldState.screenType}"
                )
                if (previousFoldState != foldState) {
                    val wasClosed = previousFoldState == FoldState.CLOSED
                    val isNowClosed = foldState == FoldState.CLOSED
                    if (wasClosed && !isNowClosed) {
                        when (settings.unfoldAction) {
                            LauncherSettings.ACTION_SEARCH -> {}
                            LauncherSettings.ACTION_DRAWER -> {}
                        }
                    } else if (!wasClosed && isNowClosed) {
                        when (settings.foldAction) {
                            LauncherSettings.ACTION_PAGE_0 -> {}
                            LauncherSettings.ACTION_LOCK -> {
                                runCatching { LockAccessibilityService.lock() }
                            }
                        }
                    }
                    previousFoldState = foldState
                }
            }

            LaunchedEffect(settings.allowLandscape) {
                requestedOrientation = if (settings.allowLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            LauncherTheme(amoledDark = settings.amoledDark) {
                val iconEpochs by hiltViewModel<LauncherShellViewModel>().iconEpochs.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalAppWidgetHost provides appWidgetHost,
                    LocalWidgetConfigLauncher provides ::startWidgetConfig,
                    LocalOrphanWidgetConfigResult provides orphanConfigResult,
                    LocalIconEpochs provides iconEpochs,
                    LocalScreenType provides foldState.screenType,
                    LocalFoldState provides foldState,
                ) {
                    LauncherShell(
                        homeSignals = homeSignals,
                        onOpenSettings = { startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java)) },
                    )
                }
            }
        }
    }

    /** Pending callback for the in-flight widget configuration activity (see [startWidgetConfig]). */
    private var pendingConfigCallback: ((Boolean) -> Unit)? = null

    /** A config result that arrived with NO live callback: the process was killed while the widget's
     *  configuration activity was in front and the system redelivered the result to a fresh instance.
     *  The home screen consumes it against its process-death-surviving pending-bind state. */
    private val orphanConfigResult = MutableStateFlow<Boolean?>(null)

    /**
     * Launches [appWidgetId]'s configuration activity through the system so the framework marks the
     * widget configured (a raw ACTION_APPWIDGET_CONFIGURE Intent does not). The result arrives in
     * [onActivityResult]. Used by the home screen's add-widget flow via [LocalWidgetConfigLauncher].
     */
    private fun startWidgetConfig(appWidgetId: Int, onResult: (Boolean) -> Unit) {
        pendingConfigCallback = onResult
        val launched = runCatching {
            appWidgetHost.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, WIDGET_CONFIG_REQUEST, null)
        }.isSuccess
        if (!launched) {
            pendingConfigCallback = null
            onResult(false)
        }
    }

    @Deprecated("Required for AppWidgetHost.startAppWidgetConfigureActivityForResult (legacy result API)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WIDGET_CONFIG_REQUEST) {
            val ok = resultCode == RESULT_OK
            val cb = pendingConfigCallback
            pendingConfigCallback = null
            if (cb != null) cb(ok) else orphanConfigResult.value = ok
        }
    }

    override fun onStart() {
        super.onStart()
        // A launcher is the device HOME — a host hiccup must never crash it.
        runCatching { appWidgetHost.startListening() }
    }

    override fun onResume() {
        super.onResume()
        wasForeground = true
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
        wasForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Not hasWindowFocus() like Launcher3 — our Compose popups are separate windows and would
        // steal focus from the activity. See [wasForeground] for why not lifecycle.currentState.
        homeSignals.tryEmit(wasForeground)
    }

    private companion object {
        /** Request code for the system-routed widget configuration activity. */
        const val WIDGET_CONFIG_REQUEST = 0x4357
    }
}