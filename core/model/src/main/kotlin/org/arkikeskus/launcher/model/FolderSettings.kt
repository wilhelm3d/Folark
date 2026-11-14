package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Home folders. */
@Immutable
data class FolderSettings(
    /** Home folders: tile shape, colour theme and what the closed tile previews. */
    val shape: FolderShape = FolderShape.SQUIRCLE,
    val theme: FolderTheme = FolderTheme.GLASS,
    val preview: FolderPreview = FolderPreview.GRID_2,
    /** Home folders, opened: columns of the app grid and the overall size (icons + sheet height). */
    val openColumns: Int = 4,
    val openSize: SizeChoice = SizeChoice.MEDIUM,
    /** Tap a folder → launch its first app; a long-press (without moving) opens the folder. */
    val tapFirstApp: Boolean = false,
    /** The apps in a folder's 2×2 / 3×3 tile open with a tap on them; the last place opens the
     *  folder when it holds more apps than the tile shows. */
    val directApps: Boolean = false,
    /** How a home folder opens (one of [FolderAnimation]). */
    val openAnimation: FolderAnimation = FolderAnimation.SHEET,
)

/** How a folder opens. */
enum class FolderAnimation(override val stored: String) : StoredChoice {
    SHEET("sheet"),
    ZOOM("zoom"),
    FADE("fade"),
    NONE("none"),
}

/** Closed-tile preview: a 2Ã2 or 3Ã3 grid of the first apps, or the first app alone as a cover. */
enum class FolderPreview(override val stored: String) : StoredChoice {
    GRID_2("grid2"),
    GRID_3("grid3"),
    COVER("cover"),
}

/** A closed folder's shape. */
enum class FolderShape(override val stored: String) : StoredChoice {
    SQUIRCLE("squircle"),
    CIRCLE("circle"),
    SQUARE("square"),
}

/** An open folder's look. */
enum class FolderTheme(override val stored: String) : StoredChoice {
    GLASS("glass"),
    DARK("dark"),
    LIGHT("light"),
    ACCENT("accent"),
}

/** Small, medium or large: how big an open folder is, the note widget's text, a floating window. */
enum class SizeChoice(override val stored: String) : StoredChoice {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
}
