package org.arkikeskus.launcher.feature.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.designsystem.theme.LocalLauncherColors

/** The class of the launcher's notification listener: the system only shows media sessions to an
 *  app whose notification listener the user has enabled. */
private const val NOTIFICATION_LISTENER_CLASS = "org.arkikeskus.launcher.notifications.NotificationDotListenerService"

internal data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val packageName: String,
)

/**
 * Follows the media session that is playing (or, if none is, the most recent one) and exposes it as
 * Compose state. [start] / [stop] bracket the time the launcher is in front; starting again also
 * picks up a notification-access grant made in the meantime.
 */
internal class MediaSessionWatcher(private val context: Context) {
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set
    var accessDenied by mutableStateOf(false)
        private set

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context.packageName, NOTIFICATION_LISTENER_CLASS)
    private var controller: MediaController? = null
    private var listening = false

    private val controllerCallback = object : MediaController.Callback() {
        // Position updates arrive about once a second: only the play/pause flag matters here, so
        // the title and cover art are not fetched and re-wrapped again.
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val shown = nowPlaying ?: return publish()
            val playing = state?.state == PlaybackState.STATE_PLAYING
            if (shown.playing != playing) nowPlaying = shown.copy(playing = playing)
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = attach(null)
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pick(controllers.orEmpty())
    }

    fun start() {
        val manager = manager ?: return
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                listening = true
            }
            pick(manager.getActiveSessions(listenerComponent))
            accessDenied = false
        } catch (e: SecurityException) {
            // Notification access is off: the widget offers to open that settings page.
            accessDenied = true
            nowPlaying = null
        }
    }

    fun stop() {
        if (listening) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        attach(null)
    }

    private fun pick(controllers: List<MediaController>) {
        attach(
            controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull(),
        )
    }

    private fun attach(next: MediaController?) {
        if (controller?.sessionToken != next?.sessionToken) {
            runCatching { controller?.unregisterCallback(controllerCallback) }
            controller = next
            runCatching { next?.registerCallback(controllerCallback) }
        }
        publish()
    }

    private fun publish() {
        val current = controller
        nowPlaying = if (current == null) null else {
            val metadata = current.metadata
            val art = runCatching {
                metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            }.getOrNull()
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                art = runCatching { art?.asImageBitmap() }.getOrNull(),
                playing = current.playbackState?.state == PlaybackState.STATE_PLAYING,
                packageName = current.packageName.orEmpty(),
            )
        }
    }

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (nowPlaying?.playing == true) controls.pause() else controls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }

    fun previous() { controller?.transportControls?.skipToPrevious() }

    /**
     * Brings up the system volume control without changing the level. Asked of the session first,
     * so playback on a cast or Bluetooth device gets that device's slider; the plain media-volume
     * panel is the fallback.
     */
    fun showVolume() {
        val shown = runCatching {
            controller?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }.getOrNull() != null
        if (!shown) {
            runCatching {
                context.getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    /** Opens the playing app: its own "now playing" screen when it offers one, else the app. */
    fun open() {
        val current = controller ?: return
        // Sent with the background-start opt-in; a plain send() is silently blocked on Android 14+.
        val activity = runCatching { current.sessionActivity }.getOrNull()
        val opened = activity != null && sendNotificationIntent(context, activity)
        if (!opened) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(current.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
        }
    }
}

/** Now playing: cover art, track and artist with previous / play-pause / next. */
@Composable
fun NowPlayingWidget(modifier: Modifier = Modifier, viewModel: FolarkWidgetsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val watcher = remember(context) { MediaSessionWatcher(context) }
    LifecycleResumeEffect(watcher) {
        watcher.start()
        onPauseOrDispose { watcher.stop() }
    }
    val playing = watcher.nowPlaying
    val idle = playing == null && !watcher.accessDenied
    FolarkWidgetSurface(theme = settings.widgets.mediaTheme, modifier = modifier, seeThrough = idle) { themeInk ->
        val artBackground = settings.widgets.mediaArtwork && playing?.art != null
        // Over cover art the text is always white on a dark veil, whatever the theme.
        val ink = if (artBackground) Color.White else themeInk
        if (artBackground) {
            Image(
                bitmap = playing!!.art!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        when {
            watcher.accessDenied -> Text(
                text = stringResource(R.string.notifications_widget_allow_access),
                color = ink,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    .padding(12.dp),
            )
            // Idle: the widget is see-through and shows nothing at all.
            playing == null -> Unit
            else -> Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val art = playing.art
                if (!artBackground && art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Column(Modifier.weight(1f).clickable { watcher.open() }) {
                    Text(
                        text = playing.title.ifBlank { stringResource(R.string.media_widget_name) },
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playing.artist.isNotBlank()) {
                        Text(
                            text = playing.artist,
                            color = ink.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MediaButton(R.drawable.ic_media_prev, R.string.media_widget_previous, ink, 34.dp) { watcher.previous() }
                MediaButton(
                    if (playing.playing) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                    if (playing.playing) R.string.media_widget_pause else R.string.media_widget_play,
                    ink, 42.dp,
                ) { watcher.playPause() }
                MediaButton(R.drawable.ic_media_next, R.string.media_widget_next, ink, 34.dp) { watcher.next() }
                MediaButton(R.drawable.ic_media_volume, R.string.media_widget_volume, ink, 34.dp) { watcher.showVolume() }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: Int, description: Int, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = tint,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

/** Device status: ring gauges for the battery, storage, memory and connected Bluetooth devices. */
@Composable
fun DeviceStatsWidget(modifier: Modifier = Modifier, viewModel: FolarkWidgetsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val stats by viewModel.deviceStats.collectAsStateWithLifecycle()
    val launcherColors = LocalLauncherColors.current
    val accent = MaterialTheme.colorScheme.primary
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshStats()
    }
    val gauges = buildList {
        if (settings.widgets.statsShowBattery) {
            battery?.let {
                add(StatGaugeData(stringResource(R.string.battery_widget_name), it.percent / 100f, launcherColors.batteryColor(it.percent), R.drawable.ic_stat_battery))
            }
        }
        if (settings.widgets.statsShowStorage) {
            add(StatGaugeData(stringResource(R.string.stats_widget_storage), stats.storageUsed, accent, R.drawable.ic_stat_storage))
        }
        if (settings.widgets.statsShowMemory) {
            add(StatGaugeData(stringResource(R.string.stats_widget_memory), stats.memoryUsed, accent, R.drawable.ic_stat_memory))
        }
        if (settings.widgets.statsShowBluetooth) {
            stats.bluetooth.forEach { (name, level) ->
                add(StatGaugeData(name, level / 100f, launcherColors.batteryColor(level), R.drawable.ic_status_bluetooth))
            }
        }
    }
    val needsBluetoothPermission = settings.widgets.statsShowBluetooth && stats.bluetoothNeedsPermission &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    FolarkWidgetSurface(theme = settings.widgets.statsTheme, modifier = modifier) { ink ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            val slots = gauges.size + if (needsBluetoothPermission) 1 else 0
            if (slots == 0) return@BoxWithConstraints
            // Compact: the ring holds the icon, the percentage sits under it; the name only when
            // the widget is tall enough for a second line.
            val showLabels = maxHeight >= 56.dp
            val showNames = maxHeight >= 100.dp
            val labelRoom = (if (showLabels) 16.dp else 0.dp) + (if (showNames) 14.dp else 0.dp)
            val diameter = minOf(maxWidth / slots - 6.dp, maxHeight - labelRoom, MAX_GAUGE_DIAMETER).coerceAtLeast(22.dp)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                gauges.forEach { gauge ->
                    StatGauge(gauge, ink, diameter, showLabels, showNames)
                }
                if (needsBluetoothPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            runCatching { askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        },
                    ) {
                        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_status_bluetooth),
                                contentDescription = null,
                                tint = ink,
                                modifier = Modifier.size(diameter * 0.5f),
                            )
                        }
                        if (showLabels) {
                            Text(
                                text = stringResource(R.string.stats_widget_bluetooth_allow),
                                color = ink.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One ring of the device status widget. */
private data class StatGaugeData(val label: String, val fraction: Float, val color: Color, val icon: Int)

/** The rings stop growing here, so a large widget spreads them out instead of blowing them up. */
private val MAX_GAUGE_DIAMETER = 46.dp

@Composable
private fun StatGauge(gauge: StatGaugeData, ink: Color, diameter: Dp, showPercent: Boolean, showName: Boolean) {
    val clamped = gauge.fraction.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.1f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = ink.copy(alpha = 0.22f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = gauge.color, startAngle = -90f, sweepAngle = 360f * clamped, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Icon(
                painter = painterResource(gauge.icon),
                contentDescription = gauge.label,
                tint = ink,
                modifier = Modifier.size(diameter * 0.46f),
            )
        }
        if (showPercent) {
            Text(
                text = "${(clamped * 100).toInt()}%",
                color = ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (showName) {
            Text(
                text = gauge.label,
                color = ink.copy(alpha = 0.75f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
