package org.arkikeskus.launcher.recents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.R
import org.arkikeskus.launcher.data.ShellSplit
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.IconRequest
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.rememberRecentsBackdrop
import org.arkikeskus.launcher.ui.component.recentsSelect
import org.arkikeskus.launcher.ui.component.recentsEnter
import kotlin.math.abs
import kotlin.math.roundToInt

private val Ink = Color(0xFFE8F4F5)
private val Muted = Color(0xFF8FA6AA)
private val CardBg = Color(0xFF131D20)
private val CardLine = Color(0xFF1C292C)
private val Chip = Color(0xFF1B2629)
private val Mono = FontFamily.Monospace

/** The slots of split view in the order apps are picked: left, middle/right, a pop-up. */
private fun slotLabels(count: Int, thirdInSplit: Boolean): List<Int> = when {
    count <= 2 -> listOf(R.string.recents_slot_left, R.string.recents_slot_right)
    thirdInSplit -> listOf(R.string.recents_slot_left, R.string.recents_slot_middle, R.string.recents_slot_right, R.string.recents_slot_popup)
    else -> listOf(R.string.recents_slot_left, R.string.recents_slot_right, R.string.recents_slot_popup, R.string.recents_slot_popup)
}.take(maxOf(count, 2))

@Composable
internal fun RecentsScreen(
    state: RecentsState,
    thumbnails: Map<Int, ImageBitmap>,
    settings: LauncherSettings,
    onOpen: (RecentsCard) -> Unit,
    onClose: (RecentsCard) -> Unit,
    onCloseAll: () -> Unit,
    onPopUp: (RecentsCard) -> Unit,
    onUnsplit: (RecentsCard) -> Unit,
    onOpenTogether: (List<RecentsCard>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val columns = if (wide) settings.recentsColumnsInner else settings.recentsColumnsOuter
    val corner = settings.recentsCornerDp.dp
    val canClose = state.root && settings.recentsRootClose

    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<RecentsCard>() }
    var confirmAll by remember { mutableStateOf(false) }
    val speed = settings.recentsAnimationSpeed
    val backdrop = rememberRecentsBackdrop(settings.recentsEnterAnimation, speed, Unit)

    Box(
        Modifier
            .fillMaxSize()
            // The backdrop fades in with the cards (read while drawing: no recomposition per frame).
            .drawBehind { drawRect(Color(0xFF05090A).copy(alpha = settings.recentsBackdropDim * backdrop())) }
            // A tap on empty space goes back home.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (selecting) { selecting = false; picked.clear() } else onDismiss()
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---------------------------------------------------------------- header
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recents_title), color = Ink, fontSize = if (wide) 40.sp else 32.sp, fontWeight = FontWeight.Bold)
                    val count = state.cards.sumOf { it.tasks.size }
                    Text(
                        when {
                            state.loading -> stringResource(R.string.recents_loading)
                            count == 0 -> stringResource(R.string.recents_nothing_open)
                            else -> stringResource(R.string.recents_count, count)
                        },
                        color = Muted, fontSize = 12.sp, fontFamily = Mono,
                    )
                }
                if (settings.recentsThumbnails || settings.recentsRootClose) {
                    Pill(
                        text = stringResource(if (state.root) R.string.recents_root_on else R.string.recents_root_off),
                        color = if (state.root) accent else Muted,
                        filled = false,
                    )
                }
                if (state.cards.size >= 2) {
                    Pill(
                        text = stringResource(if (selecting) R.string.recents_cancel else R.string.recents_split_view),
                        color = if (selecting) onAccent else accent,
                        background = if (selecting) accent else Color.Transparent,
                        filled = selecting,
                        onClick = { selecting = !selecting; picked.clear() },
                    )
                }
                if (state.cards.isNotEmpty() && !selecting) {
                    Pill(
                        text = stringResource(if (confirmAll) R.string.recents_close_all_confirm else R.string.recents_close_all),
                        color = onAccent, background = accent, filled = true,
                        onClick = {
                            if (settings.recentsConfirmCloseAll && !confirmAll) confirmAll = true
                            else { confirmAll = false; onCloseAll() }
                        },
                    )
                }
            }

            // ---------------------------------------------------------------- body
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !state.canList -> NeedsGrant(accent)
                    !state.loading && state.cards.isEmpty() -> Text(
                        stringResource(R.string.recents_nothing_open_long),
                        color = Muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        itemsIndexed(state.cards, key = { _, c -> c.key }) { index, card ->
                            val slot = picked.indexOf(card)
                            RecentsCardView(
                                card = card,
                                motion = Modifier
                                    .recentsEnter(settings.recentsEnterAnimation, index, columns, speed, Unit)
                                    .recentsSelect(settings.recentsSelectAnimation, slot >= 0, slot, picked.size, accent, corner, speed),
                                thumbnail = if (settings.recentsThumbnails) thumbnails[card.tasks.first().taskId] else null,
                                secondThumbnails = card.tasks.drop(1).map { if (settings.recentsThumbnails) thumbnails[it.taskId] else null },
                                height = settings.recentsCardHeight.dp,
                                corner = corner,
                                accent = accent,
                                showTime = settings.recentsShowTime,
                                showActions = settings.recentsShowActions && !selecting,
                                canClose = canClose && !selecting,
                                selecting = selecting,
                                slotLabel = if (slot >= 0) slotLabels(picked.size, settings.recentsThirdInSplit).getOrNull(slot) else null,
                                onTap = {
                                    if (!selecting) onOpen(card)
                                    else if (slot >= 0) picked.remove(card)
                                    else if (picked.size < MAX_PICK && !card.isSplit) picked.add(card)
                                },
                                onClose = { onClose(card) },
                                onSplit = {
                                    selecting = true
                                    picked.clear()
                                    picked.add(card)
                                },
                                onPopUp = { onPopUp(card) },
                                onUnsplit = { onUnsplit(card) },
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- split view bar
        if (selecting) {
            val labels = slotLabels(picked.size, settings.recentsThirdInSplit)
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEB0B1214))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (picked.isEmpty()) stringResource(R.string.recents_pick_hint)
                    else picked.mapIndexed { i, c -> "${stringResource(labels[i])} · ${c.label}" }.joinToString("   "),
                    color = accent, fontFamily = Mono, fontSize = 13.sp, maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Pill(
                    text = stringResource(R.string.recents_open_together),
                    color = onAccent, background = if (picked.size >= 2) accent else accent.copy(alpha = 0.35f), filled = true,
                    onClick = { if (picked.size >= 2) onOpenTogether(picked.toList()) },
                )
            }
        }
    }
}

private const val MAX_PICK = 4

@Composable
private fun RecentsCardView(
    card: RecentsCard,
    motion: Modifier,
    thumbnail: ImageBitmap?,
    secondThumbnails: List<ImageBitmap?>,
    height: Dp,
    corner: Dp,
    accent: Color,
    showTime: Boolean,
    showActions: Boolean,
    canClose: Boolean,
    selecting: Boolean,
    slotLabel: Int?,
    onTap: () -> Unit,
    onClose: () -> Unit,
    onSplit: () -> Unit,
    onPopUp: () -> Unit,
    onUnsplit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 140.dp.toPx() }
    val shape = RoundedCornerShape(corner)
    val picked = slotLabel != null

    Column(
        Modifier
            .fillMaxWidth()
            .then(motion)
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - (abs(offset.value) / (dismissPx * 1.6f)).coerceIn(0f, 0.8f)
            }
            .then(
                // With root: flick a card up (or down) to close it.
                if (canClose) Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo(offset.value + d) } },
                    onDragStopped = { velocity ->
                        if (abs(offset.value) > dismissPx || abs(velocity) > 2500f) {
                            offset.animateTo(if (offset.value < 0) -dismissPx * 3 else dismissPx * 3)
                            onClose()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ) else Modifier,
            )
            .clip(shape)
            .background(CardBg)
            .border(if (picked) BorderStroke(2.dp, accent) else BorderStroke(1.dp, CardLine), shape)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                card.apps.forEach { app -> AppBadge(app, 32.dp) }
            }
            Column(Modifier.weight(1f)) {
                Text(card.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    if (card.isSplit) add(stringResource(R.string.recents_split_card))
                    if (showTime) add(timeLabel(LocalContext.current, card.inactiveMs))
                }.filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
            }
            if (slotLabel != null) {
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(accent).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(stringResource(slotLabel), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (selecting && !card.isSplit) {
                Box(Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, accent, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(stringResource(R.string.recents_add), color = accent, fontSize = 12.sp)
                }
            } else if (canClose) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Chip).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { CrossGlyph(Color(0xFFC9D6D8)) }
            }
        }

        // The picture: Android's own screenshot (root), the split's halves side by side, or the icon.
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val pictures = listOf(thumbnail) + secondThumbnails
            card.apps.forEachIndexed { i, app ->
                Thumb(pictures.getOrNull(i), app, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape((corner.value * 0.6f).dp))
            }
        }

        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.isSplit) {
                    SmallAction(stringResource(R.string.recents_unsplit), accent, onUnsplit)
                } else {
                    SmallAction(stringResource(R.string.recents_split), accent, onSplit)
                    SmallAction(stringResource(R.string.recents_popup), accent, onPopUp)
                }
            }
        }
    }
}

@Composable
private fun Thumb(picture: ImageBitmap?, app: AppItem?, modifier: Modifier, shape: RoundedCornerShape) {
    Box(modifier.clip(shape).background(Color(0xFF0E171A)), contentAlignment = Alignment.Center) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else if (app != null) {
            AppBadge(app, 64.dp)
        }
    }
}

@Composable
private fun AppBadge(app: AppItem?, size: Dp) {
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
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    filled: Boolean,
    background: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(background) else Modifier.border(1.dp, color.copy(alpha = 0.6f), shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun CrossGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        drawLine(color, Offset(0f, 0f), Offset(w, w), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(0f, w), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
    }
}

/** Without the one-time DUMP grant Android won't say what is open: how to give it. */
@Composable
private fun NeedsGrant(accent: Color) {
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
            color = MaterialTheme.colorScheme.onPrimary, background = accent, filled = true,
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("adb", command))
            },
        )
    }
}

private fun timeLabel(context: Context, inactiveMs: Long?): String {
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
