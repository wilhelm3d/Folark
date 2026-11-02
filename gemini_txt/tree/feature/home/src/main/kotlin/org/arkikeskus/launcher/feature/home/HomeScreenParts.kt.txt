package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.arkikeskus.launcher.model.WidgetPlacement
import org.arkikeskus.launcher.ui.component.rememberParallaxOffset
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalHomeCovered

// Small home-screen pieces: pending widget binds and the parallax runner (split out of HomeScreen.kt).

/** In-flight widget bind/configure state. [restoreRowId] non-null = re-binding a restored placeholder
 *  row (from a backup) rather than adding a new widget. */
internal data class PendingWidgetBind(
    val appWidgetId: Int,
    val provider: AppWidgetProviderInfo,
    val restoreRowId: Long?,
    val placement: WidgetPlacement? = null,
    /** Non-null = the new widget goes into this widget stack rather than onto a grid [placement]. */
    val stackId: Long? = null,
)

/** Bundles [PendingWidgetBind] into saved instance state so an in-flight bind survives process death
 *  while the system bind dialog or the widget's configuration activity is in the foreground. */
internal val PendingWidgetBindSaver = Saver<PendingWidgetBind?, android.os.Bundle>(
    save = { p ->
        p?.let {
            android.os.Bundle().apply {
                putInt("id", it.appWidgetId)
                putParcelable("provider", it.provider)
                putLong("row", it.restoreRowId ?: -1L)
                putLong("stack", it.stackId ?: -1L)
                it.placement?.let { p ->
                    putIntArray("placement", intArrayOf(p.page, p.cellX, p.cellY, p.spanX, p.spanY))
                }
            }
        }
    },
    restore = { b ->
        @Suppress("DEPRECATION")
        val provider = b.getParcelable<AppWidgetProviderInfo>("provider")
        provider?.let {
            PendingWidgetBind(b.getInt("id"), it, b.getLong("row").takeIf { row -> row >= 0 },
                b.getIntArray("placement")?.takeIf { p -> p.size == 5 }?.let { p ->
                    WidgetPlacement(p[0], p[1], p[2], p[3], p[4])
                },
                b.getLong("stack", -1L).takeIf { stack -> stack >= 0 })
        }
    },
)

/**
 * Runs the parallax sensor while home is in front, not covered and not in battery saver, and hands
 * its offset (one [State] for the screen's lifetime) to [out] for the home layer to read.
 */
@Composable
internal fun HomeParallax(enabledSetting: Boolean, out: MutableState<State<Offset>?>) {
    var homeResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        homeResumed = true
        onPauseOrDispose { homeResumed = false }
    }
    val active = enabledSetting && homeResumed && !LocalBatterySaver.current && !LocalHomeCovered.current
    val offset = rememberParallaxOffset(enabled = active)
    SideEffect { if (out.value !== offset) out.value = offset }
}
