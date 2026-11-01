package org.arkikeskus.launcher.feature.settings

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.SplitPickerLook
import kotlin.math.roundToInt

/**
 * Settings ▸ Recents ▸ Split screen: how two apps are put side by side (the engine), and how the
 * split picker looks, with a live preview.
 */
@Composable
internal fun SplitScreenSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val look = s.recents.picker
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_split_screen),
        icon = Icons.Rounded.HorizontalSplit,
        subtitle = stringResource(R.string.settings_split_screen_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_engine),
            options = listOf(
                LauncherSettings.SPLIT_ENGINE_INTENT_ROUTING to stringResource(R.string.settings_split_engine_intent),
                LauncherSettings.SPLIT_ENGINE_CLASSIC to stringResource(R.string.settings_split_engine_classic),
                LauncherSettings.SPLIT_ENGINE_LSPOSED to stringResource(R.string.settings_split_engine_lsposed),
                LauncherSettings.SPLIT_ENGINE_ROOT to stringResource(R.string.settings_split_engine_root),
            ),
            selected = s.recents.splitEngine,
            onSelect = viewModel::setRecentsSplitEngine,
        )
        Hint(
            stringResource(
                when (s.recents.splitEngine) {
                    LauncherSettings.SPLIT_ENGINE_CLASSIC -> R.string.settings_split_engine_classic_desc
                    LauncherSettings.SPLIT_ENGINE_LSPOSED -> R.string.settings_split_engine_lsposed_desc
                    LauncherSettings.SPLIT_ENGINE_ROOT -> R.string.settings_split_engine_root_desc
                    else -> R.string.settings_split_engine_auto_desc
                },
            ),
        )
    }

    // While a slider is dragged the preview follows the finger (the saved value catches up after).
    var dragIcon by remember { mutableStateOf<Float?>(null) }
    var dragOpacity by remember { mutableStateOf<Float?>(null) }
    val shown = look.copy(
        iconSize = dragIcon?.roundToInt() ?: look.iconSize,
        opacity = dragOpacity ?: look.opacity,
    )

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_split_picker),
        icon = Icons.Rounded.Apps,
        subtitle = stringResource(R.string.settings_split_picker_desc),
        sharedByBothDisplays = true,
    ) {
        SplitPickerPreview(shown)
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_split_picker_columns),
            value = look.columns,
            min = SplitPickerLook.MIN_COLUMNS,
            max = SplitPickerLook.MAX_COLUMNS,
            onValueChange = viewModel::setSplitPickerColumns,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_split_picker_icon),
            value = look.iconSize.toFloat(),
            onValueChange = { viewModel.setSplitPickerIconSize(it.roundToInt()) },
            valueRange = SplitPickerLook.MIN_ICON.toFloat()..SplitPickerLook.MAX_ICON.toFloat(),
            valueDisplay = "${shown.iconSize} dp",
            onDragValue = { dragIcon = it },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_labels),
            checked = look.labels,
            onCheckedChange = viewModel::setSplitPickerLabels,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_folders),
            subtitle = stringResource(R.string.settings_split_picker_folders_desc),
            checked = look.folders,
            onCheckedChange = viewModel::setSplitPickerFolders,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_picker_search),
            checked = look.search,
            onCheckedChange = viewModel::setSplitPickerSearch,
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_recents),
            options = listOf(
                SplitPickerLook.RECENTS_OFF to stringResource(R.string.settings_split_picker_recents_off),
                SplitPickerLook.RECENTS_ICONS to stringResource(R.string.settings_split_picker_recents_icons),
                SplitPickerLook.RECENTS_PREVIEWS to stringResource(R.string.settings_split_picker_recents_previews),
            ),
            selected = look.recents,
            onSelect = viewModel::setSplitPickerRecents,
        )
        if (look.recents == SplitPickerLook.RECENTS_PREVIEWS) {
            Hint(stringResource(R.string.settings_split_picker_recents_previews_desc))
        }
        if (look.recents != SplitPickerLook.RECENTS_OFF) {
            ExpressiveRowDivider()
            ExpressiveStepperRow(
                title = stringResource(R.string.settings_split_picker_recents_count),
                value = look.recentsCount,
                min = SplitPickerLook.MIN_RECENTS,
                max = SplitPickerLook.MAX_RECENTS,
                onValueChange = viewModel::setSplitPickerRecentsCount,
            )
        }
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_sort),
            options = listOf(
                SplitPickerLook.SORT_NAME to stringResource(R.string.settings_split_picker_sort_name),
                SplitPickerLook.SORT_USAGE to stringResource(R.string.settings_split_picker_sort_usage),
            ),
            selected = look.sort,
            onSelect = viewModel::setSplitPickerSort,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_split_picker_opacity),
            value = look.opacity,
            onValueChange = viewModel::setSplitPickerOpacity,
            valueRange = SplitPickerLook.MIN_OPACITY..SplitPickerLook.MAX_OPACITY,
            valueDisplay = "${(shown.opacity * 100).roundToInt()}%",
            onDragValue = { dragOpacity = it },
        )
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_split_picker_open_anim),
            options = listOf(
                SplitPickerLook.OPEN_ZOOM to stringResource(R.string.settings_split_picker_open_zoom),
                SplitPickerLook.OPEN_FADE to stringResource(R.string.settings_split_picker_open_fade),
                SplitPickerLook.OPEN_NONE to stringResource(R.string.settings_split_picker_open_none),
            ),
            selected = look.openAnim,
            onSelect = viewModel::setSplitPickerOpenAnim,
        )
        Hint(
            stringResource(
                when (look.openAnim) {
                    SplitPickerLook.OPEN_FADE -> R.string.settings_split_picker_open_fade_desc
                    SplitPickerLook.OPEN_NONE -> R.string.settings_split_picker_open_none_desc
                    else -> R.string.settings_split_picker_open_zoom_desc
                },
            ),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

// --------------------------------------------------------------------- live preview

/** A small split screen: an app on the left, the picker (as set) on the right. */
@Composable
private fun SplitPickerPreview(look: SplitPickerLook) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.settings_split_picker_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(22.dp))
                // A colourful wallpaper, so the picker's see-through background shows.
                .background(Brush.linearGradient(listOf(Color(0xFF3A2E8C), Color(0xFF0E6E73), Color(0xFF8C3A5A))))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The first app: a plain app window.
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color(0xFFECEFF3)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.85f)))
                repeat(6) { i ->
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.9f else 0.65f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFC9CFD6)))
                }
            }
            PickerMock(look, accent, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * The picker in the preview. With an opening animation set, it plays on a loop: the highlighted
 * app is "tapped" and opens the way it will on the phone (Zoom grows it out of its icon, Fade fades
 * it in); Off shows no animation, as Android's own is used then.
 */
@Composable
private fun PickerMock(look: SplitPickerLook, accent: Color, modifier: Modifier) {
    val scale = 0.42f
    val icon = (look.iconSize * scale).dp
    val gap = 4.dp
    val density = LocalDensity.current
    var box by remember { mutableStateOf(Rect.Zero) }
    var tile by remember { mutableStateOf<Rect?>(null) }
    val p = remember { Animatable(0f) }
    val anim = if (ValueAnimator.areAnimatorsEnabled()) look.openAnim else SplitPickerLook.OPEN_NONE
    LaunchedEffect(anim) {
        p.snapTo(0f)
        if (anim == SplitPickerLook.OPEN_NONE) return@LaunchedEffect
        while (true) {
            delay(PREVIEW_IDLE_MS)
            p.animateTo(1f, tween(PREVIEW_OPEN_MS, easing = FastOutSlowInEasing))
            delay(PREVIEW_HOLD_MS)
            p.animateTo(0f, tween(PREVIEW_RESET_MS))
        }
    }
    val zoom = anim == SplitPickerLook.OPEN_ZOOM
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0E12).copy(alpha = look.opacity))
            .onGloballyPositioned { box = it.boundsInRoot() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .graphicsLayer {
                    val q = if (zoom) p.value else 0f
                    scaleX = 1f - 0.06f * q
                    scaleY = 1f - 0.06f * q
                    alpha = 1f - 0.7f * q
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (look.search) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)))
            }
            if (look.recents != SplitPickerLook.RECENTS_OFF) {
                val previews = look.recents == SplitPickerLook.RECENTS_PREVIEWS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(look.recentsCount.coerceAtMost(if (previews) 4 else 5)) { i ->
                        if (previews) {
                            Box(Modifier.width(16.dp).height(24.dp).clip(RoundedCornerShape(3.dp)).background(mockColor(i + 3).copy(alpha = 0.8f)))
                        } else {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(mockColor(i + 3)))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            }
            Box(Modifier.fillMaxSize()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val rows = 6
                    var index = 0
                    repeat(rows) { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(look.columns) { c ->
                                val folder = look.folders && r == 0 && c < 2
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (folder) {
                                        Box(
                                            Modifier.size(icon).clip(RoundedCornerShape(icon * 0.3f)).background(Color.White.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                repeat(2) { y ->
                                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                        repeat(2) { x -> Box(Modifier.size(icon * 0.3f).clip(CircleShape).background(mockColor(c * 4 + y * 2 + x))) }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (!(look.folders && r == 0)) {
                                        val first = index == 0
                                        Box(
                                            Modifier
                                                .size(icon)
                                                .then(if (first) Modifier.onGloballyPositioned { tile = it.boundsInRoot() } else Modifier)
                                                .clip(CircleShape)
                                                .background(if (first) accent else mockColor(index)),
                                        )
                                        index++
                                    } else {
                                        Spacer(Modifier.size(icon))
                                    }
                                    if (look.labels) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(Modifier.width(icon * 0.9f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.45f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // The app opening (Zoom: out of its icon; Fade: over the whole picker).
        val from = tile
        if (p.value > 0f && from != null && box.width > 0f) {
            val end = Rect(Offset.Zero, box.size)
            val start = from.translate(-box.topLeft)
            val r = if (zoom) lerp(start, end, p.value) else end
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                        .size(r.width.toDp(), r.height.toDp())
                        .graphicsLayer { alpha = if (zoom) 1f else p.value }
                        .clip(RoundedCornerShape(if (zoom) icon * 0.5f * (1f - p.value) else 0.dp))
                        .background(if (zoom) lerpColor(accent, Color(0xFF1B2127), (p.value * 1.6f).coerceAtMost(1f)) else Color(0xFF1B2127)),
                    contentAlignment = Alignment.Center,
                ) {
                    val size = if (zoom) icon + (34.dp - icon) * p.value else 34.dp
                    Box(Modifier.size(size).clip(CircleShape).background(accent))
                }
            }
        }
    }
}

private const val PREVIEW_IDLE_MS = 1300L
private const val PREVIEW_OPEN_MS = 420
private const val PREVIEW_HOLD_MS = 900L
private const val PREVIEW_RESET_MS = 260

private val MOCK = listOf(
    Color(0xFFEF6C6C), Color(0xFFF2B35A), Color(0xFF6CC28A), Color(0xFF5A9BF2),
    Color(0xFFB07CF2), Color(0xFF4FC3C7), Color(0xFFF27CB4), Color(0xFF9AA5B1),
)

private fun mockColor(i: Int): Color = MOCK[i.mod(MOCK.size)]
