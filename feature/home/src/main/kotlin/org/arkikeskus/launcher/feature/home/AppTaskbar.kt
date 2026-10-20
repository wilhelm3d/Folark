package org.arkikeskus.launcher.feature.home

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
import android.graphics.Color
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
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
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
    val theme: String,
    val opacity: Float,
    val handles: String,
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
    val recentsButton: String,
    val quickSwitch: Boolean,
    val splitControls: Boolean,
    val splitSide: String,
    val threeApp: Boolean,
    val hideFullScreen: Boolean,
    val liveEnabled: Boolean,
    val liveEffect: String,
    val nudgeEnabled: Boolean,
    val nudgeEffect: String,
    val nudgeSoftness: Float,
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
    private var livePill: TaskbarLivePill? = null
    private var liveIndex = 0
    private val SPACE_GAP_DP = 6

    /** The app icons on the bar now, by badgeKey, for the notification nudge. */
    private val iconViews = HashMap<String, View>()

    private var repository: AppRepository? = null
    private var source: LauncherAppsSource? = null
    private val iconCache = HashMap<String, Drawable>()
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
                    enabled = s.taskbarEnabled,
                    pinned = (s.taskbarPinnedApps ?: favorites).mapNotNull { byKey[it] }.take(MAX_PINNED),
                    recentCount = s.taskbarRecentCount,
                    iconSizeDp = s.taskbarIconSize,
                    spacingDp = s.taskbarIconSpacing,
                    bottomDp = s.taskbarBottomOffset,
                    theme = s.taskbarTheme,
                    opacity = s.taskbarOpacity,
                    handles = s.taskbarHandles,
                    onHome = s.taskbarOnHome,
                    keepOpen = s.taskbarKeepOpen,
                    themedIcons = s.useThemedIcons,
                    iconPack = s.iconPackPackage,
                    customIcons = custom,
                    iconShape = s.iconShape,
                    // Apps opened through Folark, newest first: the recent apps without Usage access.
                    launchHistory = usage.entries.sortedByDescending { it.value.lastUsed }.map { it.key }.take(24),
                    mediaEnabled = s.taskbarMediaEnabled,
                    mediaShowPaused = s.taskbarMediaShowPaused,
                    mediaPosition = s.taskbarMediaPosition,
                    mediaTap = s.taskbarMediaTap,
                    mediaControls = s.taskbarMediaControls,
                    mediaShape = s.taskbarMediaShape,
                    mediaBadge = s.taskbarMediaBadge,
                    systemDrag = s.taskbarSystemDrag,
                    recentsButton = s.taskbarRecentsButton,
                    quickSwitch = s.taskbarQuickSwitch,
                    splitControls = s.taskbarSplitControls,
                    splitSide = s.taskbarSplitSide,
                    threeApp = s.taskbarThreeApp,
                    hideFullScreen = s.taskbarHideFullScreen,
                    liveEnabled = s.liveActivities && s.liveActivitiesTaskbar,
                    liveEffect = s.liveActivityEffect,
                    nudgeEnabled = s.iconNudge && s.iconNudgeTaskbar,
                    nudgeEffect = s.iconNudgeEffect,
                    nudgeSoftness = s.iconNudgeSoftness,
                )
            }.distinctUntilChanged().collect { next ->
                val previous = setup
                val style = "${next.themedIcons}|${next.iconPack}|${next.customIcons.hashCode()}|${next.iconShape}"
                if (style != iconStyle) {
                    iconStyle = style
                    iconCache.clear()
                }
                setup = next
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
            entry.notificationBadgeRepository().live.collect { next ->
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
        ensureProbe(s != null && s.enabled && s.keepOpen && screenOn && largeScreen())
        val active = s != null && s.enabled && screenOn && largeScreen() && keyguard?.isKeyguardLocked != true &&
            (s.pinned.isNotEmpty() || recents.isNotEmpty() || mediaShown(s) || (s.liveEnabled && live.isNotEmpty()))
        if (!active || s == null) {
            expanded = false
            remove()
            TaskbarSpace.set(0)
            return
        }
        val mode = when {
            // Typing on home or in the drawer: the bar would cover the keyboard's bottom row.
            homeVisible && TaskbarSpace.keyboardOnHome.value -> MODE_NONE
            homeVisible && s.onHome -> MODE_BAR_FIXED
            homeVisible -> MODE_NONE
            // Full screen (a video, a game): no bar covering it; a corner swipe still opens it.
            s.keepOpen && !(s.hideFullScreen && fullScreen) && !keyboardUp -> MODE_BAR_FIXED
            expanded -> MODE_BAR
            else -> MODE_HANDLES
        }
        // Only the bar that stays on home takes room there (home and the drawer step aside).
        if (mode != MODE_BAR_FIXED || !homeVisible) TaskbarSpace.set(0)
        if (mode == shownMode && !rebuild) return
        remove()
        when (mode) {
            MODE_BAR, MODE_BAR_FIXED -> showBar(s, fixed = mode == MODE_BAR_FIXED)
            MODE_HANDLES -> showHandles(s)
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
            add(zone, params(Gravity.BOTTOM or side, 0, 0))
        }
    }

    private fun open() {
        expanded = true
        loadRecents(rebuild = true, force = false)
    }

    // ------------------------------------------------------------------ the bar

    @SuppressLint("ClickableViewAccessibility")
    private fun showBar(s: TaskbarSetup, fixed: Boolean) {
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
            iconViews[app.badgeKey] = view
            row.addView(view, LinearLayout.LayoutParams(size, size).apply {
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
                // A long press shows the next one when there are several.
                setOnLongClickListener {
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
        if (showMedia && s.mediaPosition == LauncherSettings.TASKBAR_MEDIA_POS_MIDDLE) addMedia()
        val recentApps = recents.filter { r -> s.pinned.none { it.key == r.key } }
        if (s.pinned.isNotEmpty() && recentApps.isNotEmpty()) {
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
        val barLayout = params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(s.bottomDp), watchOutside = !fixed)
        add(column, barLayout)
        barRow = row
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
    private fun barBackground(s: TaskbarSetup): GradientDrawable? {
        val alpha = (s.opacity.coerceIn(0f, 1f) * 255).toInt()
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

    @SuppressLint("ClickableViewAccessibility")
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
                        // Up and to the right: split screen. Up and to the left: floating window.
                        val next = when {
                            up < armDistance * 0.6f -> ACTION_NONE
                            dx > armDistance -> ACTION_SPLIT
                            dx < -armDistance -> ACTION_FLOAT
                            else -> ACTION_NONE
                        }
                        // Over an app: split goes to Android's own drag and drop, and its drop zones.
                        if (next == ACTION_SPLIT && !homeVisible && useDropZones(s) && !triedSystem) {
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
                        }
                        hint.visibility = View.VISIBLE
                        hint.text = context.getString(
                            when (action) {
                                ACTION_SPLIT -> R.string.taskbar_hint_split
                                ACTION_FLOAT -> R.string.taskbar_hint_floating
                                else -> R.string.taskbar_hint_choose
                            },
                        )
                        hint.alpha = if (action == ACTION_NONE) 0.7f else 1f
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
                        ACTION_FLOAT -> repository?.launchFloating(app)
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
    private fun openSplit(app: AppItem) {
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
            val right = setup?.splitSide != LauncherSettings.TASKBAR_SPLIT_SIDE_LEFT
            withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = right) }
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
            val changed = next != recents
            recents = next
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
        val packages = if (hasUsageAccess(context)) usageHistory() else null
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

    companion object {
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

        private const val ACTION_NONE = 0
        private const val ACTION_OPEN = 1
        private const val ACTION_SPLIT = 2
        private const val ACTION_FLOAT = 3

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
