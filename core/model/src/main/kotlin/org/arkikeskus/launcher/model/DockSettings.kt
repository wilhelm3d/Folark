package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** The dock row on home. */
@Immutable
data class DockSettings(
    val enabled: Boolean = true,
    val columns: Int = 4,
    val showLabels: Boolean = false,
    val backgroundOpacity: Float = 0.35f,
    val innerAlignment: String = LauncherSettings.DOCK_ALIGNMENT_CENTER,
)
