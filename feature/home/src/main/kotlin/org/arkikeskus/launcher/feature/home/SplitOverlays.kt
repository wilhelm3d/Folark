package org.arkikeskus.launcher.feature.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.SplitEvents
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * Split screen's helpers drawn over everything (they need "Display over other apps"; without it
 * they stay off). None of them takes a touch.
 *
 * - S5 divider marks: while a divider is dragged, faint lines at ⅓, ½ and ⅔ (three apps: also ¼
 *   and ¾), where a let-go lands exactly (the module's magnet) and the haptic ticks are.
 * - S14 drag hint: while something (text, a picture) is dragged out of an app in split screen,
 *   each pane is outlined, so it is clear it can be dropped into the other app. Android tells
 *   every window that can take a drop when such a drag starts and ends; a one-pixel window that
 *   never takes a touch listens for that.
 */
object SplitOverlays {
    private var marks: View? = null
    private var hint: View? = null
    private var probe: View? = null
    /** The split's divider places (thousandths) as last reported, for the hint's outlines. */
    @Volatile private var lastFractions: IntArray = intArrayOf(500)

    fun start(context: Context, scope: CoroutineScope, settings: Flow<LauncherSettings>) {
        val app = context.applicationContext
        scope.launch {
            combine(settings.map { it.recents.dividerMarks }.distinctUntilChanged(), SplitEvents.dragging) { on, panes ->
                if (on) panes else 0
            }.distinctUntilChanged().collect { panes ->
                withContext(Dispatchers.Main) { if (panes > 0) showMarks(app, panes) else hideMarks(app) }
            }
        }
        scope.launch { SplitEvents.layouts.collect { (_, fractions) -> lastFractions = fractions } }
        scope.launch {
            combine(settings.map { it.recents.splitDragHint }.distinctUntilChanged(), SplitEvents.stages) { on, stages ->
                on && stages.values.count { it != SplitEvents.STAGE_NONE } >= 2
            }.distinctUntilChanged().collect { wanted ->
                withContext(Dispatchers.Main) { if (wanted) showProbe(app) else hideProbe(app) }
            }
        }
    }

    private fun allowed(context: Context) = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "FolarkSplitOverlay"
    }

    private fun wm(context: Context) = context.getSystemService(WindowManager::class.java)

    // ------------------------------------------------------------------ S5 marks

    private fun showMarks(context: Context, panes: Int) {
        if (marks != null || !allowed(context)) return
        val view = MarksView(context, if (panes >= 3) floatArrayOf(0.25f, 1f / 3, 0.5f, 2f / 3, 0.75f) else floatArrayOf(1f / 3, 0.5f, 2f / 3))
        runCatching { wm(context).addView(view, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }
            .onSuccess { marks = view }
    }

    private fun hideMarks(context: Context) {
        val view = marks ?: return
        marks = null
        view.animate().alpha(0f).setDuration(160).withEndAction { runCatching { wm(context).removeView(view) } }.start()
    }

    private class MarksView(context: Context, private val at: FloatArray) : View(context) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255); strokeWidth = 2f * resources.displayMetrics.density }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }

        init {
            alpha = 0f
            animate().alpha(1f).setDuration(140).start()
        }

        override fun onDraw(canvas: Canvas) {
            val sideBySide = width >= height * 0.8f
            val r = 4f * resources.displayMetrics.density
            for (f in at) {
                if (sideBySide) {
                    val x = width * f
                    canvas.drawLine(x, height * 0.18f, x, height * 0.82f, line)
                    canvas.drawCircle(x, height * 0.5f, r, dot)
                } else {
                    val y = height * f
                    canvas.drawLine(width * 0.18f, y, width * 0.82f, y, line)
                    canvas.drawCircle(width * 0.5f, y, r, dot)
                }
            }
        }
    }

    // ------------------------------------------------------------------ S14 drag hint

    private fun showProbe(context: Context) {
        if (probe != null || !allowed(context)) return
        val view = View(context).apply {
            setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> { showHint(context); true }
                    DragEvent.ACTION_DRAG_ENDED -> { hideHint(context); true }
                    else -> true
                }
            }
        }
        runCatching { wm(context).addView(view, params(1, 1)) }.onSuccess { probe = view }
    }

    private fun hideProbe(context: Context) {
        hideHint(context)
        val view = probe ?: return
        probe = null
        runCatching { wm(context).removeView(view) }
    }

    private fun showHint(context: Context) {
        if (hint != null) return
        val view = HintView(context, lastFractions)
        runCatching { wm(context).addView(view, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)) }
            .onSuccess { hint = view }
    }

    private fun hideHint(context: Context) {
        val view = hint ?: return
        hint = null
        view.animate().alpha(0f).setDuration(160).withEndAction { runCatching { wm(context).removeView(view) } }.start()
    }

    private class HintView(context: Context, fractions: IntArray) : View(context) {
        private val stops = (listOf(0f) + fractions.sorted().map { it / 1000f } + listOf(1f))
        private val density = resources.displayMetrics.density
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            color = Color.argb(200, 120, 200, 255)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 120, 200, 255) }
        private val rect = RectF()

        init {
            alpha = 0f
            animate().alpha(1f).setDuration(140).start()
        }

        override fun onDraw(canvas: Canvas) {
            val sideBySide = width >= height * 0.8f
            val inset = 10f * density
            for (i in 0 until stops.size - 1) {
                if (sideBySide) rect.set(width * stops[i] + inset, inset, width * stops[i + 1] - inset, height - inset)
                else rect.set(inset, height * stops[i] + inset, width - inset, height * stops[i + 1] - inset)
                canvas.drawRoundRect(rect, 24f * density, 24f * density, fill)
                canvas.drawRoundRect(rect, 24f * density, 24f * density, outline)
            }
        }
    }
}
