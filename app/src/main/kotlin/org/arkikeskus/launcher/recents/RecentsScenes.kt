package org.arkikeskus.launcher.recents

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.Haptics
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.SplitSizes
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.setTaskbarPairs
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType

/**
 * A split card from Recents kept as a scene: saved as an app pair, as a shortcut on home, or
 * pinned on the taskbar (split out of RecentsViewModel). A scene saved before (the same apps in
 * the same order) is used again rather than saved twice.
 */
internal class RecentsScenes(
    private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    private val appScope: CoroutineScope,
) {
    /**
     * Saves the split [card] as a scene: its apps in order, and (with the Vector module) where the
     * divider is now, so the scene opens the same way.
     */
    fun saveAsPair(card: RecentsCard): Boolean {
        if (!canSave(card)) return false
        appScope.launch {
            val pair = scene(card)
            if (pair == null) toastUnavailable() else appRepository.saveAppPair(pair)
        }
        return true
    }

    /**
     * The split [card] (two or three apps) as a scene with a shortcut on the home screen of
     * [screen]: the same scene again when it was saved before (same apps), else a new one.
     */
    fun addToHome(card: RecentsCard, screen: ScreenType): Boolean {
        if (!canSave(card)) return false
        appScope.launch {
            val pair = savedScene(card)
            if (pair == null) {
                toastUnavailable()
                return@launch
            }
            val s = settingsRepository.settings(screen).first()
            homeLayoutRepository.addShortcut(
                pair.app1Package,
                HomeItemEntity.appPairShortcutId(pair.id),
                pair.app1UserSerial,
                s.home.columns, s.home.rows, screen,
            )
        }
        return true
    }

    /** The split [card] pinned on the taskbar as one scene (the same scene when saved before). */
    fun pinSplitToTaskbar(card: RecentsCard) {
        if (!canSave(card)) return
        appScope.launch {
            // Settings ▸ Taskbar ▸ Lock taskbar apps: nothing joins the bar.
            if (settingsRepository.settings(ScreenType.INNER).first().taskbar.pinsLocked) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, org.arkikeskus.launcher.R.string.recents_taskbar_locked, android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val pair = savedScene(card)
            if (pair == null) {
                toastUnavailable()
                return@launch
            }
            val pinned = settingsRepository.settings(ScreenType.INNER).first().taskbar.pinnedPairs
            if (pair.id !in pinned) settingsRepository.setTaskbarPairs(pinned + pair.id)
            Haptics.play(context, Haptics.Event.SPLIT)
        }
    }

    /** The card's task layout couldn't be read, so no scene can be made from it right now. */
    private suspend fun toastUnavailable() {
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(
                context, org.arkikeskus.launcher.R.string.recents_scene_unavailable,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun canSave(card: RecentsCard): Boolean = card.apps.size in 2..3 && card.apps.none { it == null }

    /** The scene of [card] as saved: the one saved before with the same apps, else saved now. */
    private suspend fun savedScene(card: RecentsCard): AppPair? {
        val fresh = scene(card) ?: return null
        val known = appRepository.appPairs.first().firstOrNull {
            it.app1Key == fresh.app1Key && it.app2Key == fresh.app2Key && it.app3Key == fresh.app3Key
        }
        // The same apps as a scene saved before: used again, but with the layout and sizes the
        // split has now (re-saving after a re-split updates the scene instead of keeping old sizes).
        return known?.let { saved ->
            if (saved.layout == fresh.layout && saved.sizes == fresh.sizes && saved.peek == fresh.peek) saved
            else fresh.copy(id = saved.id, name = saved.name).also { appRepository.saveAppPair(it) }
        } ?: fresh.also { appRepository.saveAppPair(it) }
    }

    /** The scene [card] makes: its apps in order, with the layout the split has now. */
    private suspend fun scene(card: RecentsCard): AppPair? {
        val apps = card.apps
        if (apps.size !in 2..3 || apps.any { it == null }) return null
        val (a, b) = apps.requireNoNulls()
        val c = apps.getOrNull(2)
        val firstTask = card.tasks.firstOrNull()?.taskId ?: -1
        val layout = if (c == null) SplitThree.layoutForSnap(SplitThree.currentSnap(context, firstTask)) else AppPair.LAYOUT_EVEN
        return AppPair(
            id = java.util.UUID.randomUUID().toString(),
            name = apps.filterNotNull().joinToString(" + ") { it.label },
            app1Key = a.key, app1Package = a.packageName, app1ClassName = a.className, app1UserSerial = a.userSerial,
            app2Key = b.key, app2Package = b.packageName, app2ClassName = b.className, app2UserSerial = b.userSerial,
            app3Key = c?.key.orEmpty(), app3Package = c?.packageName.orEmpty(),
            app3ClassName = c?.className.orEmpty(), app3UserSerial = c?.userSerial ?: 0L,
            peek = layout == AppPair.LAYOUT_PEEK,
            layout = layout,
            // A trio saved from a three-pane split opens as one again.
            thirdMode = if (c != null && card.tasks.size >= 3) AppPair.THIRD_PANE else AppPair.THIRD_AUTO,
            // S11: the divider places as they are now (a free size, three apps' sizes, peek).
            sizes = dividerPlaces(card)?.joinToString(",").orEmpty(),
        )
    }

    /**
     * The split's divider places in thousandths, from where Android laid its tasks out (left to
     * right); null when it didn't say, or for plain half and half.
     */
    private fun dividerPlaces(card: RecentsCard): List<Int>? {
        val bounds = card.tasks.map { it.bounds ?: return null }
        if (bounds.size !in 2..3) return null
        val sideBySide = bounds.map { it[0] }.distinct().size == bounds.size
        val sorted = bounds.sortedBy { if (sideBySide) it[0] else it[1] }
        val start = if (sideBySide) sorted.first()[0] else sorted.first()[1]
        val end = if (sideBySide) sorted.last()[2] else sorted.last()[3]
        val span = (end - start).takeIf { it > 0 } ?: return null
        val places = sorted.dropLast(1).map { b -> (((if (sideBySide) b[2] else b[3]) - start).toLong() * 1000 / span).toInt() }
        return places.takeUnless { SplitSizes.isEven(it) }
    }
}
