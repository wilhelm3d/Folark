package org.arkikeskus.launcher.ui.component

import androidx.compose.runtime.compositionLocalOf
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.ScreenType

val LocalScreenType = compositionLocalOf { ScreenType.OUTER }
val LocalFoldState = compositionLocalOf { FoldState.CLOSED }
