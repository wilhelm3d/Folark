package org.arkikeskus.launcher.feature.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** The small dot under a taskbar icon whose app is open now (Settings ▸ Recents). */
internal class RunningDot(lightTheme: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (lightTheme) Color.argb(200, 0, 0, 0) else Color.argb(230, 255, 255, 255)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = b.width() / 22f
        canvas.drawCircle(b.exactCenterX(), b.bottom - r * 1.6f, r, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
