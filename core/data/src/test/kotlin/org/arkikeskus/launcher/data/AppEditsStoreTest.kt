package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.model.ScreenType
import org.junit.Test

/** The stores behind adding and removing apps: "new" marks, the dock order for undo, muted channels. */
class AppEditsStoreTest {

    @Test
    fun `new apps are marked until opened`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        assertThat(repo.newApps.first()).isEmpty()
        repo.markNewApps(listOf("a/A/0", "b/B/0"))
        assertThat(repo.newApps.first()).containsExactly("a/A/0", "b/B/0")
        repo.clearNewApp("a/A/0")
        assertThat(repo.newApps.first()).containsExactly("b/B/0")
        // Clearing an app that is not new changes nothing.
        repo.clearNewApp("zzz")
        assertThat(repo.newApps.first()).containsExactly("b/B/0")
    }

    @Test
    fun `the dock order is read back so an undo can put an app back in its place`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        listOf("a", "b", "c").forEach { repo.addToDock(it, ScreenType.OUTER) }
        assertThat(repo.dockKeys(ScreenType.OUTER)).containsExactly("a", "b", "c").inOrder()
        val index = repo.dockKeys(ScreenType.OUTER).indexOf("b")
        repo.removeFromDock("b", ScreenType.OUTER)
        repo.addToDockAt("b", index, ScreenType.OUTER)
        assertThat(repo.dockKeys(ScreenType.OUTER)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `muted channels keep their names and unmute one by one or all`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        repo.setChannelHidden("chat.app", "promo", hidden = true, name = "Promotions")
        repo.setChannelHidden("chat.app", "msgs", hidden = true, name = null)
        assertThat(repo.hiddenChannels.first()).containsExactly("chat.app", setOf("promo", "msgs"))
        assertThat(repo.hiddenChannelList.first().map { it.name }).containsExactly("Promotions", null)
        repo.setChannelHidden("chat.app", "promo", hidden = false)
        assertThat(repo.hiddenChannels.first()).containsExactly("chat.app", setOf("msgs"))
        repo.clearHiddenChannels()
        assertThat(repo.hiddenChannelList.first()).isEmpty()
    }
}
