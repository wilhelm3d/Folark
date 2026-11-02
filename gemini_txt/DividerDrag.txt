package org.arkikeskus.launcher.recents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.feature.home.LockAccessibilityService
import kotlin.math.roundToInt

/**
 * Moves the split screen's divider, as a finger would: a touch from (x0, y0) to (x1, y1).
 *
 * Root is preferred when it's there: `input swipe` injects a real touch at the input layer, which
 * grabs and drags the divider far more reliably than an accessibility gesture (the service only
 * reports whether the gesture was *dispatched*, not whether it actually caught the divider, so peek
 * view came out hit-or-miss). Without root, the accessibility service is the fallback.
 */
internal object DividerDrag {
    suspend fun drag(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        if (withContext(Dispatchers.IO) { RootShell.available() }) {
            val byRoot = withContext(Dispatchers.IO) {
                RootShell.run(
                    "input swipe ${x0.roundToInt()} ${y0.roundToInt()} ${x1.roundToInt()} ${y1.roundToInt()} $DURATION_MS",
                ) != null
            }
            if (byRoot) return true
        }
        return withContext(Dispatchers.Main) { LockAccessibilityService.swipe(x0, y0, x1, y1, DURATION_MS) }
    }

    private const val DURATION_MS = 650L
}
