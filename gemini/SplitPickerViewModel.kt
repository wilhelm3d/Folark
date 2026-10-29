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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject

@HiltViewModel
class SplitPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val apps: StateFlow<List<AppItem>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val splitEngine = settingsRepository.settings(ScreenType.INNER)
        .map { it.recents.splitEngine }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING)

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(appRepository.apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun formSplit(firstPackage: String, right: Boolean, second: AppItem) {
        val first = apps.value.firstOrNull { it.packageName == firstPackage }
        val engine = splitEngine.value

        val leftPkg = if (right) second.packageName else firstPackage
        val rightPkg = if (right) firstPackage else second.packageName

        appScope.launch(Dispatchers.IO) {
            delay(80L)

            if (engine == LauncherSettings.SPLIT_ENGINE_LSPOSED || engine == LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING) {
                if (SplitThree.ready(context)) {
                    val t1 = ShellSplit.taskIdOf(context, firstPackage) ?: -1
                    val t2 = ShellSplit.taskIdOf(context, second.packageName) ?: -1
                    if (SplitThree.startSplitTasks(context, t1, t2, SplitThree.SNAP_EVEN)) {
                        return@launch
                    }
                }
            }

            if (ShellSplit.hasDump(context)) {
                withContext(Dispatchers.Main) {
                    if (first != null) appRepository.launch(first)
                }
                delay(180L)
                withContext(Dispatchers.Main) {
                    appRepository.launch(second)
                }
                delay(160L)
                var rightTaskId: Int? = null
                repeat(8) {
                    rightTaskId = ShellSplit.taskIdOf(context, second.packageName)
                    if (rightTaskId != null) return@repeat
                    delay(40L)
                }
                val id = rightTaskId
                if (id != null && ShellSplit.moveToSide(context, id, right = true)) {
                    return@launch
                }
            }

            if (RootShell.available()) {
                if (RootTasks.launchSplitPair(leftPkg, rightPkg)) {
                    return@launch
                }
            }
        }
    }
}
