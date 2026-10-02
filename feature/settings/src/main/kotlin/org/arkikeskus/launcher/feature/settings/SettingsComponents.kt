package org.arkikeskus.launcher.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/** M3 Expressive Rounded Section Container Card */
@Composable
fun ExpressiveCategoryContainer(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalExpressivePalette.current

    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = palette.shadow,
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                        text = title,
                        color = palette.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = palette.dim,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
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
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
) {
    val palette = LocalExpressivePalette.current

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
            if (valueDisplay != null) {
                Text(
                    text = valueDisplay,
                    color = Accent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
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

        Surface(
            color = palette.bg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { (value, label) ->
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

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(value) },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
