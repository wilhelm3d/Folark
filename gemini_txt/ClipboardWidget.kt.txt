package org.arkikeskus.launcher.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.arkikeskus.launcher.data.ClipEntry
import org.arkikeskus.launcher.data.ClipboardHistoryRepository
import javax.inject.Inject

@HiltViewModel
class ClipboardWidgetViewModel @Inject constructor(
    private val repository: ClipboardHistoryRepository,
) : ViewModel() {
    val clips = repository.clips
    fun attach() = repository.attach()
    fun detach() = repository.detach()
    fun copy(entry: ClipEntry) = repository.copy(entry)
    fun remove(entry: ClipEntry) = repository.remove(entry)
    fun clear() = repository.clear()
}

/**
 * Clipboard history: the last things copied, newest first, each with a button to copy it again
 * (a tap on the row does the same; a long press removes it). No card and no corners: the text sits
 * straight on the wallpaper with a soft shadow, and the list fades out at its top and bottom edges.
 * As many clips as fit are shown; the header opens the whole list.
 */
@Composable
fun ClipboardWidget(modifier: Modifier = Modifier, viewModel: ClipboardWidgetViewModel = hiltViewModel()) {
    DisposableEffect(viewModel) {
        viewModel.attach()
        onDispose { viewModel.detach() }
    }
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .softEdges()
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(enabled = clips.isNotEmpty()) { showAll = true }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = INK, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.clipboard_widget_title),
                style = shadowed(13.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            if (clips.isNotEmpty()) {
                Text("${clips.size}", style = shadowed(12.sp, FontWeight.Medium, INK.copy(alpha = 0.7f)))
            }
        }
        if (clips.isEmpty()) {
            Text(
                stringResource(R.string.clipboard_widget_empty),
                style = shadowed(13.sp, FontWeight.Normal, INK.copy(alpha = 0.75f)),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        } else {
            // Only what fits: a list that scrolls would fight home's own swipes.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val fits = (maxHeight / ROW_HEIGHT).toInt().coerceAtLeast(1)
                Column {
                    clips.take(fits).forEach { clip ->
                        ClipRow(
                            clip = clip,
                            color = INK,
                            shadow = true,
                            onCopy = { viewModel.copy(clip) },
                            onRemove = { viewModel.remove(clip) },
                        )
                    }
                }
            }
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(stringResource(R.string.clipboard_widget_title)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(clips, key = { "${it.time}/${it.text.hashCode()}" }) { clip ->
                        ClipRow(
                            clip = clip,
                            color = MaterialTheme.colorScheme.onSurface,
                            shadow = false,
                            onCopy = { viewModel.copy(clip) },
                            onRemove = { viewModel.remove(clip) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAll = false }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clear(); showAll = false }) {
                    Text(stringResource(R.string.clipboard_clear))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(clip: ClipEntry, color: Color, shadow: Boolean, onCopy: () -> Unit, onRemove: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var copied by remember(clip) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_SHOWN_MS)
            copied = false
        }
    }
    val copy = {
        onCopy()
        copied = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(CircleShape)
            .combinedClickable(
                onClick = copy,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemove()
                },
            )
            .padding(start = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // A copied picture has no text: named as one (copy puts the picture back).
                if (clip.image != null) stringResource(R.string.clipboard_picture) else clip.text.replace('\n', ' '),
                style = if (shadow) shadowed(14.sp, FontWeight.Medium, color) else TextStyle(color = color, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(clip.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = if (shadow) shadowed(11.sp, FontWeight.Normal, color.copy(alpha = 0.7f)) else TextStyle(color = color.copy(alpha = 0.6f), fontSize = 11.sp),
                maxLines = 1,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = copy),
        ) {
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = stringResource(if (copied) R.string.clipboard_copied else R.string.clipboard_copy),
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Text that reads on any wallpaper without a card behind it. */
private fun shadowed(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, color: Color = INK) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
)

/** Fades the content out towards its top and bottom edges, so the widget has no visible border. */
private fun Modifier.softEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 14.dp.toPx() / size.height.coerceAtLeast(1f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fade.coerceAtMost(0.3f) to Color.Black,
                (1f - fade).coerceAtLeast(0.7f) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val INK = Color.White
private val ROW_HEIGHT = 48.dp
private const val COPIED_SHOWN_MS = 1_500L
