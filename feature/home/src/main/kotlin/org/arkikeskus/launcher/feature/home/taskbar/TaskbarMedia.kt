package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.workspace.*
import org.arkikeskus.launcher.feature.home.widgets.*

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.arkikeskus.launcher.model.catching

/**
 * What is playing, for the taskbar's media icon: the session that is playing (or, if none is, the
 * most recent one), its cover and its controls. Needs notification access, like the Now playing
 * widget; without it the taskbar simply shows no media icon. [onChange] runs on the main thread
 * whenever what the icon shows changes.
 */
internal class TaskbarMedia(private val context: Context, private val onChange: () -> Unit) {

    data class State(
        val packageName: String,
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
    )

    var state: State? = null
        private set

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context.packageName, LISTENER_CLASS)
    private var controller: MediaController? = null
    private var listening = false

    /** The last playback state and track length the session sent (callbacks, not queries). */
    private var lastPlayback: PlaybackState? = null
    private var duration = 0L

    /**
     * Where playback is now and the track's length, in ms, worked out from the last state the
     * player sent (position + time since then x speed); null when the player gives no length.
     */
    fun progress(): Pair<Long, Long>? {
        val total = duration.takeIf { it > 0L } ?: return null
        val p = lastPlayback ?: return 0L to total
        var at = p.position
        if (p.state == PlaybackState.STATE_PLAYING && p.lastPositionUpdateTime > 0L) {
            at += ((android.os.SystemClock.elapsedRealtime() - p.lastPositionUpdateTime) * p.playbackSpeed).toLong()
        }
        return at.coerceIn(0L, total) to total
    }

    /** Whether the player lets its position be set (a drag on the progress bar). */
    val canSeek: Boolean get() = (lastPlayback?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L

    fun seekTo(positionMs: Long) {
        catching { controller?.transportControls?.seekTo(positionMs) }
    }

    private val callback = object : MediaController.Callback() {
        // Position updates arrive about once a second: only the play/pause flag matters here, so the
        // title and cover are not fetched again (each fetch is a fresh copy of the cover).
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
            // Kept for the progress bar, which works the position out from it (no polling).
            lastPlayback = playbackState
            val shown = state ?: return publish()
            val playing = playbackState?.state == PlaybackState.STATE_PLAYING
            if (shown.playing != playing) {
                state = shown.copy(playing = playing)
                onChange()
            }
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            publish(metadata)
        }
        override fun onSessionDestroyed() = attach(null)
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pick(controllers.orEmpty())
    }

    fun start() {
        if (listening) return
        val manager = manager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            listening = true
            pick(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Notification access is off: no media icon.
            attach(null)
        }
    }

    fun stop() {
        if (listening) {
            catching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        attach(null)
    }

    fun previous() {
        catching { controller?.transportControls?.skipToPrevious() }
    }

    fun next() {
        catching { controller?.transportControls?.skipToNext() }
    }

    /** Stops playback (pausing as well, for players that ignore a stop). */
    fun stopPlayback() {
        val controls = controller?.transportControls ?: return
        catching { controls.pause() }
        catching { controls.stop() }
    }

    fun playPause() {
        val current = controller ?: return
        catching {
            if (current.playbackState?.state == PlaybackState.STATE_PLAYING) {
                current.transportControls.pause()
            } else {
                current.transportControls.play()
            }
        }
    }

    /** Opens the player's own screen (its session activity), else false. */
    fun openPlayer(): Boolean {
        val intent = controller?.sessionActivity ?: return false
        return sendNotificationIntent(context, intent)
    }

    private fun pick(controllers: List<MediaController>) {
        attach(
            controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull(),
        )
    }

    private fun attach(next: MediaController?) {
        if (controller?.sessionToken != next?.sessionToken) {
            catching { controller?.unregisterCallback(callback) }
            controller = next
            catching { next?.registerCallback(callback) }
            // One read when a session is taken up; the callbacks keep both current after that.
            lastPlayback = catching { next?.playbackState }.getOrNull()
            duration = catching { next?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) }.getOrNull() ?: 0L
        }
        publish()
    }

    /** [metadata]: what the session just sent, or (by default) read from it. */
    private fun publish(metadata: MediaMetadata? = controller?.metadata) {
        val current = controller
        val next = if (current == null) {
            null
        } else {
            State(
                packageName = current.packageName,
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = (
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ).orEmpty(),
                art = catching {
                    metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                }.getOrNull(),
                playing = current.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
        val old = state
        // Players re-send the same song often (radio, podcasts): with the same text, play state and
        // cover nothing on the taskbar changes.
        val same = old == next || (
            old != null && next != null &&
                old.copy(art = null) == next.copy(art = null) && sameArt(old.art, next.art)
            )
        if (same) return
        state = next
        onChange()
    }

    private fun sameArt(a: Bitmap?, b: Bitmap?): Boolean =
        a === b || (a != null && b != null && catching { a.sameAs(b) }.getOrDefault(false))

    private companion object {
        const val LISTENER_CLASS = "org.arkikeskus.launcher.notifications.NotificationDotListenerService"
    }
}
