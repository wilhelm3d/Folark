package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/**
 * How the split picker (the app drawer that opens beside an app dragged to a screen edge) looks:
 * Settings ▸ Recents ▸ Split screen. Every part can be switched off.
 */
@Immutable
data class SplitPickerLook(
    /** Icons per row. */
    val columns: Int = 5,
    /** Icon size, dp. */
    val iconSize: Int = 46,
    /** App names under the icons. */
    val labels: Boolean = true,
    /** Drawer folders as the first row; their apps then aren't listed again below. Off: every app in the grid. */
    val folders: Boolean = true,
    /** The row of recently used apps at the top: one of RECENTS_*. */
    val recents: String = RECENTS_ICONS,
    /** How many recent apps that row shows. */
    val recentsCount: Int = 5,
    /** Order of the apps: one of SORT_*. */
    val sort: String = SORT_NAME,
    /** A search field at the top. */
    val search: Boolean = false,
    /** How solid the drawer's background is (0 see-through … 1 solid). */
    val opacity: Float = 0.95f,
    /** How the picked app opens in the picker's half: one of OPEN_*. */
    val openAnim: String = OPEN_ZOOM,
) {
    companion object {
        const val RECENTS_OFF = "off"
        const val RECENTS_ICONS = "icons"
        /** Small screenshots of the apps (needs root; icons without it). */
        const val RECENTS_PREVIEWS = "previews"
        val RECENTS_STYLES = listOf(RECENTS_OFF, RECENTS_ICONS, RECENTS_PREVIEWS)

        const val SORT_NAME = "name"
        /** Most used first (Folark's own usage count). */
        const val SORT_USAGE = "usage"
        val SORTS = listOf(SORT_NAME, SORT_USAGE)

        /** The tapped icon grows to fill the picker's half, then the app opens over it. */
        const val OPEN_ZOOM = "zoom"
        /** The app fades in over the picker. */
        const val OPEN_FADE = "fade"
        /** Android's own opening animation. */
        const val OPEN_NONE = "none"
        val OPEN_ANIMS = listOf(OPEN_ZOOM, OPEN_FADE, OPEN_NONE)

        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 7
        const val MIN_ICON = 32
        const val MAX_ICON = 64
        const val MIN_RECENTS = 2
        const val MAX_RECENTS = 10
        const val MIN_OPACITY = 0.5f
        const val MAX_OPACITY = 1f
    }
}
