package org.arkikeskus.launcher.feature.home

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MAX_PINNED
import org.arkikeskus.launcher.model.AppItem

// Pinning on the taskbar itself: pin and unpin from the long-press menu, a drop from the drawer or
// home, reordering by dragging a pinned icon along the bar, and the same as accessibility actions
// (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/** The pinned apps' keys in bar order (the full setup, not the cover screen's slimmed one). */
internal fun AppTaskbar.pinnedKeys(): List<String> = setup?.pinned?.map { it.key }.orEmpty()

internal fun AppTaskbar.isPinned(app: AppItem): Boolean = setup?.pinned?.any { it.key == app.key } == true

private fun AppTaskbar.savePinned(keys: List<String>) {
    val repo = settingsRepo ?: return
    scope.launch { repo.setTaskbarPinned(keys.distinct().take(MAX_PINNED)) }
}

/** Adds [app] at the end of the pinned apps; false when it is already there or the bar is full. */
internal fun AppTaskbar.pin(app: AppItem): Boolean {
    val keys = pinnedKeys()
    if (app.key in keys) return false
    if (keys.size >= MAX_PINNED) {
        Toast.makeText(context, R.string.taskbar_full, Toast.LENGTH_SHORT).show()
        return false
    }
    savePinned(keys + app.key)
    return true
}

internal fun AppTaskbar.unpin(app: AppItem) {
    val keys = pinnedKeys()
    if (app.key !in keys) return
    savePinned(keys - app.key)
}

/** Moves pinned [app] to position [to] (clamped) among the pinned apps. */
internal fun AppTaskbar.movePinned(app: AppItem, to: Int) {
    val keys = pinnedKeys().toMutableList()
    val from = keys.indexOf(app.key)
    if (from < 0) return
    val target = to.coerceIn(0, keys.size - 1)
    if (target == from) return
    keys.removeAt(from)
    keys.add(target, app.key)
    savePinned(keys)
}

/** "Pin to taskbar" / "Unpin from taskbar" for the long-press menu. */
internal fun AppTaskbar.pinMenuRow(app: AppItem): Pair<String, () -> Unit> =
    if (isPinned(app)) {
        context.getString(R.string.taskbar_unpin) to { unpin(app) }
    } else {
        context.getString(R.string.taskbar_pin) to { pin(app) }
    }

/**
 * What a TalkBack user can do with an app icon on the bar besides a tap (open): open it in split
 * screen or a floating window, its shortcuts, pin or unpin it, and move a pinned one along the bar.
 */
internal fun AppTaskbar.addIconAccessibility(
    view: View,
    app: AppItem,
    onSplit: () -> Unit,
    onFloat: () -> Unit,
    onShortcuts: () -> Unit,
) {
    val split = View.generateViewId()
    val float = View.generateViewId()
    val shortcuts = View.generateViewId()
    val pinToggle = View.generateViewId()
    val left = View.generateViewId()
    val right = View.generateViewId()
    view.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            fun add(id: Int, label: Int) = info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, context.getString(label)))
            add(split, R.string.taskbar_a11y_split)
            add(float, R.string.taskbar_a11y_float)
            add(shortcuts, R.string.taskbar_a11y_shortcuts)
            val keys = pinnedKeys()
            val at = keys.indexOf(app.key)
            add(pinToggle, if (at >= 0) R.string.taskbar_unpin else R.string.taskbar_pin)
            if (at > 0) add(left, R.string.taskbar_a11y_move_left)
            if (at >= 0 && at < keys.size - 1) add(right, R.string.taskbar_a11y_move_right)
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val at = pinnedKeys().indexOf(app.key)
            when (action) {
                split -> onSplit()
                float -> onFloat()
                shortcuts -> onShortcuts()
                pinToggle -> if (at >= 0) unpin(app) else pin(app)
                left -> movePinned(app, at - 1)
                right -> movePinned(app, at + 1)
                else -> return super.performAccessibilityAction(host, action, args)
            }
            return true
        }
    }
}
