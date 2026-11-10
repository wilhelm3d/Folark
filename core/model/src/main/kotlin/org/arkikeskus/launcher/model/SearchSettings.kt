package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** What the drawer's search looks through. */
@Immutable
data class SearchSettings(
    /** Include contacts in app-drawer search (gated by READ_CONTACTS; requested when enabled). */
    val contacts: Boolean = false,
    /** Search (both displays): offer app shortcuts and a "search the web" row. */
    val shortcuts: Boolean = true,
    val web: Boolean = true,
    /**
     * Which engine "Search the web" opens: Android's own search ([LauncherSettings.SEARCH_ENGINE_SYSTEM])
     * or a browser page of a named engine. Nothing is sent before the row is tapped.
     */
    val webEngine: String = LauncherSettings.SEARCH_ENGINE_SYSTEM,
    /** Include files and media from storage in search (gated by a read permission, requested when enabled). */
    val files: Boolean = false,
    /** Quick actions in search: timers, alarms, events, directions, email, notes, numbers, toggles. */
    val actions: Boolean = true,
    /** Search finds Android's own settings pages (Wi-Fi, notifications, developer options, …). */
    val settingsPages: Boolean = true,
    /** The top app found shows its shortcuts (New message, New tab, …) right under it. */
    val inlineShortcuts: Boolean = true,
)
