package org.arkikeskus.launcher.data

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference

/**
 * Where the app about to open was tapped, so it can grow out of that spot. Folark's screens record
 * the place of every lifted finger ([touch]); the taskbar records the icon itself ([view]). A launch
 * takes the place only if it was recorded a moment ago, so a later launch from elsewhere (a
 * notification, a widget) never grows out of an old spot.
 */
object LaunchOrigin {
    private var source: WeakReference<View>? = null
    private val bounds = Rect()
    private var recordedAt = 0L

    /** A finger lifted at ([rawX], [rawY]) on screen, over [view]'s window: an icon of [sizePx] there. */
    fun touch(view: View, rawX: Float, rawY: Float, sizePx: Int) {
        val half = sizePx / 2
        bounds.set(rawX.toInt() - half, rawY.toInt() - half, rawX.toInt() + half, rawY.toInt() + half)
        remember(view)
    }

    /** The icon [view] itself was tapped. */
    fun view(view: View) {
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        bounds.set(at[0], at[1], at[0] + view.width, at[1] + view.height)
        remember(view)
    }

    private fun remember(view: View) {
        source = WeakReference(view)
        recordedAt = SystemClock.uptimeMillis()
    }

    /** The recorded view and the icon's place on screen, once; null if none was recorded just now. */
    internal fun take(): Pair<View, Rect>? {
        val view = source?.get()
        val fresh = SystemClock.uptimeMillis() - recordedAt <= MAX_AGE_MS
        source = null
        if (view == null || !fresh || !view.isAttachedToWindow) return null
        return view to Rect(bounds)
    }

    private const val MAX_AGE_MS = 1_000L
}
