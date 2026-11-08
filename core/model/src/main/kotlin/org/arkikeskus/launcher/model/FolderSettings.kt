package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Home folders. */
@Immutable
data class FolderSettings(
    /** Home folders: tile shape, colour theme and what the closed tile previews. */
    val shape: String = LauncherSettings.FOLDER_SHAPE_SQUIRCLE,
    val theme: String = LauncherSettings.FOLDER_THEME_GLASS,
    val preview: String = LauncherSettings.FOLDER_PREVIEW_GRID_2,
    /** Home folders, opened: columns of the app grid and the overall size (icons + sheet height). */
    val openColumns: Int = 4,
    val openSize: String = LauncherSettings.FOLDER_OPEN_MEDIUM,
    /** Tap a folder → launch its first app; a long-press (without moving) opens the folder. */
    val tapFirstApp: Boolean = false,
    /** The apps in a folder's 2×2 / 3×3 tile open with a tap on them; the last place opens the
     *  folder when it holds more apps than the tile shows. */
    val directApps: Boolean = false,
    /** How a home folder opens (one of FOLDER_ANIM_*). */
    val openAnimation: String = LauncherSettings.FOLDER_ANIM_SHEET,
)
