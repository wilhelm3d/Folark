package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.workspace.*
import org.arkikeskus.launcher.feature.home.widgets.*

import android.app.PendingIntent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Process
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.SplitApp
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.SUGGEST_MS
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.UNDO_ABOVE_BAR_DP
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.UNDO_MS
import org.arkikeskus.launcher.feature.home.taskbar.AppTaskbar.Companion.homeVisible
import org.arkikeskus.launcher.model.catching

// Opening apps from the taskbar: launch, split, beside, a third pane, and the undo / suggestion pills (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

internal fun AppTaskbar.launchApp(app: AppItem) {
    if (repository?.launch(app)?.isSuccess == true) split?.opened(app)
}

// ------------------------------------------------------------------ split screen

/**
 * Puts [app] in split screen beside the app in front, on the [right] side (see
 * [SplitCoordinator.pairWithFront]). On the home screen there is no app to split with, so the
 * most recent app goes beside it. When no split can be made the app just opens, with a note on
 * where to set split screen up.
 */
internal fun AppTaskbar.openSplit(app: AppItem, right: Boolean = setup?.splitSide != LauncherSettings.TASKBAR_SPLIT_SIDE_LEFT, peek: Boolean = false) {
    val coordinator = split ?: return launchApp(app)
    val partner = if (homeVisible) recents.firstOrNull { it.key != app.key } else null
    if (homeVisible && partner == null) {
        launchApp(app)
        return
    }
    scope.launch {
        if (!coordinator.canSplit()) {
            Toast.makeText(context, R.string.taskbar_split_setup_needed, Toast.LENGTH_LONG).show()
            launchApp(app)
            return@launch
        }
        if (!coordinator.pairWithFront(app, appOnRight = right, partnerIfHome = partner, peek = peek)) launchApp(app)
    }
}

/**
 * Split-select: [app] opens on one side and Folark's picker drawer on the other; [three]: the
 * picker (full screen) chooses two more apps for a three-app split (see [SplitCoordinator.openPicker]).
 */
internal fun AppTaskbar.openSplitPick(app: AppItem, right: Boolean, three: Boolean = false) {
    val coordinator = split ?: return launchApp(app)
    scope.launch {
        if (!coordinator.canSplit()) {
            Toast.makeText(context, R.string.taskbar_split_setup_needed, Toast.LENGTH_LONG).show()
            launchApp(app)
            return@launch
        }
        coordinator.openPicker(SplitApp(app), firstOnRight = right, three = three)
    }
}

/**
 * A notification's [intent] (from [packageName], profile [userSerial]) in split screen beside the
 * last app; opened normally when that can't be done.
 */
internal fun AppTaskbar.openBeside(intent: android.app.PendingIntent, packageName: String, userSerial: Long) {
    val coordinator = split
    scope.launch {
        val userId = catching {
            context.getSystemService(android.os.UserManager::class.java)?.getUserForSerialNumber(userSerial)?.hashCode()
        }.getOrNull() ?: (Process.myUid() / 100_000)
        val ok = coordinator != null && coordinator.openBeside(intent, packageName, userId)
        if (!ok) {
            Toast.makeText(context, R.string.taskbar_live_split_failed, Toast.LENGTH_SHORT).show()
            sendNotificationIntent(context, intent)
        }
    }
}

// ------------------------------------------------------------------ "Open with …"

/** "Open with Maps": a pill above the bar; a tap puts the partner beside the app, untouched it goes. */
internal fun AppTaskbar.showSuggestion(offer: SplitCoordinator.Suggestion) {
    hideSuggestion()
    if (!largeScreen()) return
    val pill = TextView(context).apply {
        text = context.getString(R.string.taskbar_open_with, offer.partner.label)
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        elevation = dpf(6f)
        background = GradientDrawable().apply {
            cornerRadius = dpf(22f)
            setColor(Color.argb(235, 28, 32, 38))
            setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }
        contentDescription = text
        setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            handler.removeCallbacks(suggestionTimeout)
            split?.let { c -> scope.launch { c.acceptSuggestion() } }
        }
        alpha = 0f
        translationY = dpf(12f)
    }
    val params = params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, systemBottomInset() + dp(UNDO_ABOVE_BAR_DP))
    if (catching { windowManager.addView(pill, params) }.isSuccess) {
        suggestionWindow = pill
        pill.animate().alpha(1f).translationY(0f).setDuration(160).start()
        handler.postDelayed(suggestionTimeout, SUGGEST_MS)
    }
}

internal fun AppTaskbar.hideSuggestion() {
    handler.removeCallbacks(suggestionTimeout)
    val pill = suggestionWindow ?: return
    suggestionWindow = null
    catching { windowManager.removeView(pill) }
}

// ------------------------------------------------------------------ undo split

/**
 * A small "Undo split" pill above the bar: tapping it closes the split that just formed, keeping
 * the app that was there before. It goes by itself after [UNDO_MS].
 */
internal fun AppTaskbar.showUndo() {
    hideUndo()
    if (!largeScreen()) return
    val pill = TextView(context).apply {
        text = context.getString(R.string.taskbar_undo_split)
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        elevation = dpf(6f)
        background = GradientDrawable().apply {
            cornerRadius = dpf(22f)
            setColor(Color.argb(235, 28, 32, 38))
            setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }
        contentDescription = text
        setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            hideUndo()
            split?.let { c -> scope.launch { c.undo() } }
        }
        alpha = 0f
        translationY = dpf(12f)
    }
    val params = params(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, systemBottomInset() + dp(UNDO_ABOVE_BAR_DP))
    if (catching { windowManager.addView(pill, params) }.isSuccess) {
        undoWindow = pill
        pill.animate().alpha(1f).translationY(0f).setDuration(160).start()
        handler.postDelayed(undoTimeout, UNDO_MS)
    }
}

internal fun AppTaskbar.hideUndo() {
    handler.removeCallbacks(undoTimeout)
    val pill = undoWindow ?: return
    undoWindow = null
    catching { windowManager.removeView(pill) }
}

/** Adds [app] as the third pane of the two-app split showing now (Android's own three-way split). */
internal fun AppTaskbar.addThird(app: AppItem) {
    val coordinator = split ?: return
    scope.launch { coordinator.addThird(SplitApp(app), native = true, orSideStage = false) }
}

// ------------------------------------------------------------------ recent apps
