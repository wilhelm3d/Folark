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
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import org.arkikeskus.launcher.data.LaunchOrigin
import org.arkikeskus.launcher.data.Haptics
import org.arkikeskus.launcher.data.SplitSizes

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
                        label = pairApps.joinToString(" + ") { it!!.label },
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
        val ordered = if (s.recents.sort == LauncherSettings.RECENTS_SORT_NAME) cards.sortedBy { it.label.lowercase() } else cards
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
        // The app grows out of its card (when the card's place was recorded on the tap).
        val options = LaunchOrigin.takeOptions()
        val moved = runCatching {
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

    private fun RecentsCard.splitApp(i: Int) = SplitApp(tasks[i].packageName, apps.getOrNull(i), tasks[i].taskId)

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

    /**
     * Saves the split [card] as a scene: its apps in order, and (with the Vector module) where the
     * divider is now, so the scene opens the same way.
     */
    fun saveAsPair(card: RecentsCard): Boolean {
        if (!canSave(card)) return false
        appScope.launch { scene(card)?.let { appRepository.saveAppPair(it) } }
        return true
    }

    /**
     * The split [card] (two or three apps) as a scene with a shortcut on the home screen of
     * [screen]: the same scene again when it was saved before (same apps), else a new one.
     */
    fun addToHome(card: RecentsCard, screen: ScreenType): Boolean {
        if (!canSave(card)) return false
        appScope.launch {
            val fresh = scene(card) ?: return@launch
            val known = appRepository.appPairs.first().firstOrNull {
                it.app1Key == fresh.app1Key && it.app2Key == fresh.app2Key && it.app3Key == fresh.app3Key
            }
            val pair = known ?: fresh.also { appRepository.saveAppPair(it) }
            val s = settingsRepository.settings(screen).first()
            homeLayoutRepository.addShortcut(
                pair.app1Package,
                org.arkikeskus.launcher.data.local.HomeItemEntity.appPairShortcutId(pair.id),
                pair.app1UserSerial,
                s.home.columns, s.home.rows, screen,
            )
        }
        return true
    }

    /** The split [card] pinned on the taskbar as one scene (the same scene when saved before). */
    fun pinSplitToTaskbar(card: RecentsCard) {
        if (!canSave(card)) return
        appScope.launch {
            val fresh = scene(card) ?: return@launch
            val known = appRepository.appPairs.first().firstOrNull {
                it.app1Key == fresh.app1Key && it.app2Key == fresh.app2Key && it.app3Key == fresh.app3Key
            }
            val pair = known ?: fresh.also { appRepository.saveAppPair(it) }
            val pinned = settingsRepository.settings(ScreenType.INNER).first().taskbar.pinnedPairs
            if (pair.id !in pinned) settingsRepository.setTaskbarPairs(pinned + pair.id)
            Haptics.play(context, Haptics.Event.SPLIT)
        }
    }

    private fun canSave(card: RecentsCard): Boolean = card.apps.size in 2..3 && card.apps.none { it == null }

    /** The scene [card] makes: its apps in order, with the layout the split has now. */
    private suspend fun scene(card: RecentsCard): AppPair? {
        val apps = card.apps
        if (apps.size !in 2..3 || apps.any { it == null }) return null
        val (a, b) = apps.requireNoNulls()
        val c = apps.getOrNull(2)
        val firstTask = card.tasks.firstOrNull()?.taskId ?: -1
        val layout = if (c == null) SplitThree.layoutForSnap(SplitThree.currentSnap(context, firstTask)) else AppPair.LAYOUT_EVEN
        return AppPair(
            id = java.util.UUID.randomUUID().toString(),
            name = apps.joinToString(" + ") { it!!.label },
            app1Key = a.key, app1Package = a.packageName, app1ClassName = a.className, app1UserSerial = a.userSerial,
            app2Key = b.key, app2Package = b.packageName, app2ClassName = b.className, app2UserSerial = b.userSerial,
            app3Key = c?.key.orEmpty(), app3Package = c?.packageName.orEmpty(),
            app3ClassName = c?.className.orEmpty(), app3UserSerial = c?.userSerial ?: 0L,
            peek = layout == AppPair.LAYOUT_PEEK,
            layout = layout,
            // A trio saved from a three-pane split opens as one again.
            thirdMode = if (c != null && card.tasks.size >= 3) AppPair.THIRD_PANE else AppPair.THIRD_AUTO,
            // S11: the divider places as they are now (a free size, three apps' sizes, peek).
            sizes = dividerPlaces(card)?.joinToString(",").orEmpty(),
        )
    }

    /**
     * The split's divider places in thousandths, from where Android laid its tasks out (left to
     * right); null when it didn't say, or for plain half and half.
     */
    private fun dividerPlaces(card: RecentsCard): List<Int>? {
        val bounds = card.tasks.map { it.bounds ?: return null }
        if (bounds.size !in 2..3) return null
        val sideBySide = bounds.map { it[0] }.distinct().size == bounds.size
        val sorted = bounds.sortedBy { if (sideBySide) it[0] else it[1] }
        val start = if (sideBySide) sorted.first()[0] else sorted.first()[1]
        val end = if (sideBySide) sorted.last()[2] else sorted.last()[3]
        val span = (end - start).takeIf { it > 0 } ?: return null
        val places = sorted.dropLast(1).map { b -> (((if (sideBySide) b[2] else b[3]) - start).toLong() * 1000 / span).toInt() }
        return places.takeUnless { SplitSizes.isEven(it) }
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
        appScope.launch { splitCoordinator.exit(keep) }
    }

    /**
     * Split view: [picked][0] left and [picked][1] right as one split (any older split is closed
     * first, see [SplitCoordinator.pair]); a third joins as a third pane ([thirdInSplit] with
     * Android's three-way split through the module when [threeApp]) or else floats, and a fourth floats.
     */
    fun openTogether(picked: List<RecentsCard>, thirdInSplit: Boolean, threeApp: Boolean = false) {
        if (picked.size < 2) return
        appScope.launch {
            val formed = splitCoordinator.pair(first = picked[1].splitApp(0), second = picked[0].splitApp(0), firstOnRight = true)
            if (!formed) {
                openSingleTask(picked[0].tasks.first(), picked[0].apps.firstOrNull())
                return@launch
            }
            if (picked.size >= 3) {
                val tileThird = thirdInSplit && settings.value.launch.threeAppNative
                val joined = tileThird && splitCoordinator.addThird(picked[2].splitApp(0), native = threeApp)
                if (!joined) popUp(picked[2])
            }
            if (picked.size >= 4) {
                delay(SPLIT_STEP_MS)
                popUp(picked[3])
            }
        }
    }

    /**
     * [third] dropped on the split [pair]: the pair made again (left and right as on the card) and
     * the third added once it has settled, as the split drawer does — bringing the old split back
     * first left System UI no split to add to (the third app floated, Recents went home).
     */
    fun addThird(pair: RecentsCard, third: RecentsCard) {
        if (third.tasks.isEmpty()) return
        if (pair.tasks.size < 2) {
            openTogether(listOf(pair, third), thirdInSplit = true, threeApp = true)
            return
        }
        appScope.launch {
            val got = splitCoordinator.three(pair.splitApp(0), pair.splitApp(1), third.splitApp(0), native = true)
            when (got) {
                0 -> openNow(pair)
                2 -> popUp(third)
            }
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
                if (card.tasks.isEmpty()) return
                appScope.launch { splitCoordinator.openPicker(card.splitApp(0), firstOnRight = edge == EDGE_RIGHT) }
            }
            EDGE_THREE -> {
                if (card.tasks.isEmpty()) return
                appScope.launch { splitCoordinator.openPicker(card.splitApp(0), firstOnRight = false, three = true) }
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
            // The peeking app on its side, the main app on the other.
            val formed = splitCoordinator.pair(
                first = SplitApp(peekTask.packageName, peek.apps.firstOrNull(), peekTask.taskId),
                second = SplitApp(mainTask.packageName, main.apps.firstOrNull(), mainTask.taskId),
                firstOnRight = !left,
            )
            if (!formed) return@launch
            if (splitCoordinator.snapPeek(peekOnLeft = left)) return@launch

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
            openNow(card)
            SplitThree.splitSelect(context, task, index)
        }
    }

    fun nativeThreeAppSelect(card: RecentsCard) {
        appScope.launch {
            openNow(card)
            SplitThree.selectThird(context, 3)
        }
    }

    /**
     * Peek view of three ([picked]: the big app first, then the two at the edges): a three-app
     * split made, then Motorola's three-app peek (the middle app big, a strip of each other one at
     * the screen's edges). Without the module or a third pane, as two-app peek view.
     */
    fun openPeekThree(picked: List<RecentsCard>, onTwo: (RecentsCard, RecentsCard) -> Unit) {
        if (picked.size < 3) return
        appScope.launch {
            // The first pick ends up in the middle (Motorola's index 1): [edge, big] then the other edge.
            val formed = splitCoordinator.pair(first = picked[1].splitApp(0), second = picked[0].splitApp(0), firstOnRight = false)
            val joined = formed && splitCoordinator.addThird(picked[2].splitApp(0), native = true, orSideStage = false, peek = true)
            if (!joined) withContext(Dispatchers.Main) { onTwo(picked[0], picked[1]) }
        }
    }

    fun peekSplit(card: RecentsCard, side: String, screen: android.graphics.Rect, peekFraction: Float) {
        val left = side != LauncherSettings.RECENTS_ATTACHED_RIGHT
        appScope.launch {
            openNow(card)
            // A three-app split: Motorola's three-app peek (the middle app big, the others at the edges).
            if (card.tasks.size >= 3) {
                if (SplitThree.ready(context) && SplitThree.snap(context, SplitThree.SNAP_THREE_PEEK)) return@launch
            }
            if (splitCoordinator.snapPeek(peekOnLeft = left)) return@launch
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

        const val MEMORY_KEEP_MS = 30_000L
        const val NO_PICTURE_KEEP_MS = 20_000L
        fun thumbnailBudget(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()

        const val SPLIT_STEP_MS = 200L
        const val ATTACH_SETTLE_MS = 350L

        val SAFE_PKG = Regex("[A-Za-z0-9_.]+")
    }
}
