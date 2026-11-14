package org.arkikeskus.launcher.data.root

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUpdatesTest {

    @Test fun aDisabledUpdateAppAlwaysMeansBlocked() {
        assertFalse(SystemUpdates.decide(packageOn = false, backgroundMode = "allow", blockedByFolark = false))
        assertFalse(SystemUpdates.decide(packageOn = false, backgroundMode = null, blockedByFolark = false))
    }

    @Test fun theAppOpsBlockCountsEvenWhenThePackageLooksOn() {
        // Motorola protects the OTA app from being disabled: the block sits in its AppOps.
        assertFalse(SystemUpdates.decide(packageOn = true, backgroundMode = "ignore", blockedByFolark = false))
    }

    @Test fun readAppOpsWinOverFolarksOwnNote() {
        // Another tool may have allowed the app again: what the phone says is the answer.
        assertTrue(SystemUpdates.decide(packageOn = true, backgroundMode = "default", blockedByFolark = true))
        assertTrue(SystemUpdates.decide(packageOn = true, backgroundMode = "allow", blockedByFolark = true))
    }

    @Test fun withoutRootTheNoteDecides() {
        assertFalse(SystemUpdates.decide(packageOn = true, backgroundMode = null, blockedByFolark = true))
        assertTrue(SystemUpdates.decide(packageOn = true, backgroundMode = null, blockedByFolark = false))
    }
}
