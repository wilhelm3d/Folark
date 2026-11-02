package org.arkikeskus.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State

/**
 * Notification badge counts by app badge key, read lazily. The home surfaces receive this one
 * stable object instead of the map itself: a new notification then only recomposes the places
 * that actually look a count up (`badges[key]`), not everything the map used to be passed
 * through. [enabled] mirrors the notification-dots setting; while off every lookup is null.
 */
@Stable
class BadgeCounts(
    private val counts: State<Map<String, Int>>,
    private val enabled: () -> Boolean,
) {
    operator fun get(key: String): Int? = if (enabled()) counts.value[key] else null
}

/**
 * Reads a badge count in a scope of its own and hands it to [content]. Looked up directly as an
 * argument (`badgeCount = badges[key]`), the read belongs to whatever surrounds the icon (a whole
 * home page, the dock) and a single new notification recomposed all of it.
 */
@Composable
internal fun WithBadge(count: () -> Int, content: @Composable (Int) -> Unit) {
    content(count())
}
