package org.arkikeskus.launcher.ui

import android.graphics.Rect
import org.arkikeskus.launcher.model.AppItem

/**
 * Where the taskbar sits on screen while it is shown on home, so an app dragged from the drawer or
 * the home grid can be dropped onto it to pin it. The taskbar (feature/home) publishes its bounds and
 * how to pin; the drag code here only asks [isOver] and calls [pin], so neither depends on the other.
 */
object TaskbarDropTarget {
    /** The bar's bounds in screen coordinates, or null when it is not on screen. */
    @Volatile
    var bounds: Rect? = null

    /** Pins an app on the bar; false when it was already pinned or the bar is full. */
    @Volatile
    var onPin: ((AppItem) -> Boolean)? = null

    /** Whether the screen point ([x], [y]) is over the bar (with a little slack above it). */
    fun isOver(x: Float, y: Float, slackPx: Int = 0): Boolean {
        val r = bounds ?: return false
        if (onPin == null) return false
        return x >= r.left && x <= r.right && y >= r.top - slackPx && y <= r.bottom
    }

    fun pin(app: AppItem): Boolean = onPin?.invoke(app) == true

    fun clear() {
        bounds = null
        onPin = null
    }
}
