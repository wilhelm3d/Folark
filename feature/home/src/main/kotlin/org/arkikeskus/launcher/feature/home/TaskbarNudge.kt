package org.arkikeskus.launcher.feature.home

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.IconNudge
import org.arkikeskus.launcher.ui.component.NudgeFrame

/**
 * Plays a notification nudge on a taskbar icon: the same movement and ring as on home
 * ([IconNudge]), with the view's own transform and a ring drawn in its overlay.
 */
internal fun playTaskbarNudge(view: View, effect: String, color: Int, softness: Float) {
    running.remove(view)?.cancel()
    val frame = NudgeFrame()
    val ring = NudgeRing(color)
    // The colour wash uses the icon's colour filter; the one it had (if any) comes back after.
    val image = view as? android.widget.ImageView
    val ownFilter = image?.colorFilter
    view.overlay.add(ring)
    view.pivotX = view.width / 2f
    view.pivotY = if (effect == LauncherSettings.NUDGE_BOUNCE) view.height.toFloat() else view.height / 2f
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = IconNudge.DURATION_MS.toLong()
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            IconNudge.frame(effect, a.animatedValue as Float, frame, softness)
            view.translationX = frame.dx * view.width
            view.translationY = frame.dy * view.height
            view.scaleX = frame.scaleX
            view.scaleY = frame.scaleY
            view.rotation = frame.rotation
            ring.update(frame, view.width, view.height)
            if (image != null && effect == LauncherSettings.NUDGE_TINT) {
                image.colorFilter = if (frame.tintAlpha > 0f) {
                    android.graphics.PorterDuffColorFilter(
                        android.graphics.Color.argb(
                            (255 * frame.tintAlpha).toInt(),
                            android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color),
                        ),
                        android.graphics.PorterDuff.Mode.SRC_ATOP,
                    )
                } else ownFilter
            }
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private fun done() {
                view.translationX = 0f; view.translationY = 0f
                view.scaleX = 1f; view.scaleY = 1f; view.rotation = 0f
                view.overlay.remove(ring)
                if (image != null && effect == LauncherSettings.NUDGE_TINT) image.colorFilter = ownFilter
            }
            override fun onAnimationEnd(animation: android.animation.Animator) = done()
            override fun onAnimationCancel(animation: android.animation.Animator) = done()
        })
    }
    running[view] = animator
    animator.start()
}

/** The nudge each icon is playing, so a new one restarts it instead of piling up. */
private val running = java.util.WeakHashMap<View, ValueAnimator>()

/** The ripple ring or glow around a taskbar icon, drawn in the icon's overlay. */
private class NudgeRing(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var radius = 0f
    private var alphaF = 0f
    private var soft = false
    private var core = 0.55f
    private var cx = 0f
    private var cy = 0f
    private var iconSize = 0f

    fun update(frame: NudgeFrame, width: Int, height: Int) {
        cx = width / 2f
        cy = height / 2f
        iconSize = minOf(width, height).toFloat()
        radius = iconSize / 2f * frame.ringScale
        alphaF = frame.ringAlpha
        soft = frame.ringSoft
        core = frame.ringCore
        // The overlay draws within these bounds; the bar doesn't clip its icons, so rings may spread.
        val reach = (radius * 1.3f).toInt() + 2
        setBounds((cx - reach).toInt(), (cy - reach).toInt(), (cx + reach).toInt(), (cy + reach).toInt())
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (alphaF <= 0f || radius <= 0f) return
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        if (soft) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx, cy, radius * 1.25f,
                intArrayOf(android.graphics.Color.argb((150 * alphaF).toInt(), r, g, b), android.graphics.Color.TRANSPARENT),
                floatArrayOf(core, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius * 1.25f, paint)
            paint.shader = null
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = iconSize * 0.05f
            paint.color = android.graphics.Color.argb((205 * alphaF).toInt(), r, g, b)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
