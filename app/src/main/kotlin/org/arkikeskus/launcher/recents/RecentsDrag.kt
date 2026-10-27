package org.arkikeskus.launcher.recents

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Drop on the left half of a card: split view; on the right half: attached view. */
internal const val COMBINE_SPLIT = "split"
internal const val COMBINE_ATTACHED = "attached"
/** Dropped on a split card: joins it as the third app (Settings ▸ Launch ▸ Real three-app split). */
internal const val COMBINE_THIRD = "third"
/** Dropped on one pane of a three-app card: that pane's app is swapped for the held one (rebuilt). */
internal const val COMBINE_REPLACE = "replace"

/** Long-press-dragged to a screen edge (inner screen): place the app there, Aero-style. */
internal const val EDGE_NONE = 0
internal const val EDGE_LEFT = 1
internal const val EDGE_RIGHT = 2
internal const val EDGE_TOP = 3

/**
 * Dragging one Recents card onto another (hold a card, then move it): which card is held, where
 * the finger is (in the screen's coordinates), and which card and which half it is over.
 *
 * Letting go doesn't act at once: the held card's miniature first flies into the card it was
 * dropped on (or back to its own place when dropped on nothing), and only when it has landed does
 * the split / attached view open — see DragLayer, which calls [finish].
 */
@Stable
internal class RecentsDrag {
    var card by mutableStateOf<RecentsCard?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var target by mutableStateOf<Int?>(null)
        private set
    var mode by mutableStateOf(COMBINE_SPLIT)
        private set

    /** Which pane of a three-app card the finger is over (0-based), -1 when not over one. */
    var paneIndex by mutableStateOf(-1)
        private set

    /** The pane that was under the finger at the drop, captured by [finish] for [COMBINE_REPLACE]. */
    var droppedPane = -1
        private set

    /** The screen edge the finger is over (inner screen only), one of EDGE_*; EDGE_NONE otherwise. */
    var edge by mutableStateOf(EDGE_NONE)
        private set

    /** The edge and card at the drop, captured by [finish] for an edge placement (EDGE_NONE if none). */
    var droppedEdge = EDGE_NONE
        private set
    var edgeCard: RecentsCard? = null
        private set

    /** Where the held card was when it was picked up (where it flies back to). */
    var origin by mutableStateOf<Rect?>(null)
        private set

    /** Let go: the miniature is flying to [landing] (the target card, or [origin]). */
    var settling by mutableStateOf(false)
        private set
    var landing by mutableStateOf<Rect?>(null)
        private set

    /** The drop to carry out once the miniature has landed (null: it flies home, nothing happens). */
    private var pending: Pair<RecentsCard, String>? = null

    /** Where each card is on screen, kept up to date as they are laid out. */
    val bounds = HashMap<Int, Rect>()
    var cards: List<RecentsCard> = emptyList()

    /** Split cards (two apps) take a drop as their third app. */
    var allowThird = false

    /** Three-app cards take a drop on one of their panes as a swap of that app. */
    var allowReplace = false

    /** Long-press-drag to a screen edge places the app there (inner screen only). */
    var allowEdge = false
    var screenW = 0f
    var screenH = 0f
    private var pendingEdge: Pair<RecentsCard, Int>? = null

    fun start(card: RecentsCard, at: Offset) {
        this.card = card
        origin = bounds[card.key]
        pointer = at
        settling = false
        landing = null
        pending = null
        update()
    }

    fun move(by: Offset) {
        if (settling) return
        pointer += by
        update()
    }

    private fun update() {
        val held = card ?: return
        val hit = cards.firstOrNull {
            it.key != held.key &&
                (!it.isSplit || (allowThird && it.tasks.size == 2) || (allowReplace && it.tasks.size >= 3)) &&
                bounds[it.key]?.contains(pointer) == true
        }
        target = hit?.key
        paneIndex = -1
        edge = EDGE_NONE
        if (hit != null) {
            val r = bounds.getValue(hit.key)
            mode = when {
                allowReplace && hit.tasks.size >= 3 -> {
                    val n = hit.tasks.size
                    paneIndex = (((pointer.x - r.left) / (r.width / n)).toInt()).coerceIn(0, n - 1)
                    COMBINE_REPLACE
                }
                hit.isSplit -> COMBINE_THIRD
                pointer.x < r.center.x -> COMBINE_SPLIT
                else -> COMBINE_ATTACHED
            }
        } else if (allowEdge && screenW > 0f && screenH > 0f) {
            // Not over a card: which screen edge the finger is near (Aero-style placement).
            edge = when {
                // Generous zones (not just the corners): the top band is full screen, otherwise the
                // left or right 40% snaps that half, with a dead zone in the middle for free movement.
                pointer.y < screenH * 0.16f -> EDGE_TOP
                pointer.x < screenW * 0.40f -> EDGE_LEFT
                pointer.x > screenW * 0.60f -> EDGE_RIGHT
                else -> EDGE_NONE
            }
        }
    }

    /** Let go of the card: it will land on the card under the finger, or fly home. */
    fun release() {
        if (card == null || settling) return
        val held = card
        val onto = cards.firstOrNull { it.key == target }
        pending = onto?.let { it to mode }
        // No card under the finger but at a screen edge: an Aero edge placement instead.
        pendingEdge = if (onto == null && allowEdge && edge != EDGE_NONE && held != null) held to edge else null
        landing = onto?.let { bounds[it.key] } ?: origin
        target = null
        settling = true
    }

    /** The miniature has landed: the drop to perform (held, onto, how), or null; then all is cleared. */
    fun finish(): Triple<RecentsCard, RecentsCard, String>? {
        val held = card
        val drop = pending
        droppedPane = paneIndex
        val e = pendingEdge
        droppedEdge = e?.second ?: EDGE_NONE
        edgeCard = e?.first
        cancel()
        return if (held != null && drop != null) Triple(held, drop.first, drop.second) else null
    }

    fun cancel() {
        card = null
        target = null
        paneIndex = -1
        edge = EDGE_NONE
        origin = null
        landing = null
        settling = false
        pending = null
        pendingEdge = null
    }
}
