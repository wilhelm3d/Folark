package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.IconNudge
import org.arkikeskus.launcher.ui.component.IconNudgeEvent
import org.arkikeskus.launcher.ui.component.iconNudge
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

/**
 * Notifications card: an app's icon nudges when a new notification arrives while home is in view
 * (and on the taskbar). A live preview plays the chosen effect (following the softness slider as it
 * moves), and a row below plays all seven side by side; tap one to choose it.
 */
@Composable
internal fun IconNudgeRows(s: LauncherSettings, viewModel: SettingsViewModel, previewIcon: ImageBitmap?) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_icon_nudge),
        subtitle = stringResource(R.string.settings_icon_nudge_desc),
        checked = s.iconNudge,
        onCheckedChange = viewModel::setIconNudge,
        shared = true,
    )
    if (!s.iconNudge) return
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_icon_nudge_taskbar),
        checked = s.iconNudgeTaskbar,
        onCheckedChange = viewModel::setIconNudgeTaskbar,
        shared = true,
    )
    ExpressiveRowDivider()

    // The slider's value while dragging, so the preview follows the finger before it is saved.
    var dragSoftness by remember { mutableStateOf<Float?>(null) }
    val softness = dragSoftness ?: s.iconNudgeSoftness
    NudgePreview(previewIcon, s.iconNudgeEffect, softness)
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_icon_nudge_effect),
        options = NUDGE_LABELS.map { (key, label) -> key to stringResource(label) },
        selected = s.iconNudgeEffect,
        onSelect = viewModel::setIconNudgeEffect,
    )
    if (s.iconNudgeEffect in SOFTENABLE) {
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_icon_nudge_softness),
            subtitle = stringResource(R.string.settings_icon_nudge_softness_desc),
            value = s.iconNudgeSoftness,
            valueRange = 0f..1f,
            onValueChange = {
                viewModel.setIconNudgeSoftness((it * 100f).roundToInt() / 100f)
                dragSoftness = null
            },
            valueDisplay = "%d%%".format((softness * 100).roundToInt()),
            onDragValue = { dragSoftness = it },
        )
    }
    ExpressiveRowDivider()
    NudgeGallery(previewIcon, s.iconNudgeEffect, softness, onPick = viewModel::setIconNudgeEffect)
}

/** The chosen effect on a big icon, replaying every few seconds and on a tap. */
@Composable
private fun NudgePreview(icon: ImageBitmap?, effect: String, softness: Float) {
    val event = rememberLoopingNudge(effect, startDelayMs = 0L)
    var tapped by remember { mutableStateOf<IconNudgeEvent?>(null) }
    var taps by remember { mutableLongStateOf(0L) }
    val accent = LocalAccent.current
    val shown = listOfNotNull(event.value, tapped).maxByOrNull { it.time }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PREVIEW_BACKDROP)
                .clickable {
                    taps++
                    tapped = IconNudgeEvent("tap", accent.toArgb(), System.currentTimeMillis(), -taps)
                },
        ) {
            NudgeIcon(icon, shown, effect, softness, 60.dp)
        }
        Text(
            text = stringResource(R.string.settings_icon_nudge_preview),
            color = LocalExpressivePalette.current.dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

/** Every effect at once, each replaying in turn; the chosen one is ringed. Tap to choose. */
@Composable
private fun NudgeGallery(icon: ImageBitmap?, selected: String, softness: Float, onPick: (String) -> Unit) {
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            stringResource(R.string.settings_icon_nudge_all),
            color = palette.text,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PREVIEW_BACKDROP)
                .padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            NUDGE_LABELS.forEachIndexed { index, (key, label) ->
                // Staggered so the row ripples along instead of everything moving at once.
                val event = rememberLoopingNudge(key, startDelayMs = index * GALLERY_STAGGER_MS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(key) }
                        .padding(vertical = 6.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .then(
                                if (key == selected) Modifier.border(2.dp, accent, CircleShape) else Modifier,
                            ),
                    ) {
                        NudgeIcon(icon, event.value, key, softness, 30.dp)
                    }
                    Text(
                        stringResource(label),
                        color = if (key == selected) accent else Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NudgeIcon(icon: ImageBitmap?, event: IconNudgeEvent?, effect: String, softness: Float, size: Dp) {
    val accent = LocalAccent.current
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.iconNudge(event, effect, softness).size(size))
    } else {
        Box(Modifier.iconNudge(event, effect, softness).size(size).clip(CircleShape).background(accent))
    }
}

/** A nudge that fires every few seconds while shown (after [startDelayMs]), in the accent colour. */
@Composable
private fun rememberLoopingNudge(effect: String, startDelayMs: Long): androidx.compose.runtime.State<IconNudgeEvent?> {
    val accent = LocalAccent.current.toArgb()
    val state = remember { mutableStateOf<IconNudgeEvent?>(null) }
    LaunchedEffect(effect, accent) {
        delay(startDelayMs)
        var n = 0L
        while (true) {
            state.value = IconNudgeEvent("preview:$effect", accent, System.currentTimeMillis(), ++n)
            delay(IconNudge.DURATION_MS + PREVIEW_PAUSE_MS)
        }
    }
    return state
}

private const val PREVIEW_PAUSE_MS = 1_300L
private const val GALLERY_STAGGER_MS = 260L
private val PREVIEW_BACKDROP = Color(0xFF2B3550)

/** The effects the softness slider changes. */
private val SOFTENABLE = setOf(LauncherSettings.NUDGE_GLOW, LauncherSettings.NUDGE_PULSE, LauncherSettings.NUDGE_TINT)

private val NUDGE_LABELS = listOf(
    LauncherSettings.NUDGE_BOUNCE to R.string.settings_nudge_bounce,
    LauncherSettings.NUDGE_WIGGLE to R.string.settings_nudge_wiggle,
    LauncherSettings.NUDGE_PULSE to R.string.settings_nudge_pulse,
    LauncherSettings.NUDGE_GLOW to R.string.settings_nudge_glow,
    LauncherSettings.NUDGE_RIPPLE to R.string.settings_nudge_ripple,
    LauncherSettings.NUDGE_JELLY to R.string.settings_nudge_jelly,
    LauncherSettings.NUDGE_TINT to R.string.settings_nudge_tint,
)
