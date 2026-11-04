package org.arkikeskus.launcher.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * The media controls' progress bar: a thin track with the played part in [tint]. The position is
 * worked out from the player's last state ([TaskbarMedia.progress]), so nothing asks the player
 * while it plays; the bar redraws twice a second, only while it is on screen and the music plays.
 * A drag seeks when the player allows it.
 */
@SuppressLint("ViewConstructor")
internal class TaskbarMediaProgress(
    context: Context,
    private val media: TaskbarMedia,
    private val tint: Int,
    private val onTouched: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, Color.red(tint), Color.green(tint), Color.blue(tint))
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
    }
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
    }
    /** While a finger seeks: the fraction it points at (else -1). */
    private var dragFraction = -1f

    private val tick = Runnable { invalidate() }

    override fun onDraw(canvas: Canvas) {
        val (at, total) = media.progress() ?: return
        val inset = 2f * density
        val y = height / 2f
        val start = inset
        val end = width - inset
        val fraction = if (dragFraction >= 0f) dragFraction else at.toFloat() / total
        canvas.drawLine(start, y, end, y, track)
        canvas.drawLine(start, y, start + (end - start) * fraction.coerceIn(0f, 1f), y, played)
        removeCallbacks(tick)
        if (isAttachedToWindow && media.state?.playing == true && dragFraction < 0f) postDelayed(tick, TICK_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!media.canSeek || width == 0) return false
        val fraction = (event.x / width).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragFraction = fraction
                onTouched()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                media.progress()?.let { (_, total) -> media.seekTo((total * fraction).toLong()) }
                dragFraction = -1f
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragFraction = -1f
                invalidate()
            }
        }
        return true
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
