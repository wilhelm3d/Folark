package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
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
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    glassBlurRadius: Float,
    glassDarkTint: Float,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
    ) {
        LiquidGlassContainer(
            blurRadiusDp = glassBlurRadius,
            darkTintAlpha = glassDarkTint,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { newText ->
                            nameInput = newText
                            onRename(newText)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.folder_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(items = folder.apps, key = { it.key }) { app ->
                        AppIcon(
                            appItem = app,
                            labelColor = MaterialTheme.colorScheme.onSurface,
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
}
