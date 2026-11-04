package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.data.SplitThree
import android.annotation.SuppressLint
import org.arkikeskus.launcher.data.LaunchOrigin
import android.content.pm.ShortcutInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.abs
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.AUTO_COLLAPSE_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MAX_SHORTCUTS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MIME_ACTIVITY
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.homeVisible

// The taskbar's pop-ups: split and app menus, app shortcuts, live-activity details (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/** The split screen's actions above [anchor]: swap sides, or one of its apps full screen. */
internal fun AppTaskbar.showSplitMenu(anchor: View, s: TaskbarSetup, fixed: Boolean) {
    hideShortcuts()
    handler.removeCallbacks(collapse)
    scope.launch {
        val ready = ShellSplit.available(context)
        val tasks = if (ready) withContext(Dispatchers.IO) { ShellSplit.splitTasks(context) } else emptyList()
        if (!anchor.isAttachedToWindow) return@launch
        val rows = mutableListOf<Pair<String, (() -> Unit)?>>()
        when {
            !ready -> rows += context.getString(R.string.taskbar_split_setup_needed) to null
            tasks.size < 2 -> rows += context.getString(R.string.taskbar_split_none) to null
            else -> {
                rows += context.getString(R.string.taskbar_split_swap) to {
                    scope.launch(Dispatchers.IO) { ShellSplit.swapSides(context) }
                }
                // Android's peek view, one tap (Folark's Vector module in SystemUI).
                if (tasks.size == 2 && SplitThree.ready(context)) {
                    rows += context.getString(R.string.taskbar_split_peek_left) to {
                        scope.launch { SplitThree.snap(context, SplitThree.SNAP_PEEK_LEFT) }
                    }
                    rows += context.getString(R.string.taskbar_split_peek_right) to {
                        scope.launch { SplitThree.snap(context, SplitThree.SNAP_PEEK_RIGHT) }
                    }
                    rows += context.getString(R.string.taskbar_split_even) to {
                        scope.launch { SplitThree.snap(context, SplitThree.SNAP_EVEN) }
                    }
                    // Divider sizes: each app two thirds (Settings ▸ Recents ▸ Split screen).
                    if (s.ratioMenu) {
                        tasks.forEach { task ->
                            val label = allApps.firstOrNull { it.packageName == task.packageName }?.label ?: task.packageName
                            rows += context.getString(R.string.taskbar_split_two_thirds, label) to {
                                scope.launch {
                                    val ok = split?.snapTo(SplitThree.SNAP_TWO_THIRDS, forTask = task.taskId) == true
                                    if (!ok) Toast.makeText(context, R.string.taskbar_split_no_third, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                tasks.forEach { task ->
                    val label = allApps.firstOrNull { it.packageName == task.packageName }?.label ?: task.packageName
                    rows += context.getString(R.string.taskbar_split_keep, label) to {
                        scope.launch(Dispatchers.IO) { ShellSplit.exitSplit(context, task.taskId) }
                    }
                }
            }
        }
        showMenu(anchor, s, fixed, rows)
    }
}

/** A small card of text rows above [anchor]; a row without an action is a note. */
@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.showMenu(anchor: View, s: TaskbarSetup, fixed: Boolean, rows: List<Pair<String, (() -> Unit)?>>) {
    val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
    val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dpf(22f)
            setColor(if (light) Color.argb(245, 242, 243, 246) else Color.argb(235, 28, 28, 32))
            setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
        }
        setPadding(dp(6), dp(6), dp(6), dp(6))
        elevation = dpf(8f)
    }
    rows.forEach { (label, action) ->
        panel.addView(TextView(context).apply {
            text = label
            setTextColor(if (action == null) Color.argb(170, Color.red(tint), Color.green(tint), Color.blue(tint)) else tint)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(260)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (action != null) {
                background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Color.TRANSPARENT) }
                setOnClickListener {
                    action()
                    hideShortcuts()
                    expanded = false
                    if (!fixed) refresh()
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    panel.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            hideShortcuts()
            if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
        false
    }
    placeAbove(anchor, panel)
}

/** Shows [panel] in its own window just above [anchor] (the same slot as the shortcuts card). */
internal fun AppTaskbar.placeAbove(anchor: View, panel: View) {
    panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val at = IntArray(2)
    anchor.getLocationOnScreen(at)
    val screenWidth = context.resources.displayMetrics.widthPixels
    val x = (at[0] + anchor.width / 2 - panel.measuredWidth / 2)
        .coerceIn(dp(8), (screenWidth - panel.measuredWidth - dp(8)).coerceAtLeast(dp(8)))
    val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
    if (runCatching { windowManager.addView(panel, params) }.isSuccess) shortcutWindow = panel
}

/** The app's shortcuts in a card above [anchor]: tap one to open it, drag it (over an app) to split. */
@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.showShortcuts(app: AppItem, anchor: View, s: TaskbarSetup, fixed: Boolean) {
    hideShortcuts()
    handler.removeCallbacks(collapse)
    scope.launch {
        val dpi = context.resources.displayMetrics.densityDpi
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val items = withContext(Dispatchers.IO) {
            shortcutsOf(app).map { info ->
                info to runCatching { launcherApps?.getShortcutIconDrawable(info, dpi) }.getOrNull()
            }
        }
        if (!anchor.isAttachedToWindow) return@launch
        if (items.isEmpty()) {
            // No shortcuts: still offer pinning (the menu says there are none).
            val (pinLabel, pinAction) = pinMenuRow(app)
            showMenu(anchor, s, fixed, listOf(
                pinLabel to pinAction,
                context.getString(R.string.taskbar_no_shortcuts) to null,
            ))
            return@launch
        }
        val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
        val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(22f)
                setColor(if (light) Color.argb(245, 242, 243, 246) else Color.argb(235, 28, 28, 32))
                setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = dpf(8f)
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        // Pin to / unpin from the taskbar, above the app's own shortcuts.
        val (pinLabel, pinAction) = pinMenuRow(app)
        panel.addView(TextView(context).apply {
            text = pinLabel
            setTextColor(tint)
            textSize = 14f
            maxLines = 1
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Color.TRANSPARENT) }
            setOnClickListener {
                pinAction()
                hideShortcuts()
                expanded = false
                if (!fixed) refresh()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        items.forEach { (info, drawable) ->
            val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(16), dp(8))
                background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(Color.TRANSPARENT) }
                contentDescription = label
            }
            rowView.addView(ImageView(context).apply { setImageDrawable(drawable) }, LinearLayout.LayoutParams(dp(28), dp(28)))
            rowView.addView(TextView(context).apply {
                text = label
                setTextColor(tint)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(220)
                setPadding(dp(12), 0, 0, 0)
            })
            var downX = 0f
            var downY = 0f
            var gone = false
            rowView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY; gone = false
                        (v.background as? GradientDrawable)?.setColor(Color.argb(40, Color.red(tint), Color.green(tint), Color.blue(tint)))
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val far = abs(event.rawX - downX) > slop * 2 || abs(event.rawY - downY) > slop * 2
                        if (!gone && far && !homeVisible && useDropZones(s)) {
                            val data = shortcutDragData(info)
                            if (data != null && startSystemDrag(v, label, MIME_ACTIVITY, data, fixed)) gone = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        (v.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
                        val far = abs(event.rawX - downX) > slop * 2 || abs(event.rawY - downY) > slop * 2
                        if (!gone && !far) {
                            LaunchOrigin.view(v)
                            runCatching { launcherApps?.startShortcut(info, null, null) }
                            hideShortcuts()
                            expanded = false
                            if (!fixed) refresh()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> (v.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
                }
                true
            }
            panel.addView(rowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && !systemDragging) {
                hideShortcuts()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
            false
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val x = (at[0] + anchor.width / 2 - panel.measuredWidth / 2)
            .coerceIn(dp(8), (screenWidth - panel.measuredWidth - dp(8)).coerceAtLeast(dp(8)))
        val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        if (runCatching { windowManager.addView(panel, params) }.isSuccess) shortcutWindow = panel
    }
}

/**
 * The live pill opened up, as a small panel over the bar: the app, a big readout (clock, ETA,
 * percentage), the title and text, a progress bar, and the notification's buttons plus Open.
 * Closes on a touch outside it, a choice, or after a while.
 */
internal fun AppTaskbar.showLiveDetails(item: org.arkikeskus.launcher.model.LiveActivity, anchor: View, s: TaskbarSetup, fixed: Boolean) {
    hideShortcuts()
    handler.removeCallbacks(collapse)
    val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
    val ink = if (light) Color.rgb(20, 20, 24) else Color.WHITE
    val soft = if (light) Color.argb(170, 20, 20, 24) else Color.argb(190, 255, 255, 255)
    val accent = item.color ?: Color.rgb(143, 211, 255)
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dpf(24f)
            setColor(if (light) Color.argb(248, 242, 243, 246) else Color.argb(242, 22, 28, 31))
            setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255))
        }
        setPadding(dp(18), dp(14), dp(18), dp(14))
        elevation = dpf(10f)
        minimumWidth = dp(260)
    }
    fun text(value: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false, lines: Int = 1) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        maxLines = lines
        ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    panel.addView(text(item.appLabel.ifBlank { item.packageName }, 12f, soft))
    val now = System.currentTimeMillis()
    item.shortText(now).takeIf { it != item.title && it != item.text }?.let {
        panel.addView(text(it, 28f, ink, bold = true).apply {
            if (item.ticks || item.progress != null) typeface = android.graphics.Typeface.MONOSPACE
        })
    }
    panel.addView(text(item.title, 15f, ink, bold = true, lines = 2))
    val body = item.bigText ?: item.text
    if (body.isNotBlank() && body != item.title) panel.addView(text(body, 13f, soft, lines = 3))
    if (item.progress != null || item.indeterminate) {
        val bar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = item.indeterminate
            max = 1000
            progress = ((item.progress ?: 0f) * 1000).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        panel.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(8) })
    }
    val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
    fun button(label: String, filled: Boolean, onClick: () -> Unit) = text(label, 14f, if (filled) Color.WHITE else ink, bold = true).apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dpf(999f)
            setColor(if (filled) accent else if (light) Color.argb(26, 0, 0, 0) else Color.argb(32, 255, 255, 255))
        }
        setOnClickListener {
            hideShortcuts()
            onClick()
            if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
    }
    item.actions.take(3).forEach { action ->
        buttons.addView(button(action.title, false) { sendNotificationIntent(context, action.intent) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
    }
    // Beside the last app in split screen (Settings ▸ Recents ▸ Split screen).
    val intent = item.contentIntent
    if (s.notificationSplit && intent != null && largeScreen()) {
        buttons.addView(button(context.getString(R.string.taskbar_live_split), false) { openBeside(intent, item.packageName, item.userSerial) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
    }
    buttons.addView(button(org.arkikeskus.launcher.model.LiveActivity.OPEN_LABEL, true) {
        item.contentIntent?.let { sendNotificationIntent(context, it) }
    })
    panel.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    panel.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            hideShortcuts()
            if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
        }
        false
    }
    panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val at = IntArray(2)
    anchor.getLocationOnScreen(at)
    val screenWidth = context.resources.displayMetrics.widthPixels
    val width = panel.measuredWidth.coerceAtMost(dp(380))
    val x = (at[0] + anchor.width / 2 - width / 2).coerceIn(dp(8), (screenWidth - width - dp(8)).coerceAtLeast(dp(8)))
    val y = (at[1] - panel.measuredHeight - dp(12)).coerceAtLeast(dp(8))
    val params = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }
    if (runCatching { windowManager.addView(panel, params) }.isSuccess) {
        shortcutWindow = panel
        handler.postDelayed({ if (shortcutWindow === panel) hideShortcuts() }, LIVE_PANEL_IDLE_MS)
    }
}

internal fun AppTaskbar.hideShortcuts() {
    shortcutWindow?.let { runCatching { windowManager.removeView(it) } }
    shortcutWindow = null
}

/** The app's shortcuts (from its manifest, the app itself, and pinned ones), best first. */
internal fun AppTaskbar.shortcutsOf(app: AppItem): List<ShortcutInfo> = runCatching {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    val query = LauncherApps.ShortcutQuery()
        .setPackage(app.packageName)
        .setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
    launcherApps.getShortcuts(query, app.user).orEmpty()
        .filter { it.isEnabled && !(it.shortLabel ?: it.longLabel).isNullOrBlank() }
        .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
        .distinctBy { it.id }
        .take(MAX_SHORTCUTS)
}.getOrDefault(emptyList())

// ------------------------------------------------------------------ the media icon
