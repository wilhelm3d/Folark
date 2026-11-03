package org.arkikeskus.launcher.feature.home

import android.view.DragEvent
import android.os.UserHandle
import android.os.Bundle
import android.graphics.RectF
import android.content.pm.ShortcutInfo
import android.content.pm.LauncherApps
import android.content.ComponentName
import android.content.ClipDescription
import android.content.ClipData
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_FLOAT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_NONE
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_PEEK
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT_LEFT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_SPLIT_RIGHT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_THREE
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.ACTION_TOP
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.DRAG_ALPHA
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.EXTRA_DISALLOW_HIT_REGION
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.EXTRA_DRAG_PENDING_INTENT
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.GHOST_ALPHA
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MIME_ACTIVITY

// Dragging from the taskbar: system drag-and-drop, the ghost icon, drop chips and edge zones (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/**
 * Whether a split drag goes to Android's drop zones: when they are on, except over an open split
 * screen with "third app" off (the drop zones would offer a third place; Folark's own split
 * replaces one side instead).
 */
internal fun AppTaskbar.useDropZones(s: TaskbarSetup): Boolean = s.systemDrag && (s.threeApp || !splitOpen)

// ------------------------------------------------------------------ Android's drag and drop

/**
 * Hands [source] to Android's drag and drop, as Motorola's taskbar does: an item of type [mime]
 * with [data] (a launch link and who it is for). Over an app, Android shows its split drop zones
 * and opens the item in the half it is dropped on. False if Android refused the drag.
 */
internal fun AppTaskbar.startSystemDrag(source: View, label: CharSequence, mime: String, data: Intent, fixed: Boolean): Boolean {
    barRegion()?.let { data.putExtra(EXTRA_DISALLOW_HIT_REGION, it) }
    val clip = ClipData(ClipDescription(label, arrayOf(mime)), ClipData.Item(data))
    source.setOnDragListener { _, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENDED -> {
                systemDragging = false
                source.setOnDragListener(null)
                hideShortcuts()
                if (!fixed) {
                    expanded = false
                    refresh()
                }
                true
            }
            else -> false
        }
    }
    systemDragging = true
    val started = runCatching {
        source.startDragAndDrop(
            clip,
            View.DragShadowBuilder(source),
            null,
            View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE,
        )
    }.getOrDefault(false)
    if (!started) {
        systemDragging = false
        source.setOnDragListener(null)
    }
    return started
}

/** The bar's place on screen: nothing may be dropped there. */
internal fun AppTaskbar.barRegion(): RectF? {
    val row = barRow ?: return null
    if (!row.isAttachedToWindow) return null
    val at = IntArray(2)
    row.getLocationOnScreen(at)
    return RectF(at[0].toFloat(), at[1].toFloat(), (at[0] + row.width).toFloat(), (at[1] + row.height).toFloat())
}

/** What Android needs to open [app] where it is dropped; null if no launch link could be made. */
internal fun AppTaskbar.appDragData(app: AppItem): Intent? {
    val link = mainActivityLink(app) ?: return null
    return Intent()
        .putExtra(EXTRA_DRAG_PENDING_INTENT, link)
        .putExtra(Intent.EXTRA_USER, app.user)
}

/** The launch link Android's own launcher uses; for Folark's own user, a plain one as a fallback. */
internal fun AppTaskbar.mainActivityLink(app: AppItem): PendingIntent? {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val system = runCatching {
        LauncherApps::class.java
            .getMethod("getMainActivityLaunchIntent", ComponentName::class.java, Bundle::class.java, UserHandle::class.java)
            .invoke(launcherApps, app.componentName, null, app.user) as? PendingIntent
    }.getOrNull()
    if (system != null) return system
    if (app.user != Process.myUserHandle()) return null
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.componentName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return runCatching {
        PendingIntent.getActivity(
            context,
            app.key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }.getOrNull()
}

/**
 * What Android needs to open the shortcut [info] where it is dropped. Android takes items typed
 * as shortcuts only from system apps, so the shortcut goes as an app item ([MIME_ACTIVITY])
 * carrying the shortcut's own launch link.
 */
internal fun AppTaskbar.shortcutDragData(info: ShortcutInfo): Intent? {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
    val link = runCatching { launcherApps.getShortcutIntent(info.`package`, info.id, null, info.userHandle) }.getOrNull()
        ?: return null
    return Intent()
        .putExtra(EXTRA_DRAG_PENDING_INTENT, link)
        .putExtra(Intent.EXTRA_USER, info.userHandle)
}

// ------------------------------------------------------------------ app shortcuts

/** Puts a copy of [source] (and the [hint]) in a full-screen window that ignores touches. */
internal fun AppTaskbar.ghostStart(source: ImageView, hint: TextView) {
    if (ghostWindow != null) return
    val size = source.width.takeIf { it > 0 } ?: dp(48)
    val root = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
    }
    val copy = ImageView(context).apply {
        setImageDrawable(source.drawable)
        scaleType = source.scaleType
        setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
        alpha = GHOST_ALPHA
        elevation = dpf(8f)
    }
    root.addView(copy, FrameLayout.LayoutParams(size, size))
    (hint.parent as? android.view.ViewGroup)?.removeView(hint)
    hint.visibility = View.INVISIBLE
    root.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    if (setup?.edgeSnap == true) addEdgeZones(root) else addDropChips(root)
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { passThroughAlpha?.let { alpha = it } }
    if (runCatching { windowManager.addView(root, params) }.isSuccess) {
        ghostWindow = root
        ghostIcon = copy
        source.alpha = DRAG_ALPHA
    }
}

/** Keeps the copy just above the finger at ([rawX], [rawY]) on screen, the hint above it. */
internal fun AppTaskbar.ghostMove(rawX: Float, rawY: Float, hint: TextView) {
    val root = ghostWindow ?: return
    val copy = ghostIcon ?: return
    root.getLocationOnScreen(ghostOrigin)
    val half = (copy.layoutParams?.width ?: dp(48)) / 2f
    copy.x = rawX - ghostOrigin[0] - half
    copy.y = rawY - ghostOrigin[1] - half * 2 - dp(16)
    if (hint.width > 0) {
        hint.x = rawX - ghostOrigin[0] - hint.width / 2f
        hint.y = copy.y - hint.height - dp(10)
    }
}

internal fun AppTaskbar.ghostEnd() {
    ghostWindow?.let { runCatching { windowManager.removeView(it) } }
    ghostWindow = null
    ghostIcon = null
    splitChip = null
    floatChip = null
    leftZone = null
    rightZone = null
    topZone = null
    bottomZone = null
    peekZone = null
    threeZone = null
}

// ---------------------------------------------------------------- drag drop-zone chips

/**
 * Two chips in the dragged-icon window while an app icon is dragged: "Floating window" on the
 * left, "Split screen" on the right — the sides the drag arms. The one matching the current
 * [action] lights up. They are only a guide (the window ignores touches); the gesture direction
 * (and, over an app, Android's own drop zones) does the launching.
 */
internal fun AppTaskbar.addDropChips(root: FrameLayout) {
    val top = (context.resources.displayMetrics.heightPixels * 0.56f).toInt()
    floatChip = makeDropChip(context.getString(R.string.taskbar_drop_float))
    splitChip = makeDropChip(context.getString(R.string.taskbar_drop_split))
    root.addView(
        floatChip,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START; leftMargin = dp(24); topMargin = top
        },
    )
    root.addView(
        splitChip,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END; rightMargin = dp(24); topMargin = top
        },
    )
    highlightDropChips(ACTION_NONE)
}

internal fun AppTaskbar.makeDropChip(label: String): TextView = TextView(context).apply {
    text = label
    textSize = 13f
    gravity = Gravity.CENTER
    setPadding(dp(16), dp(10), dp(16), dp(10))
    elevation = dpf(6f)
}

/** Lights up the chip for [action] (ACTION_FLOAT = left, ACTION_SPLIT = right), dims the other. */
internal fun AppTaskbar.highlightDropChips(action: Int) {
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_200)
    } else Color.rgb(180, 200, 255)
    fun style(chip: TextView?, armed: Boolean) {
        chip ?: return
        chip.background = GradientDrawable().apply {
            cornerRadius = dpf(20f)
            setColor(if (armed) accent else Color.argb(205, 20, 24, 30))
            if (!armed) setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }
        chip.setTextColor(if (armed) Color.BLACK else Color.WHITE)
        chip.animate()
            .scaleX(if (armed) 1.12f else 1f).scaleY(if (armed) 1.12f else 1f)
            .alpha(if (armed) 1f else 0.82f).setDuration(110).start()
    }
    style(floatChip, action == ACTION_FLOAT)
    style(splitChip, action == ACTION_SPLIT)
}

// ---------------------------------------------------------------- edge-snap glass drop zones

/**
 * Four frosted-glass drop zones hugging the screen edges while an icon is dragged (edge-snap):
 * left / right half (split), top (full screen, or a third app over a split), bottom (floating
 * window). The one the finger is over lights up with the accent and shows its label. Only a
 * guide (the window ignores touches); [edgeAction] decides what the drop does.
 */
internal fun AppTaskbar.addEdgeZones(root: FrameLayout) {
    // Full-region previews of where the app will land — the same Windows-snap look as Folark's
    // Recents: a glass region fills the target half/area, and only the one under the finger shows.
    val m = context.resources.displayMetrics
    val w = m.widthPixels
    val h = m.heightPixels
    val margin = dp(12)
    val tall = h - dp(120) // clear of the bar at the bottom
    fun region(label: Int) = makeEdgeZone(context.getString(label))
    leftZone = region(R.string.taskbar_snap_left).also {
        root.addView(it, FrameLayout.LayoutParams(w / 2 - margin, tall, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = margin })
    }
    rightZone = region(R.string.taskbar_snap_right).also {
        root.addView(it, FrameLayout.LayoutParams(w / 2 - margin, tall, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = margin })
    }
    topZone = region(R.string.taskbar_snap_top).also {
        root.addView(it, FrameLayout.LayoutParams(w - margin * 2, tall, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = margin })
    }
    bottomZone = region(R.string.taskbar_snap_float).also {
        root.addView(it, FrameLayout.LayoutParams((w * 0.5f).toInt(), (h * 0.34f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(96) })
    }
    peekZone = region(R.string.taskbar_snap_peek).also {
        root.addView(it, FrameLayout.LayoutParams((w * 0.84f).toInt(), tall, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = margin })
    }
    threeZone = region(R.string.taskbar_snap_three).also {
        root.addView(it, FrameLayout.LayoutParams(w - margin * 2, tall, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = margin })
    }
    highlightEdgeZones(ACTION_NONE)
}

internal fun AppTaskbar.makeEdgeZone(label: String): FrameLayout = FrameLayout(context).apply {
    elevation = dpf(4f)
    addView(
        TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            tag = "label"
        },
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
    )
}

/** Lights the glass zone the finger is over (frosted when idle, accent and grown when armed). */
internal fun AppTaskbar.highlightEdgeZones(action: Int) {
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_200)
    } else Color.rgb(180, 200, 255)
    // Only the region under the finger shows (the rest are hidden), exactly like Recents.
    fun style(zone: FrameLayout?, armed: Boolean) {
        zone ?: return
        if (!armed) {
            if (zone.visibility != View.GONE) zone.visibility = View.GONE
            return
        }
        zone.background = GradientDrawable().apply {
            cornerRadius = dpf(26f)
            setColor(Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent)))
            setStroke(dp(2), Color.argb(225, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        zone.findViewWithTag<TextView>("label")?.apply {
            alpha = 1f
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        if (zone.visibility != View.VISIBLE) {
            zone.visibility = View.VISIBLE
            zone.alpha = 0f
            zone.scaleX = 0.97f; zone.scaleY = 0.97f
            zone.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
        }
    }
    style(leftZone, action == ACTION_SPLIT_LEFT)
    style(rightZone, action == ACTION_SPLIT_RIGHT)
    style(topZone, action == ACTION_TOP)
    style(bottomZone, action == ACTION_FLOAT)
    style(peekZone, action == ACTION_PEEK)
    style(threeZone, action == ACTION_THREE)
}

/** The screen edge the finger is over while dragging (edge-snap): the six Recents-style zones. */
internal fun AppTaskbar.edgeAction(rawX: Float, rawY: Float, up: Float, zones: DropZones): Int {
    if (up < dp(28)) return ACTION_NONE // lift the icon clear of the bar first
    val m = context.resources.displayMetrics
    val w = m.widthPixels.toFloat()
    val h = m.heightPixels.toFloat()
    val centre = rawX > w * 0.40f && rawX < w * 0.60f
    // A zone that is off (Settings ▸ Recents ▸ Split screen ▸ Drop zones) leaves its part of the
    // screen to the next one, or to nothing.
    return when {
        // Centre of the top/bottom edges: peek and three-app split (same zones as Recents).
        zones.peek && rawY < h * 0.22f && centre -> ACTION_PEEK
        zones.three && rawY > h * 0.78f && centre -> ACTION_THREE
        zones.left && rawX < w * 0.32f -> ACTION_SPLIT_LEFT
        zones.right && rawX > w * 0.68f -> ACTION_SPLIT_RIGHT
        zones.top && rawY < h * 0.16f -> ACTION_TOP
        zones.bottom && rawY > h * 0.84f -> ACTION_FLOAT
        else -> ACTION_NONE
    }
}
