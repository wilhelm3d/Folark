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

    fun componentName(context: Context): ComponentName = ComponentName(context.packageName, PICKER)

    fun intent(context: Context, firstPackage: String, right: Boolean): Intent = Intent().apply {
        component = componentName(context)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
        )
        putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
        putExtra(EXTRA_RIGHT, right)
    }

    suspend fun openBesideApp(
        context: Context,
        appRepository: AppRepository,
        firstApp: AppItem?,
        firstPackage: String,
        firstTaskId: Int?,
        firstOnRight: Boolean,
    ) {
        val pickerIntent = intent(context, firstPackage, firstOnRight)
        val noAnim = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

        if (ShellSplit.hasDump(context)) {
            withContext(Dispatchers.Main) {
                if (firstOnRight) {
                    runCatching { context.startActivity(pickerIntent, noAnim) }
                } else {
                    if (firstApp != null) appRepository.launch(firstApp)
                }
            }
            delay(150L)
            withContext(Dispatchers.Main) {
                if (firstOnRight) {
                    if (firstApp != null) appRepository.launch(firstApp)
                } else {
                    runCatching { context.startActivity(pickerIntent, noAnim) }
                }
            }
            delay(120L)
            withContext(Dispatchers.IO) {
                if (firstOnRight) {
                    var tId = firstTaskId ?: ShellSplit.taskIdOf(context, firstPackage)
                    repeat(6) {
                        if (tId != null) return@repeat
                        delay(30L)
                        tId = ShellSplit.taskIdOf(context, firstPackage)
                    }
                    if (tId != null) ShellSplit.moveToSide(context, tId, right = true)
                } else {
                    var pickerId: Int? = null
                    repeat(6) {
                        pickerId = ShellSplit.taskIdOf(context, context.packageName)
                        if (pickerId != null) return@repeat
                        delay(30L)
                    }
                    if (pickerId != null) ShellSplit.moveToSide(context, pickerId, right = false)
                }
            }
            return
        }

        withContext(Dispatchers.Main) {
            runCatching { context.startActivity(pickerIntent, noAnim) }
        }
    }
}
