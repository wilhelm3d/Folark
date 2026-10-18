package org.arkikeskus.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val MIXER_LISTENER_CLASS = "org.arkikeskus.launcher.notifications.NotificationDotListenerService"

/** One app's media session, as the mixer shows it. */
internal data class MixerSession(
    val key: String,
    val packageName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    /** Playing on another device (Cast and similar): only then does the session own a volume. */
    val remote: Boolean,
    val volume: Int,
    val maxVolume: Int,
)

/**
 * Every app's media session at once (Spotify and YouTube side by side, say), while home is in front.
 * Needs notification access, like the other media widgets.
 */
internal class MediaMixerWatcher(private val context: Context) {
    var sessions by mutableStateOf<List<MixerSession>>(emptyList())
        private set
    var accessDenied by mutableStateOf(false)
        private set

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context.packageName, MIXER_LISTENER_CLASS)
    private var controllers: List<MediaController> = emptyList()
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = publish()
        override fun onSessionDestroyed() = publish()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> use(list.orEmpty()) }

    fun start() {
        val manager = manager ?: return
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                listening = true
            }
            use(manager.getActiveSessions(listenerComponent))
            accessDenied = false
        } catch (e: SecurityException) {
            accessDenied = true
            use(emptyList())
        }
    }

    fun stop() {
        if (listening) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        use(emptyList())
    }

    private fun use(next: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = next
        next.forEach { runCatching { it.registerCallback(callback) } }
        publish()
    }

    private fun publish() {
        sessions = controllers.mapNotNull { c ->
            runCatching {
                val metadata = c.metadata
                val info = c.playbackInfo
                MixerSession(
                    key = c.sessionToken.hashCode().toString(),
                    packageName = c.packageName.orEmpty(),
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    remote = info.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                    volume = info.currentVolume,
                    maxVolume = info.maxVolume,
                )
            }.getOrNull()
        }
    }

    private fun controller(session: MixerSession) = controllers.firstOrNull { it.sessionToken.hashCode().toString() == session.key }

    fun playPause(session: MixerSession) {
        val controls = controller(session)?.transportControls ?: return
        if (session.playing) controls.pause() else controls.play()
    }

    fun next(session: MixerSession) { controller(session)?.transportControls?.skipToNext() }

    fun previous(session: MixerSession) { controller(session)?.transportControls?.skipToPrevious() }

    /** Only a remote session (Cast) has a volume of its own; local playback shares the media volume. */
    fun setVolume(session: MixerSession, volume: Int) {
        runCatching { controller(session)?.setVolumeTo(volume, 0) }
    }

    fun open(session: MixerSession) {
        val c = controller(session) ?: return
        val activity = runCatching { c.sessionActivity }.getOrNull()
        val opened = activity != null && sendNotificationIntent(context, activity)
        if (!opened) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(session.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
        }
    }
}

/** Motorola's own per-app volume screen (Moto "Multi volume"), where the phone has it. */
private val MOTO_PER_APP_VOLUME = ComponentName("com.motorola.dynamicvolume", "com.motorola.dynamicvolume.ui.SettingsRouterActivity")

/**
 * Media mixer: every app that plays media, each with its own play/pause and skip buttons, and the
 * media volume underneath. Android gives apps one shared volume for sound played on the phone; a
 * session playing on another device (Cast) gets its own slider. On Motorola phones a button opens
 * Motorola's per-app volume, which can set each app's own level.
 */
@Composable
fun MediaMixerWidget(modifier: Modifier = Modifier, viewModel: FolarkWidgetsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val watcher = remember(context) { MediaMixerWatcher(context) }
    LifecycleResumeEffect(watcher) {
        watcher.start()
        onPauseOrDispose { watcher.stop() }
    }
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember(audio) { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var mediaVolume by remember { mutableFloatStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f) }
    // Volume keys and other apps change it too: the slider follows.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                mediaVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: mediaVolume
            }
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED))
            }
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val motoPerApp = remember(context) {
        runCatching { context.packageManager.getActivityInfo(MOTO_PER_APP_VOLUME, 0) }.isSuccess
    }

    FolarkWidgetSurface(theme = settings.mediaWidgetTheme, modifier = modifier) { ink ->
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.mixer_widget_title),
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (motoPerApp) {
                    Text(
                        stringResource(R.string.mixer_per_app),
                        color = ink.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent().setComponent(MOTO_PER_APP_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    watcher.accessDenied -> Text(
                        stringResource(R.string.notifications_widget_allow_access),
                        color = ink,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                            .padding(8.dp),
                    )
                    watcher.sessions.isEmpty() -> Text(
                        stringResource(R.string.mixer_nothing_playing),
                        color = ink.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        // As many apps as fit: a list that scrolls would fight home's own swipes.
                        val fits = (maxHeight / MIXER_ROW).toInt().coerceAtLeast(1)
                        Column {
                            watcher.sessions.take(fits).forEach { session ->
                                MixerRow(session, ink, watcher)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_media_volume),
                    contentDescription = stringResource(R.string.mixer_media_volume),
                    tint = ink,
                    modifier = Modifier.size(18.dp),
                )
                Slider(
                    value = mediaVolume,
                    onValueChange = { v ->
                        mediaVolume = v
                        runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v.toInt(), 0) }
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink, inactiveTrackColor = ink.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MixerRow(session: MixerSession, ink: Color, watcher: MediaMixerWatcher) {
    val context = LocalContext.current
    val icon = remember(session.packageName) { appIcon(context, session.packageName) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(MIXER_ROW),
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape))
            }
            Column(Modifier.weight(1f).clickable { watcher.open(session) }) {
                Text(
                    session.title.ifBlank { appLabel(context, session.packageName) },
                    color = ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.artist.isNotBlank()) {
                    Text(session.artist, color = ink.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MixerButton(R.drawable.ic_media_prev, R.string.media_widget_previous, ink, 30.dp) { watcher.previous(session) }
            MixerButton(
                if (session.playing) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                if (session.playing) R.string.media_widget_pause else R.string.media_widget_play,
                ink,
                36.dp,
            ) { watcher.playPause(session) }
            MixerButton(R.drawable.ic_media_next, R.string.media_widget_next, ink, 30.dp) { watcher.next(session) }
        }
        if (session.remote && session.maxVolume > 0) {
            // Cast and other remote playback: this session's own volume.
            var level by remember(session.key) { mutableFloatStateOf(session.volume.toFloat()) }
            LaunchedEffect(session.volume) { level = session.volume.toFloat() }
            Slider(
                value = level,
                onValueChange = { level = it; watcher.setVolume(session, it.toInt()) },
                valueRange = 0f..session.maxVolume.toFloat(),
                colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink, inactiveTrackColor = ink.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        }
    }
}

@Composable
private fun MixerButton(icon: Int, description: Int, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

private fun appIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
}.getOrNull()

private fun appLabel(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

/** Sent by Android when any stream's volume changes (not in the public SDK, but stable for years). */
private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private val MIXER_ROW = 48.dp
