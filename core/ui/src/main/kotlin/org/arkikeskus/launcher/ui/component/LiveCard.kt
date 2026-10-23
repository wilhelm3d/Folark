package org.arkikeskus.launcher.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LiveActivity
import kotlin.math.abs

/**
 * A live activity opened up (a long press on its pill): everything a glance needs without opening
 * the app — the app, the title and text, a big ticking clock / ETA / percentage, a full progress
 * bar, and the notification's own buttons (pause, stop, cancel...) plus Open. With several going
 * on at once, a swipe sideways moves between them ([count] dots at the bottom).
 */
@Composable
fun LiveCard(
    activity: LiveActivity,
    onOpen: () -> Unit,
    onAction: (LiveActivity.Action) -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    onSwitch: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val accent = Color(activity.color ?: LivePillPainter.FALLBACK_ACCENT)
    val shown = if (accent.luminanceOf() < 0.18f) Color(0xFF8FD3FF) else accent
    val icon = remember(activity.key, activity.icon != null) {
        runCatching { activity.icon?.loadDrawable(context)?.squareBitmap(64)?.asImageBitmap() }.getOrNull()
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activity.ticks) {
        if (!activity.ticks) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    val progress by animateFloatAsState(activity.progress ?: 0f, tween(500), label = "progress")
    val busy = rememberInfiniteTransition(label = "busy")
    val sweep by busy.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep")
    var dragged by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .graphicsLayer { shadowElevation = 28f; this.shape = shape; clip = true }
            .background(Color(0xF0121A1D))
            .drawBehind {
                // A soft glow of the app's colour along the top.
                drawRect(Brush.verticalGradient(listOf(shown.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.6f))
            }
            .then(
                if (onSwitch != null && count > 1) {
                    Modifier.pointerInput(index, count) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                if (abs(dragged) > 60.dp.toPx()) onSwitch(((index + if (dragged < 0) 1 else -1) % count + count) % count)
                            },
                        ) { _, d -> dragged += d }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) {
                Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(shown), modifier = Modifier.size(22.dp))
            }
            Text(
                activity.appLabel.ifBlank { activity.packageName },
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            activity.subText?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
            }
        }
        // The big readout: a running clock, an ETA, a percentage.
        val big = activity.shortText(now).takeIf { it != activity.title && it != activity.text }
        if (big != null) {
            Text(
                big, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = if (activity.ticks || activity.progress != null) FontFamily.Monospace else null, maxLines = 1,
            )
        }
        Text(activity.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val body = activity.bigText?.takeIf { it.isNotBlank() } ?: activity.text
        if (body.isNotBlank() && body != activity.title) {
            Text(body, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (activity.progress != null || activity.indeterminate) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .drawBehind {
                        val r = CornerRadius(size.height / 2)
                        if (activity.indeterminate) {
                            val w = size.width * 0.3f
                            drawRoundRect(shown, Offset(-w + (size.width + w) * sweep, 0f), Size(w, size.height), r)
                        } else {
                            drawRoundRect(shown, Offset.Zero, Size(size.width * progress, size.height), r)
                        }
                    },
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            activity.actions.take(3).forEach { action ->
                CardButton(action.title, filled = false, accent = shown) { onAction(action) }
            }
            Spacer(Modifier.weight(1f))
            CardButton(LiveActivity.OPEN_LABEL, filled = true, accent = shown, onClick = onOpen)
        }
        if (count > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardButton(text: String, filled: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) accent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text, color = if (filled) (if (accent.luminanceOf() > 0.55f) Color(0xFF101418) else Color.White) else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

private fun Color.luminanceOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
