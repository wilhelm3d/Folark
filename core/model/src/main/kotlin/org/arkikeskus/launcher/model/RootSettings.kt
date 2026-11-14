package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Root features (all off unless root is enabled). */
@Immutable
data class RootSettings(
    /** Settings ▸ Root. [enabled] off: Folark never asks for root. The modes ([RootMode]) pick
     *  Android's way or root for locking, Android's Recents key and the notification shade; the
     *  "keep" switches give (and give back) grants with root instead of ADB or settings pages. */
    val enabled: Boolean = true,
    val lockMode: RootMode = RootMode.FALLBACK,
    val recentsMode: RootMode = RootMode.FALLBACK,
    val shadeMode: RootMode = RootMode.FALLBACK,
    val keepDump: Boolean = false,
    val keepUsage: Boolean = false,
    val keepSecureSettings: Boolean = false,
    val keepNotifications: Boolean = false,
    val keepAccessibility: Boolean = false,
    val keepWidgets: Boolean = false,
)

/**
 * How a system action (locking, Android's Recents key, the notification shade) is done, as
 * Settings ▸ Root says for it. [stored] is the value kept in the settings store (and in backups),
 * the same text the settings always used.
 */
enum class RootMode(val stored: String) {
    /** Android's way only. */
    OFF("off"),

    /** Android's way, and root when that can't (the accessibility service is off). */
    FALLBACK("fallback"),

    /** Root, and Android's way when root isn't there. */
    ALWAYS("always"),
    ;

    companion object {
        /** The mode stored as [value]; anything else (nothing stored, an old or garbled value) is [FALLBACK]. */
        fun from(value: String?): RootMode = entries.firstOrNull { it.stored == value } ?: FALLBACK
    }
}
