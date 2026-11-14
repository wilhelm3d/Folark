package org.arkikeskus.launcher.recents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.R
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.RecentsTheme

// What Recents shows around the cards (split out of RecentsScreen.kt): picking apps for a split
// view, the header with its buttons, the cards in the chosen theme, and the bar while picking.

/**
 * Picking apps to open together (Split view): whether it is under way, what is picked, and how the
 * picked apps will open. They open by themselves once [openAt] are picked: two side by side, three
 * in a three-way split, or as peek view ([peekPick]); with 0 they are picked freely and opened with
 * the Open button.
 */
@Stable
internal class RecentsPicking {
    var selecting by mutableStateOf(false)
        private set
    val picked = mutableStateListOf<RecentsCard>()
    var openAt by mutableIntStateOf(2)
        private set
    var peekPick by mutableStateOf(false)
        private set

    /** Peek view of three: the app that shows big in the middle, chosen by tapping it. */
    var peekBig by mutableStateOf<RecentsCard?>(null)

    /** Starts picking with [card]: [count] apps then open together, as peek view when [peek]. */
    fun start(card: RecentsCard, count: Int, peek: Boolean) {
        selecting = true
        peekPick = peek
        picked.clear()
        picked.add(card)
        openAt = count
    }

    /** The header's Split view / Cancel button: picking starts afresh, or stops. */
    fun toggle() {
        selecting = !selecting
        picked.clear()
    }

    /** Stops picking and forgets what was picked. */
    fun cancel() {
        selecting = false
        openAt = 2
        peekPick = false
        picked.clear()
    }

    /** A card tapped while picking: picked, or no longer; never more than the mode takes, nor a split. */
    fun tap(card: RecentsCard) {
        if (card in picked) picked.remove(card)
        else if ((if (openAt > 0) openAt else MAX_PICK) > picked.size && !card.isSplit) picked.add(card)
    }

    /** A mode chip: [count] apps (0 = freely), as peek view when [peek]. */
    fun chooseMode(count: Int, peek: Boolean) {
        openAt = count
        peekPick = peek
        // Peek view and two side by side take two: keep the first two picked.
        while (count in 1 until picked.size) picked.removeAt(picked.lastIndex)
    }

    /** The picked apps were opened: the next pick starts with two side by side again. */
    fun opened() {
        openAt = 2
        peekPick = false
    }

    /** Three apps for peek view, and which one is big is still to be tapped. */
    val choosingPeekBig: Boolean get() = peekPick && openAt >= 3 && picked.size >= 3
}

/**
 * Opens the picked apps by themselves once the mode has enough of them: after a short pause (so the
 * last pick shows), two side by side, three in a three-way split, or as peek view (three only once
 * the big one is tapped).
 */
@Composable
internal fun OpenWhenPicked(
    picking: RecentsPicking,
    onOpenTogether: (List<RecentsCard>) -> Unit,
    onOpenThree: (List<RecentsCard>) -> Unit,
    onPeek: (main: RecentsCard, peek: RecentsCard) -> Unit,
    onPeekThree: (List<RecentsCard>) -> Unit,
) {
    LaunchedEffect(picking.picked.size, picking.openAt, picking.peekPick, picking.peekBig) {
        if (picking.choosingPeekBig && picking.peekBig == null) return@LaunchedEffect
        if (picking.openAt > 0 && picking.picked.size >= picking.openAt) {
            delay(220)
            val chosen = picking.picked.toList()
            when {
                picking.peekPick && chosen.size >= 3 -> {
                    // The chosen big app first (it opens in the middle), the others at the edges.
                    val big = picking.peekBig ?: chosen[0]
                    onPeekThree(listOf(big) + chosen.filter { it.key != big.key })
                    picking.peekBig = null
                }
                picking.peekPick -> onPeek(chosen[0], chosen[1])
                picking.openAt >= 3 -> onOpenThree(chosen)
                else -> onOpenTogether(chosen)
            }
            picking.opened()
        }
    }
}

/**
 * The title with what is open (or what is going on), and the Split view and Close all buttons.
 * Close all asks once more first when Settings ▸ Recents says so ([confirmAll] is that second step).
 */
@Composable
internal fun RecentsHeader(
    state: RecentsState,
    settings: LauncherSettings,
    picking: RecentsPicking,
    dragging: Boolean,
    wide: Boolean,
    side: Dp,
    accent: Color,
    onAccent: Color,
    glass: Boolean,
    confirmAll: Boolean,
    onConfirmAll: (Boolean) -> Unit,
    onCloseAll: () -> Unit,
) {
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
                    dragging -> stringResource(R.string.recents_drag_hint)
                    else -> stringResource(R.string.recents_count, count)
                },
                color = Muted, fontSize = 12.sp, fontFamily = Mono,
            )
        }
        val selecting = picking.selecting
        if (state.cards.size >= 2 && (settings.recents.btnSplitView || selecting)) {
            Pill(
                text = stringResource(if (selecting) R.string.recents_cancel else R.string.recents_split_view),
                color = if (selecting) onAccent else accent,
                background = if (selecting) accent else Color.Transparent,
                filled = selecting,
                glass = glass,
                onClick = { picking.toggle() },
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
                    if (settings.recents.confirmCloseAll && !confirmAll) onConfirmAll(true)
                    else { onConfirmAll(false); onCloseAll() }
                },
            )
        }
    }
}

/**
 * The cards in the theme of Settings ▸ Recents, or why there are none: no permission to list them,
 * a list Android won't hand over, or nothing open. Rows of cards (iPad, Carousel, Deck, …) run to
 * the screen's edges; the rest keep the [side] margin.
 */
@Composable
internal fun RecentsCardsArea(
    state: RecentsState,
    settings: LauncherSettings,
    env: RecentsEnv,
    onCover: Boolean,
    side: Dp,
    accent: Color,
    modifier: Modifier,
) {
    val edgeToEdge = settings.recents.theme in EDGE_TO_EDGE_THEMES
    val ticks = rememberScrollTicks(settings.recents.scrollTicks)
    Box(modifier.fillMaxWidth().padding(horizontal = if (edgeToEdge) 0.dp else side).then(ticks)) {
        when {
            !state.canList -> NeedsGrant(accent)
            state.unreadable -> Text(
                stringResource(R.string.recents_unreadable),
                color = Muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
            )
            !state.loading && state.cards.isEmpty() -> Text(
                stringResource(R.string.recents_nothing_open_long),
                color = Muted, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
            )
            else -> when (settings.recents.theme) {
                RecentsTheme.IPAD -> IpadTheme(state.cards, env)
                RecentsTheme.CAROUSEL -> CarouselTheme(state.cards, env)
                RecentsTheme.DECK -> DeckTheme(state.cards, env)
                RecentsTheme.COMPACT -> CompactTheme(state.cards, env, if (onCover) 1 else settings.recents.columnsInner.coerceAtMost(3))
                RecentsTheme.SWITCHER -> SwitcherTheme(state.cards, env)
                RecentsTheme.STACK -> StackTheme(state.cards, env)
                RecentsTheme.ARC -> ArcTheme(state.cards, env)
                RecentsTheme.PORTRAIT -> PortraitTheme(state.cards, env)
                RecentsTheme.SPOTLIGHT -> SpotlightTheme(state.cards, env)
                RecentsTheme.GALLERY -> GalleryTheme(state.cards, env)
                else -> FolarkTheme(state.cards, env, if (onCover) settings.recents.columnsOuter else settings.recents.columnsInner)
            }
        }
    }
}

/** Themes whose cards scroll sideways and so run to the screen's edges. */
private val EDGE_TO_EDGE_THEMES = setOf(
    RecentsTheme.IPAD,
    RecentsTheme.CAROUSEL,
    RecentsTheme.DECK,
    RecentsTheme.ARC,
    RecentsTheme.PORTRAIT,
    RecentsTheme.SPOTLIGHT,
    RecentsTheme.GALLERY,
)

/**
 * The bar at the bottom while picking: how the picked apps open (the chosen mode filled; each opens
 * by itself once enough are picked), for peek view of three which one is big, and what is picked so
 * far, with Open when picking freely.
 */
@Composable
internal fun BoxScope.SplitViewBar(
    picking: RecentsPicking,
    settings: LauncherSettings,
    peekCount: Int,
    side: Dp,
    accent: Color,
    onAccent: Color,
    glass: Boolean,
    onOpenTogether: (List<RecentsCard>) -> Unit,
) {
    val picked = picking.picked
    val labels = slotLabels(picked.size, settings.recents.thirdInSplit)
    val barSurface = Modifier.recentsSurface(RoundedCornerShape(999.dp), glass, false, accent, solid = Color(0xEB0B1214))
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(barSurface.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val modes = buildList {
                add(Triple(R.string.recents_mode_two, 2, false))
                if (settings.launch.threeAppSplit) add(Triple(R.string.recents_mode_three, 3, false))
                add(Triple(R.string.recents_mode_peek, peekCount, true))
                add(Triple(R.string.recents_mode_free, 0, false))
            }
            modes.forEach { (label, count, peek) ->
                val on = picking.openAt == count && picking.peekPick == peek
                Pill(
                    text = stringResource(label),
                    color = if (on) onAccent else Ink,
                    background = if (on) accent else Color.White.copy(alpha = 0.08f),
                    filled = true, glass = false,
                    // Peek as a symbol: one big pane, the others peeking in at the edges.
                    glyph = if (peek) ({ c -> PeekGlyph(c, three = count >= 3) }) else null,
                    onClick = { picking.chooseMode(count, peek) },
                )
            }
        }
        if (picking.choosingPeekBig) {
            // P3: which app shows big in the middle — tap it.
            Row(
                barSurface.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.recents_pick_peek_which), color = accent, fontSize = 13.sp)
                picked.toList().forEach { card ->
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { picking.peekBig = card }.padding(4.dp)) {
                        AppBadge(card.apps.firstOrNull(), 36.dp)
                    }
                }
            }
        }
        Row(
            barSurface.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val openAt = picking.openAt
            val peekPick = picking.peekPick
            Text(
                if (peekPick && openAt >= 3 && picked.size < 3) stringResource(R.string.recents_pick_peek_three)
                else if (peekPick && picked.size < 2) stringResource(R.string.recents_pick_peek)
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
