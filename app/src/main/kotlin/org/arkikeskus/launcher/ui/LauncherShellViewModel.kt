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
) : ViewModel() {
    private val _screenType = MutableStateFlow(ScreenType.OUTER)
    fun setScreenType(type: ScreenType) { _screenType.value = type }

    val settings: StateFlow<LauncherSettings> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    // The rest are cut from that one stream: each settings(...) collection reads every setting
    // again on every write to the store.
    val iconStyle: StateFlow<IconStyle> = settings
        .map { IconStyle(it.iconPackPackage, it.useThemedIcons, it.iconShape) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconStyle())

    val drawerHeightFraction: StateFlow<Float> = settings.map { it.drawerHeightFraction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerWidthFraction: StateFlow<Float> = settings.map { it.drawerWidthFraction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerAlignment: StateFlow<String> = settings.map { it.drawerAlignment }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.DRAWER_ALIGNMENT_CENTER)

    val iconEpochs: StateFlow<IconEpochs> = launcherAppsSource.iconEpochs

    /** Hand-picked icons (app key → "pack|drawable"), provided to every icon surface. Eagerly: the
     *  first frame should already draw the picked icons instead of flashing the default ones. */
    val customIcons: StateFlow<Map<String, String>> = settingsRepository.customIcons
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
}
