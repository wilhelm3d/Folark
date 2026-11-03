package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.feature.home.TaskbarVisibility.Facts
import org.arkikeskus.launcher.feature.home.TaskbarVisibility.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskbarVisibilityTest {

    /** Over an app, screen on, nothing special: the default (handles only). */
    private val overApp = Facts(
        enabled = true, screenOn = true, keyguardLocked = false, hasContent = true,
        homeVisible = false, keyboardOnHome = false, keyboardUp = false, manuallyHidden = false,
        expanded = false, onHome = true, keepOpen = false, hideFullScreen = true, fullScreen = false,
        foreground = "com.example.mail",
    )
    private val onHome = overApp.copy(homeVisible = true, foreground = null)

    private fun mode(f: Facts) = TaskbarVisibility.mode(f)

    @Test fun nothingWhenOffLockedDarkOrEmpty() {
        assertEquals(Mode.NONE, mode(onHome.copy(enabled = false)))
        assertEquals(Mode.NONE, mode(onHome.copy(screenOn = false)))
        assertEquals(Mode.NONE, mode(onHome.copy(keyguardLocked = true)))
        assertEquals(Mode.NONE, mode(onHome.copy(hasContent = false)))
    }

    @Test fun home() {
        assertEquals(Mode.BAR_FIXED, mode(onHome))
        assertEquals(Mode.NONE, mode(onHome.copy(onHome = false)))
        assertEquals(Mode.NONE, mode(onHome.copy(keyboardOnHome = true)))
        assertEquals(Mode.HANDLES, mode(onHome.copy(manuallyHidden = true)))
        assertEquals(Mode.BAR_FIXED, mode(onHome.copy(manuallyHidden = true, expanded = true)))
    }

    @Test fun overAnApp() {
        assertEquals(Mode.HANDLES, mode(overApp))
        assertEquals(Mode.BAR, mode(overApp.copy(expanded = true)))
        assertEquals(Mode.BAR_FIXED, mode(overApp.copy(keepOpen = true)))
        assertEquals(Mode.HANDLES, mode(overApp.copy(keepOpen = true, keyboardUp = true)))
        assertEquals(Mode.HANDLES, mode(overApp.copy(keepOpen = true, fullScreen = true)))
        assertEquals(Mode.BAR_FIXED, mode(overApp.copy(keepOpen = true, fullScreen = true, hideFullScreen = false)))
    }

    @Test fun hiddenInThisApp() {
        // The hide button in an app is remembered for that app: handles even with keep-open on,
        // until a handle opens it again.
        val hidden = overApp.copy(keepOpen = true, manuallyHidden = true)
        assertEquals(Mode.HANDLES, mode(hidden))
        assertEquals(Mode.BAR, mode(hidden.copy(expanded = true)))
    }

    @Test fun perAppRules() {
        val pkg = overApp.foreground!!
        assertEquals(Mode.NONE, mode(overApp.copy(hiddenApps = setOf(pkg), expanded = true)))
        assertEquals(Mode.BAR_FIXED, mode(overApp.copy(alwaysApps = setOf(pkg))))
        assertEquals(Mode.HANDLES, mode(overApp.copy(alwaysApps = setOf(pkg), keyboardUp = true)))
        assertEquals(Mode.HANDLES, mode(overApp.copy(autoHideApps = setOf(pkg), keepOpen = true)))
        assertEquals(Mode.MINI, mode(overApp.copy(miniApps = setOf(pkg))))
        assertEquals(Mode.HANDLES, mode(overApp.copy(miniApps = setOf(pkg), keyboardUp = true)))
        // Rules for another app change nothing here.
        assertEquals(Mode.HANDLES, mode(overApp.copy(hiddenApps = setOf("other"), miniApps = setOf("other"))))
    }

    @Test fun needsForeground() {
        val none = emptySet<String>()
        val one = setOf("a")
        fun needs(
            hidden: Set<String> = none, always: Set<String> = none, autoHide: Set<String> = none,
            mini: Set<String> = none, here: Set<String> = none, keepOpen: Boolean = false, follows: Boolean = false,
        ) = TaskbarVisibility.needsForeground(hidden, always, autoHide, mini, here, keepOpen, follows)
        assertFalse(needs())
        assertTrue(needs(hidden = one))
        assertTrue(needs(always = one))
        assertTrue(needs(mini = one))
        assertTrue(needs(here = one))
        // Auto-hide and following open apps only matter for a kept-open bar.
        assertFalse(needs(autoHide = one))
        assertFalse(needs(follows = true))
        assertTrue(needs(autoHide = one, keepOpen = true))
        assertTrue(needs(follows = true, keepOpen = true))
    }
}
