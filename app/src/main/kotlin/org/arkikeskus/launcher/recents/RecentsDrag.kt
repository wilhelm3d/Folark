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

/**
 * Dragging one Recents card onto another (hold a card, then move it): which card is held, where
 * the finger is (in the screen's coordinates), and which card and which half it is over.
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

    /** Where each card is on screen, kept up to date as they are laid out. */
    val bounds = HashMap<Int, Rect>()
    var cards: List<RecentsCard> = emptyList()

    fun start(card: RecentsCard, at: Offset) {
        this.card = card
        pointer = at
        update()
    }

    fun move(by: Offset) {
        pointer += by
        update()
    }

    private fun update() {
        val held = card ?: return
        val hit = cards.firstOrNull { it.key != held.key && !it.isSplit && bounds[it.key]?.contains(pointer) == true }
        target = hit?.key
        if (hit != null) mode = if (pointer.x < bounds.getValue(hit.key).center.x) COMBINE_SPLIT else COMBINE_ATTACHED
    }

    /** The card dropped on and how, or null when let go over nothing. */
    fun drop(): Pair<RecentsCard, String>? {
        val onto = cards.firstOrNull { it.key == target }
        val how = mode
        cancel()
        return onto?.let { it to how }
    }

    fun cancel() {
        card = null
        target = null
    }
}
