package org.arkikeskus.launcher.recents

import org.arkikeskus.launcher.data.SplitThree
import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

    /** Packages the user has muted from Recents (persistent; see [toggleMute]). */
    val mutedApps: StateFlow<Set<String>> = settingsRepository.mutedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _state = MutableStateFlow(RecentsState())
    val state: StateFlow<RecentsState> = _state.asStateFlow()

    /**
     * The screenshots, decoded small (see [decode]) and held in a cache bounded by bytes: the
     * least recently shown ones go first when it is full. [_thumbnails] is what the screen draws.
     */
    private val cache = object : android.util.LruCache<Int, ImageBitmap>(thumbnailBudget()) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }
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
                    pinned = splitGroup.all { pinKey(it) in s.recents.pinned },
                )
            } else {
                val app = appFor(task, apps)
                // Only apps that have a place in the drawer: no system plumbing.
                if (app == null) continue
                cards += RecentsCard(listOf(task), listOf(app), app.label, task.inactiveMs, pinned = pinKey(task) in s.recents.pinned)
            }
        }
        val ordered = if (s.recents.sort == LauncherSettings.RECENTS_SORT_NAME) cards.sortedBy { it.label.lowercase() } else cards
        // Pinned apps first (in the same order among themselves); they never fall off the end.
        val sorted = ordered.sortedByDescending { it.pinned }
        val shown = sorted.filter { it.pinned } + sorted.filterNot { it.pinned }.take((s.recents.maxApps - sorted.count { it.pinned }).coerceAtLeast(0))
        // Pinned apps kept in memory: their priority is given again (Android resets it).
        if (root && s.recents.pinKeepAlive) {
            val running = shown.filter { it.pinned }.flatMap { c -> c.tasks.map { it.packageName } }.toSet()
            if (running.isNotEmpty()) rememberExemptions(RootTasks.protect(running))
        }
        return RecentsState(loading = false, canList = true, root = root, cards = shown)
    }

    private fun appFor(task: OpenTask, apps: List<AppItem>): AppItem? =
        apps.firstOrNull { it.packageName == task.packageName && it.user.hashCode() == task.userId }
            ?: apps.firstOrNull { it.packageName == task.packageName }

    /**
     * Android's screenshots of the open apps. First its saved files (quick, one by one so the first
     * cards fill in at once); the ones it has only in memory, or keeps elsewhere on this Android
     * version, are then asked from Android's task manager through the root helper, all together.
     */
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

    /**
     * A screenshot decoded no bigger than a card needs: shrunk by whole steps (halving) until it
     * is at most [THUMB_MAX_EDGE] px on its long side, in 16-bit colour (screenshots have no see-
     * through parts). A full inner-screen picture would take ~20 MB; this takes well under 1 MB.
     */
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

    /**
     * Closes everything but the pinned apps. Without pins: Android's own "clear all" (needs only
     * the DUMP grant), else root. With pins: root closes the others one by one (without root
     * Android's "clear all" can't leave any open, so it closes the pinned ones too).
     */
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

    /** Pins or unpins [card]'s apps; with root and the setting, keeps them in memory (or stops). */
    /**
     * Saves the apps of a split card as an app pair (Settings ▸ App pairs; then on home and the
     * taskbar too). False when one of its apps isn't known (not installed for this user any more).
     */
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
                    // Still pinned for another profile: leave it.
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

    /** Android's App info page of [card]'s app (for the right profile). */
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

    /** A split card back to one app full screen. */
    fun unsplit(card: RecentsCard) {
        val keep = card.tasks.first().taskId
        appScope.launch(Dispatchers.IO) { ShellSplit.exitSplit(context, keep) }
    }

    /**
     * Split view: the first two side by side, a third joining the split where the phone supports
     * three (Razr Fold) or else as a pop-up, a fourth as a pop-up. Runs on after Recents closes.
     */
    fun openTogether(picked: List<RecentsCard>, thirdInSplit: Boolean, threeApp: Boolean = false) {
        if (picked.size < 2) return
        val tasks = picked.map { it.tasks.first() }
        appScope.launch {
            open(picked[0])
            delay(SPLIT_STEP_MS)
            withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, tasks[1].taskId, right = true) }
            if (picked.size >= 3) {
                delay(SPLIT_STEP_MS)
                // Tiling the third pane means asking SystemUI's module to start a task into a third
                // slot, which can crash System UI on some ROMs, so it is off by default: the third app
                // floats instead (reliable everywhere). Only when the user opts in do we try to tile.
                val tileThird = thirdInSplit && settings.value.launch.threeAppNative
                val joined = tileThird && withContext(Dispatchers.IO) {
                    // A real third pane through Folark's module in SystemUI; else Android's own
                    // split command (some phones take a third there), else it floats (below).
                    (threeApp && SplitThree.ready(context) && SplitThree.addThird(context, taskId = tasks[2].taskId)) || run {
                        ShellSplit.moveToSide(context, tasks[2].taskId, right = true)
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

    /**
     * [third] dropped on the split card [pair]: the pair comes back to the front, then [third]
     * joins it as a third pane (Folark's SystemUI module); without the module it floats on top.
     */
    fun addThird(pair: RecentsCard, third: RecentsCard) {
        val task = third.tasks.firstOrNull() ?: return
        appScope.launch {
            open(pair)
            delay(SPLIT_STEP_MS)
            // Same trade-off as [openTogether]: tile the third pane only on opt-in (it can crash
            // System UI on some ROMs); otherwise it floats.
            val joined = settings.value.launch.threeAppNative && withContext(Dispatchers.IO) {
                SplitThree.ready(context) && SplitThree.addThird(context, taskId = task.taskId)
            }
            if (!joined) popUp(third)
        }
    }

    /**
     * [with] dropped on pane [paneIndex] of the multi-app card [target]: the split is rebuilt with
     * that pane's app swapped for [with]. Android has no in-place pane swap, so the split is torn
     * down and reopened from the new trio (a brief flash) — best effort, like [openTogether].
     */
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

    /**
     * [card] long-press-dragged to a screen edge (inner screen, Aero-style): top = full screen; left
     * or right = the app into that split half beside whatever is behind Recents. A floating (pop-up)
     * app dragged to a side is pulled into the split this way. Best effort, like the other split ops.
     */
    fun placeAtEdge(card: RecentsCard, edge: Int) {
        when (edge) {
            EDGE_TOP -> open(card)
            EDGE_BOTTOM -> popUp(card)
            EDGE_LEFT, EDGE_RIGHT -> {
                // Show Folark's picker to choose the partner; it then builds the split from the two
                // real apps (the dragged app on the side it was dropped).
                val pkg = card.tasks.firstOrNull()?.packageName ?: return
                runCatching { context.startActivity(SplitPicker.intent(context, pkg, right = edge == EDGE_RIGHT)) }
            }
        }
    }

    /** Whether split view can work: Android's split command needs the one-time DUMP grant. */
    fun canSplit(): Boolean = ShellSplit.hasDump(context)

    /** A fresh root check (Settings, or Recents opened after root was turned on again). */
    fun recheckRoot() {
        viewModelScope.launch(Dispatchers.IO) { RootShell.available(recheck = true) }
    }

    /**
     * Attached view: [main] and [peek] in split screen, then the divider dragged near the edge so
     * [peek] only peeks in at [side] ("left"/"right"), [peekFraction] of the [screen] width. The
     * drag is a touch made by Folark's accessibility service (or root), as a finger would do it.
     */
    fun openAttached(main: RecentsCard, peek: RecentsCard, side: String, peekFraction: Float, screen: android.graphics.Rect) {
        val mainTask = main.tasks.firstOrNull() ?: return
        val left = side != LauncherSettings.RECENTS_ATTACHED_RIGHT
        appScope.launch {
            open(peek)
            delay(SPLIT_STEP_MS)
            val moved = withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, mainTask.taskId, right = left) }
            if (!moved) return@launch
            // Peek it. By default Folark drags the divider like a finger (Android's normal input path,
            // which it handles safely); only when the user opts into native peek do we ask SystemUI's
            // module to snap — that cold snap can crash System UI on some ROMs, so it is off by default.
            if (settings.value.recents.attachedNative &&
                SplitThree.ready(context) &&
                SplitThree.snap(context, if (left) SplitThree.SNAP_PEEK_LEFT else SplitThree.SNAP_PEEK_RIGHT)
            ) return@launch
            delay(ATTACH_SETTLE_MS)
            val y = screen.exactCenterY()
            val from = screen.exactCenterX()
            val to = if (left) screen.left + screen.width() * peekFraction else screen.right - screen.width() * peekFraction
            DividerDrag.drag(from, y, to, y)
        }
    }

    /**
     * Motorola's native two-app split-select: [card] goes into split and the system's own app
     * picker chooses the other app. Uses Moto's SplitScreenController.startTaskWithSelectRequest.
     */
    fun nativeSplitSelect(card: RecentsCard) {
        val task = card.tasks.firstOrNull()?.taskId ?: return
        val index = if (settings.value.recents.attachedSide == LauncherSettings.RECENTS_ATTACHED_RIGHT) 1 else 0
        appScope.launch {
            open(card)
            delay(SPLIT_STEP_MS)
            SplitThree.splitSelect(context, task, index)
        }
    }

    /**
     * Motorola's native three-app split-select: while [card]'s split is showing, the system shows
     * its picker for the third app (requestEnterThreeAppsSplitSelectByGesture).
     */
    fun nativeThreeAppSelect(card: RecentsCard) {
        appScope.launch {
            open(card)
            delay(SPLIT_STEP_MS)
            SplitThree.selectThird(context, 3)
        }
    }

    /**
     * A split card's two apps as peek view, the one at [side] peeking in. Same reliability trade-off
     * as [openAttached]: the finger-style divider drag by default, SystemUI's native snap only on opt-in.
     */
    fun peekSplit(card: RecentsCard, side: String, screen: android.graphics.Rect, peekFraction: Float) {
        val left = side != LauncherSettings.RECENTS_ATTACHED_RIGHT
        appScope.launch {
            open(card)
            if (settings.value.recents.attachedNative &&
                SplitThree.ready(context) &&
                SplitThree.snap(context, if (left) SplitThree.SNAP_PEEK_LEFT else SplitThree.SNAP_PEEK_RIGHT)
            ) return@launch
            delay(ATTACH_SETTLE_MS)
            val y = screen.exactCenterY()
            val from = screen.exactCenterX()
            val to = if (left) screen.left + screen.width() * peekFraction else screen.right - screen.width() * peekFraction
            DividerDrag.drag(from, y, to, y)
        }
    }

    /**
     * Mute or unmute every app on [card], persistently. The muted set is stored so it survives a
     * restart of Folark and of the app; on root it is enforced with appops PLAY_AUDIO (ignore to
     * mute, allow to unmute), which the system keeps across app restarts and reboots. Whether a
     * given ROM honours the PLAY_AUDIO op varies, so this is best-effort when it isn't honoured.
     */
    fun toggleMute(card: RecentsCard) {
        val pkgs = card.tasks.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        if (pkgs.isEmpty()) return
        appScope.launch {
            val current = settingsRepository.mutedPackages()
            // "Mute" when none of the card's apps are muted yet; otherwise this is an "Unmute".
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
        /** Long side of a decoded screenshot, px (a card is rarely bigger). */
        const val THUMB_MAX_EDGE = 720

        /** Screenshot memory: an eighth of what the app may use, at most 48 MB. */
        fun thumbnailBudget(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()

        const val SPLIT_STEP_MS = 450L
        const val ATTACH_SETTLE_MS = 900L

        /** Package names allowed into a shell command (never anything that could inject). */
        val SAFE_PKG = Regex("[A-Za-z0-9_.]+")
    }
}
