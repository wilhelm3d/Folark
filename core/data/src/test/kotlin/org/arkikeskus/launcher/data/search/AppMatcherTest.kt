package org.arkikeskus.launcher.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {

    private val apps = listOf("Google Maps", "Gmail", "YouTube", "Spotify", "Messages", "Café Bonjour", "Settings")

    private fun top(q: String) = AppMatcher.rank(apps, q) { it }.firstOrNull()

    @Test fun prefixBeatsEverything() {
        assertEquals("Gmail", top("gma"))
        assertEquals("Settings", top("set"))
    }

    @Test fun wordStart() = assertEquals("Google Maps", top("maps"))

    @Test fun initials() {
        // A name that starts with the letters still comes first; the initials match right after.
        assertEquals(listOf("Gmail", "Google Maps"), AppMatcher.rank(apps, "gm") { it })
        assertEquals("YouTube", top("yt"))
    }

    @Test fun lettersInOrder() = assertEquals("Gmail", top("gml"))

    @Test fun oneTypo() {
        assertEquals("Spotify", top("spotfy"))
        assertEquals("Spotify", top("sptoify"))
        assertEquals("Messages", top("mesages"))
        assertNull(AppMatcher.score("Spotify", "spxxfy"))
    }

    @Test fun accentsAndCase() = assertEquals("Café Bonjour", top("CAFE"))

    @Test fun shortQueriesStayStrict() {
        // Two letters: no gaps or typos, only real starts and initials.
        assertNull(AppMatcher.score("Spotify", "sy"))
        assertNotNull(AppMatcher.score("Spotify", "sp"))
    }

    @Test fun noMatch() = assertTrue(AppMatcher.rank(apps, "zzzz") { it }.isEmpty())
}
