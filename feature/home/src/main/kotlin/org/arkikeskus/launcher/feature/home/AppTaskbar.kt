package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.model.AppPair
import android.annotation.SuppressLint
import org.arkikeskus.launcher.data.LaunchOrigin
import org.arkikeskus.launcher.data.IconShapes
import android.app.KeyguardManager
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.FolarkRecents
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.DEFAULT_NUDGE_COLOR
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Folark's taskbar on the unfolded display: your pinned apps, a divider, and the apps you used last.
 *
 *  - On Folark's home screen and app drawer it stays on screen (Settings ▸ Taskbar).
 *  - Over other apps, including full-screen apps and videos, it opens with a swipe up from either
 *    bottom corner (two invisible touch zones), or stays open if the user chose that.
 *  - Tap an icon to open the app. Drag it up and to the right for split screen, up and to the left
 *    for a floating window.
 *
 * It lives in application overlay windows when the user allowed "Display over other apps" (hosted by
 * [TaskbarHost]), and otherwise in accessibility overlay windows owned by [LockAccessibilityService].
 * It draws its own views and starts apps; it reads nothing from the screen.
 * Recent apps come from Android's usage history when the user allowed Usage access, otherwise from
 * the apps opened through Folark.
 */
internal class AppTaskbar(
    internal val context: Context,
    /** The window type of every window of the bar: an ordinary application overlay when Folark may
     *  display over other apps (see [TaskbarHost]), else the accessibility service's overlay. */
    internal val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
) {

    /** Application overlays are untrusted: Android 12+ blocks touches through a NOT_TOUCHABLE one
     *  unless it is at most this opaque (InputManager's maximum obscuring opacity). */
    internal val passThroughAlpha: Float? =
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) MAX_OBSCURING_ALPHA else null


    internal val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val handler = Handler(Looper.getMainLooper())
    // Read each time: the cover and inner displays need not share a density.
    internal fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    internal fun dpf(value: Float): Float = value * context.resources.displayMetrics.density

    internal var setup: TaskbarSetup? = null
    internal var allApps: List<AppItem> = emptyList()
    internal var recents: List<AppItem> = emptyList()

    /** Two apps sharing a split screen, shown as one tile among the recent apps. */
    internal data class OpenPair(val apps: List<AppItem>, val taskIds: List<Int>)
    internal var openPair: OpenPair? = null
    /** [openPair] as the last background read found it (applied with the recent apps). */
    @Volatile internal var nextOpenPair: OpenPair? = null

    /** Writes the pinned apps (pin, unpin, reorder from the bar or a drop from the drawer). */
    internal var settingsRepo: org.arkikeskus.launcher.data.SettingsRepository? = null

    /**
     * Apps the hide button was tapped in: there the bar stays as handles (a handle still opens it),
     * remembered across restarts until "Show here again" is tapped in that app.
     */
    internal var hiddenHere: Set<String> =
        runCatching { prefs().getStringSet(PREF_HIDDEN_HERE, null)?.toSet() }.getOrNull().orEmpty()
        private set

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun setHiddenHere(packageName: String, hidden: Boolean) {
        val next = if (hidden) hiddenHere + packageName else hiddenHere - packageName
        if (next == hiddenHere) return
        hiddenHere = next
        runCatching { prefs().edit().putStringSet(PREF_HIDDEN_HERE, next).apply() }
        setup?.let { updateWatching(it) }
    }

    /** Tells the hosts whether the app in front must be followed (see [TaskbarVisibility.needsForeground]). */
    private fun updateWatching(s: TaskbarSetup) {
        val needForeground = TaskbarVisibility.needsForeground(
            s.hiddenApps, s.alwaysApps, s.autoHideApps, s.miniApps, hiddenHere, s.keepOpen,
            followsOpenApps = s.recentCount > 0 || s.runningDots,
        )
        LockAccessibilityService.watchApps(needForeground || s.replaceRecents)
        // Without the accessibility service, the app in front comes from the usage history.
        TaskbarHost.watchApps(context, needForeground && s.enabled)
    }
    /** Packages open now (Android's task list), for the taskbar's running dots and open-apps mode. */
    @Volatile internal var runningPackages: Set<String> = emptySet()
    /** How many open windows each package has now (split pairs, pop-ups count separately). */
    @Volatile internal var runningCounts: Map<String, Int> = emptyMap()
    internal var shownRunning: Set<String> = emptySet()
    internal var shownCounts: Map<String, Int> = emptyMap()
    internal var screenOn = true
    private val shown = mutableListOf<View>()
    internal var shownMode = MODE_NONE

    /** An invisible 1-pixel window that hears whether Android's bars are shown: when an app goes
     *  full screen (video, games) they are hidden, and the kept-open bar steps aside. */
    private var probe: View? = null
    private var fullScreen = false
    /** The keyboard is up in the app in front (as far as Android tells this window). */
    private var keyboardUp = false
    internal var expanded = false

    /** The hide button was tapped: stay as handles (even for a kept-open bar) until a handle reopens it. */
    private var manuallyHidden = false
    internal var recentsJob: Job? = null
    internal val collapse = Runnable { expanded = false; mediaControlsOpen = false; refresh() }

    /** What is playing, for the media icon; a change redraws the bar if it is showing. */
    internal val media = TaskbarMedia(context) { onMediaChanged() }
    internal var mediaControlsOpen = false

    /** The bar last drawn with a media icon: a change to a player it doesn't show changes nothing. */
    private var mediaWasShown = false

    private fun onMediaChanged() {
        val shownNow = mediaShown(setup)
        if (!shownNow) mediaControlsOpen = false
        val barUp = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED
        refresh(rebuild = barUp && (shownNow || mediaWasShown))
        mediaWasShown = shownNow
    }

    internal val closeMediaControls = Runnable {
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

    internal var mediaHiddenFor: String? = null
    internal var mediaHiddenAt = 0L
    internal var lastMediaTapAt = 0L
    internal val mediaSingleTap = Runnable { toggleMediaCard() }

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

    internal var repository: AppRepository? = null
    internal var split: SplitCoordinator? = null
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
                    TaskbarHost.checkNow()
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
        settingsRepo = settingsRepository
        // A drop from the drawer or home onto the bar pins the app (while the bar stays on home).
        org.arkikeskus.launcher.ui.TaskbarDropTarget.onPin = { app -> pin(app) }
        // Recents: while a card is dragged the bar comes up as a drop target, then goes again.
        org.arkikeskus.launcher.ui.TaskbarDropTarget.onDragging = { dragging ->
            handler.post {
                if (dragging) {
                    if (shownMode != MODE_BAR && shownMode != MODE_BAR_FIXED) {
                        expanded = true
                        handler.removeCallbacks(collapse)
                        refresh(rebuild = true)
                    }
                } else if (expanded && !homeVisible) {
                    handler.removeCallbacks(collapse)
                    handler.postDelayed(collapse, AUTO_COLLAPSE_MS / 2)
                }
            }
        }
        val appRepository = entry.appRepository()
        repository = appRepository
        split = entry.splitCoordinator()
        // "Undo split" for a few seconds after a split forms (any way: the taskbar, Recents, the picker).
        scope.launch { split?.undoOffer?.collect { offer -> if (offer != null) showUndo() else hideUndo() } }
        // "Open with …" for a few seconds after an app you often pair is opened (off by default).
        scope.launch { split?.suggestion?.collect { offer -> if (offer != null) showSuggestion(offer) else hideSuggestion() } }
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
                    launchGlow = s.taskbar.launchGlow,
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
                    zones = s.recents.dropZones,
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
                    coverBar = s.taskbar.coverBar,
                    coverCount = s.taskbar.coverCount,
                    ratioMenu = s.recents.splitRatioMenu,
                    notificationSplit = s.recents.notificationSplit,
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
                updateWatching(next)
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
        org.arkikeskus.launcher.ui.TaskbarDropTarget.clear()
        hideUndo()
        hideSuggestion()
        scope.cancel()
        media.stop()
        handler.removeCallbacksAndMessages(null)
        runCatching { context.unregisterReceiver(screenReceiver) }
        remove()
        ensureProbe(false)
    }

    // ------------------------------------------------------------------ what is on screen

    internal fun largeScreen(): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP

    /**
     * The setup for the screen in use: the full one on the inner screen; on the cover screen the
     * slim bar when it is switched on (Settings ▸ Taskbar ▸ Cover screen), else none.
     */
    internal fun current(): TaskbarSetup? {
        val s = setup ?: return null
        if (largeScreen()) return s
        return if (s.coverBar) s.forCover() else null
    }

    /**
     * The cover screen's bar: only apps, at most [TaskbarSetup.coverCount] of them (pinned first,
     * then recent ones), smaller, never kept open and never on home: a corner swipe brings it up.
     */
    private fun TaskbarSetup.forCover(): TaskbarSetup {
        val pins = pinned.take(coverCount)
        return copy(
            pinned = pins,
            recentCount = (coverCount - pins.size).coerceAtLeast(0),
            iconSizeDp = iconSizeDp.coerceAtMost(COVER_ICON_DP),
            spacingDp = spacingDp.coerceAtMost(COVER_SPACING_DP),
            onHome = false,
            keepOpen = false,
            hideButton = false,
            mediaEnabled = false,
            liveEnabled = false,
            pairIds = emptyList(),
            pairs = emptyList(),
            splitControls = false,
            alwaysApps = emptySet(),
            autoHideApps = emptySet(),
            miniApps = emptySet(),
        )
    }

    /** Re-evaluates whether the taskbar belongs on screen, and in which form. */
    fun refresh(rebuild: Boolean = false) {
        // Null on the cover screen unless its slim bar is on (see [current]).
        val s = current()
        // The probe also hears the keyboard, so a kept-open bar never sits on top of it.
        ensureProbe(s != null && s.enabled && (s.keepOpen || s.alwaysApps.isNotEmpty() || s.autoHideApps.isNotEmpty() || s.miniApps.isNotEmpty()) && screenOn)
        if (s == null) {
            expanded = false
            remove()
            TaskbarSpace.set(0)
            return
        }
        val facts = TaskbarVisibility.Facts(
            enabled = s.enabled,
            screenOn = screenOn,
            keyguardLocked = keyguard?.isKeyguardLocked == true,
            hasContent = s.pinned.isNotEmpty() || s.pairs.isNotEmpty() || recents.isNotEmpty() || openPair != null ||
                mediaShown(s) || (s.liveEnabled && live.isNotEmpty()),
            homeVisible = homeVisible,
            keyboardOnHome = TaskbarSpace.keyboardOnHome.value,
            keyboardUp = keyboardUp,
            manuallyHidden = manuallyHidden || (!homeVisible && foreground != null && foreground in hiddenHere),
            expanded = expanded,
            onHome = s.onHome,
            keepOpen = s.keepOpen,
            hideFullScreen = s.hideFullScreen,
            fullScreen = fullScreen,
            foreground = foreground,
            hiddenApps = s.hiddenApps,
            alwaysApps = s.alwaysApps,
            autoHideApps = s.autoHideApps,
            miniApps = s.miniApps,
        )
        if (!TaskbarVisibility.active(facts)) {
            expanded = false
            remove()
            TaskbarSpace.set(0)
            return
        }
        val mode = when (TaskbarVisibility.mode(facts)) {
            TaskbarVisibility.Mode.NONE -> MODE_NONE
            TaskbarVisibility.Mode.HANDLES -> MODE_HANDLES
            TaskbarVisibility.Mode.BAR -> MODE_BAR
            TaskbarVisibility.Mode.BAR_FIXED -> MODE_BAR_FIXED
            TaskbarVisibility.Mode.MINI -> MODE_MINI
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
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Nothing to see in it, and as an application overlay it must not block touches below.
            if (passThroughAlpha != null) alpha = 0f
        }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) probe = view
    }

    /** The app in front (followed only while something needs it, see [updateWatching]), null when unknown. */
    internal var foreground: String? = null

    /**
     * Another app came to the front: a bar on screen reads its recent apps and running dots again
     * (rebuilt only if they changed); otherwise the bar is re-decided when a per-app rule or a
     * "hidden here" is involved.
     */
    fun onForegroundApp(packageName: String) {
        val s = setup ?: return
        if (packageName == foreground) return
        val before = foreground
        foreground = packageName
        fun ruled(p: String?) = p != null &&
            (p in s.hiddenApps || p in s.alwaysApps || p in s.autoHideApps || p in s.miniApps || p in hiddenHere)
        val barUp = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED
        when {
            barUp && !homeVisible && (s.recentCount > 0 || s.runningDots) -> loadRecents(rebuild = true, force = false)
            ruled(before) || ruled(packageName) -> refresh()
        }
    }

    /** The home screen came to the front or left it. */
    fun onHomeVisibilityChanged() {
        expanded = false
        loadRecents(rebuild = true, force = false)
    }

    private var lastNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** A fold, unfold, rotation or dark/light switch (from its host). */
    fun onConfigurationChanged() {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val nightChanged = night != lastNightMode
        lastNightMode = night
        // Icons are tinted for dark or light: drawn again.
        if (nightChanged) iconCache.clear()
        loadRecents(rebuild = true, force = nightChanged)
    }

    private fun remove() {
        org.arkikeskus.launcher.ui.TaskbarDropTarget.bounds = null
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
    internal fun params(gravity: Int, x: Int, y: Int, watchOutside: Boolean = false) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
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
    /**
     * The system's accent colour (Material You) in one of three shades, the same everywhere on the
     * bar: [ACCENT_LIGHT] for highlights and chips, [ACCENT_MID] for rims, [ACCENT_DEEP] for fills.
     * Before Android 12 (no system palette) a fixed blue of the same lightness.
     */
    internal fun accent(shade: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(
                when (shade) {
                    ACCENT_DEEP -> android.R.color.system_accent1_800
                    ACCENT_MID -> android.R.color.system_accent1_300
                    else -> android.R.color.system_accent1_200
                },
            )
        }
        return when (shade) {
            ACCENT_DEEP -> Color.rgb(30, 50, 90)
            ACCENT_MID -> Color.rgb(90, 160, 255)
            else -> Color.rgb(180, 200, 255)
        }
    }

    internal fun systemBottomInset(): Int = runCatching {
        val insets = windowManager.currentWindowMetrics.windowInsets
        maxOf(
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
            insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
        )
    }.getOrDefault(0)

    /** How far a bottom-anchored overlay is lifted to clear the system's bottom inset (0 when off). */
    private fun TaskbarSetup.insetLift(): Int = if (clearInset) systemBottomInset() else 0

    /**
     * How far the pull handles and the mini pill sit above the bottom edge. An ordinary overlay
     * window cannot take touches where Android's navigation bar / gesture strip is (that window is
     * above it), so there they always clear it; the accessibility overlay can sit right at the edge.
     */
    private fun TaskbarSetup.handleLift(): Int =
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) maxOf(insetLift(), systemBottomInset())
        else insetLift()

    /** Keeps Android's back gesture from taking swipes that start on [view] at a side edge. */
    private fun excludeFromSystemGestures(view: View) {
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, v.width, v.height))
        }
    }

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
            if (side != Gravity.CENTER_HORIZONTAL) excludeFromSystemGestures(zone)
            add(zone, params(Gravity.BOTTOM or side, 0, s.handleLift()))
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
        add(pill, params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(8) + s.handleLift()))
    }

    private fun open() {
        expanded = true
        manuallyHidden = false
        // Which app the bar opened over (per-app rules, "hidden here"), right away.
        TaskbarHost.checkNow()
        loadRecents(rebuild = true, force = false)
    }

    /** The hide button: animate the bar away, then leave only the pull handles. */
    private fun hideToHandles() {
        expanded = false
        if (homeVisible) {
            manuallyHidden = true
        } else {
            // Over an app: remembered for that app. Which app it is is read now (it may not have
            // been followed until something needed it).
            val known = foreground
            if (known != null) setHiddenHere(known, true) else manuallyHidden = true
            if (known == null) scope.launch {
                val app = withContext(Dispatchers.IO) { TaskbarHost.foregroundNow(context) } ?: return@launch
                foreground = app
                manuallyHidden = false
                setHiddenHere(app, true)
                refresh()
            }
        }
        handler.removeCallbacks(collapse)
        val view = barColumn
        val anim = setup?.taskbarAnim
        if (view != null && anim != null) animateBarOut(view, anim) { refresh() } else refresh()
    }

    /** Plays the chosen show animation on the bar [view] as it appears. */
    private fun animateBarIn(view: View, anim: String) {
        val dist = dpf(90f)
        view.animate().cancel()
        view.translationY = 0f; view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f; view.rotationX = 0f
        val a = view.animate()
        when (anim) {
            LauncherSettings.TASKBAR_ANIM_FADE -> { view.alpha = 0f; a.alpha(1f).setDuration(220L) }
            LauncherSettings.TASKBAR_ANIM_SCALE -> { view.scaleX = 0.82f; view.scaleY = 0.82f; view.alpha = 0f; a.scaleX(1f).scaleY(1f).alpha(1f).setDuration(240L).interpolator = DecelerateInterpolator() }
            LauncherSettings.TASKBAR_ANIM_RISE -> { view.translationY = dist; view.alpha = 0f; a.translationY(0f).alpha(1f).setDuration(260L).interpolator = DecelerateInterpolator() }
            LauncherSettings.TASKBAR_ANIM_BOUNCE -> { view.translationY = dist; a.translationY(0f).setDuration(430L).interpolator = OvershootInterpolator(2.4f) }
            LauncherSettings.TASKBAR_ANIM_FLIP -> {
                // Tips up about its bottom edge.
                view.pivotY = view.height.toFloat().takeIf { it > 0f } ?: dpf(64f)
                view.pivotX = view.width / 2f
                view.cameraDistance = 12_000f * context.resources.displayMetrics.density
                view.rotationX = 90f; view.alpha = 0f
                a.rotationX(0f).alpha(1f).setDuration(320L).interpolator = DecelerateInterpolator(1.6f)
            }
            LauncherSettings.TASKBAR_ANIM_UNFOLD -> {
                view.pivotX = view.width / 2f
                view.scaleX = 0.08f; view.alpha = 0f
                a.scaleX(1f).alpha(1f).setDuration(340L).interpolator = DecelerateInterpolator(2f)
            }
            LauncherSettings.TASKBAR_ANIM_POP -> {
                view.scaleX = 0.6f; view.scaleY = 0.6f; view.alpha = 0f
                a.scaleX(1f).scaleY(1f).alpha(1f).setDuration(380L).interpolator = OvershootInterpolator(1.8f)
            }
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
            LauncherSettings.TASKBAR_ANIM_FLIP -> {
                view.pivotY = view.height.toFloat()
                view.cameraDistance = 12_000f * context.resources.displayMetrics.density
                a.rotationX(90f).alpha(0f)
            }
            LauncherSettings.TASKBAR_ANIM_UNFOLD -> a.scaleX(0.08f).alpha(0f)
            LauncherSettings.TASKBAR_ANIM_POP -> a.scaleX(0.6f).scaleY(0.6f).alpha(0f)
            else -> a.translationY(dist) // SLIDE, BOUNCE
        }
        a.withEndAction { onEnd() }.start()
    }

    // ------------------------------------------------------------------ the bar

    @SuppressLint("ClickableViewAccessibility")
    private fun showBar(s: TaskbarSetup, fixed: Boolean, animateIn: Boolean = false) {
        val accent = accent(ACCENT_LIGHT)
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
        fun addIcon(app: AppItem, pinnedIndex: Int = -1) {
            val view = iconView(app, s, size, hint, fixed, pinnedIndex)
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
        s.pinned.forEachIndexed { i, app -> addIcon(app, i) }
        s.pairs.forEach(::addPairIcon)
        if (showMedia && s.mediaPosition == LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE) addMedia()
        val recentApps = recents.filter { r -> s.pinned.none { it.key == r.key } }
        val livePair = openPair
        if ((s.pinned.isNotEmpty() || s.pairs.isNotEmpty()) && (recentApps.isNotEmpty() || livePair != null)) {
            val divider = View(context).apply {
                setBackgroundColor(if (lightTheme) Color.argb(90, 0, 0, 0) else Color.argb(110, 255, 255, 255))
            }
            row.addView(divider, LinearLayout.LayoutParams(dp(1), (size * 0.6f).toInt()).apply {
                marginStart = dp(6) + dp(s.spacingDp) / 2
                marginEnd = dp(6) + dp(s.spacingDp) / 2
            })
        }
        // The split screen that is open (or behind home) as one tile: a tap brings it back as a split.
        if (livePair != null) {
            val description = context.getString(R.string.taskbar_open_pair, livePair.apps[0].label, livePair.apps[1].label)
            row.addView(pairTile(livePair.apps.map { it.key }, description, s, size) {
                val a = livePair.apps[0]
                val b = livePair.apps[1]
                scope.launch {
                    split?.restore(
                        org.arkikeskus.launcher.data.SplitApp(a.packageName, a, livePair.taskIds[0]),
                        org.arkikeskus.launcher.data.SplitApp(b.packageName, b, livePair.taskIds[1]),
                    )
                }
                if (!fixed) {
                    expanded = false
                    refresh()
                }
            }, LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(s.spacingDp) / 2
                marginEnd = dp(s.spacingDp) / 2
            })
        }
        recentApps.forEach { addIcon(it) }
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
                    onHold = if (s.quickSwitch) ({ LockAccessibilityService.quickSwitch() }) else null),
                    LinearLayout.LayoutParams(size, size).apply {
                        marginStart = dp(s.spacingDp) / 2
                        marginEnd = dp(s.spacingDp) / 2
                    })
            }
            // A small hide button next to Recents: tucks the bar away, leaving just the pull handles.
            if (s.hideButton) {
                // In an app it was hidden in before, the same button brings the bar back there.
                val here = foreground.takeIf { !homeVisible && it != null && it in hiddenHere }
                row.addView(barButton(R.drawable.ic_taskbar_hide, if (here != null) R.string.taskbar_show_here else R.string.taskbar_hide, s, size, fixed,
                    onTap = {
                        if (here != null) {
                            setHiddenHere(here, false)
                            expanded = false
                            refresh(rebuild = true)
                        } else {
                            hideToHandles()
                        }
                    }, onHold = null).apply { if (here != null) rotation = 180f },
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
        // Not while the bar is kept open over an app, nor in battery saver: the compositor would
        // blur what is under it again for every frame that app draws (video, maps, games). The
        // bar opened for a moment, or over home, keeps the glass.
        val blurWorthIt = (homeVisible || !fixed) &&
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true
        if (blurWorthIt && s.theme == LauncherSettings.TASKBAR_THEME_GLASS && s.glassBlur > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            barLayout.blurBehindRadius = dp(s.glassBlur)
            barLayout.flags = barLayout.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }
        add(column, barLayout)
        barRow = row
        barColumn = column
        if (animateIn) animateBarIn(column, s.taskbarAnim)
        // Tell home and the drawer where the bar's top edge is (and drags where to drop to pin),
        // whenever it is laid out again.
        row.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> publishSpace(v) }
        // A drag over a split screen is handled differently when a third app isn't wanted.
        if (!fixed || !homeVisible) checkSplitForThirdApp(s)
        if (!fixed) {
            handler.removeCallbacks(collapse)
            handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
    }

    /** The room from the bar's top edge to the bottom of the screen, for home and the drawer. */
    private fun publishSpace(barRow: View) {
        if (!barRow.isAttachedToWindow) return
        val location = IntArray(2)
        barRow.getLocationOnScreen(location)
        // Where an app (or a Recents card) can be dropped to pin it: wherever the bar is up.
        org.arkikeskus.launcher.ui.TaskbarDropTarget.bounds = android.graphics.Rect(
            location[0], location[1], location[0] + barRow.width, location[1] + barRow.height,
        )
        if (!homeVisible || shownMode != MODE_BAR_FIXED) return
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        TaskbarSpace.set(screenHeight - location[1] + dp(SPACE_GAP_DP))
    }

    /** The bar's backdrop for the chosen theme; null for the see-through one. */
    private fun barBackground(s: TaskbarSetup): Drawable? {
        val alpha = (s.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (s.theme == LauncherSettings.TASKBAR_THEME_GLASS) return glassBackground(alpha)
        if (s.theme == LauncherSettings.TASKBAR_THEME_NEON) return neonBackground(alpha)
        val (fill, rim) = when (s.theme) {
            LauncherSettings.TASKBAR_THEME_CLEAR -> return null
            LauncherSettings.TASKBAR_THEME_AMOLED -> Color.argb(maxOf(alpha, 235), 0, 0, 0) to Color.argb(28, 255, 255, 255)
            LauncherSettings.TASKBAR_THEME_FROSTED -> Color.argb((alpha * 0.55f).toInt(), 40, 42, 48) to Color.argb(70, 255, 255, 255)
            LauncherSettings.TASKBAR_THEME_LIGHT -> Color.argb(alpha, 242, 243, 246) to Color.argb(40, 0, 0, 0)
            LauncherSettings.TASKBAR_THEME_ACCENT -> {
                val base = accent(ACCENT_DEEP)
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

    /**
     * Neon: near-black, with a rim in the accent colour and a soft glow of it inside the edge
     * (two layers: the glow is a wider, fainter stroke under the sharp one).
     */
    private fun neonBackground(alpha: Int): Drawable {
        val accent = accent(ACCENT_MID)
        fun withAlpha(a: Int) = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))
        val glow = GradientDrawable().apply {
            cornerRadius = dpf(28f)
            setColor(Color.argb(maxOf(alpha, 200), 8, 9, 14))
            setStroke(dp(5), withAlpha(55))
        }
        val rim = GradientDrawable().apply {
            cornerRadius = dpf(28f)
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), withAlpha(230))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(glow, rim))
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
    private fun pairView(pair: AppPair, s: TaskbarSetup, size: Int): View =
        pairTile(listOfNotNull(pair.app1Key, pair.app2Key, pair.app3Key.takeIf { pair.hasThirdApp }), pair.name, s, size) {
            repository?.launchAppPair(pair)
        }

    /** Two or three apps' icons together on one rounded tile; a tap runs [onClick]. */
    private fun pairTile(keys: List<String>, description: String, s: TaskbarSetup, size: Int, onClick: () -> Unit): View {
        val frame = FrameLayout(context).apply {
            contentDescription = description
            background = GradientDrawable().apply {
                cornerRadius = size * 0.28f
                val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
                setColor(if (light) Color.argb(36, 0, 0, 0) else Color.argb(44, 255, 255, 255))
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                playLaunchGlow(this)
                onClick()
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

    /** [pinnedIndex]: the icon's place among the pinned apps (-1: a recent app), for reordering. */
    private fun iconView(app: AppItem, s: TaskbarSetup, size: Int, hint: TextView, fixed: Boolean, pinnedIndex: Int = -1): ImageView {
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
        // A pinned icon dragged along the bar (not up off it) moves among the pinned apps.
        var reordering = false
        var reorderSlot = 0
        val slotWidth = (size + dp(s.spacingDp)).toFloat()
        addIconAccessibility(
            icon, app,
            onSplit = { openSplit(app) },
            onFloat = { repository?.launchFloating(app) },
            onShortcuts = { showShortcuts(app, icon, s, fixed) },
        )
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
                    reordering = false
                    reorderSlot = 0
                    handler.removeCallbacks(collapse)
                    handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (handedOver || held) return@setOnTouchListener true
                    val dx = event.rawX - downX
                    val up = downY - event.rawY
                    if (!moved && (abs(up) > slop || abs(dx) > slop)) {
                        moved = true
                        handler.removeCallbacks(hold)
                        reordering = pinnedIndex >= 0 && abs(dx) > abs(up) * 2 && abs(up) < dp(16)
                    }
                    if (reordering) {
                        if (up > armDistance * 0.6f) {
                            // Lifted off the bar after all: an ordinary drag (split, float) from here.
                            reordering = false
                            view.translationX = 0f
                        } else {
                            view.translationX = dx
                            val slot = (dx / slotWidth).roundToInt()
                            if (slot != reorderSlot) {
                                reorderSlot = slot
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            return@setOnTouchListener true
                        }
                    }
                    if (moved) {
                        ghostStart(view as ImageView, hint)
                        ghostMove(event.rawX, event.rawY, hint)
                        // Edge-snap: the edge the finger is over. Otherwise up-right = split, up-left = float.
                        val next = if (s.edgeSnap) {
                            edgeAction(event.rawX, event.rawY, up, s.zones)
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
                                ghostStart(view, hint)
                                ghostMove(event.rawX, event.rawY, hint)
                            }
                        }
                        if (next != action) {
                            // A tick on entering a drop zone (not on leaving one).
                            if (next != ACTION_NONE && s.zones.haptics) {
                                view.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= 34) android.view.HapticFeedbackConstants.SEGMENT_TICK
                                    else android.view.HapticFeedbackConstants.CLOCK_TICK,
                                )
                            }
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
                    if (reordering) {
                        reordering = false
                        if (reorderSlot != 0) movePinned(app, pinnedIndex + reorderSlot)
                        if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
                        return@setOnTouchListener true
                    }
                    val chosen = if (!moved) ACTION_OPEN else action
                    if (chosen != ACTION_OPEN && chosen != ACTION_NONE) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    }
                    when (chosen) {
                        ACTION_OPEN -> {
                            LaunchOrigin.view(view)
                            playLaunchGlow(view)
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
                        // A split showing: the app joins it; none: the picker chooses two more apps.
                        ACTION_THREE -> if (splitOpen) addThird(app) else openSplitPick(app, right = false, three = true)
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

    /** Whether an open split screen was seen when the bar last opened (only checked if a third app isn't wanted). */
    @Volatile
    internal var splitOpen = false

    private fun checkSplitForThirdApp(s: TaskbarSetup) {
        splitOpen = false
        if (s.threeApp || !s.systemDrag || homeVisible || !ShellSplit.available(context)) return
        scope.launch {
            splitOpen = withContext(Dispatchers.IO) { ShellSplit.splitTasks(context).size >= 2 }
        }
    }

    internal var barRow: View? = null
    private var barColumn: View? = null
    internal var systemDragging = false

    internal var shortcutWindow: View? = null

    /** How long the opened-up live pill stays over the bar. */
    internal val LIVE_PANEL_IDLE_MS = 10_000L

    /** Puts [app]'s launcher icon into [view], from the cache or loaded in the background. */
    internal fun setIconInto(view: ImageView, app: AppItem, s: TaskbarSetup) {
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

    internal var ghostWindow: FrameLayout? = null
    internal var ghostIcon: ImageView? = null
    internal val ghostOrigin = IntArray(2)

    internal var splitChip: TextView? = null
    internal var floatChip: TextView? = null

    internal var leftZone: FrameLayout? = null
    internal var rightZone: FrameLayout? = null
    internal var topZone: FrameLayout? = null
    internal var bottomZone: FrameLayout? = null
    internal var peekZone: FrameLayout? = null
    internal var threeZone: FrameLayout? = null

    private fun loadIcon(app: AppItem, s: TaskbarSetup): Drawable? {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val icon = runCatching {
            source?.loadIcon(app, s.themedIcons, dark, s.iconPack, s.customIcons[app.key].orEmpty())
        }.getOrNull() ?: runCatching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
        return icon?.let { IconShapes.apply(it, s.iconShape) }
    }

    internal var suggestionWindow: View? = null
    internal val suggestionTimeout = Runnable { split?.dismissSuggestion(passedOver = true) }

    internal var undoWindow: View? = null
    internal val undoTimeout = Runnable { split?.dismissUndo() }

    internal var pendingForce = false

    /** When each package last came to the front within the window, kept up to date incrementally. */
    internal val lastResumed = HashMap<String, Long>()
    internal var usageReadUntil = 0L

    /** [android.util.LruCache] with the map-style access the bar uses. */
    private class IconCache(size: Int) : android.util.LruCache<String, Drawable>(size) {
        operator fun set(key: String, value: Drawable) { put(key, value) }
        fun clear() = evictAll()
    }

    companion object {
        /** Room for every pinned and recent icon at once, with plenty to spare (counted in icons). */
        private const val ICON_CACHE_SIZE = 48
        internal const val MEDIA_CONTROLS_MS = 6_000L
        private const val MEDIA_HIDE_SETTLE_MS = 2_000L

        /** A dragged icon is drawn see-through. */
        internal const val DRAG_ALPHA = 0.3f

        /** The copy that follows the finger is a little see-through. */
        internal const val GHOST_ALPHA = 0.8f
        private const val MODE_NONE = 0
        private const val MODE_HANDLES = 1
        internal const val MODE_BAR = 2
        internal const val MODE_BAR_FIXED = 3
        private const val MODE_MINI = 4

        internal const val ACTION_NONE = 0
        private const val ACTION_OPEN = 1
        internal const val ACTION_SPLIT = 2
        internal const val ACTION_FLOAT = 3
        internal const val ACTION_SPLIT_LEFT = 4
        internal const val ACTION_SPLIT_RIGHT = 5
        internal const val ACTION_TOP = 6
        internal const val ACTION_PEEK = 7
        internal const val ACTION_THREE = 8

        internal const val MAX_PINNED = 10
        private const val PREFS_NAME = "taskbar"
        private const val PREF_HIDDEN_HERE = "hidden_here"
        internal const val MAX_SHORTCUTS = 5

        // The recents screen of Motorola's desktop taskbar (Ready For), exported without a permission.
        private const val DESKTOP_TASKBAR_PACKAGE = "com.motorola.systemui.desk"
        private const val ACTION_DESKTOP_RECENTS = "com.motorola.taskbar.action.RECENTS"

        // What Android's drag and drop reads from a dragged app or shortcut (as Motorola's taskbar sends it).
        internal const val MIME_ACTIVITY = "application/vnd.android.activity"
        internal const val EXTRA_DRAG_PENDING_INTENT = "android.intent.extra.PENDING_INTENT"
        internal const val EXTRA_DISALLOW_HIT_REGION = "DISALLOW_HIT_REGION"
        private const val LARGE_SCREEN_DP = 600
        /** The cover screen's slim bar: its largest icons and gaps (dp). */
        private const val COVER_ICON_DP = 44
        private const val COVER_SPACING_DP = 6
        /** How long "Open with …" stays. */
        internal const val SUGGEST_MS = 4_000L
        /** How long "Undo split" stays. */
        internal const val UNDO_MS = 5_000L
        internal const val UNDO_ABOVE_BAR_DP = 84
        internal const val AUTO_COLLAPSE_MS = 6_000L
        private const val HANDLE_WIDTH_DP = 64
        private const val HANDLE_HEIGHT_DP = 40
        internal const val RECENT_WINDOW_MS = 12L * 60 * 60 * 1000
        internal const val USAGE_OVERLAP_MS = 10_000L

        /** InputManager.getMaximumObscuringOpacityForTouch()'s default (Android 12+). */
        private const val MAX_OBSCURING_ALPHA = 0.8f
        internal const val ACCENT_LIGHT = 200
        internal const val ACCENT_MID = 300
        internal const val ACCENT_DEEP = 800

        /** Whether Folark's own home screen (or its app drawer) is in front. */
        @Volatile
        var homeVisible: Boolean = false

        /** Whether the user allowed Folark to read the usage history (for the recent apps). */
        fun hasUsageAccess(context: Context): Boolean = ShellSplit.hasUsageAccess(context)
    }
}
