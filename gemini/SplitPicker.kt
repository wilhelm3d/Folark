package org.arkikeskus.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The split-select picker: Folark's own app drawer, opened in the half of the screen beside the app
 * that was dragged to an edge (Recents or the taskbar). The dragged ("first") app and the side it
 * was dropped on travel along as extras; the picker then puts the chosen app beside it. Opening it
 * beside the app, and building the split, is [SplitCoordinator]'s.
 */
object SplitPicker {
    private const val PICKER = "org.arkikeskus.launcher.recents.SplitPickerActivity"
    const val EXTRA_FIRST_PACKAGE = "first_package"
    /** [org.arkikeskus.launcher.model.AppItem.key] of the first app (it tells profiles apart), when known. */
    const val EXTRA_FIRST_KEY = "first_key"
    /** The first app's running task, when known (-1: not known). */
    const val EXTRA_FIRST_TASK = "first_task"
    const val EXTRA_RIGHT = "right"
    const val EXTRA_SELF_DOCK_RIGHT = "self_dock_right"
    const val EXTRA_PEEK = "peek"
    /** Three-app split: pick two more apps (the picker opens full screen for it). */
    const val EXTRA_THREE = "three"

    fun componentName(context: Context): ComponentName = ComponentName(context.packageName, PICKER)

    fun intent(
        context: Context,
        firstPackage: String,
        right: Boolean,
        selfDockRight: Boolean = false,
        peek: Boolean = false,
        firstKey: String? = null,
        firstTaskId: Int? = null,
        three: Boolean = false,
    ): Intent = Intent().apply {
        component = componentName(context)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
        putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
        putExtra(EXTRA_FIRST_KEY, firstKey)
        putExtra(EXTRA_FIRST_TASK, firstTaskId ?: -1)
        putExtra(EXTRA_RIGHT, right)
        putExtra(EXTRA_SELF_DOCK_RIGHT, selfDockRight)
        putExtra(EXTRA_PEEK, peek)
        putExtra(EXTRA_THREE, three)
    }
}
