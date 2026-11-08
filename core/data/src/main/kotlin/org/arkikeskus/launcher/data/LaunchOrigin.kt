package org.arkikeskus.launcher.data

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference
import org.arkikeskus.launcher.model.catching

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

    /** A tile of [screen] bounds (screen coordinates) on [view]'s window was tapped — a Recents card. */
    fun rect(view: View, screen: Rect) {
        bounds.set(screen)
        remember(view)
    }

    /**
     * Launch options that grow the opening app out of the recorded place (once), or null when
     * nothing was recorded just now. [reveal]: a clip reveal instead of a scale-up.
     */
    fun takeOptions(reveal: Boolean = false): android.os.Bundle? {
        val (view, r) = take() ?: return null
        return catching {
            val at = IntArray(2)
            view.getLocationOnScreen(at)
            val x = r.left - at[0]
            val y = r.top - at[1]
            if (reveal) {
                android.app.ActivityOptions.makeClipRevealAnimation(view, x, y, r.width(), r.height()).toBundle()
            } else {
                android.app.ActivityOptions.makeScaleUpAnimation(view, x, y, r.width(), r.height()).toBundle()
            }
        }.getOrNull()
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
