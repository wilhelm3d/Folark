package org.arkikeskus.launcher.data.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How a restore went. */
enum class RestoreResult {
    /** Everything was put back. */
    RESTORED,

    /** The file is not a Folark backup (or is damaged). */
    NOT_A_BACKUP,

    /** Made by a newer Folark than this one; update first. */
    TOO_NEW,

    /** The file could not be read or the data could not be written. */
    FAILED,
}

/**
 * Backs up and restores everything the launcher keeps: every setting of both displays (folders in
 * the drawer, hidden apps, renamed apps, picked icons, widget looks, people pins…), the home screens
 * and dock of both displays with their folders, stacks and widgets, and the home background
 * pictures. A backup is one zip file the user keeps on the phone (or anywhere the system file
 * picker reaches), so it survives uninstalling the launcher.
 *
 * Kept out of a backup: what only means something on this install — notifications being held for
 * the next batch, and whether the first-run intro was shown. Widgets of other apps come back as
 * "tap to set up" placeholders after a reinstall, since Android gives every install new widget ids.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val database: LauncherDatabase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _restoreGeneration = MutableStateFlow(0)

    /** Goes up after every restore; the home screen reloads when it sees a new value. */
    val restoreGeneration: StateFlow<Int> = _restoreGeneration.asStateFlow()

    /** The folder automatic backups go to (a document tree the user picked), or "" when off. */
    val autoBackupFolder: Flow<String> = dataStore.data.map { it[AUTO_FOLDER].orEmpty() }.distinctUntilChanged()

    /** When the last automatic backup was written (epoch ms), 0 for never. */
    val lastAutoBackup: Flow<Long> = dataStore.data.map { it[AUTO_LAST] ?: 0L }.distinctUntilChanged()

    // ------------------------------------------------------------------ backing up

    /** Writes a backup into [target] (a document the user just created). False if it failed. */
    suspend fun backupTo(target: Uri): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val bytes = buildBackup()
                writeDocument(target, bytes)
            }.isSuccess
        }
    }

    /** Builds the whole backup in memory first, so a failure never leaves half a file behind. */
    private suspend fun buildBackup(): ByteArray {
        val manifest = JSONObject()
        manifest.put("format", FORMAT)
        manifest.put("version", VERSION)
        manifest.put("created", System.currentTimeMillis())
        manifest.put("filesDir", context.filesDir.absolutePath)
        manifest.put("settings", settingsToJson(dataStore.data.first()))
        manifest.put("homeItems", homeItemsToJson())

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            backupFiles().forEach { file ->
                zip.putNextEntry(ZipEntry(FILES_DIR_ENTRY + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun settingsToJson(prefs: Preferences): JSONArray {
        val list = JSONArray()
        for ((key, value) in prefs.asMap()) {
            if (key.name in DEVICE_LOCAL_KEYS) continue
            val entry = JSONObject().put("k", key.name)
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Double -> entry.put("t", "d").put("v", value)
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                is ByteArray -> entry.put("t", "ba").put("v", Base64.encodeToString(value, Base64.NO_WRAP))
                else -> continue
            }
            list.put(entry)
        }
        return list
    }

    private fun homeItemsToJson(): JSONArray {
        val rows = JSONArray()
        database.openHelper.readableDatabase.query("SELECT * FROM $HOME_TABLE").use { c ->
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until c.columnCount) {
                    val name = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> row.put(name, c.getString(i))
                        else -> Unit
                    }
                }
                rows.put(row)
            }
        }
        return rows
    }

    /** The launcher's own files worth keeping (the home background pictures). */
    private fun backupFiles(): List<File> =
        context.filesDir.listFiles().orEmpty().filter { file ->
            file.isFile && FILE_PREFIXES.any { file.name.startsWith(it) }
        }

    private fun writeDocument(target: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        // "wt" truncates an existing file; a few providers only know plain "w".
        val stream = runCatching { resolver.openOutputStream(target, "wt") }.getOrNull()
            ?: resolver.openOutputStream(target, "w")
            ?: error("Cannot write the backup")
        stream.use { it.write(bytes) }
    }

    // ------------------------------------------------------------------ automatic backups

    /**
     * Turns automatic backups on into [folder] (from the system folder picker), keeping the right
     * to write there across restarts, and writes the first backup right away. False if it failed.
     */
    suspend fun setAutoBackupFolder(folder: Uri): Boolean = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val taken = runCatching { context.contentResolver.takePersistableUriPermission(folder, flags) }.isSuccess
        if (!taken) return@withContext false
        val previous = dataStore.data.first()[AUTO_FOLDER].orEmpty()
        if (previous.isNotEmpty() && previous != folder.toString()) releaseFolder(previous)
        dataStore.edit { it[AUTO_FOLDER] = folder.toString() }
        autoBackup(force = true)
    }

    /** Turns automatic backups off. The backups already written stay where they are. */
    suspend fun clearAutoBackupFolder() = withContext(Dispatchers.IO) {
        val previous = dataStore.data.first()[AUTO_FOLDER].orEmpty()
        if (previous.isNotEmpty()) releaseFolder(previous)
        dataStore.edit {
            it.remove(AUTO_FOLDER)
            it.remove(AUTO_LAST)
        }
    }

    private fun releaseFolder(uri: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), flags) }
    }

    /** Called when the home screen goes to the background: backs up once a day when turned on. */
    fun autoBackupSoon() {
        scope.launch { runCatching { autoBackup(force = false) } }
    }

    private suspend fun autoBackup(force: Boolean): Boolean = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val folder = prefs[AUTO_FOLDER]?.takeIf { it.isNotEmpty() } ?: return@withContext false
        val last = prefs[AUTO_LAST] ?: 0L
        val now = System.currentTimeMillis()
        if (!force && (now - last) in 0L until AUTO_INTERVAL_MS) return@withContext false
        val ok = mutex.withLock {
            runCatching {
                val bytes = buildBackup()
                val file = autoBackupFile(Uri.parse(folder)) ?: error("No backup file")
                writeDocument(file, bytes)
            }.isSuccess
        }
        if (ok) dataStore.edit { it[AUTO_LAST] = now }
        ok
    }

    /** The automatic backup file in [tree]: the one written last time, or a new one. */
    private fun autoBackupFile(tree: Uri): Uri? {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == AUTO_FILE_NAME) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        return DocumentsContract.createDocument(resolver, parent, MIME_TYPE, AUTO_FILE_NAME)
    }

    // ------------------------------------------------------------------ restoring

    /** Replaces everything with the backup in [source]. */
    suspend fun restoreFrom(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val backup = runCatching {
                context.contentResolver.openInputStream(source)?.use { readBackup(it) }
            }.getOrNull() ?: return@withLock RestoreResult.NOT_A_BACKUP
            val manifest = backup.first
            if (manifest.optString("format") != FORMAT) return@withLock RestoreResult.NOT_A_BACKUP
            if (manifest.optInt("version", 0) > VERSION) return@withLock RestoreResult.TOO_NEW
            val restored = runCatching {
                restoreFiles(backup.second)
                restoreSettings(manifest)
                restoreHomeItems(manifest.optJSONArray("homeItems") ?: JSONArray())
            }.isSuccess
            if (restored) {
                _restoreGeneration.value++
                RestoreResult.RESTORED
            } else {
                RestoreResult.FAILED
            }
        }
    }

    /** The manifest and the kept files of a backup; null when it is not one. */
    private fun readBackup(input: InputStream): Pair<JSONObject, Map<String, ByteArray>>? {
        var manifest: JSONObject? = null
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val bytes = zip.readBytes()
                total += bytes.size
                if (total > MAX_BACKUP_BYTES) return null
                val name = entry.name
                if (name == MANIFEST_ENTRY) {
                    manifest = JSONObject(String(bytes, Charsets.UTF_8))
                } else if (name.startsWith(FILES_DIR_ENTRY)) {
                    val fileName = name.removePrefix(FILES_DIR_ENTRY)
                    // Only plain names of our own files: nothing in a backup may write elsewhere.
                    val safe = fileName.isNotEmpty() && '/' !in fileName && '\\' !in fileName &&
                        ".." !in fileName && FILE_PREFIXES.any { fileName.startsWith(it) }
                    if (safe) files[fileName] = bytes
                }
            }
        }
        return manifest?.let { it to files }
    }

    private fun restoreFiles(files: Map<String, ByteArray>) {
        val dir = context.filesDir
        files.forEach { (name, bytes) ->
            val temp = File(dir, "$name.restoring")
            temp.writeBytes(bytes)
            val target = File(dir, name)
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
        }
        // The pictures this install had and the backup doesn't are no longer used.
        backupFiles().filter { it.name !in files.keys }.forEach { it.delete() }
    }

    private suspend fun restoreSettings(manifest: JSONObject) {
        val entries = manifest.optJSONArray("settings") ?: JSONArray()
        // A picture's stored path names the old install's folder; point it at this one.
        val oldDir = manifest.optString("filesDir")
        val newDir = context.filesDir.absolutePath
        fun path(value: String): String =
            if (oldDir.isNotEmpty() && oldDir != newDir && value.startsWith("$oldDir/")) newDir + value.substring(oldDir.length) else value

        dataStore.edit { p ->
            val keep = p.asMap().filterKeys { it.name in DEVICE_LOCAL_KEYS }
            p.clear()
            for ((key, value) in keep) {
                @Suppress("UNCHECKED_CAST")
                p[key as Preferences.Key<Any>] = value
            }
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val name = e.optString("k")
                if (name.isEmpty() || name in DEVICE_LOCAL_KEYS) continue
                runCatching {
                    when (e.optString("t")) {
                        "b" -> p[booleanPreferencesKey(name)] = e.getBoolean("v")
                        "i" -> p[intPreferencesKey(name)] = e.getInt("v")
                        "l" -> p[longPreferencesKey(name)] = e.getLong("v")
                        "f" -> p[floatPreferencesKey(name)] = e.getDouble("v").toFloat()
                        "d" -> p[doublePreferencesKey(name)] = e.getDouble("v")
                        "s" -> p[stringPreferencesKey(name)] = path(e.getString("v"))
                        "ss" -> {
                            val array = e.getJSONArray("v")
                            p[stringSetPreferencesKey(name)] = (0 until array.length()).map { array.getString(it) }.toSet()
                        }
                        "ba" -> p[byteArrayPreferencesKey(name)] = Base64.decode(e.getString("v"), Base64.NO_WRAP)
                    }
                    Unit
                }
            }
            // A restored launcher is set up: no first-run intro and no default layout on top.
            p[booleanPreferencesKey(KEY_ONBOARDING_DONE)] = true
            p[booleanPreferencesKey(KEY_DEFAULT_LAYOUT_SEEDED)] = true
            p[stringPreferencesKey(KEY_FIRST_RUN_FRESH)] = "no"
        }
    }

    private fun restoreHomeItems(rows: JSONArray) {
        val db = database.openHelper.writableDatabase
        // Only the columns this version has: a backup from an older version simply leaves the
        // newer columns at their defaults.
        val columns = HashSet<String>()
        db.query("PRAGMA table_info($HOME_TABLE)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) columns.add(c.getString(nameIndex))
        }
        database.runInTransaction(
            Runnable {
            db.execSQL("DELETE FROM $HOME_TABLE")
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val values = ContentValues()
                for (column in row.keys()) {
                    if (column !in columns) continue
                    when (val value = row.get(column)) {
                        JSONObject.NULL -> values.putNull(column)
                        is Int -> values.put(column, value.toLong())
                        is Long -> values.put(column, value)
                        is Double -> values.put(column, value)
                        is Boolean -> values.put(column, if (value) 1L else 0L)
                        is String -> values.put(column, value)
                        else -> values.put(column, value.toString())
                    }
                }
                if (values.size() > 0) db.insert(HOME_TABLE, SQLiteDatabase.CONFLICT_IGNORE, values)
            }
            },
        )
    }

    companion object {
        /** What the system file picker should offer and save as. */
        const val MIME_TYPE = "application/zip"

        /** The name automatic backups are kept under (always the same file, replaced each day). */
        const val AUTO_FILE_NAME = "Folark-backup.zip"

        private const val FORMAT = "folark-backup"
        private const val VERSION = 1
        private const val MANIFEST_ENTRY = "folark.json"
        private const val FILES_DIR_ENTRY = "files/"
        private const val HOME_TABLE = "home_items"
        private const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
        private const val AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000

        private val FILE_PREFIXES = listOf("home_background_")

        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_DEFAULT_LAYOUT_SEEDED = "default_layout_seeded"
        private const val KEY_FIRST_RUN_FRESH = "first_run_fresh"
        private const val KEY_AUTO_FOLDER = "backup_auto_folder"
        private const val KEY_AUTO_LAST = "backup_auto_last"

        private val AUTO_FOLDER = stringPreferencesKey(KEY_AUTO_FOLDER)
        private val AUTO_LAST = longPreferencesKey(KEY_AUTO_LAST)

        /** Settings that only mean something on this install. */
        private val DEVICE_LOCAL_KEYS = setOf(
            "people_held_notifications",
            KEY_ONBOARDING_DONE,
            KEY_DEFAULT_LAYOUT_SEEDED,
            KEY_FIRST_RUN_FRESH,
            KEY_AUTO_FOLDER,
            KEY_AUTO_LAST,
        )
    }
}
