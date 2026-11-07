package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * The opened bar's row, scrollable when it holds more than the screen is wide (many recent apps,
 * pairs, the media icon…). It never gets in the way of an icon's own gestures: it scrolls only on a
 * clearly sideways swipe (not the diagonal drag up to split), never from a pinned icon (a sideways
 * drag there reorders), and not while an icon is held. A bar that fits doesn't scroll at all.
 */
@SuppressLint("ViewConstructor")
internal class TaskbarScroller(context: Context, private val maxWidth: () -> Int) : HorizontalScrollView(context) {

    /** Views whose sideways drags are their own (pinned icons: reorder). */
    val ownDrags = HashSet<View>()

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var ownDrag = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = false
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = maxWidth()
        val given = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) cap else minOf(given, cap)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec)
    }

    private val scrollable: Boolean get() = canScrollHorizontally(1) || canScrollHorizontally(-1)

    /** A swipe down on the bar (Settings ▸ Taskbar ▸ Swipe down to hide); null: off. */
    var onSwipeDown: (() -> Unit)? = null
    private var swipingDown = false
    private val hideDistance = 28f * context.resources.displayMetrics.density

    /** Clearly downwards from where the finger went down. */
    private fun isSwipeDown(ev: MotionEvent): Boolean {
        val dx = ev.x - downX
        val dy = ev.y - downY
        return onSwipeDown != null && dy > slop * 2 && dy > abs(dx) * 1.5f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipingDown = false
                ownDrag = scrollable && childUnder(ev.x + scrollX)?.let { it in ownDrags } == true
                if (scrollable) super.onInterceptTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                // Down, off the bar: taken from the icon (it gets a cancel) to hide the bar.
                if (isSwipeDown(ev)) {
                    swipingDown = true
                    return true
                }
                if (!scrollable || ownDrag) return false
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                // Sideways, and clearly so: an icon dragged up and over (split, float) keeps it.
                if (dx > slop && dx > dy * 2f) return super.onInterceptTouchEvent(ev)
                return false
            }
        }
        return scrollable && super.onInterceptTouchEvent(ev)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            // Straight onto the bar's own backdrop (between icons): nothing intercepted it.
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipingDown = false
            }
            MotionEvent.ACTION_MOVE -> if (!swipingDown && isSwipeDown(ev)) swipingDown = true
        }
        if (swipingDown) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    swipingDown = false
                    if (ev.y - downY > hideDistance) onSwipeDown?.invoke()
                }
                MotionEvent.ACTION_CANCEL -> swipingDown = false
            }
            return true
        }
        return super.onTouchEvent(ev) || onSwipeDown != null
    }

    /** The row's child under [x] (in the row's own coordinates). */
    private fun childUnder(x: Float): View? {
        val row = getChildAt(0) as? android.view.ViewGroup ?: return null
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (x >= c.left && x < c.right) return c
        }
        return null
    }
}
