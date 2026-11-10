package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

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
import org.arkikeskus.launcher.model.catching

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
    internal val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val handler = Handler(Looper.getMainLooper())

    /** The launch glow's window, kept a moment for the next launch (see playLaunchGlow). */
    internal var glowView: View? = null
    internal var glowAnimator: android.animation.ValueAnimator? = null
    internal val glowRelease = Runnable { releaseGlow() }
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
        catching { prefs().getStringSet(PREF_HIDDEN_HERE, null)?.toSet() }.getOrNull().orEmpty()
        private set

    internal fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun setHiddenHere(packageName: String, hidden: Boolean) {
        val next = if (hidden) hiddenHere + packageName else hiddenHere - packageName
        if (next == hiddenHere) return
        hiddenHere = next
        catching { prefs().edit().putStringSet(PREF_HIDDEN_HERE, next).apply() }
        setup?.let { updateWatching(it) }
    }

    /** Tells the hosts whether the app in front must be followed (see [TaskbarVisibility.needsForeground]). */
    internal fun updateWatching(s: TaskbarSetup) {
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
    internal val shown = mutableListOf<View>()
    internal var shownMode = MODE_NONE

    /** Whether the bar itself is on screen now (not just the handles): while it is not, nothing
     *  shows the running dots or recents, so the app watch may back off for longer (checkNow() —
     *  screen on, unfold, a handle touch — looks at once when something shows the bar again). */
    internal val barShown: Boolean
        get() = shownMode == MODE_BAR || shownMode == MODE_BAR_FIXED

    /** An invisible 1-pixel window that hears whether Android's bars are shown: when an app goes
     *  full screen (video, games) they are hidden, and the kept-open bar steps aside. */
    internal var probe: View? = null
    internal var fullScreen = false
    /** Whether the handles shown now are the slim ones (over a full-screen video). */
    private var shownSlim = false
    /** The keyboard is up in the app in front (as far as Android tells this window). */
    internal var keyboardUp = false
    internal var expanded = false

    /** The hide button was tapped: stay as handles (even for a kept-open bar) until a handle reopens it. */
    internal var manuallyHidden = false
    internal var recentsJob: Job? = null
    internal val collapse = Runnable { expanded = false; mediaControlsOpen = false; refresh() }

    /** What is playing, for the media icon; a change redraws the bar if it is showing. */
    internal val media = TaskbarMedia(context) { onMediaChanged() }
    internal var mediaControlsOpen = false

    /** The bar last drawn with a media icon: a change to a player it doesn't show changes nothing. */
    internal var mediaWasShown = false

    internal fun onMediaChanged() {
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

    internal fun mediaShown(s: TaskbarSetup?): Boolean {
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
    internal var live: List<LiveActivity> = emptyList()
    /** All of them, before leaving out the ones Android shows as status-bar chips. */
    internal var liveAll: List<LiveActivity> = emptyList()

    /** The bar sits over apps, where Android's status bar (and its chips) shows. */
    internal fun liveShown(all: List<LiveActivity>): List<LiveActivity> =
        if (setup?.liveSkipChips == true) all.filterNot { it.systemChip } else all
    internal var livePill: TaskbarLivePill? = null
    internal var liveIndex = 0
    internal val SPACE_GAP_DP = 6

    /** The app icons on the bar now, by badgeKey, for the notification nudge. */
    internal val iconViews = HashMap<String, View>()
    /** Notification counts per badgeKey, for the icons' badges (AppTaskbarIcons.applyBadges). */
    @Volatile internal var badgeCounts: Map<String, Int> = emptyMap()
    /** Open apps' tasks (package → task id, user id), for the bar's previews (AppTaskbarPreviews.kt). */
    @Volatile internal var openTaskOf: Map<String, Pair<Int, Int>> = emptyMap()
    /** Task screenshots for the bar's previews, capped in bytes: about four full-screen pictures,
     *  so a large screen keeps a few more than a small one and nothing grows past that. */
    internal val previewCache = object : android.util.LruCache<Int, android.graphics.Bitmap>(1) {
        override fun sizeOf(key: Int, value: android.graphics.Bitmap): Int = value.allocationByteCount
    }.also { cache ->
        val dm = context.resources.displayMetrics
        cache.resize(maxOf(1, dm.widthPixels * dm.heightPixels * 2 * 4))
    }
    internal val previewTakenAt = HashMap<Int, Long>()
    internal var previewJob: Job? = null

    internal var repository: AppRepository? = null
    internal var split: SplitCoordinator? = null
    internal var source: LauncherAppsSource? = null
    /** Icons of the apps the bar has shown lately; the least recently shown go first when full. */
    internal val iconCache = IconCache(ICON_CACHE_SIZE)
    internal var iconStyle = ""

    internal val screenReceiver = object : BroadcastReceiver() {
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
        val entry = catching {
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
        catching {
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
                combine(settingsRepository.customIcons, settingsRepository.iconShapes) { c, sh -> c to sh },
                entry.appUsageRepository().usage,
            ) { s, favorites, all, (custom, shapes), usage ->
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
                    pinsLocked = s.taskbar.pinsLocked,
                    swipeDownHide = s.taskbar.swipeDownHide,
                    openPreviews = s.taskbar.openPreviews,
                    taskbarAnim = s.taskbar.taskbarAnim,
                    launchGlow = s.taskbar.launchGlow,
                    onHome = s.taskbar.onHome,
                    keepOpen = s.taskbar.keepOpen,
                    themedIcons = s.icons.useThemedIcons,
                    iconPack = s.icons.iconPack,
                    customIcons = custom,
                    iconShapes = shapes,
                    iconShape = s.icons.shape,
                    shapeAllIcons = s.icons.shapeAllIcons,
                    // Apps opened through Folark, newest first: the recent apps without Usage access.
                    launchHistory = usage.entries.sortedByDescending { it.value.lastUsed }.map { it.key }.take(24),
                    mediaEnabled = s.taskbar.mediaEnabled,
                    mediaShowPaused = s.taskbar.mediaShowPaused,
                    mediaPosition = s.taskbar.mediaPosition,
                    dividerStyle = s.taskbar.dividerStyle,
                    dividerLock = s.recents.dividerLock,
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
                    sceneSlots = s.taskbar.sceneSlots,
                    coverBar = s.taskbar.coverBar,
                    coverCount = s.taskbar.coverCount,
                    ratioMenu = s.recents.splitRatioMenu,
                    notificationSplit = s.recents.notificationSplit,
                )
            }.combine(appRepository.appPairs) { setup, saved ->
                setup.copy(pairs = setup.pairIds.mapNotNull { id -> saved.firstOrNull { it.id == id } })
            }.distinctUntilChanged().collect { next ->
                val previous = setup
                val style = "${next.themedIcons}|${next.iconPack}|${next.customIcons.hashCode()}|${next.iconShape}|${next.iconShapes.hashCode()}|${next.shapeAllIcons}"
                if (style != iconStyle) {
                    iconStyle = style
                    iconCache.clear()
                }
                setup = next
                preloadIcons(next)
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
        scope.launch {
            TaskbarSpace.drawerOpen.drop(1).collect { refresh() }
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
        // Notification badges on the bar's icons (pinned and recents alike): the count each app's
        // icon carries, drawn over it next to the running dot. Redrawn only for icons that changed.
        scope.launch {
            entry.notificationBadgeRepository().badges.collect { next ->
                badgeCounts = next
                applyBadges()
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
        releaseGlow()
        handler.removeCallbacksAndMessages(null)
        catching { context.unregisterReceiver(screenReceiver) }
        remove()
        ensureProbe(false)
    }

    // ------------------------------------------------------------------ what is on screen

    internal fun largeScreen(): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP

    /** Whether a bar can show on the display in use (the inner one, or the cover one with its bar). */
    internal fun barPossibleHere(): Boolean = largeScreen() || setup?.coverBar == true

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
    internal fun TaskbarSetup.forCover(): TaskbarSetup {
        val pins = pinned.take(coverCount)
        return copy(
            pinned = pins,
            recentCount = (coverCount - pins.size).coerceAtLeast(0),
            iconSizeDp = iconSizeDp.coerceAtMost(COVER_ICON_DP),
            spacingDp = spacingDp.coerceAtMost(COVER_SPACING_DP),
            onHome = false,
            keepOpen = false,
            hideButton = false,
            openPreviews = false,
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
        // The probe (a 1 px window) tells full screen and the keyboard apart: the kept-open bar and
        // per-app rules need both, and plain handles slim down over a full-screen video.
        ensureProbe(s != null && s.enabled && (s.keepOpen || s.hideFullScreen || s.alwaysApps.isNotEmpty() || s.autoHideApps.isNotEmpty() || s.miniApps.isNotEmpty()) && screenOn)
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
            drawerOpen = TaskbarSpace.drawerOpen.value,
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
        // The handles are drawn slim over a full-screen video: redraw them when that changes.
        val slimHandles = mode == MODE_HANDLES && s.hideFullScreen && fullScreen && !homeVisible
        if (mode == shownMode && !rebuild && slimHandles == shownSlim) return
        shownSlim = slimHandles
        remove()
        when (mode) {
            MODE_BAR, MODE_BAR_FIXED -> showBar(s, fixed = mode == MODE_BAR_FIXED, animateIn = shownMode != MODE_BAR && shownMode != MODE_BAR_FIXED)
            MODE_HANDLES -> showHandles(s)
            MODE_MINI -> showMini(s)
        }
        shownMode = mode
    }

    internal fun ensureProbe(wanted: Boolean) {
        if (!wanted) {
            probe?.let { catching { windowManager.removeView(it) } }
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
        if (catching { windowManager.addView(view, params) }.isSuccess) probe = view
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

    internal var lastNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** A fold, unfold, rotation or dark/light switch (from its host). */
    fun onConfigurationChanged() {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val nightChanged = night != lastNightMode
        lastNightMode = night
        // Icons are tinted for dark or light: the other theme's set (drawn again only the first time).
        iconCache.theme = night.toString()
        loadRecents(rebuild = true, force = nightChanged)
    }

    internal fun remove() {
        org.arkikeskus.launcher.ui.TaskbarDropTarget.bounds = null
        livePill = null
        iconViews.clear()
        shown.forEach { catching { windowManager.removeView(it) } }
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

    internal fun add(view: View, params: WindowManager.LayoutParams) {
        if (catching { windowManager.addView(view, params) }.isSuccess) shown += view
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

    internal fun systemBottomInset(): Int = catching {
        val insets = windowManager.currentWindowMetrics.windowInsets
        maxOf(
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
            insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
        )
    }.getOrDefault(0)

    /** How far a bottom-anchored overlay is lifted to clear the system's bottom inset (0 when off). */
    internal fun TaskbarSetup.insetLift(): Int = if (clearInset) systemBottomInset() else 0

    /**
     * How far the pull handles and the mini pill sit above the bottom edge. An ordinary overlay
     * window cannot take touches where Android's navigation bar / gesture strip is (that window is
     * above it), so there they always clear it; the accessibility overlay can sit right at the edge.
     */
    internal fun TaskbarSetup.handleLift(): Int =
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) maxOf(insetLift(), systemBottomInset())
        else insetLift()

    /** Keeps Android's back gesture from taking swipes that start on [view] at a side edge. */
    internal fun excludeFromSystemGestures(view: View) {
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, v.width, v.height))
        }
    }

    // ------------------------------------------------------------------ recents and split buttons

    /** Motorola's recents screen, or the recents of its desktop taskbar (Ready For) if chosen and present. */
    internal fun openRecents(s: TaskbarSetup) {
        if (s.recentsButton == LauncherSettings.TASKBAR_RECENTS_FOLARK && FolarkRecents.open(context)) return
        if (s.recentsButton == LauncherSettings.TASKBAR_RECENTS_DESKTOP) {
            val intent = Intent(ACTION_DESKTOP_RECENTS)
                .setPackage(DESKTOP_TASKBAR_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (catching { context.startActivity(intent) }.isSuccess) return
            Toast.makeText(context, R.string.taskbar_desktop_recents_missing, Toast.LENGTH_SHORT).show()
        }
        LockAccessibilityService.openRecents()
    }

    /** Whether an open split screen was seen when the bar last opened (only checked if a third app isn't wanted). */
    @Volatile
    internal var splitOpen = false

    internal fun checkSplitForThirdApp(s: TaskbarSetup) {
        splitOpen = false
        if (s.threeApp || !s.systemDrag || homeVisible || !ShellSplit.available(context)) return
        scope.launch {
            splitOpen = withContext(Dispatchers.IO) { ShellSplit.splitTasks(context).size >= 2 }
        }
    }

    internal var barRow: View? = null
    internal var barColumn: View? = null
    internal var systemDragging = false

    internal var shortcutWindow: View? = null

    /** How long the opened-up live pill stays over the bar. */
    internal val LIVE_PANEL_IDLE_MS = 10_000L

    /**
     * The pinned apps' icons loaded together in the background before the bar needs them (E24),
     * instead of one by one as each icon view asks.
     */
    internal fun preloadIcons(s: TaskbarSetup) {
        val missing = s.pinned.filter { iconCache[it.key] == null }
        if (missing.isEmpty()) return
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { missing.mapNotNull { app -> loadIcon(app, s)?.let { app.key to it } } }
            loaded.forEach { (key, drawable) -> if (iconCache[key] == null) iconCache[key] = drawable }
        }
    }

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

    internal fun resetIcon(view: View, hint: TextView) {
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

    internal fun loadIcon(app: AppItem, s: TaskbarSetup): Drawable? {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val icon = catching {
            source?.loadIcon(app, s.themedIcons, dark, s.iconPack, s.customIcons[app.key].orEmpty())
        }.getOrNull() ?: catching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
        return icon?.let { IconShapes.apply(it, s.iconShapes[app.key] ?: s.iconShape, plateOthers = s.shapeAllIcons) }
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
    /**
     * Icons by app key, kept per [theme] (dark / light): switching back and forth finds both
     * already drawn (E23) instead of emptying the cache each time.
     */
    internal class IconCache(size: Int) {
        private val icons = android.util.LruCache<String, Drawable>(size)
        var theme: String = ""
        operator fun get(key: String): Drawable? = icons.get("$key#$theme")
        operator fun set(key: String, value: Drawable) { icons.put("$key#$theme", value) }
        fun clear() = icons.evictAll()
    }

    companion object {
        /** Room for every pinned and recent icon at once in both themes, with some to spare (counted in icons). */
        internal const val ICON_CACHE_SIZE = 96
        internal const val MEDIA_CONTROLS_MS = 6_000L
        internal const val MEDIA_HIDE_SETTLE_MS = 2_000L

        /** A dragged icon is drawn see-through. */
        internal const val DRAG_ALPHA = 0.3f

        /** The copy that follows the finger is a little see-through. */
        internal const val GHOST_ALPHA = 0.8f
        internal const val MODE_NONE = 0
        internal const val MODE_HANDLES = 1
        internal const val MODE_BAR = 2
        internal const val MODE_BAR_FIXED = 3
        internal const val MODE_MINI = 4

        internal const val ACTION_NONE = 0
        internal const val ACTION_OPEN = 1
        internal const val ACTION_SPLIT = 2
        internal const val ACTION_FLOAT = 3
        internal const val ACTION_SPLIT_LEFT = 4
        internal const val ACTION_SPLIT_RIGHT = 5
        internal const val ACTION_TOP = 6
        internal const val ACTION_PEEK = 7
        internal const val ACTION_THREE = 8

        internal const val MAX_PINNED = 10
        internal const val PREFS_NAME = "taskbar"
        internal const val PREF_HIDDEN_HERE = "hidden_here"
        internal const val MAX_SHORTCUTS = 5

        // The recents screen of Motorola's desktop taskbar (Ready For), exported without a permission.
        internal const val DESKTOP_TASKBAR_PACKAGE = "com.motorola.systemui.desk"
        internal const val ACTION_DESKTOP_RECENTS = "com.motorola.taskbar.action.RECENTS"

        // What Android's drag and drop reads from a dragged app or shortcut (as Motorola's taskbar sends it).
        internal const val MIME_ACTIVITY = "application/vnd.android.activity"
        internal const val EXTRA_DRAG_PENDING_INTENT = "android.intent.extra.PENDING_INTENT"
        internal const val EXTRA_DISALLOW_HIT_REGION = "DISALLOW_HIT_REGION"
        internal const val LARGE_SCREEN_DP = 600
        /** The cover screen's slim bar: its largest icons and gaps (dp). */
        internal const val COVER_ICON_DP = 44
        internal const val COVER_SPACING_DP = 6
        /** How long "Open with …" stays. */
        internal const val SUGGEST_MS = 4_000L
        /** How long "Undo split" stays. */
        internal const val UNDO_MS = 5_000L
        internal const val UNDO_ABOVE_BAR_DP = 84
        internal const val AUTO_COLLAPSE_MS = 6_000L
        internal const val HANDLE_WIDTH_DP = 64
        internal const val HANDLE_HEIGHT_DP = 40
        internal const val RECENT_WINDOW_MS = 12L * 60 * 60 * 1000
        internal const val USAGE_OVERLAP_MS = 10_000L

        /** InputManager.getMaximumObscuringOpacityForTouch()'s default (Android 12+). */
        internal const val MAX_OBSCURING_ALPHA = 0.8f
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
