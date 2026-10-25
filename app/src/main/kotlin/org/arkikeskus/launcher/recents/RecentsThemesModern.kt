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
import androidx.compose.foundation.layout.Row
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
 * Recents designs with borderless cards:
 *  - Stack: a vertical deck, newest at the bottom under your thumb; swipe a card sideways to close.
 *  - Arc: cards on a curve you spin with a flick; flick one up to close.
 *  - Portrait: tall rounded cards you snap sideways, the icon and name above each.
 *  - Spotlight: the middle card large and centred, its icon and name above it.
 *  - Gallery: a row of larger cards, two or three in view, made for the unfolded screen.
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


// ---------------------------------------------------------------------------- Portrait / Spotlight / Gallery

/**
 * A card with the app's icon and name above it, then its picture (OverlayCard without a label).
 * Shared by all three card looks. [centered] puts the icon and name in the middle (Spotlight)
 * rather than at the start (Portrait / Gallery). Honours the icon, shadow and label-size settings.
 */
@Composable
private fun SwitcherCard(
    card: RecentsCard,
    env: RecentsEnv,
    index: Int,
    count: Int,
    width: Dp,
    pictureHeight: Dp,
    iconSize: Dp,
    centered: Boolean,
) {
    val showName = env.settings.recents.overlayLabels
    Column(
        Modifier.width(width),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (env.cardIcons || showName) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
            ) {
                if (env.cardIcons) card.apps.forEach { AppBadge(it, iconSize) }
                if (showName) {
                    Text(
                        card.label,
                        color = Ink,
                        fontSize = 13.sp * env.labelScale,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OverlayCard(card, env, env.cardModifier(card, index, count), pictureHeight, showLabel = false)
    }
}

/** Tall rounded cards, snapped sideways, the icon and name above each one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PortraitTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.5f else 0.8f) * env.sizeScale()).coerceIn(200.dp, maxWidth * 0.92f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.74f)
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                SwitcherCard(card, env, index, cards.size, cardWidth, pictureHeight, 26.dp, centered = false)
            }
        }
    }
}

/** The middle card large and centred, its icon and name above it; neighbours peek in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotlightTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.46f else 0.74f) * env.sizeScale()).coerceIn(190.dp, maxWidth * 0.9f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.7f)
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                SwitcherCard(card, env, index, cards.size, cardWidth, pictureHeight, 30.dp, centered = true)
            }
        }
    }
}

/** A row of larger cards, two or three in view, for the unfolded screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryTheme(cards: List<RecentsCard>, env: RecentsEnv) {
    val aspect = screenAspect()
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val cardWidth = (maxWidth * (if (wide) 0.44f else 0.82f) * env.sizeScale()).coerceIn(220.dp, maxWidth * 0.92f)
        val pictureHeight = (cardWidth / aspect).coerceAtMost(maxHeight * 0.8f)
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = edgeMarginWide(wide)),
        ) {
            itemsIndexed(cards, key = { _, c -> c.key }) { index, card ->
                SwitcherCard(card, env, index, cards.size, cardWidth, pictureHeight, 28.dp, centered = false)
            }
        }
    }
}

/** The side margin for the tablet row (a touch wider on the big screen). */
private fun edgeMarginWide(wide: Boolean): Dp = if (wide) 28.dp else 16.dp
