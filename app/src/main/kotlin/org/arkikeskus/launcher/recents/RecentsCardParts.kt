package org.arkikeskus.launcher.recents

import org.arkikeskus.launcher.data.LaunchOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.R
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.IconRequest
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RecentsAction
import org.arkikeskus.launcher.model.RecentsAttachedSide
import org.arkikeskus.launcher.model.RecentsCardLayout
import org.arkikeskus.launcher.ui.component.pressClick
import org.arkikeskus.launcher.ui.component.PressStyle
import org.arkikeskus.launcher.ui.component.LocalPressStyle
import androidx.compose.runtime.CompositionLocalProvider
import org.arkikeskus.launcher.ui.component.rememberRecentsBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

// The small pieces Recents cards are built from (split out of RecentsScreen.kt).

// ---------------------------------------------------------------------------- small pieces

@Composable
internal fun CardTitle(card: RecentsCard, env: RecentsEnv, iconSize: Dp = 32.dp, showClose: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            card.apps.forEach { app -> AppBadge(app, iconSize) }
        }
        Column(Modifier.weight(1f)) {
            Text(card.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (card.isSplit) add(stringResource(R.string.recents_split_card))
                if (env.settings.recents.showTime) add(timeLabel(LocalContext.current, card.inactiveMs))
            }.filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
        }
        CardTrailing(card, env, showClose)
    }
}

/** A picked card's slot, "Add" while picking, or ✕ (with root). */
@Composable
internal fun CardTrailing(card: RecentsCard, env: RecentsEnv, showClose: Boolean = true) {
    val slot = env.slotLabel(card)
    when {
        slot != null -> Box(Modifier.clip(RoundedCornerShape(999.dp)).background(env.accent).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(stringResource(slot), color = env.onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        env.selecting && !card.isSplit -> Box(
            Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, env.accent, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text(stringResource(R.string.recents_add), color = env.accent, fontSize = 12.sp) }
        !env.selecting -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val chip = if (env.glass) Color.White.copy(alpha = 0.16f) else Chip
            if (env.settings.recents.btnPin) {
                Box(
                    Modifier.size(40.dp)
                        .pressClick { env.onTogglePin(card) }
                        .clip(CircleShape)
                        .background(if (card.pinned) env.accent else chip),
                    contentAlignment = Alignment.Center,
                ) { PinGlyph(if (card.pinned) env.onAccent else Color(0xFFC9D6D8)) }
            } else if (card.pinned) {
                PinGlyph(env.accent)
            }
            // A pinned app isn't closed from here (unpin it first).
            if (env.canClose && showClose && env.settings.recents.btnClose && !card.pinned) {
                Box(
                    Modifier.size(40.dp).pressClick { env.onClose(card) }.clip(CircleShape).background(chip),
                    contentAlignment = Alignment.Center,
                ) { CrossGlyph(Color(0xFFC9D6D8)) }
            }
        }
    }
}

/** A pin: a round head on a short needle, leaning. */
@Composable
internal fun PinGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        drawLine(color, Offset(w * 0.52f, w * 0.48f), Offset(w * 0.12f, w * 0.88f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.26f, center = Offset(w * 0.64f, w * 0.36f))
    }
}

/**
 * Haptic ticks while cards scroll (R12, Settings ▸ Recents ▸ Tick while scrolling): one per
 * [TICK_STEP] scrolled, in any theme (they all scroll through nested scrolling).
 */
@Composable
internal fun rememberScrollTicks(on: Boolean): Modifier {
    if (!on) return Modifier
    val view = androidx.compose.ui.platform.LocalView.current
    val step = with(LocalDensity.current) { TICK_STEP.toPx() }
    val connection = remember(step) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var travelled = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset {
                travelled += kotlin.math.abs(consumed.x) + kotlin.math.abs(consumed.y)
                if (travelled >= step) {
                    travelled %= step
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                return Offset.Zero
            }
        }
    }
    return Modifier.nestedScroll(connection)
}

private val TICK_STEP = 140.dp

/**
 * A card's panes in screen order with their share of the width: from each task's laid-out
 * bounds when all have them (side by side), else in list order, equal.
 */
internal fun splitPanes(card: RecentsCard): List<Pair<Int, Float>> {
    val equal = card.tasks.indices.map { it to 1f }
    if (!card.isSplit) return equal
    val bounds = card.tasks.map { it.bounds ?: return equal }
    val sideBySide = bounds.map { it[0] }.distinct().size == bounds.size
    val sizes = bounds.map { b -> (if (sideBySide) b[2] - b[0] else b[3] - b[1]).coerceAtLeast(1) }
    val total = sizes.sum().toFloat()
    val order = card.tasks.indices.sortedBy { i -> if (sideBySide) bounds[i][0] else bounds[i][1] }
    // A peek strip stays wide enough to see.
    return order.map { i -> i to (sizes[i] / total).coerceAtLeast(MIN_PANE_SHARE) }
}

private const val MIN_PANE_SHARE = 0.07f

/** The card's picture: Android's screenshot of each app (split halves side by side), or its icon. */
@Composable
internal fun CardPicture(card: RecentsCard, env: RecentsEnv, modifier: Modifier, corner: Dp, iconSize: Dp = 64.dp) {
    Box(modifier) {
        // A split as it really is (R6): each app as wide as on screen (a third, peek, three apps),
        // left to right as placed; half and half when Android didn't say.
        val panes = remember(card.tasks) { splitPanes(card) }
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(if (card.isSplit) 4.dp else 6.dp)) {
            panes.forEach { (i, weight) ->
                val task = card.tasks[i]
                Thumb(env.thumb(task.taskId), card.apps.getOrNull(i), Modifier.weight(weight).fillMaxSize(), RoundedCornerShape(corner), env.glass, iconSize)
            }
        }
        // How much memory the card's apps use (Settings ▸ Recents ▸ Memory use on cards, root).
        if (env.memory.isNotEmpty() && !env.selecting) {
            val kb = card.tasks.map { it.packageName }.distinct().sumOf { env.memory[it] ?: 0L }
            if (kb > 0) {
                Text(
                    text = formatMemory(kb),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        // A speaker badge for the card's first app that is playing/paused audio (Settings ▸ Recents).
        if (env.speakerBadge && !env.selecting) {
            val pkg = card.tasks.firstOrNull { env.audioActive(it.packageName) }?.packageName
            if (pkg != null) {
                SpeakerBadge(
                    playing = env.audioPlaying(pkg),
                    onClick = { env.onToggleAudio(pkg) },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
    }
}

/** "312 MB" / "1.4 GB" from kilobytes. */
internal fun formatMemory(kb: Long): String {
    val mb = kb / 1024.0
    return if (mb >= 1000) String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0) else "${mb.toInt()} MB"
}

/** A round speaker button on a Recents card: pulses while the app is playing; a tap pauses/resumes it. */
@Composable
private fun SpeakerBadge(playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "speaker")
    val scale by if (playing) {
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.16f,
            animationSpec = infiniteRepeatable(tween(680), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .pressClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        SpeakerGlyph(playing, Color.White)
    }
}

/** A simple speaker drawn by hand (the app module has no icon pack): waves when playing, a slash when muted. */
@Composable
private fun SpeakerGlyph(playing: Boolean, color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.13f
        val body = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.26f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.80f)
            lineTo(w * 0.26f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.62f)
            close()
        }
        drawPath(body, color)
        if (playing) {
            drawArc(color, -50f, 100f, false, topLeft = Offset(w * 0.40f, h * 0.28f), size = Size(w * 0.26f, h * 0.44f), style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, -50f, 100f, false, topLeft = Offset(w * 0.46f, h * 0.14f), size = Size(w * 0.42f, h * 0.72f), style = Stroke(sw, cap = StrokeCap.Round))
        } else {
            drawLine(color, Offset(w * 0.60f, h * 0.32f), Offset(w * 0.92f, h * 0.68f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.92f, h * 0.32f), Offset(w * 0.60f, h * 0.68f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun CardActions(card: RecentsCard, env: RecentsEnv) {
    if (env.selecting) return
    val s = env.settings
    val any = if (card.isSplit) s.recents.btnUnsplit else s.recents.btnSplit || s.recents.btnPopup
    if (!any) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (card.isSplit) {
            SmallAction(stringResource(R.string.recents_unsplit), env.accent) { env.onUnsplit(card) }
        } else {
            if (s.recents.btnSplit) SmallAction(stringResource(R.string.recents_split), env.accent) { env.onSplit(card) }
            if (s.recents.btnPopup) SmallAction(stringResource(R.string.recents_popup), env.accent) { env.onPopUp(card) }
        }
    }
}

@Composable
private fun Thumb(picture: ImageBitmap?, app: AppItem?, modifier: Modifier, shape: RoundedCornerShape, glass: Boolean, iconSize: Dp) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.clip(shape).background(if (glass) Color.Black.copy(alpha = 0.25f) else Color(0xFF0E171A)),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
            if (thumbNeedsFit(picture.width.toFloat() / picture.height.coerceAtLeast(1), boxAspect)) {
                // Taken on the other screen (an inner-screen picture in a tall outer card, or the
                // other way round): the whole picture, over a dimmed fill of itself, instead of
                // a middle strip with the app's sides cut off.
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = THUMB_FILL_ALPHA })
                Image(picture, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
        } else if (app != null) {
            AppBadge(app, iconSize)
        }
    }
}

/** A picture this much wider or narrower than its card is shown whole ([thumbNeedsFit]). */
internal const val THUMB_FIT_RATIO = 1.35f
/** How bright the cropped fill behind a whole picture is. */
private const val THUMB_FILL_ALPHA = 0.3f

/**
 * Whether a task picture of [pictureAspect] (width / height) is shown whole in a card of
 * [boxAspect] rather than cropped: when the two differ by more than [THUMB_FIT_RATIO] either way.
 */
internal fun thumbNeedsFit(pictureAspect: Float, boxAspect: Float): Boolean {
    if (pictureAspect <= 0f || boxAspect <= 0f) return false
    val ratio = if (pictureAspect > boxAspect) pictureAspect / boxAspect else boxAspect / pictureAspect
    return ratio > THUMB_FIT_RATIO
}

@Composable
internal fun AppBadge(app: AppItem?, size: Dp) {
    if (app == null) {
        Box(Modifier.size(size).clip(CircleShape).background(Chip))
        return
    }
    val px = with(LocalDensity.current) { size.roundToPx() }
    AsyncImage(
        model = IconRequest(app, sizePx = px),
        contentDescription = app.label,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun SmallAction(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .pressClick(onClick)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
internal fun Pill(
    text: String,
    color: Color,
    filled: Boolean,
    glass: Boolean,
    background: Color = Color.Transparent,
    /** Drawn instead of [text] (which then names it for TalkBack). */
    glyph: (@Composable (Color) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .then(if (onClick != null) Modifier.pressClick(onClick) else Modifier)
            .then(
                when {
                    filled -> Modifier.clip(shape).background(background)
                    glass -> Modifier.recentsSurface(shape, true, false, color)
                    else -> Modifier.clip(shape).border(1.dp, color.copy(alpha = 0.6f), shape)
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (glyph != null) Modifier.semantics { contentDescription = text } else Modifier),
    ) {
        if (glyph != null) glyph(color)
        else Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Peek view as a symbol: one big pane with a thin strip at its edge, or ([three]) in the middle
 * with a strip at each edge — Motorola's three-app peek.
 */
@Composable
internal fun PeekGlyph(color: Color, three: Boolean) {
    Canvas(Modifier.size(width = 30.dp, height = 19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.1f)
        val r = androidx.compose.ui.geometry.CornerRadius(h * 0.14f)
        val strip = w * 0.1f
        val gap = w * 0.06f
        val bigLeft = strip + gap
        val bigRight = if (three) w - strip - gap else w
        drawRoundRect(color, Offset(0f, h * 0.18f), androidx.compose.ui.geometry.Size(strip, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(strip / 2))
        if (three) drawRoundRect(color, Offset(w - strip, h * 0.18f), androidx.compose.ui.geometry.Size(strip, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(strip / 2))
        val inset = stroke.width / 2
        drawRoundRect(color, Offset(bigLeft + inset, inset), androidx.compose.ui.geometry.Size(bigRight - bigLeft - 2 * inset, h - 2 * inset), r, style = stroke)
    }
}

@Composable
internal fun CrossGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        drawLine(color, Offset(0f, 0f), Offset(w, w), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(0f, w), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
    }
}

/** Without the one-time DUMP grant Android won't say what is open: how to give it. */
@Composable
internal fun NeedsGrant(accent: Color) {
    val context = LocalContext.current
    val command = remember { ShellSplit.grantCommand(context.packageName) }
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.recents_needs_grant), color = Ink, fontSize = 16.sp)
        Text(
            command,
            color = accent, fontFamily = Mono, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp),
        )
        Pill(
            text = stringResource(R.string.recents_copy_command),
            color = MaterialTheme.colorScheme.onPrimary, background = accent, filled = true, glass = false,
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("adb", command))
            },
        )
    }
}

internal fun timeLabel(context: Context, inactiveMs: Long?): String {
    if (inactiveMs == null) return ""
    val minutes = inactiveMs / 60_000
    return when {
        minutes < 1 -> context.getString(R.string.recents_time_now)
        minutes < 60 -> context.getString(R.string.recents_time_min, minutes.toInt())
        minutes < 24 * 60 -> context.getString(R.string.recents_time_hours, (minutes / 60f).roundToInt())
        minutes < 48 * 60 -> context.getString(R.string.recents_time_yesterday)
        else -> context.getString(R.string.recents_time_days, (minutes / (24 * 60)).toInt())
    }
}
