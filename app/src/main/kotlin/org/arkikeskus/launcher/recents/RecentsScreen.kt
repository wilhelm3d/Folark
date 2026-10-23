package org.arkikeskus.launcher.recents

import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
    LauncherSettings.RECENTS_THEME_ARC,
    LauncherSettings.RECENTS_THEME_FILMSTRIP,
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
    /** "Split 3 apps": pick two more, then the three open in a three-way split. */
    val onSplitThree: (RecentsCard) -> Unit = {},
    /** "Peek view" on one app: pick the app that peeks in; on a split card: that split as peek view. */
    val onPeekPick: (RecentsCard) -> Unit = {},
    val onPeekSplit: (RecentsCard) -> Unit = {},
) {
    val speed: Float get() = settings.recents.animationSpeed

    /** On the cover screen (the narrow one), where Recents may have gestures of its own. */
    var coverScreen: Boolean = false

    /** The card gestures for the screen Recents is on (Settings ▸ Recents ▸ Gestures on a card). */
    val swipeUpAction: String get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeUpAction else it.swipeUpAction }
    val swipeDownAction: String get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeDownAction else it.swipeDownAction }
    val longPressAction: String get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerLongPressAction else it.longPressAction }
    val doubleTapAction: String get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerDoubleTapAction else it.doubleTapAction }
    /** Borderless cards (Settings ▸ Recents ▸ Card layout). */
    val overlay: Boolean get() = settings.recents.cardLayout == LauncherSettings.RECENTS_CARD_OVERLAY
    fun thumb(taskId: Int): ImageBitmap? = if (settings.recents.thumbnails) thumbnails[taskId] else null
    fun slotLabel(card: RecentsCard): Int? {
        val slot = picked.indexOf(card)
        return if (slot >= 0) slotLabels(picked.size, settings.recents.thirdInSplit).getOrNull(slot) else null
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
    onOpenThree: (List<RecentsCard>) -> Unit,
    onPeek: (main: RecentsCard, peek: RecentsCard) -> Unit,
    onPeekSplit: (RecentsCard) -> Unit,
    onCombine: (dragged: RecentsCard, target: RecentsCard, mode: String) -> Unit,
    onAppInfo: (RecentsCard) -> Unit,
    onTogglePin: (RecentsCard) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600
    // On the cover screen, its own look when it has one (Settings ▸ Recents ▸ Own look on the cover screen).
    @Suppress("NAME_SHADOWING")
    val settings = if (!wide && settings.recents.outerLookEnabled) settings.copy(recents = settings.recents.outerLook ?: settings.recents) else settings
    val glass = settings.recents.glass
    /** The margin at the screen's sides (title, buttons, grid and list themes). */
    val side = if (wide) 28.dp else 16.dp

    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<RecentsCard>() }
    /**
     * How the picked apps open (chips on the picking bar): opens by itself once [openAt] are
     * picked — two side by side, three in a three-way split, or two as peek view ([peekPick]);
     * 0 = pick freely and press Open.
     */
    var openAt by remember { mutableIntStateOf(2) }
    var peekPick by remember { mutableStateOf(false) }
    LaunchedEffect(picked.size, openAt, peekPick) {
        if (openAt > 0 && picked.size >= openAt) {
            delay(220)
            val chosen = picked.toList()
            when {
                peekPick -> onPeek(chosen[0], chosen[1])
                openAt >= 3 -> onOpenThree(chosen)
                else -> onOpenTogether(chosen)
            }
            openAt = 2
            peekPick = false
        }
    }
    var confirmAll by remember { mutableStateOf(false) }
    val drag = remember { RecentsDrag() }
    val menu = remember { RecentsMenuState() }
    drag.cards = state.cards
    val backdrop = rememberRecentsBackdrop(settings.recents.enterAnimation, settings.recents.animationSpeed, Unit)

    val env = RecentsEnv(
        settings = settings,
        thumbnails = thumbnails,
        accent = accent,
        onAccent = onAccent,
        corner = settings.recents.cornerDp.dp,
        canClose = state.root && settings.recents.rootClose,
        selecting = selecting,
        picked = picked,
        drag = drag,
        glass = glass,
        onTap = { card ->
            if (!selecting) onOpen(card)
            else if (card in picked) picked.remove(card)
            else if ((if (openAt > 0) openAt else MAX_PICK) > picked.size && !card.isSplit) picked.add(card)
        },
        onClose = onClose,
        onSplit = { card -> selecting = true; openAt = 2; peekPick = false; picked.clear(); picked.add(card) },
        onPopUp = onPopUp,
        onUnsplit = onUnsplit,
        onCombine = onCombine,
        onAppInfo = onAppInfo,
        onTogglePin = onTogglePin,
        menu = menu,
        onSplitThree = { card -> selecting = true; peekPick = false; picked.clear(); picked.add(card); openAt = 3 },
        onPeekPick = { card -> selecting = true; peekPick = true; picked.clear(); picked.add(card); openAt = 2 },
        onPeekSplit = onPeekSplit,
    )
    env.coverScreen = !wide

    CompositionLocalProvider(LocalPressStyle provides PressStyle(settings.recents.pressAnimation, accent, settings.recents.animationSpeed)) {
    Box(
        Modifier
            .fillMaxSize()
            // The backdrop fades in with the cards (read while drawing: no recomposition per frame).
            .drawBehind { drawRect(Color(0xFF05090A).copy(alpha = settings.recents.backdropDim * backdrop())) }
            // A tap on empty space goes back home.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (selecting) { selecting = false; openAt = 2; peekPick = false; picked.clear() } else onDismiss()
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
                    if (state.cards.size >= 2 && (settings.recents.btnSplitView || selecting)) {
                        Pill(
                            text = stringResource(if (selecting) R.string.recents_cancel else R.string.recents_split_view),
                            color = if (selecting) onAccent else accent,
                            background = if (selecting) accent else Color.Transparent,
                            filled = selecting,
                            glass = glass,
                            onClick = { selecting = !selecting; picked.clear() },
                        )
                    }
                    if (state.cards.isNotEmpty() && !selecting && settings.recents.btnCloseAll) {
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
                                if (settings.recents.confirmCloseAll && !confirmAll) confirmAll = true
                                else { confirmAll = false; onCloseAll() }
                            },
                        )
                    }
                }

                // ---------------------------------------------------------------- body
                // Rows of cards (iPad, Carousel, Deck) run to the screen's edges; the rest keep the margin.
                val edgeToEdge = settings.recents.theme in EDGE_TO_EDGE_THEMES
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (edgeToEdge) 0.dp else side)) {
                    when {
                        !state.canList -> NeedsGrant(accent)
                        !state.loading && state.cards.isEmpty() -> Text(
                            stringResource(R.string.recents_nothing_open_long),
                            color = Muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
                        )
                        else -> when (settings.recents.theme) {
                            LauncherSettings.RECENTS_THEME_IPAD -> IpadTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_CAROUSEL -> CarouselTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_DECK -> DeckTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_COMPACT -> CompactTheme(state.cards, env, if (wide) settings.recents.columnsInner.coerceAtMost(3) else 1)
                            LauncherSettings.RECENTS_THEME_SWITCHER -> SwitcherTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_STACK -> StackTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_ARC -> ArcTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_BENTO -> BentoTheme(state.cards, env)
                            LauncherSettings.RECENTS_THEME_FILMSTRIP -> FilmstripTheme(state.cards, env)
                            else -> FolarkTheme(state.cards, env, if (wide) settings.recents.columnsInner else settings.recents.columnsOuter)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- split view bar
            if (selecting) {
                val labels = slotLabels(picked.size, settings.recents.thirdInSplit)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = side),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                // How the picked apps open: the chosen one is filled; each opens by itself when enough are picked.
                Row(
                    Modifier
                        .recentsSurface(RoundedCornerShape(999.dp), glass, false, accent, solid = Color(0xEB0B1214))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val modes = buildList {
                        add(Triple(R.string.recents_mode_two, 2, false))
                        if (settings.launch.threeAppSplit) add(Triple(R.string.recents_mode_three, 3, false))
                        add(Triple(R.string.recents_mode_peek, 2, true))
                        add(Triple(R.string.recents_mode_free, 0, false))
                    }
                    modes.forEach { (label, count, peek) ->
                        val on = openAt == count && peekPick == peek
                        Pill(
                            text = stringResource(label),
                            color = if (on) onAccent else Ink,
                            background = if (on) accent else Color.White.copy(alpha = 0.08f),
                            filled = true, glass = false,
                            onClick = {
                                openAt = count
                                peekPick = peek
                                // Peek view and two side by side take two: keep the first two picked.
                                while (count in 1 until picked.size) picked.removeAt(picked.lastIndex)
                            },
                        )
                    }
                }
                Row(
                    Modifier
                        .recentsSurface(RoundedCornerShape(999.dp), glass, false, accent, solid = Color(0xEB0B1214))
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        if (peekPick && picked.size < 2) stringResource(R.string.recents_pick_peek)
                        else if (openAt > 0 && picked.size < openAt) pluralStringResource(R.plurals.recents_pick_more, openAt - picked.size, openAt - picked.size)
                        else if (picked.isEmpty()) stringResource(R.string.recents_pick_hint)
                        else picked.mapIndexed { i, c -> "${stringResource(labels[i])} · ${c.label}" }.joinToString("   "),
                        color = accent, fontFamily = Mono, fontSize = 13.sp, maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (openAt == 0) {
                        Pill(
                            text = stringResource(R.string.recents_open_together),
                            color = onAccent, background = if (picked.size >= 2) accent else accent.copy(alpha = 0.35f), filled = true, glass = false,
                            onClick = { if (picked.size >= 2) onOpenTogether(picked.toList()) },
                        )
                    }
                }
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
    recentsSurface(shape, env.glass, picked, env.accent, tint = env.settings.recents.glassTint)

/**
 * The held card as a small copy of its own picture, following the finger on a spring and leaning
 * into the direction it moves. The card under it lifts and shows its two halves — split view and
 * attached view — with the half under the finger lit; moving across slides the light over. Let go
 * and the copy flies into that card (then the two open together), or home when over nothing.
 */
@Composable
private fun DragLayer(drag: RecentsDrag, env: RecentsEnv) {
    val held = drag.card ?: return
    val density = LocalDensity.current
    val speed = env.speed.coerceIn(0.25f, 4f)
    val origin = drag.origin
    // The miniature: about half the card, never bigger than 190 dp or smaller than 110 dp wide.
    val maxW = with(density) { 190.dp.toPx() }
    val minW = with(density) { 110.dp.toPx() }
    val baseW = ((origin?.width ?: maxW) * 0.55f).coerceIn(minW, maxW)
    val baseH = baseW * ((origin?.height ?: baseW) / (origin?.width ?: baseW).coerceAtLeast(1f)).coerceIn(0.4f, 2.4f)

    val pos = remember(held.key) { Animatable(drag.pointer, Offset.VectorConverter) }
    val land = remember(held.key) { Animatable(0f) }
    LaunchedEffect(held.key) {
        snapshotFlow { drag.pointer }.collect { p ->
            if (!drag.settling) launch { pos.animateTo(p, spring(dampingRatio = 0.82f, stiffness = 1100f * speed * speed)) }
        }
    }
    LaunchedEffect(held.key, drag.settling) {
        if (!drag.settling) return@LaunchedEffect
        try {
            val to = drag.landing
            if (to != null) {
                launch { pos.animateTo(to.center, spring(dampingRatio = 0.86f, stiffness = 520f * speed * speed)) }
                land.animateTo(1f, tween((260 / speed).toInt(), easing = FastOutSlowInEasing))
            }
        } finally {
            // Only this drag's landing (a new hold may have started meanwhile).
            if (drag.card?.key == held.key && drag.settling) {
                drag.finish()?.let { (from, onto, how) -> env.onCombine(from, onto, how) }
            }
        }
    }

    // The card under the finger: a dark veil with the two ways to combine.
    var shownRect by remember(held.key) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val targetRect = drag.target?.let { drag.bounds[it] }
    if (targetRect != null) shownRect = targetRect
    val veil by animateFloatAsState(if (targetRect != null) 1f else 0f, tween((150 / speed).toInt()), label = "veil")
    val attached by animateFloatAsState(
        if (drag.mode == COMBINE_ATTACHED) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 700f * speed * speed),
        label = "half",
    )
    Box(Modifier.fillMaxSize()) {
        val r = shownRect
        if (r != null && veil > 0.01f) {
            val w = with(density) { r.width.toDp() }
            val h = with(density) { r.height.toDp() }
            Row(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(w, h)
                    .graphicsLayer { alpha = veil; scaleX = 1.03f; scaleY = 1.03f }
                    .clip(RoundedCornerShape(env.corner))
                    .background(Color(0xD905090A)),
            ) {
                DropHalf(R.string.recents_drop_split, 1f - attached, env.accent, attachedDiagram = false, env, Modifier.weight(1f))
                DropHalf(R.string.recents_drop_attached, attached, env.accent, attachedDiagram = true, env, Modifier.weight(1f))
            }
        }

        // The miniature of the held card.
        val k = land.value
        val to = drag.landing
        val scaleTo = if (to != null) (to.width / baseW).coerceIn(0.3f, 3f) else 1f
        val scale = 1.06f + (scaleTo - 1.06f) * k
        val app = held.apps.firstOrNull()
        Box(
            Modifier
                .offset { IntOffset((pos.value.x - baseW / 2).roundToInt(), (pos.value.y - baseH / 2).roundToInt()) }
                .size(with(density) { baseW.toDp() }, with(density) { baseH.toDp() })
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    // Leans into the movement, settles upright when it lands.
                    rotationZ = (pos.velocity.x / 260f).coerceIn(-9f, 9f) * (1f - k)
                    alpha = 1f - 0.85f * k * k
                    shadowElevation = 34f * (1f - k)
                    shape = RoundedCornerShape(env.corner * 0.7f)
                    clip = true
                },
        ) {
            CardPicture(held, env, Modifier.matchParentSize(), env.corner * 0.7f, iconSize = 40.dp)
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { AppBadge(app, 30.dp) }
        }
    }
}

/** One half of the drop target: a small drawing of the result and its name; lit by [active] 0..1. */
@Composable
private fun DropHalf(label: Int, active: Float, accent: Color, attachedDiagram: Boolean, env: RecentsEnv, modifier: Modifier) {
    val attachedLeft = env.settings.recents.attachedSide == LauncherSettings.RECENTS_ATTACHED_LEFT
    Column(
        modifier
            .fillMaxHeight()
            .padding(8.dp)
            .graphicsLayer { val k = 0.94f + 0.06f * active; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.06f + 0.30f * active)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        val ink = Color.White.copy(alpha = 0.45f + 0.55f * active)
        Canvas(Modifier.size(52.dp, 40.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx())
            val r = CornerRadius(5.dp.toPx())
            val gap = 4.dp.toPx()
            if (!attachedDiagram) {
                val half = (size.width - gap) / 2
                drawRoundRect(ink, Offset.Zero, Size(half, size.height), r, style = stroke)
                drawRoundRect(ink, Offset(half + gap, 0f), Size(half, size.height), r, style = stroke)
            } else {
                // The main app, with the other one peeking in at the chosen side.
                val peek = size.width * 0.26f
                val main = size.width - peek - gap
                val mainX = if (attachedLeft) peek + gap else 0f
                val peekX = if (attachedLeft) 0f else main + gap
                drawRoundRect(ink, Offset(mainX, 0f), Size(main, size.height), r, style = stroke)
                drawRoundRect(ink.copy(alpha = ink.alpha * 0.7f), Offset(peekX, 0f), Size(peek, size.height), r)
            }
        }
        Text(
            stringResource(label),
            color = if (active > 0.5f) Ink else Muted,
            fontSize = 14.sp,
            fontWeight = if (active > 0.5f) FontWeight.Bold else FontWeight.Medium,
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
