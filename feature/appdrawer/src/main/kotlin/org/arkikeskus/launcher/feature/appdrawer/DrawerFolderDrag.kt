package org.arkikeskus.launcher.feature.appdrawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.arkikeskus.launcher.model.AppItem

/**
 * Drag-to-folder inside the drawer.
 *
 * An icon lifted in the drawer stays in the drawer: the cells it passes over light up, and dropping
 * it on another app makes a folder holding both (the Pixel/iOS gesture), while dropping it on a
 * folder tile files it in there. Only once the finger leaves the panel does the drag turn into the
 * old "drag it out to home / dock / taskbar" gesture ([org.arkikeskus.launcher.ui.HomeDragController]
 * takes over), so nothing outside the panel behaves differently.
 *
 * The drawer publishes what this needs: the panel's bounds, and where the icons and folder tiles
 * are — all in root coordinates, the same space the home drag controller already uses.
 */
internal class DrawerFolderDrag {
    /** The lifted app, while it is being dragged inside the drawer. */
    var dragged by mutableStateOf<AppItem?>(null)
        private set
    /** Where the finger is, in root coordinates (the floating icon is drawn here). */
    var position by mutableStateOf(Offset.Zero)
        private set
    /** The app cell under the finger: dropping there makes a folder of the two. */
    var overApp by mutableStateOf<AppItem?>(null)
        private set
    /** The folder tile under the finger: dropping there files the app in it. */
    var overFolder by mutableStateOf<Long?>(null)
        private set

    // These are plain maps on purpose: they are written from layout on every placement, and as
    // snapshot state each write would invalidate composition for a value nobody reads while
    // composing. Only the hover below is state, and it changes once per drop target crossed.
    /** Every app cell currently placed, by key, with the root bounds it was placed at. */
    val appBounds = HashMap<String, Pair<AppItem, Rect>>()

    /** Every folder tile currently placed, id → root bounds. */
    val folderBounds = HashMap<Long, Rect>()

    /**
     * Where the tab chips are, id → root bounds. A chip held down and slid over another one moves
     * the tab there (the same stored order Settings ▸ App drawer arranges), so the drop is decided
     * from where the chips really are rather than from an estimated chip width.
     */
    val tabBounds = HashMap<Long, Rect>()

    /** Folder membership as the drawer currently shows it, for "it is already in there". */
    var folderMembers: Map<Long, Set<String>> = emptyMap()

    /**
     * The drawer's app area in root coordinates. A drag is a folder drag only while the finger is
     * over the apps; leaving it (up over the header, or out of a short drawer) is the old
     * drag-it-out-to-home gesture. The header is the one strip that is never a drop target, which
     * makes it the only place an app can be carried out of the drawer.
     */
    var gridBounds by mutableStateOf(Rect.Zero)

    var onCombine: ((AppItem, AppItem) -> Unit)? = null
    var onAddToFolder: ((AppItem, Long) -> Unit)? = null
    var onOpenFolder: ((Long) -> Unit)? = null
    /** Edge auto-scroll, supplied by the drawer (null where the surface cannot scroll). */
    var onEdgeScroll: ((Float) -> Unit)? = null

    fun begin(app: AppItem) {
        dragged = app
        position = Offset.Zero
        overApp = null
        overFolder = null
    }

    /** The finger moved to [root] (root coordinates): refresh the ghost and the cell under it. */
    fun move(root: Offset) {
        position = root
        overApp = appBounds.entries
            .firstOrNull { (key, target) ->
                key != dragged?.key && !target.second.isEmpty && target.second.contains(root)
            }
            ?.value
            ?.first
        overFolder = folderBounds.entries.firstOrNull { !it.value.isEmpty && it.value.contains(root) }?.key
    }

    /**
     * Scrolls the drawer while the dragged icon rests near the top or bottom of the app area, at a
     * speed that grows with how deep into the band it is. Without this a folder above the fold is
     * simply unreachable from the end of a long list.
     */
    fun autoScroll(root: Offset, stepPx: Float, bandPx: Float) {
        val grid = gridBounds
        if (grid.isEmpty || bandPx <= 0f) return
        val depth = when {
            root.y < grid.top + bandPx -> (root.y - (grid.top + bandPx)) / bandPx
            root.y > grid.bottom - bandPx -> (root.y - (grid.bottom - bandPx)) / bandPx
            else -> 0f
        }
        onEdgeScroll?.invoke(depth.coerceIn(-1f, 1f) * stepPx)
    }

    /** Off the panel: the home controller owns the drag now, so nothing stays lit behind it. */
    fun clearHover() {
        overApp = null
        overFolder = null
    }

    /**
     * Resolves a drop that happened inside the panel. True when it landed in a folder (made a new
     * one or joined an existing one), so the caller knows not to treat it as a drag-out.
     */
    fun drop(): Boolean {
        val app = dragged ?: return false
        val appTarget = overApp
        val folderTarget = overFolder
        cancel()
        return when {
            appTarget != null && appTarget.key != app.key -> {
                onCombine?.invoke(app, appTarget)
                true
            }
            folderTarget != null -> {
                if (app.key in (folderMembers[folderTarget] ?: emptySet())) {
                    // Already filed there: open it rather than pretend the drop did something.
                    onOpenFolder?.invoke(folderTarget)
                } else {
                    onAddToFolder?.invoke(app, folderTarget)
                }
                true
            }
            else -> false
        }
    }

    fun cancel() {
        dragged = null
        overApp = null
        overFolder = null
        // Every drag that ends or leaves the panel stops the edge scroll with it.
        onEdgeScroll?.invoke(0f)
    }
}
