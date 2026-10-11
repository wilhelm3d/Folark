package org.arkikeskus.launcher.feature.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.launcher.system.BatteryMonitor
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.BatteryStatus
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.AppIcon
import javax.inject.Inject

/** What the device-status widget shows besides the battery (which has its own live stream). */
data class DeviceStats(
    /** Used share of internal storage and of RAM, 0..1. */
    val storageUsed: Float = 0f,
    val memoryUsed: Float = 0f,
    /** Connected Bluetooth devices that report a battery level: name to percent. */
    val bluetooth: List<Pair<String, Int>> = emptyList(),
    /** True when Bluetooth batteries cannot be read until the user grants the permission. */
    val bluetoothNeedsPermission: Boolean = false,
)

/** Shared state of Folark's own widgets (now playing, most used, device status, quick note). */
@HiltViewModel
class FolarkWidgetsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    appUsageRepository: AppUsageRepository,
    batteryMonitor: BatteryMonitor,
) : ViewModel() {

    /** The widgets' themes and options are stored once for both displays, so either profile reads them. */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings(ScreenType.OUTER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LauncherSettings())

    val battery: StateFlow<BatteryStatus?> = batteryMonitor.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    /** Installed, non-hidden apps by how much they are used lately (the drawer's own ranking). */
    val topApps: StateFlow<List<AppItem>> = combine(
        appRepository.apps,
        appUsageRepository.usage,
        settingsRepository.hiddenApps,
    ) { apps, usage, hidden ->
        val now = System.currentTimeMillis()
        apps.asSequence()
            .filter { it.key !in hidden }
            .mapNotNull { app -> usage[app.key]?.let { app to AppUsageRepository.currentScore(it, now) } }
            .filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<AppItem, Float>> { it.second }.thenBy { it.first.label.lowercase() })
            .take(MAX_TOP_APPS)
            .map { it.first }
            .toList()
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val statsRefresh = MutableStateFlow(0)

    // Battery levels as the system announces them, by device address. This is the route open to
    // an ordinary app holding the Bluetooth permission; the direct getters below are refused on
    // most current devices.
    private val announcedLevels = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Int>>()

    /** What the direct getters last said, read at most every ten minutes. */
    @Volatile private var directLevels: Map<String, Pair<String, Int>> = emptyMap()

    /**
     * The system's Bluetooth battery announcements. Listened to only while a status widget is on
     * screen; an announcement updates the widget from memory, without reading the devices again.
     */
    private val bluetoothEvents = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val device = runCatching {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }.getOrNull() ?: return
                val address = runCatching { device.address }.getOrNull() ?: return
                val level = intent?.getIntExtra(EXTRA_BLUETOOTH_BATTERY, -1) ?: -1
                if (intent?.action == ACTION_BLUETOOTH_BATTERY && level in 0..100) {
                    val name = runCatching { device.alias ?: device.name }.getOrNull() ?: "Bluetooth"
                    announcedLevels[address] = name to level
                } else {
                    // Disconnected, or the level is no longer known.
                    announcedLevels.remove(address)
                    directLevels = directLevels - address
                }
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_BLUETOOTH_BATTERY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }.isSuccess
        awaitClose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }

    /** Re-read the device stats now (after the Bluetooth permission was answered). */
    fun refreshStats() {
        bluetoothReadAt = 0L
        statsRefresh.value++
    }

    // Storage hardly moves and Bluetooth batteries move slowly: both are kept between the
    // once-a-minute memory readings (storage for a day, Bluetooth for ten minutes).
    @Volatile private var storageReadAt = 0L
    @Volatile private var cachedStorage = 0f
    @Volatile private var bluetoothReadAt = 0L

    /** Polled only while a status widget is on screen (WhileSubscribed), once a minute. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val deviceStats: StateFlow<DeviceStats> = merge(statsRefresh.map { }, bluetoothEvents).flatMapLatest {
        flow {
            while (true) {
                emit(readDeviceStats())
                delay(STATS_INTERVAL_MS)
            }
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DeviceStats())

    private fun readDeviceStats(): DeviceStats {
        val clock = SystemClock.elapsedRealtime()
        if (storageReadAt == 0L || clock - storageReadAt >= STORAGE_INTERVAL_MS) {
            cachedStorage = runCatching {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.totalBytes.toFloat()
                if (total <= 0f) 0f else 1f - stat.availableBytes / total
            }.getOrDefault(0f)
            storageReadAt = clock
        }
        val storage = cachedStorage
        val memory = runCatching {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            if (info.totalMem <= 0L) 0f else 1f - info.availMem.toFloat() / info.totalMem
        }.getOrDefault(0f)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        return DeviceStats(
            storageUsed = storage.coerceIn(0f, 1f),
            memoryUsed = memory.coerceIn(0f, 1f),
            bluetooth = if (needsPermission) {
                emptyList()
            } else {
                if (bluetoothReadAt == 0L || clock - bluetoothReadAt >= BLUETOOTH_INTERVAL_MS) {
                    directLevels = readDirectLevels()
                    bluetoothReadAt = clock
                }
                bluetoothBatteries()
            },
            bluetoothNeedsPermission = needsPermission,
        )
    }

    /** The connected devices' batteries: the direct readings, with every newer announcement on top. */
    private fun bluetoothBatteries(): List<Pair<String, Int>> {
        val levels = LinkedHashMap(directLevels)
        levels.putAll(announcedLevels)
        return levels.values.sortedBy { it.first.lowercase() }.take(MAX_BLUETOOTH_DEVICES)
    }

    /**
     * Battery levels of the connected Bluetooth devices, by address. The platform keeps these two
     * getters out of the public SDK, so they are looked up by name; on a system that refuses the
     * lookup only the announced levels are shown.
     */
    @SuppressLint("MissingPermission")
    private fun readDirectLevels(): Map<String, Pair<String, Int>> = runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            announcedLevels.clear()
            return@runCatching emptyMap()
        }
        val levels = LinkedHashMap<String, Pair<String, Int>>()
        adapter.bondedDevices.orEmpty().forEach { device ->
            val level = runCatching { device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int }
                .getOrNull() ?: -1
            val connected = runCatching { device.javaClass.getMethod("isConnected").invoke(device) as? Boolean }
                .getOrNull() ?: (level >= 0)
            if (connected && level in 0..100) levels[device.address] = (device.alias ?: device.name ?: "Bluetooth") to level
        }
        levels
    }.getOrDefault(emptyMap())

    fun launch(appItem: AppItem) { appRepository.launch(appItem) }

    fun setNoteText(text: String) = viewModelScope.launch { settingsRepository.setNoteWidgetText(text) }

    private companion object {
        const val MAX_TOP_APPS = 24
        const val MAX_BLUETOOTH_DEVICES = 2

        /** Sent by the system when a connected device reports its battery; not in the public SDK by name. */
        const val ACTION_BLUETOOTH_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BLUETOOTH_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
        /** Memory is re-read this often while a status widget is on screen. */
        const val STATS_INTERVAL_MS = 60_000L
        const val STORAGE_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val BLUETOOTH_INTERVAL_MS = 10L * 60 * 1000
    }
}

/** Background and text colour of one of Folark's own widgets in [theme] (LauncherSettings.WIDGET_THEME_*). */
@Composable
internal fun folarkWidgetColors(theme: String): Pair<Color, Color> {
    val (background, ink) = folarkThemeColors(theme)
    val alpha = LocalWidgetBackgroundAlpha.current
    // A faded card no longer backs dark text, so the text turns white on the wallpaper.
    return background.copy(alpha = background.alpha * alpha) to (if (alpha < WIDGET_LIGHT_TEXT_BELOW) Color.White else ink)
}

@Composable
private fun folarkThemeColors(theme: String): Pair<Color, Color> = when (theme) {
    LauncherSettings.WIDGET_THEME_DARK -> Color(0xE6131418) to Color.White
    LauncherSettings.WIDGET_THEME_LIGHT -> Color(0xF0F5F5F7) to Color(0xFF1B1B1F)
    LauncherSettings.WIDGET_THEME_ACCENT ->
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f) to MaterialTheme.colorScheme.onPrimaryContainer
    // Default: the same translucent (or tonal) card as the other built-in widgets.
    else -> widgetSurfaceColor() to widgetContentColor()
}

/** The card every Folark widget sits on, in its chosen [theme]; [content] gets the text colour. */
@Composable
internal fun FolarkWidgetSurface(
    theme: String,
    modifier: Modifier = Modifier,
    seeThrough: Boolean = false,
    content: @Composable BoxScope.(ink: Color) -> Unit,
) {
    val (background, ink) = folarkWidgetColors(theme)
    val shape = RoundedCornerShape(24.dp)
    Surface(
        // See-through: no fill and no outline, so only the wallpaper shows behind the content.
        modifier = if (seeThrough) modifier else modifier.border(1.dp, ink.copy(alpha = 0.16f * LocalWidgetBackgroundAlpha.current), shape),
        shape = shape,
        color = if (seeThrough) Color.Transparent else background,
        contentColor = ink,
    ) {
        Box { content(ink) }
    }
}

/** A grid of the apps used most lately; it fills whatever footprint it is given and reorders itself. */
@Composable
fun TopAppsWidget(modifier: Modifier = Modifier, viewModel: FolarkWidgetsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.topApps.collectAsStateWithLifecycle()
    FolarkWidgetSurface(theme = settings.appsWidgetTheme, modifier = modifier) { ink ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val labels = settings.appsWidgetLabels
            val cellWidth = 64.dp
            val cellHeight = if (labels) 78.dp else 56.dp
            val columns = (maxWidth / cellWidth).toInt().coerceAtLeast(1)
            val rows = (maxHeight / cellHeight).toInt().coerceAtLeast(1)
            val shown = apps.take(columns * rows)
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.top_apps_widget_empty),
                    color = ink.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    shown.chunked(columns).forEach { rowApps ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowApps.forEach { app ->
                                AppIcon(
                                    appItem = app,
                                    labelColor = ink,
                                    iconSize = 44.dp,
                                    showLabel = labels,
                                    maxLabelLines = 1,
                                    modifier = Modifier.clickable { viewModel.launch(app) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A sticky note: shows the text, a tap opens an editor. One note, shared by every note widget. */
@Composable
fun NoteWidget(modifier: Modifier = Modifier, viewModel: FolarkWidgetsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val fontSize = when (settings.noteWidgetTextSize) {
        LauncherSettings.FOLDER_OPEN_SMALL -> 13.sp
        LauncherSettings.FOLDER_OPEN_LARGE -> 20.sp
        else -> 16.sp
    }
    FolarkWidgetSurface(theme = settings.noteWidgetTheme, modifier = modifier) { ink ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { editing = true }
                .padding(14.dp),
        ) {
            val text = settings.noteWidgetText
            Text(
                text = text.ifBlank { stringResource(R.string.note_widget_hint) },
                color = if (text.isBlank()) ink.copy(alpha = 0.6f) else ink,
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (editing) {
        // Edited in a dialog, not in place: a text field on the home screen would fight the
        // launcher's own gestures and leave the keyboard over the icons.
        var draft by remember { mutableStateOf(settings.noteWidgetText) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.note_widget_name)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 320.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNoteText(draft)
                    editing = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
