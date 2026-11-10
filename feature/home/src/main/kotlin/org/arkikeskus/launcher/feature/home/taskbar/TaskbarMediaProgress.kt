package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

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
        // Drawn again when the line has moved about a pixel (a song on a small tile moves one pixel
        // every few seconds), within 1–10 s; not while the bar is hidden or the music paused.
        if (isAttachedToWindow && isShown && media.state?.playing == true && dragFraction < 0f) {
            val perPixel = if (end > start) total / (end - start).toDouble() else Double.MAX_VALUE
            postDelayed(tick, perPixel.toLong().coerceIn(MIN_TICK_MS, MAX_TICK_MS))
        }
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
        /** How often the line can move at most, and at least (a long podcast on a wide tile). */
        const val MIN_TICK_MS = 1_000L
        const val MAX_TICK_MS = 10_000L
    }
}
