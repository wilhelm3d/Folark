package org.arkikeskus.launcher.feature.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * plays to the end even when the bar collapses or is rebuilt as the app opens. Nothing plays when
 * Android's animations are turned off.
 */
internal fun AppTaskbar.playLaunchGlow(icon: View) {
    val style = setup?.launchGlow ?: LauncherSettings.LAUNCH_GLOW_APP
    if (style == LauncherSettings.LAUNCH_GLOW_OFF) return
    if (!animationsOn(context) || !icon.isAttachedToWindow || icon.width == 0) return
    val color = when (style) {
        LauncherSettings.LAUNCH_GLOW_WHITE -> Color.WHITE
        LauncherSettings.LAUNCH_GLOW_ACCENT -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200)
        } else Color.rgb(160, 190, 255)
        else -> glowColor(icon)
    }
    val size = (maxOf(icon.width, icon.height) * GLOW_SPREAD).toInt()
    val at = IntArray(2)
    icon.getLocationOnScreen(at)
    val glow = GlowView(context, color, iconRadius = maxOf(icon.width, icon.height) / 2f)
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
    if (runCatching { windowManager.addView(glow, params) }.isFailure) return

    icon.animate().cancel()
    icon.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110).withEndAction {
        icon.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.5f)).start()
    }.start()

    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = GLOW_MS
        interpolator = DecelerateInterpolator(1.4f)
        addUpdateListener { glow.progress = it.animatedValue as Float }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                runCatching { windowManager.removeViewImmediate(glow) }
            }
        })
        start()
    }
}

/** Whether Android's animations are on (Developer options ▸ Animator duration scale, Remove animations). */
private fun animationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/**
 * The icon's main colour, made bright enough to glow: its pixels averaged, weighted towards the
 * colourful ones, then lifted. A grey or missing icon glows soft white.
 */
private fun glowColor(icon: View): Int {
    val drawable = (icon as? ImageView)?.drawable ?: return Color.WHITE
    return runCatching {
        val side = 12
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val old = drawable.copyBounds()
        drawable.setBounds(0, 0, side, side)
        drawable.draw(canvas)
        drawable.bounds = old
        val hsv = FloatArray(3)
        var r = 0f; var g = 0f; var b = 0f; var weight = 0f
        for (y in 0 until side) for (x in 0 until side) {
            val p = bitmap.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val w = 0.15f + hsv[1] * hsv[2]
            r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w; weight += w
        }
        bitmap.recycle()
        if (weight == 0f) return Color.WHITE
        Color.colorToHSV(Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt()), hsv)
        if (hsv[1] < 0.12f) return Color.WHITE
        hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
        hsv[2] = 1f
        Color.HSVToColor(hsv)
    }.getOrDefault(Color.WHITE)
}

/** The glow itself: a halo that swells and fades, and a thin ring that spreads past it. */
private class GlowView(context: Context, private val color: Int, private val iconRadius: Float) : View(context) {
    var progress = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

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
            halo.shader = RadialGradient(
                cx, cy, haloRadius,
                intArrayOf(withAlpha(color, haloAlpha), withAlpha(color, haloAlpha / 2), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
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
