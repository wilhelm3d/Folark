package org.arkikeskus.launcher.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which way a stroke on a Recents card goes (a card swipe, or left to the list's scroll). */
class SwipeAxisTest {

    @Test fun `mostly sideways is sideways`() {
        assertThat(swipeAxis(30f, 5f)).isEqualTo(SwipeAxis.SIDEWAYS)
        assertThat(swipeAxis(-30f, 20f)).isEqualTo(SwipeAxis.SIDEWAYS)
    }

    @Test fun `mostly up or down is up-down`() {
        assertThat(swipeAxis(4f, -40f)).isEqualTo(SwipeAxis.UP_DOWN)
        assertThat(swipeAxis(-16f, 20f)).isEqualTo(SwipeAxis.UP_DOWN)
    }

    @Test fun `a diagonal is neither`() {
        assertThat(swipeAxis(20f, 20f)).isEqualTo(SwipeAxis.DIAGONAL)
        assertThat(swipeAxis(-22f, 19f)).isEqualTo(SwipeAxis.DIAGONAL)
        assertThat(swipeAxis(0f, 0f)).isEqualTo(SwipeAxis.DIAGONAL)
    }

    @Test fun `exactly at the lead counts`() {
        assertThat(swipeAxis(25f, 20f)).isEqualTo(SwipeAxis.SIDEWAYS)
        assertThat(swipeAxis(20f, 25f)).isEqualTo(SwipeAxis.UP_DOWN)
    }
}
