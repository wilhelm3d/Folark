package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

private const val OPAQUE = 0xFF000000.toInt()

private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

private fun hexOf(argb: Int): String = String.format("%06X", argb and 0xFFFFFF)

/** Parses "RRGGBB" (with or without '#') to an opaque ARGB int, or null while the text is incomplete. */
private fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or OPAQUE }
}

private fun onColor(argb: Int): Color =
    if (Color(argb).luminance() > 0.5f) Color.Black else Color.White

/**
 * A row of colour swatches plus a trailing "custom" swatch that opens the full picker. A selected
 * colour that is not among [presets] shows on the custom swatch, so the current choice is always
 * visible. Colours are compared without alpha.
 */
@Composable
fun ColorSwatchRow(
    title: String,
    presets: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalExpressivePalette.current
    val selectedRgb = selected or OPAQUE
    val isCustom = presets.none { (it or OPAQUE) == selectedRgb }
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(text = title, color = palette.text, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            presets.forEach { preset ->
                val argb = preset or OPAQUE
                Swatch(
                    fill = Brush.linearGradient(listOf(Color(argb), Color(argb))),
                    selected = argb == selectedRgb,
                    markColor = onColor(argb),
                    onClick = { onPick(argb) },
                )
            }
            Swatch(
                fill = if (isCustom) {
                    Brush.linearGradient(listOf(Color(selectedRgb), Color(selectedRgb)))
                } else {
                    Brush.sweepGradient(RAINBOW)
                },
                selected = isCustom,
                markColor = if (isCustom) onColor(selectedRgb) else Color.White,
                showAdd = !isCustom,
                contentDescription = stringResource(R.string.settings_color_custom),
                onClick = onCustom,
            )
        }
    }
}

@Composable
private fun Swatch(
    fill: Brush,
    selected: Boolean,
    markColor: Color,
    onClick: () -> Unit,
    showAdd: Boolean = false,
    contentDescription: String? = null,
) {
    val palette = LocalExpressivePalette.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Accent else palette.faint.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, contentDescription, tint = markColor, modifier = Modifier.size(20.dp))
            showAdd -> Icon(Icons.Rounded.Add, contentDescription, tint = markColor, modifier = Modifier.size(20.dp))
            else -> Unit
        }
    }
}

private val RAINBOW = listOf(
    Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00B0FF),
    Color(0xFFD500F9), Color(0xFFFF1744),
)

/** Full colour picker: hue / saturation / brightness sliders and a hex field, with a live preview. */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial or OPAQUE, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    val argb = hsvToArgb(hue, saturation, brightness)

    fun syncHex() {
        hex = hexOf(hsvToArgb(hue, saturation, brightness))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHi,
        title = { Text(title, color = palette.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(argb)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#" + hexOf(argb), color = onColor(argb), fontWeight = FontWeight.SemiBold)
                }
                PickerSlider(
                    label = stringResource(R.string.settings_color_hue),
                    value = hue,
                    range = 0f..360f,
                    track = Brush.horizontalGradient(
                        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(hsvToArgb(it, 1f, 1f)) },
                    ),
                    onChange = { hue = it; syncHex() },
                )
                PickerSlider(
                    label = stringResource(R.string.settings_color_saturation),
                    value = saturation,
                    range = 0f..1f,
                    track = Brush.horizontalGradient(
                        listOf(Color(hsvToArgb(hue, 0f, brightness)), Color(hsvToArgb(hue, 1f, brightness))),
                    ),
                    onChange = { saturation = it; syncHex() },
                )
                PickerSlider(
                    label = stringResource(R.string.settings_color_brightness),
                    value = brightness,
                    range = 0f..1f,
                    track = Brush.horizontalGradient(
                        listOf(Color.Black, Color(hsvToArgb(hue, saturation, 1f))),
                    ),
                    onChange = { brightness = it; syncHex() },
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        hex = input.uppercase().filter { it in "0123456789ABCDEF#" }.take(7)
                        parseHex(hex)?.let { parsed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_color_hex)) },
                    prefix = { Text("#") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(argb) }) {
                Text(stringResource(R.string.settings_apply), color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel), color = palette.dim) }
        },
    )
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onChange: (Float) -> Unit,
) {
    val palette = LocalExpressivePalette.current
    Column {
        Text(label, color = palette.dim, fontSize = 12.sp)
        Box(contentAlignment = Alignment.Center) {
            // The gradient shows what the slider does; the slider's own track is hidden over it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(track),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = palette.text,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}
