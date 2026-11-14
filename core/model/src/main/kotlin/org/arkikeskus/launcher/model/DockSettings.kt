package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** The dock row on home. */
@Immutable
data class DockSettings(
    val enabled: Boolean = true,
    val columns: Int = 4,
    val showLabels: Boolean = false,
    val backgroundOpacity: Float = 0.35f,
    val innerAlignment: DockAlignment = DockAlignment.CENTER,
)

/** How the dock lines up its icons on the inner display. */
enum class DockAlignment(override val stored: String) : StoredChoice {
    CENTER("center"),
    LEFT("left"),
    RIGHT("right"),
    FLOATING("floating"),
}
