package org.arkikeskus.launcher.recents

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.OpenTask
import org.arkikeskus.launcher.data.OpenTasks
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.data.SplitApp
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.data.root.ShellSafe
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RecentsAttachedSide
import org.arkikeskus.launcher.model.RecentsSort
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import org.arkikeskus.launcher.data.LaunchOrigin
import org.arkikeskus.launcher.data.Haptics
import org.arkikeskus.launcher.data.SplitSizes
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.data.setRecentsPinned
import org.arkikeskus.launcher.data.setTaskbarPairs

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
    /** Android listed tasks in a form Folark can't read (see OpenTasks.formatProblem): an empty
     *  list then means "can't tell", not "nothing open". */
    val unreadable: Boolean = false,
)

@HiltViewModel
class RecentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val splitCoordinator: SplitCoordinator,
    private val homeLayoutRepository: org.arkikeskus.launcher.data.HomeLayoutRepository,
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

    // FIX #5: register a ComponentCallbacks2 listener so the LruCache proactively releases
    // thumbnails when Android signals memory pressure, instead of holding the full budget until
    // the ViewModel is destroyed. This prevents the launcher from competing for RAM during
    // low-memory situations (e.g. while gaming or during multitasking on low-RAM devices).
    private val memoryCallbacks = object : ComponentCallbacks2 {
        // Android 14+ sends only these two levels. The cache is what makes Recents open fast, so
        // it is not emptied just because Folark left the screen (UI_HIDDEN comes every time an
        // app is opened from Recents): halved then, emptied only once Folark sits in the
        // background, where Android may soon need the memory.
        override fun onTrimMemory(level: Int) {
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> cache.evictAll()
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> cache.trimToSize(cache.maxSize() / 2)
            }
        }
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = cache.evictAll()
    }

    init {
        context.registerComponentCallbacks(memoryCallbacks)
    }

    /** Memory in use per package (KB), with root and Settings ▸ Recents ▸ Memory use on cards. */
    private val _memory = MutableStateFlow<Map<String, Long>>(emptyMap())
    val memory: StateFlow<Map<String, Long>> = _memory.asStateFlow()

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = settingsRepository.settings(ScreenType.INNER).first()
            val result = withContext(Dispatchers.IO) { load(s) }
            _state.value = result
            val ids = result.cards.flatMap { c -> c.tasks.map { it.taskId } }.toSet()
            cache.snapshot().keys.filterNot { it in ids }.forEach { cache.remove(it) }
            // The app just left (the newest card) has changed since its picture was taken: its
            // stale picture stays up until the fresh one is in, then is replaced.
            val newest = result.cards.minByOrNull { it.inactiveMs ?: Long.MAX_VALUE }?.tasks?.map { it.taskId }.orEmpty()
            publish()
            if (s.recents.thumbnails && result.root) loadThumbnails(result.cards, refresh = newest.toSet())
            if (s.recents.memoryBadge && result.root) {
                val packages = result.cards.flatMap { c -> c.tasks.map { it.packageName } }.toSet()
                // Read again only after a while, or when new apps show up (E16).
                val fresh = System.currentTimeMillis() - memoryAt < MEMORY_KEEP_MS && packages.all { it in _memory.value }
                if (!fresh) {
                    _memory.value = withContext(Dispatchers.IO) { RootTasks.memoryKb(packages) }
                    memoryAt = System.currentTimeMillis()
                }
            } else {
                _memory.value = emptyMap()
            }
        }
    }

    /** When the memory badge was last read (E16). */
    private var memoryAt = 0L
    /** Tasks Android's task manager had no picture for, and when (E13: no helper start for them). */
    private val noPicture = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    private suspend fun load(s: LauncherSettings): RecentsState = kotlinx.coroutines.coroutineScope {
        // The task list and the split read are two separate dumps: both at once (E11).
        val splitRead = if (s.recents.groupSplits) async { ShellSplit.stagedSplitTasks(context).map { it.taskId }.toSet() } else null
        loadWith(s, splitRead)
    }

    private suspend fun loadWith(s: LauncherSettings, splitRead: kotlinx.coroutines.Deferred<Set<Int>>?): RecentsState {
        val tasks = OpenTasks.list(context) ?: return RecentsState(loading = false, canList = false)
        if (tasks.isEmpty() && OpenTasks.formatProblem != null) {
            return RecentsState(loading = false, canList = true, unreadable = true)
        }
        val needRoot = s.recents.thumbnails || s.recents.rootClose || s.recents.memoryBadge || (s.recents.pinned.isNotEmpty() && (s.recents.pinKeepAlive || s.recents.btnCloseAll))
        val root = needRoot && RootShell.available()
        val apps = appRepository.apps.first()
        // Apps by package and user, built once per load (E15).
        val byPackageUser = HashMap<String, AppItem>(apps.size * 2)
        val byPackage = HashMap<String, AppItem>(apps.size)
        for (a in apps) {
            byPackageUser.putIfAbsent("${a.packageName}#${a.user.hashCode()}", a)
            byPackage.putIfAbsent(a.packageName, a)
        }
        fun appFor(task: OpenTask, @Suppress("UNUSED_PARAMETER") all: List<AppItem>): AppItem? =
            byPackageUser["${task.packageName}#${task.userId}"] ?: byPackage[task.packageName]
        // Use stagedSplitTasks so active split pairs stay grouped on one card even when Recents is covering them.
        val inSplit = splitRead?.await() ?: emptySet()
        val splitGroup = tasks.filter { it.taskId in inSplit }
        // Older split pairs (not the one on screen) are still listed as two split-screen tasks next
        // to each other: each such pair becomes one double card too.
        val olderPairs = if (s.recents.groupSplits) pairOlderSplits(tasks.filter { it.taskId !in inSplit }) else emptyMap()
        val cards = ArrayList<RecentsCard>()
        var splitAdded = false
        for (task in tasks) {
            val partner = olderPairs[task.taskId]
            if (partner != null) {
                if (partner.taskId in cards.flatMap { c -> c.tasks.map { it.taskId } }) continue
                val pair = listOf(task, partner)
                val pairApps = pair.map { appFor(it, apps) }
                if (pairApps.all { it != null }) {
                    cards += RecentsCard(
                        tasks = pair,
                        apps = pairApps,
                        label = pairApps.filterNotNull().joinToString(" + ") { it.label },
                        inactiveMs = pair.mapNotNull { it.inactiveMs }.minOrNull(),
                        pinned = pair.all { pinKey(it) in s.recents.pinned },
                    )
                    continue
                }
            }
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
        val ordered = if (s.recents.sort == RecentsSort.NAME) cards.sortedBy { it.label.lowercase() } else cards
        val sorted = ordered.sortedByDescending { it.pinned }
        val shown = sorted.filter { it.pinned } + sorted.filterNot { it.pinned }.take((s.recents.maxApps - sorted.count { it.pinned }).coerceAtLeast(0))
        if (root && s.recents.pinKeepAlive) {
            val running = shown.filter { it.pinned }.flatMap { c -> c.tasks.map { it.packageName } }.toSet()
            if (running.isNotEmpty()) rememberExemptions(RootTasks.protect(running))
        }
        return RecentsState(loading = false, canList = true, root = root, cards = shown)
    }

    /**
     * Pairs of neighbouring split-screen tasks (Android keeps a split's two tasks together in its
     * list), each task mapped to its partner. A third split task in a row starts a new pair.
     */
    private fun pairOlderSplits(tasks: List<OpenTask>): Map<Int, OpenTask> {
        val pairs = HashMap<Int, OpenTask>()
        var i = 0
        while (i < tasks.size - 1) {
            val a = tasks[i]
            val b = tasks[i + 1]
            if (a.windowingMode == "multi-window" && b.windowingMode == "multi-window" && a.packageName != b.packageName) {
                pairs[a.taskId] = b
                pairs[b.taskId] = a
                i += 2
            } else {
                i++
            }
        }
        return pairs
    }

    private fun appFor(task: OpenTask, apps: List<AppItem>): AppItem? =
        apps.firstOrNull { it.packageName == task.packageName && it.user.hashCode() == task.userId }
            ?: apps.firstOrNull { it.packageName == task.packageName }

    /** [refresh]: tasks whose cached picture is taken again anyway (the app just left). */
    private fun loadThumbnails(cards: List<RecentsCard>, refresh: Set<Int> = emptySet()) {
        viewModelScope.launch(Dispatchers.IO) {
            val wanted = cards.flatMap { it.tasks }
                .filter { it.taskId in refresh || cache.get(it.taskId) == null }
                .associate { it.taskId to it.userId }
            if (wanted.isEmpty()) return@launch
            // All saved pictures in one root call, shown together (E12, E14).
            val files = RootTasks.snapshots(context, wanted)
            var added = false
            for ((id, bytes) in files) decode(bytes)?.let { cache.put(id, it); added = true }
            if (added) publish()
            // The rest from Android's task manager (a helper start, about half a second): not again
            // for a task it had nothing for a moment ago (E13).
            val now = System.currentTimeMillis()
            noPicture.entries.removeAll { now - it.value > NO_PICTURE_KEEP_MS }
            val missing = wanted.keys.filter { it !in files.keys && !noPicture.containsKey(it) }
            if (missing.isEmpty()) return@launch
            val fromSystem = RootTasks.snapshotsFromSystem(context, missing)
            for ((id, bytes) in fromSystem) decode(bytes)?.let { cache.put(id, it) }
            missing.filter { it !in fromSystem }.forEach { noPicture[it] = now }
            if (fromSystem.isNotEmpty()) publish()
        }
    }

    private fun publish() {
        _thumbnails.value = cache.snapshot()
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = catching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        if (edge <= 0) return@catching null
        var sample = 1
        while (edge / (sample * 2) >= THUMB_MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()

    override fun onCleared() {
        context.unregisterComponentCallbacks(memoryCallbacks)
        cache.evictAll()
        super.onCleared()
    }

    // ------------------------------------------------------------------ actions

    private fun openSingleTask(task: OpenTask, app: AppItem?) {
        // The app grows out of its card (when the card's place was recorded on the tap).
        val options = LaunchOrigin.takeOptions()
        val moved = catching {
            context.getSystemService(ActivityManager::class.java).moveTaskToFront(task.taskId, 0, options)
        }.isSuccess
        if (moved) Haptics.play(context, Haptics.Event.LAUNCH)
        if (!moved && app != null) appRepository.launch(app)
    }

    /** Brings [card] back to the front, as it was (a split comes back as a split). */
    fun open(card: RecentsCard) {
        if (card.isSplit && card.tasks.size >= 2) {
            appScope.launch { openNow(card) }
            return
        }
        val task = card.tasks.first()
        openSingleTask(task, card.apps.firstOrNull())
    }

    /** [open], waiting until the card is in front (a split: until it is a split again). */
    private suspend fun openNow(card: RecentsCard) {
        if (card.isSplit && card.tasks.size >= 2) {
            if (!splitCoordinator.restore(card.splitApp(0), card.splitApp(1))) openSingleTask(card.tasks[0], card.apps.getOrNull(0))
        } else {
            openSingleTask(card.tasks.first(), card.apps.firstOrNull())
            delay(SPLIT_STEP_MS)
        }
    }


    fun close(card: RecentsCard) {
        Haptics.play(context, Haptics.Event.CLOSE)
        _state.value = _state.value.copy(cards = _state.value.cards - card)
        viewModelScope.launch(Dispatchers.IO) {
            for (task in card.tasks) {
                if (!RootTasks.removeTask(context, task.taskId)) RootTasks.forceStop(task.packageName)
            }
        }
    }

    fun closeAll() {
        Haptics.play(context, Haptics.Event.CLOSE_ALL)
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

    /** Saving a split card as a scene: an app pair, a home shortcut, a taskbar pin. */
    private val scenes = RecentsScenes(context, appRepository, settingsRepository, homeLayoutRepository, appScope)

    fun saveAsPair(card: RecentsCard): Boolean = scenes.saveAsPair(card)
    fun addToHome(card: RecentsCard, screen: ScreenType): Boolean = scenes.addToHome(card, screen)
    fun pinSplitToTaskbar(card: RecentsCard) = scenes.pinSplitToTaskbar(card)

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
        catching {
            context.getSystemService(android.content.pm.LauncherApps::class.java)
                .startAppDetailsActivity(app.componentName, app.user, null, null)
        }
    }

    fun popUp(card: RecentsCard) {
        card.apps.firstOrNull()?.let { appRepository.launchFloating(it) }
    }

    fun unsplit(card: RecentsCard) {
        val keep = card.tasks.first().taskId
        appScope.launch { splitCoordinator.exit(keep) }
    }

    /** Making split views and peek views from cards (RecentsSplitActions.kt). */
    private val splitActions = RecentsSplitActions(context, splitCoordinator, appScope, settings, object : RecentsSplitActions.Host {
        override fun open(card: RecentsCard) = this@RecentsViewModel.open(card)
        override fun popUp(card: RecentsCard) = this@RecentsViewModel.popUp(card)
        override suspend fun openNow(card: RecentsCard) = this@RecentsViewModel.openNow(card)
        override fun openSingleTask(task: OpenTask, app: AppItem?) = this@RecentsViewModel.openSingleTask(task, app)
    })

    fun openTogether(picked: List<RecentsCard>, thirdInSplit: Boolean, threeApp: Boolean = false) = splitActions.openTogether(picked, thirdInSplit, threeApp)
    fun addThird(pair: RecentsCard, third: RecentsCard) = splitActions.addThird(pair, third)
    fun replacePane(target: RecentsCard, paneIndex: Int, with: RecentsCard) = splitActions.replacePane(target, paneIndex, with)
    fun placeAtEdge(card: RecentsCard, edge: Int) = splitActions.placeAtEdge(card, edge)
    fun openAttached(main: RecentsCard, peek: RecentsCard, side: RecentsAttachedSide, peekFraction: Float, screen: android.graphics.Rect) =
        splitActions.openAttached(main, peek, side, peekFraction, screen)
    fun nativeSplitSelect(card: RecentsCard) = splitActions.nativeSplitSelect(card)
    fun nativeThreeAppSelect(card: RecentsCard) = splitActions.nativeThreeAppSelect(card)
    fun openPeekThree(picked: List<RecentsCard>, onTwo: (RecentsCard, RecentsCard) -> Unit) = splitActions.openPeekThree(picked, onTwo)
    fun peekSplit(card: RecentsCard, side: RecentsAttachedSide, screen: android.graphics.Rect, peekFraction: Float) =
        splitActions.peekSplit(card, side, screen, peekFraction)

    fun canSplit(): Boolean = ShellSplit.hasDump(context)

    fun recheckRoot() {
        viewModelScope.launch(Dispatchers.IO) { RootShell.available(recheck = true) }
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
                    pkgs.forEach { p -> if (ShellSafe.isName(p)) RootShell.run("appops set $p PLAY_AUDIO $mode") }
                }
            }
        }
    }

    private companion object {
        const val THUMB_MAX_EDGE = 720

        const val MEMORY_KEEP_MS = 30_000L
        const val NO_PICTURE_KEEP_MS = 20_000L
        fun thumbnailBudget(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()

    }
}
