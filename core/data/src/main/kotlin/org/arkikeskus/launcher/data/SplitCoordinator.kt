package org.arkikeskus.launcher.data

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import javax.inject.Singleton

/** An app to place in a split: its running task when known, else the app to open. */
data class SplitApp(val packageName: String, val app: AppItem? = null, val taskId: Int? = null) {
    constructor(app: AppItem) : this(app.packageName, app, null)
}

/**
 * Every way Folark puts apps side by side — the taskbar, Recents, the split picker — in one place,
 * so they all take the same steps: the Split engine setting (Settings ▸ Recents ▸ Split screen),
 * the checks that keep WM Shell from throwing (see [ShellSplit]), one read of what is open per step
 * instead of one per question, and a last check that the split really formed.
 *
 * The functions suspend until the split is up (or has failed) and say whether it worked; callers
 * decide what to do instead (open the app alone, a pop-up window…).
 */
@Singleton
class SplitCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** The way a split can be made now (see [SplitRoute]); also gets System UI's split events going. */
    private suspend fun route(): SplitRoute = withContext(Dispatchers.IO) {
        val engine = runCatching { settingsRepository.settings(ScreenType.INNER).first().recents.splitEngine }
            .getOrDefault(LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING)
        SplitRoute.resolve(context, engine).also { connectEvents() }
    }

    @Volatile private var eventsAskedFor = 0L

    /**
     * Once per System UI start: asks the module for its split events when none have arrived yet
     * (Folark started after System UI). Doesn't wait for them: this split still reads the window list.
     */
    private fun connectEvents() {
        val readyAt = SplitThree.readyAt(context)
        if (SplitEvents.live || readyAt == 0L || eventsAskedFor == readyAt || !SplitThree.ready(context)) return
        eventsAskedFor = readyAt
        scope.launch { SplitThree.requestEvents(context) }
    }

    /** Split screen can be made at all (the grant, root, or the module). */
    suspend fun canSplit(): Boolean = route().any

    // ------------------------------------------------------------------ undo

    /** A split just formed and can be undone: [keep] is the app that stays (full screen). */
    data class UndoOffer(val keep: SplitApp, val at: Long = System.currentTimeMillis())

    private val _undoOffer = MutableStateFlow<UndoOffer?>(null)

    /** Shown by the taskbar as "Undo split" for a few seconds (Settings ▸ Recents ▸ Split screen). */
    val undoOffer: StateFlow<UndoOffer?> = _undoOffer.asStateFlow()

    /** A split was just made with [keep] the app that was there first; offered as undo, if switched on. */
    suspend fun offerUndo(keep: SplitApp?) {
        if (keep == null) return
        val on = runCatching { settingsRepository.settings(ScreenType.INNER).first().recents.splitUndo }.getOrDefault(true)
        if (on) _undoOffer.value = UndoOffer(keep)
    }

    fun dismissUndo() {
        _undoOffer.value = null
    }

    /** Undo split: closes the split that just formed, the app that was there first staying. */
    suspend fun undo(): Boolean {
        val offer = _undoOffer.value ?: return false
        _undoOffer.value = null
        val route = route()
        val id = offer.keep.taskId ?: withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, offer.keep.packageName, route.root) }
        return exitFirst(id ?: -1, route.root)
    }

    // ------------------------------------------------------------------ two apps

    /**
     * Puts [first] on [firstOnRight]'s side and [second] on the other, as one split; with [peek]
     * it then switches to Android's peek view, [second] big and [first] peeking in from its edge.
     * Any older split is closed first (keeping [second] in front). Each app comes forward by its
     * running task, or is opened.
     */
    suspend fun pair(
        first: SplitApp,
        second: SplitApp,
        firstOnRight: Boolean,
        peek: Boolean = false,
        /** Which app stays on "Undo split" (null: no undo offered). */
        keepOnUndo: SplitApp? = second,
    ): Boolean {
        val route = route()
        if (!route.any) return false
        val formed = (route.native && natively(first, second, firstOnRight, route.root)) ||
            (route.commands && byCommands(first, second, firstOnRight, route.root))
        if (formed) {
            Haptics.play(context, Haptics.Event.SPLIT)
            SplitHistory.record(context, first.packageName, second.packageName)
            offerUndo(keepOnUndo)
        }
        if (formed && peek) {
            delay(PEEK_AFTER_MS)
            snapPeek(peekOnLeft = !firstOnRight)
        }
        return formed
    }

    /**
     * The taskbar's drag to an edge: [app] on [appOnRight]'s side, beside the app in front. On the
     * home screen (nothing in front) it goes beside [partnerIfHome] instead, or opens alone.
     */
    suspend fun pairWithFront(app: AppItem, appOnRight: Boolean, partnerIfHome: AppItem?, peek: Boolean = false): Boolean {
        val route = route()
        if (!route.any) return false
        val front = withContext(Dispatchers.IO) { ShellSplit.recent(context, route.root) }?.frontApp()
        val partner = when {
            front != null && !front.isApp(app.packageName) -> SplitApp(front.packageName, null, front.id)
            partnerIfHome != null -> SplitApp(partnerIfHome)
            else -> return false
        }
        return pair(SplitApp(app), partner, firstOnRight = appOnRight, peek = peek)
    }

    /**
     * Three apps: [first] left and [second] right as a split, then [third] as Android's third pane
     * through the module (when [native]). Returns how far it got: 0 nothing, 2 the two-app split,
     * 3 all three; on 2 callers float the third.
     */
    suspend fun three(first: SplitApp, second: SplitApp, third: SplitApp, native: Boolean, even: Boolean = false): Int {
        if (!pair(first, second, firstOnRight = false, keepOnUndo = first)) return 0
        return if (addThird(third, native = native, orSideStage = false, even = even)) 3 else 2
    }

    /**
     * [second] was just opened in the picker's half beside [first]; when it didn't land there
     * (an app of another profile may open full screen instead), the split is made again around it.
     */
    suspend fun ensurePaired(first: SplitApp, second: SplitApp, firstOnRight: Boolean): Boolean {
        val route = route()
        delay(LANDED_CHECK_MS)
        val read = withContext(Dispatchers.IO) { ShellSplit.snapshot(context, route.root) }
        val t1 = first.taskId ?: read?.taskOf(first.packageName)?.id
        val t2 = read?.taskOf(second.packageName)?.id
        if (read != null && t1 != null && t2 != null && read.isStaged(t1) && read.isStaged(t2)) {
            Haptics.play(context, Haptics.Event.SPLIT)
            SplitHistory.record(context, first.packageName, second.packageName)
            offerUndo(first.copy(taskId = t1))
            return true
        }
        return pair(first.copy(taskId = t1), second.copy(taskId = t2), firstOnRight, keepOnUndo = first)
    }

    /** Brings back a split from Recents: as it was when it is still staged, else [a] left and [b] right. */
    suspend fun restore(a: SplitApp, b: SplitApp): Boolean {
        val route = route()
        val read = withContext(Dispatchers.IO) { ShellSplit.recent(context, route.root) }
        val ta = a.taskId ?: read?.taskOf(a.packageName)?.id
        val tb = b.taskId ?: read?.taskOf(b.packageName)?.id
        if (read != null && ta != null && tb != null && read.isStaged(ta) && read.isStaged(tb)) {
            // Still a split behind Recents: bringing one half forward brings the whole split back.
            return (bringToFront(a.copy(taskId = ta), route.root) != null)
                .also { if (it) Haptics.play(context, Haptics.Event.SPLIT) }
        }
        return pair(b.copy(taskId = tb), a.copy(taskId = ta), firstOnRight = true, keepOnUndo = null)
    }

    /**
     * A third app into the split showing now: Android's own three-way split through the module
     * when [native], else (with [orSideStage]) the third app moved into a side stage, as far as WM
     * Shell allows. False when it didn't join; callers then float it.
     */
    suspend fun addThird(third: SplitApp, native: Boolean, orSideStage: Boolean = true, even: Boolean = false, peek: Boolean = false): Boolean {
        val route = route()
        if (native && SplitThree.ready(context)) {
            val id = third.taskId ?: withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, third.packageName, route.root) }
            val app = third.app
            // What is staged before: a new task staged after the request is the third (E27).
            val stagedBefore = SplitEvents.stagedNow()
            val ok = when {
                id != null -> SplitThree.addThird(context, taskId = id)
                app != null -> SplitThree.addThird(context, component = app.componentName, user = app.user)
                else -> false
            }
            // System UI can say yes and still not make a third pane (Motorola's split accepts the
            // call on some builds): only a third app really in a split stage counts, else the
            // caller floats it over the split.
            if (ok && thirdLanded(third.copy(taskId = id), route.root, stagedBefore)) {
                Haptics.play(context, Haptics.Event.SPLIT)
                // Motorola starts three apps as its carousel (one a 10 % strip): a third each instead.
                // [even]: asked for (the split drawer's 33/33/33), whatever the setting.
                // [peek]: Motorola's three-app peek instead (the middle app big, the others at the edges).
                val thirds = !peek && (even || runCatching { settingsRepository.settings(ScreenType.INNER).first().recents.threeEven }.getOrDefault(true))
                if (peek) SplitThree.snap(context, SplitThree.SNAP_THREE_PEEK)
                else if (thirds) SplitThree.snap(context, SplitThree.SNAP_THREE_EVEN)
                return true
            }
        }
        if (!orSideStage || !route.commands) return false
        val id = (third.taskId ?: withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, third.packageName, route.root) })
            ?: return false
        val moved = withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = true, root = route.root) }
        return (moved && awaitInSplit(listOf(id), route.root)).also { if (it) Haptics.play(context, Haptics.Event.SPLIT) }
    }

    /**
     * The split showing now to the divider stop [snap] (SplitThree.SNAP_*: a third, half, two
     * thirds, peek), meant for [forTask] as if it sat left / on top (-1: as the split is). False
     * without the module, or when the divider has no such stop on this screen.
     */
    suspend fun snapTo(snap: Int, forTask: Int = -1): Boolean =
        SplitThree.ready(context) && SplitThree.snap(context, snap, forTask)

    // ------------------------------------------------------------------ a notification beside an app

    /**
     * Opens [intent] (the tap action of a notification from [packageName], whose profile is
     * [userId]) in split screen beside the app used last, showing or just behind Home: in one step
     * through the module, else the notification opened and the other app moved beside it. False
     * when there is no other app or no way to make a split; callers then just open it.
     */
    suspend fun openBeside(intent: PendingIntent, packageName: String, userId: Int): Boolean {
        val route = route()
        if (!route.any) return false
        val read = withContext(Dispatchers.IO) { ShellSplit.recent(context, route.root) } ?: return false
        val other = read.lastApp(except = packageName) ?: return false
        val partner = SplitApp(other.packageName, null, other.id)
        val formed = (route.native && intentNatively(intent, packageName, userId, partner, route.root)) ||
            (route.commands && intentByCommands(intent, packageName, partner, route.root))
        if (formed) {
            Haptics.play(context, Haptics.Event.SPLIT)
            SplitHistory.record(context, packageName, other.packageName)
            offerUndo(partner)
        }
        return formed
    }

    private suspend fun intentNatively(intent: PendingIntent, packageName: String, userId: Int, partner: SplitApp, root: Boolean): Boolean {
        val id = partner.taskId ?: return false
        exitFirst(id, root)
        SplitEvents.forget(listOf(id))
        if (!SplitThree.startIntentAndTask(context, intent, userId, id, intentOnRight = true)) return false
        return awaitBeside(packageName, id, root)
    }

    /** The notification opened first (while Folark is in front), then the other app over it, moved left. */
    private suspend fun intentByCommands(intent: PendingIntent, packageName: String, partner: SplitApp, root: Boolean): Boolean {
        exitFirst(partner.taskId ?: -1, root)
        if (!withContext(Dispatchers.Main) { PendingIntents.sendFromForeground(context, intent) }) return false
        val (t1, _) = awaitTask(packageName, root) ?: return false
        delay(PARTNER_SETTLE_MS)
        val (t2, read) = bringToFront(partner, root) ?: return false
        delay(FRONT_SETTLE_MS)
        SplitEvents.forget(listOf(t1, t2))
        val moved = withContext(Dispatchers.IO) {
            ShellSplit.moveToSide(context, t2, right = false, root = root, read = read.fresh(root))
        }
        return moved && awaitInSplit(listOf(t1, t2), root)
    }

    /** Waits until an app of [packageName] and the task [partnerTask] are both in split stages. */
    private suspend fun awaitBeside(packageName: String, partnerTask: Int, root: Boolean): Boolean = withContext(Dispatchers.IO) {
        // System UI reports split stages itself: find the app's task once, then wait for the
        // report instead of reading (and parsing) the whole window list every 150 ms.
        if (SplitEvents.live) {
            val id = awaitTask(packageName, root)?.first
            if (id != null) {
                if (SplitEvents.awaitStaged(listOf(id, partnerTask), EVENT_WAIT_MS)) return@withContext true
                // No word in time: one read decides.
                val read = ShellSplit.snapshot(context, root)
                return@withContext read != null && read.isStaged(id) && read.isStaged(partnerTask)
            }
        }
        repeat(VERIFY_TRIES * 2) {
            delay(VERIFY_STEP_MS)
            val read = ShellSplit.snapshot(context, root) ?: return@repeat
            val id = read.taskOf(packageName)?.id
            if (id != null && read.isStaged(id) && read.isStaged(partnerTask)) return@withContext true
        }
        false
    }

    // ------------------------------------------------------------------ "Open with …"

    /** An app was just opened that is often paired with [partner]: offered as "Open with …". */
    data class Suggestion(val app: AppItem, val partner: AppItem, val at: Long = System.currentTimeMillis())

    private val _suggestion = MutableStateFlow<Suggestion?>(null)

    /** Shown by the taskbar as a pill for a few seconds (Settings ▸ Recents ▸ Split screen, off by default). */
    val suggestion: StateFlow<Suggestion?> = _suggestion.asStateFlow()

    /**
     * [app] was just opened from Folark (home, the drawer, the taskbar). With "Open with …" on and
     * the inner screen in use, its usual partner ([SplitHistory.suggestion]) is offered once the app
     * is up, unless the two are side by side already.
     */
    fun opened(app: AppItem) {
        scope.launch {
            val on = runCatching { settingsRepository.settings(ScreenType.INNER).first().recents.openWith }.getOrDefault(false)
            if (!on || !Screens.innerShowing(context)) return@launch
            val partnerPackage = SplitHistory.suggestion(context, app.packageName) ?: return@launch
            val apps = appRepository.apps.first()
            val partner = apps.firstOrNull { it.packageName == partnerPackage && it.user == app.user }
                ?: apps.firstOrNull { it.packageName == partnerPackage }
                ?: return@launch
            delay(SUGGEST_AFTER_MS)
            val route = route()
            if (!route.any) return@launch
            val read = withContext(Dispatchers.IO) { ShellSplit.snapshot(context, route.root) }
            if (read != null && read.splitShowing.any { it.isApp(partnerPackage) }) return@launch
            // The app has to still be the one in front (it wasn't closed again at once).
            if (read != null && read.frontApp()?.isApp(app.packageName) == false) return@launch
            _suggestion.value = Suggestion(app, partner)
        }
    }

    /** "Open with …" tapped: the app on the left, its partner beside it. */
    suspend fun acceptSuggestion(): Boolean {
        val s = _suggestion.value ?: return false
        _suggestion.value = null
        SplitHistory.accepted(context, s.app.packageName, s.partner.packageName)
        return pair(SplitApp(s.app), SplitApp(s.partner), firstOnRight = false, keepOnUndo = SplitApp(s.app))
    }

    /** The pill went away; [passedOver]: by itself, untapped (three of those and that pair isn't offered again). */
    fun dismissSuggestion(passedOver: Boolean) {
        val s = _suggestion.value ?: return
        _suggestion.value = null
        if (passedOver) SplitHistory.ignored(context, s.app.packageName, s.partner.packageName)
    }

    /** Android's peek view for the split showing now: the app on the [peekOnLeft] side peeks in. */
    suspend fun snapPeek(peekOnLeft: Boolean): Boolean =
        SplitThree.ready(context) && SplitThree.snap(context, if (peekOnLeft) SplitThree.SNAP_PEEK_LEFT else SplitThree.SNAP_PEEK_RIGHT)

    /** Closes the split, keeping [keepTaskId] (-1: none) in front. */
    suspend fun exit(keepTaskId: Int): Boolean {
        val route = route()
        return exitFirst(keepTaskId, route.root)
    }

    // ------------------------------------------------------------------ the split picker

    /**
     * Opens the split picker beside [first]: [first] on [firstOnRight]'s side, the picker in the
     * other half. Whichever comes up last is moved into its half (WM Shell's own way). Without a
     * way to run the commands the picker opens full screen, and builds the split once an app is
     * picked ([pair]).
     */
    suspend fun openPicker(first: SplitApp, firstOnRight: Boolean, peek: Boolean = false, three: Boolean = false) {
        val noAnim = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        fun picker(app: SplitApp, selfDock: Boolean) = SplitPicker.intent(
            context, app.packageName, right = firstOnRight, selfDockRight = selfDock, peek = peek,
            firstKey = app.app?.key, firstTaskId = app.taskId, three = three,
        )
        fun picker(selfDock: Boolean) = picker(first, selfDock)
        val route = route()
        // With Folark's module: the picker opens hidden and docks itself beside the first app.
        if (!three && route.native && pickerNatively(first, firstOnRight, ::picker, route.root)) return
        // Three apps don't fit a half: that picker opens full screen and builds the split after.
        if (three || !route.commands) {
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(false), noAnim) } }
            return
        }
        exitFirst(first.taskId ?: -1, route.root)
        if (firstOnRight) {
            // The picker first (it ends up on the left), then the first app, moved to the right.
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(false), noAnim) } }
            delay(PICKER_START_MS)
            val (id, read) = bringToFront(first, route.root) ?: return
            delay(FRONT_SETTLE_MS)
            withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = true, root = route.root, read = read.fresh(route.root)) }
        } else {
            // The first app first (it ends up on the left), then the picker, which moves itself right.
            bringToFront(first, route.root)
            delay(FRONT_SETTLE_MS)
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(true), noAnim) } }
        }
    }

    /**
     * With the module: the first app running (opened if need be), then the picker started on its
     * own, hidden until it is docked: it asks [dockPicker] to put it beside the first app with the
     * module's `startTasks`. (`startIntentAndTask`, one step, throws inside WM Shell on Android 17
     * builds: StageCoordinator.setSideStagePosition reads a third stage.) False when the first app
     * can't be found or opened (the caller then falls back to another way).
     */
    private suspend fun pickerNatively(
        first: SplitApp, firstOnRight: Boolean, picker: (SplitApp, Boolean) -> android.content.Intent, root: Boolean,
    ): Boolean {
        val (id, _) = running(first, root) ?: open(first, root) ?: return false
        exitFirst(id, root)
        val noAnim = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        return withContext(Dispatchers.Main) {
            runCatching { context.startActivity(picker(first.copy(taskId = id), true), noAnim) }.isSuccess
        }
    }

    /**
     * The picker (task [pickerTaskId]) docks itself beside [first], [first] on [firstOnRight]'s side:
     * through the module's `startTasks` when it runs, else WM Shell's commands (those can only move
     * the picker into the right half).
     */
    suspend fun dockPicker(pickerTaskId: Int, first: SplitApp? = null, firstOnRight: Boolean = false): Boolean {
        val route = route()
        if (route.native && first != null) {
            val firstId = first.taskId ?: withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, first.packageName, route.root) }
            if (firstId != null && firstId != pickerTaskId) {
                val position = if (firstOnRight) SplitThree.SPLIT_POSITION_RIGHT else SplitThree.SPLIT_POSITION_LEFT
                SplitEvents.forget(listOf(firstId, pickerTaskId))
                if (SplitThree.startSplitTasks(context, firstId, pickerTaskId, position) &&
                    awaitInSplit(listOf(firstId, pickerTaskId), route.root)
                ) return true
            }
        }
        if (!route.commands) return false
        if (firstOnRight) {
            // The commands only move a task into the right half: the first app comes over the
            // picker and moves right, the picker filling the left (as openPicker does without the module).
            val (id, read) = bringToFront(first ?: return false, route.root) ?: return false
            delay(FRONT_SETTLE_MS)
            return withContext(Dispatchers.IO) {
                ShellSplit.moveToSide(context, id, right = true, root = route.root, read = read.fresh(route.root))
            }
        }
        return withContext(Dispatchers.IO) {
            repeat(DOCK_TRIES) {
                if (ShellSplit.moveToSide(context, pickerTaskId, right = true, root = route.root)) return@withContext true
                delay(DOCK_RETRY_MS)
            }
            false
        }
    }

    /** Left the picker without a pick: closes the half-made split, keeping the first app. */
    suspend fun cancelPicker(first: SplitApp) {
        val route = route()
        if (!route.commands) return
        val id = first.taskId ?: withContext(Dispatchers.IO) { ShellSplit.taskIdOf(context, first.packageName, route.root) } ?: -1
        exitFirst(id, route.root)
    }

    // ------------------------------------------------------------------ the two engines

    /** The module's `startTasks`: both apps running first, then one call puts them side by side. */
    private suspend fun natively(first: SplitApp, second: SplitApp, firstOnRight: Boolean, root: Boolean): Boolean {
        exitFirst(second.taskId ?: -1, root)
        val (t2, _) = running(second, root) ?: open(second, root) ?: return false
        val (t1, _) = running(first, root) ?: open(first, root) ?: return false
        val position = if (firstOnRight) SplitThree.SPLIT_POSITION_RIGHT else SplitThree.SPLIT_POSITION_LEFT
        SplitEvents.forget(listOf(t1, t2))
        if (!SplitThree.startSplitTasks(context, t1, t2, position)) return false
        return awaitInSplit(listOf(t1, t2), root)
    }

    /** WM Shell's commands, the taskbar's proven order: [second] in front, then [first] over it, moved to its side. */
    private suspend fun byCommands(first: SplitApp, second: SplitApp, firstOnRight: Boolean, root: Boolean): Boolean {
        exitFirst(second.taskId ?: -1, root)
        val (t2, _) = bringToFront(second, root) ?: return false
        delay(PARTNER_SETTLE_MS)
        val (t1, read) = bringToFront(first, root) ?: return false
        delay(FRONT_SETTLE_MS)
        SplitEvents.forget(listOf(t1, t2))
        val moved = withContext(Dispatchers.IO) {
            ShellSplit.moveToSide(context, t1, right = firstOnRight, root = root, read = read.fresh(root))
        }
        return moved && awaitInSplit(listOf(t1, t2), root)
    }

    // ------------------------------------------------------------------ steps

    /** A read of what is open, with the time it was taken (a read a moment old is reused). */
    private class Read(val snapshot: TaskSnapshot, val at: Long = System.currentTimeMillis())

    /** [this] if it is recent enough to act on, else a new read (null when that fails). */
    private fun Read.fresh(root: Boolean): TaskSnapshot? =
        if (System.currentTimeMillis() - at <= FRESH_MS) snapshot else ShellSplit.snapshot(context, root)

    /** [app]'s running task (its known one, or the front-most of its package) and the read it was found in. */
    private suspend fun running(app: SplitApp, root: Boolean): Pair<Int, Read>? = withContext(Dispatchers.IO) {
        val read = ShellSplit.snapshot(context, root) ?: return@withContext null
        val id = app.taskId?.takeIf { read.isRunning(it) } ?: read.taskOf(app.packageName)?.id ?: return@withContext null
        id to Read(read)
    }

    /**
     * Brings [app] to the front: its running task if there is one, else it opens the app. Returns
     * the task once it is running, with the read that saw it (null when it never showed up).
     */
    private suspend fun bringToFront(app: SplitApp, root: Boolean): Pair<Int, Read>? {
        val found = running(app, root)
        val moved = found != null && withContext(Dispatchers.Main) {
            runCatching { context.getSystemService(ActivityManager::class.java).moveTaskToFront(found.first, 0) }.isSuccess.also { ShellSplit.forget() }
        }
        return if (moved) found else open(app, root)
    }

    /** Opens [app] and waits for its task (null when it can't be opened or never showed up). */
    private suspend fun open(app: SplitApp, root: Boolean): Pair<Int, Read>? {
        val item = app.app ?: appRepository.apps.first().firstOrNull { it.packageName == app.packageName } ?: return null
        // No launch click: the split's own haptic follows once the two are side by side.
        val launched = withContext(Dispatchers.Main) { appRepository.launch(item, haptic = false) }.isSuccess.also { ShellSplit.forget() }
        return if (launched) awaitTask(app.packageName, root) else null
    }

    /** Waits for [packageName] to have a running task (it was just opened). */
    private suspend fun awaitTask(packageName: String, root: Boolean): Pair<Int, Read>? = withContext(Dispatchers.IO) {
        repeat(FIND_TRIES) {
            val read = ShellSplit.snapshot(context, root)
            val id = read?.taskOf(packageName)?.id
            if (read != null && id != null) return@withContext id to Read(read)
            delay(FIND_RETRY_MS)
        }
        null
    }

    /** [third] (its task if known, else its newest task once it is running) sits in a split stage. */
    private suspend fun thirdLanded(third: SplitApp, root: Boolean, stagedBefore: Set<Int> = emptySet()): Boolean {
        // Started from its intent (no task id yet): System UI's split events say which task joined,
        // without reading the window list again and again (E27).
        if (third.taskId == null && SplitEvents.live) {
            if (SplitEvents.awaitNewStaged(stagedBefore, EVENT_WAIT_MS * 2) != null) return true
        }
        val id = third.taskId ?: awaitTask(third.packageName, root)?.first ?: return false
        return awaitInSplit(listOf(id), root)
    }

    /**
     * Waits until every task of [ids] is in a split stage (the transition takes a moment): told by
     * System UI's split events when they come, else by reading the window list a few times.
     */
    private suspend fun awaitInSplit(ids: List<Int>, root: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (SplitEvents.live) {
            if (SplitEvents.awaitStaged(ids, EVENT_WAIT_MS)) return@withContext true
            // No word in time: one read decides.
            return@withContext ShellSplit.allStagedTaskIds(context, root).containsAll(ids)
        }
        repeat(VERIFY_TRIES) {
            delay(VERIFY_STEP_MS)
            val staged = ShellSplit.allStagedTaskIds(context, root)
            if (ids.all { it in staged }) return@withContext true
        }
        false
    }

    /**
     * Closes any split before a new one is made, keeping [keep] (-1: none) in front, and waits until
     * WM Shell has let its tasks go: told by System UI's split events when they come, else by
     * reading the window list ([ShellSplit.exitSplit]).
     */
    private suspend fun exitFirst(keep: Int, root: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!SplitEvents.live) return@withContext ShellSplit.exitSplit(context, keep, root)
        val read = ShellSplit.snapshot(context, root) ?: return@withContext false
        val ids = read.stagedIds.toList()
        if (ids.isEmpty()) return@withContext true
        SplitEvents.forget(ids)
        if (!ShellSplit.exitSplit(context, keep, root, read = read, wait = false)) return@withContext false
        SplitEvents.awaitUnstaged(ids, EVENT_WAIT_MS) || ShellSplit.snapshot(context, root)?.stagedIds.isNullOrEmpty()
    }

    private companion object {
        const val PICKER_REQUEST = 0x5911
        const val PER_USER_RANGE = 100_000
        /** How long to wait for System UI to report a split change before reading the window list. */
        const val EVENT_WAIT_MS = 900L
        /** A read this old is still good enough to check a task before moving it. */
        const val FRESH_MS = 400L
        const val PICKER_START_MS = 150L
        const val PARTNER_SETTLE_MS = 300L
        const val FRONT_SETTLE_MS = 200L
        const val PEEK_AFTER_MS = 450L
        /** How long an app opened in the picker's half takes to be placed there. */
        const val LANDED_CHECK_MS = 700L
        const val FIND_TRIES = 10
        const val FIND_RETRY_MS = 80L
        // Without split events: a full window list read per step, so fewer, wider steps (E25).
        const val VERIFY_TRIES = 5
        const val VERIFY_STEP_MS = 200L
        const val DOCK_TRIES = 3
        const val DOCK_RETRY_MS = 150L
        /** "Open with …" waits for the app to be up first. */
        const val SUGGEST_AFTER_MS = 900L
    }
}
