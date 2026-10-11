package org.arkikeskus.launcher.feature.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/** The hinge of a half-folded screen: across it ([acrossScreen], laptop/tabletop) or down it (book). */
internal data class HalfFoldHinge(val acrossScreen: Boolean, val bounds: Rect)

/** Where the hinge is while the screen is half folded; null when it is flat, closed or not a foldable. */
@Composable
internal fun rememberHalfFoldHinge(): HalfFoldHinge? {
    val activity = LocalContext.current.hostActivityOrNull() ?: return null
    val layouts = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info: WindowLayoutInfo? by layouts.collectAsStateWithLifecycle(initialValue = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    if (fold.state != FoldingFeature.State.HALF_OPENED) return null
    return HalfFoldHinge(
        acrossScreen = fold.orientation == FoldingFeature.Orientation.HORIZONTAL,
        bounds = Rect(fold.bounds),
    )
}

private tailrec fun Context.hostActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivityOrNull()
    else -> null
}
