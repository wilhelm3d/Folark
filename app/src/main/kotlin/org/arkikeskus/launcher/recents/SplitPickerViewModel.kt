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
import kotlinx.coroutines.flow.map
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.LauncherSettings
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.data.root.RootShell
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

    val splitEngine = settingsRepository.settings(ScreenType.INNER).map { it.recents.splitEngine }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING)

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(appRepository.apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun formSplit(firstPackage: String, right: Boolean, second: AppItem) {
        val first = apps.value.firstOrNull { it.packageName == firstPackage }
        
        when (splitEngine.value) {
            LauncherSettings.SPLIT_ENGINE_CLASSIC -> {
                appScope.launch {
                    if (first != null) appRepository.launch(first)
                    delay(300)
                    appRepository.launchBeside(second)
                }
            }
            LauncherSettings.SPLIT_ENGINE_LSPOSED -> {
                appScope.launch(Dispatchers.IO) {
                    val t1 = ShellSplit.taskIdOf(context, firstPackage) ?: return@launch
                    val t2 = ShellSplit.taskIdOf(context, second.packageName) ?: return@launch
                    SplitThree.startSplitTasks(context, t1, t2, if (right) 1 else 0)
                }
                appRepository.launch(second)
            }
            LauncherSettings.SPLIT_ENGINE_ROOT -> {
                appScope.launch(Dispatchers.IO) {
                    if (RootShell.available()) {
                        RootTasks.launchSplitPair(if (right) second.packageName else firstPackage, if (right) firstPackage else second.packageName)
                    }
                }
            }
            else -> {
                // SPLIT_ENGINE_INTENT_ROUTING (Default)
                appRepository.launch(second)
            }
        }
    }
}
