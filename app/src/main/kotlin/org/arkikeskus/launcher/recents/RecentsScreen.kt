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

internal val Ink = Color(0xFFE8F4F5)
internal val Muted = Color(0xFF8FA6AA)
internal val CardBg = Color(0xFF131D20)
internal val CardLine = Color(0xFF1C292C)
internal val Chip = Color(0xFF1B2629)
internal val Mono = FontFamily.Monospace

/** The slots of split view in the order apps are picked: left, middle/right, a pop-up. */
internal fun slotLabels(count: Int, thirdInSplit: Boolean): List<Int> = when {
    count <= 2 -> listOf(R.string.recents_slot_left, R.string.recents_slot_right)
    thirdInSplit -> listOf(R.string.recents_slot_left, R.string.recents_slot_middle, R.string.recents_slot_right, R.string.recents_slot_popup)
    else -> listOf(R.string.recents_slot_left, R.string.recents_slot_right, R.string.recents_slot_popup, R.string.recents_slot_popup)
}.take(maxOf(count, 2))

internal const val MAX_PICK = 4


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
    /** Closes every app (the Close all button), for the "Close all" gesture. */
    val onCloseAll: () -> Unit = {},
    /** A split card's apps saved as an app pair (to open them together again from home or the taskbar). */
    val onSavePair: (RecentsCard) -> Unit = {},
    /** A split (two or three apps) saved as a scene with a shortcut on the home screen. */
    val onAddToHome: (RecentsCard) -> Unit = {},
    /** Speaker badge (Settings ▸ Recents): whether it shows, which packages have audio, whether one
     *  is playing, and the tap that pauses/resumes just that app. */
    val speakerBadge: Boolean = false,
    val audioActive: (String) -> Boolean = { false },
    val audioPlaying: (String) -> Boolean = { false },
    val onToggleAudio: (String) -> Unit = {},
    /** Place a long-press-dragged card at a screen edge (inner screen only), one of EDGE_*. */
    val onPlaceAtEdge: (RecentsCard, Int) -> Unit = { _, _ -> },
    /** Memory in use per package (KB), for the memory label on cards (empty: no label). */
    val memory: Map<String, Long> = emptyMap(),
    /** Persistent per-app mute (menu, beneath App info): whether this card is muted, and the toggle. */
    val isMuted: (RecentsCard) -> Boolean = { false },
    val onToggleMute: (RecentsCard) -> Unit = {},
    /** Motorola's native split-select pickers (shown only when native three-app split is on). */
    val onNativeSplit: (RecentsCard) -> Unit = {},
    val onNativeThird: (RecentsCard) -> Unit = {},
) {
    val speed: Float get() = settings.recents.animationSpeed

    /** On the outer screen (the narrow one), where Recents may have gestures of its own. */
    var coverScreen: Boolean = false

    /** The card gestures for the screen Recents is on (Settings ▸ Recents ▸ Gestures on a card). */
    val swipeUpAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeUpAction else it.swipeUpAction }
    val swipeDownAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeDownAction else it.swipeDownAction }
    val swipeLeftAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeLeftAction else it.swipeLeftAction }
    val swipeRightAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerSwipeRightAction else it.swipeRightAction }
    val longPressAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerLongPressAction else it.longPressAction }
    val doubleTapAction: RecentsAction get() = settings.recents.let { if (coverScreen && it.outerGestures) it.outerDoubleTapAction else it.doubleTapAction }
    /** Borderless cards (Settings ▸ Recents ▸ Card layout). */
    val overlay: Boolean get() = settings.recents.cardLayout == RecentsCardLayout.OVERLAY
    /** The app icon on each card, a card shadow, and the label size (Settings ▸ Recents). */
    val cardIcons: Boolean get() = settings.recents.cardIcons
    val cardShadow: Boolean get() = settings.recents.cardShadow
    val labelScale: Float get() = settings.recents.labelScale
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
    /** Memory in use per package (KB); empty when the memory label is off. */
    memory: Map<String, Long> = emptyMap(),
    settings: LauncherSettings,
    onCover: Boolean = false,
    onOpen: (RecentsCard) -> Unit,
    onClose: (RecentsCard) -> Unit,
    onCloseAll: () -> Unit,
    onPopUp: (RecentsCard) -> Unit,
    onUnsplit: (RecentsCard) -> Unit,
    onOpenTogether: (List<RecentsCard>) -> Unit,
    onOpenThree: (List<RecentsCard>) -> Unit,
    onPeek: (main: RecentsCard, peek: RecentsCard) -> Unit,
    /** Peek view of three: the first picked app big in the middle, the other two at the edges. */
    onPeekThree: (List<RecentsCard>) -> Unit = {},
    onPeekSplit: (RecentsCard) -> Unit,
    onCombine: (dragged: RecentsCard, target: RecentsCard, mode: String) -> Unit,
    onAppInfo: (RecentsCard) -> Unit,
    onTogglePin: (RecentsCard) -> Unit,
    onDismiss: () -> Unit,
    onSavePair: (RecentsCard) -> Unit = {},
    onAddToHome: (RecentsCard) -> Unit = {},
    /** A split card dropped on the taskbar: pinned there as a scene. */
    onPinSplit: ((RecentsCard) -> Unit)? = null,
    /** Long-press-drag a card to a screen edge (inner screen): place the app there (EDGE_*). */
    onPlaceAtEdge: (RecentsCard, Int) -> Unit = { _, _ -> },
    /** Persistent per-app mute (menu item beneath App info): is this card muted, and the toggle. */
    isMuted: (RecentsCard) -> Boolean = { false },
    onToggleMute: (RecentsCard) -> Unit = {},
    /** Motorola's native split-select pickers (shown only when native three-app split is on). */
    onNativeSplit: (RecentsCard) -> Unit = {},
    onNativeThird: (RecentsCard) -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val wide = LocalConfiguration.current.smallestScreenWidthDp >= 600
    // On the outer screen, its own look when it has one (Settings ▸ Recents ▸ Own look on the outer screen).
    @Suppress("NAME_SHADOWING")
    val settings = if (onCover && settings.recents.outerLookEnabled) settings.copy(recents = settings.recents.outerLook ?: settings.recents) else settings
    val glass = settings.recents.glass
    /** The margin at the screen's sides (title, buttons, grid and list themes). */
    val side = if (wide) 28.dp else 16.dp

    // Picking apps for a split view, and opening them once enough are picked (RecentsChrome.kt).
    val picking = remember { RecentsPicking() }
    val selecting = picking.selecting
    OpenWhenPicked(picking, onOpenTogether, onOpenThree, onPeek, onPeekThree)
    /** Peek view takes three apps (Motorola's three-app peek, through the module) where it can. */
    val peekContext = LocalContext.current
    val moduleReady = remember { org.arkikeskus.launcher.data.SplitThree.ready(peekContext) }
    val peekCount = if (settings.launch.threeAppSplit && moduleReady) 3 else 2
    var confirmAll by remember { mutableStateOf(false) }
    val drag = remember { RecentsDrag() }
    val hostView = androidx.compose.ui.platform.LocalView.current
    val menu = remember { RecentsMenuState() }
    drag.cards = state.cards
    // A split card takes a dropped app as its third with the real three-app split or the module
    // (as the split drawer's 33/33/33 does).
    drag.onPinSplit = onPinSplit
    drag.allowThird = settings.launch.threeAppSplit || moduleReady
    drag.allowReplace = settings.launch.threeAppSplit || moduleReady
    // Aero edge placement: inner screen only, where there is room for side-by-side apps.
    drag.allowEdge = !onCover
    drag.zones = settings.recents.dropZones
    with(LocalDensity.current) {
        drag.screenW = LocalConfiguration.current.screenWidthDp.dp.toPx()
        drag.screenH = LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val backdrop = rememberRecentsBackdrop(settings.recents.enterAnimation, settings.recents.animationSpeed, Unit)

    // Active media sessions, for the per-card speaker badge (no-op and empty when the badge is off).
    val audio = rememberRecentsAudio(settings.recents.speakerBadge)
    // Built again only when something it carries changes, not on every recomposition of the
    // screen (E17): each new env made every theme and card recompose.
    val env = remember(
        settings, thumbnails, accent, onAccent, selecting, state.root, glass, memory, peekCount, audio,
        onOpen, onClose, onPopUp, onUnsplit, onCombine, onAppInfo, onTogglePin, onPeekSplit, onCloseAll,
        onSavePair, onAddToHome, onPlaceAtEdge, isMuted, onToggleMute, onNativeSplit, onNativeThird, wide, onCover,
    ) { RecentsEnv(
        settings = settings,
        thumbnails = thumbnails,
        accent = accent,
        onAccent = onAccent,
        corner = settings.recents.cornerDp.dp,
        canClose = state.root && settings.recents.rootClose,
        selecting = selecting,
        picked = picking.picked,
        drag = drag,
        glass = glass,
        onTap = { card ->
            if (!picking.selecting) {
                // The app grows out of this card as it comes back.
                drag.bounds[card.key]?.let { r ->
                    val at = IntArray(2)
                    hostView.getLocationOnScreen(at)
                    LaunchOrigin.rect(
                        hostView,
                        android.graphics.Rect(
                            at[0] + r.left.toInt(), at[1] + r.top.toInt(),
                            at[0] + r.right.toInt(), at[1] + r.bottom.toInt(),
                        ),
                    )
                }
                onOpen(card)
            } else {
                picking.tap(card)
            }
        },
        onClose = onClose,
        onSplit = { card -> picking.start(card, count = 2, peek = false) },
        onPopUp = onPopUp,
        onUnsplit = onUnsplit,
        onCombine = onCombine,
        onAppInfo = onAppInfo,
        onTogglePin = onTogglePin,
        menu = menu,
        onSplitThree = { card -> picking.start(card, count = 3, peek = false) },
        onPeekPick = { card -> picking.start(card, count = peekCount, peek = true) },
        onPeekSplit = onPeekSplit,
        onCloseAll = onCloseAll,
        onSavePair = onSavePair,
        onAddToHome = onAddToHome,
        speakerBadge = settings.recents.speakerBadge,
        memory = memory,
        audioActive = { pkg -> audio.active(pkg) },
        audioPlaying = { pkg -> audio.playing(pkg) },
        onToggleAudio = { pkg -> audio.toggle(pkg) },
        onPlaceAtEdge = onPlaceAtEdge,
        isMuted = isMuted,
        onToggleMute = onToggleMute,
        onNativeSplit = onNativeSplit,
        onNativeThird = onNativeThird,
    ).also { it.coverScreen = onCover } }

    // Back undoes what is under way first (the menu, a drag, picking apps); only then does it leave.
    androidx.activity.compose.BackHandler(enabled = menu.isOpen || drag.card != null || selecting) {
        when {
            menu.isOpen -> menu.close()
            drag.card != null -> drag.cancel()
            else -> picking.cancel()
        }
    }
    // Leaving with Android's back gesture: the cards shrink back with the finger, then Recents
    // closes (let go early and they come back).
    val backShrink = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.activity.compose.PredictiveBackHandler(enabled = !menu.isOpen && drag.card == null && !selecting) { progress ->
        try {
            progress.collect { backShrink.snapTo(it.progress) }
            onDismiss()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            backShrink.animateTo(0f)
            throw e
        }
    }

    CompositionLocalProvider(LocalPressStyle provides PressStyle(settings.recents.pressAnimation, accent, settings.recents.animationSpeed)) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val k = backShrink.value
                val sc = 1f - 0.1f * k
                scaleX = sc; scaleY = sc
                alpha = 1f - 0.4f * k
            }
            // The backdrop fades in with the cards (read while drawing: no recomposition per frame).
            .drawBehind { drawRect(Color(0xFF05090A).copy(alpha = settings.recents.backdropDim * backdrop())) }
            // A tap on empty space goes back home.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (picking.selecting) picking.cancel() else onDismiss()
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 16.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                RecentsHeader(
                    state = state, settings = settings, picking = picking, dragging = drag.card != null,
                    wide = wide, side = side, accent = accent, onAccent = onAccent, glass = glass,
                    confirmAll = confirmAll, onConfirmAll = { confirmAll = it }, onCloseAll = onCloseAll,
                )
                RecentsCardsArea(state, settings, env, onCover, side, accent, Modifier.weight(1f))
            }
            if (selecting) {
                SplitViewBar(picking, settings, peekCount, side, accent, onAccent, glass, onOpenTogether)
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

