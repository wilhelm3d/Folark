package org.arkikeskus.launcher.recents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.arkikeskus.launcher.ui.component.pressClick
import org.arkikeskus.launcher.ui.component.PressStyle
import org.arkikeskus.launcher.ui.component.LocalPressStyle
import androidx.compose.runtime.CompositionLocalProvider
import org.arkikeskus.launcher.ui.component.recentsEnter
import org.arkikeskus.launcher.ui.component.recentsSelect
import org.arkikeskus.launcher.ui.component.rememberRecentsBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

internal val Ink = Color(0xFFE8F4F5)
internal val Muted = Color(0xFF8FA6AA)
private val CardBg = Color(0xFF131D20)
private val CardLine = Color(0xFF1C292C)
internal val Chip = Color(0xFF1B2629)
internal val Mono = FontFamily.Monospace

/** The slots of split view in the order apps are picked: left, middle/right, a pop-up. */
internal fun slotLabels(count: Int, thirdInSplit: Boolean): List<Int> = when {
    count <= 2 -> listOf(R.string.recents_slot_left, R.string.recents_slot_right)
    thirdInSplit -> listOf(R.string.recents_slot_left, R.string.recents_slot_middle, R.string.recents_slot_right, R.string.recents_slot_popup)
    else -> listOf(R.string.recents_slot_left, R.string.recents_slot_right, R.string.recents_slot_popup, R.string.recents_slot_popup)
}.take(maxOf(count, 2))

internal const val MAX_PICK = 4

/** Themes whose cards scroll sideways and so run to the screen's edges. */
private val EDGE_TO_EDGE_THEMES = setOf(
    LauncherSettings.RECENTS_THEME_IPAD,
    LauncherSettings.RECENTS_THEME_CAROUSEL,
    LauncherSettings.RECENTS_THEME_DECK,
)

/** Everything a Recents theme needs to draw its cards and answer touches. */
internal class RecentsEnv(
    val settings: LauncherSettings,
    val thumbnails: Map<Int, ImageBitmap>,
    val accent: Color,
    val onAccent: Color,
    val corner: Dp,
    val canClose: Boolean,
    val selecting: Boolean,
    val picked: SnapshotStateList<RecentsCard>,
    val drag: RecentsDrag,
    val glass: Boolean,
    val onTap: (RecentsCard) -> Unit,
    val onClose: (RecentsCard) -> Unit,
    val onSplit: (RecentsCard) -> Unit,
    val onPopUp: (RecentsCard) -> Unit,
    val onUnsplit: (RecentsCard) -> Unit,
    val onCombine: (RecentsCard, RecentsCard, String) -> Unit,
    val onAppInfo: (RecentsCard) -> Unit,
    val onTogglePin: (RecentsCard) -> Unit,
    val menu: RecentsMenuState,
) {
    val speed: Float get() = settings.recentsAnimationSpeed
    fun thumb(taskId: Int): ImageBitmap? = if (settings.recentsThumbnails) thumbnails[taskId] else null
    fun slotLabel(card: RecentsCard): Int? {
        val slot = picked.indexOf(card)
        return if (slot >= 0) slotLabels(picked.size, settings.recentsThirdInSplit).getOrNull(slot) else null
    }
}

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
    onCombine: (dragged: RecentsCard, target: RecentsCard, mode: String) -> Unit,
    onAppInfo: (RecentsCard) -> Unit,
    onTogglePin: (RecentsCard) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val glass = settings.recentsGlass
    /** The margin at the screen's sides (title, buttons, grid and list themes). */
    val side = if (wide) 28.dp else 16.dp

    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<RecentsCard>() }
    var confirmAll by remember { mutableStateOf(false) }
    val drag = remember { RecentsDrag() }
    val menu = remember { RecentsMenuState() }
    drag.cards = state.cards
    val backdrop = rememberRecentsBackdrop(settings.recentsEnterAnimation, settings.recentsAnimationSpeed, Unit)

    val env = RecentsEnv(
        settings = settings,
        thumbnails = thumbnails,
        accent = accent,
        onAccent = onAccent,
        corner = settings.recentsCornerDp.dp,
        canClose = state.root && settings.recentsRootClose,
        selecting = selecting,
        picked = picked,
        drag = drag,
        glass = glass,
        onTap = { card ->
            if (!selecting) onOpen(card)
            else if (card in picked) picked.remove(card)
            else if (picked.size < MAX_PICK && !card.isSplit) picked.add(card)
        },
        onClose = onClose,
        onSplit = { card -> selecting = true; picked.clear(); picked.add(card) },
        onPopUp = onPopUp,
        onUnsplit = onUnsplit,
        onCombine = onCombine,
        onAppInfo = onAppInfo,
        onTogglePin = onTogglePin,
        menu = menu,
    )

    CompositionLocalProvider(LocalPressStyle provides PressStyle(settings.recentsPressAnimation, accent, settings.recentsAnimationSpeed)) {
    Box(
        Modifier
            .fillMaxSize()
            // The backdrop fades in with the cards (read while drawing: no recomposition per frame).
            .drawBehind { drawRect(Color(0xFF05090A).copy(alpha = settings.recentsBackdropDim * backdrop())) }
            // A tap on empty space goes back home.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (selecting) { selecting = false; picked.clear() } else onDismiss()
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 16.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // ---------------------------------------------------------------- header
                Row(
                    Modifier.padding(horizontal = side),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.recents_title), color = Ink, fontSize = if (wide) 40.sp else 32.sp, fontWeight = FontWeight.Bold)
                        val count = state.cards.sumOf { it.tasks.size }
                        Text(
                            when {
                                state.loading -> stringResource(R.string.recents_loading)
                                count == 0 -> stringResource(R.string.recents_nothing_open)
                                drag.card != null -> stringResource(R.string.recents_drag_hint)
                                else -> stringResource(R.string.recents_count, count)
                            },
                            color = Muted, fontSize = 12.sp, fontFamily = Mono,
                        )
                    }
                    if (state.cards.size >= 2 && (settings.recentsBtnSplitView || selecting)) {
                        Pill(
                            text = stringResource(if (selecting) R.string.recents_cancel else R.string.recents_split_view),
                            color = if (selecting) onAccent else accent,
                            background = if (selecting) accent else Color.Transparent,
                            filled = selecting,
                            glass = glass,
                            onClick = { selecting = !selecting; picked.clear() },
                        )
                    }
                    if (state.cards.isNotEmpty() && !selecting && settings.recentsBtnCloseAll) {
                        Pill(
                            text = stringResource(
                                when {
                                    confirmAll -> R.string.recents_close_all_confirm
                                    state.cards.any { it.pinned } && !state.root -> R.string.recents_close_all_pins_too
                                    state.cards.any { it.pinned } -> R.string.recents_close_others
                                    else -> R.string.recents_close_all
                                },
                            ),
                            color = onAccent, background = accent, filled = true, glass = false,
                            onClick = {
                                if (settings.recentsConfirmCloseAll && !confirmAll) confirmAll = true
                                else { confirmAll = false; onCloseAll() }
                            },
                        )
                    }
                }

                // ---------------------------------------------------------------- body
                // Rows of cards (iPad, Carousel, Deck) run to the screen's edges; the rest keep the margin.
                val edgeToEdge = settings.recentsTheme in EDGE_TO_EDGE_THEMES
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (edgeToEdge) 0.dp else side)) {
                    when {
                        !state.canList -> NeedsGrant(accent)
                        !state.loading && state.cards.isEmpty() -> Text(
                            stringResource(R.string.recents_nothing_open_long),
                            color = Muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
                        )
                        else -> when (settings.recentsTheme) {
                            LauncherSettings.RECENTS_THEME_IPAD -> IpadTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_CAROUSEL -> CarouselTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_DECK -> DeckTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_COMPACT -> CompactTheme(state.cards, env, if (wide) settings.recentsColumnsInner.coerceAtMost(3) else 1)
                            LauncherSettings.RECENTS_THEME_SWITCHER -> SwitcherTheme(state.cards, env)
                            else -> FolarkTheme(state.cards, env, if (wide) settings.recentsColumnsInner else settings.recentsColumnsOuter)
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
                        .padding(horizontal = side)
                        .recentsSurface(RoundedCornerShape(999.dp), glass, false, accent, solid = Color(0xEB0B1214))
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
                        color = onAccent, background = if (picked.size >= 2) accent else accent.copy(alpha = 0.35f), filled = true, glass = false,
                        onClick = { if (picked.size >= 2) onOpenTogether(picked.toList()) },
                    )
                }
            }
        }

        DragLayer(drag, env)
        RecentsMenuLayer(menu, env)
    }
    }
}

// ---------------------------------------------------------------------------- shared card behaviour

/** A card's surface: Folark's dark card, or Liquid Glass (see-through, bright rim, a sheen on top). */
internal fun Modifier.recentsSurface(
    shape: Shape,
    glass: Boolean,
    picked: Boolean,
    accent: Color,
    solid: Color = CardBg,
    tint: Float = 0.18f,
): Modifier = if (glass) {
    this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = tint * 1.3f), Color.White.copy(alpha = tint * 0.35f))))
        .drawWithContent {
            drawContent()
            // The sheen: light caught along the top, fading down.
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.28f to Color.Transparent),
                size = Size(size.width, size.height * 0.5f),
            )
        }
        .border(
            if (picked) 2.dp else 1.dp,
            if (picked) Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f)))
            else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.4f))),
            shape,
        )
} else {
    this
        .clip(shape)
        .background(solid)
        .border(if (picked) 2.dp else 1.dp, if (picked) accent else CardLine, shape)
}

/** [recentsSurface] with this Recents' look (Liquid Glass and its tint from the settings). */
internal fun Modifier.envSurface(env: RecentsEnv, shape: Shape, picked: Boolean): Modifier =
    recentsSurface(shape, env.glass, picked, env.accent, tint = env.settings.recentsGlassTint)

/** The held card follows the finger; the card under it shows its two halves: split, attached. */
@Composable
private fun DragLayer(drag: RecentsDrag, env: RecentsEnv) {
    val held = drag.card ?: return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        val target = drag.target?.let { drag.bounds[it] }
        if (target != null) {
            val w = with(density) { target.width.toDp() }
            val h = with(density) { target.height.toDp() }
            Row(
                Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(w, h)
                    .clip(RoundedCornerShape(env.corner))
                    .background(Color(0xCC05090A)),
            ) {
                DropHalf(R.string.recents_drop_split, drag.mode == COMBINE_SPLIT, env.accent, Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 18.dp).background(env.accent.copy(alpha = 0.5f)))
                DropHalf(R.string.recents_drop_attached, drag.mode == COMBINE_ATTACHED, env.accent, Modifier.weight(1f))
            }
        }
        // The held app, a little card under the finger.
        val app = held.apps.firstOrNull()
        Row(
            Modifier
                .offset { IntOffset((drag.pointer.x - with(density) { 70.dp.toPx() }).roundToInt(), (drag.pointer.y - with(density) { 36.dp.toPx() }).roundToInt()) }
                .graphicsLayer { scaleX = 1.05f; scaleY = 1.05f; shadowElevation = 24f }
                .envSurface(env, RoundedCornerShape(22.dp), true)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppBadge(app, 36.dp)
            Text(held.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DropHalf(label: Int, active: Boolean, accent: Color, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(label),
            color = if (active) Ink else Muted,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

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
                if (env.settings.recentsShowTime) add(timeLabel(LocalContext.current, card.inactiveMs))
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
            if (env.settings.recentsBtnPin) {
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
            if (env.canClose && showClose && env.settings.recentsBtnClose && !card.pinned) {
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

/** The card's picture: Android's screenshot of each app (split halves side by side), or its icon. */
@Composable
internal fun CardPicture(card: RecentsCard, env: RecentsEnv, modifier: Modifier, corner: Dp, iconSize: Dp = 64.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        card.tasks.forEachIndexed { i, task ->
            Thumb(env.thumb(task.taskId), card.apps.getOrNull(i), Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(corner), env.glass, iconSize)
        }
    }
}

@Composable
internal fun CardActions(card: RecentsCard, env: RecentsEnv) {
    if (env.selecting) return
    val s = env.settings
    val any = if (card.isSplit) s.recentsBtnUnsplit else s.recentsBtnSplit || s.recentsBtnPopup
    if (!any) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (card.isSplit) {
            SmallAction(stringResource(R.string.recents_unsplit), env.accent) { env.onUnsplit(card) }
        } else {
            if (s.recentsBtnSplit) SmallAction(stringResource(R.string.recents_split), env.accent) { env.onSplit(card) }
            if (s.recentsBtnPopup) SmallAction(stringResource(R.string.recents_popup), env.accent) { env.onPopUp(card) }
        }
    }
}

@Composable
private fun Thumb(picture: ImageBitmap?, app: AppItem?, modifier: Modifier, shape: RoundedCornerShape, glass: Boolean, iconSize: Dp) {
    Box(
        modifier.clip(shape).background(if (glass) Color.Black.copy(alpha = 0.25f) else Color(0xFF0E171A)),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else if (app != null) {
            AppBadge(app, iconSize)
        }
    }
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
private fun Pill(
    text: String,
    color: Color,
    filled: Boolean,
    glass: Boolean,
    background: Color = Color.Transparent,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
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
