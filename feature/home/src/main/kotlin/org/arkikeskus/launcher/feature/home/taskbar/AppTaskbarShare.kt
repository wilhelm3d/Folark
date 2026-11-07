package org.arkikeskus.launcher.feature.home.taskbar

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import org.arkikeskus.launcher.model.AppItem

/**
 * Drop-to-share on the taskbar (split out of AppTaskbarBar): content dragged out of another app —
 * text, a link, files — dropped on one of the bar's icons opens THAT app with the content, via an
 * ACTION_SEND/ACTION_VIEW restricted to the app's own targets. Works across apps because the
 * system dispatches drag events to whatever window is under the finger, including Folark's
 * overlay windows.
 *
 * Permissions: content URIs arrive carrying the drag's grant flags; they are forwarded with
 * FLAG_GRANT_READ_URI_PERMISSION so the receiving app may read them.
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
    val intent = when {
        uris.size == 1 && text == null && (single?.scheme == "http" || single?.scheme == "https") ->
            Intent(Intent.ACTION_VIEW, single)
        uris.size == 1 -> Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, single)
        uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(
            Intent.EXTRA_STREAM, ArrayList(uris),
        )
        else -> Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text.orEmpty())
    }.setPackage(app.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (intent.action == Intent.ACTION_SEND) {
        val mime = clip.description?.getMimeType(0)
        if (!mime.isNullOrBlank()) intent.type = mime else intent.setType("text/plain")
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        // The app may have no target for this content: say so rather than nothing happening.
        Toast.makeText(context, org.arkikeskus.launcher.feature.home.R.string.taskbar_share_failed, Toast.LENGTH_SHORT).show()
        false
    }
}
