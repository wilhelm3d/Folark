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
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_FLOAT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_NONE
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_OPEN
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_PEEK
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT_LEFT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT_RIGHT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_THREE
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_TOP
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.AUTO_COLLAPSE_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MIME_ACTIVITY
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.homeVisible

// One app icon on the bar: tap, long-press menu and drag (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/** [pinnedIndex]: the icon's place among the pinned apps (-1: a recent app), for reordering. */
internal fun AppTaskbar.iconView(app: AppItem, s: TaskbarSetup, size: Int, hint: TextView, fixed: Boolean, pinnedIndex: Int = -1): ImageView {
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
        // Held: the gesture is the icon's (a scrolling bar must not take it).
        icon.parent?.requestDisallowInterceptTouchEvent(true)
        org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.PICK_UP)
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
                            org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.ZONE)
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
                        if (next != ACTION_NONE && s.zones.haptics) org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.ZONE)
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
                    org.arkikeskus.launcher.data.Haptics.play(context, org.arkikeskus.launcher.data.Haptics.Event.DROP)
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
