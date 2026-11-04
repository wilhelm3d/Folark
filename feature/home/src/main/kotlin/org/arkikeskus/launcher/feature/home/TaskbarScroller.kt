package org.arkikeskus.launcher.feature.home

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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!scrollable) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                ownDrag = childUnder(ev.x + scrollX)?.let { it in ownDrags } == true
                super.onInterceptTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (ownDrag) return false
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                // Sideways, and clearly so: an icon dragged up and over (split, float) keeps it.
                if (dx > slop && dx > dy * 2f) return super.onInterceptTouchEvent(ev)
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
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
