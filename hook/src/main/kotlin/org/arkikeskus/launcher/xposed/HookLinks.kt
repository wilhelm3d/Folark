package org.arkikeskus.launcher.xposed

import android.net.Uri

/**
 * What the app and the Vector module share, in one public place: the provider URIs the app
 * notifies (and the hooked side watches) when a setting the module reads has changed. The hook
 * objects themselves stay internal to this module; the app only ever touches these.
 */
object HookLinks {
    /** The module in the system launcher watches this (Replace Android's Recents, search). */
    val state: Uri get() = RecentsHookState.STATE_URI

    /** The module in System UI watches this (status-bar end padding). */
    val statusBarPadding: Uri get() = StatusBarPaddingHook.CHANGED_URI

    /** The module in System UI watches this (divider stops, free divider, marks). */
    val dividerStops: Uri get() = DividerStopsHook.CHANGED_URI
}
