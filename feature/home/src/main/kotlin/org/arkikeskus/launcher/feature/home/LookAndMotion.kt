package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.LauncherSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The look of one home widget, chosen from its edit bar: how round its corners are clipped, how far
 * it is inset from its cells, and how see-through it is. Works on any widget, Folark's own or an
 * app's, because it is applied from outside (a clip can only round corners, never square them off).
 */
@Immutable
data class WidgetLook(
    /** Corner radius in dp; [DEFAULT_RADIUS] leaves the widget's own corners alone. */
    val radius: Int = DEFAULT_RADIUS,
    /** Inset from the widget's cells, in dp. */
    val padding: Int = 0,
    /** 1 = opaque. */
    val opacity: Float = 1f,
) {
    val isDefault: Boolean get() = radius == DEFAULT_RADIUS && padding == 0 && opacity >= 1f

    fun serialize(): String = "$radius,$padding,$opacity"

    companion object {
        const val DEFAULT_RADIUS = -1
        const val MAX_RADIUS = 48
        const val MAX_PADDING = 24
        const val MIN_OPACITY = 0.2f

        fun parse(raw: String): WidgetLook? {
            val parts = raw.split(',')
            if (parts.size != 3) return null
            return WidgetLook(
                radius = (parts[0].toIntOrNull() ?: return null).coerceIn(DEFAULT_RADIUS, MAX_RADIUS),
                padding = (parts[1].toIntOrNull() ?: return null).coerceIn(0, MAX_PADDING),
                opacity = (parts[2].toFloatOrNull() ?: return null).coerceIn(MIN_OPACITY, 1f),
            )
        }
    }
}

/** Every customised widget look, by home row id. */
val LocalWidgetLooks = compositionLocalOf<Map<Long, WidgetLook>> { emptyMap() }

/** Applies [look] to a widget's cell box; a null or default look changes nothing. */
fun Modifier.widgetLook(look: WidgetLook?): Modifier {
    if (look == null || look.isDefault) return this
    var m = this
    if (look.padding > 0) m = m.padding(look.padding.dp)
    if (look.radius >= 0) m = m.clip(RoundedCornerShape(look.radius.dp))
    if (look.opacity < 1f) m = m.alpha(look.opacity)
    return m
}

/** The "Widget style" sheet opened from the widget edit bar. Changes are saved as each slider is released. */
@Composable
fun WidgetLookDialog(look: WidgetLook, onChange: (WidgetLook?) -> Unit, onDismiss: () -> Unit) {
    var radius by remember { mutableStateOf(look.radius.toFloat()) }
    var padding by remember { mutableStateOf(look.padding.toFloat()) }
    var opacity by remember { mutableStateOf(look.opacity) }
    fun commit() = onChange(WidgetLook(radius.roundToInt(), padding.roundToInt(), opacity).takeUnless { it.isDefault })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_look_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.widget_look_corners) + ": " +
                        if (radius < 0f) stringResource(R.string.widget_look_default) else "${radius.roundToInt()} dp",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    onValueChangeFinished = { commit() },
                    valueRange = WidgetLook.DEFAULT_RADIUS.toFloat()..WidgetLook.MAX_RADIUS.toFloat(),
                )
                Text(
                    text = stringResource(R.string.widget_look_padding) + ": ${padding.roundToInt()} dp",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = padding,
                    onValueChange = { padding = it },
                    onValueChangeFinished = { commit() },
                    valueRange = 0f..WidgetLook.MAX_PADDING.toFloat(),
                )
                Text(
                    text = stringResource(R.string.widget_look_opacity) + ": ${(opacity * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = { commit() },
                    valueRange = WidgetLook.MIN_OPACITY..1f,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_gallery_done)) } },
        dismissButton = {
            TextButton(onClick = {
                radius = WidgetLook.DEFAULT_RADIUS.toFloat(); padding = 0f; opacity = 1f
                onChange(null)
            }) { Text(stringResource(R.string.widget_look_reset)) }
        },
    )
}

/**
 * The home pages' swipe effect. [offset] is how far the page is from the settled position, in
 * pages: positive once it has moved off to the left, negative while it is still to the right.
 */
fun GraphicsLayerScope.pageScrollTransform(effect: String, offset: Float) {
    val o = offset.coerceIn(-1f, 1f)
    val d = abs(o)
    if (d < 0.001f) return
    when (effect) {
        LauncherSettings.PAGE_EFFECT_FADE -> alpha = 1f - d
        LauncherSettings.PAGE_EFFECT_ZOOM -> {
            val scale = 1f - d * 0.18f
            scaleX = scale
            scaleY = scale
            alpha = 1f - d * 0.6f
        }
        LauncherSettings.PAGE_EFFECT_CUBE -> {
            cameraDistance = 20f * density
            transformOrigin = TransformOrigin(if (o > 0f) 1f else 0f, 0.5f)
            rotationY = -o * 90f
        }
        LauncherSettings.PAGE_EFFECT_STACK -> if (o < 0f) {
            // The incoming page waits in place and grows out from under the one sliding away.
            translationX = o * size.width
            val scale = 0.86f + 0.14f * (1f - d)
            scaleX = scale
            scaleY = scale
            alpha = 1f - d
        }
    }
}
