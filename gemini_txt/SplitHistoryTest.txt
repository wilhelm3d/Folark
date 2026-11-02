package org.arkikeskus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Open with …": which partner [SplitHistory.pick] offers, on plain data. */
class SplitHistoryTest {

    private val day = 24L * 3600 * 1000
    private val now = 100 * day

    private fun pick(times: Map<String, List<Long>>, ignored: Map<String, Int> = emptyMap(), app: String = "com.chrome") =
        SplitHistory.pick(app, times, ignored, now)

    @Test
    fun `three times lately is enough, two isn't`() {
        assertEquals("com.maps", pick(mapOf("com.chrome|com.maps" to listOf(now - 3 * day, now - 2 * day, now - day))))
        assertNull(pick(mapOf("com.chrome|com.maps" to listOf(now - 2 * day, now - day))))
    }

    @Test
    fun `only times within the window count`() {
        val old = now - SplitHistory.SUGGEST_WINDOW_MS - day
        assertNull(pick(mapOf("com.chrome|com.maps" to listOf(old, old, now - day))))
    }

    @Test
    fun `either side of the pair finds the other`() {
        val t = mapOf("com.chrome|com.maps" to listOf(now - 3, now - 2, now - 1))
        assertEquals("com.chrome", pick(t, app = "com.maps"))
        assertNull(pick(t, app = "com.notes"))
    }

    @Test
    fun `the most often paired wins, the newest on a tie`() {
        val t = mapOf(
            "com.chrome|com.maps" to listOf(now - 5, now - 4, now - 3),
            "com.chrome|com.notes" to listOf(now - 9, now - 8, now - 7, now - 6),
            "com.chrome|com.mail" to listOf(now - 2, now - 1, now),
        )
        assertEquals("com.notes", pick(t))
        assertEquals("com.mail", pick(t - "com.chrome|com.notes"))
    }

    @Test
    fun `passed over three times, a pair isn't offered`() {
        val t = mapOf("com.chrome|com.maps" to listOf(now - 3, now - 2, now - 1))
        assertEquals("com.maps", pick(t, mapOf("com.chrome|com.maps" to 2)))
        assertNull(pick(t, mapOf("com.chrome|com.maps" to SplitHistory.SUGGEST_MAX_IGNORED)))
    }
}
