package org.arkikeskus.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Opens Folark's own Recents screen (Settings ▸ Recents), from home or the taskbar. */
object FolarkRecents {
    private const val ACTIVITY = "org.arkikeskus.launcher.recents.RecentsActivity"

    fun open(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent()
                .setComponent(ComponentName(context.packageName, ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
