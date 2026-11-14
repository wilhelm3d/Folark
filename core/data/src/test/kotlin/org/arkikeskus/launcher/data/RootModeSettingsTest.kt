package org.arkikeskus.launcher.data

import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.model.RootMode
import org.arkikeskus.launcher.model.ScreenType
import org.junit.Test

/**
 * Settings ▸ Root's modes are an enum in the code but the same text in the store as always ("off",
 * "fallback", "always"), so settings saved by an older Folark, and backups, read back unchanged.
 */
class RootModeSettingsTest {

    @Test
    fun `each mode is saved as its old text and read back as itself`() = runTest {
        val store = InMemoryDataStore()
        val repo = SettingsRepository(store)
        for (mode in RootMode.entries) {
            repo.setRootLockMode(mode)
            repo.setRootRecentsMode(mode)
            repo.setRootShadeMode(mode)
            assertThat(store.data.first()[Keys.ROOT_LOCK_MODE]).isEqualTo(mode.stored)
            val root = repo.settings(ScreenType.INNER).first().root
            assertThat(listOf(root.lockMode, root.recentsMode, root.shadeMode)).containsExactly(mode, mode, mode)
        }
        assertThat(RootMode.entries.map { it.stored }).containsExactly("off", "fallback", "always").inOrder()
    }

    @Test
    fun `nothing stored, or a value no Folark wrote, is the fallback mode`() = runTest {
        val store = InMemoryDataStore()
        val repo = SettingsRepository(store)
        assertThat(repo.settings(ScreenType.OUTER).first().root.shadeMode).isEqualTo(RootMode.FALLBACK)
        store.edit { it[Keys.ROOT_SHADE_MODE] = "sometimes" }
        assertThat(repo.settings(ScreenType.OUTER).first().root.shadeMode).isEqualTo(RootMode.FALLBACK)
    }
}
