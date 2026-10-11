package org.arkikeskus.launcher.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry in the home layout. A single table holds three shapes, distinguished by [containerId]
 * and [folderName]:
 *  - **App on home**: [containerId] = [HOME], [folderName] = null, package set, at a home cell.
 *  - **Folder on home**: [containerId] = [HOME], [folderName] != null, package empty, at a home cell.
 *    Its [id] is the container id its children point at.
 *  - **App inside a folder**: [containerId] = the folder's id; [page]/[cellX]/[cellY] are the in-folder
 *    order rather than a home cell.
 *  - **Pinned deep shortcut on home**: [containerId] = [HOME], [shortcutId] != null, [packageName] is
 *    the publishing app, at a home cell. Launched via LauncherApps.startShortcut.
 *  - **Widget stack on home**: [containerId] = [HOME], [builtinType] = [BUILTIN_WIDGET_STACK]; it owns
 *    the spanX×spanY footprint. Its [id] is the container id of the widgets stacked in it.
 *  - **Widget inside a stack**: [containerId] = the stack's id, [page] = 0, [cellX] = 0 and [cellY] =
 *    its order in the stack. It is drawn at the stack's size; its own spans are not used.
 *
 * The unique index on (containerId, page, cellX, cellY) enforces "one item per cell" *within each
 * container* (the home grid and each folder are independent cell spaces) — see [HomeLayoutRepository].
 */
@Entity(
    tableName = "home_items",
    indices = [Index(value = ["screenType", "containerId", "page", "cellX", "cellY"], unique = true)],
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val containerId: Long = HOME,
    val folderName: String? = null,
    val packageName: String = "",
    val className: String = "",
    val userSerial: Long = 0,
    /** Non-null → this row is a pinned deep shortcut published by [packageName]. */
    val shortcutId: String? = null,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    /** Non-null → this row is a bound app widget occupying spanX×spanY cells. */
    val appWidgetId: Int? = null,
    /** The widget provider's ComponentName.flattenToString() (set when [appWidgetId] is). */
    val widgetProvider: String? = null,
    /** Non-null → a built-in launcher widget (see [BUILTIN_SMARTSPACE]) occupying spanX×spanY cells. */
    val builtinType: String? = null,
    /** Whether this item is on the OUTER or INNER screen. Defaults to OUTER for backwards compatibility. */
    @ColumnInfo(defaultValue = "OUTER") val screenType: String = "OUTER",
) {
    /** Matches AppItem.key so app entities can be resolved against the live app list. */
    val key: String get() = "$packageName/$className/$userSerial"

    val isFolder: Boolean get() = folderName != null

    val isShortcut: Boolean get() = shortcutId != null

    val isWidget: Boolean get() = appWidgetId != null

    val isBuiltin: Boolean get() = builtinType != null

    /** True for any row that occupies a spanX×spanY footprint (an app widget — bound or a restored
     *  placeholder — or a built-in widget); everything else is 1×1. */
    val hasFootprint: Boolean get() = widgetProvider != null || builtinType != null

    companion object {
        /** [containerId] sentinel meaning "placed directly on the home screen". */
        const val HOME = -1L

        /** [builtinType] of the clock + date + next-calendar-event widget. */
        const val BUILTIN_SMARTSPACE = "smartspace"

        /** [builtinType] of the notifications widget (active notifications as app icons). */
        const val BUILTIN_NOTIFICATIONS = "notifications"

        /** [builtinType] of the battery widget (a ring gauge + percent, app-icon sized). */
        const val BUILTIN_BATTERY = "battery"

        /** [builtinType] of the people widget (conversations grouped by person, as quiet tiles). */
        const val BUILTIN_PEOPLE = "people"

        /** [builtinType] of the Dot Matrix Retro Style clock widget. */
        const val BUILTIN_NOTHING_CLOCK = "nothing_clock"

        /** [builtinType] of the Category Card Interface weather & briefing card widget. */
        const val BUILTIN_SAMSUNG_WEATHER = "samsung_weather"

        /** [builtinType] of the Category Card Interface briefing card notification widget. */
        const val BUILTIN_NOTIFICATION_WIDGET = "notification_widget"

        /** [builtinType] of the Pro Interactive Notification List Widget. */
        const val BUILTIN_INTERACTIVE_NOTIFICATIONS = "interactive_notifications"

        /** [builtinType] of the now-playing widget (track, cover art and transport controls). */
        const val BUILTIN_MEDIA = "media"

        /** [builtinType] of the most-used-apps widget (a grid that reorders itself). */
        const val BUILTIN_TOP_APPS = "top_apps"

        /** [builtinType] of the device-status widget (battery, storage, memory, Bluetooth batteries). */
        const val BUILTIN_STATS = "device_stats"

        /** [builtinType] of the weather day widget, and of its big full-forecast variant (same widget, larger default size). */
        const val BUILTIN_WEATHER_DAY = "weather_day"
        const val BUILTIN_WEATHER_BIG = "weather_big"

        /** [builtinType] of the quick-note widget. */
        const val BUILTIN_NOTE = "note"

        /** [builtinType] of a Nothing OS 3 style stackable widget container. */
        const val BUILTIN_WIDGET_STACK = "widget_stack"

        /**
         * Prefix of the [shortcutId] of a launcher-made shortcut that opens its app in a floating
         * window. The row is shaped like a pinned deep shortcut ([packageName] + [userSerial]), with
         * the app's activity class after the prefix — but it is NOT a system shortcut: it is never
         * pinned in, resolved by, or started through LauncherApps' shortcut APIs.
         */
        const val FLOATING_SHORTCUT_PREFIX = "folark.floating:"

        fun floatingShortcutId(className: String): String = FLOATING_SHORTCUT_PREFIX + className

        /**
         * Prefix of the [shortcutId] of a home shortcut for a saved app pair (the pair's id follows).
         * Like a floating shortcut it is the launcher's own, not a system shortcut. [packageName] and
         * [userSerial] are the pair's first app, so the row goes when that app is uninstalled.
         */
        const val APP_PAIR_SHORTCUT_PREFIX = "folark.pair:"

        fun appPairShortcutId(pairId: String): String = APP_PAIR_SHORTCUT_PREFIX + pairId

        /** The app pair id of a pair shortcut id, or null for any other shortcut id. */
        fun appPairShortcutPairId(shortcutId: String?): String? =
            shortcutId?.takeIf { it.startsWith(APP_PAIR_SHORTCUT_PREFIX) }?.removePrefix(APP_PAIR_SHORTCUT_PREFIX)

        /** True for a shortcut the launcher made itself (floating, app pair) — one the system's
         *  shortcut APIs know nothing about. */
        fun isLauncherShortcutId(shortcutId: String?): Boolean =
            floatingShortcutClass(shortcutId) != null || appPairShortcutPairId(shortcutId) != null

        /** The activity class of a floating shortcut id, or null for a real (system) shortcut id. */
        fun floatingShortcutClass(shortcutId: String?): String? =
            shortcutId?.takeIf { it.startsWith(FLOATING_SHORTCUT_PREFIX) }?.removePrefix(FLOATING_SHORTCUT_PREFIX)
    }
}
