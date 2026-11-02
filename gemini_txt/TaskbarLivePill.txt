package org.arkikeskus.launcher.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.ui.component.LivePillPainter

/**
 * The live-activity pill on the taskbar: the same look and effect as on home ([LivePillPainter]),
 * drawn by a plain view because the taskbar is made of views. Redraws itself only while it is on
 * screen and its effect moves (or its clock ticks).
 */
@SuppressLint("ViewConstructor")
internal class TaskbarLivePill(
    context: Context,
    private val heightPx: Int,
    private val effect: String,
    private val light: Boolean,
) : View(context) {
    private val painter = LivePillPainter()
    private val density = context.resources.displayMetrics.density
    private val glowPad = 5f * density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = if (light) Color.rgb(27, 27, 31) else Color.WHITE
        // Equal-width digits: a ticking clock doesn't wobble.
        fontFeatureSettings = "tnum"
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var icon: Bitmap? = null
    private var iconKey: String? = null
    private val started = SystemClock.uptimeMillis()

    var activity: LiveActivity? = null
        set(value) {
            val oldWidth = desiredWidth()
            field = value
            if (value != null && value.key != iconKey) {
                iconKey = value.key
                icon = runCatching { value.icon?.loadDrawable(context)?.toBitmap(48, 48) }.getOrNull()
            }
            contentDescription = value?.let { "${it.title} ${it.shortText(System.currentTimeMillis())}" }
            if (desiredWidth() != oldWidth) requestLayout()
            invalidate()
        }

    var extra: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    private fun label(): String {
        val a = activity ?: return ""
        val main = a.shortText(System.currentTimeMillis())
        return if (extra > 0) "$main  +$extra" else main
    }

    private fun iconSize(): Float = heightPx * 0.42f

    private fun desiredWidth(): Int {
        val text = textPaint.measureText(label()).coerceAtMost(MAX_TEXT_DP * density)
        return (glowPad * 2 + PAD_DP * density * 2 + iconSize() + GAP_DP * density + text).toInt() + 1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(desiredWidth(), heightPx)
    }

    override fun onDraw(canvas: Canvas) {
        val a = activity ?: return
        // "9:59" became "10:00": make room.
        if (desiredWidth() > width) requestLayout()
        val t = (SystemClock.uptimeMillis() - started) / 1000f
        val accent = a.color ?: LivePillPainter.FALLBACK_ACCENT
        val moves = LivePillPainter.animates(effect)
        painter.draw(canvas, width.toFloat(), height.toFloat(), glowPad, effect, if (moves) t else 0f, accent, a.progress, a.indeterminate, light)
        var x = glowPad + PAD_DP * density
        val cy = height / 2f
        icon?.let { bmp ->
            val s = iconSize()
            iconPaint.colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bmp, null, android.graphics.RectF(x, cy - s / 2, x + s, cy + s / 2), iconPaint)
            x += s + GAP_DP * density
        }
        val room = width - x - glowPad - PAD_DP * density
        val text = TextUtils.ellipsize(label(), textPaint, room, TextUtils.TruncateAt.END).toString()
        canvas.drawText(text, x, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        // Only while shown: a moving effect at about 30 frames a second, a clock once a second.
        if (isAttachedToWindow && isShown) {
            when {
                moves -> postInvalidateDelayed(FRAME_MS)
                a.ticks -> postInvalidateDelayed(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate()
    }

    private companion object {
        const val PAD_DP = 10f
        const val GAP_DP = 7f
        const val MAX_TEXT_DP = 120f
        const val FRAME_MS = 33L
    }
}
