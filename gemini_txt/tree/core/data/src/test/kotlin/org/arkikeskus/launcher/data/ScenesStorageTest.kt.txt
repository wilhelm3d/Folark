package org.arkikeskus.launcher.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.model.AppPair
import org.junit.Assert.assertEquals
import org.junit.Test

/** Scenes (saved app pairs) as stored: old rows read back with their layout, new ones round-trip. */
class ScenesStorageTest {

    private val key = stringPreferencesKey("app_pairs")

    @Test
    fun `a pair saved by an older version keeps its peek view and splits on the cover screen`() = runTest {
        val store = InMemoryDataStore()
        val old = listOf(
            "id1", "Chat + Maps", "k1", "com.chat", "com.chat.Main", "0", "k2", "com.maps", "com.maps.Main", "0",
            "", "", "", "0", "1",
        ).joinToString("\t")
        store.edit { it[key] = old }
        val pair = SettingsRepository(store).appPairs.first().single()
        assertEquals(AppPair.LAYOUT_PEEK, pair.effectiveLayout)
        assertEquals(AppPair.THIRD_AUTO, pair.thirdMode)
        assertEquals(AppPair.COVER_SPLIT, pair.coverMode)
    }

    @Test
    fun `a pair from before peek opens half and half`() = runTest {
        val store = InMemoryDataStore()
        store.edit { it[key] = listOf("id1", "A + B", "k1", "a", "a.Main", "0", "k2", "b", "b.Main", "0").joinToString("\t") }
        assertEquals(AppPair.LAYOUT_EVEN, SettingsRepository(store).appPairs.first().single().effectiveLayout)
    }

    @Test
    fun `a scene round-trips its layout, third app and cover screen choice`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        val scene = AppPair(
            id = "s1", name = "Work", app1Key = "k1", app1Package = "com.mail", app1ClassName = "com.mail.Main", app1UserSerial = 0,
            app2Key = "k2", app2Package = "com.docs", app2ClassName = "com.docs.Main", app2UserSerial = 10,
            app3Key = "k3", app3Package = "com.chat", app3ClassName = "com.chat.Main", app3UserSerial = 0,
            layout = AppPair.LAYOUT_FIRST_TWO_THIRDS, thirdMode = AppPair.THIRD_FLOAT, coverMode = AppPair.COVER_FIRST,
        )
        repo.saveAppPair(scene)
        val back = repo.appPairs.first().single()
        assertEquals(scene, back)
        assertEquals(AppPair.LAYOUT_FIRST_TWO_THIRDS, back.effectiveLayout)
    }

    @Test
    fun `an unknown layout from a newer version falls back to half and half`() = runTest {
        val store = InMemoryDataStore()
        val row = listOf("id1", "A + B", "k1", "a", "a.Main", "0", "k2", "b", "b.Main", "0", "", "", "", "0", "0", "spiral", "x", "y")
        store.edit { it[key] = row.joinToString("\t") }
        val pair = SettingsRepository(store).appPairs.first().single()
        assertEquals(AppPair.LAYOUT_EVEN, pair.effectiveLayout)
        assertEquals(AppPair.THIRD_AUTO, pair.thirdMode)
        assertEquals(AppPair.COVER_SPLIT, pair.coverMode)
    }
}
