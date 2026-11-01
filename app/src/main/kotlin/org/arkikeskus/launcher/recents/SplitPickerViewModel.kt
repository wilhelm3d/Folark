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
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.feature.appdrawer.DrawerFolderUi
import org.arkikeskus.launcher.feature.appdrawer.SplitRecentItem
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SplitPickerLook
import javax.inject.Inject

/** Backs [SplitPickerActivity]: what the drawer shows (as set in Settings) and building the split. */
@HiltViewModel
class SplitPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    appUsageRepository: AppUsageRepository,
) : ViewModel() {

    val look: StateFlow<SplitPickerLook> = settingsRepository.settings(ScreenType.INNER)
        .map { it.recents.picker }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitPickerLook())

    /** Apps that can go into a split, in the order set (by name, or most used first). */
    val apps: StateFlow<List<AppItem>> =
        combine(appRepository.apps, look, appUsageRepository.usage) { apps, look, usage ->
            val usable = apps.filter { !it.isHidden && !it.privateProfile && !it.isArchived }
            if (look.sort == SplitPickerLook.SORT_USAGE) {
                val now = System.currentTimeMillis()
                usable.sortedWith(
                    compareByDescending<AppItem> { a -> usage[a.key]?.let { AppUsageRepository.currentScore(it, now) } ?: 0f }
                        .thenBy { it.label.lowercase() },
                )
            } else {
                usable.sortedBy { it.label.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<DrawerFolderUi>> =
        combine(apps, settingsRepository.drawerFolders) { apps, folders ->
            val byKey = apps.associateBy { it.key }
            folders.map { f -> DrawerFolderUi(f.id, f.name, f.appKeys.mapNotNull { byKey[it] }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recents = MutableStateFlow<List<SplitRecentItem>>(emptyList())
    val recents: StateFlow<List<SplitRecentItem>> = _recents.asStateFlow()

    /**
     * The row of recent apps: the newest open apps but the first one, as many as set. Screenshots
     * only with previews on and root, and only for the apps shown (each is a root call).
     */
    fun loadRecents(firstPackage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val look = settingsRepository.settings(ScreenType.INNER).first().recents.picker
            if (look.recents == SplitPickerLook.RECENTS_OFF) {
                _recents.value = emptyList()
                return@launch
            }
            val byPackage = appRepository.apps.first()
                .filter { !it.isHidden && !it.privateProfile && !it.isArchived }
                .groupBy { it.packageName }
            val picked = OpenTasks.list(context).orEmpty()
                .asSequence()
                .filter { it.packageName != firstPackage && !it.packageName.startsWith(context.packageName) }
                .mapNotNull { task -> byPackage[task.packageName]?.firstOrNull()?.let { task to it } }
                .distinctBy { (_, app) -> app.packageName }
                .take(look.recentsCount)
                .toList()
            val withShots = look.recents == SplitPickerLook.RECENTS_PREVIEWS && RootShell.available()
            _recents.value = picked.map { (task, app) -> SplitRecentItem(task.taskId, app, task.inactiveMs) }
            if (withShots) {
                _recents.value = picked.map { (task, app) ->
                    SplitRecentItem(task.taskId, app, task.inactiveMs, RootTasks.snapshot(task.taskId, task.userId)?.let(::decodeSmall))
                }
            }
        }
    }

    private fun decodeSmall(bytes: ByteArray): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }.getOrNull()

    /** Narrows the split showing now so the first app only peeks in (once the picked app is up). */
    fun snapPeekLater(firstOnRight: Boolean) {
        appScope.launch {
            delay(PEEK_AFTER_MS)
            SplitThree.snap(context, if (firstOnRight) SplitThree.SNAP_PEEK_RIGHT else SplitThree.SNAP_PEEK_LEFT)
        }
    }

    /**
     * Builds the split once the picker has closed: the first app ([firstKey] / [firstPackage], its
     * task [firstTaskId] when known) on [firstOnRight]'s side and [second] on the other, the way the
     * Split engine setting says:
     * - Automatic: the module's native split (both tasks at once), else WM Shell's commands
     *   (DUMP grant, or root without it);
     * - Classic: the commands; Root: the commands through root;
     * - Native: the module only (the commands only while the module isn't running).
     */
    fun formSplit(
        firstPackage: String,
        firstKey: String?,
        firstTaskId: Int?,
        firstOnRight: Boolean,
        second: AppItem,
        peek: Boolean = false,
    ) {
        appScope.launch {
            val engine = settingsRepository.settings(ScreenType.INNER).first().recents.splitEngine
            val all = appRepository.apps.first()
            val first = all.firstOrNull { it.key == firstKey } ?: all.firstOrNull { it.packageName == firstPackage }
            // Let the picker go (its half of the split closes with it) before anything moves.
            delay(PICKER_CLOSE_MS)
            val hasDump = ShellSplit.hasDump(context)
            val root = withContext(Dispatchers.IO) { RootShell.available() }
            val moduleReady = SplitThree.ready(context)
            val job = SplitJob(firstPackage, first, firstTaskId, firstOnRight, second)
            val done = when (engine) {
                LauncherSettings.SPLIT_ENGINE_CLASSIC -> hasDump && job.byCommands(root = false)
                LauncherSettings.SPLIT_ENGINE_ROOT -> root && job.byCommands(root = true)
                LauncherSettings.SPLIT_ENGINE_LSPOSED ->
                    if (moduleReady) job.natively(root = !hasDump && root) else (hasDump || root) && job.byCommands(root = !hasDump)
                else -> (moduleReady && job.natively(root = !hasDump && root)) || ((hasDump || root) && job.byCommands(root = !hasDump))
            }
            if (!done) {
                // Nothing could split them: at least the picked app opens.
                withContext(Dispatchers.Main) { appRepository.launch(second) }
                return@launch
            }
            if (peek) {
                delay(PEEK_AFTER_MS)
                SplitThree.snap(context, if (firstOnRight) SplitThree.SNAP_PEEK_RIGHT else SplitThree.SNAP_PEEK_LEFT)
            }
        }
    }

    private inner class SplitJob(
        val firstPackage: String,
        val first: AppItem?,
        val firstTaskId: Int?,
        val firstOnRight: Boolean,
        val second: AppItem,
    ) {
        /** The module's `startTasks`: both apps running first, then one call puts them side by side. */
        suspend fun natively(root: Boolean): Boolean {
            val canQuery = ShellSplit.hasDump(context) || root
            if (!canQuery) return false
            withContext(Dispatchers.IO) { ShellSplit.exitSplit(context, keepTaskId = -1, root = root) }
            val t2 = running(second.packageName, null, root) ?: run {
                withContext(Dispatchers.Main) { appRepository.launch(second) }
                SplitPicker.waitForTask(context, second.packageName, null, root)
            } ?: return false
            val t1 = running(firstPackage, firstTaskId, root) ?: run {
                SplitPicker.bringToFront(context, appRepository, first, firstPackage, null, root)
            } ?: return false
            val position = if (firstOnRight) SplitThree.SPLIT_POSITION_RIGHT else SplitThree.SPLIT_POSITION_LEFT
            if (!SplitThree.startSplitTasks(context, t1, t2, position)) return false
            return formed(t1, t2, root)
        }

        /** WM Shell's commands, the taskbar's proven order: the picked app, then the first one, moved to its side. */
        suspend fun byCommands(root: Boolean): Boolean {
            withContext(Dispatchers.IO) { ShellSplit.exitSplit(context, keepTaskId = -1, root = root) }
            withContext(Dispatchers.Main) { appRepository.launch(second) }
            val t2 = SplitPicker.waitForTask(context, second.packageName, null, root) ?: return false
            delay(PARTNER_SETTLE_MS)
            val t1 = SplitPicker.bringToFront(context, appRepository, first, firstPackage, firstTaskId, root) ?: return false
            delay(FRONT_SETTLE_MS)
            val moved = withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, t1, right = firstOnRight, root = root) }
            return moved && formed(t1, t2, root)
        }

        /** [taskId] if it is running, else [packageName]'s newest running task; null when it isn't open. */
        private suspend fun running(packageName: String, taskId: Int?, root: Boolean): Int? = withContext(Dispatchers.IO) {
            if (taskId != null && ShellSplit.isRunning(context, taskId, root)) return@withContext taskId
            ShellSplit.taskIdOf(context, packageName, root)?.takeIf { ShellSplit.isRunning(context, it, root) }
        }

        /** Both tasks are in split screen (checked a few times while the transition runs). */
        private suspend fun formed(t1: Int, t2: Int, root: Boolean): Boolean = withContext(Dispatchers.IO) {
            repeat(VERIFY_TRIES) {
                delay(VERIFY_STEP_MS)
                val staged = ShellSplit.allStagedTaskIds(context, root)
                if (t1 in staged && t2 in staged) return@withContext true
            }
            false
        }
    }

    private companion object {
        const val PICKER_CLOSE_MS = 300L
        const val PARTNER_SETTLE_MS = 300L
        const val FRONT_SETTLE_MS = 200L
        const val PEEK_AFTER_MS = 450L
        const val VERIFY_TRIES = 6
        const val VERIFY_STEP_MS = 150L
    }
}
