package org.arkikeskus.launcher.recents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task pictures from the other screen are shown whole; ones that nearly fit stay cropped.
 * Real aspects: the inner screen is 1080 × 2640 (0.41 wide per height), the cover is
 * 1056 × 1066 (0.99 — nearly square).
 */
class ThumbFitTest {
    @Test fun `inner screen picture in a nearly square cover card is shown whole`() {
        // 0.41 against 0.99: the picture is far taller than the card.
        assertTrue(thumbNeedsFit(0.41f, 0.99f))
    }

    @Test fun `inner screen picture in a wide card is shown whole`() {
        // 0.41 against 1.2: even wider — shown whole.
        assertTrue(thumbNeedsFit(0.41f, 1.2f))
    }

    @Test fun `cover screen picture in a cover card is cropped`() {
        // A nearly square picture in a nearly square card: crops fine.
        assertFalse(thumbNeedsFit(0.99f, 0.99f))
    }

    @Test fun `inner screen picture in an inner card nearly fits and stays cropped`() {
        // 0.41 against 0.53: one ratio step, inside the 1.35 threshold.
        assertFalse(thumbNeedsFit(0.41f, 0.53f))
    }

    @Test fun `empty sizes are left alone`() {
        assertFalse(thumbNeedsFit(0f, 0.5f))
        assertFalse(thumbNeedsFit(0.5f, 0f))
    }
}
