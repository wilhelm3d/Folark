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
    /** Quick actions in search: timers, alarms, events, directions, email, notes, numbers. */
    val actions: Boolean = true,
    /** Search finds Android's own settings pages (Wi-Fi, notifications, developer options, …). */
    val settingsPages: Boolean = true,
    /** The top app found shows its shortcuts (New message, New tab, …) right under it. */
    val inlineShortcuts: Boolean = true,
)
