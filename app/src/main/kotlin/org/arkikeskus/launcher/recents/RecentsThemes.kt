package org.arkikeskus.launcher.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** The phone's own shape (width / height), so app pictures keep their proportions. */
@Composable
private fun screenAspect(): Float {
    val c = LocalConfiguration.current
    return (c.screenWidthDp.toFloat() / c.screenHeightDp.coerceAtLeast(1)).coerceIn(0.4f, 2f)
}

/** How much bigger than normal the cards are (Settings ▸ Recents ▸ Card height; 300 = normal). */
private fun RecentsEnv.sizeScale(): Float = settings.recentsCardHeight / 300f

// ---------------------------------------------------------------------------- Folark: cards in a grid

@Composable
internal fun FolarkTheme(cards: List<RecentsCard>, env: RecentsEnv, columns: Int) {
    val corner = env.corner
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(env.cardModifier(card, index, columns))
                    .envSurface(env, RoundedCornerShape(corner), card in env.picked)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardTitle(card, env)
                CardPicture(card, env, Modifier.fillMaxWidth().height(env.settings.recentsCardHeight.dp), (corner.value * 0.6f).dp)
                CardActions(card, env)
            }
        }
    }
}

// ---------------------------------------------------------------------------- iPad: big windows in a row

/** Like iPadOS: each app as a big window picture with its icon and name above, side by side. */
@Composable
internal fun IpadTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pictureHeight = (maxHeight * 0.62f * env.sizeScale()).coerceIn(140.dp, maxHeight * 0.86f)
        val oneWidth = pictureHeight * aspect
        LazyRow(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                val width = if (card.isSplit) oneWidth * 1.4f else oneWidth
                Column(
                    Modifier.width(width).then(env.cardModifier(card, index, cards.size)),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        card.apps.forEach { AppBadge(it, 28.dp) }
                        Text(card.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        CardTrailing(card, env)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(pictureHeight)
                            .envSurface(env, RoundedCornerShape(env.corner * 0.75f), card in env.picked)
                            .padding(if (env.glass) 4.dp else 0.dp),
                    ) {
                        CardPicture(card, env, Modifier.fillMaxSize(), env.corner * 0.65f, 72.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Carousel: one big card in the middle

/** Like Android's own: big cards in a row, the middle one in front, its neighbours smaller. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CarouselTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.56f else 0.8f) * env.sizeScale()).coerceIn(160.dp, maxWidth * 0.92f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.76f)
        val side = (maxWidth - cardWidth) / 2
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = side),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                Column(
                    Modifier
                        .width(cardWidth)
                        // Smaller and dimmer away from the middle (read while drawing: smooth scrolling).
                        .graphicsLayer {
                            val info = list.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.key == card.key }
                            if (item != null) {
                                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val mid = item.offset + item.size / 2f
                                val d = (abs(mid - center) / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val k = 1f - 0.14f * d
                                scaleX = k; scaleY = k
                                alpha = 1f - 0.45f * d
                            }
                        }
                        .then(env.cardModifier(card, index, cards.size)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        card.apps.forEach { AppBadge(it, 34.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(card.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (env.settings.recentsShowTime) Text(timeLabel(LocalContext.current, card.inactiveMs), color = Muted, fontSize = 11.sp, fontFamily = Mono)
                        }
                        CardTrailing(card, env)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(pictureHeight)
                            .envSurface(env, RoundedCornerShape(env.corner), card in env.picked)
                            .padding(if (env.glass) 5.dp else 0.dp),
                    ) { CardPicture(card, env, Modifier.fillMaxSize(), env.corner * 0.85f, 80.dp) }
                    CardActions(card, env)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Deck: overlapping cards

/** Like the iPhone: cards laid over each other in a row, each a little in front of the last. */
@Composable
internal fun DeckTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.36f else 0.66f) * env.sizeScale()).coerceIn(140.dp, maxWidth * 0.9f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.78f)
        LazyRow(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(-(cardWidth * 0.42f)),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                Column(
                    Modifier
                        .width(cardWidth)
                        .graphicsLayer {
                            rotationY = -7f
                            cameraDistance = 18f * density
                            shadowElevation = 28f
                        }
                        .then(env.cardModifier(card, index, cards.size)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        card.apps.forEach { AppBadge(it, 26.dp) }
                        Text(card.label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        CardTrailing(card, env, showClose = false)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(pictureHeight)
                            .envSurface(env, RoundedCornerShape(env.corner * 1.2f), card in env.picked)
                            .padding(if (env.glass) 4.dp else 0.dp),
                    ) { CardPicture(card, env, Modifier.fillMaxSize(), env.corner * 1.1f, 72.dp) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Compact: a list

/** A tidy list: icon, name, when, a small picture; flick sideways to close. */
@Composable
internal fun CompactTheme(cards: List<RecentsCard>, env: RecentsEnv, columns: Int) {
    val rowHeight = (88f * env.sizeScale().coerceIn(0.7f, 1.5f)).dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .then(env.cardModifier(card, index, columns, closeAxis = Orientation.Horizontal))
                    .envSurface(env, RoundedCornerShape(env.corner * 0.7f), card in env.picked)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) { CardTitle(card, env, iconSize = 40.dp) }
                CardPicture(
                    card, env,
                    Modifier.width(rowHeight * 0.75f * card.tasks.size).fillMaxSize(),
                    env.corner * 0.4f, 28.dp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- Switcher: big icons

/** Like a computer's app switcher: big icons on one panel, no pictures. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwitcherTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val icon: Dp = (72f * env.sizeScale().coerceIn(0.7f, 1.6f)).dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FlowRow(
            Modifier
                .envSurface(env, RoundedCornerShape(env.corner * 1.3f), false)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            cards.forEachIndexed { index, card ->
                Column(
                    Modifier
                        .width(icon + 36.dp)
                        .then(env.cardModifier(card, index, 6))
                        .envSurface(env, RoundedCornerShape(22.dp), card in env.picked)
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                        card.apps.forEach { AppBadge(it, if (card.isSplit) icon * 0.7f else icon) }
                    }
                    Text(card.label, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    val slot = env.slotLabel(card)
                    if (slot != null || env.canClose) Box(Modifier.height(30.dp)) { CardTrailing(card, env) }
                    else Spacer(Modifier.size(0.dp))
                }
            }
        }
    }
}
