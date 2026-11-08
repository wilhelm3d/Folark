package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.LaunchSettings

import org.arkikeskus.launcher.model.RootSettings

import org.arkikeskus.launcher.model.RecentsSettings

import org.arkikeskus.launcher.model.LiveSettings

import org.arkikeskus.launcher.model.TaskbarSettings

import org.arkikeskus.launcher.model.AppearanceSettings

import org.arkikeskus.launcher.model.MotionSettings

import org.arkikeskus.launcher.model.FoldSettings

import org.arkikeskus.launcher.model.StatusBarSettings

import org.arkikeskus.launcher.model.WidgetSettings

import org.arkikeskus.launcher.model.FolderSettings

import org.arkikeskus.launcher.model.DotSettings

import org.arkikeskus.launcher.model.IconSettings

import org.arkikeskus.launcher.model.SearchSettings

import org.arkikeskus.launcher.model.DrawerSettings

import org.arkikeskus.launcher.model.HomeSettings

import org.arkikeskus.launcher.model.DockSettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.withLiquidGlass
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.model.DropZones
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicReferenceArray
import javax.inject.Singleton

// The app-drawer folder store: folders, their members and their order. Split out of
// SettingsRepository as extension functions so the repository file stays readable; it is
// still the same type, and callers are unchanged.

// --- App-drawer folders ----------------------------------------------------------------------
// Serialized one folder per line, tab-separated fields: "id\tname\tkey1\tkey2...". App keys
// never contain a tab; folder names are normalized in serializeFolders, so a plain split
// round-trips cleanly.

val SettingsRepository.drawerFolders: Flow<List<DrawerFolder>> get() = keyFlow(Keys.DRAWER_FOLDERS) { parseFolders(it) }

/** Creates an empty drawer folder, returns its new id. */
suspend fun SettingsRepository.createDrawerFolder(name: String): Long {
    var newId = 1L
    edit { p ->
        val folders = parseFolders(p[Keys.DRAWER_FOLDERS])
        newId = (folders.maxOfOrNull { it.id } ?: 0L) + 1L
        p[Keys.DRAWER_FOLDERS] = serializeFolders(folders + DrawerFolder(newId, name, emptyList()))
    }
    return newId
}

suspend fun SettingsRepository.renameDrawerFolder(id: Long, name: String) = editFolders { folders ->
    folders.map { if (it.id == id) it.copy(name = name) else it }
}

suspend fun SettingsRepository.deleteDrawerFolder(id: Long) = editFolders { folders -> folders.filterNot { it.id == id } }

suspend fun SettingsRepository.addAppsToDrawerFolder(id: Long, keys: List<String>) = editFolders { folders ->
    folders.map { f ->
        if (f.id == id) f.copy(appKeys = (f.appKeys + keys).distinct()) else f
    }
}

/** Takes [key] out of the folder and answers how many apps it still holds (0 = now empty). */
suspend fun SettingsRepository.removeAppFromDrawerFolder(id: Long, key: String): Int {
    var remaining = 0
    editFolders { folders ->
        folders.map { f ->
            if (f.id != id) {
                f
            } else {
                f.copy(appKeys = f.appKeys - key).also { remaining = it.appKeys.size }
            }
        }
    }
    return remaining
}

/**
 * Puts [keys] in folder [id] and takes them out of every other folder in the same write, so an
 * app is never briefly in two folders. Folders emptied by the move are dropped: the one that
 * loses its last app disappears instead of sitting there empty (the iOS behaviour).
 */
suspend fun SettingsRepository.moveAppsIntoDrawerFolder(id: Long, keys: List<String>) = editFolders { folders ->
    val moving = keys.toSet()
    folders.mapNotNull { f ->
        when {
            f.id == id -> f.copy(appKeys = (f.appKeys + keys).distinct())
            else -> {
                val remaining = f.appKeys.filterNot { it in moving }
                when {
                    remaining.size == f.appKeys.size -> f
                    remaining.isEmpty() -> null
                    else -> f.copy(appKeys = remaining)
                }
            }
        }
    }
}

/**
 * Moves the folder at [from] to [to]. The folder list is also the tab order, so this one move
 * reorders the settings list and the drawer's tab row together.
 */
suspend fun SettingsRepository.moveDrawerFolder(from: Int, to: Int) = editFolders { folders ->
    if (from == to || from !in folders.indices || to !in folders.indices) {
        folders
    } else {
        folders.toMutableList().apply { add(to, removeAt(from)) }
    }
}

private suspend fun SettingsRepository.editFolders(block: (List<DrawerFolder>) -> List<DrawerFolder>) = edit { p ->
    p[Keys.DRAWER_FOLDERS] = serializeFolders(block(parseFolders(p[Keys.DRAWER_FOLDERS])))
}

private fun SettingsRepository.parseFolders(raw: String?): List<DrawerFolder> =
    raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
        val parts = line.split('\t')
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        DrawerFolder(id, parts.getOrNull(1).orEmpty(), parts.drop(2).filter { it.isNotEmpty() })
    } ?: emptyList()

private fun SettingsRepository.serializeFolders(folders: List<DrawerFolder>): String =
    folders.joinToString("\n") { f ->
        // The record format is tab/newline-separated; a pasted separator in a folder name would
        // truncate the record and corrupt the member list — normalize at the one write point.
        val safeName = f.name.replace(SEPARATORS, " ")
        (listOf(f.id.toString(), safeName) + f.appKeys).joinToString("\t")
    }

internal val SEPARATORS = Regex("[\\t\\n\\r]+")
