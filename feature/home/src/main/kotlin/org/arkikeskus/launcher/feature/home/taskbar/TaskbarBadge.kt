package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The notification count over a taskbar icon: a small filled circle at the icon's top end carrying
 * the number (99 or more shows "99+"). Drawn over the icon's foreground, layered next to the
 * running dot where the app is open too (see [AppTaskbarIcons.applyBadges]).
 */
internal class BadgeDrawable(val count: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 62, 52) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = b.width() / 7.2f
        val cx = b.right - r * 1.7f
        val cy = b.top + r * 1.7f
        canvas.drawCircle(cx, cy, r, paint)
        textPaint.textSize = r * 1.25f
        val label = if (count > 99) "99+" else count.toString()
        // Centred on the circle's middle, nudged up half a text-height so it reads as centred.
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, y, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
