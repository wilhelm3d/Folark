package org.arkikeskus.launcher.recents

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.data.OpenTask
import org.junit.Before
import org.junit.Test

/** Dragging a Recents card onto another: which card it is over, how they'd combine, the drop. */
class RecentsDragTest {

    private fun task(id: Int) = OpenTask(id, "app.$id", null, 0, null, null)
    private fun single(id: Int) = RecentsCard(listOf(task(id)), listOf(null), "app $id", null)
    private fun split(a: Int, b: Int) = RecentsCard(listOf(task(a), task(b)), listOf(null, null), "pair", null)

    private val one = single(1)
    private val two = single(2)
    private val pair = split(3, 4)
    private lateinit var drag: RecentsDrag

    @Before fun setUp() {
        drag = RecentsDrag()
        drag.cards = listOf(one, two, pair)
        // Three cards side by side, each 100 wide and 200 tall.
        drag.bounds[one.key] = Rect(0f, 0f, 100f, 200f)
        drag.bounds[two.key] = Rect(100f, 0f, 200f, 200f)
        drag.bounds[pair.key] = Rect(200f, 0f, 300f, 200f)
    }

    @Test fun `over nothing there is no target`() {
        drag.start(one, Offset(50f, 100f))
        assertThat(drag.target).isNull() // over itself
        drag.move(Offset(0f, 400f))
        assertThat(drag.target).isNull()
    }

    @Test fun `left half of a card is split view, right half attached view`() {
        drag.start(one, Offset(50f, 100f))
        drag.move(Offset(70f, 0f)) // x = 120: left half of card 2
        assertThat(drag.target).isEqualTo(two.key)
        assertThat(drag.mode).isEqualTo(COMBINE_SPLIT)
        drag.move(Offset(60f, 0f)) // x = 180: right half
        assertThat(drag.mode).isEqualTo(COMBINE_ATTACHED)
    }

    @Test fun `a split card is no target unless three-app split is on`() {
        drag.start(one, Offset(50f, 100f))
        drag.move(Offset(200f, 0f)) // x = 250: over the pair
        assertThat(drag.target).isNull()

        drag.allowThird = true
        drag.move(Offset(1f, 0f))
        assertThat(drag.target).isEqualTo(pair.key)
        assertThat(drag.mode).isEqualTo(COMBINE_THIRD)
    }

    @Test fun `letting go over a card lands on it and hands over the drop once`() {
        drag.start(one, Offset(50f, 100f))
        drag.move(Offset(70f, 0f))
        drag.release()
        assertThat(drag.settling).isTrue()
        assertThat(drag.landing).isEqualTo(drag.bounds[two.key])
        val drop = drag.finish()
        assertThat(drop).isEqualTo(Triple(one, two, COMBINE_SPLIT))
        assertThat(drag.card).isNull()
        assertThat(drag.finish()).isNull()
    }

    @Test fun `letting go over nothing flies home with no drop`() {
        drag.start(one, Offset(50f, 100f))
        drag.move(Offset(0f, 500f))
        drag.release()
        assertThat(drag.landing).isEqualTo(drag.bounds[one.key])
        assertThat(drag.finish()).isNull()
    }

    @Test fun `no moving while it lands`() {
        drag.start(one, Offset(50f, 100f))
        drag.release()
        drag.move(Offset(70f, 0f))
        assertThat(drag.target).isNull()
    }
}
