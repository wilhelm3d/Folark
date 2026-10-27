package org.arkikeskus.launcher.feature.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The indicator under a taskbar icon whose app is open now (Settings ▸ Recents). A single dot for one
 * window; a wider pill (Windows-style) for [windows] >= 2, so split pairs and pop-ups read as several.
 */
internal class RunningDot(lightTheme: Boolean, private val windows: Int = 1) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (lightTheme) Color.argb(200, 0, 0, 0) else Color.argb(230, 255, 255, 255)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = b.width() / 22f
        val cy = b.bottom - r * 1.6f
        val cx = b.exactCenterX()
        if (windows <= 1) {
            canvas.drawCircle(cx, cy, r, paint)
        } else {
            // Half-length grows with the number of open windows (capped), a pill rather than a dot.
            val half = r * (0.4f + windows.coerceAtMost(3) * 0.9f)
            canvas.drawRoundRect(cx - half, cy - r, cx + half, cy + r, r, r, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
