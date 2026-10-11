package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.arkikeskus.launcher.data.MotoLinks

/** Settings ▸ Appearance ▸ Motorola: a tap on the weather opens Motorola Weather (from HomeScreen). */
val LocalMotoWeatherTap = compositionLocalOf { false }

/** Makes the weather text open Motorola Weather, when that is switched on and the phone has it. */
@Composable
internal fun Modifier.motoWeatherTap(): Modifier {
    if (!LocalMotoWeatherTap.current) return this
    val context = LocalContext.current
    val available = remember { MotoLinks.available(context, MotoLinks.WEATHER) }
    if (!available) return this
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null) { MotoLinks.open(context, MotoLinks.WEATHER) }
}
