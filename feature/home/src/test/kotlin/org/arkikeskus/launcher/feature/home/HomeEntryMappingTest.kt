package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.os.Process
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.ui.AppShortcuts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What each stored home row shows ([homeEntries]): the rules that decide whether a row is an app, a
 * folder, a shortcut, a widget, a placeholder or a stack, and when it shows nothing at all. A wrong
 * branch here quietly drops an icon from home, so each kind is pinned down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeEntryMappingTest {

    private val provider = "com.example/.Widget"
    private fun app(pkg: String) = AppItem(pkg, "$pkg.Main", Process.myUserHandle(), 0L, pkg)
    private val maps = app("com.maps")
    private val chat = app("com.chat")
    private val apps = listOf(maps, chat).associateBy { it.key }

    private fun row(id: Long, x: Int = 0, container: Long = HomeItemEntity.HOME, pkg: String = "", cls: String = "") =
        HomeItemEntity(id = id, containerId = container, packageName = pkg, className = cls, page = 0, cellX = x, cellY = 0)

    private fun map(rows: List<HomeItemEntity>, pairs: List<AppPair> = emptyList(), resolved: Map<String, AppShortcuts.Resolved> = emptyMap()) =
        homeEntries(rows, apps, pairs) { resolved[it] }

    @Test
    fun `an installed app shows, a missing one does not`() {
        val entries = map(listOf(row(1, pkg = "com.maps", cls = "com.maps.Main"), row(2, x = 1, pkg = "com.gone", cls = "com.gone.Main")))
        assertThat(entries).containsExactly(PlacedApp(maps, 0, 0, 0))
    }

    @Test
    fun `a folder holds its installed apps once each, in stored order`() {
        val folder = row(10).copy(folderName = "Daily")
        val children = listOf(
            row(11, container = 10, pkg = "com.chat", cls = "com.chat.Main"),
            row(12, x = 1, container = 10, pkg = "com.maps", cls = "com.maps.Main"),
            row(13, x = 2, container = 10, pkg = "com.chat", cls = "com.chat.Main"),
            row(14, x = 3, container = 10, pkg = "com.gone", cls = "com.gone.Main"),
        )
        val entries = map(listOf(folder) + children)
        assertThat(entries).containsExactly(PlacedFolder(10, "Daily", listOf(chat, maps), 0, 0, 0))
    }

    @Test
    fun `a widget, its unbound placeholder and a built-in widget`() {
        val widget = row(20).copy(appWidgetId = 7, widgetProvider = provider, spanX = 2, spanY = 2)
        val pending = row(21, x = 2).copy(widgetProvider = provider)
        val builtin = row(22, x = 3).copy(builtinType = HomeItemEntity.BUILTIN_BATTERY)
        val entries = map(listOf(widget, pending, builtin))
        assertThat(entries.map { it::class }).containsExactly(PlacedWidget::class, PendingWidget::class, PlacedBuiltin::class).inOrder()
        assertThat((entries[0] as PlacedWidget).appWidgetId).isEqualTo(7)
        assertThat((entries[0] as WidgetEntry).spanX).isEqualTo(2)
    }

    @Test
    fun `a stack holds widgets, placeholders and built-ins, and disappears when empty`() {
        val stack = row(30).copy(builtinType = HomeItemEntity.BUILTIN_WIDGET_STACK, spanX = 4, spanY = 2)
        val children = listOf(
            row(31, container = 30).copy(builtinType = HomeItemEntity.BUILTIN_SMARTSPACE),
            row(32, x = 1, container = 30).copy(appWidgetId = 8, widgetProvider = provider),
            row(33, x = 2, container = 30).copy(widgetProvider = provider),
            // Neither a widget nor a placeholder: a stack cannot hold it.
            row(34, x = 3, container = 30, pkg = "com.maps", cls = "com.maps.Main"),
        )
        val emptyStack = row(40, x = 4).copy(builtinType = HomeItemEntity.BUILTIN_WIDGET_STACK)
        val entries = map(listOf(stack, emptyStack) + children)
        assertThat(entries).hasSize(1)
        val placed = entries.single() as PlacedWidgetStack
        assertThat(placed.items.map { (it as WidgetEntry).rowId }).containsExactly(31L, 32L, 33L).inOrder()
    }

    @Test
    fun `shortcuts - a resolved system one, a floating one and an app pair`() {
        val system = row(50, pkg = "com.chat").copy(shortcutId = "compose")
        val unresolved = row(51, x = 1, pkg = "com.chat").copy(shortcutId = "other")
        val floating = row(52, x = 2, pkg = "com.maps").copy(shortcutId = HomeItemEntity.floatingShortcutId("com.maps.Main"))
        val pair = AppPair("p1", "Maps + Chat", maps.key, maps.packageName, maps.className, 0L, chat.key, chat.packageName, chat.className, 0L)
        val pairRow = row(53, x = 3, pkg = "com.maps").copy(shortcutId = HomeItemEntity.appPairShortcutId("p1"))
        val lostPairRow = row(54, x = 4, pkg = "com.maps").copy(shortcutId = HomeItemEntity.appPairShortcutId("gone"))
        val entries = map(
            listOf(system, unresolved, floating, pairRow, lostPairRow),
            pairs = listOf(pair),
            resolved = mapOf(shortcutKey(system) to AppShortcuts.Resolved("New message", null)),
        ).map { it as PlacedShortcut }
        assertThat(entries.map { it.rowId }).containsExactly(50L, 52L, 53L).inOrder()
        assertThat(entries[0].label).isEqualTo("New message")
        assertThat(entries[1].floatingApp).isEqualTo(maps)
        assertThat(entries[2].pairApps).isEqualTo(maps to chat)
        assertThat(entries[2].label).isEqualTo("Maps + Chat")
    }
}
