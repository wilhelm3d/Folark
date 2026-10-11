package org.arkikeskus.launcher.feature.home

import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small notification icons, kept once drawn. An app's icon is usually a resource in its own APK:
 * reading it opens that APK, so it is done off the main thread, and a re-posted notification with
 * the same icon draws straight from memory.
 */
private val notificationIconCache = LruCache<String, ImageBitmap>(64)

private fun notificationIconKey(icon: Icon, id: String, px: Int): String =
    if (icon.type == Icon.TYPE_RESOURCE) "res:" + icon.resPackage + ":" + icon.resId + ":" + px else "id:" + id + ":" + px

/**
 * [icon] rasterised at [px]; null while it loads and when it can't be read. [id] names the
 * notification (key and post time) for icons that aren't app resources.
 */
@Composable
internal fun rememberNotificationIcon(icon: Icon?, id: String, px: Int): ImageBitmap? {
    if (icon == null || px <= 0) return null
    val context = LocalContext.current
    val cacheKey = remember(icon, id, px) { notificationIconKey(icon, id, px) }
    var bitmap by remember(cacheKey) { mutableStateOf(notificationIconCache.get(cacheKey)) }
    if (bitmap == null) {
        LaunchedEffect(cacheKey) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { icon.loadDrawable(context)?.toBitmap(width = px, height = px)?.asImageBitmap() }.getOrNull()
            }
            if (loaded != null) notificationIconCache.put(cacheKey, loaded)
            bitmap = loaded
        }
    }
    return bitmap
}
