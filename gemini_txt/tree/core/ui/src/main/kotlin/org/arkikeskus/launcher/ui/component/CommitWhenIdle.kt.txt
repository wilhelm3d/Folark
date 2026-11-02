package org.arkikeskus.launcher.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/**
 * Saves a text field's [value] through [commit] once typing pauses for [delayMs], and once more
 * when the field goes away with a change still unsaved. Each save is a write to storage that every
 * screen watching it reacts to, so it shouldn't happen on every keystroke. [key] names what is being
 * edited (a folder id); a new key starts over.
 */
@Composable
fun CommitWhenIdle(key: Any?, value: String, delayMs: Long = 400L, commit: (String) -> Unit) {
    val latestCommit = rememberUpdatedState(commit)
    val latestValue = rememberUpdatedState(value)
    val saved = remember(key) { arrayOf(value) }
    LaunchedEffect(key, value) {
        if (value == saved[0]) return@LaunchedEffect
        delay(delayMs)
        saved[0] = value
        latestCommit.value(value)
    }
    DisposableEffect(key) {
        onDispose {
            val last = latestValue.value
            if (last != saved[0]) {
                saved[0] = last
                latestCommit.value(last)
            }
        }
    }
}
