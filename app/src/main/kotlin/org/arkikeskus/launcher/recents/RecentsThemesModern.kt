package org.arkikeskus.launcher.recents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Four Recents designs made around gestures, all with borderless cards:
 *  - Stack: a vertical deck, newest at the bottom under your thumb; swipe a card sideways to close.
 *  - Arc: cards on a curve you spin with a flick; flick one up to close.
 *  - Bento: how recently you used an app is its size — a big tile, two medium, then icon tiles.
 *  - Filmstrip: one big picture you swipe through, with a strip of all apps below to scrub.
 * Every card keeps every gesture from Settings ▸ Recents ▸ Gestures (cardModifier).
 */

// screenAspect() and sizeScale() are shared with the classic looks (RecentsThemes.kt).

// ---------------------------------------------------------------------------- Stack

@Composable
internal fun StackTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.5f else 0.9f) * env.sizeScale()).coerceIn(180.dp, maxWidth * 0.95f)
        val pictureHeight = (cardWidth / aspect * 0.8f).coerceAtMost(maxHeight * 0.6f)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            // The newest at the bottom, where the thumb is; older ones further up the deck.
            reverseLayout = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(-(pictureHeight * 0.55f)),
            contentPadding = PaddingValues(top = pictureHeight * 0.6f, bottom = 16.dp),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                Box(
                    Modifier
                        .width(cardWidth)
                        // Newer cards lie on top of older ones.
                        .zIndex((cards.size - index).toFloat())
                        .graphicsLayer {
                            val info = list.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.key == card.key }
                            if (item != null) {
                                val span = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
                                // 0 at the bottom of the screen, 1 at the top: the further up, the
                                // further back — smaller, tipped away, a little dimmer.
                                val d = (item.offset / span).coerceIn(0f, 1f)
                                val k = 1f - 0.18f * d
                                scaleX = k; scaleY = k
                                rotationX = 10f * d
                                cameraDistance = 22f * density
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                alpha = 1f - 0.4f * d
                            }
                        },
                ) {
                    OverlayCard(
                        card, env,
                        env.cardModifier(card, index, 1, closeAxis = Orientation.Horizontal),
                        pictureHeight,
                        showLabel = false,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Arc

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArcTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.4f else 0.68f) * env.sizeScale()).coerceIn(150.dp, maxWidth * 0.9f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.6f)
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                Box(
                    Modifier
                        .width(cardWidth)
                        // On a wheel: away from the middle a card tips outward and drops down the curve.
                        .graphicsLayer {
                            val info = list.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.key == card.key }
                            if (item != null) {
                                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val d = ((item.offset + item.size / 2f) - center) / item.size.coerceAtLeast(1)
                                val a = abs(d).coerceAtMost(2.5f)
                                rotationZ = d.coerceIn(-2.5f, 2.5f) * 9f
                                translationY = a * a * item.size * 0.09f
                                val k = 1f - 0.1f * a.coerceAtMost(1.5f)
                                scaleX = k; scaleY = k
                                alpha = 1f - 0.28f * a.coerceAtMost(1.8f)
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                        },
                ) {
                    OverlayCard(card, env, env.cardModifier(card, index, cards.size), pictureHeight)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Bento

@Composable
internal fun BentoTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val columns = if (wide) 6 else 4
        val gap = 12.dp
        val tile = (maxWidth - gap * (columns - 1)) / columns
        val hero = (maxWidth / aspect * 0.5f).coerceIn(160.dp, maxHeight * 0.42f) * env.sizeScale().coerceIn(0.7f, 1.4f)
        val medium = hero * 0.62f
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            itemsIndexed(
                cards,
                key = { _, c -> c.key },
                span = { i, _ -> GridItemSpan(if (i == 0) maxLineSpan else if (i <= 2) maxLineSpan / 2 else 1) },
            ) { index, card ->
                val mod = env.cardModifier(card, index, columns, sideSwipes = true)
                when {
                    index == 0 -> OverlayCard(card, env, mod, hero)
                    index <= 2 -> OverlayCard(card, env, mod, medium, showLabel = false)
                    else -> BentoTile(card, env, mod, tile * 1.25f)
                }
            }
        }
    }
}

/** A small Bento tile: the app's icon big on a dimmed corner of its picture, its name below. */
@Composable
private fun BentoTile(card: RecentsCard, env: RecentsEnv, cardModifier: Modifier, height: Dp) {
    val picked = card in env.picked
    val pick by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = 0.7f), label = "pick")
    val shape = RoundedCornerShape(env.corner * 0.8f)
    Column(cardModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .drawBehind {
                    if (pick > 0.01f) {
                        val g = 3.dp.toPx()
                        drawRoundRect(
                            env.accent.copy(alpha = pick), Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g),
                            CornerRadius((env.corner * 0.8f).toPx() + g), style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                .graphicsLayer { val k = 1f - 0.06f * pick; scaleX = k; scaleY = k; this.shape = shape; clip = true; shadowElevation = 10f },
            contentAlignment = Alignment.Center,
        ) {
            CardPicture(card, env, Modifier.fillMaxSize(), env.corner * 0.8f, iconSize = 34.dp)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.45f)))))
            Box(Modifier.graphicsLayer { shadowElevation = 12f; this.shape = androidx.compose.foundation.shape.CircleShape; clip = false }) {
                AppBadge(card.apps.firstOrNull(), 40.dp)
            }
        }
        Text(
            card.label, color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------- Filmstrip

@Composable
internal fun FilmstripTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    if (cards.isEmpty()) return
    val aspect = screenAspect()
    val pager = rememberPagerState(pageCount = { cards.size })
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The strip follows the big picture: the current app stays in view below.
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val stripHeight = 92.dp
        val cardWidth = (maxWidth * (if (wide) 0.56f else 0.84f)).coerceAtLeast(180.dp)
        val bigHeight = (cardWidth / aspect).coerceAtMost(maxHeight - stripHeight - 64.dp).coerceAtLeast(160.dp)
        val side = (maxWidth - cardWidth) / 2
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = side),
                pageSpacing = 16.dp,
                key = { cards[it].key },
            ) { page ->
                val card = cards[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(cardWidth)
                            .graphicsLayer {
                                val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                                val k = 1f - 0.08f * off
                                scaleX = k; scaleY = k
                                alpha = 1f - 0.35f * off
                            },
                    ) {
                        OverlayCard(card, env, env.cardModifier(card, page, 1), bigHeight)
                    }
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().height(stripHeight),
                state = strip,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                itemsIndexed(cards, key = { _, c -> c.key }) { i, card ->
                    val current = pager.currentPage == i
                    val lift by animateFloatAsState(if (current) 1f else 0f, spring(dampingRatio = 0.7f), label = "strip")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 54.dp, height = 70.dp)
                                .graphicsLayer {
                                    val k = 0.92f + 0.12f * lift
                                    scaleX = k; scaleY = k
                                    alpha = 0.6f + 0.4f * lift
                                    shape = RoundedCornerShape(12.dp); clip = true
                                }
                                .clickable(remember { MutableInteractionSource() }, null) {
                                    scope.launch { pager.animateScrollToPage(i) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            CardPicture(card, env, Modifier.fillMaxSize(), 12.dp, iconSize = 26.dp)
                        }
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(width = 18.dp * lift, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(env.accent),
                        )
                    }
                }
            }
        }
    }
}
