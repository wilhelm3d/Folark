package org.arkikeskus.launcher.feature.settings

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

private val THEMES = listOf(
    LauncherSettings.RECENTS_THEME_FOLARK to R.string.settings_recents_theme_folark,
    LauncherSettings.RECENTS_THEME_IPAD to R.string.settings_recents_theme_ipad,
    LauncherSettings.RECENTS_THEME_CAROUSEL to R.string.settings_recents_theme_carousel,
    LauncherSettings.RECENTS_THEME_DECK to R.string.settings_recents_theme_deck,
    LauncherSettings.RECENTS_THEME_COMPACT to R.string.settings_recents_theme_compact,
    LauncherSettings.RECENTS_THEME_SWITCHER to R.string.settings_recents_theme_switcher,
    LauncherSettings.RECENTS_THEME_STACK to R.string.settings_recents_theme_stack,
    LauncherSettings.RECENTS_THEME_ARC to R.string.settings_recents_theme_arc,
    LauncherSettings.RECENTS_THEME_PORTRAIT to R.string.settings_recents_theme_portrait,
    LauncherSettings.RECENTS_THEME_SPOTLIGHT to R.string.settings_recents_theme_spotlight,
    LauncherSettings.RECENTS_THEME_GALLERY to R.string.settings_recents_theme_gallery,
)

/**
 * Settings ▸ Recents ▸ Look: the theme (each tile a small sketch of it), Liquid Glass, and
 * dragging one app onto another (split view or attached view).
 */
@Composable
internal fun RecentsLookRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    Text(stringResource(R.string.settings_recents_theme), color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        THEMES.forEach { (key, label) ->
            val chosen = s.recents.theme == key
            Column(
                Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (chosen) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) accent else palette.faint.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .clickable { viewModel.setRecentsTheme(key) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B1214)),
                ) { sketch(key, s.recents.glass, accent) }
                Text(stringResource(label), color = palette.text, fontSize = 12.sp)
            }
        }
    }
    ExpressiveRowDivider()

    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_recents_glass),
        subtitle = stringResource(R.string.settings_recents_glass_desc),
        checked = s.recents.glass,
        onCheckedChange = viewModel::setRecentsGlass,
    )
    if (s.recents.glass) {
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_glass_blur),
            value = s.recents.glassBlur.toFloat(),
            onValueChange = { viewModel.setRecentsGlassBlur(it.roundToInt()) },
            valueRange = 0f..LauncherSettings.MAX_RECENTS_GLASS_BLUR.toFloat(),
            valueDisplay = "${s.recents.glassBlur} dp",
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_glass_tint),
            value = s.recents.glassTint,
            onValueChange = viewModel::setRecentsGlassTint,
            valueRange = LauncherSettings.MIN_RECENTS_GLASS_TINT..LauncherSettings.MAX_RECENTS_GLASS_TINT,
            valueDisplay = "${(s.recents.glassTint * 100).roundToInt()} %",
        )
    }
    ExpressiveRowDivider()

    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_recents_drag),
        subtitle = stringResource(R.string.settings_recents_drag_desc),
        checked = s.recents.dragDrop,
        onCheckedChange = viewModel::setRecentsDragDrop,
    )
    if (s.recents.dragDrop) {
        ExpressiveRowDivider()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_recents_attached_side),
            options = listOf(
                LauncherSettings.RECENTS_ATTACHED_LEFT to stringResource(R.string.settings_recents_attached_left),
                LauncherSettings.RECENTS_ATTACHED_RIGHT to stringResource(R.string.settings_recents_attached_right),
            ),
            selected = s.recents.attachedSide,
            onSelect = viewModel::setRecentsAttachedSide,
        )
        ExpressiveRowDivider()
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_recents_attached_peek),
            subtitle = stringResource(R.string.settings_recents_attached_peek_desc),
            value = s.recents.attachedPeek,
            onValueChange = viewModel::setRecentsAttachedPeek,
            valueRange = LauncherSettings.MIN_RECENTS_ATTACHED_PEEK..LauncherSettings.MAX_RECENTS_ATTACHED_PEEK,
            valueDisplay = "${(s.recents.attachedPeek * 100).roundToInt()} %",
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_recents_attached_native),
            subtitle = stringResource(R.string.settings_recents_attached_native_desc),
            checked = s.recents.attachedNative,
            onCheckedChange = viewModel::setRecentsAttachedNative,
        )
    }
}

/** A tiny drawing of each theme's layout. */
private fun DrawScope.sketch(theme: String, glass: Boolean, accent: Color) {
    val w = size.width
    val h = size.height
    fun card(x: Float, y: Float, cw: Float, ch: Float, r: Float = 6f, front: Boolean = false) {
        if (glass) {
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.08f)), startY = y, endY = y + ch),
                Offset(x, y), Size(cw, ch), CornerRadius(r),
            )
            drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(x, y), Size(cw, ch), CornerRadius(r), style = Stroke(1.2f))
        } else {
            drawRoundRect(if (front) Color(0xFF2A4448) else Color(0xFF1C2A2D), Offset(x, y), Size(cw, ch), CornerRadius(r))
        }
    }
    fun dot(x: Float, y: Float, r: Float) = drawCircle(accent, r, Offset(x, y))
    when (theme) {
        LauncherSettings.RECENTS_THEME_IPAD -> {
            val cw = w * 0.36f
            for (i in 0..2) {
                val x = w * 0.06f + i * (cw + w * 0.08f)
                dot(x + 4f, h * 0.2f, 3f)
                card(x, h * 0.3f, cw, h * 0.6f)
            }
        }
        LauncherSettings.RECENTS_THEME_CAROUSEL -> {
            card(-w * 0.18f, h * 0.22f, w * 0.3f, h * 0.56f)
            card(w * 0.88f, h * 0.22f, w * 0.3f, h * 0.56f)
            card(w * 0.2f, h * 0.12f, w * 0.6f, h * 0.76f, 8f, front = true)
            dot(w * 0.26f, h * 0.06f, 3f)
        }
        LauncherSettings.RECENTS_THEME_DECK -> {
            for (i in 0..3) card(w * 0.06f + i * w * 0.2f, h * 0.14f, w * 0.34f, h * 0.72f, 8f, front = i == 3)
        }
        LauncherSettings.RECENTS_THEME_COMPACT -> {
            for (i in 0..3) {
                val y = h * 0.08f + i * h * 0.22f
                card(w * 0.06f, y, w * 0.88f, h * 0.18f, 4f)
                dot(w * 0.14f, y + h * 0.09f, 3.5f)
            }
        }
        LauncherSettings.RECENTS_THEME_STACK -> {
            // A deck: the newest at the bottom, older ones smaller further up.
            for (i in 3 downTo 0) {
                val k = 1f - 0.12f * i
                val cw = w * 0.78f * k
                card((w - cw) / 2f, h * (0.5f - 0.13f * i), cw, h * 0.42f * k, 7f, front = i == 0)
            }
        }
        LauncherSettings.RECENTS_THEME_ARC -> {
            for (i in -1..1) {
                rotate(i * 12f, Offset(w / 2f, h * 1.6f)) {
                    card(w * 0.35f, h * 0.16f + kotlin.math.abs(i) * h * 0.05f, w * 0.3f, h * 0.62f, 7f, front = i == 0)
                }
            }
        }
        LauncherSettings.RECENTS_THEME_PORTRAIT -> {
            // A tall very-rounded card in front, neighbours peeking, icon above.
            card(-w * 0.16f, h * 0.22f, w * 0.28f, h * 0.62f, 9f)
            card(w * 0.88f, h * 0.22f, w * 0.28f, h * 0.62f, 9f)
            dot(w * 0.3f, h * 0.1f, 3f)
            card(w * 0.26f, h * 0.18f, w * 0.48f, h * 0.72f, 11f, front = true)
        }
        LauncherSettings.RECENTS_THEME_SPOTLIGHT -> {
            // The middle card centred, its icon centred above.
            card(-w * 0.12f, h * 0.26f, w * 0.26f, h * 0.54f, 7f)
            card(w * 0.86f, h * 0.26f, w * 0.26f, h * 0.54f, 7f)
            dot(w * 0.5f, h * 0.1f, 3f)
            card(w * 0.28f, h * 0.2f, w * 0.44f, h * 0.68f, 8f, front = true)
        }
        LauncherSettings.RECENTS_THEME_GALLERY -> {
            // Two larger cards side by side, each with its icon above.
            dot(w * 0.28f, h * 0.14f, 2.5f)
            card(w * 0.08f, h * 0.22f, w * 0.4f, h * 0.66f, 7f, front = true)
            dot(w * 0.72f, h * 0.14f, 2.5f)
            card(w * 0.52f, h * 0.22f, w * 0.4f, h * 0.66f, 7f, front = true)
        }
        LauncherSettings.RECENTS_THEME_SWITCHER -> {
            card(w * 0.08f, h * 0.2f, w * 0.84f, h * 0.6f, 10f)
            for (i in 0..3) drawCircle(accent.copy(alpha = if (i == 1) 1f else 0.55f), h * 0.11f, Offset(w * 0.22f + i * w * 0.19f, h * 0.5f))
        }
        else -> {
            for (r in 0..1) for (c in 0..2) {
                card(w * 0.06f + c * w * 0.31f, h * 0.08f + r * h * 0.46f, w * 0.26f, h * 0.38f, 5f)
            }
        }
    }
}
