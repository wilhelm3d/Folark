package org.arkikeskus.launcher.feature.appdrawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.expressive.LocalAccent

/**
 * An open drawer folder, presented the way a phone folder should be: a card that grows out of the
 * grid rather than a sheet that takes the screen.
 *
 * Everything the folder needs is in the card itself — tap the name to type over it, drag an app out
 * of the card to take it out (a folder emptied this way vanishes on its own), and type in the field
 * at the bottom to add something. Nothing is behind a menu, and no dialog asks for a name first.
 */
@Composable
internal fun FolderPopover(
    folder: DrawerFolderUi,
    allApps: List<AppItem>,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    onRename: (String) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onRemoveApp: (AppItem) -> Unit,
    onAddApps: (List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    // How far an app has to be pulled for letting go to take it out of the folder.
    val takeOutAt = with(density) { 72.dp.toPx() }
    // The card grows in, and shrinks out before it is actually dismissed, so opening and closing a
    // folder reads as one movement instead of a pop.
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = tween(160),
        label = "folderPopover",
    )

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            delay(170)
            onDismiss()
        }
    }

    // Back closes the folder, not the drawer behind it.
    BackHandler(enabled = true) { close() }

    var editingName by remember(folder.id) { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var query by remember(folder.id) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // The app being dragged out of the folder, and how far it has travelled.
    var lifted by remember { mutableStateOf<AppItem?>(null) }
    var pull by remember { mutableStateOf(Offset.Zero) }
    val willTakeOut = pull.getDistance() > takeOutAt

    fun commitName() {
        val trimmed = name.trim()
        editingName = false
        if (trimmed.isNotEmpty() && trimmed != folder.name) onRename(trimmed)
    }

    // The card never fills a landscape window: it grows to a share of what is actually there.
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.7f).coerceAtLeast(280.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * progress))
            .pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    alpha = progress
                    val s = 0.94f + 0.06f * progress
                    scaleX = s
                    scaleY = s
                }
                // Taps on the card must not fall through to the scrim behind it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxCardHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val nameField = remember { FocusRequester() }
                    if (editingName) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitName() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(nameField)
                                .padding(vertical = 8.dp),
                        )
                        LaunchedEffect(Unit) { nameField.requestFocus() }
                        TextButton(onClick = { commitName() }) {
                            Text(stringResource(R.string.folder_save))
                        }
                    } else {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Tapping the name is the rename gesture — no separate menu entry.
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    name = folder.name
                                    editingName = true
                                }
                                .padding(vertical = 8.dp),
                        )
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.folder_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }

                if (folder.apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_popover_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 20.dp),
                    )
                } else {
                    // Columns follow the space the card actually has, so a narrow window shows
                    // three across instead of squeezing four.
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 8.dp),
                    ) {
                        val columns = (maxWidth / 74.dp).toInt().coerceIn(3, 6)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            folder.apps.chunked(columns).forEach { rowApps ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    rowApps.forEach { app ->
                                        Box(Modifier.weight(1f)) {
                                            FolderApp(
                                                app = app,
                                                badgeCount = badges[app.badgeKey] ?: 0,
                                                badgeShowCount = badgeShowCount,
                                                badgeScale = badgeScale,
                                                lifted = lifted?.key == app.key,
                                                offset = if (lifted?.key == app.key) pull else Offset.Zero,
                                                onOpen = { onAppClick(app) },
                                                onLift = {
                                                    lifted = app
                                                    pull = Offset.Zero
                                                },
                                                onMove = { pull += it },
                                                onDrop = {
                                                    val takeOut = pull.getDistance() > takeOutAt
                                                    lifted = null
                                                    pull = Offset.Zero
                                                    if (takeOut) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onRemoveApp(app)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    // The hint only exists while something is being pulled, and it changes over as
                    // the finger passes the point where letting go takes the app out.
                    if (lifted != null) {
                        Text(
                            text = stringResource(
                                if (willTakeOut) R.string.folder_popover_release else R.string.folder_popover_pull,
                            ),
                            color = if (willTakeOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }

                // Adding apps: type here and tap, instead of opening a second list. The field only
                // grows results while it has something in it, so the card stays the folder.
                val inFolder = remember(folder.apps) { folder.apps.map { it.key }.toSet() }
                val matches = remember(query, allApps, inFolder) {
                    if (query.isBlank()) {
                        emptyList()
                    } else {
                        allApps.filter { it.key !in inFolder && it.label.contains(query, ignoreCase = true) }.take(5)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, end = 8.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accent),
                        // Enter adds the first match, so a folder can be filled without reaching up.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            matches.firstOrNull()?.let {
                                onAddApps(listOf(it.key))
                                query = ""
                            }
                        }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.folder_popover_search),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                }
                if (query.isNotBlank()) {
                    if (matches.isEmpty()) {
                        Text(
                            text = stringResource(R.string.folder_popover_no_match),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                        )
                    } else {
                        matches.forEach { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddApps(listOf(app.key))
                                        query = ""
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                            ) {
                                AppIcon(
                                    appItem = app,
                                    labelColor = Color.Transparent,
                                    showLabel = false,
                                    iconSize = 32.dp,
                                )
                                Text(
                                    text = app.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                )
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.folder_add),
                                    tint = accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.folder_delete)) },
            text = { Text(stringResource(R.string.folder_delete_confirm, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.folder_cancel))
                }
            },
        )
    }
}

/**
 * One app inside the folder card. Tap opens it; a long press lifts it, and from there it follows
 * the finger so it can be dragged out of the folder (or dropped back in, by letting go near where
 * it started).
 */
@Composable
private fun FolderApp(
    app: AppItem,
    badgeCount: Int,
    badgeShowCount: Boolean,
    badgeScale: Float,
    lifted: Boolean,
    offset: Offset,
    onOpen: () -> Unit,
    onLift: () -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                val s = if (lifted) 1.15f else 1f
                scaleX = s
                scaleY = s
                alpha = if (lifted) 0.9f else 1f
            }
            .pointerInput(app.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var tapped = false
                    // Waiting out the long-press timeout: a lift (then the app can be dragged out),
                    // a tap (opens it) or a scroll (leaves it alone).
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                            if (!change.pressed) {
                                tapped = true
                                return@withTimeoutOrNull
                            }
                            if (change.isConsumed ||
                                (change.position - down.position).getDistance() > slop
                            ) {
                                return@withTimeoutOrNull
                            }
                        }
                    }
                    if (tapped) {
                        onOpen()
                        return@awaitEachGesture
                    }
                    onLift()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag(down.id) { change ->
                        change.consume()
                        onMove(change.positionChange())
                    }
                    onDrop()
                }
            }
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        AppIcon(
            appItem = app,
            labelColor = MaterialTheme.colorScheme.onSurface,
            showLabel = true,
            iconSize = 54.dp,
            badgeCount = badgeCount,
            badgeShowCount = badgeShowCount,
            badgeScale = badgeScale,
        )
    }
}

/** Names a brand-new empty folder (the tab rail's "+"), then opens it so the name can be changed. */
@Composable
internal fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    // Read outside the click lambda: stringResource is composable, onCreate is not.
    val defaultName = stringResource(R.string.folder_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim().ifEmpty { defaultName }) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.folder_new_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_cancel))
            }
        },
    )
}
