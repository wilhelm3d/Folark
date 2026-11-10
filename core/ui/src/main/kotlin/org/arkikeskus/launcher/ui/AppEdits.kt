package org.arkikeskus.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What happens to apps on home, the dock and in the drawer, told to the whole launcher:
 *  - an [UndoOffer] after an app was removed or hidden ("Spotify removed from home · Undo"), shown
 *    by [UndoBar] above every surface, so removing needs no "are you sure";
 *  - a [Reveal] after "Add to home", so home jumps to the page the app landed on and pops it in.
 */
object AppEdits {
    private val _undo = MutableStateFlow<UndoOffer?>(null)
    val undo: StateFlow<UndoOffer?> = _undo.asStateFlow()

    private val _reveal = MutableSharedFlow<Reveal>(extraBufferCapacity = 4)
    val reveal: SharedFlow<Reveal> = _reveal.asSharedFlow()

    /** [label] was taken off home; [undo] puts it back where it was. */
    fun removedFromHome(label: String, undo: () -> Unit) = offer(UndoOffer(UndoOffer.Kind.HOME, label, 1, undo))

    /** [label] was taken off the dock; [undo] puts it back in its place. */
    fun removedFromDock(label: String, undo: () -> Unit) = offer(UndoOffer(UndoOffer.Kind.DOCK, label, 1, undo))

    /** [label] was hidden from the app list; [undo] shows it again. */
    fun hidden(label: String, undo: () -> Unit) = offer(UndoOffer(UndoOffer.Kind.HIDDEN, label, 1, undo))

    /** [count] apps were removed from home at once (edit mode); [undo] puts them all back. */
    fun removedMany(count: Int, undo: () -> Unit) = offer(UndoOffer(UndoOffer.Kind.MANY, "", count, undo))

    private fun offer(offer: UndoOffer) {
        _undo.value = offer
    }

    /** The bar went away (timed out, or Undo was tapped). */
    fun dismiss(offer: UndoOffer) {
        _undo.compareAndSet(offer, null)
    }

    /** Home should show [key] (an app key) on [page] of [screen] (ScreenType name). */
    fun reveal(key: String, page: Int, screen: String) {
        _reveal.tryEmit(Reveal(key, page, screen))
    }

    data class Reveal(val key: String, val page: Int, val screen: String)
}

/** One "… · Undo" offer; each is its own object, so a newer one replaces an older one cleanly. */
class UndoOffer(val kind: Kind, val label: String, val count: Int, val undo: () -> Unit) {
    enum class Kind { HOME, DOCK, HIDDEN, MANY }
}

/**
 * The undo bar: a pill at the bottom for [UNDO_SHOW_MS] after something was removed or hidden.
 * Placed once, above home and the drawer (LauncherShell).
 */
@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val offer by AppEdits.undo.collectAsState()
    // The last offer stays drawn while the bar slides away.
    val shown = remember { arrayOfNulls<UndoOffer>(1) }
    if (offer != null) shown[0] = offer
    LaunchedEffect(offer) {
        val current = offer ?: return@LaunchedEffect
        delay(UNDO_SHOW_MS)
        AppEdits.dismiss(current)
    }
    AnimatedVisibility(
        visible = offer != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
    ) {
        val o = shown[0] ?: return@AnimatedVisibility
        val message = when (o.kind) {
            UndoOffer.Kind.HOME -> stringResource(R.string.undo_removed_home, o.label)
            UndoOffer.Kind.DOCK -> stringResource(R.string.undo_removed_dock, o.label)
            UndoOffer.Kind.HIDDEN -> stringResource(R.string.undo_hidden, o.label)
            UndoOffer.Kind.MANY -> pluralStringResource(R.plurals.undo_removed_many, o.count, o.count)
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF0202124),
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 6.dp)) {
                Text(
                    message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = 14.dp),
                )
                TextButton(onClick = {
                    AppEdits.dismiss(o)
                    o.undo()
                }) {
                    Text(stringResource(R.string.undo_action), color = Color(0xFF8AB4F8))
                }
            }
        }
    }
}

/** How long the undo bar stays. */
private const val UNDO_SHOW_MS = 5_000L
