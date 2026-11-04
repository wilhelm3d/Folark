package org.arkikeskus.launcher.feature.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.abs
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.AUTO_COLLAPSE_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MEDIA_CONTROLS_MS
import org.arkikeskus.launcher.feature.home.AppTaskbar.Companion.MODE_BAR

// The taskbar's media icon and its controls card (split out of AppTaskbar.kt; extension functions on [AppTaskbar]).

/** The cover of what is playing (the player's icon if it has none), with the player as a badge. */
@SuppressLint("ClickableViewAccessibility")
internal fun AppTaskbar.mediaIconView(s: TaskbarSetup, size: Int, controls: View?, fixed: Boolean): View {
    val playing = media.state
    val player = playing?.let { st -> allApps.firstOrNull { it.packageName == st.packageName } }
    val inset = size / 12
    val round = s.mediaShape != LauncherSettings.TASKBAR_MEDIA_SHAPE_ROUNDED
    val frame = FrameLayout(context).apply {
        contentDescription = playing?.title?.ifEmpty { null } ?: context.getString(R.string.taskbar_media)
        clipChildren = false
        clipToPadding = false
    }
    val cover = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (round) {
                    outline.setOval(0, 0, view.width, view.height)
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.24f)
                }
            }
        }
        clipToOutline = true
        elevation = dpf(4f)
        // Paused: the cover dims a little.
        alpha = if (playing?.playing == true) 1f else 0.7f
    }
    val art = playing?.art
    if (art != null) {
        cover.setImageBitmap(art)
    } else if (player != null) {
        setIconInto(cover, player, s)
    }
    frame.addView(cover, FrameLayout.LayoutParams(size - inset * 2, size - inset * 2, Gravity.CENTER))
    if (s.mediaBadge && art != null && player != null) {
        val badge = ImageView(context).apply { elevation = dpf(6f) }
        setIconInto(badge, player, s)
        val badgeSize = (size * 0.4f).toInt()
        frame.addView(badge, FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.BOTTOM or Gravity.END))
    }

    val slop = ViewConfiguration.get(context).scaledTouchSlop
    var downY = 0f
    var downX = 0f
    var moved = false
    var held = false
    val hold = Runnable {
        held = true
        openPlayer(player)
    }
    frame.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; moved = false; held = false
                handler.removeCallbacks(collapse)
                handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(downY - event.rawY) > slop || abs(event.rawX - downX) > slop)) {
                    moved = true
                    handler.removeCallbacks(hold)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(hold)
                if (event.actionMasked == MotionEvent.ACTION_UP && !held && !moved) onMediaTap()
                if (!fixed) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
        }
        true
    }
    return frame
}

internal fun AppTaskbar.openPlayer(player: AppItem?) {
    if (!media.openPlayer() && player != null) launchApp(player)
    expanded = false
    mediaControlsOpen = false
    refresh()
}

/**
 * A tap on the cover opens (or closes) the player card; a second tap right after it stops the
 * music and hides the cover. The single tap waits the double-tap time before it acts.
 */
internal fun AppTaskbar.onMediaTap() {
    val now = SystemClock.uptimeMillis()
    if (now - lastMediaTapAt <= ViewConfiguration.getDoubleTapTimeout()) {
        lastMediaTapAt = 0L
        handler.removeCallbacks(mediaSingleTap)
        stopAndHideMedia()
        return
    }
    lastMediaTapAt = now
    handler.removeCallbacks(mediaSingleTap)
    handler.postDelayed(mediaSingleTap, ViewConfiguration.getDoubleTapTimeout().toLong())
}

internal fun AppTaskbar.toggleMediaCard() {
    handler.removeCallbacks(closeMediaControls)
    if (mediaControlsOpen) {
        mediaControlsOpen = false
    } else {
        mediaControlsOpen = true
        handler.postDelayed(closeMediaControls, MEDIA_CONTROLS_MS)
    }
    refresh(rebuild = true)
}

internal fun AppTaskbar.stopAndHideMedia() {
    val playing = media.state ?: return
    media.stopPlayback()
    mediaHiddenFor = playing.packageName
    mediaHiddenAt = SystemClock.uptimeMillis()
    mediaControlsOpen = false
    handler.removeCallbacks(closeMediaControls)
    refresh(rebuild = true)
}

/** The player card above the bar: cover, title and artist, back, play/pause and forward. */
internal fun AppTaskbar.mediaControlsView(s: TaskbarSetup): View {
    val light = s.theme == LauncherSettings.TASKBAR_THEME_LIGHT
    val tint = if (light) Color.rgb(20, 20, 24) else Color.WHITE
    val playingNow = media.state
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dpf(26f)
            setColor(if (light) Color.argb(235, 242, 243, 246) else Color.argb(215, 24, 24, 28))
            setStroke(dp(1), if (light) Color.argb(40, 0, 0, 0) else Color.argb(50, 255, 255, 255))
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        visibility = if (mediaControlsOpen) View.VISIBLE else View.GONE
    }
    val art = playingNow?.art
    if (art != null) {
        val cover = ImageView(context).apply {
            setImageBitmap(art)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpf(12f))
                }
            }
            clipToOutline = true
        }
        panel.addView(cover, LinearLayout.LayoutParams(dp(52), dp(52)))
    }
    if (playingNow != null && (playingNow.title.isNotEmpty() || playingNow.artist.isNotEmpty())) {
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        if (playingNow.title.isNotEmpty()) {
            texts.addView(TextView(context).apply {
                text = playingNow.title
                setTextColor(tint)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(200)
            })
        }
        if (playingNow.artist.isNotEmpty()) {
            texts.addView(TextView(context).apply {
                text = playingNow.artist
                setTextColor(Color.argb(180, Color.red(tint), Color.green(tint), Color.blue(tint)))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(200)
            })
        }
        panel.addView(texts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    fun button(icon: Int, description: Int, action: () -> Unit) {
        val view = ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(tint)
            contentDescription = context.getString(description)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                action()
                handler.removeCallbacks(collapse)
                handler.removeCallbacks(closeMediaControls)
                handler.postDelayed(closeMediaControls, MEDIA_CONTROLS_MS)
                if (shownMode == MODE_BAR) handler.postDelayed(collapse, AUTO_COLLAPSE_MS)
            }
        }
        panel.addView(view, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    button(android.R.drawable.ic_media_previous, R.string.taskbar_media_previous) { media.previous() }
    val playing = media.state?.playing == true
    button(
        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        if (playing) R.string.taskbar_media_pause else R.string.taskbar_media_play,
    ) { media.playPause() }
    button(android.R.drawable.ic_media_next, R.string.taskbar_media_next) { media.next() }
    return panel
}
