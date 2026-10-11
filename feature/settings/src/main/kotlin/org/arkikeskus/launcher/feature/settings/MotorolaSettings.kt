package org.arkikeskus.launcher.feature.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.IconShapes
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import java.io.File

/** Appearance card: the shape of app icons (both displays), as tiles drawn in each shape. */
@Composable
internal fun IconShapeRow(selected: String, onSelect: (String) -> Unit) {
    val palette = LocalExpressivePalette.current
    val shapes = listOf(
        LauncherSettings.ICON_SHAPE_SYSTEM to R.string.settings_icon_shape_system,
        LauncherSettings.ICON_SHAPE_CIRCLE to R.string.settings_icon_shape_circle,
        LauncherSettings.ICON_SHAPE_SQUIRCLE to R.string.settings_icon_shape_squircle,
        LauncherSettings.ICON_SHAPE_ROUNDED to R.string.settings_icon_shape_rounded,
        LauncherSettings.ICON_SHAPE_TEARDROP to R.string.settings_icon_shape_teardrop,
        LauncherSettings.ICON_SHAPE_PEBBLE to R.string.settings_icon_shape_pebble,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_icon_shape),
            color = palette.text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.settings_icon_shape_desc),
            color = palette.dim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp),
        ) {
            shapes.forEach { (shape, label) ->
                ShapeTile(
                    shape = shape,
                    label = stringResource(label),
                    selected = shape == selected,
                    onClick = { onSelect(shape) },
                )
            }
        }
    }
}

@Composable
private fun ShapeTile(shape: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalExpressivePalette.current
    val tileShape = RoundedCornerShape(16.dp)
    val px = with(LocalDensity.current) { 36.dp.toPx() }
    val path = remember(shape, px) { IconShapes.path(shape, px)?.asComposePath() }
    val fill = if (selected) Accent else palette.dim.copy(alpha = 0.55f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(tileShape)
            .border(1.dp, if (selected) Accent else palette.faint.copy(alpha = 0.25f), tileShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Canvas(Modifier.size(36.dp)) {
            if (path != null) {
                drawPath(path, fill)
            } else {
                drawRoundRect(fill, cornerRadius = CornerRadius(size.minDimension * 0.3f))
            }
        }
        Text(
            text = label,
            color = if (selected) Accent else palette.text,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Appearance page: Motorola's own settings that Folark can open, and whether a tap on Folark's
 * weather opens Motorola Weather. Rows only appear for screens this phone has; the card is hidden
 * on other phones.
 */
@Composable
internal fun MotorolaSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val links = remember {
        listOf(
            Triple(MotoLinks.STYLES, R.string.settings_moto_styles, R.string.settings_moto_styles_desc),
            Triple(MotoLinks.SECOND_SCREEN_WALLPAPER, R.string.settings_moto_second_wallpaper, R.string.settings_moto_second_wallpaper_desc),
            Triple(MotoLinks.SECOND_SCREEN_CLOCK, R.string.settings_moto_second_clock, R.string.settings_moto_second_clock_desc),
            Triple(MotoLinks.GESTURES, R.string.settings_moto_gestures, R.string.settings_moto_gestures_desc),
            Triple(MotoLinks.SWIPE_TO_SPLIT, R.string.settings_moto_swipe_split, R.string.settings_moto_swipe_split_desc),
        ).filter { MotoLinks.available(context, it.first) }
    }
    val weather = remember { MotoLinks.available(context, MotoLinks.WEATHER) }
    if (links.isEmpty() && !weather) return
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_motorola),
        icon = Icons.Rounded.Smartphone,
        subtitle = stringResource(R.string.settings_motorola_desc),
        sharedByBothDisplays = true,
    ) {
        if (weather) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_moto_weather_tap),
                subtitle = stringResource(R.string.settings_moto_weather_tap_desc),
                checked = s.motoWeatherTap,
                onCheckedChange = viewModel::setMotoWeatherTap,
            )
        }
        links.forEachIndexed { index, (action, title, description) ->
            if (index > 0 || weather) ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(title),
                description = stringResource(description),
                onClick = { MotoLinks.open(context, action) },
            )
        }
    }
}

/** The wallpapers that came with the phone, to pick one as this display's home background. */
@Composable
internal fun MotoWallpaperDialog(files: List<File>, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_moto_wallpapers)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                files.forEach { file -> WallpaperThumb(file, onClick = { onPick(file) }) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun WallpaperThumb(file: File, onClick: () -> Unit) {
    var picture by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        picture = withContext(Dispatchers.IO) { decodeThumb(file) }
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(170.dp)
            .clip(shape)
            .border(1.dp, LocalExpressivePalette.current.faint.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick),
    ) {
        picture?.let {
            Image(
                bitmap = it,
                contentDescription = file.nameWithoutExtension,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).height(170.dp),
            )
        }
    }
}

private fun decodeThumb(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

private const val THUMB_PX = 320
