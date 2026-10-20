package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.recentsEnter
import org.arkikeskus.launcher.ui.component.recentsSelect
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

private val ENTER_LABELS = listOf(
    LauncherSettings.RECENTS_ENTER_RISE to R.string.settings_recents_enter_rise,
    LauncherSettings.RECENTS_ENTER_ZOOM to R.string.settings_recents_enter_zoom,
    LauncherSettings.RECENTS_ENTER_CASCADE to R.string.settings_recents_enter_cascade,
    LauncherSettings.RECENTS_ENTER_FLIP to R.string.settings_recents_enter_flip,
    LauncherSettings.RECENTS_ENTER_FAN to R.string.settings_recents_enter_fan,
    LauncherSettings.RECENTS_ENTER_FOCUS to R.string.settings_recents_enter_focus,
    LauncherSettings.RECENTS_ENTER_NONE to R.string.settings_recents_anim_none,
)

private val SELECT_LABELS = listOf(
    LauncherSettings.RECENTS_SELECT_POP to R.string.settings_recents_select_pop,
    LauncherSettings.RECENTS_SELECT_RING to R.string.settings_recents_select_ring,
    LauncherSettings.RECENTS_SELECT_LIFT to R.string.settings_recents_select_lift,
    LauncherSettings.RECENTS_SELECT_TILT to R.string.settings_recents_select_tilt,
    LauncherSettings.RECENTS_SELECT_GLOW to R.string.settings_recents_select_glow,
    LauncherSettings.RECENTS_SELECT_JIGGLE to R.string.settings_recents_select_jiggle,
    LauncherSettings.RECENTS_SELECT_NONE to R.string.settings_recents_anim_none,
)

/**
 * Settings ▸ Recents ▸ Animations: how the cards come in, how a picked card answers in split view,
 * and how fast; with a live preview (tap a card to pick it, Replay to see the entrance again).
 */
@Composable
internal fun RecentsMotionRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    var dragSpeed by remember { mutableStateOf<Float?>(null) }
    val speed = dragSpeed ?: s.recentsAnimationSpeed
    RecentsMotionPreview(s.recentsEnterAnimation, s.recentsSelectAnimation, speed)
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_recents_enter),
        options = ENTER_LABELS.map { (key, label) -> key to stringResource(label) },
        selected = s.recentsEnterAnimation,
        onSelect = viewModel::setRecentsEnterAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_recents_select),
        options = SELECT_LABELS.map { (key, label) -> key to stringResource(label) },
        selected = s.recentsSelectAnimation,
        onSelect = viewModel::setRecentsSelectAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSliderRow(
        title = stringResource(R.string.settings_recents_anim_speed),
        value = s.recentsAnimationSpeed,
        onValueChange = viewModel::setRecentsAnimationSpeed,
        valueRange = LauncherSettings.MIN_RECENTS_ANIM_SPEED..LauncherSettings.MAX_RECENTS_ANIM_SPEED,
        valueDisplay = "%.1f×".format(speed),
        onDragValue = { dragSpeed = it },
    )
}

@Composable
private fun RecentsMotionPreview(enter: String, select: String, speed: Float) {
    val palette = LocalExpressivePalette.current
    val accent = LocalAccent.current
    var play by remember { mutableIntStateOf(0) }
    val picked = remember { mutableStateListOf<Int>() }
    val tints = listOf(Color(0xFF16302E), Color(0xFF2A1E33), Color(0xFF1C2A22), Color(0xFF1F2427))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B1214))
                .padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tints.forEachIndexed { i, tint ->
                    val slot = picked.indexOf(i)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(112.dp)
                            .recentsEnter(enter, i, columns = 4, speed = speed, playKey = play)
                            .recentsSelect(select, slot >= 0, slot, picked.size, accent, 14.dp, speed)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF131D20))
                            .clickable {
                                if (slot >= 0) picked.remove(i) else if (picked.size < 4) picked.add(i)
                            }
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(accent.copy(alpha = 0.7f)))
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f)))
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(9.dp)).background(tint))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_recents_anim_preview),
                color = palette.faint, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
                    .clickable { play++; picked.clear() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text(stringResource(R.string.settings_recents_anim_replay), color = Color.White, fontSize = 13.sp) }
        }
    }
}
