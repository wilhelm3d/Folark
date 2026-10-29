package org.arkikeskus.launcher.recents

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import org.arkikeskus.launcher.data.SplitPicker
import org.arkikeskus.launcher.data.SplitThree
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
    /** Pinned: first in Recents, left open by Close all (and kept in memory with root). */
    val pinned: Boolean = false,
) {
    /** The pin keys of its apps ("package/user"). */
    val pinKeys: List<String> get() = tasks.map { pinKey(it) }
    val key: Int get() = tasks.first().taskId
    val isSplit: Boolean get() = tasks.size > 1
}

internal fun pinKey(task: OpenTask): String = "${task.packageName}/${task.userId}"

data class RecentsState(
    val loading: Boolean = true,
    /** The one-time DUMP grant is there (without it Android won't tell Folark what is open). */
    val canList: Boolean = true,
    val root: Boolean = false,
    val cards: List<RecentsCard> = emptyList(),
)

@HiltViewModel
class RecentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings(ScreenType.INNER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    val mutedApps: StateFlow<Set<String>> = settingsRepository.mutedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _state = MutableStateFlow(RecentsState())
    val state: StateFlow<RecentsState> = _state.asStateFlow()

    private val cache = object : android.util.LruCache<Int, ImageBitmap>(thumbnailBudget()) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }
    private val _thumbnails = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Int, ImageBitmap>> = _thumbnails.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = settingsRepository.settings(ScreenType.INNER).first()
            val result = withContext(Dispatchers.IO) { load(s) }
            _state.value = result
            val ids = result.cards.flatMap { c -> c.tasks.map { it.taskId } }.toSet()
            cache.snapshot().keys.filterNot { it in ids }.forEach { cache.remove(it) }
            publish()
            if (s.recents.thumbnails && result.root) loadThumbnails(result.cards)
        }
    }

    private suspend fun load(s: LauncherSettings): RecentsState {
        val tasks = OpenTasks.list(context) ?: return RecentsState(loading = false, canList = false)
        val needRoot = s.recents.thumbnails || s.recents.rootClose || (s.recents.pinned.isNotEmpty() && (s.recents.pinKeepAlive || s.recents.btnCloseAll))
        val root = needRoot && RootShell.available()
        val apps = appRepository.apps.first()
        // Use stagedSplitTasks so active split pairs stay grouped on one card even when Recents is covering them.
        val inSplit = ShellSplit.stagedSplitTasks(context).map { it.taskId }.toSet()
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
                    pinned = splitGroup.all { pinKey(it) in s.recents.pinned },
                )
            } else {
                val app = appFor(task, apps)
                if (app == null) continue
                cards += RecentsCard(listOf(task), listOf(app), app.label, task.inactiveMs, pinned = pinKey(task) in s.recents.pinned)
            }
        }
        val ordered = if (s.recents.sort == LauncherSettings.RECENTS_SORT_NAME) cards.sortedBy { it.label.lowercase() } else cards
        val sorted = ordered.sortedByDescending { it.pinned }
        val shown = sorted.filter { it.pinned } + sorted.filterNot { it.pinned }.take((s.recents.maxApps - sorted.count { it.pinned }).coerceAtLeast(0))
        if (root && s.recents.pinKeepAlive) {
            val running = shown.filter { it.pinned }.flatMap { c -> c.tasks.map { it.packageName } }.toSet()
            if (running.isNotEmpty()) rememberExemptions(RootTasks.protect(running))
        }
        return RecentsState(loading = false, canList = true, root = root, cards = shown)
    }

    private fun appFor(task: OpenTask, apps: List<AppItem>): AppItem? =
        apps.firstOrNull { it.packageName == task.packageName && it.user.hashCode() == task.userId }
            ?: apps.firstOrNull { it.packageName == task.packageName }

    private fun loadThumbnails(cards: List<RecentsCard>) {
        viewModelScope.launch(Dispatchers.IO) {
            val missing = ArrayList<Int>()
            for (task in cards.flatMap { it.tasks }) {
                if (cache.get(task.taskId) != null) continue
                val bitmap = RootTasks.snapshot(task.taskId, task.userId)?.let(::decode)
                if (bitmap != null) {
                    cache.put(task.taskId, bitmap)
                    publish()
                } else {
                    missing += task.taskId
                }
            }
            if (missing.isEmpty()) return@launch
            val fromSystem = RootTasks.snapshotsFromSystem(context, missing)
            for ((id, bytes) in fromSystem) decode(bytes)?.let { cache.put(id, it) }
            if (fromSystem.isNotEmpty()) publish()
        }
    }

    private fun publish() {
        _thumbnails.value = cache.snapshot()
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        if (edge <= 0) return@runCatching null
        var sample = 1
        while (edge / (sample * 2) >= THUMB_MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()

    override fun onCleared() {
        cache.evictAll()
        super.onCleared()
    }

    // ------------------------------------------------------------------ actions

    private fun openSingleTask(task: OpenTask, app: AppItem?) {
        val moved = runCatching {
            context.getSystemService(ActivityManager::class.java).moveTaskToFront(task.taskId, 0)
        }.isSuccess
        if (!moved && app != null) appRepository.launch(app)
    }

    /** Brings [card] back to the front, as it was (a split comes back as a split). */
    fun open(card: RecentsCard) {
        if (card.isSplit && card.tasks.size >= 2) {
            appScope.launch {
                openSingleTask(card.tasks[0], card.apps.getOrNull(0))
                delay(160L)
                withContext(Dispatchers.IO) {
                    if (ShellSplit.splitTasks(context).size < 2) {
                        ShellSplit.moveToSideFast(context, card.tasks[1].taskId, right = true)
                    }
                }
            }
            return
        }
        val task = card.tasks.first()
        openSingleTask(task, card.apps.firstOrNull())
    }

    fun close(card: RecentsCard) {
        _state.value = _state.value.copy(cards = _state.value.cards - card)
        viewModelScope.launch(Dispatchers.IO) {
            for (task in card.tasks) {
                if (!RootTasks.removeTask(context, task.taskId)) RootTasks.forceStop(task.packageName)
            }
        }
    }

    fun closeAll() {
        val cards = _state.value.cards
        val root = _state.value.root
        val keep = cards.filter { it.pinned }
        _state.value = _state.value.copy(cards = if (root) keep else emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            val others = cards.filterNot { it.pinned }.flatMap { c -> c.tasks.map { it.taskId } }
            when {
                keep.isNotEmpty() && root -> RootTasks.removeTasks(context, others)
                !ShellSplit.clearAllRecents(context) && root -> RootTasks.removeTasks(context, others)
            }
        }
    }

    fun saveAsPair(card: RecentsCard): Boolean {
        val apps = card.apps
        if (apps.size !in 2..3 || apps.any { it == null }) return false
        val (a, b) = apps.requireNoNulls()
        val c = apps.getOrNull(2)
        appRepository.saveAppPair(
            org.arkikeskus.launcher.model.AppPair(
                id = java.util.UUID.randomUUID().toString(),
                name = apps.joinToString(" + ") { it!!.label },
                app1Key = a.key, app1Package = a.packageName, app1ClassName = a.className, app1UserSerial = a.userSerial,
                app2Key = b.key, app2Package = b.packageName, app2ClassName = b.className, app2UserSerial = b.userSerial,
                app3Key = c?.key.orEmpty(), app3Package = c?.packageName.orEmpty(),
                app3ClassName = c?.className.orEmpty(), app3UserSerial = c?.userSerial ?: 0L,
            ),
        )
        return true
    }

    fun togglePin(card: RecentsCard) {
        val pin = !card.pinned
        _state.value = _state.value.copy(
            cards = _state.value.cards.map { if (it.key == card.key) it.copy(pinned = pin) else it }.sortedByDescending { it.pinned },
        )
        appScope.launch {
            val s = settingsRepository.settings(ScreenType.INNER).first()
            val next = if (pin) s.recents.pinned + card.pinKeys else s.recents.pinned - card.pinKeys.toSet()
            settingsRepository.setRecentsPinned(next)
            if (!s.recents.pinKeepAlive) return@launch
            withContext(Dispatchers.IO) {
                if (!RootShell.available()) return@withContext
                val packages = card.tasks.map { it.packageName }.toSet()
                if (pin) {
                    rememberExemptions(RootTasks.protect(packages))
                } else {
                    val exempt = settingsRepository.pinExemptions()
                    val stillPinned = next.map { it.substringBefore('/') }.toSet()
                    for (p in packages - stillPinned) RootTasks.unprotect(p, p in exempt)
                    settingsRepository.setPinExemptions(exempt - (packages - stillPinned))
                }
            }
        }
    }

    private fun rememberExemptions(added: Set<String>) {
        if (added.isEmpty()) return
        appScope.launch { settingsRepository.setPinExemptions(settingsRepository.pinExemptions() + added) }
    }

    fun appInfo(card: RecentsCard) {
        val app = card.apps.firstOrNull() ?: return
        runCatching {
            context.getSystemService(android.content.pm.LauncherApps::class.java)
                .startAppDetailsActivity(app.componentName, app.user, null, null)
        }
    }

    fun popUp(card: RecentsCard) {
        card.apps.firstOrNull()?.let { appRepository.launchFloating(it) }
    }

    fun unsplit(card: RecentsCard) {
        val keep = card.tasks.first().taskId
        appScope.launch(Dispatchers.IO) { ShellSplit.exitSplit(context, keep) }
    }

    /**
     * Split view: clears any stale split stage first so WMShell never rejects a previously staged
     * task with "App does not support split screen", brings [picked][0] to the front, and docks [picked][1].
     */
    fun openTogether(picked: List<RecentsCard>, thirdInSplit: Boolean, threeApp: Boolean = false) {
        if (picked.size < 2) return
        val tasks = picked.map { it.tasks.first() }
        appScope.launch {
            // 1. If any task is still stuck in a previous split stage, exit split keeping tasks[0] first!
            withContext(Dispatchers.IO) {
                val staged = ShellSplit.allStagedTaskIds(context)
                if (staged.isNotEmpty()) {
                    ShellSplit.exitSplit(context, tasks[0].taskId)
                    delay(120L)
                }
            }
            // 2. Bring the first app to the front full-screen
            openSingleTask(tasks[0], picked[0].apps.firstOrNull())
            delay(SPLIT_STEP_MS)
            // 3. Dock the second app into the right split stage
            withContext(Dispatchers.IO) {
                ShellSplit.moveToSideFast(context, tasks[1].taskId, right = true)
            }
            if (picked.size >= 3) {
                delay(SPLIT_STEP_MS)
                val tileThird = thirdInSplit && settings.value.launch.threeAppNative
                val joined = tileThird && withContext(Dispatchers.IO) {
                    (threeApp && SplitThree.ready(context) && SplitThree.addThird(context, taskId = tasks[2].taskId)) || run {
                        ShellSplit.moveToSideFast(context, tasks[2].taskId, right = true)
                        delay(SPLIT_STEP_MS)
                        ShellSplit.splitTasks(context).any { it.taskId == tasks[2].taskId }
                    }
                }
                if (!joined) popUp(picked[2])
            }
            if (picked.size >= 4) {
                delay(SPLIT_STEP_MS)
                popUp(picked[3])
            }
        }
    }

    fun addThird(pair: RecentsCard, third: RecentsCard) {
        val task = third.tasks.firstOrNull() ?: return
        appScope.launch {
            open(pair)
            delay(SPLIT_STEP_MS)
            val joined = settings.value.launch.threeAppNative && withContext(Dispatchers.IO) {
                SplitThree.ready(context) && SplitThree.addThird(context, taskId = task.taskId)
            }
            if (!joined) popUp(third)
        }
    }

    fun replacePane(target: RecentsCard, paneIndex: Int, with: RecentsCard) {
        if (paneIndex !in target.tasks.indices || target.tasks.size < 2) return
        if (with.tasks.first().taskId in target.tasks.map { it.taskId }) return
        val panes = target.tasks.indices.map { i ->
            if (i == paneIndex) with
            else RecentsCard(
                tasks = listOf(target.tasks[i]),
                apps = listOf(target.apps.getOrNull(i)),
                label = target.apps.getOrNull(i)?.label ?: target.tasks[i].packageName,
                inactiveMs = target.tasks[i].inactiveMs,
            )
        }
        openTogether(panes, thirdInSplit = true, threeApp = true)
    }

    fun placeAtEdge(card: RecentsCard, edge: Int) {
        when (edge) {
            EDGE_TOP -> open(card)
            EDGE_BOTTOM -> popUp(card)
            EDGE_LEFT, EDGE_RIGHT -> {
                val task = card.tasks.firstOrNull() ?: return
                val right = edge == EDGE_RIGHT
                val firstApp = card.apps.firstOrNull()
                appScope.launch {
                    SplitPicker.openBesideApp(
                        context = context,
                        appRepository = appRepository,
                        firstApp = firstApp,
                        firstPackage = task.packageName,
                        firstTaskId = task.taskId,
                        firstOnRight = right,
                    )
                }
            }
        }
    }

    fun canSplit(): Boolean = ShellSplit.hasDump(context)

    fun recheckRoot() {
        viewModelScope.launch(Dispatchers.IO) { RootShell.available(recheck = true) }
    }

    /**
     * Attached (peek) view: clears stale split state first, pairs [main] and [peek], and snaps to
     * Motorola's peek target (or drags the divider to a safe non-dismiss peek fraction).
     */
    fun openAttached(main: RecentsCard, peek: RecentsCard, side: String, peekFraction: Float, screen: android.graphics.Rect) {
        val mainTask = main.tasks.firstOrNull() ?: return
        val peekTask = peek.tasks.firstOrNull() ?: return
        val left = side != LauncherSettings.RECENTS_ATTACHED_RIGHT
        appScope.launch {
            withContext(Dispatchers.IO) {
                val staged = ShellSplit.allStagedTaskIds(context)
                if (staged.isNotEmpty()) {
                    ShellSplit.exitSplit(context, peekTask.taskId)
                    delay(120L)
                }
            }
            openSingleTask(peekTask, peek.apps.firstOrNull())
            delay(SPLIT_STEP_MS)
            val moved = withContext(Dispatchers.IO) { ShellSplit.moveToSideFast(context, mainTask.taskId, right = left) }
            if (!moved) return@launch

            if (SplitThree.ready(context) &&
                SplitThree.snap(context, if (left) SplitThree.SNAP_PEEK_LEFT else SplitThree.SNAP_PEEK_RIGHT)
            ) return@launch

            delay(ATTACH_SETTLE_MS)
            val safeFraction = peekFraction.coerceIn(0.18f, 0.30f)
            val y = screen.exactCenterY()
            val from = screen.exactCenterX()
            val to = if (left) screen.left + screen.width() * safeFraction else screen.right - screen.width() * safeFraction
            DividerDrag.drag(from, y, to, y)
        }
    }

    fun nativeSplitSelect(card: RecentsCard) {
        val task = card.tasks.firstOrNull()?.taskId ?: return
        val index = if (settings.value.recents.attachedSide == LauncherSettings.RECENTS_ATTACHED_RIGHT) 1 else 0
        appScope.launch {
            open(card)
            delay(SPLIT_STEP_MS)
            SplitThree.splitSelect(context, task, index)
        }
    }

    fun nativeThreeAppSelect(card: RecentsCard) {
        appScope.launch {
            open(card)
            delay(SPLIT_STEP_MS)
            SplitThree.selectThird(context, 3)
        }
    }

    fun peekSplit(card: RecentsCard, side: String, screen: android.graphics.Rect, peekFraction: Float) {
        val left = side != LauncherSettings.RECENTS_ATTACHED_RIGHT
        appScope.launch {
            open(card)
            if (SplitThree.ready(context) &&
                SplitThree.snap(context, if (left) SplitThree.SNAP_PEEK_LEFT else SplitThree.SNAP_PEEK_RIGHT)
            ) return@launch
            delay(ATTACH_SETTLE_MS)
            val safeFraction = peekFraction.coerceIn(0.18f, 0.30f)
            val y = screen.exactCenterY()
            val from = screen.exactCenterX()
            val to = if (left) screen.left + screen.width() * safeFraction else screen.right - screen.width() * safeFraction
            DividerDrag.drag(from, y, to, y)
        }
    }

    fun toggleMute(card: RecentsCard) {
        val pkgs = card.tasks.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        if (pkgs.isEmpty()) return
        appScope.launch {
            val current = settingsRepository.mutedPackages()
            val muting = pkgs.none { it in current }
            settingsRepository.setMutedPackages(if (muting) current + pkgs else current - pkgs)
            withContext(Dispatchers.IO) {
                if (RootShell.available()) {
                    val mode = if (muting) "ignore" else "allow"
                    pkgs.forEach { p -> if (SAFE_PKG.matches(p)) RootShell.run("appops set $p PLAY_AUDIO $mode") }
                }
            }
        }
    }

    private companion object {
        const val THUMB_MAX_EDGE = 720

        fun thumbnailBudget(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()

        const val SPLIT_STEP_MS = 200L
        const val ATTACH_SETTLE_MS = 350L

        val SAFE_PKG = Regex("[A-Za-z0-9_.]+")
    }
}
