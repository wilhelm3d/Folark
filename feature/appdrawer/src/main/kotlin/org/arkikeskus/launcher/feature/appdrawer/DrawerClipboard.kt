package org.arkikeskus.launcher.feature.appdrawer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.ClipEntry
import org.arkikeskus.launcher.data.ClipKind
import org.arkikeskus.launcher.data.ClipboardHistoryRepository
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import javax.inject.Inject

@HiltViewModel
class DrawerClipboardViewModel @Inject constructor(
    val repository: ClipboardHistoryRepository,
) : ViewModel() {
    val clips = repository.clips
}

/**
 * Settings ▸ App drawer ▸ Clipboard row: the last things copied, newest first, as cards across the
 * top of the drawer. Each card shows what it is (a link, a phone number, an email, a picture or
 * text) and the actions for it: copy it again (ready to paste in any app), open the link, call,
 * write an email, search, share. A long press removes it. The clipboard is followed by
 * [FollowClipboard] (for as long as the row is switched on), not by the row itself: a list item is
 * dropped while scrolled out of view, and copies made then would be missed.
 */
@Composable
internal fun DrawerClipboardRow(viewModel: DrawerClipboardViewModel = hiltViewModel()) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    if (clips.isEmpty()) return
    val p = LocalExpressivePalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = p.dim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.drawer_clipboard), color = p.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(clips.take(MAX_SHOWN), key = { "${it.time}/${it.image ?: it.text.hashCode()}" }) { clip ->
                ClipCard(clip, viewModel.repository)
            }
        }
    }
}

/** Follows the clipboard while [enabled] (Settings ▸ App drawer ▸ Clipboard row). */
@Composable
internal fun FollowClipboard(enabled: Boolean, viewModel: DrawerClipboardViewModel = hiltViewModel()) {
    if (!enabled) return
    DisposableEffect(viewModel) {
        viewModel.repository.attach()
        onDispose { viewModel.repository.detach() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipCard(clip: ClipEntry, repository: ClipboardHistoryRepository) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val p = LocalExpressivePalette.current
    val accent = LocalAccent.current
    val kind = clip.kind
    var copied by remember(clip) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(COPIED_SHOWN_MS); copied = false }
    }
    val copy = {
        repository.copy(clip)
        copied = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Surface(
        color = p.surfaceHi,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = p.shadow,
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = copy,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    repository.remove(clip)
                },
            ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(kind), contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(kindLabel(kind)),
                    color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (kind == ClipKind.IMAGE) {
                val picture = rememberClipPicture(repository, clip)
                Box(
                    Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).clip(RoundedCornerShape(10.dp)).background(p.faint.copy(alpha = 0.15f)),
                ) {
                    picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) }
                }
            } else {
                Text(
                    clip.text.replace('\n', ' '),
                    color = p.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(PREVIEW_HEIGHT),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ClipAction(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, R.string.drawer_clip_copy, accent, onClick = copy)
                when (kind) {
                    ClipKind.LINK -> ClipAction(Icons.Rounded.OpenInNew, R.string.drawer_clip_open, p.text) {
                        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(ClipKind.url(clip.text))))
                    }
                    ClipKind.PHONE -> ClipAction(Icons.Rounded.Call, R.string.drawer_clip_call, p.text) {
                        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(clip.text.trim()))))
                    }
                    ClipKind.EMAIL -> ClipAction(Icons.Rounded.Email, R.string.drawer_clip_email, p.text) {
                        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + clip.text.trim())))
                    }
                    ClipKind.TEXT -> ClipAction(Icons.Rounded.Search, R.string.drawer_clip_search, p.text) {
                        launchWebSearch(context, clip.text.trim().take(SEARCH_MAX_CHARS))
                    }
                    ClipKind.IMAGE -> Unit
                }
                ClipAction(Icons.Rounded.Share, R.string.drawer_clip_share, p.text) { share(context, clip, repository) }
            }
        }
    }
}

@Composable
private fun ClipAction(icon: ImageVector, label: Int, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = stringResource(label), tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** A copied picture, decoded small off the main thread. */
@Composable
private fun rememberClipPicture(repository: ClipboardHistoryRepository, clip: ClipEntry): ImageBitmap? {
    val picture by produceState<ImageBitmap?>(null, clip.image) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = repository.imageFile(clip) ?: return@runCatching null
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return picture
}

private fun share(context: Context, clip: ClipEntry, repository: ClipboardHistoryRepository) {
    val send = if (clip.image != null) {
        val uri = repository.imageUri(clip) ?: return
        Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri(null, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clip.text)
    }
    start(context, Intent.createChooser(send, null))
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun kindIcon(kind: ClipKind): ImageVector = when (kind) {
    ClipKind.LINK -> Icons.Rounded.Link
    ClipKind.PHONE -> Icons.Rounded.Call
    ClipKind.EMAIL -> Icons.Rounded.Email
    ClipKind.IMAGE -> Icons.Rounded.Image
    ClipKind.TEXT -> Icons.Rounded.TextFields
}

private fun kindLabel(kind: ClipKind): Int = when (kind) {
    ClipKind.LINK -> R.string.drawer_clip_link
    ClipKind.PHONE -> R.string.drawer_clip_phone
    ClipKind.EMAIL -> R.string.drawer_clip_email_kind
    ClipKind.IMAGE -> R.string.drawer_clip_image
    ClipKind.TEXT -> R.string.drawer_clip_text
}

private const val MAX_SHOWN = 10
private val CARD_WIDTH = 196.dp
private val PREVIEW_HEIGHT = 40.dp
private const val COPIED_SHOWN_MS = 1_500L
private const val SEARCH_MAX_CHARS = 200
