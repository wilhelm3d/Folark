package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.arkikeskus.launcher.ui.component.CommitWhenIdle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LiquidGlassContainer
import org.arkikeskus.launcher.ui.component.isDefaultOrBlankFolderName

/**
 * Opened folder popup dialog on the Home Screen.
 *
 * Uses [LiquidGlassContainer] for frosted liquid glass background, allows entering a folder name
 * if new/default ("Folder"), and displays static text once named.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDialog(
    folder: PlacedFolder,
    badges: BadgeCounts,
    badgeShowCount: Boolean,
    badgeScale: Float,
    glassBlurRadius: Float,
    glassDarkTint: Float,
    /** How the folder opens (LauncherSettings.FOLDER_ANIM_*), and the animation speed multiplier. */
    openAnimation: String = LauncherSettings.FOLDER_ANIM_SHEET,
    animationSpeed: Float = 1f,
    onRename: (String) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onRemoveFromFolder: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultFolderName = stringResource(R.string.folder_default_name)
    val initialIsDefault = remember(folder.id) {
        isDefaultOrBlankFolderName(folder.name, defaultFolderName)
    }
    var isEditingName by remember(folder.id) { mutableStateOf(initialIsDefault) }
    var nameInput by remember(folder.id) {
        mutableStateOf(if (initialIsDefault) "" else folder.name)
    }
    CommitWhenIdle(folder.id, nameInput, commit = onRename)
    // Settings ▸ Grid ▸ Folders: theme, grid columns, and how big the opened folder is.
    val style = LocalFolderStyle.current
    val contentColor = folderContentColor(style.theme)
    val (openIconSize, openMaxHeight) = when (style.openSize) {
        LauncherSettings.FOLDER_OPEN_SMALL -> 44.dp to 260.dp
        LauncherSettings.FOLDER_OPEN_LARGE -> 68.dp to 520.dp
        else -> 56.dp to 360.dp
    }

    val asSheet = openAnimation == LauncherSettings.FOLDER_ANIM_SHEET
    val content: @Composable (Shape) -> Unit = { shape ->
        FolderSurface(
            theme = style.theme,
            shape = shape,
            glassBlurRadius = glassBlurRadius,
            glassDarkTint = glassDarkTint,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (asSheet) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { newText -> nameInput = newText },
                        singleLine = true,
                        label = { Text(stringResource(R.string.folder_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(style.openColumns.coerceIn(2, 8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = openMaxHeight),
                ) {
                    items(items = folder.apps, key = { it.key }) { app ->
                        AppIcon(
                            appItem = app,
                            labelColor = contentColor,
                            iconSize = openIconSize,
                            showLabel = true,
                            maxLabelLines = 2,
                            badgeCount = badges[app.badgeKey] ?: 0,
                            badgeShowCount = badgeShowCount,
                            badgeScale = badgeScale,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        onAppClick(app)
                                        onDismiss()
                                    },
                                    onLongClick = { onRemoveFromFolder(app) },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (asSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            dragHandle = null,
        ) {
            content(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        }
    } else {
        // A centred card that zooms or fades in (or just appears), at the chosen animation speed.
        val progress = remember(folder.id) { Animatable(if (openAnimation == LauncherSettings.FOLDER_ANIM_NONE) 1f else 0f) }
        LaunchedEffect(folder.id) {
            progress.animateTo(1f, tween((240 / animationSpeed.coerceAtLeast(0.1f)).toInt(), easing = FastOutSlowInEasing))
        }
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 520.dp)
                    .graphicsLayer {
                        alpha = progress.value
                        if (openAnimation == LauncherSettings.FOLDER_ANIM_ZOOM) {
                            val scale = 0.8f + 0.2f * progress.value
                            scaleX = scale
                            scaleY = scale
                        }
                    },
            ) {
                content(RoundedCornerShape(28.dp))
            }
        }
    }
}
