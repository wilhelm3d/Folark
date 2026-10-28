package org.arkikeskus.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The split-select picker activity: Folark's app drawer, shown to choose the second app for a split.
 * The first (dragged) app and the side it was dropped on travel along as extras; the picker then
 * builds the split from the two real apps (the proven, reliable path), the first app on [EXTRA_RIGHT]'s side.
 */
object SplitPicker {
    private const val PICKER = "org.arkikeskus.launcher.recents.SplitPickerActivity"
    const val EXTRA_FIRST_PACKAGE = "first_package"
    const val EXTRA_RIGHT = "right"

    /** Opens the picker to choose the partner for [firstPackage], which lands on the [right] side. */
    fun intent(context: Context, firstPackage: String, right: Boolean): Intent = Intent().apply {
        component = ComponentName(context.packageName, PICKER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
        putExtra(EXTRA_RIGHT, right)
    }
}
