package org.arkikeskus.launcher.feature.home.taskbar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.catching

/**
 * Drop-to-share on the taskbar (split out of AppTaskbarBar): content dragged out of another app —
 * text, a link, files — dropped on one of the bar's icons opens THAT app with the content, via an
 * ACTION_SEND/ACTION_VIEW restricted to the app's own targets. Works across apps because the
 * system dispatches drag events to whatever window is under the finger, including Folark's
 * overlay windows.
 *
 * Permissions: files are copied while the drop may read them and handed on from Folark's own
 * share folder (see [shareDropTo]).
 */

/** The app whose icon sits at [x]/[y] in [row]'s coordinates, or null. */
internal fun AppTaskbar.findIconUnder(row: View, x: Float, y: Float): AppItem? {
    val rowLocation = IntArray(2)
    row.getLocationInWindow(rowLocation)
    for ((badgeKey, view) in iconViews) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = location[0] - rowLocation[0]
        val top = location[1] - rowLocation[1]
        if (x >= left && x <= left + view.width && y >= top && y <= top + view.height) {
            return appForBadgeKey(badgeKey)
        }
    }
    return null
}

/** The [AppItem] an icon stands for, from its badge key ("package/userSerial"). */
private fun AppTaskbar.appForBadgeKey(badgeKey: String): AppItem? =
    setup?.pinned?.firstOrNull { it.badgeKey == badgeKey }
        ?: recents.firstOrNull { it.badgeKey == badgeKey }

/**
 * Opens [app]'s own targets with [clip]'s content: a single http(s) link as ACTION_VIEW, files as
 * ACTION_SEND (or ACTION_SEND_MULTIPLE for several), text as ACTION_SEND with EXTRA_TEXT.
 *
 * Files from another app: Folark may read them only while the drop is being handled (its window is
 * not an activity, so it cannot ask to keep that access), and it cannot pass on an access it does
 * not keep. So each file is opened right here, during the drop, copied into Folark's own share
 * folder in the background, and handed to [app] from there. A file its app does not let go of
 * gets a message that says so.
 */
internal fun AppTaskbar.shareDropTo(app: AppItem, clip: ClipData?): Boolean {
    clip ?: return false
    val context = this.context
    var text: String? = null
    val uris = ArrayList<Uri>()
    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        val uri = item.uri
        if (uri != null) uris += uri else item.text?.let { text = it.toString() }
    }
    val single = uris.firstOrNull()
    // A link (or plain text): nothing to read, it goes straight on.
    if (uris.isEmpty() || (uris.size == 1 && text == null && (single?.scheme == "http" || single?.scheme == "https"))) {
        val intent = if (single != null) Intent(Intent.ACTION_VIEW, single)
        else Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text.orEmpty()).setType("text/plain")
        return startShare(intent.setPackage(app.packageName))
    }
    // Files: opened now, while the drop still lets Folark read them.
    val resolver = context.contentResolver
    val opened = uris.take(MAX_DROP_FILES).mapNotNull { uri ->
        val stream = catching { resolver.openInputStream(uri) }.getOrNull() ?: return@mapNotNull null
        val name = catching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"
        val mime = catching { resolver.getType(uri) }.getOrNull()
        DroppedFile(stream, safeName(name), mime)
    }
    if (opened.isEmpty()) {
        Toast.makeText(context, R.string.taskbar_share_locked, Toast.LENGTH_SHORT).show()
        return false
    }
    val clipMime = clip.description?.getMimeType(0)
    scope.launch {
        val shared = withContext(Dispatchers.IO) { copyToShareFolder(context, opened) }
        if (shared.isEmpty()) {
            Toast.makeText(context, R.string.taskbar_share_locked, Toast.LENGTH_SHORT).show()
            return@launch
        }
        val mimes = opened.mapNotNull { it.mime }.distinct()
        val type = when {
            mimes.size == 1 -> mimes.first()
            mimes.isNotEmpty() -> mimes.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
            !clipMime.isNullOrBlank() -> clipMime
            else -> "*/*"
        }
        val intent = if (shared.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, shared.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shared))
        }
        intent.type = type
        if (text != null) intent.putExtra(Intent.EXTRA_TEXT, text)
        startShare(intent.setPackage(app.packageName))
    }
    return true
}

private class DroppedFile(val stream: InputStream, val name: String, val mime: String?)

/** Copies [files] into the share folder (emptied first: only the last drop is kept) and returns
 *  Folark's own URIs for them; each stream is closed. A file over [MAX_DROP_BYTES] is left out. */
private fun copyToShareFolder(context: Context, files: List<DroppedFile>): List<Uri> {
    val dir = File(context.cacheDir, DROP_DIR)
    catching { dir.deleteRecursively() }
    dir.mkdirs()
    val authority = context.packageName + ".clips"
    return files.mapIndexedNotNull { i, f ->
        // Each in its own numbered folder, so two files of the same name do not overwrite each other.
        val target = File(File(dir, i.toString()).apply { mkdirs() }, f.name)
        val ok = catching {
            f.stream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_DROP_BYTES) error("too big")
                        out.write(buffer, 0, n)
                    }
                }
            }
        }.isSuccess
        if (!ok) {
            target.delete()
            null
        } else {
            catching { FileProvider.getUriForFile(context, authority, target) }.getOrNull()
        }
    }
}

/** A file name safe to create (no folders, nothing hidden, not empty). */
private fun safeName(name: String): String =
    name.substringAfterLast('/').replace(Regex("[\\\\:*?\"<>|\\u0000]"), "_").trimStart('.').take(120).ifEmpty { "file" }

private fun AppTaskbar.startShare(intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return catching {
        context.startActivity(intent)
        true
    }.getOrElse {
        // The app has no target for this content: say so rather than nothing happening.
        Toast.makeText(context, R.string.taskbar_share_failed, Toast.LENGTH_SHORT).show()
        false
    }
}

/** Folark's share folder in its cache (served by the ".clips" FileProvider, see clip_paths.xml). */
private const val DROP_DIR = "drops"
private const val MAX_DROP_FILES = 10
private const val MAX_DROP_BYTES = 200L * 1024 * 1024
