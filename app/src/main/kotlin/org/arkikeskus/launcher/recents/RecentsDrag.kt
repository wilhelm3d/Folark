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
            it.key != held.key && (!it.isSplit || (allowThird && it.tasks.size == 2)) && bounds[it.key]?.contains(pointer) == true
        }
        target = hit?.key
        if (hit != null) mode = when {
            hit.isSplit -> COMBINE_THIRD
            pointer.x < bounds.getValue(hit.key).center.x -> COMBINE_SPLIT
            else -> COMBINE_ATTACHED
        }
    }

    /** Let go of the card: it will land on the card under the finger, or fly home. */
    fun release() {
        if (card == null || settling) return
        val onto = cards.firstOrNull { it.key == target }
        pending = onto?.let { it to mode }
        landing = onto?.let { bounds[it.key] } ?: origin
        target = null
        settling = true
    }

    /** The miniature has landed: the drop to perform (held, onto, how), or null; then all is cleared. */
    fun finish(): Triple<RecentsCard, RecentsCard, String>? {
        val held = card
        val drop = pending
        cancel()
        return if (held != null && drop != null) Triple(held, drop.first, drop.second) else null
    }

    fun cancel() {
        card = null
        target = null
        origin = null
        landing = null
        settling = false
        pending = null
    }
}
