package org.arkikeskus.launcher.recents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.model.AppItem
import javax.inject.Inject

/** Backs [SplitPickerActivity]: the app list, the drawer folders (resolved), and building the split. */
@HiltViewModel
class SplitPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val apps: StateFlow<List<AppItem>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(appRepository.apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Builds the split now that both apps are known: [second] (the pick) and the first app named by
     * [firstPackage], which lands on the [right] side. Same order the taskbar split uses — launch the
     * partner, then the first app, then move it to its side — so it forms reliably. Runs on the
     * application scope so it survives the picker finishing.
     */
    fun formSplit(firstPackage: String, right: Boolean, second: AppItem) {
        val first = apps.value.firstOrNull { it.packageName == firstPackage }
        appScope.launch {
            appRepository.launch(second)
            delay(PARTNER_DELAY_MS)
            if (first != null) appRepository.launch(first)
            delay(SETTLE_DELAY_MS)
            val target = (first ?: second).packageName
            val id = withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, target) }
            if (id != null) withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = right) }
        }
    }

    private companion object {
        const val PARTNER_DELAY_MS = 450L
        const val SETTLE_DELAY_MS = 900L
    }
}
