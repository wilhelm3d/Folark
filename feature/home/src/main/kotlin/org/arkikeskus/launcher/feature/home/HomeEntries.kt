package org.arkikeskus.launcher.feature.home

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings

// What the home screen shows (split out of HomeViewModel.kt): the placed entries, the state the
// screen is drawn from, and the footprints of the built-in widgets.

/** Default grid footprint of the built-in smartspace widget (clamped to the column count). */
internal const val SMARTSPACE_DEFAULT_SPAN_X = 4
internal const val SMARTSPACE_DEFAULT_SPAN_Y = 2

/** Smallest allowed smartspace size — below 3 columns the clock + event row no longer fit. */
internal const val SMARTSPACE_MIN_SPAN_X = 3

/** Edit-frame default width of the built-in notifications widget (the picker ADDS it full-width —
 *  a narrower footprint can't center its icon group on an odd column count). */
internal const val NOTIFICATIONS_DEFAULT_SPAN_X = 4
internal const val NOTIFICATIONS_DEFAULT_SPAN_Y = 1

/** Smallest allowed notifications-widget width — one icon + the overflow chip still fit. */
internal const val NOTIFICATIONS_MIN_SPAN_X = 2

/** Default AND minimum footprint of the built-in battery widget — app-icon sized. */
internal const val BATTERY_SPAN = 1

/** The people widget defaults to a few rows of tiles; the user stretches it to the whole grid. */
internal const val PEOPLE_DEFAULT_SPAN_Y = 3

/** Narrowest people widget: two quiet tiles, or one tile with content. */
internal const val PEOPLE_MIN_SPAN_X = 2

/** Something placed on a home page: an app, a folder, a shortcut or one of the [WidgetEntry] kinds. */
@Immutable
sealed interface HomeEntry {
    val page: Int
    val cellX: Int
    val cellY: Int
}

/**
 * A widget-like entry (an app widget, its not-yet-bound placeholder, a built-in widget or a stack of
 * widgets): it has its own home_items row, [rowId], and covers [spanX]×[spanY] cells. Apps, folders
 * and shortcuts are not: they take one cell each.
 */
@Immutable
internal sealed interface WidgetEntry : HomeEntry {
    val rowId: Long
    val spanX: Int
    val spanY: Int
}

/** An app shortcut placed at a free cell on a home page. */
@Immutable
internal data class PlacedApp(
    val app: AppItem,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
) : HomeEntry

/** A folder placed at a free cell, holding [apps] (resolved, in order). */
@Immutable
data class PlacedFolder(
    val id: Long,
    val name: String,
    val apps: List<AppItem>,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
) : HomeEntry

/** A pinned deep shortcut placed at a free cell ([rowId] is the home_items row, used to move/remove). */
@Immutable
data class PlacedShortcut(
    val rowId: Long,
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    val label: String,
    val icon: ImageBitmap?,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    /** Non-null → a launcher-made shortcut that opens this app in a floating window (drawn with the
     *  app's own icon), not a system deep shortcut. */
    val floatingApp: AppItem? = null,
    /** Non-null → a shortcut for this saved app pair; [pairApps] are its two apps, for the icon. */
    val appPair: org.arkikeskus.launcher.model.AppPair? = null,
    val pairApps: Pair<AppItem, AppItem>? = null,
    /** The pair's optional third (floating) app, drawn small between the other two. */
    val pairThirdApp: AppItem? = null,
) : HomeEntry

/** A bound app widget placed at a home cell, occupying [spanX]×[spanY] cells. */
@Immutable
internal data class PlacedWidget(
    override val rowId: Long,
    val appWidgetId: Int,
    val provider: ComponentName,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    override val spanX: Int,
    override val spanY: Int,
) : WidgetEntry

/**
 * A restored widget whose device-local `appWidgetId` isn't bound on this device yet. Rendered as a
 * tap-to-set-up placeholder occupying [spanX]×[spanY] cells until the user re-binds it (see the restore
 * flow in HomeScreen). Its row already carries the [provider] and spans from the backup.
 */
@Immutable
data class PendingWidget(
    override val rowId: Long,
    val provider: ComponentName,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    override val spanX: Int,
    override val spanY: Int,
) : WidgetEntry

/** A built-in launcher widget ([type] — smartspace or notifications), occupying [spanX]×[spanY]. */
@Immutable
internal data class PlacedBuiltin(
    override val rowId: Long,
    val type: String,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    override val spanX: Int,
    override val spanY: Int,
) : WidgetEntry

/** A Nothing OS 3 style stack of widgets occupying [spanX]×[spanY] cells. Swipable vertically. */
@Immutable
internal data class PlacedWidgetStack(
    override val rowId: Long,
    val items: List<HomeEntry>,
    override val page: Int,
    override val cellX: Int,
    override val cellY: Int,
    override val spanX: Int,
    override val spanY: Int,
) : WidgetEntry

@Immutable
data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val dockApps: List<AppItem> = emptyList(),
    val entries: List<HomeEntry> = emptyList(),
    val pageCount: Int = 1,
    /** The page HOME returns to, clamped to the pages that exist. */
    val homePage: Int = 0,
    /** Pages with any stored row on them — including rows that resolved to nothing (an uninstalled
     *  shortcut's), which [entries] leaves out but the page menu must still treat as occupied. */
    val occupiedPages: Set<Int> = emptySet(),
    /** False for the placeholder shown before settings and the layout have been read. */
    val loaded: Boolean = false,
)
