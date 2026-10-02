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
data class IconStyle(val iconPack: String = "", val themed: Boolean = false)

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

    val iconStyle: StateFlow<IconStyle> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType)
            .map { IconStyle(it.iconPackPackage, it.useThemedIcons) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconStyle())

    val drawerHeightFraction: StateFlow<Float> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType).map { it.drawerHeightFraction }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerWidthFraction: StateFlow<Float> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType).map { it.drawerWidthFraction }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val drawerAlignment: StateFlow<String> = _screenType.flatMapLatest { screenType ->
        settingsRepository.settings(screenType).map { it.drawerAlignment }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.DRAWER_ALIGNMENT_CENTER)

    val iconEpochs: StateFlow<IconEpochs> = launcherAppsSource.iconEpochs
}
