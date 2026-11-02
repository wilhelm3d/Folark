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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.data.di.ApplicationScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One thing that was copied, and when Folark first saw it. [image]: a copied picture, kept as
 *  this file name in Folark's private clips folder ([text] is then empty). */
data class ClipEntry(val text: String, val time: Long, val image: String? = null) {
    val kind: ClipKind get() = ClipKind.of(this)
}

/** What a clip is, for the actions offered with it. */
enum class ClipKind {
    LINK, PHONE, EMAIL, TEXT, IMAGE;

    companion object {
        fun of(clip: ClipEntry): ClipKind {
            if (clip.image != null) return IMAGE
            val s = clip.text.trim()
            if (s.contains('\n') || s.length > 300) return TEXT
            return when {
                android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches() -> EMAIL
                android.util.Patterns.WEB_URL.matcher(s).matches() && (s.contains('.') || s.startsWith("http")) -> LINK
                looksLikePhone(s) -> PHONE
                else -> TEXT
            }
        }

        /** 7–15 digits written like a phone number (+, spaces, dashes, brackets), not a date. */
        private fun looksLikePhone(s: String): Boolean {
            val digits = s.count { it.isDigit() }
            if (digits !in 7..15) return false
            if (!s.all { it.isDigit() || it in "+-() ./" }) return false
            if (DATE.matches(s)) return false
            return android.util.Patterns.PHONE.matcher(s).matches()
        }

        private val DATE = Regex("""\d{1,4}[-./]\d{1,2}[-./]\d{1,4}""")

        /** [text] as a link to open (adds https:// when it was copied without one). */
        fun url(text: String): String {
            val s = text.trim()
            return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "https://$s"
        }
    }
}

/**
 * The clipboard history behind the clipboard widget. Android lets only the app in front read the
 * clipboard, so Folark looks when home comes back into view ([capture], from the launcher's window
 * focus) and while it is in front (a clipboard listener). Several copies made in another app before
 * returning home count as one: the last.
 *
 * Only while a clipboard widget is on screen or the app drawer's clipboard row is switched on
 * ([attach]/[detach]); nothing is read otherwise. Copied pictures are kept as a small private copy.
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
    /** Copied pictures, one small JPEG each (never in backups: filesDir/clips isn't backed up). */
    private val imageDir = File(context.filesDir, IMAGE_DIR)

    /** A copied picture's file, for showing it. */
    fun imageFile(entry: ClipEntry): File? = entry.image?.let { File(imageDir, it) }?.takeIf { it.exists() }

    /** A link other apps can open a copied picture by (Folark's file provider; read-only). */
    fun imageUri(entry: ClipEntry): android.net.Uri? = imageFile(entry)?.let {
        runCatching { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}$PROVIDER_SUFFIX", it) }.getOrNull()
    }

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
            if (description.hasMimeType("image/*")) {
                // A copied picture (a screenshot, an image from the browser): kept as a small copy,
                // since the app it came from only lets Folark read it for a moment.
                val uri = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
                scope.launch(Dispatchers.IO) { saveImage(uri)?.let { addImage(it) } }
                return
            }
            if (!description.hasMimeType("text/*")) return
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!text.isNullOrEmpty()) add(text)
        }
    }

    /** Puts [entry] back on the clipboard (ready to paste anywhere) and at the top of the list. */
    fun copy(entry: ClipEntry) {
        val cm = clipboard ?: return
        runCatching {
            val clip = if (entry.image != null) {
                ClipData.newUri(context.contentResolver, CLIP_LABEL, imageUri(entry) ?: return)
            } else {
                ClipData.newPlainText(CLIP_LABEL, entry.text)
            }
            cm.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                lastSeen = cm.primaryClipDescription?.timestamp ?: lastSeen
            }
        }
        if (entry.image != null) addImage(entry.image) else add(entry.text)
    }

    fun remove(entry: ClipEntry) {
        _clips.update { list -> list.filterNot { it == entry } }
        save()
        deleteUnusedImages()
    }

    fun clear() {
        _clips.value = emptyList()
        save()
        deleteUnusedImages()
    }

    /** Reads the picture at [uri] (while Android allows it) into a small private JPEG; its name. */
    private fun saveImage(uri: android.net.Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        if (edge <= 0) return@runCatching null
        var sample = 1
        while (edge / (sample * 2) >= IMAGE_MAX_EDGE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        imageDir.mkdirs()
        val name = "clip_${System.currentTimeMillis()}.jpg"
        File(imageDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        name
    }.getOrNull()

    private fun addImage(name: String) {
        // update {}: pictures are added from a background thread, texts from the main one.
        _clips.update { list -> (listOf(ClipEntry("", System.currentTimeMillis(), name)) + list.filterNot { it.image == name }).take(MAX_CLIPS) }
        save()
        deleteUnusedImages()
    }

    /** Pictures no longer in the list (removed, cleared, or pushed out by newer clips) are deleted. */
    private fun deleteUnusedImages() {
        scope.launch(Dispatchers.IO) {
            val keep = _clips.value.mapNotNull { it.image }.toSet()
            // A picture saved a moment ago may not be in the list yet: those are left alone.
            val settled = System.currentTimeMillis() - FRESH_MS
            imageDir.listFiles()?.filter { it.name !in keep && it.lastModified() < settled }?.forEach { runCatching { it.delete() } }
        }
    }

    private fun isSensitive(description: ClipDescription): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private fun add(text: String) {
        val kept = text.take(MAX_CHARS)
        _clips.update { list -> (listOf(ClipEntry(kept, System.currentTimeMillis())) + list.filterNot { it.image == null && it.text == kept }).take(MAX_CLIPS) }
        save()
        deleteUnusedImages()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val stored = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ClipEntry(o.getString("t"), o.optLong("at"), o.optString("img").takeIf { it.isNotEmpty() })
            }
        }.getOrDefault(emptyList())
        // Clips captured before the file was read stay on top.
        _clips.update { now -> (now + stored.filterNot { s -> now.any { it.text == s.text && it.image == s.image } }).take(MAX_CLIPS) }
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
        snapshot.forEach { c ->
            array.put(JSONObject().put("t", c.text).put("at", c.time).apply { c.image?.let { put("img", it) } })
        }
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
        const val IMAGE_DIR = "clips"
        const val FRESH_MS = 60_000L
        const val IMAGE_MAX_EDGE = 1280
        /** Folark's file provider for copied pictures (declared in the app's manifest). */
        const val PROVIDER_SUFFIX = ".clips"
    }
}
