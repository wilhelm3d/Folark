package org.arkikeskus.launcher.model

enum class FoldState {
    CLOSED,      // Outer screen (no fold feature or not folded)
    OPENED,      // Inner screen, flat
    HALF_OPENED; // Inner screen, partially folded

    val screenType: ScreenType
        get() = when (this) {
            OPENED, HALF_OPENED -> ScreenType.INNER
            CLOSED -> ScreenType.OUTER
        }
}
