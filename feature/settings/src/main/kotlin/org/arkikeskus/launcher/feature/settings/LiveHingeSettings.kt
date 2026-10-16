package org.arkikeskus.launcher.feature.settings

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.liveHingeCurve
import org.arkikeskus.launcher.ui.component.liveHingeEffect
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

/** Whether the phone reports its hinge angle (the live-hinge transition needs it). */
internal fun hasHingeSensor(context: Context): Boolean = runCatching {
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
}.getOrDefault(false)

/**
 * Inner display card, under the fold transition when "Live hinge" is chosen: a preview that plays
 * the effect (tap to replay), the effect itself, its speed, the glass (blur, frost) and the hinge
 * tuning. These are shared by both displays.
 */
@Composable
internal fun LiveHingeRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    // Slider values while dragging, so the preview follows the finger before they are saved.
    var speed by remember { mutableStateOf<Float?>(null) }
    var blur by remember { mutableStateOf<Float?>(null) }
    var frost by remember { mutableStateOf<Float?>(null) }
    val shownSpeed = speed ?: s.liveHingeSpeed
    val shownBlur = blur ?: s.liveHingeBlurDp.toFloat()
    val shownFrost = frost ?: s.liveHingeFrost

    ExpressiveRowDivider()
    LiveHingePreview(s = s, speed = shownSpeed, blurDp = shownBlur, frost = shownFrost)
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_live_hinge_style),
        options = LIVE_STYLES.map { (style, label) -> style to stringResource(label) },
        selected = s.liveHingeStyle,
        onSelect = viewModel::setLiveHingeStyle,
    )
    ExpressiveRowDivider()

    ExpressiveSliderRow(
        title = stringResource(R.string.settings_live_hinge_speed),
        subtitle = stringResource(R.string.settings_live_hinge_speed_desc),
        value = s.liveHingeSpeed,
        valueRange = LauncherSettings.MIN_LIVE_HINGE_SPEED..LauncherSettings.MAX_LIVE_HINGE_SPEED,
        onValueChange = { viewModel.setLiveHingeSpeed((it * 10f).roundToInt() / 10f) },
        valueDisplay = "%.1fx".format(s.liveHingeSpeed),
        onDragValue = { speed = it },
    )
    ExpressiveRowDivider()

    run {
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_live_hinge_blur),
            subtitle = stringResource(R.string.settings_live_hinge_blur_desc),
            value = s.liveHingeBlurDp.toFloat(),
            valueRange = 0f..LauncherSettings.MAX_LIVE_HINGE_BLUR_DP.toFloat(),
            onValueChange = { viewModel.setLiveHingeBlur(it.roundToInt()) },
            valueDisplay = "%d dp".format(s.liveHingeBlurDp),
            onDragValue = { blur = it },
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_live_hinge_frost),
            subtitle = stringResource(R.string.settings_live_hinge_frost_desc),
            value = s.liveHingeFrost,
            valueRange = 0f..LauncherSettings.MAX_LIVE_HINGE_FROST,
            onValueChange = { viewModel.setLiveHingeFrost((it * 100f).roundToInt() / 100f) },
            valueDisplay = "%d%%".format((s.liveHingeFrost * 100).roundToInt()),
            onDragValue = { frost = it },
        )
    }
    ExpressiveRowDivider()

    ExpressiveSliderRow(
        title = stringResource(R.string.settings_live_hinge_end),
        subtitle = stringResource(R.string.settings_live_hinge_end_desc),
        value = s.liveHingeEndAngle.toFloat(),
        valueRange = LauncherSettings.MIN_LIVE_HINGE_END_ANGLE.toFloat()..180f,
        onValueChange = { viewModel.setLiveHingeEndAngle(it.roundToInt()) },
        valueDisplay = "%d°".format(s.liveHingeEndAngle),
    )
    ExpressiveRowDivider()
    ExpressiveSliderRow(
        title = stringResource(R.string.settings_live_hinge_smoothing),
        subtitle = stringResource(R.string.settings_live_hinge_smoothing_desc),
        value = s.liveHingeSmoothingMs.toFloat(),
        valueRange = 0f..LauncherSettings.MAX_LIVE_HINGE_SMOOTHING_MS.toFloat(),
        onValueChange = { viewModel.setLiveHingeSmoothing(it.roundToInt()) },
        valueDisplay = "%d ms".format(s.liveHingeSmoothingMs),
    )
}

/** The mini home screen opening with the chosen effect, as if the phone were unfolded slowly. */
@Composable
private fun LiveHingePreview(s: LauncherSettings, speed: Float, blurDp: Float, frost: Float) {
    val palette = LocalExpressivePalette.current
    val opening = remember { Animatable(1f) }
    var plays by remember { mutableIntStateOf(0) }
    LaunchedEffect(plays, s.liveHingeStyle) {
        opening.snapTo(0f)
        delay(PREVIEW_PAUSE_MS)
        opening.animateTo(1f, tween(PREVIEW_OPEN_MS, easing = LinearEasing))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { plays++ },
        ) {
            Box(
                Modifier.liveHingeEffect(
                    progress = { liveHingeCurve(opening.value, speed) },
                    style = s.liveHingeStyle,
                    blurDp = blurDp,
                    frost = frost,
                ),
            ) { HomeThemePreview(s) }
        }
        Text(
            text = stringResource(R.string.settings_live_hinge_preview),
            color = palette.dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

private val LIVE_STYLES = listOf(
    LauncherSettings.LIVE_HINGE_STYLE_PAGES to R.string.settings_live_hinge_style_pages,
    LauncherSettings.LIVE_HINGE_STYLE_WATER to R.string.settings_live_hinge_style_water,
    LauncherSettings.LIVE_HINGE_STYLE_INK to R.string.settings_live_hinge_style_ink,
    LauncherSettings.LIVE_HINGE_STYLE_FADE to R.string.settings_live_hinge_style_fade,
    LauncherSettings.LIVE_HINGE_STYLE_DROP to R.string.settings_live_hinge_style_drop,
    LauncherSettings.LIVE_HINGE_STYLE_DOORS to R.string.settings_live_hinge_style_doors,
    LauncherSettings.LIVE_HINGE_STYLE_PORTAL to R.string.settings_live_hinge_style_portal,
    LauncherSettings.LIVE_HINGE_STYLE_ZOOM to R.string.settings_live_hinge_style_zoom,
)

private const val PREVIEW_PAUSE_MS = 350L
private const val PREVIEW_OPEN_MS = 1_600
