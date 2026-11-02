package org.arkikeskus.launcher.ui.component

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

/**
 * Whether the keyboard is really on screen, as Android tells the window. Compose's own keyboard
 * inset can stay behind after the keyboard is closed (seen on the outer screen: the search bar
 * stayed up with an empty gap below), so this asks the window itself, again on every layout.
 */
@Composable
fun rememberKeyboardShown(): State<Boolean> {
    val view = LocalView.current
    var layouts by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener { layouts++ }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    // Compose's value changing is also a moment to ask again.
    val composeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    return remember(view, composeBottom) {
        derivedStateOf {
            layouts
            platformKeyboardShown(view)
        }
    }
}

/** The keyboard's room at the bottom, or none when Android says the keyboard is closed. */
@Composable
fun keyboardInsets(): WindowInsets {
    val shown by rememberKeyboardShown()
    return if (shown) WindowInsets.ime else WindowInsets(0, 0, 0, 0)
}

private fun platformKeyboardShown(view: View): Boolean =
    view.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
