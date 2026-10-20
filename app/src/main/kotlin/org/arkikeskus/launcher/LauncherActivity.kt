package org.arkikeskus.launcher

import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ClipboardHistoryRepository
import org.arkikeskus.launcher.data.backup.BackupRepository
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.ui.theme.LauncherFontFamily
import org.arkikeskus.launcher.feature.home.APPWIDGET_HOST_ID
import org.arkikeskus.launcher.feature.home.LocalAppWidgetHost
import org.arkikeskus.launcher.feature.home.LocalOrphanWidgetConfigResult
import org.arkikeskus.launcher.feature.home.LocalWidgetConfigLauncher
import org.arkikeskus.launcher.feature.home.LockAccessibilityService
import org.arkikeskus.launcher.launcher.system.FoldStateMonitor
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.LauncherShell
import org.arkikeskus.launcher.ui.HingeAngleSource
import org.arkikeskus.launcher.ui.followHinge
import org.arkikeskus.launcher.ui.component.liveHingeEffect
import org.arkikeskus.launcher.ui.component.liveHingeTimedMs
import org.arkikeskus.launcher.ui.LauncherShellViewModel
import org.arkikeskus.launcher.ui.LocalPopupTheme
import org.arkikeskus.launcher.ui.component.BadgeStyle
import org.arkikeskus.launcher.ui.component.rememberSystemPowerSave
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalBadgeStyle
import org.arkikeskus.launcher.ui.component.LocalAppLaunchZoom
import org.arkikeskus.launcher.ui.component.LocalAquamorphicTouch
import org.arkikeskus.launcher.ui.component.LocalCustomIcons
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
    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var clipboardHistory: ClipboardHistoryRepository

    /** The restore count this screen was built with; a restore made in Settings reloads it. */
    private var seenRestore = 0

    // Use the Activity context (not applicationContext) for the host — Launcher3 does the same; a
    // collection widget's RemoteViewsAdapter and the host's listener callbacks register against this.
    private val appWidgetHost by lazy { AppWidgetHost(this, APPWIDGET_HOST_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        seenRestore = backupRepository.restoreGeneration.value
        enableEdgeToEdge()
        requestHighRefreshRate()
        // One fold-state flow for the activity's lifetime. Building it inside setContent created a
        // new shared flow (and a new coroutine in lifecycleScope) on every recomposition.
        val foldStates = foldStateMonitor.foldStateFlow(this, lifecycleScope)
        registerLockReceiver()
        setContent {
            val foldState by foldStates.collectAsStateWithLifecycle()
            val screenType = foldState.screenType
            // Each display reads its own profile; remembered so a recomposition doesn't resubscribe.
            val settingsFlow = remember(screenType) { settingsRepository.settings(screenType) }
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = LauncherSettings())
            // Folark's battery saver: on, off, or together with the system's own.
            val systemPowerSave = rememberSystemPowerSave()
            val batterySaver = when (settings.batterySaverMode) {
                LauncherSettings.BATTERY_SAVER_ON -> true
                LauncherSettings.BATTERY_SAVER_OFF -> false
                else -> systemPowerSave
            }
            SideEffect {
                if (batterySaverActive != batterySaver) {
                    batterySaverActive = batterySaver
                    // Ask again: the cap on the refresh rate just changed.
                    requestedRefreshRate = 0f
                    applyRefreshRate(idle = true)
                }
            }

            // Live hinge (Inner display ▸ Fold transition): the hinge sensor is read only while
            // home is started and that transition is chosen for the inner display.
            val innerSettingsFlow = remember { settingsRepository.settings(ScreenType.INNER) }
            val innerSettings by innerSettingsFlow.collectAsStateWithLifecycle(initialValue = LauncherSettings())
            val liveHinge = innerSettings.foldTransitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE && hinge.available
            LifecycleStartEffect(liveHinge) {
                if (liveHinge) hinge.start()
                onStopOrDispose { hinge.stop() }
            }

            var previousFoldState by remember { mutableStateOf(foldState) }
            // Plays the fold transition of the display that was just switched TO.
            val foldTransition = remember { Animatable(1f) }
            LaunchedEffect(foldState) {
                val previous = previousFoldState
                previousFoldState = foldState
                if (previous.screenType == foldState.screenType) return@LaunchedEffect
                // Read the target display's profile straight from the store: the `settings` state
                // above may still hold the profile of the display that was just left.
                val target = settingsRepository.settings(foldState.screenType).first()
                if (foldState.screenType == ScreenType.INNER) {
                    when (target.unfoldAction) {
                        LauncherSettings.ACTION_SEARCH, LauncherSettings.ACTION_DRAWER ->
                            shellCommands.tryEmit(target.unfoldAction)
                        else -> Unit
                    }
                } else {
                    when (target.foldAction) {
                        // "Already on home" semantics: close the drawer and snap to the home page.
                        LauncherSettings.ACTION_PAGE_0 -> homeSignals.tryEmit(true)
                        LauncherSettings.ACTION_LOCK -> runCatching { LockAccessibilityService.lock() }
                        else -> Unit
                    }
                }
            }

            // Keyed on the display, not the posture: a flat -> half-opened change must not cancel
            // a running transition and strand the launcher half transparent. A second display
            // switch restarts the effect, which again ends fully visible.
            val skipFirstTransition = remember { booleanArrayOf(true) }
            LaunchedEffect(screenType) {
                if (skipFirstTransition[0]) {
                    skipFirstTransition[0] = false
                    return@LaunchedEffect
                }
                foldTransition.snapTo(0f)
                // The display just switched to: its transition and how fast it plays.
                val target = settingsRepository.settings(screenType).first()
                val speed = target.foldTransitionSpeed
                if (screenType == ScreenType.INNER && liveHinge) {
                    // Follows the hinge while it moves, never faster than the display's speed, then
                    // plays what is left at that speed (fully open, or resting half open).
                    val effectMs = liveHingeTimedMs(speed)
                    followHinge(
                        hinge,
                        foldTransition,
                        innerSettings.liveHingeEndAngle.toFloat(),
                        innerSettings.liveHingeSmoothingMs,
                        maxPerMs = 1f / effectMs,
                    )
                    val restMs = ((1f - foldTransition.value) * effectMs).toInt().coerceAtLeast(MIN_FINISH_MS)
                    foldTransition.animateTo(1f, tween(durationMillis = restMs, easing = LinearOutSlowInEasing))
                } else if (screenType == ScreenType.OUTER && target.foldTransitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE) {
                    // Closed: the outer display plays its hinge effect on a timer (the hinge has stopped).
                    foldTransition.animateTo(1f, tween(liveHingeTimedMs(speed), easing = FastOutSlowInEasing))
                } else {
                    val ms = (CLASSIC_TRANSITION_MS / speed.coerceAtLeast(0.1f)).toInt().coerceIn(120, 4_200)
                    foldTransition.animateTo(1f, tween(durationMillis = ms, easing = FastOutSlowInEasing))
                }
            }

            // Unlocking straight to home plays the display's transition too (Settings ▸ each
            // display ▸ Also when unlocking): hidden while the phone is locked, revealed with the
            // effect at the display's speed once it is unlocked.
            val currentScreen by rememberUpdatedState(screenType)
            LaunchedEffect(Unit) {
                unlockSignals.collect { signal ->
                    val target = settingsRepository.settings(currentScreen).first()
                    val style = target.foldTransitionStyle
                    if (!target.unlockTransition || signal == UNLOCK_SHOW) {
                        foldTransition.snapTo(1f)
                        return@collect
                    }
                    if (signal == UNLOCK_HIDE) {
                        foldTransition.snapTo(0f)
                        return@collect
                    }
                    foldTransition.snapTo(0f)
                    val speed = target.foldTransitionSpeed
                    if (style == LauncherSettings.TRANSITION_LIVE_HINGE) {
                        // The hinge isn't moving: the live effect plays on a timer, as on the cover screen.
                        foldTransition.animateTo(1f, tween(liveHingeTimedMs(speed), easing = FastOutSlowInEasing))
                    } else {
                        val ms = (CLASSIC_TRANSITION_MS / speed.coerceAtLeast(0.1f)).toInt().coerceIn(120, 4_200)
                        foldTransition.animateTo(1f, tween(durationMillis = ms, easing = FastOutSlowInEasing))
                    }
                }
            }

            LaunchedEffect(settings.allowLandscape) {
                requestedOrientation = if (settings.allowLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            LauncherTheme(
                amoledDark = settings.amoledDark,
                fontFamily = settings.appFontFamily
                    .takeIf { it != LauncherSettings.FONT_FAMILY_DEFAULT }
                    ?.let { LauncherFontFamily.resolve(it) },
                boldText = settings.appFontBold,
                fontScale = settings.appFontSizeScale,
            ) {
                val iconEpochs by hiltViewModel<LauncherShellViewModel>().iconEpochs.collectAsStateWithLifecycle()
                val customIcons by hiltViewModel<LauncherShellViewModel>().customIcons.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalAppWidgetHost provides appWidgetHost,
                    LocalWidgetConfigLauncher provides ::startWidgetConfig,
                    LocalOrphanWidgetConfigResult provides orphanConfigResult,
                    LocalIconEpochs provides iconEpochs,
                    LocalCustomIcons provides customIcons,
                    LocalPopupTheme provides settings.popupTheme,
                    LocalBadgeStyle provides BadgeStyle(settings.dotShape, settings.dotColor, settings.dotPosition),
                    LocalBatterySaver provides batterySaver,
                    LocalScreenType provides foldState.screenType,
                    LocalFoldState provides foldState,
                    LocalAquamorphicTouch provides settings.aquamorphicTouchEnabled,
                    LocalAppLaunchZoom provides settings.appLaunchZoomEnabled,
                ) {
                    val transitionStyle = settings.foldTransitionStyle
                    val liveBook = liveHinge && screenType == ScreenType.INNER &&
                        transitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE
                    // The outer display's hinge effect, on a timer after closing (no sensor needed).
                    val outerEffect = screenType == ScreenType.OUTER &&
                        transitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE
                    LauncherShell(
                        homeSignals = homeSignals,
                        shellCommands = shellCommands,
                        onOpenSettings = { startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java)) },
                        modifier = Modifier.then(
                            if (liveBook) {
                                Modifier.liveHingeEffect(
                                    progress = { foldTransition.value },
                                    style = innerSettings.liveHingeStyle,
                                    blurDp = innerSettings.liveHingeBlurDp.toFloat(),
                                    frost = innerSettings.liveHingeFrost,
                                )
                            } else if (outerEffect) {
                                Modifier.liveHingeEffect(
                                    progress = { foldTransition.value },
                                    style = innerSettings.liveHingeOuterStyle,
                                    blurDp = innerSettings.liveHingeBlurDp.toFloat(),
                                    frost = innerSettings.liveHingeFrost,
                                )
                            } else {
                                Modifier
                            },
                        ).graphicsLayer {
                            val t = foldTransition.value
                            if (t >= 1f || liveBook || outerEffect) return@graphicsLayer
                            alpha = t
                            when (transitionStyle) {
                                LauncherSettings.TRANSITION_CROSSFADE -> Unit
                                LauncherSettings.TRANSITION_SLIDE -> translationX = (1f - t) * size.width * 0.12f
                                LauncherSettings.TRANSITION_SCALE -> {
                                    scaleX = 0.88f + 0.12f * t
                                    scaleY = 0.88f + 0.12f * t
                                }
                                LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE -> {
                                    scaleX = 1.08f - 0.08f * t
                                    scaleY = 1.08f - 0.08f * t
                                }
                                // Live hinge folding shut (or without a sensor): the timed book sweep.
                                LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP, LauncherSettings.TRANSITION_LIVE_HINGE -> {
                                    cameraDistance = 16f * density
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = (1f - t) * -18f
                                }
                                else -> {
                                    scaleX = 0.95f + 0.05f * t
                                    scaleY = 0.95f + 0.05f * t
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    /** The classic transitions' length at speed 1 (ms), and the shortest finish of a live one. */
    private val CLASSIC_TRANSITION_MS = 420f
    private val MIN_FINISH_MS = 60

    /** The hinge angle, for the live-hinge fold transition (read only while it is chosen). */
    private val hinge by lazy { HingeAngleSource(this) }

    /** One-shot commands for the shell (currently: open the drawer after an unfold). */
    private val shellCommands = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dropToIdleRefreshRate = Runnable { applyRefreshRate(idle = true) }

    /** The rate last asked of the window; 0 = nothing asked yet (or the display changed). */
    private var requestedRefreshRate = 0f

    /** Android's top refresh rate setting, read each time home comes back; unknown = no cap. */
    private var peakRefreshCap = Float.POSITIVE_INFINITY

    /** Mirrors [LocalBatterySaver] for the refresh-rate code, which runs outside composition. */
    private var batterySaverActive = false

    /**
     * Asks the window for the panel's fastest refresh rate — or, when [idle], for the slowest one
     * that is still at least 60 Hz, so a home screen nobody is touching does not keep the panel at
     * its top rate. Read from the display in use each time rather than hard-coded: the cover and
     * the inner display of a foldable usually top out at different rates.
     */
    private fun applyRefreshRate(idle: Boolean) {
        runCatching {
            val all = display?.supportedModes?.map { it.refreshRate }?.distinct().orEmpty()
            // Never above Android's own top rate (Settings ▸ Smooth display): such an ask is ignored
            // at best, and each change of ask is a window relayout.
            val rates = all.filter { it <= peakRefreshCap + 1f }.ifEmpty { all }
            val top = rates.maxOrNull() ?: return
            // Battery saver: never above the idle rate, even while a finger is down.
            val fastest = if (batterySaverActive) rates.filter { it <= IDLE_REFRESH_RATE + 1f }.maxOrNull() ?: top else top
            val target = if (idle) rates.filter { it >= IDLE_REFRESH_RATE - 1f }.minOrNull() ?: fastest else fastest
            if (target == requestedRefreshRate) return
            requestedRefreshRate = target
            window.attributes = window.attributes.apply { preferredRefreshRate = target }
        }
    }

    /** Full rate now (a touch, a fold, coming home), back to the idle rate after a quiet moment. */
    private fun requestHighRefreshRate() {
        refreshHandler.removeCallbacks(dropToIdleRefreshRate)
        applyRefreshRate(idle = false)
        refreshHandler.postDelayed(dropToIdleRefreshRate, REFRESH_IDLE_AFTER_MS)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // Stay fast for as long as a finger is down; the countdown starts on release.
                refreshHandler.removeCallbacks(dropToIdleRefreshRate)
                applyRefreshRate(idle = false)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    // Before the tap reaches the icon: an app opened by it grows out of this spot.
                    org.arkikeskus.launcher.data.LaunchOrigin.touch(
                        window.decorView,
                        ev.rawX,
                        ev.rawY,
                        (LAUNCH_ORIGIN_DP * resources.displayMetrics.density).toInt(),
                    )
                }
                refreshHandler.removeCallbacks(dropToIdleRefreshRate)
                refreshHandler.postDelayed(dropToIdleRefreshRate, REFRESH_IDLE_AFTER_MS)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A fold/unfold arrives here (the activity is not recreated) and moves the window to a
        // display with other modes: forget what was asked of the old one and ask again.
        requestedRefreshRate = 0f
        requestHighRefreshRate()
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
        LockAccessibilityService.setHomeVisible(true)
        keepAccessibilityServiceOn()
        // Back from Settings after a restore: rebuild the home screen from the restored layout.
        val restore = backupRepository.restoreGeneration.value
        if (restore != seenRestore) {
            seenRestore = restore
            recreate()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) revealAfterLock()
        // Android lets only the focused app read the clipboard: home just came into view, so pick
        // up what was copied meanwhile (only while a clipboard widget is on home).
        if (hasFocus) clipboardHistory.capture()
    }

    override fun onResume() {
        super.onResume()
        wasForeground = true
        // Settings ▸ Root ▸ keep: give back a grant Android took away (cheap unless one is missing).
        org.arkikeskus.launcher.data.root.RootGrants.restoreIfNeeded(this)
        val cap = org.arkikeskus.launcher.data.SystemControls.peakRefreshRate(this)
            ?.takeIf { it > 0f && it.isFinite() } ?: Float.POSITIVE_INFINITY
        if (cap != peakRefreshCap) {
            peakRefreshCap = cap
            requestedRefreshRate = 0f
        }
        // Coming home plays an animation without any touch on the launcher.
        requestHighRefreshRate()
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
        LockAccessibilityService.setHomeVisible(false)
        wasForeground = false
        refreshHandler.removeCallbacks(dropToIdleRefreshRate)
        // Once a day, when automatic backups are on (Settings ▸ Backup & restore).
        backupRepository.autoBackupSoon()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Not hasWindowFocus() like Launcher3 — our Compose popups are separate windows and would
        // steal focus from the activity. See [wasForeground] for why not lifecycle.currentState.
        homeSignals.tryEmit(wasForeground)
    }

    // ---------------------------------------------------------------- unlock transition

    /** Hide / play / show signals for the unlock transition (see setContent). */
    private val unlockSignals = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Home was hidden when the screen went off and waits to be revealed. */
    private var hiddenForLock = false
    private var screenOnAt = 0L

    private val lockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                // Locked: home is hidden behind the lock screen, ready to be revealed.
                Intent.ACTION_SCREEN_OFF -> {
                    hiddenForLock = true
                    unlockSignals.tryEmit(UNLOCK_HIDE)
                }
                Intent.ACTION_SCREEN_ON -> screenOnAt = android.os.SystemClock.elapsedRealtime()
                Intent.ACTION_USER_PRESENT -> if (hasWindowFocus()) revealAfterLock()
            }
        }
    }

    /**
     * Home in front with the phone unlocked after the screen was off: play the transition when this
     * follows the unlock, or just show home when the phone was unlocked into another app earlier.
     */
    private fun revealAfterLock() {
        if (!hiddenForLock) return
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return
        hiddenForLock = false
        val sinceScreenOn = android.os.SystemClock.elapsedRealtime() - screenOnAt
        unlockSignals.tryEmit(if (sinceScreenOn < UNLOCK_WINDOW_MS) UNLOCK_PLAY else UNLOCK_SHOW)
    }

    private fun registerLockReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching { registerReceiver(lockReceiver, filter, RECEIVER_NOT_EXPORTED) }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(lockReceiver) }
        super.onDestroy()
    }

    /** If Android switched Folark's accessibility service off, turn it back on (System controls). */
    private fun keepAccessibilityServiceOn() {
        val service = android.content.ComponentName(this, LockAccessibilityService::class.java)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            org.arkikeskus.launcher.data.SystemControls.keepServiceOn(applicationContext, service)
        }
    }

    private companion object {
        const val UNLOCK_HIDE = 0
        const val UNLOCK_PLAY = 1
        const val UNLOCK_SHOW = 2

        /** Unlocked this soon after the screen came on: the unlock just happened, so it plays. */
        const val UNLOCK_WINDOW_MS = 20_000L

        /** Request code for the system-routed widget configuration activity. */
        const val WIDGET_CONFIG_REQUEST = 0x4357

        /** The rate an untouched home screen settles at, and how long after the last touch. */
        const val IDLE_REFRESH_RATE = 60f
        const val REFRESH_IDLE_AFTER_MS = 3_000L

        /** The size of the spot an app grows out of: about an icon. */
        const val LAUNCH_ORIGIN_DP = 56
    }
}