package org.arkikeskus.launcher.feature.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How much of the bottom of the screen Folark's own taskbar covers while it stays on home and in the
 * drawer (pixels from the screen's bottom edge to the bar's top, plus a small gap); 0 when it isn't
 * there. Home's dock and the drawer keep clear of it. Android does not report this bar as an inset,
 * because it is Folark's own overlay window.
 */
object TaskbarSpace {
    private val _bottomPx = MutableStateFlow(0)
    val bottomPx: StateFlow<Int> = _bottomPx.asStateFlow()

    internal fun set(px: Int) {
        _bottomPx.value = px.coerceAtLeast(0)
    }

    private val _keyboardOnHome = MutableStateFlow(false)

    /** The keyboard is up over Folark (searching the drawer, writing a note): the bar steps aside. */
    val keyboardOnHome: StateFlow<Boolean> = _keyboardOnHome.asStateFlow()

    fun setKeyboardOnHome(shown: Boolean) {
        _keyboardOnHome.value = shown
    }
}
