package org.arkikeskus.launcher.recents

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Active media sessions, read through the notification-listener grant Folark already uses, so a
 * Recents card can show a speaker badge for an app that is playing and pause/resume just that app.
 *
 * Android offers no public per-UID stream mute, so "mute this app" is the app's own media session
 * paused (and "unmute" is play) — the one per-app audio control there is. Apps without a media
 * session (no badge) can't be controlled this way.
 */
internal class RecentsAudio {
    /** package name -> is it playing right now; a package is "active" (has a session) when it is a key. */
    var state by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    private var controllers: Map<String, MediaController> = emptyMap()

    fun active(packageName: String): Boolean = packageName in state
    fun playing(packageName: String): Boolean = state[packageName] == true

    fun toggle(packageName: String) {
        val controller = controllers[packageName] ?: return
        if (playing(packageName)) runCatching { controller.transportControls.pause() }
        else runCatching { controller.transportControls.play() }
    }

    internal fun refresh(list: List<MediaController>) {
        controllers = list.associateBy { it.packageName.orEmpty() }.filterKeys { it.isNotEmpty() }
        state = controllers.mapValues { (_, c) -> c.playbackState?.state == PlaybackState.STATE_PLAYING }
    }
}

@Composable
internal fun rememberRecentsAudio(enabled: Boolean): RecentsAudio {
    val context = LocalContext.current
    val audio = remember { RecentsAudio() }
    DisposableEffect(enabled) {
        if (!enabled) {
            audio.refresh(emptyList())
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context.packageName, LISTENER_CLASS)
        val callbacks = mutableListOf<Pair<MediaController, MediaController.Callback>>()
        fun watch(list: List<MediaController>?) {
            callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            callbacks.clear()
            val ctrls = list.orEmpty()
            ctrls.forEach { c ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(s: PlaybackState?) = audio.refresh(ctrls)
                    override fun onSessionDestroyed() = audio.refresh(ctrls)
                }
                runCatching { c.registerCallback(cb) }
                callbacks += c to cb
            }
            audio.refresh(ctrls)
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        // Needs the notification-listener grant; without it getActiveSessions throws and no badge shows.
        val ok = runCatching {
            manager?.addOnActiveSessionsChangedListener(listener, component)
            watch(manager?.getActiveSessions(component))
            true
        }.getOrDefault(false)
        onDispose {
            if (ok) runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            callbacks.clear()
        }
    }
    return audio
}

private const val LISTENER_CLASS = "org.arkikeskus.launcher.notifications.NotificationDotListenerService"
