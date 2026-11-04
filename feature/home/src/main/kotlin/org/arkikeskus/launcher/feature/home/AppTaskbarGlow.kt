package org.arkikeskus.launcher.feature.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import kotlin.math.pow
import org.arkikeskus.launcher.model.LauncherSettings

// The glow when an app is started from the taskbar (split out of AppTaskbar.kt; extension
// functions on [AppTaskbar]).

private const val GLOW_MS = 520L
/** The glow window's size as a multiple of the icon's. */
private const val GLOW_SPREAD = 2.4f

/**
 * A soft glow in the app's own colour blooms out of [icon] and fades, with a ring spreading from
 * it, while the icon gives a small pulse. The glow has its own little untouchable window, so it
 * plays to the end even when the bar collapses or is rebuilt as the app opens. That window is kept
 * for a few seconds after a glow and moved for the next one (launching several apps in a row adds
 * one window, not one each). Nothing plays when Android's animations are turned off.
 */
internal fun AppTaskbar.playLaunchGlow(icon: View) {
    val style = setup?.launchGlow ?: LauncherSettings.LAUNCH_GLOW_APP
    if (style == LauncherSettings.LAUNCH_GLOW_OFF) return
    if (!animationsOn(context) || !icon.isAttachedToWindow || icon.width == 0) return
    val color = when (style) {
        LauncherSettings.LAUNCH_GLOW_WHITE -> Color.WHITE
        LauncherSettings.LAUNCH_GLOW_ACCENT -> accent(AppTaskbar.ACCENT_LIGHT)
        else -> glowColor(icon)
    }
    val size = (maxOf(icon.width, icon.height) * GLOW_SPREAD).toInt()
    val at = IntArray(2)
    icon.getLocationOnScreen(at)
    val params = WindowManager.LayoutParams(
        size,
        size,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = at[0] + icon.width / 2 - size / 2
        y = at[1] + icon.height / 2 - size / 2
        // An untouchable application overlay must stay see-through enough for taps to pass.
        passThroughAlpha?.let { alpha = it }
        title = "FolarkLaunchGlow"
    }
    handler.removeCallbacks(glowRelease)
    glowAnimator?.cancel()
    val kept = glowView as? GlowView
    val glow = kept ?: GlowView(context)
    glow.prepare(color, iconRadius = maxOf(icon.width, icon.height) / 2f)
    val placed = if (kept != null) runCatching { windowManager.updateViewLayout(glow, params) }.isSuccess
    else runCatching { windowManager.addView(glow, params) }.isSuccess
    if (!placed) {
        releaseGlow()
        return
    }
    glowView = glow
    glow.visibility = View.VISIBLE

    icon.animate().cancel()
    icon.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110).withEndAction {
        icon.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.5f)).start()
    }.start()

    glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = GLOW_MS
        interpolator = DecelerateInterpolator(1.4f)
        addUpdateListener { glow.progress = it.animatedValue as Float }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                glow.visibility = View.INVISIBLE
                handler.postDelayed(glowRelease, GLOW_KEEP_MS)
            }
        })
        start()
    }
}

/** Takes the glow's window away (the bar stops, or no glow played for a while). */
internal fun AppTaskbar.releaseGlow() {
    handler.removeCallbacks(glowRelease)
    glowAnimator?.cancel()
    glowAnimator = null
    glowView?.let { runCatching { windowManager.removeViewImmediate(it) } }
    glowView = null
}

/** How long the glow's window waits for the next launch before it goes. */
private const val GLOW_KEEP_MS = 4_000L

/** Whether Android's animations are on (Developer options ▸ Animator duration scale, Remove animations). */
private fun animationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/**
 * The icon's own colour (the same one Own colour / Soft colour themed icons use), made bright
 * enough to glow. A grey or missing icon glows soft white.
 */
private fun glowColor(icon: View): Int {
    val drawable = (icon as? ImageView)?.drawable ?: return Color.WHITE
    // The bar keeps its icon drawables cached, so the same drawable comes back launch after launch:
    // sample it once, not on the main thread at every tap.
    synchronized(glowColors) { glowColors[drawable] }?.let { return it }
    return glowColorOf(drawable).also { synchronized(glowColors) { glowColors[drawable] = it } }
}

/** Glow colours by icon drawable; entries go with their drawable. */
private val glowColors = java.util.WeakHashMap<android.graphics.drawable.Drawable, Int>()

private fun glowColorOf(drawable: android.graphics.drawable.Drawable): Int {
    val color = org.arkikeskus.launcher.data.IconColors.dominant(drawable) ?: return Color.WHITE
    val saturation = org.arkikeskus.launcher.data.IconColors.saturation(color)
    if (saturation < org.arkikeskus.launcher.data.IconColors.GREY_SATURATION) return Color.WHITE
    return org.arkikeskus.launcher.data.IconColors.tone(color, saturation.coerceIn(0.45f, 0.85f), 1f)
}

/** The glow itself: a halo that swells and fades, and a thin ring that spreads past it. */
private class GlowView(context: Context) : View(context) {
    private var color = Color.WHITE
    private var iconRadius = 0f

    var progress = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val haloMatrix = Matrix()

    /** A new glow: its colour and the icon's radius. The halo's gradient is made once per glow. */
    fun prepare(color: Int, iconRadius: Float) {
        this.color = color
        this.iconRadius = iconRadius
        // A unit-radius gradient, scaled and moved per frame with a matrix instead of a new
        // shader on every frame; its fade goes through the paint's alpha.
        halo.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(color, withAlpha(color, 128), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        progress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val p = progress
        val fade = (1f - p).pow(1.6f)
        // A quick bloom at the start, then the fade.
        val bloom = if (p < 0.18f) p / 0.18f else 1f
        val haloRadius = iconRadius * (0.95f + 0.75f * p)
        val haloAlpha = (230 * bloom * fade).toInt().coerceIn(0, 255)
        if (haloAlpha > 0 && haloRadius > 0f) {
            haloMatrix.setScale(haloRadius, haloRadius)
            haloMatrix.postTranslate(cx, cy)
            halo.shader?.setLocalMatrix(haloMatrix)
            halo.alpha = haloAlpha
            canvas.drawCircle(cx, cy, haloRadius, halo)
        }
        // From the icon's edge out to near the window's edge (GLOW_SPREAD icon radii).
        val ringRadius = iconRadius * (1f + (GLOW_SPREAD - 1.2f) * p)
        ring.strokeWidth = iconRadius * 0.12f * (1f - p) + 1f
        ring.color = withAlpha(color, (200 * fade).toInt().coerceIn(0, 255))
        canvas.drawCircle(cx, cy, ringRadius.coerceAtMost(minOf(cx, cy) - ring.strokeWidth), ring)
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
}
