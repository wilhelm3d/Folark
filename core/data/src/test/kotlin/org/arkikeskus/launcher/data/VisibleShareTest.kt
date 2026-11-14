package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [visibleShare]: how much of what an icon mask shows a made-up themed glyph covers. */
class VisibleShareTest {

    private val edge = 96
    private val opaque = 0xFF00FFFF.toInt()

    private fun glyph(draw: (x: Int, y: Int) -> Boolean) =
        IntArray(edge * edge) { i -> if (draw(i % edge, i / edge)) opaque else 0 }

    @Test
    fun `an empty glyph covers nothing and a full one everything`() {
        assertThat(visibleShare(glyph { _, _ -> false }, edge, adaptive = true)).isEqualTo(0f)
        assertThat(visibleShare(glyph { _, _ -> true }, edge, adaptive = true)).isEqualTo(1f)
    }

    @Test
    fun `only the middle of an adaptive foreground counts`() {
        // A frame round the outer sixth is hidden by the mask on an adaptive icon, not on a plain one.
        val frame = glyph { x, y -> x < edge / 6 || y < edge / 6 || x >= edge * 5 / 6 || y >= edge * 5 / 6 }
        assertThat(visibleShare(frame, edge, adaptive = true)).isLessThan(0.02f)
        assertThat(visibleShare(frame, edge, adaptive = false)).isGreaterThan(0.3f)
    }

    @Test
    fun `a disc filling the mask counts as covering it, a small logo does not`() {
        val c = (edge - 1) / 2f
        val disc = glyph { x, y -> (x - c) * (x - c) + (y - c) * (y - c) <= (edge / 3f) * (edge / 3f) }
        val logo = glyph { x, y -> (x - c) * (x - c) + (y - c) * (y - c) <= (edge / 8f) * (edge / 8f) }
        assertThat(visibleShare(disc, edge, adaptive = true)).isGreaterThan(0.95f)
        assertThat(visibleShare(logo, edge, adaptive = true)).isLessThan(0.2f)
    }
}
