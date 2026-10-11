package org.arkikeskus.launcher.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.ExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.abs

/** True when the settings use the Studio look (Settings ▸ Appearance ▸ Settings look). */
val LocalSettingsStudio = staticCompositionLocalOf { false }

/** The Studio look: dark charcoal, whatever the system theme. */
val StudioExpressivePalette = ExpressivePalette(
    bg = Color(0xFF0B0C0F),
    surfaceHi = Color(0xFF17191E),
    text = Color(0xFFEDEEF0),
    dim = Color(0xFF8E939B),
    faint = Color(0xFF5F646C),
    trackOff = Color(0xFF2C3037),
    thumbOff = Color(0xFFB4B9C1),
    btn = Color.White.copy(alpha = 0.06f),
    shadow = 0.dp,
)

/** M3 Expressive Rounded Section Container Card */
@Composable
fun ExpressiveCategoryContainer(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** True when every row in this card is shared by both displays (shows the scope badge). */
    sharedByBothDisplays: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val studio = LocalSettingsStudio.current

    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(if (studio) 22.dp else 24.dp),
        shadowElevation = palette.shadow,
        border = if (studio) BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Category Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (studio) 36.dp else 40.dp)
                        .clip(RoundedCornerShape(if (studio) 11.dp else 14.dp))
                        .background(Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (studio) title.uppercase() else title,
                        color = palette.text,
                        fontSize = if (studio) 14.5.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (studio) 1.2.sp else (-0.2).sp,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = palette.dim,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    if (sharedByBothDisplays) ScopeBadge(modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Internal Items
            content()
        }
    }
}

/** Divider between setting rows inside a container */
@Composable
fun ExpressiveRowDivider() {
    val palette = LocalExpressivePalette.current
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.8.dp,
        color = palette.faint.copy(alpha = 0.15f),
    )
}

/** Expressive Setting Switch Row */
@Composable
fun ExpressiveSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    /** Marks a row that applies to both displays inside an otherwise per-display card. */
    shared: Boolean = false,
) {
    val palette = LocalExpressivePalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The whole row toggles, not just the small switch — easier to hit on a cover screen.
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.dim,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.text,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = palette.dim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (shared) ScopeBadge(modifier = Modifier.padding(top = 3.dp))
        }

        Switch(
            checked = checked,
            // The row's toggleable handles the click; null keeps one semantics node per row.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent,
                checkedThumbColor = Color.White,
                checkedBorderColor = Accent,
                uncheckedTrackColor = palette.trackOff,
                uncheckedThumbColor = palette.thumbOff,
                uncheckedBorderColor = palette.trackOff,
            ),
        )
    }
}

/** Expressive Setting Stepper Row (+ / - numeric control) */
@Composable
fun ExpressiveStepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val palette = LocalExpressivePalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.dim,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = title,
            color = palette.text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExpressiveStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.settings_step_decrease),
                onClick = { if (value > min) onValueChange(value - 1) },
            )
            Text(
                text = "$value",
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center,
                color = palette.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            ExpressiveStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_step_increase),
                onClick = { if (value < max) onValueChange(value + 1) },
            )
        }
    }
}

@Composable
private fun ExpressiveStepButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(palette.btn)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val SLIDER_WRITE_INTERVAL_MS = 120L
private const val DRAG_VALUE_HOLD_MS = 400L

/** Expressive Setting Slider Row */
@Composable
fun ExpressiveSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    icon: ImageVector? = null,
    valueDisplay: String? = null,
    subtitle: String? = null,
    /** Gets the value while the thumb is dragged, and null once the saved value has caught up
     *  (live previews follow the finger instead of the throttled writes). */
    onDragValue: ((Float?) -> Unit)? = null,
) {
    val palette = LocalExpressivePalette.current
    val studio = LocalSettingsStudio.current
    // The thumb follows the finger from local state. The setting itself is written at most a few
    // times a second while dragging and once more on release: every write is a disk transaction
    // that every screen observing the settings reacts to, so one per pointer move made sliders lag.
    var thumb by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var lastWrite by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) { if (!dragging) thumb = value }
    if (onDragValue != null) {
        // After the drag: drop the preview value once the saved one matches it (at once), or after
        // a moment if the write never comes back (an out-of-range value, a failed write).
        LaunchedEffect(dragging, value) {
            if (!dragging) {
                if (abs(value - thumb) > 0.0001f) delay(DRAG_VALUE_HOLD_MS)
                onDragValue(null)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.dim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    color = palette.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (valueDisplay != null && studio) {
                Text(
                    text = valueDisplay,
                    color = Accent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else if (valueDisplay != null) {
                Text(
                    text = valueDisplay,
                    color = Accent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, color = palette.dim, fontSize = 12.sp, lineHeight = 16.sp)
        }

        Slider(
            value = thumb,
            onValueChange = {
                dragging = true
                thumb = it
                onDragValue?.invoke(it)
                val now = SystemClock.uptimeMillis()
                if (now - lastWrite >= SLIDER_WRITE_INTERVAL_MS) {
                    lastWrite = now
                    onValueChange(it)
                }
            },
            onValueChangeFinished = {
                dragging = false
                onValueChange(thumb)
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = palette.trackOff,
            ),
        )
    }
}

/** Expressive Action Card (Sub-screen trigger or click action) */
@Composable
fun ExpressiveActionItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector = Icons.Rounded.ChevronRight,
    shared: Boolean = false,
) {
    val palette = LocalExpressivePalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.dim,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Accent,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = palette.dim,
                    fontSize = 12.5.sp,
                )
            }
            if (shared) ScopeBadge(modifier = Modifier.padding(top = 3.dp))
        }

        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = if (trailingIcon == Icons.Rounded.Add) Accent else palette.faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Expressive Segmented Control Row */
@Composable
fun <T> ExpressiveSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
) {
    val palette = LocalExpressivePalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (!title.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.dim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    color = palette.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Up to three options sit in one row; more wrap into rows of two, because four labels in a
        // single row do not fit a narrow cover screen (they wrapped mid-word).
        val rows = if (options.size <= 3) listOf(options) else options.chunked(2)
        Surface(
            color = palette.bg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rows.forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowOptions.forEach { (value, label) ->
                            val isSelected = value == selected
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) Accent else Color.Transparent,
                                animationSpec = tween(200),
                                label = "segmentedBg",
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else palette.text,
                                animationSpec = tween(200),
                                label = "segmentedText",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bgColor)
                                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }
                        // Keep an odd last option the same width as the ones above it.
                        if (rows.size > 1 && rowOptions.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
