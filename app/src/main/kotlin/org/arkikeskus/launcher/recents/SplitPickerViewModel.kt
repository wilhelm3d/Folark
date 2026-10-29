package org.arkikeskus.launcher.recents

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.feature.appdrawer.SplitRecentItem
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

    private val _recents = MutableStateFlow<List<SplitRecentItem>>(emptyList())
    val recents: StateFlow<List<SplitRecentItem>> = _recents.asStateFlow()

    fun loadRecents(firstPackage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allApps = appRepository.apps.first()
            val openTasks = OpenTasks.list(context).orEmpty()
            val root = RootShell.available()
            val items = ArrayList<SplitRecentItem>()
            for (task in openTasks) {
                if (task.packageName == context.packageName) continue
                if (task.packageName == firstPackage) continue
                val app = allApps.firstOrNull { it.packageName == task.packageName } ?: continue
                if (items.any { it.app.packageName == app.packageName }) continue
                val thumb = if (root) RootTasks.snapshot(task.taskId, task.userId)?.let(::decodeSmall) else null
                items += SplitRecentItem(
                    taskId = task.taskId,
                    app = app,
                    inactiveMs = task.inactiveMs,
                    thumbnail = thumb,
                )
            }
            _recents.value = items.take(5)
        }
    }

    private fun decodeSmall(bytes: ByteArray): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }.getOrNull()

    fun formSplit(firstPackage: String, right: Boolean, second: AppItem, peek: Boolean = false) {
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
                        if (peek) {
                            delay(100L)
                            SplitThree.snap(context, if (right) SplitThree.SNAP_PEEK_RIGHT else SplitThree.SNAP_PEEK_LEFT)
                        }
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
                    if (peek) {
                        delay(100L)
                        SplitThree.snap(context, if (right) SplitThree.SNAP_PEEK_RIGHT else SplitThree.SNAP_PEEK_LEFT)
                    }
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
