package org.arkikeskus.launcher.recents

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.OpenTask
import org.arkikeskus.launcher.data.SplitApp
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RecentsAttachedSide

/** Waits between the steps of making a split, and for a split to settle before peeking. */
internal const val SPLIT_STEP_MS = 200L
private const val ATTACH_SETTLE_MS = 350L

/** Pane [i] of this card as the split coordinator takes it. */
internal fun RecentsCard.splitApp(i: Int) = SplitApp(tasks[i].packageName, apps.getOrNull(i), tasks[i].taskId)

/**
 * The split views Recents makes from its cards: two or three apps side by side, a third dropped
 * on a split, a pane swapped, an app placed at a screen edge, peek view, and Motorola's own
 * pickers (split out of RecentsViewModel). Opening a single card stays with the ViewModel ([Host]).
 */
internal class RecentsSplitActions(
    private val context: Context,
    private val splitCoordinator: SplitCoordinator,
    private val appScope: CoroutineScope,
    private val settings: StateFlow<LauncherSettings>,
    private val host: Host,
) {
    /** What the split actions need back from Recents: opening cards as they were. */
    interface Host {
        fun open(card: RecentsCard)
        fun popUp(card: RecentsCard)
        suspend fun openNow(card: RecentsCard)
        fun openSingleTask(task: OpenTask, app: AppItem?)
    }

    private fun open(card: RecentsCard) = host.open(card)
    private fun popUp(card: RecentsCard) = host.popUp(card)
    private suspend fun openNow(card: RecentsCard) = host.openNow(card)
    private fun openSingleTask(task: OpenTask, app: AppItem?) = host.openSingleTask(task, app)

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
            // Paused by the crash guard: three panes can't be made, and the person is told why
            // instead of finding the third app floating. Stop here — the two-app split stays,
            // and the third app is not floated over it.
            if (SplitThree.paused(context)) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, org.arkikeskus.launcher.hook.R.string.split_native_paused, android.widget.Toast.LENGTH_LONG).show()
                }
                return@launch
            }
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

    /**
     * Attached (peek) view: clears stale split state first, pairs [main] and [peek], and snaps to
     * Motorola's peek target (or drags the divider to a safe non-dismiss peek fraction).
     */
    fun openAttached(main: RecentsCard, peek: RecentsCard, side: RecentsAttachedSide, peekFraction: Float, screen: android.graphics.Rect) {
        val mainTask = main.tasks.firstOrNull() ?: return
        val peekTask = peek.tasks.firstOrNull() ?: return
        val left = side != RecentsAttachedSide.RIGHT
        appScope.launch {
            // The peeking app on its side, the main app on the other.
            val formed = splitCoordinator.pair(
                first = SplitApp(peekTask.packageName, peek.apps.firstOrNull(), peekTask.taskId),
                second = SplitApp(mainTask.packageName, main.apps.firstOrNull(), mainTask.taskId),
                firstOnRight = !left,
            )
            if (!formed) return@launch
            peekInto(left, screen, peekFraction)
        }
    }

    fun nativeSplitSelect(card: RecentsCard) {
        val task = card.tasks.firstOrNull()?.taskId ?: return
        val index = if (settings.value.recents.attachedSide == RecentsAttachedSide.RIGHT) 1 else 0
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

    fun peekSplit(card: RecentsCard, side: RecentsAttachedSide, screen: android.graphics.Rect, peekFraction: Float) {
        val left = side != RecentsAttachedSide.RIGHT
        appScope.launch {
            openNow(card)
            // A three-app split: Motorola's three-app peek (the middle app big, the others at the edges).
            if (card.tasks.size >= 3) {
                if (SplitThree.ready(context) && SplitThree.snap(context, SplitThree.SNAP_THREE_PEEK)) return@launch
            }
            peekInto(left, screen, peekFraction)
        }
    }

    /**
     * Turns the split showing now into peek view, the app on the [left] side peeking in, the way
     * Settings ▸ Recents ▸ Native peek says: on, System UI's own peek snap (Folark's module) first;
     * off (the default), the divider dragged like a finger, which some ROMs handle far more
     * reliably. Either way the other route is the fallback when the first one cannot be taken
     * (no module; no root and no accessibility service to drag with).
     */
    private suspend fun peekInto(left: Boolean, screen: android.graphics.Rect, peekFraction: Float) {
        val native = settings.value.recents.attachedNative
        if (native && splitCoordinator.snapPeek(peekOnLeft = left)) return
        delay(ATTACH_SETTLE_MS)
        val safeFraction = peekFraction.coerceIn(0.18f, 0.30f)
        val y = screen.exactCenterY()
        val from = screen.exactCenterX()
        val to = if (left) screen.left + screen.width() * safeFraction else screen.right - screen.width() * safeFraction
        if (DividerDrag.drag(from, y, to, y)) return
        if (!native) splitCoordinator.snapPeek(peekOnLeft = left)
    }
}
