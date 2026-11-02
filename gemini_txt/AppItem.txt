package org.arkikeskus.launcher.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable

/**
 * A launchable application entry, as surfaced by [android.content.pm.LauncherApps].
 * Carries the [UserHandle] so apps in work/private profiles can be launched correctly.
 */
@Immutable
data class AppItem(
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val isHidden: Boolean = false,
    /** In Private Space (Android 15+): shown only in the drawer's Private section. */
    val privateProfile: Boolean = false,
    /** App is archived by Android (Android 15+). */
    val isArchived: Boolean = false,
) {
    val componentName: ComponentName get() = ComponentName(packageName, className)

    // Built once: read on every icon, badge, list key and cache lookup. (Body properties are not
    // part of equals/hashCode/copy; copy() builds them again from the new values.)

    /** Stable identity for list keys and icon cache keys (unique across user profiles). */
    val key: String = "$packageName/$className/$userSerial"

    /** Package-level identity (per profile) used to match notification badges. */
    val badgeKey: String = "$packageName/$userSerial"
}
