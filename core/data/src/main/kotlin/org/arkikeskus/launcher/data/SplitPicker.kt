package org.arkikeskus.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** The split-select picker activity (its app drawer shown in the empty pane of a split). */
object SplitPicker {
    private const val PICKER = "org.arkikeskus.launcher.recents.SplitPickerActivity"

    /** An intent that opens the picker as its own task, so it can be paired beside the dragged app. */
    fun intent(context: Context): Intent = Intent().apply {
        component = ComponentName(context.packageName, PICKER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
}
