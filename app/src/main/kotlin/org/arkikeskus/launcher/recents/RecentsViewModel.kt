package org.arkikeskus.launcher.recents

import android.app.ActivityManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.OpenTask
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject

/** One card in Folark's Recents: an open app, or the apps sharing a split screen. */
data class RecentsCard(
    val tasks: List<OpenTask>,
    val apps: List<AppItem?>,
    val label: String,
    val inactiveMs: Long?,
) {
    val key: Int get() = tasks.first().taskId
    val isSplit: Boolean get() = tasks.size > 1
}

data class RecentsState(
    val loading: Boolean = true,
    /** The one-time DUMP grant is there (without it Android won't tell Folark what is open). */
    val canList: Boolean = true,
    val root: Boolean = false,
    val cards: List<RecentsCard> = emptyList(),
)

/**
 * Folark's own Recents (Settings ▸ Recents): what is open, read from Android's task list, with
 * root extras (screenshots, closing one app) when root is there right now.
 */
@HiltViewModel
class RecentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Recents settings are shared by both displays. */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings(ScreenType.INNER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    private val _state = MutableStateFlow(RecentsState())
    val state: StateFlow<RecentsState> = _state.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Int, ImageBitmap>> = _thumbnails.asStateFlow()

    private var loadJob: Job? = null

    /** Reads what is open again (each time Recents comes to the front). */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = settingsRepository.settings(ScreenType.INNER).first()
            val result = withContext(Dispatchers.IO) { load(s) }
            _state.value = result
            // Screenshots of apps no longer open are let go.
            val ids = result.cards.flatMap { c -> c.tasks.map { it.taskId } }.toSet()
            _thumbnails.value = _thumbnails.value.filterKeys { it in ids }
            if (s.recentsThumbnails && result.root) loadThumbnails(result.cards)
        }
    }

    private suspend fun load(s: LauncherSettings): RecentsState {
        val tasks = OpenTasks.list(context) ?: return RecentsState(loading = false, canList = false)
        val needRoot = s.recentsThumbnails || s.recentsRootClose
        val root = needRoot && RootShell.available()
        val apps = appRepository.apps.first()
        // Apps sharing the visible split screen stay together on one card.
        val inSplit = ShellSplit.splitTasks(context).map { it.taskId }.toSet()
        val splitGroup = tasks.filter { it.taskId in inSplit }
        val cards = ArrayList<RecentsCard>()
        var splitAdded = false
        for (task in tasks) {
            if (task.taskId in inSplit && splitGroup.size > 1) {
                if (splitAdded) continue
                splitAdded = true
                val groupApps = splitGroup.map { appFor(it, apps) }
                cards += RecentsCard(
                    tasks = splitGroup,
                    apps = groupApps,
                    label = splitGroup.mapIndexed { i, t -> groupApps[i]?.label ?: t.packageName }.joinToString(" + "),
                    inactiveMs = splitGroup.mapNotNull { it.inactiveMs }.minOrNull(),
                )
            } else {
                val app = appFor(task, apps)
                // Only apps that have a place in the drawer: no system plumbing.
                if (app == null) continue
                cards += RecentsCard(listOf(task), listOf(app), app.label, task.inactiveMs)
            }
        }
        val sorted = if (s.recentsSort == LauncherSettings.RECENTS_SORT_NAME) cards.sortedBy { it.label.lowercase() } else cards
        return RecentsState(loading = false, canList = true, root = root, cards = sorted.take(s.recentsMaxApps))
    }

    private fun appFor(task: OpenTask, apps: List<AppItem>): AppItem? =
        apps.firstOrNull { it.packageName == task.packageName && it.user.hashCode() == task.userId }
            ?: apps.firstOrNull { it.packageName == task.packageName }

    /** Android's saved screenshots, one by one so the first cards fill in quickly. */
    private fun loadThumbnails(cards: List<RecentsCard>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (task in cards.flatMap { it.tasks }) {
                if (task.taskId in _thumbnails.value) continue
                val bytes = RootTasks.snapshot(task.taskId, task.userId) ?: continue
                val bitmap = runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull() ?: continue
                _thumbnails.value = _thumbnails.value + (task.taskId to bitmap)
            }
        }
    }

    // ------------------------------------------------------------------ actions

    /** Brings [card] back to the front, as it was (a split comes back as a split). */
    fun open(card: RecentsCard) {
        val task = card.tasks.first()
        val moved = runCatching {
            context.getSystemService(ActivityManager::class.java).moveTaskToFront(task.taskId, 0)
        }.isSuccess
        if (!moved) card.apps.firstOrNull()?.let { appRepository.launch(it) }
    }

    /** Closes [card]'s apps (root), then shows the list again. */
    fun close(card: RecentsCard) {
        _state.value = _state.value.copy(cards = _state.value.cards - card)
        viewModelScope.launch(Dispatchers.IO) {
            for (task in card.tasks) {
                if (!RootTasks.removeTask(context, task.taskId)) RootTasks.forceStop(task.packageName)
            }
        }
    }

    /** Closes everything: Android's own "clear all" (needs only the DUMP grant), else root. */
    fun closeAll() {
        val cards = _state.value.cards
        _state.value = _state.value.copy(cards = emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            if (!ShellSplit.clearAllRecents(context) && _state.value.root) {
                cards.flatMap { it.tasks }.forEach { RootTasks.removeTask(context, it.taskId) }
            }
        }
    }

    fun popUp(card: RecentsCard) {
        card.apps.firstOrNull()?.let { appRepository.launchFloating(it) }
    }

    /** A split card back to one app full screen. */
    fun unsplit(card: RecentsCard) {
        val keep = card.tasks.first().taskId
        appScope.launch(Dispatchers.IO) { ShellSplit.exitSplit(context, keep) }
    }

    /**
     * Split view: the first two side by side, a third joining the split where the phone supports
     * three (Razr Fold) or else as a pop-up, a fourth as a pop-up. Runs on after Recents closes.
     */
    fun openTogether(picked: List<RecentsCard>, thirdInSplit: Boolean) {
        if (picked.size < 2) return
        val tasks = picked.map { it.tasks.first() }
        appScope.launch {
            open(picked[0])
            delay(SPLIT_STEP_MS)
            withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, tasks[1].taskId, right = true) }
            if (picked.size >= 3) {
                delay(SPLIT_STEP_MS)
                val joined = thirdInSplit && withContext(Dispatchers.IO) {
                    ShellSplit.moveToSide(context, tasks[2].taskId, right = true)
                    delay(SPLIT_STEP_MS)
                    ShellSplit.splitTasks(context).any { it.taskId == tasks[2].taskId }
                }
                if (!joined) popUp(picked[2])
            }
            if (picked.size >= 4) {
                delay(SPLIT_STEP_MS)
                popUp(picked[3])
            }
        }
    }

    /** Whether split view can work: Android's split command needs the one-time DUMP grant. */
    fun canSplit(): Boolean = ShellSplit.hasDump(context)

    /** A fresh root check (Settings, or Recents opened after root was turned on again). */
    fun recheckRoot() {
        viewModelScope.launch(Dispatchers.IO) { RootShell.available(recheck = true) }
    }

    private companion object {
        const val SPLIT_STEP_MS = 450L
    }
}
