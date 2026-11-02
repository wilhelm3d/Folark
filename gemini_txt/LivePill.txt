package org.arkikeskus.launcher.ui.component

import android.graphics.drawable.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LiveActivity

/**
 * One live activity as a pill: the app's status icon, the few characters that matter (a ticking
 * timer, an ETA, a percentage) and the title, over the chosen [effect]. [animate] false holds the
 * effect still (battery saver, home covered). Tap opens it, long press calls [onLongPress].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LivePill(
    activity: LiveActivity,
    effect: String,
    animate: Boolean,
    light: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: Int = 0,
    onLongPress: (() -> Unit)? = null,
    height: Dp = 36.dp,
) {
    val context = LocalContext.current
    val painter = remember { LivePillPainter() }
    val accent = activity.color ?: LivePillPainter.FALLBACK_ACCENT
    val icon = remember(activity.key, activity.icon != null) { activity.icon?.bitmap(context) }
    // The effect's clock: read only while drawing, so a frame redraws the pill and nothing else.
    var t by remember { mutableFloatStateOf(0f) }
    val moving = animate && LivePillPainter.animates(effect)
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        val start = System.nanoTime() - (t * 1e9f).toLong()
        while (true) {
            androidx.compose.runtime.withFrameNanos { now -> t = (now - start) / 1e9f }
            delay(FRAME_MS)
        }
    }
    // A running clock: the text changes once a second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activity.ticks, animate) {
        if (!activity.ticks) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    val ink = if (light) Color(0xFF1B1B1F) else Color.White
    val glowPad = 6.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .height(height + glowPad * 2)
            .widthIn(max = 280.dp)
            .drawBehind {
                drawIntoCanvas { canvas ->
                    painter.draw(
                        canvas.nativeCanvas, size.width, size.height, glowPad.toPx(), effect, t, accent,
                        activity.progress, activity.indeterminate, light,
                    )
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = glowPad + 12.dp, vertical = glowPad),
    ) {
        if (icon != null) {
            Image(
                icon,
                contentDescription = null,
                // The app's colour, unless it would vanish against the pill.
                colorFilter = ColorFilter.tint(Color(accent).let { if (light == (it.luminance() > 0.5f)) ink else it }),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            activity.shortText(now),
            color = ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (activity.ticks) FontFamily.Monospace else null,
            maxLines = 1,
        )
        val title = activity.title.takeIf { it.isNotBlank() && it != activity.shortText(now) }
        if (title != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = ink.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (extra > 0) {
            Spacer(Modifier.width(8.dp))
            Text("+$extra", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun Icon.bitmap(context: android.content.Context): ImageBitmap? = runCatching {
    loadDrawable(context)?.squareBitmap(48)?.asImageBitmap()
}.getOrNull()

/** With the frame wait, about 30 frames a second: plenty for these slow effects. */
private const val FRAME_MS = 16L

/** A drawable drawn into a [size]-pixel square (no androidx.core needed here). */
internal fun android.graphics.drawable.Drawable.squareBitmap(size: Int): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap
}
