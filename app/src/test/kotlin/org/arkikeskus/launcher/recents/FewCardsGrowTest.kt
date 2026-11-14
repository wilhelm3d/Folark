package org.arkikeskus.launcher.recents

import org.junit.Assert.assertEquals
import org.junit.Test

/** Few Recents cards grow into the room of the empty columns, within the screen and a limit. */
class FewCardsGrowTest {
    @Test fun `two cards in four columns on the inner screen grow to the limit`() {
        // 850 dp less the margins, four columns: two cards could double, the limit holds them.
        assertEquals(FEW_CARDS_MAX_GROW, fewCardsGrow(2, 4, 794f, 700f, 14f, 300f), 0.001f)
    }

    @Test fun `a full row does not grow`() {
        assertEquals(1f, fewCardsGrow(2, 2, 379f, 1500f, 14f, 300f), 0.001f)
    }

    @Test fun `low room keeps the height inside the screen`() {
        assertEquals(1.2f, fewCardsGrow(1, 4, 794f, 360f, 14f, 300f), 0.001f)
    }

    @Test fun `never smaller than the grid`() {
        assertEquals(1f, fewCardsGrow(3, 4, 794f, 100f, 14f, 300f), 0.001f)
        assertEquals(1f, fewCardsGrow(0, 4, 794f, 700f, 14f, 300f), 0.001f)
    }

    @Test fun `cover screen — a single card grows only as tall as the room`() {
        // The cover is short (about 330 dp of room over the bottom margin): a lone card
        // could double by width but the height stops it at the room.
        assertEquals(1.1f, fewCardsGrow(1, 2, 370f, 330f, 14f, 300f), 0.01f)
    }
}
