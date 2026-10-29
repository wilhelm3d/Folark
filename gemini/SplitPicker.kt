package org.arkikeskus.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem

object SplitPicker {
    private const val PICKER = "org.arkikeskus.launcher.recents.SplitPickerActivity"
    const val EXTRA_FIRST_PACKAGE = "first_package"
    const val EXTRA_RIGHT = "right"
    const val EXTRA_SELF_DOCK_RIGHT = "self_dock_right"

    fun componentName(context: Context): ComponentName = ComponentName(context.packageName, PICKER)

    fun intent(
        context: Context,
        firstPackage: String,
        right: Boolean,
        selfDockRight: Boolean = false,
    ): Intent = Intent().apply {
        component = componentName(context)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
        putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
        putExtra(EXTRA_RIGHT, right)
        putExtra(EXTRA_SELF_DOCK_RIGHT, selfDockRight)
    }

    suspend fun openBesideApp(
        context: Context,
        appRepository: AppRepository,
        firstApp: AppItem?,
        firstPackage: String,
        firstTaskId: Int?,
        firstOnRight: Boolean,
    ) {
        val noAnim = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

        if (ShellSplit.hasDump(context)) {
            if (firstOnRight) {
                // [Picker | App 1]:
                // 1. Launch invisible Picker first (it stays transparent until MultiWindow mode triggers!)
                val pickerIntent = intent(context, firstPackage, right = true, selfDockRight = false)
                withContext(Dispatchers.Main) {
                    runCatching { context.startActivity(pickerIntent, noAnim) }
                }
                delay(90L)
                // 2. Launch App 1 and immediately dock it to the right
                withContext(Dispatchers.Main) {
                    if (firstApp != null) appRepository.launch(firstApp)
                }
                delay(90L)
                withContext(Dispatchers.IO) {
                    var appTaskId: Int? = firstTaskId ?: ShellSplit.taskIdOf(context, firstPackage)
                    repeat(6) {
                        if (appTaskId != null) return@repeat
                        delay(30L)
                        appTaskId = ShellSplit.taskIdOf(context, firstPackage)
                    }
                    if (appTaskId != null) {
                        ShellSplit.moveToSideFast(context, appTaskId, right = true)
                    }
                }
            } else {
                // [App 1 | Picker]:
                // 1. Launch App 1 first
                withContext(Dispatchers.Main) {
                    if (firstApp != null) appRepository.launch(firstApp)
                }
                delay(90L)
                // 2. Launch invisible Picker with selfDockRight = true:
                // SplitPickerActivity immediately docks its own `taskId` in onCreate and only reveals
                // the Drawer UI once onMultiWindowModeChanged fires!
                val pickerIntent = intent(context, firstPackage, right = false, selfDockRight = true)
                withContext(Dispatchers.Main) {
                    runCatching { context.startActivity(pickerIntent, noAnim) }
                }
            }
            return
        }

        val fallbackIntent = intent(context, firstPackage, firstOnRight)
        withContext(Dispatchers.Main) {
            runCatching { context.startActivity(fallbackIntent, noAnim) }
        }
    }
}
