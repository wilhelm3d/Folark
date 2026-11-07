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
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.HANDLE_HEIGHT_DP
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.HANDLE_WIDTH_DP
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.homeVisible

// The corner handles, the mini bar and the bar's in/out animations (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

// ------------------------------------------------------------------ the corner handles

/**
 * Two invisible touch zones in the bottom corners, clear of the middle where Android's own
 * home gesture starts. A swipe up (or a tap) in either opens the bar.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.showHandles(s: TaskbarSetup) {
    val sides = when (s.handles) {
        LauncherSettings.TASKBAR_HANDLES_LEFT -> listOf(Gravity.START)
        LauncherSettings.TASKBAR_HANDLES_RIGHT -> listOf(Gravity.END)
        LauncherSettings.TASKBAR_HANDLES_CENTER -> listOf(Gravity.CENTER_HORIZONTAL)
        else -> listOf(Gravity.START, Gravity.END)
    }
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    // Over a full-screen video the corners hold the player's own buttons (full screen, captions):
    // the handles shrink to a thin strip along the very bottom, so a tap there still reaches the
    // player and only a swipe up from the edge opens the bar.
    val slim = s.hideFullScreen && fullScreen && !homeVisible
    sides.forEach { side ->
        val zone = FrameLayout(context).apply {
            contentDescription = context.getString(R.string.taskbar_handle)
            minimumWidth = dp(HANDLE_WIDTH_DP)
            minimumHeight = dp(if (slim) HANDLE_SLIM_HEIGHT_DP else HANDLE_HEIGHT_DP)
            var downY = 0f
            var opened = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = event.rawY; opened = false }
                    MotionEvent.ACTION_MOVE -> if (!opened && downY - event.rawY > slop * 2) {
                        opened = true
                        open()
                    }
                    // A plain tap opens it too, except on the slim strip over a video.
                    MotionEvent.ACTION_UP -> if (!opened && !slim) open()
                }
                true
            }
        }
        // A pull hint shows where the swipe starts, in the chosen style (Settings ▸ Taskbar ▸ Pull style).
        if (s.handleHint && !slim) {
            val (hintView, lp) = handleHintView(s.handleStyle)
            zone.addView(hintView, lp)
        }
        if (side != Gravity.CENTER_HORIZONTAL) excludeFromSystemGestures(zone)
        add(zone, params(Gravity.BOTTOM or side, 0, s.handleLift()))
    }
}

/** The pull-hint view for [style] (a line, one/two arrows, dots or a text label), bottom-centred. */
internal fun AppTaskbar.handleHintView(style: String): Pair<View, FrameLayout.LayoutParams> {
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
internal fun AppTaskbar.showMini(s: TaskbarSetup) {
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

internal fun AppTaskbar.open() {
    expanded = true
    manuallyHidden = false
    // Which app the bar opened over (per-app rules, "hidden here"), right away.
    TaskbarHost.checkNow()
    loadRecents(rebuild = true, force = false)
}

/** The hide button: animate the bar away, then leave only the pull handles. */
internal fun AppTaskbar.hideToHandles() {
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
internal fun AppTaskbar.animateBarIn(view: View, anim: String) {
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
internal fun AppTaskbar.animateBarOut(view: View, anim: String, onEnd: () -> Unit) {
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

/** The handles' height over a full-screen video: a strip along the edge, clear of the player's buttons. */
private const val HANDLE_SLIM_HEIGHT_DP = 14
