package org.arkikeskus.launcher.data.search

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.model.catching
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files and media on the phone, found by name through MediaStore (Settings ▸ App drawer ▸ Search ▸
 * Files). Only the index is read — nothing is opened or uploaded. Off unless the user turned it on
 * and granted a read permission; a missing permission leaves the search empty rather than failing.
 *
 * What MediaStore shows depends on the access: the media permissions show pictures, videos and
 * sounds; documents (PDFs, texts, downloads) of other apps only appear with "All files access"
 * (Android 11+), which the user can add from the same setting.
 */
@Singleton
class FileSearchProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : SearchProvider {

    override suspend fun isEnabled(): Boolean =
        settingsRepository.settings(ScreenType.OUTER).first().search.files

    override suspend fun query(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        // The query's own % and _ are matched as typed, not as wildcards.
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val args = arrayOf("%$escaped%")
        val found = ArrayList<SearchResult>()
        catching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                while (cursor.moveToNext() && found.size < MAX_RESULTS) {
                    val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
                    val mime = cursor.getString(mimeColumn) ?: continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    found += SearchResult.Action(
                        kind = SearchResult.Action.FILE,
                        label = name.take(72),
                        detail = FILE_DETAIL.format(mime.substringAfterLast('/').ifEmpty { mime }),
                        args = mapOf("uri" to uri.toString(), "mime" to mime, "name" to name),
                    )
                }
            }
        }
        found
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val FILE_DETAIL = "File · %s"
    }
}
