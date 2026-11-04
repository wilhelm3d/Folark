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
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACCENT_DEEP
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACCENT_LIGHT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACCENT_MID
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.AUTO_COLLAPSE_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MODE_BAR_FIXED
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.homeVisible

// The opened bar: its row, backdrop themes, pair tiles and buttons (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

// ------------------------------------------------------------------ the bar

@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.showBar(s: TaskbarSetup, fixed: Boolean, animateIn: Boolean = false) {
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
    // More than fits (many recents, pairs…): the row scrolls sideways inside the bar's backdrop.
    val scroller = TaskbarScroller(context) {
        windowManager.currentWindowMetrics.bounds.width() - dp(SCROLL_SIDE_MARGIN_DP) * 2
    }.apply {
        background = row.background
        row.background = null
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
        if (pinnedIndex >= 0) scroller.ownDrags += view
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
        scroller.addView(row, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
        addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
internal fun AppTaskbar.publishSpace(barRow: View) {
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
internal fun AppTaskbar.barBackground(s: TaskbarSetup): Drawable? {
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
internal fun AppTaskbar.neonBackground(alpha: Int): Drawable {
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
internal fun AppTaskbar.glassBackground(alpha: Int): Drawable {
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
internal fun AppTaskbar.pairView(pair: AppPair, s: TaskbarSetup, size: Int): View =
    pairTile(listOfNotNull(pair.app1Key, pair.app2Key, pair.app3Key.takeIf { pair.hasThirdApp }), pair.name, s, size) {
        // A saved pair opens as a split: its haptic here (the open split's tile gets it from restore()).
        org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.SPLIT)
        repository?.launchAppPair(pair)
    }

/** Two or three apps' icons together on one rounded tile; a tap runs [onClick]. */
internal fun AppTaskbar.pairTile(keys: List<String>, description: String, s: TaskbarSetup, size: Int, onClick: () -> Unit): View {
    val frame = FrameLayout(context).apply {
        contentDescription = description
        background = GradientDrawable().apply {
            cornerRadius = size * 0.28f
            val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
            setColor(if (light) Color.argb(36, 0, 0, 0) else Color.argb(44, 255, 255, 255))
        }
        setOnClickListener {
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

/** A plain button on the bar: [onTap], and [onHold] for a long press (if any). */
@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.barButton(
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

/** The bar keeps this far from each screen edge before its row scrolls. */
private const val SCROLL_SIDE_MARGIN_DP = 16
