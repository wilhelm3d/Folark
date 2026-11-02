package org.arkikeskus.launcher.ui.component

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws the live-activity pill's backdrop and its effect, with plain Android graphics so the home
 * screen (Compose) and the taskbar (views) share one look. [glowPad] is the room left around the
 * pill for the glow; the pill itself fills the rest.
 */
class LivePillPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val clip = Path()
    private val matrix = Matrix()
    private var glowRadius = 0f

    /**
     * [t] is seconds since the effect started (it loops); [accent] the app's colour; [progress]
     * 0..1 (null: none, NaN-free); [light] draws a light pill for light themes.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        glowPad: Float,
        effect: String,
        t: Float,
        accent: Int,
        progress: Float?,
        indeterminate: Boolean,
        light: Boolean,
    ) {
        rect.set(glowPad, glowPad, width - glowPad, height - glowPad)
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val r = rect.height() / 2f
        val base = if (light) Color.argb(235, 246, 246, 250) else Color.argb(225, 22, 22, 28)
        val pulse = (sin(t * 2.0 * PI / PULSE_S).toFloat() + 1f) / 2f

        canvas.save()
        if (effect == LauncherSettings.LIVE_EFFECT_BREATHE) {
            val s = 0.965f + 0.035f * pulse
            canvas.scale(s, s, rect.centerX(), rect.centerY())
        }
        // Glow: a soft halo of the app's colour around the pill, breathing.
        if (effect == LauncherSettings.LIVE_EFFECT_GLOW && glowPad > 0f) {
            if (glowRadius != glowPad) {
                glowRadius = glowPad
                glow.maskFilter = BlurMaskFilter(glowPad * 0.9f, BlurMaskFilter.Blur.NORMAL)
            }
            glow.strokeWidth = glowPad * 0.8f
            glow.color = withAlpha(accent, (90 + 120 * pulse).toInt())
            canvas.drawRoundRect(rect, r, r, glow)
        }
        fill.color = base
        canvas.drawRoundRect(rect, r, r, fill)

        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        // A faint wash of the app's colour, so pills of different apps tell apart at a glance.
        fx.shader = null
        fx.color = withAlpha(accent, if (light) 34 else 46)
        canvas.drawRect(rect, fx)
        when (effect) {
            LauncherSettings.LIVE_EFFECT_SHIMMER -> {
                // A band of light sweeping across, then a pause.
                val cycle = (t % SHIMMER_S) / SHIMMER_S
                val band = rect.width() * 0.45f
                val x = rect.left - band + (rect.width() + band * 2) * (cycle * 1.6f).coerceAtMost(1f)
                fx.color = Color.WHITE
                fx.shader = LinearGradient(
                    x - band / 2, 0f, x + band / 2, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(if (light) 140 else 70, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, fx)
            }
            LauncherSettings.LIVE_EFFECT_PROGRESS -> {
                // The pill fills up with the app's colour as the task advances.
                fx.shader = null
                fx.color = withAlpha(accent, if (light) 90 else 110)
                if (progress != null && !indeterminate) {
                    canvas.drawRect(rect.left, rect.top, rect.left + rect.width() * progress.coerceIn(0f, 1f), rect.bottom, fx)
                } else {
                    // No end known: a block gliding back and forth.
                    val w = rect.width() * 0.3f
                    val p = (sin(t * 2.0 * PI / 2.2).toFloat() + 1f) / 2f
                    val x = rect.left + (rect.width() - w) * p
                    canvas.drawRect(x, rect.top, x + w, rect.bottom, fx)
                }
            }
            LauncherSettings.LIVE_EFFECT_AURORA -> {
                // Two slow lights of the app's colour drifting inside the pill.
                val a = (sin(t * 2.0 * PI / 5.0).toFloat() + 1f) / 2f
                val b = (sin(t * 2.0 * PI / 7.0 + 1.7).toFloat() + 1f) / 2f
                fx.color = Color.WHITE
                fx.shader = RadialGradient(
                    rect.left + rect.width() * a, rect.centerY(), rect.height() * 1.4f,
                    withAlpha(accent, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, fx)
                fx.shader = RadialGradient(
                    rect.left + rect.width() * (1f - b), rect.centerY(), rect.height() * 1.2f,
                    withAlpha(shiftHue(accent), 120), Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, fx)
            }
            LauncherSettings.LIVE_EFFECT_BREATHE -> {
                fx.shader = null
                fx.color = withAlpha(accent, (30 + 60 * pulse).toInt())
                canvas.drawRect(rect, fx)
            }
            else -> Unit
        }
        fx.shader = null
        canvas.restore()

        // Orbit: a light running around the rim. Every other effect gets a quiet rim.
        stroke.strokeWidth = rect.height() * 0.06f
        if (effect == LauncherSettings.LIVE_EFFECT_ORBIT) {
            val sweep = SweepGradient(
                rect.centerX(), rect.centerY(),
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(accent, 255), Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 0.85f, 0.92f, 1f),
            )
            matrix.setRotate((t % ORBIT_S) / ORBIT_S * 360f, rect.centerX(), rect.centerY())
            sweep.setLocalMatrix(matrix)
            stroke.shader = sweep
            stroke.color = Color.WHITE
        } else {
            stroke.shader = null
            stroke.color = if (light) Color.argb(40, 0, 0, 0) else Color.argb(55, 255, 255, 255)
        }
        val inset = stroke.strokeWidth / 2f
        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, r - inset, r - inset, stroke)
        stroke.shader = null
        canvas.restore()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun shiftHue(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + 50f) % 360f
        hsv[1] = hsv[1].coerceAtLeast(0.35f)
        return Color.HSVToColor(hsv)
    }

    companion object {
        /** Whether [effect] moves (needs redrawing every frame). */
        fun animates(effect: String): Boolean = effect != LauncherSettings.LIVE_EFFECT_STILL

        /** A colour for an app that set none: the system accent. */
        const val FALLBACK_ACCENT = 0xFF8AB4F8.toInt()

        private const val PULSE_S = 1.8f
        private const val SHIMMER_S = 2.6f
        private const val ORBIT_S = 2.4f
    }
}
