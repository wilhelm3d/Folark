package org.arkikeskus.launcher.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** Whether this device can blur what is behind a window (Android 12+ with blur enabled by the
 *  system — battery saver and some vendor builds switch it off). */
fun supportsWindowBlur(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return runCatching {
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }.getOrDefault(false)
}

/**
 * Blurs whatever is behind the launcher window — i.e. the wallpaper — by up to [maxRadiusDp],
 * scaled by [fraction] (0 = sharp, 1 = full blur). [fraction] is read in a snapshot flow, so it can
 * be an animation value (the drawer's open progress) without recomposing the caller.
 *
 * This is the only way a third-party launcher gets a real frosted backdrop: the wallpaper is a
 * separate system window, so no in-app RenderEffect can reach it. A no-op below Android 12; where
 * the system has blur disabled the flag is simply ignored.
 */
@Composable
fun WindowBlurBehind(maxRadiusDp: Float, fraction: () -> Float) {
    val context = LocalContext.current
    val maxRadiusPx = with(LocalDensity.current) { maxRadiusDp.coerceAtLeast(0f) * density }
    val currentFraction = rememberUpdatedState(fraction)
    val window = context.findActivity()?.window ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    LaunchedEffect(window, maxRadiusPx) {
        if (maxRadiusPx <= 0f) {
            window.setBlurBehind(0)
            return@LaunchedEffect
        }
        snapshotFlow {
            // Quantised: every distinct radius is a window relayout, so don't chase every pixel.
            val raw = currentFraction.value().coerceIn(0f, 1f) * maxRadiusPx
            (raw / BLUR_STEP_PX).roundToInt() * BLUR_STEP_PX
        }.distinctUntilChanged().collect { radius -> window.setBlurBehind(radius) }
    }
    DisposableEffect(window) {
        onDispose { window.setBlurBehind(0) }
    }
}

private const val BLUR_STEP_PX = 4

/**
 * Sets the blur radius and the blur flag in one window update, the flag only while there is
 * something to blur. Setting them together also means a second blur (the widget picker's) that
 * clears both when it closes can't leave the drawer's blur switched off afterwards.
 */
private fun android.view.Window.setBlurBehind(radiusPx: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val params = attributes
        val flag = WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val flags = if (radiusPx > 0) params.flags or flag else params.flags and flag.inv()
        if (params.blurBehindRadius != radiusPx || params.flags != flags) {
            params.blurBehindRadius = radiusPx
            params.flags = flags
            attributes = params
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
