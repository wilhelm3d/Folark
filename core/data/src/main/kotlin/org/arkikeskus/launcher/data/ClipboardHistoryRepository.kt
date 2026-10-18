package org.arkikeskus.launcher.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One thing that was copied, and when Folark first saw it. */
data class ClipEntry(val text: String, val time: Long)

/**
 * The clipboard history behind the clipboard widget. Android lets only the app in front read the
 * clipboard, so Folark looks when home comes back into view ([capture], from the launcher's window
 * focus) and while it is in front (a clipboard listener). Several copies made in another app before
 * returning home count as one: the last.
 *
 * Only while a clipboard widget is on screen ([attach]/[detach]); nothing is read otherwise.
 * Clips Android marks as sensitive (passwords, one-time codes) are never kept. The list lives in a
 * private file on the device, never in backups, and is cleared from the widget.
 */
@Singleton
class ClipboardHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val file = File(context.filesDir, FILE_NAME)

    private val _clips = MutableStateFlow<List<ClipEntry>>(emptyList())
    val clips: StateFlow<List<ClipEntry>> = _clips

    private var widgets = 0
    private var loaded = false
    /** Timestamp of the clip last looked at: the same clip is not read twice (each read of another
     *  app's clip shows Android's "pasted from your clipboard" notice). */
    private var lastSeen = Long.MIN_VALUE

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    /** A clipboard widget appeared: load the history, start following the clipboard, look once. */
    fun attach() {
        widgets++
        if (widgets == 1) {
            load()
            runCatching { clipboard?.addPrimaryClipChangedListener(listener) }
        }
        capture()
    }

    /** A clipboard widget went away; with none left, the clipboard is no longer looked at. */
    fun detach() {
        widgets = (widgets - 1).coerceAtLeast(0)
        if (widgets == 0) runCatching { clipboard?.removePrimaryClipChangedListener(listener) }
    }

    /** Adds the current clip, if it is new text and not sensitive. Call when Folark is in front. */
    fun capture() {
        if (widgets == 0) return
        val cm = clipboard ?: return
        runCatching {
            val description = cm.primaryClipDescription ?: return
            val stamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) description.timestamp else 0L
            if (stamp != 0L && stamp == lastSeen) return
            lastSeen = stamp
            if (isSensitive(description)) return
            if (!description.hasMimeType("text/*")) return
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!text.isNullOrEmpty()) add(text)
        }
    }

    /** Puts [entry] back on the clipboard and at the top of the list. */
    fun copy(entry: ClipEntry) {
        val cm = clipboard ?: return
        runCatching {
            cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, entry.text))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                lastSeen = cm.primaryClipDescription?.timestamp ?: lastSeen
            }
        }
        add(entry.text)
    }

    fun remove(entry: ClipEntry) {
        _clips.value = _clips.value.filterNot { it == entry }
        save()
    }

    fun clear() {
        _clips.value = emptyList()
        save()
    }

    private fun isSensitive(description: ClipDescription): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private fun add(text: String) {
        val kept = text.take(MAX_CHARS)
        _clips.value = (listOf(ClipEntry(kept, System.currentTimeMillis())) + _clips.value.filterNot { it.text == kept })
            .take(MAX_CLIPS)
        save()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val stored = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ClipEntry(o.getString("t"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
        // Clips captured before the file was read stay on top.
        _clips.value = (_clips.value + stored.filterNot { s -> _clips.value.any { it.text == s.text } }).take(MAX_CLIPS)
    }

    private fun save() {
        scope.launch(Dispatchers.IO) {
            // The newest list at write time: saves that run late or out of order never undo a change.
            runCatching {
                synchronized(file) { write(_clips.value) }
            }
        }
    }

    private fun write(snapshot: List<ClipEntry>) {
        val array = JSONArray()
        snapshot.forEach { array.put(JSONObject().put("t", it.text).put("at", it.time)) }
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "clipboard_history.json"
        const val CLIP_LABEL = "Folark"
        const val MAX_CLIPS = 30
        const val MAX_CHARS = 4_000
    }
}
