package org.arkikeskus.launcher.feature.home

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.WidgetPlacement

// Small workspace helpers: drop-target cache, widget size reporting, page dots (split out of Workspace.kt).

/** The last drop-target answer of a widget drag, keyed by what it was worked out for. */
internal class DropTargetCache {
    var placement: WidgetPlacement? = null
    var rowId: Long? = null
    var entries: List<HomeEntry>? = null
    var valid: Boolean = false
}

/** The home_items row id of a widget inside a stack (null for anything a stack cannot hold). */
/** Who an entry is, for its place in the page's composition (not where it sits). */
internal fun HomeEntry.slotKey(): String = when (this) {
    is PlacedApp -> "app:" + app.key
    is PlacedFolder -> "folder:$id"
    is PlacedShortcut -> "shortcut:$rowId"
    is PlacedWidget -> "widget:$rowId"
    is PendingWidget -> "pending:$rowId"
    is PlacedBuiltin -> "builtin:$rowId"
    is PlacedWidgetStack -> "stack:$rowId"
}

/**
 * The size-options bundle Launcher3 builds for a hosted widget: min/max width/height in dp plus (on
 * Android S+) the list of supported sizes. Collection widgets — a RemoteViewsService-backed
 * ListView/GridView/StackView such as WhatsApp's conversation list — render EMPTY when handed an empty
 * options bundle (the old `Bundle.EMPTY`), because the RemoteViewsService can't size its list. Adapted
 * from AOSP Launcher3 `WidgetSizes.getWidgetSizeOptions` (Apache-2.0).
 */
/** The size last given to each widget view, so a recomposition doesn't send the same size again. */
private val sentWidgetSizes = java.util.WeakHashMap<android.view.View, Long>()

/**
 * Gives a widget its size in dp, but only when it changed. Every send is a call into the system
 * and wakes the widget's app to redraw, and the page's views are updated on every recomposition.
 */
internal fun pushWidgetSize(view: android.appwidget.AppWidgetHostView, wDp: Int, hDp: Int) {
    if (wDp <= 0 || hDp <= 0) return
    val packed = (wDp.toLong() shl 32) or hDp.toLong()
    if (sentWidgetSizes[view] == packed) return
    sentWidgetSizes[view] = packed
    val opts = widgetSizeOptions(wDp, hDp)
    runCatching {
        view.updateAppWidgetSize(opts, wDp, hDp, wDp, hDp)
        view.updateAppWidgetOptions(opts)
    }
}

internal fun widgetSizeOptions(wDp: Int, hDp: Int): android.os.Bundle =
    android.os.Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
        // Tell the provider this widget is on the HOME screen (not keyguard). Some providers — notably
        // WhatsApp's chat-list collection — refuse to populate ("pending config activity") until the
        // host category is known. Launcher3 sets this too.
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(wDp.toFloat(), hDp.toFloat())),
            )
        }
    }

/**
 * True if this view tree contains an internally scrollable collection view — an `AdapterView`
 * (ListView / GridView / StackView / AdapterViewFlipper) or a ScrollView. Mirrors AOSP Launcher3
 * `LauncherAppWidgetHostView.checkScrollableRecursively` (Apache-2.0); used to let a collection widget
 * keep its own touch-scroll instead of the home swipe-up detector stealing the vertical drag.
 */
internal fun android.view.View.containsScrollableCollection(): Boolean = when (this) {
    is android.widget.AdapterView<*> -> true
    is android.widget.ScrollView -> true
    is android.widget.HorizontalScrollView -> true
    is android.view.ViewGroup -> (0 until childCount).any { getChildAt(it).containsScrollableCollection() }
    else -> false
}

@Composable
internal fun PageDots(count: Int, current: () -> Int, home: Int, modifier: Modifier = Modifier) {
    val currentIndex = current()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            // The home page's dot wears a ring, so the page HOME returns to is visible at a glance.
            val color = if (index == currentIndex) Color.White else Color.White.copy(alpha = 0.4f)
            Box(
                modifier = Modifier
                    .size(if (index == home) 9.dp else 7.dp)
                    .then(if (index == home) Modifier.border(1.5.dp, color, CircleShape).padding(2.dp) else Modifier)
                    .background(color = color, shape = CircleShape),
            )
        }
    }
}

/** Widget bounds applied optimistically (by row id) until the DB flow catches up — spans included,
 *  so an edit-frame resize shows on the hosted content immediately, not only after the round-trip. */
internal data class WidgetBounds(val page: Int, val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int)
