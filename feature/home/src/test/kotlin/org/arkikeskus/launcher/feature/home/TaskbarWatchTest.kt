package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.feature.home.taskbar.*

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The app-watch polling pace (TaskbarHost ticks on this). */
class TaskbarWatchTest {

    @Test
    fun `a change returns to the fast pace from anywhere`() {
        assertThat(TaskbarWatch.nextDelay(changed = true, current = 1_000, cap = TaskbarWatch.SLOW_MS)).isEqualTo(1_000)
        assertThat(TaskbarWatch.nextDelay(changed = true, current = 10_000, cap = TaskbarWatch.SLOW_MS)).isEqualTo(1_000)
    }

    @Test
    fun `quiet ticks ease one and a half times towards the cap`() {
        assertThat(TaskbarWatch.nextDelay(changed = false, current = 1_000, cap = TaskbarWatch.SLOW_MS)).isEqualTo(1_500)
        assertThat(TaskbarWatch.nextDelay(changed = false, current = 1_500, cap = TaskbarWatch.SLOW_MS)).isEqualTo(2_250)
        assertThat(TaskbarWatch.nextDelay(changed = false, current = 2_250, cap = TaskbarWatch.SLOW_MS)).isEqualTo(3_000)
    }

    @Test
    fun `the cap holds however quiet it has been`() {
        assertThat(TaskbarWatch.nextDelay(changed = false, current = 5_000, cap = TaskbarWatch.SLOW_MS)).isEqualTo(3_000)
        // 9_000 would ease to 13_500; the hidden cap is far enough away to let it grow that far.
        assertThat(TaskbarWatch.nextDelay(changed = false, current = 9_000, cap = TaskbarWatch.HIDDEN_MS)).isEqualTo(13_500)
    }

    @Test
    fun `the bar shown takes the short cap, hidden the long one`() {
        assertThat(TaskbarWatch.cap(barShown = true, heard = false)).isEqualTo(TaskbarWatch.SLOW_MS)
        assertThat(TaskbarWatch.cap(barShown = false, heard = false)).isEqualTo(TaskbarWatch.HIDDEN_MS)
    }

    @Test
    fun `a service hearing switches takes the safety net pace, shown or not`() {
        // It reports every app switch itself: polling only catches a service killed mid-session.
        assertThat(TaskbarWatch.cap(barShown = true, heard = true)).isEqualTo(TaskbarWatch.SAFETY_MS)
        assertThat(TaskbarWatch.cap(barShown = false, heard = true)).isEqualTo(TaskbarWatch.SAFETY_MS)
    }

    @Test
    fun `hidden the backoff climbs past the shown cap but never past the hidden one`() {
        var delay = TaskbarWatch.FAST_MS
        val seen = mutableListOf<Long>()
        repeat(12) {
            delay = TaskbarWatch.nextDelay(changed = false, current = delay, cap = TaskbarWatch.HIDDEN_MS)
            seen.add(delay)
        }
        assertThat(seen.last()).isEqualTo(TaskbarWatch.HIDDEN_MS)
        // Somewhere on the way it passed the pace a shown bar would have been capped at…
        assertThat(seen.any { it > TaskbarWatch.SLOW_MS }).isTrue()
        // …and it never jumps straight there from a change (a change resets to fast).
        assertThat(seen.first()).isEqualTo(1_500)
    }
}
