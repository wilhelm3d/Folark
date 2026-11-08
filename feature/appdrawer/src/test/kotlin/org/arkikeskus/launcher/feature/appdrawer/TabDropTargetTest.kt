package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind dragging a drawer tab chip: which index a lifted chip lands on. It is the
 * one part of the gesture that can be wrong without looking wrong on screen (an off-by-one leaves
 * the tab one place off), and it needs no Compose or device to check.
 */
class TabDropTargetTest {

    private val tabs = listOf(1L to "Calls", 2L to "Games", 3L to "Tools")

    /** Three 100 px chips in a row: centres at x = 50, 150, 250. */
    private fun rowBounds() = mapOf(
        1L to Rect(0f, 0f, 100f, 48f),
        2L to Rect(100f, 0f, 200f, 48f),
        3L to Rect(200f, 0f, 300f, 48f),
    )

    /** The same chips stacked in the two-pane rail: centres at y = 24, 72, 120. */
    private fun railBounds() = mapOf(
        1L to Rect(0f, 0f, 160f, 48f),
        2L to Rect(0f, 48f, 160f, 96f),
        3L to Rect(0f, 96f, 160f, 144f),
    )

    @Test
    fun `a chip released where it started keeps its index`() {
        assertEquals(0, tabDropTarget(tabs, 0, 1L, rowBounds(), 0f, horizontal = true))
        assertEquals(2, tabDropTarget(tabs, 2, 3L, rowBounds(), 0f, horizontal = true))
    }

    @Test
    fun `dragging right past one chip lands after it`() {
        // Chip 1's centre 50 + 120 = 170: past chip 2 (150), not past chip 3 (250).
        assertEquals(1, tabDropTarget(tabs, 0, 1L, rowBounds(), 120f, horizontal = true))
    }

    @Test
    fun `dragging left past a chip lands before it`() {
        // Chip 3's centre 250 - 120 = 130: past chip 1 (50), not past chip 2 (150).
        assertEquals(1, tabDropTarget(tabs, 2, 3L, rowBounds(), -120f, horizontal = true))
    }

    @Test
    fun `a drag to the far end lands last`() {
        assertEquals(2, tabDropTarget(tabs, 0, 1L, rowBounds(), 400f, horizontal = true))
    }

    @Test
    fun `the two-pane rail reorders on the vertical axis`() {
        // Chip 1's centre 24 + 60 = 84: past chip 2 (72), not past chip 3 (120).
        assertEquals(1, tabDropTarget(tabs, 0, 1L, railBounds(), 60f, horizontal = false))
    }

    @Test
    fun `a chip whose place is unknown stays put`() {
        assertEquals(1, tabDropTarget(tabs, 1, 4L, rowBounds(), 200f, horizontal = true))
    }
}
