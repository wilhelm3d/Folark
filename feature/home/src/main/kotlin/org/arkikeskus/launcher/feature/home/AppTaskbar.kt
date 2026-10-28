package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.model.AppPair
import android.annotation.SuppressLint
import org.arkikeskus.launcher.data.LaunchOrigin
import org.arkikeskus.launcher.data.IconShapes
import android.view.DragEvent
import android.os.UserHandle
import android.os.Bundle
import android.graphics.RectF
import android.content.pm.ShortcutInfo
import android.content.pm.LauncherApps
import android.content.ComponentName
import android.content.ClipDescription
import android.content.ClipData
import android.app.PendingIntent
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.FolarkRecents
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.DEFAULT_NUDGE_COLOR
import kotlin.math.abs

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppTaskbarEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun appRepository(): AppRepository
    fun appUsageRepository(): AppUsageRepository
    fun launcherAppsSource(): LauncherAppsSource
    fun notificationBadgeRepository(): NotificationBadgeRepository
}

/** Everything the bar draws from, in one value so a change rebuilds it once. */
private data class TaskbarSetup(
    val enabled: Boolean,
    val pinned: List<AppItem>,
    val recentCount: Int,
    val iconSizeDp: Int,
    val spacingDp: Int,
    val bottomDp: Int,
    val clearInset: Boolean,
    val theme: String,
    val opacity: Float,
    val glassBlur: Int,
    val handles: String,
    val handleHint: Boolean,
    val handleStyle: String,
    val hideButton: Boolean,
    val taskbarAnim: String,
    val onHome: Boolean,
    val keepOpen: Boolean,
    val themedIcons: Boolean,
    val iconPack: String,
    val customIcons: Map<String, String>,
    val iconShape: String,
    val launchHistory: List<String>,
    val mediaEnabled: Boolean,
    val mediaShowPaused: Boolean,
    val mediaPosition: String,
    val mediaTap: String,
    val mediaControls: Boolean,
    val mediaShape: String,
    val mediaBadge: Boolean,
    val systemDrag: Boolean,
    val edgeSnap: Boolean,
    val recentsButton: String,
    val runningApps: Boolean,
    val runningDots: Boolean,
    val multiDots: Boolean,
    val quickSwitch: Boolean,
    val splitControls: Boolean,
    val splitSide: String,
    val threeApp: Boolean,
    val hideFullScreen: Boolean,
    val hiddenApps: Set<String>,
    val replaceRecents: Boolean,
    val alwaysApps: Set<String>,
    val autoHideApps: Set<String>,
    val miniApps: Set<String>,
    val liveEnabled: Boolean,
    val liveEffect: String,
    val liveExpand: Boolean,
    /** Leave out what Android shows as a status-bar chip (calls, Live updates). */
    val liveSkipChips: Boolean,
    val nudgeEnabled: Boolean,
    val nudgeEffect: String,
    val nudgeSoftness: Float,
    /** Saved app pairs / trios pinned on the bar (ids from settings, then the pairs themselves). */
    val pairIds: List<String>,
    val pairs: List<AppPair> = emptyList(),
)

/**
 * Folark's taskbar on the unfolded display: your pinned apps, a divider, and the apps you used last.
 *
 *  - On Folark's home screen and app drawer it stays on screen (Settings ▸ Taskbar).
 *  - Over other apps, including full-screen apps and videos, it opens with a swipe up from either
 *    bottom corner (two invisible touch zones), or stays open if the user chose that.
 *  - Tap an icon to open the app. Drag it up and to the right for split screen, up and to the left
 *    for a floating window.
 *
 * It lives in accessibility overlay windows owned by [LockAccessibilityService], the only kind of
 * window an app may keep above other apps, full-screen ones included, without the "display over
 * other apps" permission. It draws its own views and starts apps; it reads nothing from the screen.
 * Recent apps come from Android's usage history when the user allowed Usage access, otherwise from
 * the apps opened through Folark.
 */
internal class AppTaskbar(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    // Read each time: the cover and inner displays need not share a density.
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun dpf(value: Float): Float = value * context.resources.displayMetrics.density

    private var setup: TaskbarSetup? = null
    private var allApps: List<AppItem> = emptyList()
    private var recents: List<AppItem> = emptyList()
    /** Packages open now (Android's task list), for the taskbar's running dots and open-apps mode. */
    @Volatile private var runningPackages: Set<String> = emptySet()
    /** How many open windows each package has now (split pairs, pop-ups count separately). */
    @Volatile private var runningCounts: Map<String, Int> = emptyMap()
    private var shownRunning: Set<String> = emptySet()
    private var shownCounts: Map<String, Int> = emptyMap()
    private var screenOn = true
    private val shown = mutableListOf<View>()
    private var shownMode = MODE_NONE

    /** An invisible 1-pixel window that hears whether Android's bars are shown: when an app goes
     *  full screen (video, games) they are hidden, and the kept-open bar steps aside. */
    private var probe: View? = null
    private var fullScreen = false
    /** The keyboard is up in the app in front (as far as Android tells this window). */
    private var keyboardUp = false
    private var expanded = false

    /** The hide button was tapped: stay as handles (even for a kept-open bar) until a handle reopens it. */
    private var manuallyHidden = false
    private var recentsJob: Job? = null
    private val collapse = Runnable { expanded = false; mediaControlsOpen = false; refresh() }

    /** What is playing, for the media icon; a change redraws the bar if it is showing. */
    private val media = TaskbarMedia(context) { onMediaChanged() }
    private var mediaControlsOpen = false

    /** The bar last drawn with a media icon: a change to a player it doesn't show changes nothing. */
    private var mediaWasShown = false

    private fun onMediaChanged() {
        val shownNow = mediaShown(setup)
        if (!shownNow) mediaControlsOpen = false
        val barUp = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED
        refresh(rebuild = barUp && (shownNow || mediaWasShown))
        mediaWasShown = shownNow
    }

    private val closeMediaControls = Runnable {
        mediaControlsOpen = false
        refresh(rebuild = true)
    }

    private fun mediaShown(s: TaskbarSetup?): Boolean {
        val playing = media.state ?: return false
        if (s == null || !s.mediaEnabled) return false
        // Hidden with a double tap: stays away until that player plays again (the first moment
        // after the stop is ignored, while the player still reports itself as playing).
        val hidden = mediaHiddenFor
        if (hidden != null) {
            val settling = SystemClock.uptimeMillis() - mediaHiddenAt < MEDIA_HIDE_SETTLE_MS
            if (playing.packageName == hidden && (!playing.playing || settling)) return false
            mediaHiddenFor = null
        }
        return playing.playing || s.mediaShowPaused
    }

    private var mediaHiddenFor: String? = null
    private var mediaHiddenAt = 0L
    private var lastMediaTapAt = 0L
    private val mediaSingleTap = Runnable { toggleMediaCard() }

    /** What is going on now (timers, navigation, downloads), and the pill showing it. */
    private var live: List<LiveActivity> = emptyList()
    /** All of them, before leaving out the ones Android shows as status-bar chips. */
    private var liveAll: List<LiveActivity> = emptyList()

    /** The bar sits over apps, where Android's status bar (and its chips) shows. */
    private fun liveShown(all: List<LiveActivity>): List<LiveActivity> =
        if (setup?.liveSkipChips == true) all.filterNot { it.systemChip } else all
    private var livePill: TaskbarLivePill? = null
    private var liveIndex = 0
    private val SPACE_GAP_DP = 6

    /** The app icons on the bar now, by badgeKey, for the notification nudge. */
    private val iconViews = HashMap<String, View>()

    private var repository: AppRepository? = null
    private var source: LauncherAppsSource? = null
    /** Icons of the apps the bar has shown lately; the least recently shown go first when full. */
    private val iconCache = IconCache(ICON_CACHE_SIZE)
    private var iconStyle = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                // Screen off: nothing is shown, so the player isn't followed either.
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; expanded = false; media.stop() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    setup?.let { if (it.enabled && it.mediaEnabled) media.start() }
                }
            }
            if (screenOn) loadRecents(rebuild = true, force = false) else refresh()
        }
    }

    fun start() {
        val entry = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, AppTaskbarEntryPoint::class.java)
        }.getOrNull() ?: return
        val settingsRepository = entry.settingsRepository()
        val appRepository = entry.appRepository()
        repository = appRepository
        source = entry.launcherAppsSource()
        runCatching {
            context.registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
            )
        }
        scope.launch {
            combine(
                settingsRepository.settings(ScreenType.INNER),
                settingsRepository.dockFavorites(ScreenType.INNER),
                appRepository.apps,
                settingsRepository.customIcons,
                entry.appUsageRepository().usage,
            ) { s, favorites, all, custom, usage ->
                val byKey = all.associateBy { it.key }
                allApps = all
                TaskbarSetup(
                    // Android's taskbar chosen instead (Settings ▸ Taskbar): Folark's stays away.
                    enabled = s.taskbar.enabled && s.taskbar.source == LauncherSettings.TASKBAR_SOURCE_FOLARK,
                    pinned = (s.taskbar.pinnedApps ?: favorites).mapNotNull { byKey[it] }.take(MAX_PINNED),
                    recentCount = s.taskbar.recentCount,
                    iconSizeDp = s.taskbar.iconSize,
                    spacingDp = s.taskbar.iconSpacing,
                    bottomDp = s.taskbar.bottomOffset,
                    clearInset = s.taskbar.clearSystemInset,
                    theme = s.taskbar.theme,
                    opacity = s.taskbar.opacity,
                    glassBlur = s.taskbar.glassBlur,
                    handles = s.taskbar.handles,
                    handleHint = s.taskbar.handleHint,
                    handleStyle = s.taskbar.taskbarHandleStyle,
                    hideButton = s.taskbar.hideButton,
                    taskbarAnim = s.taskbar.taskbarAnim,
                    onHome = s.taskbar.onHome,
                    keepOpen = s.taskbar.keepOpen,
                    themedIcons = s.icons.useThemedIcons,
                    iconPack = s.icons.iconPack,
                    customIcons = custom,
                    iconShape = s.icons.shape,
                    // Apps opened through Folark, newest first: the recent apps without Usage access.
                    launchHistory = usage.entries.sortedByDescending { it.value.lastUsed }.map { it.key }.take(24),
                    mediaEnabled = s.taskbar.mediaEnabled,
                    mediaShowPaused = s.taskbar.mediaShowPaused,
                    mediaPosition = s.taskbar.mediaPosition,
                    mediaTap = s.taskbar.mediaTap,
                    mediaControls = s.taskbar.mediaControls,
                    mediaShape = s.taskbar.mediaShape,
                    mediaBadge = s.taskbar.mediaBadge,
                    systemDrag = s.taskbar.systemDrag,
                    edgeSnap = s.taskbar.edgeSnap,
                    // Folark Recents switched off: the button opens Android's again.
                    recentsButton = s.taskbar.recentsButton.let {
                        if (it == LauncherSettings.TASKBAR_RECENTS_FOLARK && !s.recents.enabled) LauncherSettings.TASKBAR_RECENTS_SYSTEM else it
                    },
                    runningApps = s.taskbar.runningApps,
                    runningDots = s.taskbar.runningDots,
                    multiDots = s.taskbar.multiInstanceDots,
                    quickSwitch = s.taskbar.quickSwitch,
                    splitControls = s.taskbar.splitControls,
                    splitSide = s.taskbar.splitSide,
                    threeApp = s.taskbar.threeApp,
                    hideFullScreen = s.taskbar.hideFullScreen,
                    hiddenApps = s.taskbar.hiddenApps,
                    replaceRecents = s.recents.enabled && s.recents.replaceSystem,
                    alwaysApps = s.taskbar.alwaysApps,
                    autoHideApps = s.taskbar.autoHideApps,
                    miniApps = s.taskbar.miniApps,
                    liveEnabled = s.live.activities && s.live.activitiesTaskbar,
                    liveEffect = s.live.activityEffect,
                    liveExpand = s.live.expandOnLongPress,
                    liveSkipChips = s.live.skipSystemChips,
                    nudgeEnabled = s.icons.nudge && s.icons.nudgeTaskbar,
                    nudgeEffect = s.icons.nudgeEffect,
                    nudgeSoftness = s.icons.nudgeSoftness,
                    pairIds = s.taskbar.pinnedPairs,
                )
            }.combine(appRepository.appPairs) { setup, saved ->
                setup.copy(pairs = setup.pairIds.mapNotNull { id -> saved.firstOrNull { it.id == id } })
            }.distinctUntilChanged().collect { next ->
                val previous = setup
                val style = "${next.themedIcons}|${next.iconPack}|${next.customIcons.hashCode()}|${next.iconShape}"
                if (style != iconStyle) {
                    iconStyle = style
                    iconCache.clear()
                }
                setup = next
                if (previous?.liveSkipChips != next.liveSkipChips) live = liveShown(liveAll)
                // Per-app rules need to know which app is in front: only then is the service told.
                // ...and so does handing Android's Recents over to Folark Recents.
                LockAccessibilityService.replaceSystemRecents = next.replaceRecents
                LockAccessibilityService.watchApps(next.hiddenApps.isNotEmpty() || next.alwaysApps.isNotEmpty() || next.autoHideApps.isNotEmpty() || next.miniApps.isNotEmpty() || next.replaceRecents)
                // The media session is only followed while the media icon is wanted.
                if (next.enabled && next.mediaEnabled) media.start() else media.stop()
                // Only Folark's own launch order moved (every launch): rebuild only if the recent apps changed.
                val onlyHistory = previous != null && previous.copy(launchHistory = next.launchHistory) == next
                loadRecents(rebuild = true, force = !onlyHistory)
            }
        }
        // The keyboard came up over Folark (or went): the bar steps aside and comes back.
        scope.launch {
            TaskbarSpace.keyboardOnHome.drop(1).collect { refresh() }
        }
        // A new notification: its app's icon on the bar nudges (not in battery saver).
        scope.launch {
            entry.notificationBadgeRepository().nudges.collect { n ->
                val s = setup ?: return@collect
                if (!s.nudgeEnabled || (shownMode != MODE_BAR && shownMode != MODE_BAR_FIXED)) return@collect
                if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return@collect
                iconViews[n.badgeKey]?.let { playTaskbarNudge(it, s.nudgeEffect, n.color ?: DEFAULT_NUDGE_COLOR, s.nudgeSoftness) }
            }
        }
        // Live activities: the pill follows them; it appears or goes with a rebuild of the bar.
        scope.launch {
            entry.notificationBadgeRepository().live.collect { all ->
                liveAll = all
                val next = liveShown(all)
                val had = live.isNotEmpty()
                live = next
                val pill = livePill
                if (pill != null && next.isNotEmpty() && had) {
                    pill.activity = next[liveIndex % next.size]
                    pill.extra = next.size - 1
                } else if (had != next.isNotEmpty()) {
                    refresh(rebuild = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED)
                }
            }
        }
        // An app update, a new icon style or icon pack: drawn again.
        scope.launch {
            entry.launcherAppsSource().iconEpochs.drop(1).collect {
                iconCache.clear()
                if (shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED) refresh(rebuild = true)
            }
        }
    }

    fun stop() {
        TaskbarSpace.set(0)
        scope.cancel()
        media.stop()
        handler.removeCallbacksAndMessages(null)
        runCatching { context.unregisterReceiver(screenReceiver) }
        remove()
        ensureProbe(false)
    }

    // ------------------------------------------------------------------ what is on screen

    private fun largeScreen(): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP

    /** Re-evaluates whether the taskbar belongs on screen, and in which form. */
    fun refresh(rebuild: Boolean = false) {
        val s = setup
        // The probe also hears the keyboard, so a kept-open bar never sits on top of it.
        ensureProbe(s != null && s.enabled && (s.keepOpen || s.alwaysApps.isNotEmpty() || s.autoHideApps.isNotEmpty() || s.miniApps.isNotEmpty()) && screenOn && largeScreen())
        val active = s != null && s.enabled && screenOn && largeScreen() && keyguard?.isKeyguardLocked != true &&
            (s.pinned.isNotEmpty() || s.pairs.isNotEmpty() || recents.isNotEmpty() || mediaShown(s) || (s.liveEnabled && live.isNotEmpty()))
        if (!active || s == null) {
            expanded = false
            remove()
            TaskbarSpace.set(0)
            return
        }
        val mode = when {
            // Typing on home or in the drawer: the bar would cover the keyboard's bottom row.
            homeVisible && TaskbarSpace.keyboardOnHome.value -> MODE_NONE
            // The hide button was tapped: show only the pull handles until a handle swipe reopens it.
            manuallyHidden && !expanded -> MODE_HANDLES
            homeVisible && s.onHome -> MODE_BAR_FIXED
            homeVisible -> MODE_NONE
            // Settings ▸ Taskbar ▸ per app: never in these; always (not over the keyboard) in those.
            foreground in s.hiddenApps -> MODE_NONE
            foreground in s.alwaysApps && !keyboardUp -> MODE_BAR_FIXED
            // Just opened from a handle or the mini pill: the full bar (it collapses again by itself).
            expanded -> MODE_BAR
            // Per app: auto-hide (a corner swipe reveals it), or a small tap-to-open mini pill.
            foreground in s.autoHideApps -> MODE_HANDLES
            foreground in s.miniApps && !keyboardUp -> MODE_MINI
            // Full screen (a video, a game): no bar covering it; a corner swipe still opens it.
            s.keepOpen && !(s.hideFullScreen && fullScreen) && !keyboardUp -> MODE_BAR_FIXED
            else -> MODE_HANDLES
        }
        // Only the bar that stays on home takes room there (home and the drawer step aside).
        if (mode != MODE_BAR_FIXED || !homeVisible) TaskbarSpace.set(0)
        if (mode == shownMode && !rebuild) return
        remove()
        when (mode) {
            MODE_BAR, MODE_BAR_FIXED -> showBar(s, fixed = mode == MODE_BAR_FIXED, animateIn = shownMode != MODE_BAR && shownMode != MODE_BAR_FIXED)
            MODE_HANDLES -> showHandles(s)
            MODE_MINI -> showMini(s)
        }
        shownMode = mode
    }

    private fun ensureProbe(wanted: Boolean) {
        if (!wanted) {
            probe?.let { runCatching { windowManager.removeView(it) } }
            probe = null
            fullScreen = false
            keyboardUp = false
            return
        }
        if (probe != null) return
        val view = View(context)
        view.setOnApplyWindowInsetsListener { v, insets ->
            val barsShown = insets.isVisible(WindowInsets.Type.statusBars()) ||
                insets.isVisible(WindowInsets.Type.navigationBars())
            val ime = insets.isVisible(WindowInsets.Type.ime())
            if (fullScreen == barsShown || keyboardUp != ime) {
                fullScreen = !barsShown
                keyboardUp = ime
                handler.post { refresh() }
            }
            v.onApplyWindowInsets(insets)
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) probe = view
    }

    /** The app in front (only followed while per-app rules exist), null when unknown. */
    private var foreground: String? = null

    /** Another app came to the front: re-decides the bar only when a per-app rule is involved. */
    fun onForegroundApp(packageName: String) {
        val s = setup ?: return
        if (packageName == foreground) return
        val before = foreground
        foreground = packageName
        fun ruled(p: String?) = p != null && (p in s.hiddenApps || p in s.alwaysApps || p in s.autoHideApps || p in s.miniApps)
        if (ruled(before) || ruled(packageName)) refresh()
    }

    /** The home screen came to the front or left it. */
    fun onHomeVisibilityChanged() {
        expanded = false
        loadRecents(rebuild = true, force = false)
    }

    private var lastNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** A fold, unfold, rotation or dark/light switch (from the accessibility service). */
    fun onConfigurationChanged() {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val nightChanged = night != lastNightMode
        lastNightMode = night
        // Icons are tinted for dark or light: drawn again.
        if (nightChanged) iconCache.clear()
        loadRecents(rebuild = true, force = nightChanged)
    }

    private fun remove() {
        livePill = null
        iconViews.clear()
        shown.forEach { runCatching { windowManager.removeView(it) } }
        shown.clear()
        shownMode = MODE_NONE
        ghostEnd()
        if (!systemDragging) hideShortcuts()
        barRow = null
        handler.removeCallbacks(collapse)
    }

    /** [watchOutside]: only the opened bar needs to hear touches elsewhere (to close itself). */
    private fun params(gravity: Int, x: Int, y: Int, watchOutside: Boolean = false) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (watchOutside) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0) or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        this.x = x
        this.y = y
    }

    private fun add(view: View, params: WindowManager.LayoutParams) {
        if (runCatching { windowManager.addView(view, params) }.isSuccess) shown += view
    }

    /**
     * The space the system keeps at the bottom of the current display for its gesture handle (gesture
     * navigation) or its navigation bar (3-button), in px. Read fresh from the display's own insets,
     * not from Folark's tiny overlay windows, so it is right on either screen of the fold. 0 if unknown.
     */
    private fun systemBottomInset(): Int = runCatching {
        val insets = windowManager.currentWindowMetrics.windowInsets
        maxOf(
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
            insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
        )
    }.getOrDefault(0)

    /** How far a bottom-anchored overlay is lifted to clear the system's bottom inset (0 when off). */
    private fun TaskbarSetup.insetLift(): Int = if (clearInset) systemBottomInset() else 0

    // ------------------------------------------------------------------ the corner handles

    /**
     * Two invisible touch zones in the bottom corners, clear of the middle where Android's own
     * home gesture starts. A swipe up (or a tap) in either opens the bar.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showHandles(s: TaskbarSetup) {
        val sides = when (s.handles) {
            LauncherSettings.TASKBAR_HANDLES_LEFT -> listOf(Gravity.START)
            LauncherSettings.TASKBAR_HANDLES_RIGHT -> listOf(Gravity.END)
            LauncherSettings.TASKBAR_HANDLES_CENTER -> listOf(Gravity.CENTER_HORIZONTAL)
            else -> listOf(Gravity.START, Gravity.END)
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        sides.forEach { side ->
            val zone = FrameLayout(context).apply {
                contentDescription = context.getString(R.string.taskbar_handle)
                minimumWidth = dp(HANDLE_WIDTH_DP)
                minimumHeight = dp(HANDLE_HEIGHT_DP)
                var downY = 0f
                var opened = false
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downY = event.rawY; opened = false }
                        MotionEvent.ACTION_MOVE -> if (!opened && downY - event.rawY > slop * 2) {
                            opened = true
                            open()
                        }
                        MotionEvent.ACTION_UP -> if (!opened) open()
                    }
                    true
                }
            }
            // A pull hint shows where the swipe starts, in the chosen style (Settings ▸ Taskbar ▸ Pull style).
            if (s.handleHint) {
                val (hintView, lp) = handleHintView(s.handleStyle)
                zone.addView(hintView, lp)
            }
            add(zone, params(Gravity.BOTTOM or side, 0, s.insetLift()))
        }
    }

    /** The pull-hint view for [style] (a line, one/two arrows, dots or a text label), bottom-centred. */
    private fun handleHintView(style: String): Pair<View, FrameLayout.LayoutParams> {
        val white = Color.argb(185, 255, 255, 255)
        fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(2) }
        return when (style) {
            LauncherSettings.TASKBAR_HANDLE_ARROW ->
                ChevronView(context, 1, white).also { it.elevation = dpf(2f) } to lp(dp(30), dp(13))
            LauncherSettings.TASKBAR_HANDLE_DOUBLE ->
                ChevronView(context, 2, white).also { it.elevation = dpf(2f) } to lp(dp(30), dp(20))
            LauncherSettings.TASKBAR_HANDLE_DOTS -> {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; elevation = dpf(2f) }
                repeat(3) { i ->
                    row.addView(
                        View(context).apply {
                            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(white) }
                        },
                        LinearLayout.LayoutParams(dp(5), dp(5)).apply { if (i < 2) marginEnd = dp(5) },
                    )
                }
                row to lp(FrameLayout.LayoutParams.WRAP_CONTENT, dp(5))
            }
            LauncherSettings.TASKBAR_HANDLE_TEXT -> {
                val tv = TextView(context).apply {
                    text = context.getString(R.string.taskbar_handle_text)
                    setTextColor(white)
                    textSize = 11f
                    elevation = dpf(2f)
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(11f)
                        setColor(Color.argb(150, 18, 22, 26))
                        setStroke(dp(1), Color.argb(45, 255, 255, 255))
                    }
                    setPadding(dp(13), dp(3), dp(13), dp(3))
                }
                tv to lp(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            }
            else -> { // TASKBAR_HANDLE_LINE
                val v = View(context).apply {
                    background = GradientDrawable().apply { cornerRadius = dpf(3f); setColor(white) }
                    elevation = dpf(2f)
                }
                v to lp(dp(28), dp(5))
            }
        }
    }

    /** Draws [count] stacked up-chevrons — the "arrow" and "two arrows" pull hints. */
    private class ChevronView(context: Context, private val count: Int, tint: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = tint
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.strokeWidth = (h / count) * 0.18f
            val armW = w * 0.42f
            val cx = w / 2f
            for (i in 0 until count) {
                val slotTop = i * (h / count)
                val top = slotTop + (h / count) * 0.28f
                val bottom = slotTop + (h / count) * 0.72f
                canvas.drawLine(cx - armW, bottom, cx, top, paint)
                canvas.drawLine(cx, top, cx + armW, bottom, paint)
            }
        }
    }

    /** A small visible pill at the bottom (per-app Mini pill): tap or swipe up to open the full bar. */
    private fun showMini(s: TaskbarSetup) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val pill = FrameLayout(context).apply {
            contentDescription = context.getString(R.string.taskbar_handle)
            background = GradientDrawable().apply {
                cornerRadius = dpf(16f)
                setColor(Color.argb(210, 18, 22, 26))
                setStroke(dp(1), Color.argb(55, 255, 255, 255))
            }
            elevation = dpf(6f)
            setPadding(dp(18), dp(7), dp(18), dp(7))
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(3f)
                        setColor(Color.argb(190, 255, 255, 255))
                    }
                },
                FrameLayout.LayoutParams(dp(26), dp(5), Gravity.CENTER),
            )
            var downY = 0f
            var opened = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = event.rawY; opened = false }
                    MotionEvent.ACTION_MOVE -> if (!opened && downY - event.rawY > slop * 2) { opened = true; open() }
                    MotionEvent.ACTION_UP -> if (!opened) open()
                }
                true
            }
        }
        add(pill, params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(8) + s.insetLift()))
    }

    private fun open() {
        expanded = true
        manuallyHidden = false
        loadRecents(rebuild = true, force = false)
    }

    /** The hide button: animate the bar away, then leave only the pull handles. */
    private fun hideToHandles() {
        manuallyHidden = true
        expanded = false
        handler.removeCallbacks(collapse)
        val view = barColumn
        val anim = setup?.taskbarAnim
        if (view != null && anim != null) animateBarOut(view, anim) { refresh() } else refresh()
    }

    /** Plays the chosen show animation on the bar [view] as it appears. */
    private fun animateBarIn(view: View, anim: String) {
        val dist = dpf(90f)
        view.animate().cancel()
        view.translationY = 0f; view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f
        val a = view.animate()
        when (anim) {
            LauncherSettings.TASKBAR_ANIM_FADE -> { view.alpha = 0f; a.alpha(1f).setDuration(220L) }
            LauncherSettings.TASKBAR_ANIM_SCALE -> { view.scaleX = 0.82f; view.scaleY = 0.82f; view.alpha = 0f; a.scaleX(1f).scaleY(1f).alpha(1f).setDuration(240L).interpolator = DecelerateInterpolator() }
            LauncherSettings.TASKBAR_ANIM_RISE -> { view.translationY = dist; view.alpha = 0f; a.translationY(0f).alpha(1f).setDuration(260L).interpolator = DecelerateInterpolator() }
            LauncherSettings.TASKBAR_ANIM_BOUNCE -> { view.translationY = dist; a.translationY(0f).setDuration(430L).interpolator = OvershootInterpolator(2.4f) }
            else -> { view.translationY = dist; a.translationY(0f).setDuration(240L).interpolator = DecelerateInterpolator() } // SLIDE
        }
        a.start()
    }

    /** Plays the chosen hide animation on the bar [view], then runs [onEnd] (which removes it). */
    private fun animateBarOut(view: View, anim: String, onEnd: () -> Unit) {
        val dist = dpf(90f)
        view.animate().cancel()
        val a = view.animate().setDuration(190L)
        when (anim) {
            LauncherSettings.TASKBAR_ANIM_FADE -> a.alpha(0f)
            LauncherSettings.TASKBAR_ANIM_SCALE -> a.scaleX(0.82f).scaleY(0.82f).alpha(0f)
            LauncherSettings.TASKBAR_ANIM_RISE -> a.translationY(dist).alpha(0f)
            else -> a.translationY(dist) // SLIDE, BOUNCE
        }
        a.withEndAction { onEnd() }.start()
    }

    // ------------------------------------------------------------------ the bar

    @SuppressLint("ClickableViewAccessibility")
    private fun showBar(s: TaskbarSetup, fixed: Boolean, animateIn: Boolean = false) {
        val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200)
        } else Color.rgb(180, 200, 255)
        val lightTheme = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
        val hint = TextView(context).apply {
            setTextColor(Color.BLACK)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dpf(18f)
                setColor(accent)
            }
            visibility = View.GONE
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            barBackground(s)?.let { background = it }
            val pad = if (s.theme == LauncherSettings.TASKBAR_THEME_CLEAR) dp(4) else dp(10)
            setPadding(pad, dp(6), pad, dp(6))
            clipChildren = false
            clipToPadding = false
        }
        val size = dp(s.iconSizeDp)
        fun addIcon(app: AppItem) {
            val view = iconView(app, s, size, hint, fixed)
            // An app that is open now: a small dot under its icon (Settings ▸ Recents).
            if (s.runningDots && app.packageName in runningPackages) {
                val windows = if (s.multiDots) runningCounts[app.packageName] ?: 1 else 1
                view.foreground = RunningDot(lightTheme, windows)
            }
            iconViews[app.badgeKey] = view
            row.addView(view, LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(s.spacingDp) / 2
                marginEnd = dp(s.spacingDp) / 2
            })
        }
        fun addPairIcon(pair: AppPair) {
            row.addView(pairView(pair, s, size), LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(s.spacingDp) / 2
                marginEnd = dp(s.spacingDp) / 2
            })
        }
        val showMedia = mediaShown(s)
        mediaWasShown = showMedia
        val controls = if (showMedia) mediaControlsView(s) else null
        fun addMedia() {
            row.addView(mediaIconView(s, size, controls, fixed), LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(s.spacingDp) / 2
                marginEnd = dp(s.spacingDp) / 2
            })
        }
        livePill = null
        if (s.liveEnabled && live.isNotEmpty()) {
            val pill = TaskbarLivePill(context, size + dp(10), s.liveEffect, lightTheme).apply {
                activity = live[liveIndex % live.size]
                extra = live.size - 1
                setOnClickListener {
                    val current = activity ?: return@setOnClickListener
                    current.contentIntent?.let { sendNotificationIntent(context, it) }
                    if (!fixed) {
                        expanded = false
                        refresh()
                    }
                }
                // A long press opens it up (Settings ▸ Notifications), or else shows the next one.
                setOnLongClickListener {
                    if (s.liveExpand) {
                        val current = activity ?: return@setOnLongClickListener false
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        showLiveDetails(current, this, s, fixed)
                        return@setOnLongClickListener true
                    }
                    if (live.size < 2) return@setOnLongClickListener false
                    liveIndex = (liveIndex + 1) % live.size
                    activity = live[liveIndex]
                    true
                }
            }
            livePill = pill
            row.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, size + dp(10)).apply {
                marginEnd = dp(4) + dp(s.spacingDp) / 2
            })
        }
        if (showMedia && s.mediaPosition == LauncherSettings.TASKBAR_MEDIA_POS_START) addMedia()
        s.pinned.forEach(::addIcon)
        s.pairs.forEach(::addPairIcon)
        if (showMedia && s.mediaPosition == LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE) addMedia()
        val recentApps = recents.filter { r -> s.pinned.none { it.key == r.key } }
        if ((s.pinned.isNotEmpty() || s.pairs.isNotEmpty()) && recentApps.isNotEmpty()) {
            val divider = View(context).apply {
                setBackgroundColor(if (lightTheme) Color.argb(90, 0, 0, 0) else Color.argb(110, 255, 255, 255))
            }
            row.addView(divider, LinearLayout.LayoutParams(dp(1), (size * 0.6f).toInt()).apply {
                marginStart = dp(6) + dp(s.spacingDp) / 2
                marginEnd = dp(6) + dp(s.spacingDp) / 2
            })
        }
        recentApps.forEach(::addIcon)
        if (showMedia && s.mediaPosition == LauncherSettings.TASKBAR_MEDIA_POS_END) addMedia()
        val withRecents = s.recentsButton != LauncherSettings.TASKBAR_RECENTS_OFF
        if (withRecents || s.splitControls) {
            if (row.childCount > 0) {
                row.addView(View(context).apply {
                    setBackgroundColor(if (lightTheme) Color.argb(90, 0, 0, 0) else Color.argb(110, 255, 255, 255))
                }, LinearLayout.LayoutParams(dp(1), (size * 0.6f).toInt()).apply {
                    marginStart = dp(6) + dp(s.spacingDp) / 2
                    marginEnd = dp(6) + dp(s.spacingDp) / 2
                })
            }
            if (s.splitControls) {
                row.addView(barButton(R.drawable.ic_taskbar_split, R.string.taskbar_split_menu, s, size, fixed,
                    onTap = { view -> showSplitMenu(view, s, fixed) }, onHold = null),
                    LinearLayout.LayoutParams(size, size).apply {
                        marginStart = dp(s.spacingDp) / 2
                        marginEnd = dp(s.spacingDp) / 2
                    })
            }
            if (withRecents) {
                row.addView(barButton(R.drawable.ic_taskbar_recents, R.string.taskbar_recents, s, size, fixed,
                    onTap = { openRecents(s) },
                    onHold = if (s.quickSwitch) ({ LockAccessibilityService.quickSwitch(); Unit }) else null),
                    LinearLayout.LayoutParams(size, size).apply {
                        marginStart = dp(s.spacingDp) / 2
                        marginEnd = dp(s.spacingDp) / 2
                    })
            }
            // A small hide button next to Recents: tucks the bar away, leaving just the pull handles.
            if (s.hideButton) {
                row.addView(barButton(R.drawable.ic_taskbar_hide, R.string.taskbar_hide, s, size, fixed,
                    onTap = { hideToHandles() }, onHold = null),
                    LinearLayout.LayoutParams(size, size).apply {
                        marginStart = dp(s.spacingDp) / 2
                        marginEnd = dp(s.spacingDp) / 2
                    })
            }
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            if (controls != null) {
                addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            }
            addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            if (!fixed) {
                // A touch anywhere else on the screen puts the opened bar away again.
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        expanded = false
                        refresh()
                    }
                    false
                }
            }
        }
        val barLayout = params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(s.bottomDp) + s.insetLift(), watchOutside = !fixed)
        // Glass theme: real backdrop blur behind the bar window (Android 12+; the system may ignore it
        // when cross-window blur is off, leaving just the translucent fill — no harm either way).
        if (s.theme == LauncherSettings.TASKBAR_THEME_GLASS && s.glassBlur > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            barLayout.blurBehindRadius = dp(s.glassBlur)
            barLayout.flags = barLayout.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }
        add(column, barLayout)
        barRow = row
        barColumn = column
        if (animateIn) animateBarIn(column, s.taskbarAnim)
        if (fixed && homeVisible) {
            // Tell home and the drawer where the bar's top edge is, whenever it is laid out again.
            row.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> publishSpace(v) }
        }
        // A drag over a split screen is handled differently when a third app isn't wanted.
        if (!fixed || !homeVisible) checkSplitForThirdApp(s)
        if (!fixed) {
            handler.removeCallbacks(collapse)
            handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
    }

    /** The room from the bar's top edge to the bottom of the screen, for home and the drawer. */
    private fun publishSpace(barRow: View) {
        if (!barRow.isAttachedToWindow || !homeVisible || shownMode != MODE_BAR_FIXED) return
        val location = IntArray(2)
        barRow.getLocationOnScreen(location)
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        TaskbarSpace.set(screenHeight - location[1] + dp(SPACE_GAP_DP))
    }

    /** The bar's backdrop for the chosen theme; null for the see-through one. */
    private fun barBackground(s: TaskbarSetup): Drawable? {
        val alpha = (s.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (s.theme == LauncherSettings.TASKBAR_THEME_GLASS) return glassBackground(alpha)
        val (fill, rim) = when (s.theme) {
            LauncherSettings.TASKBAR_THEME_CLEAR -> return null
            LauncherSettings.TASKBAR_THEME_FROSTED -> Color.argb((alpha * 0.55f).toInt(), 40, 42, 48) to Color.argb(70, 255, 255, 255)
            LauncherSettings.TASKBAR_THEME_LIGHT -> Color.argb(alpha, 242, 243, 246) to Color.argb(40, 0, 0, 0)
            LauncherSettings.TASKBAR_THEME_ACCENT -> {
                val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getColor(android.R.color.system_accent1_800)
                } else Color.rgb(30, 50, 90)
                Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)) to Color.argb(50, 255, 255, 255)
            }
            else -> Color.argb(alpha, 24, 24, 28) to Color.argb(45, 255, 255, 255)
        }
        return GradientDrawable().apply {
            cornerRadius = dpf(28f)
            setColor(fill)
            setStroke(dp(1), rim)
        }
    }

    /** Liquid Glass: milky see-through fill, bright rim, and a sheen along the top. */
    private fun glassBackground(alpha: Int): Drawable {
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb((alpha * 0.42f).toInt(), 255, 255, 255), Color.argb((alpha * 0.14f).toInt(), 255, 255, 255)),
        ).apply {
            cornerRadius = dpf(28f)
            setStroke(dp(1), Color.argb(150, 255, 255, 255))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255)),
        ).apply { cornerRadius = dpf(22f) }
        return android.graphics.drawable.LayerDrawable(arrayOf(body, sheen)).apply {
            setLayerInset(1, dp(10), dp(2), dp(10), 0)
            setLayerGravity(1, Gravity.TOP or Gravity.FILL_HORIZONTAL)
            setLayerHeight(1, dp(16))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    /**
     * A saved app pair (or trio) on the bar: its apps' icons together on one rounded tile. A tap
     * opens them in split screen — with a third app as a third pane where Folark's Vector module
     * runs in SystemUI, floating otherwise (see AppRepository.launchAppPair).
     */
    private fun pairView(pair: AppPair, s: TaskbarSetup, size: Int): View {
        val keys = listOfNotNull(pair.app1Key, pair.app2Key, pair.app3Key.takeIf { pair.hasThirdApp })
        val frame = FrameLayout(context).apply {
            contentDescription = pair.name
            background = GradientDrawable().apply {
                cornerRadius = size * 0.28f
                val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
                setColor(if (light) Color.argb(36, 0, 0, 0) else Color.argb(44, 255, 255, 255))
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                repository?.launchAppPair(pair)
            }
        }
        val three = keys.size == 3
        val small = (size * if (three) 0.46f else 0.54f).toInt()
        val spots = if (three) {
            listOf(Gravity.TOP or Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM or Gravity.START, Gravity.BOTTOM or Gravity.END)
        } else {
            listOf(Gravity.TOP or Gravity.START, Gravity.BOTTOM or Gravity.END)
        }
        val pad = size / 14
        keys.forEachIndexed { i, key ->
            val app = allApps.firstOrNull { it.key == key } ?: return@forEachIndexed
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val cached = iconCache[app.key]
            if (cached != null) {
                image.setImageDrawable(cached)
            } else {
                scope.launch {
                    val drawable = withContext(Dispatchers.IO) { loadIcon(app, s) }
                    if (drawable != null) {
                        iconCache[app.key] = drawable
                        image.setImageDrawable(drawable)
                    }
                }
            }
            frame.addView(image, FrameLayout.LayoutParams(small, small, spots[i]).apply { setMargins(pad, pad, pad, pad) })
        }
        return frame
    }

    private fun iconView(app: AppItem, s: TaskbarSetup, size: Int, hint: TextView, fixed: Boolean): ImageView {
        val icon = ImageView(context).apply {
            contentDescription = app.label
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = size / 12
            setPadding(inset, inset, inset, inset)
            // See-through bar: a soft shadow under each icon keeps it readable on any background.
            if (s.theme == LauncherSettings.TASKBAR_THEME_CLEAR) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val p = view.paddingLeft
                        outline.setOval(p, p, view.width - p, view.height - p)
                        outline.alpha = 0.6f
                    }
                }
                elevation = dpf(5f)
            }
        }
        val cached = iconCache[app.key]
        if (cached != null) {
            icon.setImageDrawable(cached)
        } else {
            scope.launch {
                val drawable = withContext(Dispatchers.IO) { loadIcon(app, s) }
                if (drawable != null) {
                    iconCache[app.key] = drawable
                    icon.setImageDrawable(drawable)
                }
            }
        }

        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val armDistance = dp(48)
        var downX = 0f
        var downY = 0f
        var moved = false
        var held = false
        var handedOver = false
        var triedSystem = false
        var action = ACTION_NONE
        val hold = Runnable {
            held = true
            icon.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showShortcuts(app, icon, s, fixed)
        }
        icon.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; moved = false; held = false; handedOver = false; triedSystem = false
                    action = ACTION_NONE
                    handler.removeCallbacks(collapse)
                    handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (handedOver || held) return@setOnTouchListener true
                    val dx = event.rawX - downX
                    val up = downY - event.rawY
                    if (abs(up) > slop || abs(dx) > slop) {
                        moved = true
                        handler.removeCallbacks(hold)
                    }
                    if (moved) {
                        ghostStart(view as ImageView, hint)
                        ghostMove(event.rawX, event.rawY, hint)
                        // Edge-snap: the edge the finger is over. Otherwise up-right = split, up-left = float.
                        val next = if (s.edgeSnap) {
                            edgeAction(event.rawX, event.rawY, up)
                        } else when {
                            up < armDistance * 0.6f -> ACTION_NONE
                            dx > armDistance -> ACTION_SPLIT
                            dx < -armDistance -> ACTION_FLOAT
                            else -> ACTION_NONE
                        }
                        // Over an app (old drag): split goes to Android's own drag and drop, and its drop zones.
                        if (!s.edgeSnap && next == ACTION_SPLIT && !homeVisible && useDropZones(s) && !triedSystem) {
                            triedSystem = true
                            val data = appDragData(app)
                            if (data != null) {
                                resetIcon(view, hint)
                                if (startSystemDrag(view, app.label, MIME_ACTIVITY, data, fixed)) {
                                    handedOver = true
                                    return@setOnTouchListener true
                                }
                                ghostStart(view as ImageView, hint)
                                ghostMove(event.rawX, event.rawY, hint)
                            }
                        }
                        if (next != action) {
                            action = next
                            val big = action != ACTION_NONE
                            ghostIcon?.animate()?.scaleX(if (big) 1.2f else 1f)?.scaleY(if (big) 1.2f else 1f)?.setDuration(110)?.start()
                            // The drop guide shows where to drop; the armed zone/chip lights up.
                            if (s.edgeSnap) highlightEdgeZones(action) else highlightDropChips(action)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(hold)
                    resetIcon(view, hint)
                    if (handedOver || held) return@setOnTouchListener true
                    val chosen = if (!moved) ACTION_OPEN else action
                    when (chosen) {
                        ACTION_OPEN -> {
                            LaunchOrigin.view(view)
                            launchApp(app)
                        }
                        ACTION_SPLIT -> openSplit(app)
                        // Left/right: open the app on that side and Folark's picker on the other.
                        ACTION_SPLIT_LEFT -> openSplitPick(app, right = false)
                        ACTION_SPLIT_RIGHT -> openSplitPick(app, right = true)
                        ACTION_TOP -> if (splitOpen) addThird(app) else launchApp(app)
                        ACTION_FLOAT -> repository?.launchFloating(app)
                        // Top-centre: peek view. Bottom-centre: three-app split (join an open split,
                        // else start a two-app split as the base).
                        ACTION_PEEK -> openSplit(app, peek = true)
                        ACTION_THREE -> if (splitOpen) addThird(app) else openSplit(app)
                    }
                    if (chosen != ACTION_NONE) {
                        expanded = false
                        if (!fixed) refresh()
                    } else if (!fixed) {
                        handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    resetIcon(view, hint)
                    if (!fixed && !handedOver && !held) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                }
            }
            true
        }
        return icon
    }

    // ------------------------------------------------------------------ recents and split buttons

    /** A plain button on the bar: [onTap], and [onHold] for a long press (if any). */
    @SuppressLint("ClickableViewAccessibility")
    private fun barButton(
        icon: Int,
        description: Int,
        s: TaskbarSetup,
        size: Int,
        fixed: Boolean,
        onTap: (View) -> Unit,
        onHold: (() -> Unit)?,
    ): View {
        val view = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = context.getString(description)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = size / 4
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(140, 20, 20, 25))
            }
        }
        view.setOnClickListener {
            onTap(view)
            if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
        if (onHold != null) {
            view.setOnLongClickListener {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onHold()
                expanded = false
                if (!fixed) refresh()
                true
            }
        }
        return view
    }

    /** Motorola's recents screen, or the recents of its desktop taskbar (Ready For) if chosen and present. */
    private fun openRecents(s: TaskbarSetup) {
        if (s.recentsButton == LauncherSettings.TASKBAR_RECENTS_FOLARK && FolarkRecents.open(context)) return
        if (s.recentsButton == LauncherSettings.TASKBAR_RECENTS_DESKTOP) {
            val intent = Intent(ACTION_DESKTOP_RECENTS)
                .setPackage(DESKTOP_TASKBAR_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
            Toast.makeText(context, R.string.taskbar_desktop_recents_missing, Toast.LENGTH_SHORT).show()
        }
        LockAccessibilityService.openRecents()
    }

    /** The split screen's actions above [anchor]: swap sides, or one of its apps full screen. */
    private fun showSplitMenu(anchor: View, s: TaskbarSetup, fixed: Boolean) {
        hideShortcuts()
        handler.removeCallbacks(collapse)
        scope.launch {
            val ready = ShellSplit.available(context)
            val tasks = if (ready) withContext(Dispatchers.IO) { ShellSplit.splitTasks(context) } else emptyList()
            if (!anchor.isAttachedToWindow) return@launch
            val rows = mutableListOf<Pair<String, (() -> Unit)?>>()
            when {
                !ready -> rows += context.getString(R.string.taskbar_split_setup_needed) to null
                tasks.size < 2 -> rows += context.getString(R.string.taskbar_split_none) to null
                else -> {
                    rows += context.getString(R.string.taskbar_split_swap) to {
                        scope.launch(Dispatchers.IO) { ShellSplit.swapSides(context) }
                        Unit
                    }
                    // Android's peek view, one tap (Folark's Vector module in SystemUI).
                    if (tasks.size == 2 && SplitThree.ready(context)) {
                        rows += context.getString(R.string.taskbar_split_peek_left) to {
                            scope.launch { SplitThree.snap(context, SplitThree.SNAP_PEEK_LEFT) }
                            Unit
                        }
                        rows += context.getString(R.string.taskbar_split_peek_right) to {
                            scope.launch { SplitThree.snap(context, SplitThree.SNAP_PEEK_RIGHT) }
                            Unit
                        }
                        rows += context.getString(R.string.taskbar_split_even) to {
                            scope.launch { SplitThree.snap(context, SplitThree.SNAP_EVEN) }
                            Unit
                        }
                    }
                    tasks.forEach { task ->
                        val label = allApps.firstOrNull { it.packageName == task.packageName }?.label ?: task.packageName
                        rows += context.getString(R.string.taskbar_split_keep, label) to {
                            scope.launch(Dispatchers.IO) { ShellSplit.exitSplit(context, task.taskId) }
                            Unit
                        }
                    }
                }
            }
            showMenu(anchor, s, fixed, rows)
        }
    }

    /** A small card of text rows above [anchor]; a row without an action is a note. */
    @SuppressLint("ClickableViewAccessibility")
    private fun showMenu(anchor: View, s: TaskbarSetup, fixed: Boolean, rows: List<Pair<String, (() -> Unit)?>>) {
        val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
        val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(22f)
                setColor(if (light) Color.argb(245, 242, 243, 246) else Color.argb(235, 28, 28, 32))
                setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = dpf(8f)
        }
        rows.forEach { (label, action) ->
            panel.addView(TextView(context).apply {
                text = label
                setTextColor(if (action == null) Color.argb(170, Color.red(tint), Color.green(tint), Color.blue(tint)) else tint)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(260)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                if (action != null) {
                    background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Color.TRANSPARENT) }
                    setOnClickListener {
                        action()
                        hideShortcuts()
                        expanded = false
                        if (!fixed) refresh()
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideShortcuts()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
            false
        }
        placeAbove(anchor, panel)
    }

    /** Shows [panel] in its own window just above [anchor] (the same slot as the shortcuts card). */
    private fun placeAbove(anchor: View, panel: View) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val x = (at[0] + anchor.width / 2 - panel.measuredWidth / 2)
            .coerceIn(dp(8), (screenWidth - panel.measuredWidth - dp(8)).coerceAtLeast(dp(8)))
        val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        if (runCatching { windowManager.addView(panel, params) }.isSuccess) shortcutWindow = panel
    }

    /** Whether an open split screen was seen when the bar last opened (only checked if a third app isn't wanted). */
    @Volatile
    private var splitOpen = false

    private fun checkSplitForThirdApp(s: TaskbarSetup) {
        splitOpen = false
        if (s.threeApp || !s.systemDrag || homeVisible || !ShellSplit.available(context)) return
        scope.launch {
            splitOpen = withContext(Dispatchers.IO) { ShellSplit.splitTasks(context).size >= 2 }
        }
    }

    /**
     * Whether a split drag goes to Android's drop zones: when they are on, except over an open split
     * screen with "third app" off (the drop zones would offer a third place; Folark's own split
     * replaces one side instead).
     */
    private fun useDropZones(s: TaskbarSetup): Boolean = s.systemDrag && (s.threeApp || !splitOpen)

    // ------------------------------------------------------------------ Android's drag and drop

    private var barRow: View? = null
    private var barColumn: View? = null
    private var systemDragging = false

    /**
     * Hands [source] to Android's drag and drop, as Motorola's taskbar does: an item of type [mime]
     * with [data] (a launch link and who it is for). Over an app, Android shows its split drop zones
     * and opens the item in the half it is dropped on. False if Android refused the drag.
     */
    private fun startSystemDrag(source: View, label: CharSequence, mime: String, data: Intent, fixed: Boolean): Boolean {
        barRegion()?.let { data.putExtra(EXTRA_DISALLOW_HIT_REGION, it) }
        val clip = ClipData(ClipDescription(label, arrayOf(mime)), ClipData.Item(data))
        source.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENDED -> {
                    systemDragging = false
                    source.setOnDragListener(null)
                    hideShortcuts()
                    if (!fixed) {
                        expanded = false
                        refresh()
                    }
                    true
                }
                else -> false
            }
        }
        systemDragging = true
        val started = runCatching {
            source.startDragAndDrop(
                clip,
                View.DragShadowBuilder(source),
                null,
                View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE,
            )
        }.getOrDefault(false)
        if (!started) {
            systemDragging = false
            source.setOnDragListener(null)
        }
        return started
    }

    /** The bar's place on screen: nothing may be dropped there. */
    private fun barRegion(): RectF? {
        val row = barRow ?: return null
        if (!row.isAttachedToWindow) return null
        val at = IntArray(2)
        row.getLocationOnScreen(at)
        return RectF(at[0].toFloat(), at[1].toFloat(), (at[0] + row.width).toFloat(), (at[1] + row.height).toFloat())
    }

    /** What Android needs to open [app] where it is dropped; null if no launch link could be made. */
    private fun appDragData(app: AppItem): Intent? {
        val link = mainActivityLink(app) ?: return null
        return Intent()
            .putExtra(EXTRA_DRAG_PENDING_INTENT, link)
            .putExtra(Intent.EXTRA_USER, app.user)
    }

    /** The launch link Android's own launcher uses; for Folark's own user, a plain one as a fallback. */
    private fun mainActivityLink(app: AppItem): PendingIntent? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val system = runCatching {
            LauncherApps::class.java
                .getMethod("getMainActivityLaunchIntent", ComponentName::class.java, Bundle::class.java, UserHandle::class.java)
                .invoke(launcherApps, app.componentName, null, app.user) as? PendingIntent
        }.getOrNull()
        if (system != null) return system
        if (app.user != Process.myUserHandle()) return null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            PendingIntent.getActivity(
                context,
                app.key.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }.getOrNull()
    }

    /**
     * What Android needs to open the shortcut [info] where it is dropped. Android takes items typed
     * as shortcuts only from system apps, so the shortcut goes as an app item ([MIME_ACTIVITY])
     * carrying the shortcut's own launch link.
     */
    private fun shortcutDragData(info: ShortcutInfo): Intent? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val link = runCatching { launcherApps.getShortcutIntent(info.`package`, info.id, null, info.userHandle) }.getOrNull()
            ?: return null
        return Intent()
            .putExtra(EXTRA_DRAG_PENDING_INTENT, link)
            .putExtra(Intent.EXTRA_USER, info.userHandle)
    }

    // ------------------------------------------------------------------ app shortcuts

    private var shortcutWindow: View? = null

    /** How long the opened-up live pill stays over the bar. */
    private val LIVE_PANEL_IDLE_MS = 10_000L

    /** The app's shortcuts in a card above [anchor]: tap one to open it, drag it (over an app) to split. */
    @SuppressLint("ClickableViewAccessibility")
    private fun showShortcuts(app: AppItem, anchor: View, s: TaskbarSetup, fixed: Boolean) {
        hideShortcuts()
        handler.removeCallbacks(collapse)
        scope.launch {
            val dpi = context.resources.displayMetrics.densityDpi
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val items = withContext(Dispatchers.IO) {
                shortcutsOf(app).map { info ->
                    info to runCatching { launcherApps?.getShortcutIconDrawable(info, dpi) }.getOrNull()
                }
            }
            if (!anchor.isAttachedToWindow) return@launch
            if (items.isEmpty()) {
                Toast.makeText(context, R.string.taskbar_no_shortcuts, Toast.LENGTH_SHORT).show()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                return@launch
            }
            val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
            val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dpf(22f)
                    setColor(if (light) Color.argb(245, 242, 243, 246) else Color.argb(235, 28, 28, 32))
                    setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
                }
                setPadding(dp(6), dp(6), dp(6), dp(6))
                elevation = dpf(8f)
            }
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            items.forEach { (info, drawable) ->
                val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
                val rowView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(8), dp(16), dp(8))
                    background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Color.TRANSPARENT) }
                    contentDescription = label
                }
                rowView.addView(ImageView(context).apply { setImageDrawable(drawable) }, LinearLayout.LayoutParams(dp(28), dp(28)))
                rowView.addView(TextView(context).apply {
                    text = label
                    setTextColor(tint)
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxWidth = dp(220)
                    setPadding(dp(12), 0, 0, 0)
                })
                var downX = 0f
                var downY = 0f
                var gone = false
                rowView.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX; downY = event.rawY; gone = false
                            (v.background as? GradientDrawable)?.setColor(Color.argb(40, Color.red(tint), Color.green(tint), Color.blue(tint)))
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val far = abs(event.rawX - downX) > slop * 2 || abs(event.rawY - downY) > slop * 2
                            if (!gone && far && !homeVisible && useDropZones(s)) {
                                val data = shortcutDragData(info)
                                if (data != null && startSystemDrag(v, label, MIME_ACTIVITY, data, fixed)) gone = true
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            (v.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
                            val far = abs(event.rawX - downX) > slop * 2 || abs(event.rawY - downY) > slop * 2
                            if (!gone && !far) {
                                LaunchOrigin.view(v)
                                runCatching { launcherApps?.startShortcut(info, null, null) }
                                hideShortcuts()
                                expanded = false
                                if (!fixed) refresh()
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> (v.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
                    }
                    true
                }
                panel.addView(rowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            panel.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && !systemDragging) {
                    hideShortcuts()
                    if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                }
                false
            }
            panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val at = IntArray(2)
            anchor.getLocationOnScreen(at)
            val screenWidth = context.resources.displayMetrics.widthPixels
            val x = (at[0] + anchor.width / 2 - panel.measuredWidth / 2)
                .coerceIn(dp(8), (screenWidth - panel.measuredWidth - dp(8)).coerceAtLeast(dp(8)))
            val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            if (runCatching { windowManager.addView(panel, params) }.isSuccess) shortcutWindow = panel
        }
    }

    /**
     * The live pill opened up, as a small panel over the bar: the app, a big readout (clock, ETA,
     * percentage), the title and text, a progress bar, and the notification's buttons plus Open.
     * Closes on a touch outside it, a choice, or after a while.
     */
    private fun showLiveDetails(item: org.arkikeskus.launcher.model.LiveActivity, anchor: View, s: TaskbarSetup, fixed: Boolean) {
        hideShortcuts()
        handler.removeCallbacks(collapse)
        val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
        val ink = if (light) Color.rgb(20, 20, 24) else Color.WHITE
        val soft = if (light) Color.argb(170, 20, 20, 24) else Color.argb(190, 255, 255, 255)
        val accent = item.color ?: Color.rgb(143, 211, 255)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(24f)
                setColor(if (light) Color.argb(248, 242, 243, 246) else Color.argb(242, 22, 28, 31))
                setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
            }
            setPadding(dp(18), dp(14), dp(18), dp(14))
            elevation = dpf(10f)
            minimumWidth = dp(260)
        }
        fun text(value: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false, lines: Int = 1) = TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            maxLines = lines
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        panel.addView(text(item.appLabel.ifBlank { item.packageName }, 12f, soft))
        val now = System.currentTimeMillis()
        item.shortText(now).takeIf { it != item.title && it != item.text }?.let {
            panel.addView(text(it, 28f, ink, bold = true).apply {
                if (item.ticks || item.progress != null) typeface = android.graphics.Typeface.MONOSPACE
            })
        }
        panel.addView(text(item.title, 15f, ink, bold = true, lines = 2))
        val body = item.bigText ?: item.text
        if (body.isNotBlank() && body != item.title) panel.addView(text(body, 13f, soft, lines = 3))
        if (item.progress != null || item.indeterminate) {
            val bar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = item.indeterminate
                max = 1000
                progress = ((item.progress ?: 0f) * 1000).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
            }
            panel.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(8) })
        }
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        fun button(label: String, filled: Boolean, onClick: () -> Unit) = text(label, 14f, if (filled) Color.WHITE else ink, bold = true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dpf(999f)
                setColor(if (filled) accent else if (light) Color.argb(26, 0, 0, 0) else Color.argb(32, 255, 255, 255))
            }
            setOnClickListener {
                hideShortcuts()
                onClick()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
        }
        item.actions.take(3).forEach { action ->
            buttons.addView(button(action.title, false) { sendNotificationIntent(context, action.intent) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        buttons.addView(button(org.arkikeskus.launcher.model.LiveActivity.OPEN_LABEL, true) {
            item.contentIntent?.let { sendNotificationIntent(context, it) }
        })
        panel.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideShortcuts()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
            false
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = panel.measuredWidth.coerceAtMost(dp(380))
        val x = (at[0] + anchor.width / 2 - width / 2).coerceIn(dp(8), (screenWidth - width - dp(8)).coerceAtLeast(dp(8)))
        val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        if (runCatching { windowManager.addView(panel, params) }.isSuccess) {
            shortcutWindow = panel
            handler.postDelayed({ if (shortcutWindow === panel) hideShortcuts() }, LIVE_PANEL_IDLE_MS)
        }
    }

    private fun hideShortcuts() {
        shortcutWindow?.let { runCatching { windowManager.removeView(it) } }
        shortcutWindow = null
    }

    /** The app's shortcuts (from its manifest, the app itself, and pinned ones), best first. */
    private fun shortcutsOf(app: AppItem): List<ShortcutInfo> = runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        launcherApps.getShortcuts(query, app.user).orEmpty()
            .filter { it.isEnabled && !(it.shortLabel ?: it.longLabel).isNullOrBlank() }
            .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
            .distinctBy { it.id }
            .take(MAX_SHORTCUTS)
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ the media icon

    /** The cover of what is playing (the player's icon if it has none), with the player as a badge. */
    @SuppressLint("ClickableViewAccessibility")
    private fun mediaIconView(s: TaskbarSetup, size: Int, controls: View?, fixed: Boolean): View {
        val playing = media.state
        val player = playing?.let { st -> allApps.firstOrNull { it.packageName == st.packageName } }
        val inset = size / 12
        val round = s.mediaShape != LauncherSettings.TASKBAR_MEDIA_SHAPE_ROUNDED
        val frame = FrameLayout(context).apply {
            contentDescription = playing?.title?.ifEmpty { null } ?: context.getString(R.string.taskbar_media)
            clipChildren = false
            clipToPadding = false
        }
        val cover = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (round) {
                        outline.setOval(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.24f)
                    }
                }
            }
            clipToOutline = true
            elevation = dpf(4f)
            // Paused: the cover dims a little.
            alpha = if (playing?.playing == true) 1f else 0.7f
        }
        val art = playing?.art
        if (art != null) {
            cover.setImageBitmap(art)
        } else if (player != null) {
            setIconInto(cover, player, s)
        }
        frame.addView(cover, FrameLayout.LayoutParams(size - inset * 2, size - inset * 2, Gravity.CENTER))
        if (s.mediaBadge && art != null && player != null) {
            val badge = ImageView(context).apply { elevation = dpf(6f) }
            setIconInto(badge, player, s)
            val badgeSize = (size * 0.4f).toInt()
            frame.addView(badge, FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.BOTTOM or Gravity.END))
        }

        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downY = 0f
        var downX = 0f
        var moved = false
        var held = false
        val hold = Runnable {
            held = true
            openPlayer(player)
        }
        frame.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; moved = false; held = false
                    handler.removeCallbacks(collapse)
                    handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(downY - event.rawY) > slop || abs(event.rawX - downX) > slop)) {
                        moved = true
                        handler.removeCallbacks(hold)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    if (event.actionMasked == MotionEvent.ACTION_UP && !held && !moved) onMediaTap()
                    if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                }
            }
            true
        }
        return frame
    }

    private fun openPlayer(player: AppItem?) {
        if (!media.openPlayer() && player != null) launchApp(player)
        expanded = false
        mediaControlsOpen = false
        refresh()
    }

    /**
     * A tap on the cover opens (or closes) the player card; a second tap right after it stops the
     * music and hides the cover. The single tap waits the double-tap time before it acts.
     */
    private fun onMediaTap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastMediaTapAt <= ViewConfiguration.getDoubleTapTimeout()) {
            lastMediaTapAt = 0L
            handler.removeCallbacks(mediaSingleTap)
            stopAndHideMedia()
            return
        }
        lastMediaTapAt = now
        handler.removeCallbacks(mediaSingleTap)
        handler.postDelayed(mediaSingleTap, ViewConfiguration.getDoubleTapTimeout().toLong())
    }

    private fun toggleMediaCard() {
        handler.removeCallbacks(closeMediaControls)
        if (mediaControlsOpen) {
            mediaControlsOpen = false
        } else {
            mediaControlsOpen = true
            handler.postDelayed(closeMediaControls, MEDIA_CONTROLS_MS)
        }
        refresh(rebuild = true)
    }

    private fun stopAndHideMedia() {
        val playing = media.state ?: return
        media.stopPlayback()
        mediaHiddenFor = playing.packageName
        mediaHiddenAt = SystemClock.uptimeMillis()
        mediaControlsOpen = false
        handler.removeCallbacks(closeMediaControls)
        refresh(rebuild = true)
    }

    /** The player card above the bar: cover, title and artist, back, play/pause and forward. */
    private fun mediaControlsView(s: TaskbarSetup): View {
        val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
        val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
        val playingNow = media.state
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(26f)
                setColor(if (light) Color.argb(235, 242, 243, 246) else Color.argb(215, 24, 24, 28))
                setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(50, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = if (mediaControlsOpen) View.VISIBLE else View.GONE
        }
        val art = playingNow?.art
        if (art != null) {
            val cover = ImageView(context).apply {
                setImageBitmap(art)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dpf(12f))
                    }
                }
                clipToOutline = true
            }
            panel.addView(cover, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
        if (playingNow != null && (playingNow.title.isNotEmpty() || playingNow.artist.isNotEmpty())) {
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            if (playingNow.title.isNotEmpty()) {
                texts.addView(TextView(context).apply {
                    text = playingNow.title
                    setTextColor(tint)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxWidth = dp(200)
                })
            }
            if (playingNow.artist.isNotEmpty()) {
                texts.addView(TextView(context).apply {
                    text = playingNow.artist
                    setTextColor(Color.argb(180, Color.red(tint), Color.green(tint), Color.blue(tint)))
                    textSize = 12f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxWidth = dp(200)
                })
            }
            panel.addView(texts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        fun button(icon: Int, description: Int, action: () -> Unit) {
            val view = ImageView(context).apply {
                setImageResource(icon)
                setColorFilter(tint)
                contentDescription = context.getString(description)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    action()
                    handler.removeCallbacks(collapse)
                    handler.removeCallbacks(closeMediaControls)
                    handler.postDelayed(closeMediaControls, MEDIA_CONTROLS_MS)
                    if (shownMode == MODE_BAR) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                }
            }
            panel.addView(view, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        button(android.R.drawable.ic_media_previous, R.string.taskbar_media_previous) { media.previous() }
        val playing = media.state?.playing == true
        button(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (playing) R.string.taskbar_media_pause else R.string.taskbar_media_play,
        ) { media.playPause() }
        button(android.R.drawable.ic_media_next, R.string.taskbar_media_next) { media.next() }
        return panel
    }

    /** Puts [app]'s launcher icon into [view], from the cache or loaded in the background. */
    private fun setIconInto(view: ImageView, app: AppItem, s: TaskbarSetup) {
        val cached = iconCache[app.key]
        if (cached != null) {
            view.setImageDrawable(cached)
            return
        }
        scope.launch {
            val drawable = withContext(Dispatchers.IO) { loadIcon(app, s) }
            if (drawable != null) {
                iconCache[app.key] = drawable
                view.setImageDrawable(drawable)
            }
        }
    }

    private fun resetIcon(view: View, hint: TextView) {
        view.animate().cancel()
        view.translationX = 0f; view.translationY = 0f; view.scaleX = 1f; view.scaleY = 1f
        view.alpha = 1f
        hint.visibility = View.GONE
        ghostEnd()
    }

    // ------------------------------------------------------------------ the dragged icon

    private var ghostWindow: FrameLayout? = null
    private var ghostIcon: ImageView? = null
    private val ghostOrigin = IntArray(2)

    /** Puts a copy of [source] (and the [hint]) in a full-screen window that ignores touches. */
    private fun ghostStart(source: ImageView, hint: TextView) {
        if (ghostWindow != null) return
        val size = source.width.takeIf { it > 0 } ?: dp(48)
        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        val copy = ImageView(context).apply {
            setImageDrawable(source.drawable)
            scaleType = source.scaleType
            setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
            alpha = GHOST_ALPHA
            elevation = dpf(8f)
        }
        root.addView(copy, FrameLayout.LayoutParams(size, size))
        (hint.parent as? android.view.ViewGroup)?.removeView(hint)
        hint.visibility = View.INVISIBLE
        root.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (setup?.edgeSnap == true) addEdgeZones(root) else addDropChips(root)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (runCatching { windowManager.addView(root, params) }.isSuccess) {
            ghostWindow = root
            ghostIcon = copy
            source.alpha = DRAG_ALPHA
        }
    }

    /** Keeps the copy just above the finger at ([rawX], [rawY]) on screen, the hint above it. */
    private fun ghostMove(rawX: Float, rawY: Float, hint: TextView) {
        val root = ghostWindow ?: return
        val copy = ghostIcon ?: return
        root.getLocationOnScreen(ghostOrigin)
        val half = (copy.layoutParams?.width ?: dp(48)) / 2f
        copy.x = rawX - ghostOrigin[0] - half
        copy.y = rawY - ghostOrigin[1] - half * 2 - dp(16)
        if (hint.width > 0) {
            hint.x = rawX - ghostOrigin[0] - hint.width / 2f
            hint.y = copy.y - hint.height - dp(10)
        }
    }

    private fun ghostEnd() {
        ghostWindow?.let { runCatching { windowManager.removeView(it) } }
        ghostWindow = null
        ghostIcon = null
        splitChip = null
        floatChip = null
        leftZone = null
        rightZone = null
        topZone = null
        bottomZone = null
        peekZone = null
        threeZone = null
    }

    // ---------------------------------------------------------------- drag drop-zone chips

    private var splitChip: TextView? = null
    private var floatChip: TextView? = null

    /**
     * Two chips in the dragged-icon window while an app icon is dragged: "Floating window" on the
     * left, "Split screen" on the right — the sides the drag arms. The one matching the current
     * [action] lights up. They are only a guide (the window ignores touches); the gesture direction
     * (and, over an app, Android's own drop zones) does the launching.
     */
    private fun addDropChips(root: FrameLayout) {
        val top = (context.resources.displayMetrics.heightPixels * 0.56f).toInt()
        floatChip = makeDropChip(context.getString(R.string.taskbar_drop_float))
        splitChip = makeDropChip(context.getString(R.string.taskbar_drop_split))
        root.addView(
            floatChip,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START; leftMargin = dp(24); topMargin = top
            },
        )
        root.addView(
            splitChip,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END; rightMargin = dp(24); topMargin = top
            },
        )
        highlightDropChips(ACTION_NONE)
    }

    private fun makeDropChip(label: String): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        elevation = dpf(6f)
    }

    /** Lights up the chip for [action] (ACTION_FLOAT = left, ACTION_SPLIT = right), dims the other. */
    private fun highlightDropChips(action: Int) {
        val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200)
        } else Color.rgb(180, 200, 255)
        fun style(chip: TextView?, armed: Boolean) {
            chip ?: return
            chip.background = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(if (armed) accent else Color.argb(205, 20, 24, 30))
                if (!armed) setStroke(dp(1), Color.argb(60, 255, 255, 255))
            }
            chip.setTextColor(if (armed) Color.BLACK else Color.WHITE)
            chip.animate()
                .scaleX(if (armed) 1.12f else 1f).scaleY(if (armed) 1.12f else 1f)
                .alpha(if (armed) 1f else 0.82f).setDuration(110).start()
        }
        style(floatChip, action == ACTION_FLOAT)
        style(splitChip, action == ACTION_SPLIT)
    }

    // ---------------------------------------------------------------- edge-snap glass drop zones

    private var leftZone: FrameLayout? = null
    private var rightZone: FrameLayout? = null
    private var topZone: FrameLayout? = null
    private var bottomZone: FrameLayout? = null
    private var peekZone: FrameLayout? = null
    private var threeZone: FrameLayout? = null

    /**
     * Four frosted-glass drop zones hugging the screen edges while an icon is dragged (edge-snap):
     * left / right half (split), top (full screen, or a third app over a split), bottom (floating
     * window). The one the finger is over lights up with the accent and shows its label. Only a
     * guide (the window ignores touches); [edgeAction] decides what the drop does.
     */
    private fun addEdgeZones(root: FrameLayout) {
        // Full-region previews of where the app will land — the same Windows-snap look as Folark's
        // Recents: a glass region fills the target half/area, and only the one under the finger shows.
        val m = context.resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val margin = dp(12)
        val tall = h - dp(120) // clear of the bar at the bottom
        fun region(label: Int) = makeEdgeZone(context.getString(label))
        leftZone = region(R.string.taskbar_snap_left).also {
            root.addView(it, FrameLayout.LayoutParams(w / 2 - margin, tall, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = margin })
        }
        rightZone = region(R.string.taskbar_snap_right).also {
            root.addView(it, FrameLayout.LayoutParams(w / 2 - margin, tall, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = margin })
        }
        topZone = region(R.string.taskbar_snap_top).also {
            root.addView(it, FrameLayout.LayoutParams(w - margin * 2, tall, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = margin })
        }
        bottomZone = region(R.string.taskbar_snap_float).also {
            root.addView(it, FrameLayout.LayoutParams((w * 0.5f).toInt(), (h * 0.34f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(96) })
        }
        peekZone = region(R.string.taskbar_snap_peek).also {
            root.addView(it, FrameLayout.LayoutParams((w * 0.84f).toInt(), tall, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = margin })
        }
        threeZone = region(R.string.taskbar_snap_three).also {
            root.addView(it, FrameLayout.LayoutParams(w - margin * 2, tall, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = margin })
        }
        highlightEdgeZones(ACTION_NONE)
    }

    private fun makeEdgeZone(label: String): FrameLayout = FrameLayout(context).apply {
        elevation = dpf(4f)
        addView(
            TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                tag = "label"
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
    }

    /** Lights the glass zone the finger is over (frosted when idle, accent and grown when armed). */
    private fun highlightEdgeZones(action: Int) {
        val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200)
        } else Color.rgb(180, 200, 255)
        // Only the region under the finger shows (the rest are hidden), exactly like Recents.
        fun style(zone: FrameLayout?, armed: Boolean) {
            zone ?: return
            if (!armed) {
                if (zone.visibility != View.GONE) zone.visibility = View.GONE
                return
            }
            zone.background = GradientDrawable().apply {
                cornerRadius = dpf(26f)
                setColor(Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent)))
                setStroke(dp(2), Color.argb(225, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }
            zone.findViewWithTag<TextView>("label")?.apply {
                alpha = 1f
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            if (zone.visibility != View.VISIBLE) {
                zone.visibility = View.VISIBLE
                zone.alpha = 0f
                zone.scaleX = 0.97f; zone.scaleY = 0.97f
                zone.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
        style(leftZone, action == ACTION_SPLIT_LEFT)
        style(rightZone, action == ACTION_SPLIT_RIGHT)
        style(topZone, action == ACTION_TOP)
        style(bottomZone, action == ACTION_FLOAT)
        style(peekZone, action == ACTION_PEEK)
        style(threeZone, action == ACTION_THREE)
    }

    private fun loadIcon(app: AppItem, s: TaskbarSetup): Drawable? {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val icon = runCatching {
            source?.loadIcon(app, s.themedIcons, dark, s.iconPack, s.customIcons[app.key].orEmpty())
        }.getOrNull() ?: runCatching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
        return icon?.let { IconShapes.apply(it, s.iconShape) }
    }

    private fun launchApp(app: AppItem) {
        repository?.launch(app)
    }

    // ------------------------------------------------------------------ split screen

    /**
     * Puts [app] in split screen beside the app in front: [app] is opened (it covers the current
     * app), then Android's split-screen command moves it into the right half, so the app that was
     * in front fills the left half. On the home screen there is no app to split with, so the most
     * recent app is opened first. Without the one-time permission (see [ShellSplit]) the app just
     * opens, with a note on where to set split screen up.
     */
    private fun openSplit(app: AppItem, right: Boolean = setup?.splitSide != LauncherSettings.TASKBAR_SPLIT_SIDE_LEFT, peek: Boolean = false) {
        if (!ShellSplit.available(context)) {
            Toast.makeText(context, R.string.taskbar_split_setup_needed, Toast.LENGTH_LONG).show()
            launchApp(app)
            return
        }
        val partner = if (homeVisible) recents.firstOrNull { it.key != app.key } else null
        if (homeVisible && partner == null) {
            launchApp(app)
            return
        }
        scope.launch {
            if (partner != null) {
                launchApp(partner)
                delay(SPLIT_PARTNER_DELAY_MS)
            }
            launchApp(app)
            delay(SPLIT_SETTLE_DELAY_MS)
            var taskId: Int? = null
            repeat(SPLIT_FIND_TRIES) {
                if (taskId == null) {
                    taskId = withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, app.packageName) }
                    if (taskId == null) delay(SPLIT_RETRY_MS)
                }
            }
            val id = taskId
            if (id == null) {
                return@launch
            }
            withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = right) }
            if (peek) {
                // Narrow the split so the dragged app only peeks in (Android's own peek layout).
                delay(SPLIT_SETTLE_DELAY_MS)
                SplitThree.snap(context, if (right) SplitThree.SNAP_PEEK_RIGHT else SplitThree.SNAP_PEEK_LEFT)
            }
        }
    }

    /**
     * Split-select: [app] opens on one side and Folark's picker drawer on the other. Reuses the same
     * launch-then-moveToSide path as [openSplit] (which forms a split reliably here), with the picker
     * as the partner; picking an app in the drawer replaces the picker in its pane.
     */
    private fun openSplitPick(app: AppItem, right: Boolean) {
        if (!ShellSplit.available(context)) {
            Toast.makeText(context, R.string.taskbar_split_setup_needed, Toast.LENGTH_LONG).show()
            launchApp(app)
            return
        }
        // Show the picker to choose the partner; it builds the split from the two real apps.
        runCatching { context.startActivity(SplitPicker.intent(context, app.packageName, right)) }
    }

    /** The screen edge the finger is over while dragging (edge-snap): the six Recents-style zones. */
    private fun edgeAction(rawX: Float, rawY: Float, up: Float): Int {
        if (up < dp(28)) return ACTION_NONE // lift the icon clear of the bar first
        val m = context.resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        return when {
            // Centre of the top/bottom edges: peek and three-app split (same zones as Recents).
            rawY < h * 0.22f && rawX > w * 0.40f && rawX < w * 0.60f -> ACTION_PEEK
            rawY > h * 0.78f && rawX > w * 0.40f && rawX < w * 0.60f -> ACTION_THREE
            rawX < w * 0.32f -> ACTION_SPLIT_LEFT
            rawX > w * 0.68f -> ACTION_SPLIT_RIGHT
            rawY < h * 0.16f -> ACTION_TOP
            rawY > h * 0.84f -> ACTION_FLOAT
            else -> ACTION_NONE
        }
    }

    /** Adds [app] as the third pane of the two-app split showing now (Android's own three-way split). */
    private fun addThird(app: AppItem) {
        scope.launch {
            withContext(Dispatchers.IO) { SplitThree.addThird(context, component = app.componentName, user = app.user) }
        }
    }

    // ------------------------------------------------------------------ recent apps

    /**
     * Reads the recent apps, then shows the bar. [force]: rebuild its windows even if the recent apps
     * are unchanged (what the bar looks like changed); otherwise they are rebuilt only when the recent
     * apps it shows changed. A change of form (bar, handles, nothing) always rebuilds, in [refresh].
     */
    private fun loadRecents(rebuild: Boolean, force: Boolean = true) {
        // A forced rebuild still waiting (its read cancelled by this newer call) is carried over.
        pendingForce = pendingForce || force
        val s = setup ?: run { refresh(rebuild); return }
        recentsJob?.cancel()
        // Nothing on screen would show recent apps: no usage query.
        if (!s.enabled || !screenOn || !largeScreen() || (homeVisible && !s.onHome)) {
            pendingForce = false
            refresh(rebuild)
            return
        }
        recentsJob = scope.launch {
            val next = withContext(Dispatchers.IO) { recentApps(s) }
            val running = runningPackages
            val counts = runningCounts
            val changed = next != recents || (s.runningDots && (running != shownRunning || counts != shownCounts))
            recents = next
            shownRunning = running
            shownCounts = counts
            val barUp = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED
            val forced = pendingForce
            pendingForce = false
            refresh(rebuild = rebuild && (forced || (changed && barUp)))
        }
    }

    private var pendingForce = false

    private fun recentApps(s: TaskbarSetup): List<AppItem> {
        if (s.recentCount <= 0) return emptyList()
        val pinnedKeys = s.pinned.map { it.key }.toSet()
        val own = context.packageName
        val myUser = Process.myUserHandle()
        // Open apps from Android's task list (one-time DUMP grant), for the dots and open-apps mode.
        val open = if (s.runningApps || s.runningDots) OpenTasks.list(context) else null
        runningPackages = open?.map { it.packageName }?.toSet().orEmpty()
        // Count concurrently-open windows per package: freeform pop-ups and split tasks each count,
        // while any number of plain full-screen recents of one app counts once (not several stale ones).
        runningCounts = open?.groupBy { it.packageName }?.mapValues { (_, tasks) ->
            val live = tasks.count { it.windowingMode == "freeform" || it.windowingMode == "multi-window" }
            val fullScreen = if (tasks.any { it.windowingMode == null || it.windowingMode == "fullscreen" }) 1 else 0
            (live + fullScreen).coerceAtLeast(1)
        }.orEmpty()
        val packages = if (s.runningApps && open != null) open.map { it.packageName }.distinct()
            else if (hasUsageAccess(context)) usageHistory() else null
        val ordered: List<AppItem> = if (packages != null) {
            packages.mapNotNull { pkg ->
                allApps.firstOrNull { it.packageName == pkg && it.user == myUser }
                    ?: allApps.firstOrNull { it.packageName == pkg }
            }
        } else {
            val byKey = allApps.associateBy { it.key }
            s.launchHistory.mapNotNull { byKey[it] }
        }
        return ordered
            .filter { it.packageName != own && it.key !in pinnedKeys }
            .distinctBy { it.key }
            .take(s.recentCount)
    }

    /** When each package last came to the front within the window, kept up to date incrementally. */
    private val lastResumed = HashMap<String, Long>()
    private var usageReadUntil = 0L

    /**
     * Packages brought to the front in the last hours, newest first (needs Usage access). The whole
     * window is read once; after that only the events since the last read (with a little overlap for
     * events recorded late), so each call is a small system call.
     */
    @Synchronized
    private fun usageHistory(): List<String>? = runCatching {
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val windowStart = now - RECENT_WINDOW_MS
        val from = if (usageReadUntil in windowStart..now) {
            usageReadUntil - USAGE_OVERLAP_MS
        } else {
            lastResumed.clear()
            windowStart
        }
        val events = manager.queryEvents(from, now) ?: return null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.timeStamp > (lastResumed[event.packageName] ?: 0L)
            ) {
                lastResumed[event.packageName] = event.timeStamp
            }
        }
        usageReadUntil = now
        lastResumed.entries.removeIf { it.value < windowStart }
        lastResumed.entries.sortedByDescending { it.value }.map { it.key }
    }.getOrNull()

    /** [android.util.LruCache] with the map-style access the bar uses. */
    private class IconCache(size: Int) : android.util.LruCache<String, Drawable>(size) {
        operator fun set(key: String, value: Drawable) { put(key, value) }
        fun clear() = evictAll()
    }

    companion object {
        /** Room for every pinned and recent icon at once, with plenty to spare (counted in icons). */
        private const val ICON_CACHE_SIZE = 48
        private const val MEDIA_CONTROLS_MS = 6_000L
        private const val MEDIA_HIDE_SETTLE_MS = 2_000L

        /** A dragged icon is drawn see-through. */
        private const val DRAG_ALPHA = 0.3f

        /** The copy that follows the finger is a little see-through. */
        private const val GHOST_ALPHA = 0.8f
        private const val MODE_NONE = 0
        private const val MODE_HANDLES = 1
        private const val MODE_BAR = 2
        private const val MODE_BAR_FIXED = 3
        private const val MODE_MINI = 4

        private const val ACTION_NONE = 0
        private const val ACTION_OPEN = 1
        private const val ACTION_SPLIT = 2
        private const val ACTION_FLOAT = 3
        private const val ACTION_SPLIT_LEFT = 4
        private const val ACTION_SPLIT_RIGHT = 5
        private const val ACTION_TOP = 6
        private const val ACTION_PEEK = 7
        private const val ACTION_THREE = 8

        private const val MAX_PINNED = 10
        private const val MAX_SHORTCUTS = 5

        // The recents screen of Motorola's desktop taskbar (Ready For), exported without a permission.
        private const val DESKTOP_TASKBAR_PACKAGE = "com.motorola.systemui.desk"
        private const val ACTION_DESKTOP_RECENTS = "com.motorola.taskbar.action.RECENTS"

        // What Android's drag and drop reads from a dragged app or shortcut (as Motorola's taskbar sends it).
        private const val MIME_ACTIVITY = "application/vnd.android.activity"
        private const val EXTRA_DRAG_PENDING_INTENT = "android.intent.extra.PENDING_INTENT"
        private const val EXTRA_DISALLOW_HIT_REGION = "DISALLOW_HIT_REGION"
        private const val LARGE_SCREEN_DP = 600
        private const val AUTO_COLLAPSE_MS = 6_000L
        private const val HANDLE_WIDTH_DP = 64
        private const val HANDLE_HEIGHT_DP = 40
        private const val RECENT_WINDOW_MS = 12L * 60 * 60 * 1000
        private const val USAGE_OVERLAP_MS = 10_000L
        private const val SPLIT_SETTLE_DELAY_MS = 150L
        private const val SPLIT_PARTNER_DELAY_MS = 450L
        private const val SPLIT_FIND_TRIES = 10
        private const val SPLIT_RETRY_MS = 100L

        /** Whether Folark's own home screen (or its app drawer) is in front. */
        @Volatile
        var homeVisible: Boolean = false

        /** Whether the user allowed Folark to read the usage history (for the recent apps). */
        fun hasUsageAccess(context: Context): Boolean = ShellSplit.hasUsageAccess(context)
    }
}
