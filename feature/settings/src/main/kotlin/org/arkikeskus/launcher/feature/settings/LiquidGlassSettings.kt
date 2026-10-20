package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import kotlin.math.roundToInt

/**
 * Settings ▸ Appearance ▸ Liquid Glass everywhere: one switch and one level for the drawer,
 * folders, menus, the clock widget, the taskbar and Recents, with a preview of the glass.
 */
@Composable
internal fun LiquidGlassEverywhereSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_liquid_glass),
        icon = Icons.Rounded.BlurOn,
        subtitle = stringResource(R.string.settings_liquid_glass_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_liquid_glass_everywhere),
            subtitle = stringResource(R.string.settings_liquid_glass_everywhere_desc),
            checked = s.liquidGlassEverywhere,
            onCheckedChange = viewModel::setLiquidGlassEverywhere,
        )
        if (!s.liquidGlassEverywhere) return@ExpressiveCategoryContainer
        ExpressiveRowDivider()
        var dragLevel by remember { mutableStateOf<Float?>(null) }
        val level = dragLevel ?: s.liquidGlassLevel
        GlassPreview(level)
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_liquid_glass_level),
            subtitle = stringResource(R.string.settings_liquid_glass_level_desc),
            value = s.liquidGlassLevel,
            onValueChange = viewModel::setLiquidGlassLevel,
            valueRange = 0f..1f,
            valueDisplay = "${(level * 100).roundToInt()} %",
            onDragValue = { dragLevel = it },
        )
    }
}

/** A glass card over a colourful backdrop, at [level]. */
@Composable
private fun GlassPreview(level: Float) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accent, Color(0xFF3B5BDB), Color(0xFF0B1214), Color(0xFFE8590C)))),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(70.dp)
                .liquidGlass(
                    shape = shape,
                    tint = Color.Black.copy(alpha = 0.6f - 0.42f * level),
                    highlight = 0.5f + 0.5f * level,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.settings_liquid_glass), color = Color.White, fontSize = 15.sp)
        }
    }
}
