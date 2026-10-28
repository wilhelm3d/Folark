package org.arkikeskus.launcher.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.model.AppItem
import javax.inject.Inject

/** Backs [SplitPickerActivity]: the app list and the drawer folders (resolved), and launching a pick. */
@HiltViewModel
class SplitPickerViewModel @Inject constructor(
    private val appRepository: AppRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val apps: StateFlow<List<AppItem>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(appRepository.apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens the picked app. In a split pane this replaces the picker with the app, as Android slides it in. */
    fun launch(app: AppItem) {
        appRepository.launch(app)
    }
}
