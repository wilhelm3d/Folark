package org.arkikeskus.launcher.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.junit.Test

/**
 * JVM unit tests for the dock-favorites merge logic. Backed by an in-memory [DataStore] fake (no
 * file I/O), so the tests are fast, deterministic, and platform-independent.
 */
class SettingsRepositoryTest {

    @Test
    fun `upgrade repairs page counters restored as Long by an older version before reading`() = runTest {
        val store = InMemoryDataStore()
        store.edit {
            it[longPreferencesKey("home_page")] = 2L
            it[longPreferencesKey("home_page_count")] = 4L
        }
        val repo = SettingsRepository(store)
        assertThat(repo.settings.first().home.page).isEqualTo(2)
        assertThat(repo.settings.first().home.pageCount).isEqualTo(4)
        assertThat(store.data.first()[intPreferencesKey("home_page")]).isEqualTo(2)
        assertThat(store.data.first()[intPreferencesKey("home_page_count")]).isEqualTo(4)
        assertThat(SettingsRepository(store).settings.first().home.page).isEqualTo(2)
    }

    @Test
    fun `legacy page counters clamp before conversion so large Longs cannot wrap`() = runTest {
        val store = InMemoryDataStore()
        store.edit {
            it[longPreferencesKey("home_page")] = Long.MIN_VALUE
            it[longPreferencesKey("home_page_count")] = Long.MAX_VALUE
        }
        val settings = SettingsRepository(store).settings.first()
        assertThat(settings.home.page).isEqualTo(0)
        assertThat(settings.home.pageCount).isEqualTo(HomeLayoutRepository.MAX_PAGES)
    }

    private fun newRepository() = SettingsRepository(InMemoryDataStore())

    @Test
    fun `reorderVisibleDock keeps favorites hidden by the column cap`() = runTest {
        val repo = newRepository()
        listOf("a", "b", "c", "d", "e", "f").forEach { repo.addToDock(it) }

        // Only the first 4 are visible (dockColumns); reorder those, leaving e & f hidden.
        repo.reorderVisibleDock(listOf("d", "c", "b", "a"))

        assertThat(repo.dockFavorites.first())
            .containsExactly("d", "c", "b", "a", "e", "f").inOrder()
    }

    @Test
    fun `reorderVisibleDock does not introduce duplicates`() = runTest {
        val repo = newRepository()
        listOf("a", "b", "c").forEach { repo.addToDock(it) }

        // A duplicate in the visible list must be collapsed, and the tail kept once.
        repo.reorderVisibleDock(listOf("b", "b", "a"))

        assertThat(repo.dockFavorites.first()).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun `addToDock appends once and ignores duplicates`() = runTest {
        val repo = newRepository()
        repo.addToDock("a")
        repo.addToDock("a")
        repo.addToDock("b")

        assertThat(repo.dockFavorites.first()).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `removeFromDock drops only the given key`() = runTest {
        val repo = newRepository()
        listOf("a", "b", "c").forEach { repo.addToDock(it) }

        repo.removeFromDock("b")

        assertThat(repo.dockFavorites.first()).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `addToDockAt inserts at the given index`() = runTest {
        val repo = newRepository()
        listOf("a", "b", "c").forEach { repo.addToDock(it) }

        repo.addToDockAt("x", index = 1)

        assertThat(repo.dockFavorites.first()).containsExactly("a", "x", "b", "c").inOrder()
    }

    @Test
    fun `addToDockAt repositions an existing key without duplicating`() = runTest {
        val repo = newRepository()
        listOf("a", "b", "c").forEach { repo.addToDock(it) }

        repo.addToDockAt("c", index = 0)

        assertThat(repo.dockFavorites.first()).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `searchContacts defaults to false and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().search.contacts).isFalse()

        repo.setSearchContacts(true)
        assertThat(repo.settings.first().search.contacts).isTrue()

        repo.setSearchContacts(false)
        assertThat(repo.settings.first().search.contacts).isFalse()
    }

    @Test
    fun `leftSwipeAppKey defaults to blank and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.leftSwipeAppKey).isEmpty()

        repo.setLeftSwipeAppKey("com.example/Main/0")
        assertThat(repo.settings.first().home.leftSwipeAppKey).isEqualTo("com.example/Main/0")

        // null clears back to blank (None / gesture disabled).
        repo.setLeftSwipeAppKey(null)
        assertThat(repo.settings.first().home.leftSwipeAppKey).isEmpty()
    }

    @Test
    fun `desktopLocked defaults to false and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.desktopLocked).isFalse()

        repo.setDesktopLocked(true)
        assertThat(repo.settings.first().home.desktopLocked).isTrue()

        repo.setDesktopLocked(false)
        assertThat(repo.settings.first().home.desktopLocked).isFalse()
    }

    @Test
    fun `showFrequentApps defaults to false and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().drawer.showFrequentApps).isFalse()

        repo.setShowFrequentApps(true)
        assertThat(repo.settings.first().drawer.showFrequentApps).isTrue()

        repo.setShowFrequentApps(false)
        assertThat(repo.settings.first().drawer.showFrequentApps).isFalse()
    }

    @Test
    fun `homeColumns defaults to 4, persists and coerces to its range`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.columns).isEqualTo(4)

        repo.setHomeColumns(8)
        assertThat(repo.settings.first().home.columns).isEqualTo(8)

        repo.setHomeColumns(99)
        assertThat(repo.settings.first().home.columns).isEqualTo(SettingsRepository.MAX_COLUMNS)

        repo.setHomeColumns(1)
        assertThat(repo.settings.first().home.columns).isEqualTo(SettingsRepository.MIN_COLUMNS)
    }

    @Test
    fun `homeRows defaults to 6, persists and coerces to its range`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.rows).isEqualTo(6)

        repo.setHomeRows(8)
        assertThat(repo.settings.first().home.rows).isEqualTo(8)

        repo.setHomeRows(99)
        assertThat(repo.settings.first().home.rows).isEqualTo(SettingsRepository.MAX_ROWS)

        repo.setHomeRows(1)
        assertThat(repo.settings.first().home.rows).isEqualTo(SettingsRepository.MIN_ROWS)
    }

    @Test
    fun `notificationWidgetCountStyle defaults to number and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().widgets.notificationCountStyle)
            .isEqualTo(LauncherSettings.COUNT_NUMBER)

        repo.setNotificationWidgetCountStyle(LauncherSettings.COUNT_DOT)
        assertThat(repo.settings.first().widgets.notificationCountStyle)
            .isEqualTo(LauncherSettings.COUNT_DOT)
    }

    @Test
    fun `notificationWidgetCountStyle falls back to number on an unknown stored value`() = runTest {
        val repo = newRepository()
        repo.setNotificationWidgetCountStyle("garbage")
        assertThat(repo.settings.first().widgets.notificationCountStyle)
            .isEqualTo(LauncherSettings.COUNT_NUMBER)
    }

    @Test
    fun `amoledDark defaults to false and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().appearance.amoledDark).isFalse()

        repo.setAmoledDark(true)
        assertThat(repo.settings.first().appearance.amoledDark).isTrue()

        repo.setAmoledDark(false)
        assertThat(repo.settings.first().appearance.amoledDark).isFalse()
    }

    @Test
    fun `drawer folder names with tabs and newlines round-trip without corrupting the record`() = runTest {
        val repo = newRepository()
        val id = repo.createDrawerFolder("Fun\tstuff\nrow2")
        repo.addAppsToDrawerFolder(id, listOf("com.a/A/0", "com.b/B/0"))
        val folder = repo.drawerFolders.first().single()
        assertThat(folder.name).isEqualTo("Fun stuff row2")
        assertThat(folder.appKeys).containsExactly("com.a/A/0", "com.b/B/0").inOrder()
    }

    @Test
    fun `twoLineHomeLabels defaults to false and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.twoLineLabels).isFalse()

        repo.setTwoLineHomeLabels(true)
        assertThat(repo.settings.first().home.twoLineLabels).isTrue()

        repo.setTwoLineHomeLabels(false)
        assertThat(repo.settings.first().home.twoLineLabels).isFalse()
    }

    @Test
    fun `twoLineDrawerLabels defaults to true and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().drawer.twoLineLabels).isTrue()

        repo.setTwoLineDrawerLabels(false)
        assertThat(repo.settings.first().drawer.twoLineLabels).isFalse()

        repo.setTwoLineDrawerLabels(true)
        assertThat(repo.settings.first().drawer.twoLineLabels).isTrue()
    }

    @Test
    fun `drawerLayoutMode defaults to grid and round-trips`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().drawer.layoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_GRID)

        repo.setDrawerLayoutMode(LauncherSettings.DRAWER_LAYOUT_LIST)
        assertThat(repo.settings.first().drawer.layoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_LIST)

        repo.setDrawerLayoutMode(LauncherSettings.DRAWER_LAYOUT_GRID)
        assertThat(repo.settings.first().drawer.layoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_GRID)
    }

    @Test
    fun `customLabels saves and reads correctly`() = runTest {
        val repo = newRepository()
        assertThat(repo.customLabels.first()).isEmpty()

        repo.setCustomLabel("com.example.app/0", "Custom Name")
        assertThat(repo.customLabels.first()["com.example.app/0"]).isEqualTo("Custom Name")

        repo.setCustomLabel("com.example.app/0", null)
        assertThat(repo.customLabels.first()).isEmpty()
    }

    @Test
    fun `setCustomAppLabel saves and removes correctly`() = runTest {
        val repo = newRepository()
        assertThat(repo.customLabels.first()).isEmpty()

        repo.setCustomAppLabel("com.example.app/MainActivity", "My Custom App")
        assertThat(repo.customLabels.first()["com.example.app/MainActivity"]).isEqualTo("My Custom App")

        repo.setCustomAppLabel("com.example.app/MainActivity", "")
        assertThat(repo.customLabels.first()["com.example.app/MainActivity"]).isNull()
    }

    @Test
    fun `parallaxWallpaper defaults to false and toggles`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings.first().home.parallaxWallpaper).isFalse()

        repo.setParallaxWallpaper(true)
        assertThat(repo.settings.first().home.parallaxWallpaper).isTrue()

        repo.setParallaxWallpaper(false)
        assertThat(repo.settings.first().home.parallaxWallpaper).isFalse()
    }

    @Test
    fun `drawerColumns defaults to 4 outer and 6 inner, coerces to range 3-8 and is independent`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.columns).isEqualTo(4)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.columns).isEqualTo(6)

        repo.setDrawerColumns(5, ScreenType.OUTER)
        repo.setDrawerColumns(8, ScreenType.INNER)

        assertThat(repo.settings(ScreenType.OUTER).first().drawer.columns).isEqualTo(5)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.columns).isEqualTo(8)

        // Test range clamping (3 to 8)
        repo.setDrawerColumns(1, ScreenType.OUTER)
        repo.setDrawerColumns(10, ScreenType.INNER)

        assertThat(repo.settings(ScreenType.OUTER).first().drawer.columns).isEqualTo(3)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.columns).isEqualTo(8)
    }

    @Test
    fun `drawerRows defaults to 6, coerces to range 3-8 and is independent`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.rows).isEqualTo(6)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.rows).isEqualTo(6)

        repo.setDrawerRows(5, ScreenType.OUTER)
        repo.setDrawerRows(7, ScreenType.INNER)

        assertThat(repo.settings(ScreenType.OUTER).first().drawer.rows).isEqualTo(5)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.rows).isEqualTo(7)

        // Test range clamping (3 to 8)
        repo.setDrawerRows(1, ScreenType.OUTER)
        repo.setDrawerRows(12, ScreenType.INNER)

        assertThat(repo.settings(ScreenType.OUTER).first().drawer.rows).isEqualTo(3)
        assertThat(repo.settings(ScreenType.INNER).first().drawer.rows).isEqualTo(8)
    }

    @Test
    fun `advanced folding features roundtrip and persist correctly`() = runTest {
        val repo = newRepository()
        repo.setFoldAction(LauncherSettings.ACTION_PAGE_0, ScreenType.OUTER)
        repo.setUnfoldAction(LauncherSettings.ACTION_SEARCH, ScreenType.INNER)
        repo.setFoldTransitionStyle(LauncherSettings.TRANSITION_SLIDE)

        val outerSettings = repo.settings(ScreenType.OUTER).first()
        assertThat(outerSettings.fold.foldAction).isEqualTo(LauncherSettings.ACTION_PAGE_0)
        assertThat(outerSettings.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_SLIDE)

        val innerSettings = repo.settings(ScreenType.INNER).first()
        assertThat(innerSettings.fold.unfoldAction).isEqualTo(LauncherSettings.ACTION_SEARCH)
    }

    @Test
    fun `aquamorphic animations and fluid motion settings roundtrip and support independent outer and inner screen configuration`() = runTest {
        val repo = newRepository()
        repo.setAquamorphicTouchEnabled(false, ScreenType.OUTER)
        repo.setFoldTransitionStyle(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP, ScreenType.OUTER)
        repo.setAppLaunchZoomEnabled(false, ScreenType.OUTER)
        repo.setPageBounceEnabled(false, ScreenType.OUTER)

        repo.setAquamorphicTouchEnabled(true, ScreenType.INNER)
        repo.setFoldTransitionStyle(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE, ScreenType.INNER)
        repo.setAppLaunchZoomEnabled(true, ScreenType.INNER)
        repo.setPageBounceEnabled(true, ScreenType.INNER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.motion.aquamorphicTouchEnabled).isFalse()
        assertThat(outer.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        assertThat(outer.motion.appLaunchZoomEnabled).isFalse()
        assertThat(outer.home.pageBounceEnabled).isFalse()

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.motion.aquamorphicTouchEnabled).isTrue()
        assertThat(inner.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        assertThat(inner.motion.appLaunchZoomEnabled).isTrue()
        assertThat(inner.home.pageBounceEnabled).isTrue()
    }

    @Test
    fun `advanced app drawer customizations roundtrip and support independent outer and inner screen configuration`() = runTest {
        val repo = newRepository()
        // Outer screen config
        repo.setDrawerSearchPosition(LauncherSettings.SEARCH_BOTTOM, ScreenType.OUTER)
        repo.setDrawerIndexBarEnabled(false, ScreenType.OUTER)
        repo.setAzIndexPosition(LauncherSettings.AZ_POSITION_VERTICAL_LEFT, ScreenType.OUTER)
        repo.setDrawerBackgroundBlur(12f, ScreenType.OUTER)
        repo.setDrawerScrimOpacity(0.7f, ScreenType.OUTER)
        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_MOTO, ScreenType.OUTER)
        repo.setDrawerAccentColor(0xFF00BCD4.toInt(), ScreenType.OUTER)
        repo.setShowDrawerLabels(false, ScreenType.OUTER)
        repo.setShowFrequentApps(true, ScreenType.OUTER)

        // Inner screen config (independent)
        repo.setDrawerSearchPosition(LauncherSettings.SEARCH_TOP, ScreenType.INNER)
        repo.setDrawerIndexBarEnabled(true, ScreenType.INNER)
        repo.setAzIndexPosition(LauncherSettings.AZ_POSITION_HORIZONTAL_TOP, ScreenType.INNER)
        repo.setDrawerBackgroundBlur(4f, ScreenType.INNER)
        repo.setDrawerScrimOpacity(0.3f, ScreenType.INNER)
        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_ONE_UI, ScreenType.INNER)
        repo.setDrawerAccentColor(0xFFFF1744.toInt(), ScreenType.INNER)
        repo.setShowDrawerLabels(true, ScreenType.INNER)
        repo.setShowFrequentApps(false, ScreenType.INNER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.drawer.searchPosition).isEqualTo(LauncherSettings.SEARCH_BOTTOM)
        assertThat(outer.drawer.indexBarEnabled).isFalse()
        assertThat(outer.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
        assertThat(outer.drawer.backgroundBlur).isEqualTo(12f)
        assertThat(outer.drawer.scrimOpacity).isEqualTo(0.7f)
        assertThat(outer.drawer.style).isEqualTo(LauncherSettings.DRAWER_STYLE_MOTO)
        assertThat(outer.drawer.accentColor).isEqualTo(0xFF00BCD4.toInt())
        assertThat(outer.drawer.showLabels).isFalse()
        assertThat(outer.drawer.showFrequentApps).isTrue()

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.drawer.searchPosition).isEqualTo(LauncherSettings.SEARCH_TOP)
        assertThat(inner.drawer.indexBarEnabled).isTrue()
        assertThat(inner.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_HORIZONTAL_TOP)
        assertThat(inner.drawer.backgroundBlur).isEqualTo(4f)
        assertThat(inner.drawer.scrimOpacity).isEqualTo(0.3f)
        assertThat(inner.drawer.style).isEqualTo(LauncherSettings.DRAWER_STYLE_ONE_UI)
        assertThat(inner.drawer.accentColor).isEqualTo(0xFFFF1744.toInt())
        assertThat(inner.drawer.showLabels).isTrue()
        assertThat(inner.drawer.showFrequentApps).isFalse()
    }

    @Test
    fun `gesture settings support independent outer and inner screen configuration`() = runTest {
        val repo = newRepository()
        repo.setLeftSwipeAppKey("app.outer", ScreenType.OUTER)
        repo.setRightSwipeAppKey("app.outer.right", ScreenType.OUTER)

        repo.setLeftSwipeAppKey("app.inner", ScreenType.INNER)
        repo.setRightSwipeAppKey("app.inner.right", ScreenType.INNER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.home.leftSwipeAppKey).isEqualTo("app.outer")
        assertThat(outer.home.rightSwipeAppKey).isEqualTo("app.outer.right")

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.home.leftSwipeAppKey).isEqualTo("app.inner")
        assertThat(inner.home.rightSwipeAppKey).isEqualTo("app.inner.right")
    }

    @Test
    fun `drawer style automatically adopts authentic signature brand color accent`() = runTest {
        val repo = newRepository()
        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_ONE_UI, ScreenType.OUTER)
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.accentColor).isEqualTo(0xFF0A84FF.toInt())

        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_MOTO, ScreenType.OUTER)
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.accentColor).isEqualTo(0xFF00BFA5.toInt())

        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_NOTHING_OS, ScreenType.OUTER)
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.accentColor).isEqualTo(0xFFFF3B30.toInt())
    }

    @Test
    fun `dpi scaling settings roundtrip and support independent outer and inner screen configuration`() = runTest {
        val repo = newRepository()
        val defaultOuter = repo.settings(ScreenType.OUTER).first()
        assertThat(defaultOuter.appearance.dpiWorkspace).isEqualTo(1.0f)
        assertThat(defaultOuter.appearance.dpiAppDrawer).isEqualTo(1.0f)
        assertThat(defaultOuter.appearance.dpiSettings).isEqualTo(1.0f)

        repo.setDpiWorkspace(0.8f, ScreenType.OUTER)
        repo.setDpiAppDrawer(0.9f, ScreenType.OUTER)
        repo.setDpiSettings(1.1f, ScreenType.OUTER)

        repo.setDpiWorkspace(1.2f, ScreenType.INNER)
        repo.setDpiAppDrawer(1.3f, ScreenType.INNER)
        repo.setDpiSettings(1.4f, ScreenType.INNER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.appearance.dpiWorkspace).isEqualTo(0.8f)
        assertThat(outer.appearance.dpiAppDrawer).isEqualTo(0.9f)
        assertThat(outer.appearance.dpiSettings).isEqualTo(1.1f)

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.appearance.dpiWorkspace).isEqualTo(1.2f)
        assertThat(inner.appearance.dpiAppDrawer).isEqualTo(1.3f)
        assertThat(inner.appearance.dpiSettings).isEqualTo(1.4f)
    }

    @Test
    fun `dpi scaling values are coerced within 0,7f to 1,4f range`() = runTest {
        val repo = newRepository()
        repo.setDpiWorkspace(0.5f, ScreenType.OUTER)
        repo.setDpiAppDrawer(2.0f, ScreenType.OUTER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.appearance.dpiWorkspace).isEqualTo(0.7f)
        assertThat(outer.appearance.dpiAppDrawer).isEqualTo(1.4f)
    }

    @Test
    fun `drawer dimensions and alignment settings roundtrip and support independent outer and inner screen configuration`() = runTest {
        val repo = newRepository()
        val defaultOuter = repo.settings(ScreenType.OUTER).first()
        assertThat(defaultOuter.drawer.heightFraction).isEqualTo(1.0f)
        assertThat(defaultOuter.drawer.widthFraction).isEqualTo(1.0f)
        assertThat(defaultOuter.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_CENTER)

        // Configure OUTER
        repo.setDrawerHeightFraction(0.8f, ScreenType.OUTER)
        repo.setDrawerWidthFraction(0.7f, ScreenType.OUTER)
        repo.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_LEFT, ScreenType.OUTER)

        // Configure INNER
        repo.setDrawerHeightFraction(0.6f, ScreenType.INNER)
        repo.setDrawerWidthFraction(0.5f, ScreenType.INNER)
        repo.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_RIGHT, ScreenType.INNER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.drawer.heightFraction).isEqualTo(0.8f)
        assertThat(outer.drawer.widthFraction).isEqualTo(0.7f)
        assertThat(outer.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_LEFT)

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.drawer.heightFraction).isEqualTo(0.6f)
        assertThat(inner.drawer.widthFraction).isEqualTo(0.5f)
        assertThat(inner.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_RIGHT)
    }

    @Test
    fun `drawer dimension fractions are coerced within 0,4f to 1,0f range`() = runTest {
        val repo = newRepository()
        repo.setDrawerHeightFraction(0.2f, ScreenType.OUTER)
        repo.setDrawerWidthFraction(1.5f, ScreenType.OUTER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.drawer.heightFraction).isEqualTo(0.4f)
        assertThat(outer.drawer.widthFraction).isEqualTo(1.0f)
    }

    @Test
    fun `all configuration preferences strictly isolate between outer and inner screen profiles`() = runTest {
        val repo = newRepository()

        // Outer configurations
        repo.setHomeColumns(5, ScreenType.OUTER)
        repo.setHomeRows(7, ScreenType.OUTER)
        repo.setDockColumns(5, ScreenType.OUTER)
        repo.setDockEnabled(true, ScreenType.OUTER)
        repo.setDockBackgroundOpacity(0.8f, ScreenType.OUTER)
        repo.setShowDockLabels(true, ScreenType.OUTER)
        repo.setShowHomeLabels(false, ScreenType.OUTER)
        repo.setAppLabelTextScale(1.2f, ScreenType.OUTER)
        repo.setAppLabelColor(0xFF112233.toInt(), ScreenType.OUTER)
        repo.setTwoLineHomeLabels(true, ScreenType.OUTER)
        repo.setTwoLineDrawerLabels(false, ScreenType.OUTER)
        repo.setParallaxWallpaper(true, ScreenType.OUTER)
        repo.setAmoledDark(true, ScreenType.OUTER)
        repo.setShowDrawerSearch(false, ScreenType.OUTER)
        repo.setDrawerOpensAtTop(false, ScreenType.OUTER)
        repo.setShowStatusBar(true, ScreenType.OUTER)
        repo.setHideSystemStatusBar(true, ScreenType.OUTER)
        repo.setStatusBarScrimOpacity(0.9f, ScreenType.OUTER)
        repo.setShowPageIndicator(false, ScreenType.OUTER)
        repo.setSwipeDownAction(LauncherSettings.GESTURE_SEARCH, ScreenType.OUTER)
        repo.setDoubleTapAction(LauncherSettings.GESTURE_LOCK, ScreenType.OUTER)
        repo.setSwipeUpAction(LauncherSettings.GESTURE_NONE, ScreenType.OUTER)

        // Inner configurations
        repo.setHomeColumns(6, ScreenType.INNER)
        repo.setHomeRows(8, ScreenType.INNER)
        repo.setDockColumns(6, ScreenType.INNER)
        repo.setDockEnabled(false, ScreenType.INNER)
        repo.setDockBackgroundOpacity(0.2f, ScreenType.INNER)
        repo.setShowDockLabels(false, ScreenType.INNER)
        repo.setShowHomeLabels(true, ScreenType.INNER)
        repo.setAppLabelTextScale(0.9f, ScreenType.INNER)
        repo.setAppLabelColor(0xFF445566.toInt(), ScreenType.INNER)
        repo.setTwoLineHomeLabels(false, ScreenType.INNER)
        repo.setTwoLineDrawerLabels(true, ScreenType.INNER)
        repo.setParallaxWallpaper(false, ScreenType.INNER)
        repo.setAmoledDark(false, ScreenType.INNER)
        repo.setShowDrawerSearch(true, ScreenType.INNER)
        repo.setDrawerOpensAtTop(true, ScreenType.INNER)
        repo.setShowStatusBar(false, ScreenType.INNER)
        repo.setHideSystemStatusBar(false, ScreenType.INNER)
        repo.setStatusBarScrimOpacity(0.3f, ScreenType.INNER)
        repo.setShowPageIndicator(true, ScreenType.INNER)
        repo.setSwipeDownAction(LauncherSettings.GESTURE_DRAWER, ScreenType.INNER)
        repo.setDoubleTapAction(LauncherSettings.GESTURE_NOTIFICATIONS, ScreenType.INNER)
        repo.setSwipeUpAction(LauncherSettings.GESTURE_SEARCH, ScreenType.INNER)

        // Assert Outer Screen settings
        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.home.columns).isEqualTo(5)
        assertThat(outer.home.rows).isEqualTo(7)
        assertThat(outer.dock.columns).isEqualTo(5)
        assertThat(outer.dock.enabled).isTrue()
        assertThat(outer.dock.backgroundOpacity).isEqualTo(0.8f)
        assertThat(outer.dock.showLabels).isTrue()
        assertThat(outer.home.showLabels).isFalse()
        assertThat(outer.icons.labelTextScale).isEqualTo(1.2f)
        assertThat(outer.icons.labelColor).isEqualTo(0xFF112233.toInt())
        assertThat(outer.home.twoLineLabels).isTrue()
        assertThat(outer.drawer.twoLineLabels).isFalse()
        assertThat(outer.home.parallaxWallpaper).isTrue()
        assertThat(outer.appearance.amoledDark).isTrue()
        assertThat(outer.drawer.showSearch).isFalse()
        assertThat(outer.drawer.opensAtTop).isFalse()
        assertThat(outer.statusBar.enabled).isTrue()
        assertThat(outer.statusBar.hideSystem).isTrue()
        assertThat(outer.statusBar.scrimOpacity).isEqualTo(0.9f)
        assertThat(outer.home.showPageIndicator).isFalse()
        assertThat(outer.home.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(outer.home.doubleTapAction).isEqualTo(LauncherSettings.GESTURE_LOCK)
        assertThat(outer.home.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_NONE)

        // Assert Inner Screen settings
        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.home.columns).isEqualTo(6)
        assertThat(inner.home.rows).isEqualTo(8)
        assertThat(inner.dock.columns).isEqualTo(6)
        assertThat(inner.dock.enabled).isFalse()
        assertThat(inner.dock.backgroundOpacity).isEqualTo(0.2f)
        assertThat(inner.dock.showLabels).isFalse()
        assertThat(inner.home.showLabels).isTrue()
        assertThat(inner.icons.labelTextScale).isEqualTo(0.9f)
        assertThat(inner.icons.labelColor).isEqualTo(0xFF445566.toInt())
        assertThat(inner.home.twoLineLabels).isFalse()
        assertThat(inner.drawer.twoLineLabels).isTrue()
        assertThat(inner.home.parallaxWallpaper).isFalse()
        assertThat(inner.appearance.amoledDark).isFalse()
        assertThat(inner.drawer.showSearch).isTrue()
        assertThat(inner.drawer.opensAtTop).isTrue()
        assertThat(inner.statusBar.enabled).isFalse()
        assertThat(inner.statusBar.hideSystem).isFalse()
        assertThat(inner.statusBar.scrimOpacity).isEqualTo(0.3f)
        assertThat(inner.home.showPageIndicator).isTrue()
        assertThat(inner.home.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_DRAWER)
        assertThat(inner.home.doubleTapAction).isEqualTo(LauncherSettings.GESTURE_NOTIFICATIONS)
        assertThat(inner.home.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
    }

    // --- Display profiles never inherit from each other ----------------------------------------

    @Test
    fun `upgrade freezes what the inner profile inherited, then the profiles are independent`() = runTest {
        val store = InMemoryDataStore()
        store.edit {
            it[booleanPreferencesKey("show_home_labels")] = false
            it[floatPreferencesKey("dock_opacity")] = 0.8f
            it[stringPreferencesKey("fold_transition_style")] = LauncherSettings.TRANSITION_SLIDE
        }
        val repo = SettingsRepository(store)

        // The old build showed the inner display the outer values; an upgrade must not change that.
        val inherited = repo.settings(ScreenType.INNER).first()
        assertThat(inherited.home.showLabels).isFalse()
        assertThat(inherited.dock.backgroundOpacity).isEqualTo(0.8f)
        assertThat(inherited.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_SLIDE)

        // ...but from now on an outer change stays on the outer display.
        repo.setShowHomeLabels(true, ScreenType.OUTER)
        repo.setDockBackgroundOpacity(0.1f, ScreenType.OUTER)
        repo.setFoldTransitionStyle(LauncherSettings.TRANSITION_SCALE, ScreenType.OUTER)

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.home.showLabels).isFalse()
        assertThat(inner.dock.backgroundOpacity).isEqualTo(0.8f)
        assertThat(inner.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_SLIDE)
        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.home.showLabels).isTrue()
        assertThat(outer.dock.backgroundOpacity).isEqualTo(0.1f)
    }

    @Test
    fun `fresh install - an outer setting never reaches the inner display`() = runTest {
        val repo = newRepository()
        repo.settings(ScreenType.OUTER).first()

        repo.setShowDrawerLabels(false, ScreenType.OUTER)
        repo.setAppLabelTextScale(1.4f, ScreenType.OUTER)
        repo.setShowStatusBar(true, ScreenType.OUTER)

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.drawer.showLabels).isTrue()
        assertThat(inner.icons.labelTextScale).isEqualTo(1.0f)
        assertThat(inner.statusBar.enabled).isFalse()
    }

    @Test
    fun `drawer look is stored per display, clamped and opaque`() = runTest {
        val repo = newRepository()
        assertThat(repo.settings(ScreenType.OUTER).first().drawer.themeTint)
            .isEqualTo(LauncherSettings.DRAWER_THEME_SYSTEM)

        repo.setDrawerGlassEnabled(true, ScreenType.INNER)
        repo.setDrawerPanelOpacity(0.01f, ScreenType.INNER)
        repo.setDrawerThemeTint(LauncherSettings.DRAWER_THEME_CUSTOM, ScreenType.INNER)
        repo.setDrawerCustomColor(0x00123456, ScreenType.INNER)

        val inner = repo.settings(ScreenType.INNER).first()
        assertThat(inner.drawer.glassEnabled).isTrue()
        assertThat(inner.drawer.panelOpacity).isEqualTo(LauncherSettings.MIN_DRAWER_PANEL_OPACITY)
        assertThat(inner.drawer.themeTint).isEqualTo(LauncherSettings.DRAWER_THEME_CUSTOM)
        assertThat(inner.drawer.customColor).isEqualTo(0xFF123456.toInt())

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.drawer.glassEnabled).isFalse()
        assertThat(outer.drawer.themeTint).isEqualTo(LauncherSettings.DRAWER_THEME_SYSTEM)
    }

    @Test
    fun `a style change keeps an accent the user picked`() = runTest {
        val repo = newRepository()
        repo.setDrawerAccentColor(0xFF7B1FA2.toInt(), ScreenType.OUTER)

        repo.setDrawerStyle(LauncherSettings.DRAWER_STYLE_ONE_UI, ScreenType.OUTER)

        assertThat(repo.settings(ScreenType.OUTER).first().drawer.accentColor).isEqualTo(0xFF7B1FA2.toInt())
    }

    @Test
    fun `copyProfile copies settings but not the grid size or the dock apps`() = runTest {
        val repo = newRepository()
        repo.setDrawerGlassEnabled(true, ScreenType.INNER)
        repo.setSwipeUpAction(LauncherSettings.GESTURE_SEARCH, ScreenType.INNER)
        repo.setFoldTransitionStyle(LauncherSettings.TRANSITION_CROSSFADE, ScreenType.INNER)
        repo.setHomeColumns(7, ScreenType.INNER)
        repo.addToDock("pkg/Activity", ScreenType.INNER)
        repo.setDrawerScrimOpacity(0.9f, ScreenType.OUTER) // unset on the source: goes back to default

        repo.copyProfile(from = ScreenType.INNER, to = ScreenType.OUTER)

        val outer = repo.settings(ScreenType.OUTER).first()
        assertThat(outer.drawer.glassEnabled).isTrue()
        assertThat(outer.home.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(outer.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_CROSSFADE)
        assertThat(outer.drawer.scrimOpacity).isEqualTo(0.5f)
        assertThat(outer.home.columns).isEqualTo(4)
        assertThat(repo.dockFavorites(ScreenType.OUTER).first()).isEmpty()
        assertThat(repo.settings(ScreenType.INNER).first().home.columns).isEqualTo(7)
    }
}
