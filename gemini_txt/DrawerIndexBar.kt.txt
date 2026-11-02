package org.arkikeskus.launcher.feature.appdrawer

import org.arkikeskus.launcher.model.LauncherSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.drawer.readableOn
import org.arkikeskus.launcher.ui.expressive.LocalAccent

// The A–Z index bar and letter headers (split out of AppDrawerScreen.kt).

/** The heading letter an app is filed under: its first letter, or '#' for digits and symbols. */
internal fun drawerLetterOf(app: AppItem): Char =
    app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

/** One "A" / "B" / "C" heading between the apps of the full list. */
@Composable
internal fun DrawerLetterHeader(letter: Char) {
    Text(
        text = letter.toString(),
        color = LocalAccent.current,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
internal fun DrawerIndexBar(
    position: String,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
    /** Settings ▸ App drawer ▸ index bar size: scales the letters and the strip they sit in. */
    scale: Float = 1f,
    /** When set, the letter under the finger is reported here (null on release) and the bar draws
     *  no bubble of its own: the drawer shows it large instead. */
    onActiveLetter: ((Char?) -> Unit)? = null,
) {
    val alphabet = remember { listOf('#') + ('A'..'Z').toList() }
    // The gesture blocks below live as long as the bar: always call the newest callback (it holds
    // the current app list) and read the newest position.
    val currentOnLetter by rememberUpdatedState(onLetterSelected)
    val currentPosition by rememberUpdatedState(position)
    val currentActiveLetter by rememberUpdatedState(onActiveLetter)
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateSelection(offset: Offset) {
        val count = alphabet.size
        val isVertical = currentPosition == LauncherSettings.AZ_POSITION_VERTICAL_RIGHT || currentPosition == LauncherSettings.AZ_POSITION_VERTICAL_LEFT
        val fraction = if (isVertical) {
            if (barSize.height > 0) (offset.y / barSize.height).coerceIn(0f, 1f) else 0f
        } else {
            if (barSize.width > 0) (offset.x / barSize.width).coerceIn(0f, 1f) else 0f
        }
        val idx = (fraction * count).toInt().coerceIn(0, count - 1)
        val char = alphabet[idx]
        if (selectedLetter != char) {
            selectedLetter = char
            currentOnLetter(char)
        }
    }

    Box(modifier = modifier) {
        val isVertical = position == LauncherSettings.AZ_POSITION_VERTICAL_RIGHT || position == LauncherSettings.AZ_POSITION_VERTICAL_LEFT
        if (isVertical) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 4.dp * scale)
                    .onSizeChanged { barSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                updateSelection(offset)
                                tryAwaitRelease()
                                selectedLetter = null
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> updateSelection(offset) },
                            onDragEnd = { selectedLetter = null },
                            onDragCancel = { selectedLetter = null },
                            onDrag = { change, _ -> updateSelection(change.position) },
                        )
                    },
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                alphabet.forEach { char ->
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp * scale,
                            fontWeight = if (selectedLetter == char) FontWeight.ExtraBold else FontWeight.Medium,
                        ),
                        color = if (selectedLetter == char) LocalAccent.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp * scale)
                    .onSizeChanged { barSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                updateSelection(offset)
                                tryAwaitRelease()
                                selectedLetter = null
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> updateSelection(offset) },
                            onDragEnd = { selectedLetter = null },
                            onDragCancel = { selectedLetter = null },
                            onDrag = { change, _ -> updateSelection(change.position) },
                        )
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                alphabet.forEach { char ->
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp * scale,
                            fontWeight = if (selectedLetter == char) FontWeight.ExtraBold else FontWeight.Medium,
                        ),
                        color = if (selectedLetter == char) LocalAccent.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        LaunchedEffect(selectedLetter) { currentActiveLetter?.invoke(selectedLetter) }
        DisposableEffect(Unit) { onDispose { currentActiveLetter?.invoke(null) } }
        if (onActiveLetter == null) selectedLetter?.let { letter ->
            Surface(
                shape = CircleShape,
                color = LocalAccent.current,
                contentColor = readableOn(LocalAccent.current),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
