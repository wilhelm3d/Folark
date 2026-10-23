package org.arkikeskus.launcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.IconEpochs
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import org.arkikeskus.launcher.data.NotificationNudge
import kotlinx.coroutines.flow.SharedFlow
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.data.PersonTileState
import org.arkikeskus.launcher.data.PersonEventKind
import org.arkikeskus.launcher.data.PersonEntry
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.content.Context

/** The app-icon style (icon pack + themed), so the shell's floating drag icon matches the surfaces. */
data class IconStyle(val iconPack: String = "", val themed: Boolean = false, val shape: String = "")

/**
 * Exposes just the icon-style settings to [LauncherShell] so the single floating drag icon — drawn
 * above the home and drawer, outside their own CompositionLocal scopes — renders with the selected
 * icon pack / themed icons instead of the default system icon. Also relays the icon re-fetch epochs
 * (see [org.arkikeskus.launcher.model.IconEpochs]) that the shell provides to every icon surface.
 */
@HiltViewModel
class LauncherShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    launcherAppsSource: LauncherAppsSource,
    private val badgeRepository: NotificationBadgeRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _screenType = MutableStateFlow(ScreenType.OUTER)
    fun setScreenType(type: ScreenType) { _screenType.value = type }

    val settings: StateFlow<LauncherSettings> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    // The rest are cut from that one stream: each settings(...) collection reads every setting
    // again on every write to the store.
    val iconStyle: StateFlow<IconStyle> = settings
        .map { IconStyle(it.icons.iconPack, it.icons.useThemedIcons, it.icons.shape) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconStyle())

    val drawerHeightFraction: StateFlow<Float> = settings.map { it.drawer.heightFraction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerWidthFraction: StateFlow<Float> = settings.map { it.drawer.widthFraction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerAlignment: StateFlow<String> = settings.map { it.drawer.alignment }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.DRAWER_ALIGNMENT_CENTER)

    val iconEpochs: StateFlow<IconEpochs> = launcherAppsSource.iconEpochs

    /** What is going on now, for the live pill. */
    val live: StateFlow<List<LiveActivity>> = badgeRepository.live

    /** Every notification, for the apps' long-press menus. */
    val notifications: StateFlow<List<StatusNotification>> = badgeRepository.allNotifications

    fun dismissNotification(key: String) = badgeRepository.cancelNotification(key)

    /** New notifications as they arrive, to nudge their apps' icons. */
    val nudges: SharedFlow<NotificationNudge> = badgeRepository.nudges

    /** Sender photos for chat apps' dots (app badgeKey → photo), loaded off the main thread. */
    val avatars: StateFlow<Map<String, ImageBitmap>> = settings.map { it.dots.avatars }.distinctUntilChanged()
        .flatMapLatest { on ->
            if (!on) flowOf(emptyMap()) else badgeRepository.people.map(::loadAvatars).flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val avatarCache = HashMap<String, ImageBitmap?>()

    private fun loadAvatars(tiles: List<PersonTileState>): Map<String, ImageBitmap> {
        val newest = HashMap<String, PersonEntry>()
        for (entry in tiles.asSequence().flatMap { it.entries }) {
            if (entry.kind == PersonEventKind.APP || entry.heldUntil != 0L || entry.personIcon == null) continue
            val key = "${entry.packageName}/${entry.userSerial}"
            val current = newest[key]
            if (current == null || entry.postTime > current.postTime) newest[key] = entry
        }
        val used = HashSet<String>()
        val out = HashMap<String, ImageBitmap>()
        for ((badgeKey, entry) in newest) {
            val cacheKey = "${entry.key}@${entry.postTime}"
            used += cacheKey
            val bitmap = avatarCache.getOrPut(cacheKey) {
                runCatching { entry.personIcon?.loadDrawable(context)?.toBitmap(AVATAR_PX, AVATAR_PX)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) out[badgeKey] = bitmap
        }
        avatarCache.keys.retainAll(used)
        return out
    }

    private companion object {
        const val AVATAR_PX = 96
    }

    /** Hand-picked icons (app key → "pack|drawable"), provided to every icon surface. Eagerly: the
     *  first frame should already draw the picked icons instead of flashing the default ones. */
    val customIcons: StateFlow<Map<String, String>> = settingsRepository.customIcons
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
}
