package org.arkikeskus.launcher.recents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.feature.home.LockAccessibilityService
import kotlin.math.roundToInt

/**
 * Moves the split screen's divider, as a finger would: a touch from (x0, y0) to (x1, y1) made by
 * Folark's accessibility service, or with root when the service can't.
 */
internal object DividerDrag {
    suspend fun drag(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val byService = withContext(Dispatchers.Main) { LockAccessibilityService.swipe(x0, y0, x1, y1, DURATION_MS) }
        if (byService) return true
        return withContext(Dispatchers.IO) {
            RootShell.available() && RootShell.run(
                "input swipe ${x0.roundToInt()} ${y0.roundToInt()} ${x1.roundToInt()} ${y1.roundToInt()} $DURATION_MS",
            ) != null
        }
    }

    private const val DURATION_MS = 650L
}
