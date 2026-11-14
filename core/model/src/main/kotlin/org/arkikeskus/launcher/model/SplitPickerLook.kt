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
    /** The row of recently used apps at the top. */
    val recents: PickerRecents = PickerRecents.ICONS,
    /** How many recent apps that row shows. */
    val recentsCount: Int = 5,
    /** Order of the apps. */
    val sort: PickerSort = PickerSort.NAME,
    /** A search field at the top. */
    val search: Boolean = false,
    /** How solid the drawer's background is (0 see-through … 1 solid). */
    val opacity: Float = 0.95f,
    /** How the picked app opens in the picker's half. */
    val openAnim: PickerOpen = PickerOpen.ZOOM,
    /** A row of the apps you marked as favorites ([favoriteKeys], in the order added). */
    val favorites: Boolean = true,
    val favoriteKeys: List<String> = emptyList(),
    /** A row of the apps the dragged app was last put beside. */
    val recentPairs: Boolean = true,
    /** A Peek switch at the top: the pick then opens as Android's peek view (needs the Vector module). */
    val peekToggle: Boolean = true,
    /** Long-press an app for "Open as pop-up", "Open full screen" and favorites. */
    val longPress: Boolean = true,
) {
    companion object {
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

/** The split picker's row of recently used apps. */
enum class PickerRecents(override val stored: String) : StoredChoice {
    OFF("off"),
    ICONS("icons"),

    /** Small screenshots of the apps (needs root; icons without it). */
    PREVIEWS("previews"),
}

/** The order of the split picker's apps. */
enum class PickerSort(override val stored: String) : StoredChoice {
    NAME("name"),

    /** Most used first (Folark's own usage count). */
    USAGE("usage"),
}

/** How the app picked in the split picker opens in its half. */
enum class PickerOpen(override val stored: String) : StoredChoice {
    /** The tapped icon grows to fill the picker's half, then the app opens over it. */
    ZOOM("zoom"),

    /** The app fades in over the picker. */
    FADE("fade"),

    /** Android's own opening animation. */
    NONE("none"),
}
